//! Single-thread matching-engine benchmark.
//!
//! Mirrors the oracle bench's workload (`bench/torihiki/bench.cljk`) so the
//! numbers are comparable in shape: a deterministic LCG tape generated into
//! plain arrays BEFORE the clock starts, 43% cancels / 12% aggressive
//! crossings / 45% passive quotes, cancels aimed at a ring of recently
//! placed orders so slots turn over, owner = tape index & 1023.
//!
//! Two paths are timed:
//! - `match`: `Engine::apply_order` / `apply_cancel` directly (no crypto).
//! - `verify`: the same tape through `apply_signed_order` /
//!   `apply_signed_cancel` — every order and cancel Ed25519-verified.
//!   Signatures are produced once up front (untimed); the clock covers
//!   verification + matching only.
//!
//! Like the oracle, the run refuses to report when no cancel succeeded.
//!
//! Usage: `bench [--ops N] [--markets M] [--mode match|verify|both]`
//! Defaults calibrate from `--ops` when given; otherwise each mix runs long
//! enough to exceed 30 s on this machine (see README for the recorded runs).

use std::time::Instant;
use torihiki_engine::auth::{sign_cancel, sign_order};
use torihiki_engine::domain::NewOrder;
use torihiki_engine::engine::{ApplyOutcome, Engine, MarketConfig};

const N_LEVELS: u32 = 65_536;
const MID: i64 = 32_768;
const CAP: u32 = 2_097_152;
const RING_BITS: usize = 20;
const RING_LEN: usize = 1 << RING_BITS;
const RING_MASK: usize = RING_LEN - 1;

fn lcg(s: u64) -> u64 {
    s.wrapping_mul(25_214_903_917).wrapping_add(11) & ((1u64 << 48) - 1)
}

struct Tape {
    kind: Vec<u8>, // 0 = new, 1 = cancel
    side: Vec<u8>,
    level: Vec<i64>,
    qty: Vec<i64>,
    owner: Vec<i64>,
    market: Vec<u64>,
}

fn gen_tape(n: usize, markets: u64) -> Tape {
    let mut t = Tape {
        kind: vec![0; n],
        side: vec![0; n],
        level: vec![0; n],
        qty: vec![0; n],
        owner: vec![0; n],
        market: vec![0; n],
    };
    let mut s = 12345u64;
    for i in 0..n {
        let s1 = lcg(s);
        let s2 = lcg(s1);
        let s3 = lcg(s2);
        let r = (s1 >> 16) % 100;
        let spread = ((s2 >> 16) % 40) as i64;
        let sd = ((s3 >> 16) % 2) as u8;
        t.owner[i] = (i & 1023) as i64;
        t.market[i] = (i as u64) % markets;
        if r < 43 {
            t.kind[i] = 1;
        } else if r < 55 {
            t.kind[i] = 0;
            t.side[i] = sd;
            t.level[i] = if sd == 0 { MID + 30 } else { MID - 30 };
            t.qty[i] = (1 + ((s3 >> 20) % 12) as i64).max(1);
        } else {
            t.kind[i] = 0;
            t.side[i] = sd;
            t.level[i] = if sd == 0 {
                MID - 1 - spread
            } else {
                MID + 1 + spread
            };
            t.qty[i] = (1 + ((s3 >> 20) % 20) as i64).max(1);
        }
        s = s3;
    }
    t
}

struct Stats {
    ops: u64,
    placed: u64,
    cancelled: u64,
    fills: u64,
}

fn run_match(engine: &mut Engine, tape: &Tape) -> Stats {
    let n = tape.kind.len();
    let mut ring_oid = vec![-1i64; RING_LEN];
    let mut ring_owner = vec![0i64; RING_LEN];
    let mut ring_market = vec![0u64; RING_LEN];
    let mut st = Stats {
        ops: n as u64,
        placed: 0,
        cancelled: 0,
        fills: 0,
    };
    let mut w = 0usize;
    for i in 0..n {
        if tape.kind[i] == 0 {
            match engine.apply_order(NewOrder {
                market: tape.market[i],
                account: tape.owner[i],
                side: tape.side[i],
                level: tape.level[i],
                qty: tape.qty[i],
                flags: 0,
            }) {
                ApplyOutcome::Applied { oid, fills } => {
                    st.fills += fills.len() as u64;
                    if let Some(id) = oid {
                        ring_oid[w & RING_MASK] = id;
                        ring_owner[w & RING_MASK] = tape.owner[i];
                        ring_market[w & RING_MASK] = tape.market[i];
                        w += 1;
                        st.placed += 1;
                    }
                }
                ApplyOutcome::Rejected { .. } => {}
                ApplyOutcome::Cancelled { .. } => unreachable!(),
            }
        } else {
            let slot = w.wrapping_sub(1 + (i % 524_288)) & RING_MASK;
            let oid = ring_oid[slot];
            if oid >= 0 {
                match engine.apply_cancel(ring_market[slot], oid, ring_owner[slot]) {
                    ApplyOutcome::Cancelled { .. } => st.cancelled += 1,
                    ApplyOutcome::Rejected { .. } => {}
                    _ => {}
                }
                ring_oid[slot] = -1;
            }
        }
    }
    st
}

