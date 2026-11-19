//! Parity with the oracle: replay fixture tapes produced by RUNNING the
//! existing implementation against `torihiki.book` and compare every
//! observable.
//!
//! Fixture provenance: `.agent-output/gen-parity.cljk`, executed with
//! `kbb --backend sci` (see `engine-rs/evidence/oracle-runs.md` for the exact
//! command, tool versions, and date). Each fixture records the oracle's own
//! `place!`/`cancel!` return values (`-1` = nothing rested), the fill maps,
//! and the final resting count and touch.
//!
//! Deliberate surfacing differences versus the oracle, asserted here:
//!
//! - The oracle's `cancel!` answers every non-cancellable id with `0`
//!   (unknown, filled, already cancelled, stale generation, wrong owner).
//!   This engine returns `Rejection::UnknownOrder`, or
//!   `Rejection::NotOwner` when the id names a live order of another
//!   account. Same state effect (nothing changes), louder reason.
//! - `amend-reduce-keeps-place` is recorded in the evidence EDN but NOT in
//!   the replayable JSON: amend (`reduce-to!`) is outside stage-1 scope
//!   (new order, cancel, fills). See `engine-rs/README.md`.

use std::collections::HashMap;
use torihiki_engine::book::Book;
use torihiki_engine::domain::{Rejection, Side};

#[derive(Debug, serde::Deserialize)]
struct Fixture {
    name: String,
    #[serde(rename = "n-levels")]
    n_levels: u32,
    cap: u32,
    tape: Vec<Op>,
    result: Expected,
}

#[derive(Debug, serde::Deserialize)]
struct Op {
    op: String,
    side: Option<u8>,
    level: Option<i64>,
    qty: Option<i64>,
    account: Option<i64>,
    flags: Option<u8>,
    oid: Option<i64>,
}

#[derive(Debug, serde::Deserialize)]
struct Expected {
    results: Vec<ExpResult>,
    resting: u64,
    #[serde(rename = "best-bid")]
    best_bid: i64,
    #[serde(rename = "best-ask")]
    best_ask: i64,
}

#[derive(Debug, serde::Deserialize)]
struct ExpResult {
    #[allow(dead_code)]
    op: String,
    oid: Option<i64>,
    fills: Option<Vec<ExpFill>>,
    qty: Option<i64>,
}

#[derive(Debug, serde::Deserialize)]
struct ExpFill {
    #[serde(rename = "taker-owner")]
    taker_owner: i64,
    #[serde(rename = "maker-owner")]
    maker_owner: i64,
    #[serde(rename = "maker-oid")]
    maker_oid: i64,
    level: i64,
    qty: i64,
    #[serde(rename = "taker-side")]
    taker_side: u8,
}

fn load_fixtures() -> Vec<Fixture> {
    let text = include_str!("parity_fixtures.jsonl");
    text.lines()
        .filter(|l| !l.trim().is_empty())
        .map(serde_json::from_str)
        .collect::<Result<Vec<_>, _>>()
        .expect("fixtures must parse")
}

#[test]
fn oracle_fixture_count() {
    // Guard against a truncated fixture file silently weakening this test.
    assert_eq!(load_fixtures().len(), 12);
}