/// Pre-sign the whole tape (untimed), then verify + apply under the clock.
/// Cancel oids are collected by one untimed matching pass first.
fn run_verify(engine: &mut Engine, tape: &Tape) -> Stats {
    // One untimed pass to learn the concrete cancel sequence.
    let n = tape.kind.len();
    let mut probe = Engine::new();
    for m in 0..tape.market.iter().copied().max().unwrap_or(0) + 1 {
        probe
            .add_market(
                m,
                MarketConfig {
                    n_levels: N_LEVELS,
                    cap: CAP,
                },
            )
            .unwrap();
    }
    enum Pre {
        New {
            order: NewOrder,
            seed_idx: usize,
        },
        Cancel {
            market: u64,
            oid: i64,
            owner: i64,
            seed_idx: usize,
        },
        CancelMiss,
    }
    let mut ring_oid = vec![-1i64; RING_LEN];
    let mut ring_owner = vec![0i64; RING_LEN];
    let mut ring_market = vec![0u64; RING_LEN];
    let mut w = 0usize;
    let mut pre: Vec<Pre> = Vec::with_capacity(n);
    for i in 0..n {
        if tape.kind[i] == 0 {
            let order = NewOrder {
                market: tape.market[i],
                account: tape.owner[i],
                side: tape.side[i],
                level: tape.level[i],
                qty: tape.qty[i],
                flags: 0,
            };
            if let ApplyOutcome::Applied { oid: Some(id), .. } = probe.apply_order(order) {
                ring_oid[w & RING_MASK] = id;
                ring_owner[w & RING_MASK] = tape.owner[i];
                ring_market[w & RING_MASK] = tape.market[i];
                w += 1;
            }
            pre.push(Pre::New {
                order,
                seed_idx: (tape.owner[i] & 1023) as usize,
            });
        } else {
            let slot = w.wrapping_sub(1 + (i % 524_288)) & RING_MASK;
            let oid = ring_oid[slot];
            if oid >= 0 {
                // Record the attempt; success or already-gone is decided
                // again in the timed run.
                let _ = probe.apply_cancel(ring_market[slot], oid, ring_owner[slot]);
                pre.push(Pre::Cancel {
                    market: ring_market[slot],
                    oid,
                    owner: ring_owner[slot],
                    seed_idx: (ring_owner[slot] & 1023) as usize,
                });
                ring_oid[slot] = -1;
            } else {
                pre.push(Pre::CancelMiss);
            }
        }
    }
    // Keys: one fixed seed per account id (deterministic, no RNG).
    let seeds: Vec<[u8; 32]> = (0..1024).map(|a| [(a & 0xff) as u8; 32]).collect();
    enum Signed {
        New(torihiki_engine::auth::SignedOrder),
        Cancel(torihiki_engine::auth::SignedCancel),
        Miss,
    }
    let signed: Vec<Signed> = pre
        .into_iter()
        .map(|p| match p {
            Pre::New { order, seed_idx } => Signed::New(sign_order(&seeds[seed_idx], order)),
            Pre::Cancel {
                market,
                oid,
                owner,
                seed_idx,
            } => Signed::Cancel(sign_cancel(&seeds[seed_idx], market, oid, owner)),
            Pre::CancelMiss => Signed::Miss,
        })
        .collect();
    // Timed run: verify + apply.
    let mut st = Stats {
        ops: n as u64,
        placed: 0,
        cancelled: 0,
        fills: 0,
    };
    for s in &signed {
        match s {
            Signed::New(o) => match engine.apply_signed_order(o) {
                ApplyOutcome::Applied { oid, fills } => {
                    st.fills += fills.len() as u64;
                    if oid.is_some() {
                        st.placed += 1;
                    }
                }
                ApplyOutcome::Rejected { .. } => {}
                ApplyOutcome::Cancelled { .. } => unreachable!(),
            },
            Signed::Cancel(c) => {
                if let ApplyOutcome::Cancelled { .. } = engine.apply_signed_cancel(c) {
                    st.cancelled += 1;
                }
            }
            Signed::Miss => {}
        }
    }
    st
}

fn machine() -> String {
    let uname = std::process::Command::new("uname")
        .args(["-srm"])
        .output()
        .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
        .unwrap_or_else(|_| "unknown".into());
    let cpu = std::process::Command::new("sysctl")
        .args(["-n", "machdep.cpu.brand_string"])
        .output()
        .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
        .unwrap_or_default();
    let mem = std::process::Command::new("sysctl")
        .args(["-n", "hw.memsize"])
        .output()
        .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
        .unwrap_or_default();
    let cores = std::process::Command::new("sysctl")
        .args(["-n", "hw.ncpu"])
        .output()
        .map(|o| String::from_utf8_lossy(&o.stdout).trim().to_string())
        .unwrap_or_default();
    format!(
        "{} | {} | hw.ncpu={} | hw.memsize={}",
        uname, cpu, cores, mem
    )
}