#[test]
fn replay_matches_oracle() {
    for fx in load_fixtures() {
        let mut book = Book::new(fx.n_levels, fx.cap);
        // Live orders by id -> (owner, remaining qty), tracked exactly so a
        // `qty: 0` from the oracle maps to the right explicit reason.
        let mut live: HashMap<i64, (i64, i64)> = HashMap::new();
        assert_eq!(
            fx.tape.len(),
            fx.result.results.len(),
            "fixture {}: tape/result length",
            fx.name
        );
        for (i, (op, exp)) in fx.tape.iter().zip(fx.result.results.iter()).enumerate() {
            match op.op.as_str() {
                "new" => {
                    let side = Side::from_u8(op.side.unwrap()).unwrap();
                    let out = book
                        .place(
                            side,
                            op.level.unwrap() as u32,
                            op.qty.unwrap(),
                            op.flags.unwrap_or(0),
                            op.account.unwrap(),
                        )
                        .unwrap_or_else(|e| {
                            panic!("fixture {} op {}: unexpected rejection {:?}", fx.name, i, e)
                        });
                    let exp_oid = exp.oid.unwrap();
                    assert_eq!(
                        out.oid,
                        if exp_oid < 0 { None } else { Some(exp_oid) },
                        "fixture {} op {}: resting oid",
                        fx.name,
                        i
                    );
                    let exp_fills = exp.fills.as_deref().unwrap_or(&[]);
                    assert_eq!(
                        out.fills.len(),
                        exp_fills.len(),
                        "fixture {} op {}: fill count",
                        fx.name,
                        i
                    );
                    for (f, e) in out.fills.iter().zip(exp_fills.iter()) {
                        assert_eq!(
                            f.taker, e.taker_owner,
                            "fixture {} op {}: taker",
                            fx.name, i
                        );
                        assert_eq!(
                            f.maker, e.maker_owner,
                            "fixture {} op {}: maker",
                            fx.name, i
                        );
                        assert_eq!(
                            f.maker_oid, e.maker_oid,
                            "fixture {} op {}: maker-oid",
                            fx.name, i
                        );
                        assert_eq!(
                            f.level as i64, e.level,
                            "fixture {} op {}: level",
                            fx.name, i
                        );
                        assert_eq!(f.qty, e.qty, "fixture {} op {}: qty", fx.name, i);
                        assert_eq!(
                            f.taker_side, e.taker_side,
                            "fixture {} op {}: taker-side",
                            fx.name, i
                        );
                    }
                    if let Some(oid) = out.oid {
                        let taken: i64 = out.fills.iter().map(|f| f.qty).sum();
                        live.insert(oid, (op.account.unwrap(), op.qty.unwrap() - taken));
                    }
                    // Makers consumed by this taker shrink; emptied ones
                    // leave `live`.
                    for f in &out.fills {
                        if let Some(entry) = live.get_mut(&f.maker_oid) {
                            entry.1 -= f.qty;
                            if entry.1 <= 0 {
                                live.remove(&f.maker_oid);
                            }
                        }
                    }
                }
                "cancel" => {
                    let oid = op.oid.unwrap();
                    let owner = op.account.unwrap();
                    let exp_qty = exp.qty.unwrap();
                    match book.cancel(oid, owner) {
                        Ok(q) => {
                            assert!(
                                exp_qty > 0,
                                "fixture {} op {}: oracle qty 0 but we cancelled {}",
                                fx.name,
                                i,
                                q
                            );
                            assert_eq!(q, exp_qty, "fixture {} op {}: cancelled qty", fx.name, i);
                            live.remove(&oid);
                        }
                        Err(Rejection::NotOwner) => {
                            assert_eq!(
                                exp_qty, 0,
                                "fixture {} op {}: oracle cancelled but we say not-owner",
                                fx.name, i
                            );
                            assert!(
                                live.get(&oid).is_some_and(|(o, _)| *o != owner),
                                "fixture {} op {}: not-owner with no live other-owner order",
                                fx.name,
                                i
                            );
                        }
                        Err(Rejection::UnknownOrder) => {
                            assert_eq!(
                                exp_qty, 0,
                                "fixture {} op {}: oracle cancelled but we say unknown",
                                fx.name, i
                            );
                        }
                        Err(e) => panic!(
                            "fixture {} op {}: unexpected cancel rejection {:?}",
                            fx.name, i, e
                        ),
                    }
                }
                other => panic!("fixture {} op {}: unknown op {}", fx.name, i, other),
            }
        }
        assert_eq!(
            book.resting_count(),
            fx.result.resting,
            "fixture {}: resting count",
            fx.name
        );
        assert_eq!(
            book.best(Side::Bid),
            fx.result.best_bid,
            "fixture {}: best bid",
            fx.name
        );
        assert_eq!(
            book.best(Side::Ask),
            fx.result.best_ask,
            "fixture {}: best ask",
            fx.name
        );
        // Replay determinism: the same tape on a fresh book reaches the same
        // state hash.
        let h1 = {
            let mut e = torihiki_engine::Engine::new();
            e.add_market(
                1,
                torihiki_engine::engine::MarketConfig {
                    n_levels: fx.n_levels,
                    cap: fx.cap,
                },
            )
            .unwrap();
            replay_engine(&mut e, &fx);
            e.state_hash_hex()
        };
        let h2 = {
            let mut e = torihiki_engine::Engine::new();
            e.add_market(
                1,
                torihiki_engine::engine::MarketConfig {
                    n_levels: fx.n_levels,
                    cap: fx.cap,
                },
            )
            .unwrap();
            replay_engine(&mut e, &fx);
            e.state_hash_hex()
        };
        assert_eq!(h1, h2, "fixture {}: replay hash", fx.name);
    }
}

fn replay_engine(e: &mut torihiki_engine::Engine, fx: &Fixture) {
    use torihiki_engine::domain::NewOrder;
    for op in &fx.tape {
        match op.op.as_str() {
            "new" => {
                let _ = e.apply_order(NewOrder {
                    market: 1,
                    account: op.account.unwrap(),
                    side: op.side.unwrap(),
                    level: op.level.unwrap(),
                    qty: op.qty.unwrap(),
                    flags: op.flags.unwrap_or(0),
                });
            }
            "cancel" => {
                let _ = e.apply_cancel(1, op.oid.unwrap(), op.account.unwrap());
            }
            _ => {}
        }
    }
}