fn resting(engine: &Engine) -> u64 {
    engine
        .market_ids()
        .iter()
        .map(|m| engine.book(*m).map(|b| b.resting_count()).unwrap_or(0))
        .sum()
}

fn run_mix(label: &str, ops: usize, markets: u64, mode: &str) {
    println!(
        "generating a {}-operation tape ({} markets)...",
        fmt(ops as f64),
        markets
    );
    let tape = gen_tape(ops, markets);
    println!("warming up...");
    {
        let mut e = Engine::new();
        for m in 0..markets {
            e.add_market(
                m,
                MarketConfig {
                    n_levels: N_LEVELS,
                    cap: CAP,
                },
            )
            .unwrap();
        }
        let small = Tape {
            kind: tape.kind[..ops.min(200_000)].to_vec(),
            side: tape.side[..ops.min(200_000)].to_vec(),
            level: tape.level[..ops.min(200_000)].to_vec(),
            qty: tape.qty[..ops.min(200_000)].to_vec(),
            owner: tape.owner[..ops.min(200_000)].to_vec(),
            market: tape.market[..ops.min(200_000)].to_vec(),
        };
        for _ in 0..2 {
            if mode == "verify" {
                run_verify(&mut e, &small);
            } else {
                run_match(&mut e, &small);
            }
        }
    }
    let mut e = Engine::new();
    for m in 0..markets {
        e.add_market(
            m,
            MarketConfig {
                n_levels: N_LEVELS,
                cap: CAP,
            },
        )
        .unwrap();
    }
    println!("measuring {} operations [{}]...", fmt(ops as f64), label);
    let t0 = Instant::now();
    let st = if mode == "verify" {
        run_verify(&mut e, &tape)
    } else {
        run_match(&mut e, &tape)
    };
    let secs = t0.elapsed().as_secs_f64();
    let ops_per_sec = st.ops as f64 / secs;
    println!();
    println!("  mix               {} (mode={})", label, mode);
    println!("  operations        {}", fmt(st.ops as f64));
    println!("  placed            {}", fmt(st.placed as f64));
    println!("  cancelled         {}", fmt(st.cancelled as f64));
    println!("  fills             {}", fmt(st.fills as f64));
    println!("  resting at end    {}", fmt(resting(&e) as f64));
    println!("  state hash        {}", e.state_hash_hex());
    println!("  elapsed           {:.3} s", secs);
    println!();
    println!("  THROUGHPUT        {} ops/sec", fmt(ops_per_sec));
    println!(
        "  latency           {:.0} ns/op",
        secs * 1e9 / st.ops as f64
    );
    println!();
    if st.ops >= 1000 && st.cancelled == 0 {
        println!("REFUSED: no cancel succeeded — the tape did not exercise cancellation");
        std::process::exit(1);
    }
}

fn fmt(x: f64) -> String {
    let s = format!("{:.0}", x);
    let mut out = String::new();
    for (i, c) in s.chars().rev().enumerate() {
        if i > 0 && i % 3 == 0 {
            out.push(',');
        }
        out.push(c);
    }
    out.chars().rev().collect()
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let mut ops: Option<usize> = None;
    let mut markets = 1u64;
    let mut mode = "both".to_string();
    let mut i = 1;
    while i < args.len() {
        match args[i].as_str() {
            "--ops" => {
                ops = Some(args[i + 1].parse().expect("--ops needs a number"));
                i += 1;
            }
            "--markets" => {
                markets = args[i + 1].parse().expect("--markets needs a number");
                i += 1;
            }
            "--mode" => {
                mode = args[i + 1].clone();
                i += 1;
            }
            other => {
                eprintln!("unknown arg {}", other);
                std::process::exit(2);
            }
        }
        i += 1;
    }
    println!("machine: {}", machine());
    match rustc_version::version() {
        Ok(v) => println!("rustc: {}", v),
        Err(_) => println!("rustc: unknown"),
    }
    if mode == "both" {
        let n = ops.unwrap_or(8_000_000);
        run_mix(
            &format!(
                "steady-state 43% cancel / 12% cross / 45% quote, {} market(s)",
                markets
            ),
            n,
            markets,
            "match",
        );
        run_mix(
            &format!(
                "steady-state 43% cancel / 12% cross / 45% quote, {} market(s)",
                markets
            ),
            n,
            markets,
            "verify",
        );
    } else {
        let n = ops.unwrap_or(8_000_000);
        run_mix(
            &format!(
                "steady-state 43% cancel / 12% cross / 45% quote, {} market(s)",
                markets
            ),
            n,
            markets,
            &mode,
        );
    }
}
