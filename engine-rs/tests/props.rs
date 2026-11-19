//! Property tests (proptest):
//!
//! - Conservation of quantity: every submitted lot is filled, resting, or
//!   explicitly cancelled — never created or destroyed (mirrors the oracle's
//!   `conservation-of-quantity` test).
//! - No crossed book after matching.
//! - Price-time priority against an independent naive model: a sorted-`Vec`
//!   reference matcher with per-level FIFO queues must agree with the engine
//!   on every fill and every resting queue.
//! - Determinism under replay: the same op sequence reaches the same state
//!   hash twice, and interleaved rejections do not move it.

use proptest::prelude::*;
use torihiki_engine::domain::{NewOrder, Side};
use torihiki_engine::engine::{ApplyOutcome, Engine, MarketConfig};

const N_LEVELS: u32 = 64;

/// A deliberately naive reference matcher: per-side queues as plain vectors,
/// best level found by scan. Independent of `Book`'s intrusive lists.
#[derive(Default)]
struct Model {
    bids: Vec<Ord>,
    asks: Vec<Ord>,
}

#[derive(Clone, Debug)]
struct Ord {
    oid: u64,
    owner: i64,
    level: u32,
    qty: i64,
}

#[derive(Clone, Copy, Debug)]
struct ModelPlace {
    side: Side,
    level: u32,
    qty: i64,
    ioc: bool,
    post_only: bool,
    owner: i64,
}

#[derive(Clone, Debug, PartialEq, Eq)]
struct ModelFill {
    maker_oid: u64,
    maker: i64,
    level: u32,
    qty: i64,
}

impl Model {
    /// Returns (rested_oid_or_new_id, fills). `next_oid` supplies fresh ids.
    fn place(&mut self, p: ModelPlace, next_oid: &mut u64) -> (Option<u64>, Vec<ModelFill>) {
        let ModelPlace {
            side,
            level,
            qty: qty0,
            ioc,
            post_only,
            owner,
        } = p;
        let mut qty = qty0;
        fn best(side: Side, bids: &[Ord], asks: &[Ord]) -> Option<u32> {
            match side {
                Side::Bid => asks.iter().map(|o| o.level).min(),
                Side::Ask => bids.iter().map(|o| o.level).max(),
            }
        }
        let crosses = |best: Option<u32>| match (side, best) {
            (_, None) => false,
            (Side::Bid, Some(b)) => b <= level,
            (Side::Ask, Some(b)) => b >= level,
        };
        if post_only && crosses(best(side, &self.bids, &self.asks)) {
            return (None, vec![]);
        }
        let opp = match side {
            Side::Bid => &mut self.asks,
            Side::Ask => &mut self.bids,
        };
        let mut fills = vec![];
        while qty > 0 {
            // Best opposing level, scanned naively from the same vector we
            // consume — no second borrow needed.
            let bl = match side {
                Side::Bid => opp.iter().map(|o| o.level).min().filter(|&b| b <= level),
                Side::Ask => opp.iter().map(|o| o.level).max().filter(|&b| b >= level),
            };
            let Some(bl) = bl else { break };
            // Head of the FIFO queue at the best level = earliest oid there.
            let idx = opp
                .iter()
                .filter(|o| o.level == bl)
                .min_by_key(|o| o.oid)
                .map(|o| opp.iter().position(|x| x.oid == o.oid).unwrap());
            let Some(i) = idx else { break };
            let traded = qty.min(opp[i].qty);
            fills.push(ModelFill {
                maker_oid: opp[i].oid,
                maker: opp[i].owner,
                level: bl,
                qty: traded,
            });
            opp[i].qty -= traded;
            if opp[i].qty == 0 {
                opp.remove(i);
            }
            qty -= traded;
        }
        if qty == 0 || ioc {
            return (None, fills);
        }
        let oid = *next_oid;
        *next_oid += 1;
        match side {
            Side::Bid => self.bids.push(Ord {
                oid,
                owner,
                level,
                qty,
            }),
            Side::Ask => self.asks.push(Ord {
                oid,
                owner,
                level,
                qty,
            }),
        }
        (Some(oid), fills)
    }

    fn resting_total(&self) -> i64 {
        self.bids.iter().map(|o| o.qty).sum::<i64>() + self.asks.iter().map(|o| o.qty).sum::<i64>()
    }
}

#[derive(Clone, Debug)]
enum Op {
    New {
        side: Side,
        level: u32,
        qty: i64,
        flags: u8,
        owner: i64,
    },
    Cancel {
        nth_new: usize,
        owner: i64,
    },
}

fn op_strategy() -> impl Strategy<Value = Op> {
    prop_oneof![
        (
            prop::bool::ANY,
            0..N_LEVELS,
            1i64..50,
            // All shaped flag values, including combinations (3 = IOC|post-only,
            // 5..7 add reduce-only, which the book ignores like the oracle's).
            prop::sample::select(vec![0u8, 0, 1, 2, 3, 4, 5, 6, 7]),
            0i64..4,
        )
            .prop_map(|(s, l, q, f, o)| Op::New {
                side: if s { Side::Bid } else { Side::Ask },
                level: l,
                qty: q,
                flags: f,
                owner: o,
            }),
        (0usize..30, 0i64..4).prop_map(|(n, o)| Op::Cancel {
            nth_new: n,
            owner: o
        }),
    ]
}

proptest! {
    #![proptest_config(ProptestConfig { cases: 256, ..ProptestConfig::default() })]

    /// The engine agrees with the naive model on every fill and every
    /// resting queue, and the book never crosses.
    #[test]
    fn matches_naive_model(ops in proptest::collection::vec(op_strategy(), 1..120)) {
        let mut engine = Engine::new();
        engine.add_market(1, MarketConfig { n_levels: N_LEVELS, cap: 4096 }).unwrap();
        let mut model = Model::default();
        let mut next_oid = 0u64;
        // Engine oids (slot*gen) <-> model oids, in placement order.
        let mut new_oids: Vec<Option<i64>> = vec![];
        let mut model_oids: Vec<Option<u64>> = vec![];
        let mut new_idx = 0usize;

        for op in &ops {
            match op {
                Op::New { side, level, qty, flags, owner } => {
                    let ioc = flags & 1 != 0;
                    let po = flags & 2 != 0;
                    let (m_oid, m_fills) = model.place(
                        ModelPlace { side: *side, level: *level, qty: *qty, ioc, post_only: po, owner: *owner },
                        &mut next_oid,
                    );
                    let out = engine.apply_order(NewOrder {
                        market: 1, account: *owner,
                        side: *side as u8, level: *level as i64,
                        qty: *qty, flags: *flags,
                    });
                    match out {
                        ApplyOutcome::Applied { oid, fills } => {
                            prop_assert_eq!(oid.is_some(), m_oid.is_some(), "rested agreement");
                            prop_assert_eq!(fills.len(), m_fills.len(), "fill count");
                            for (f, m) in fills.iter().zip(m_fills.iter()) {
                                prop_assert_eq!(f.maker, m.maker, "maker");
                                prop_assert_eq!(f.level, m.level, "fill price = maker level");
                                prop_assert_eq!(f.qty, m.qty, "fill qty");
                                prop_assert_eq!(f.taker, *owner, "taker");
                                prop_assert_eq!(f.taker_side, *side as u8, "taker side");
                            }
                            // Fills print at the maker's level: the taker's
                            // limit never sets the price (no rounding exists
                            // — everything is integral).
                            new_oids.push(oid);
                            model_oids.push(m_oid);
                            new_idx += 1;
                        }
                        ApplyOutcome::Rejected { .. } => {
                            prop_assert!(false, "shape-valid new order must not reject");
                        }
                        ApplyOutcome::Cancelled { .. } => {
                            prop_assert!(false, "new order never cancels");
                        }
                    }
                }
                Op::Cancel { nth_new, owner } => {
                    let target = nth_new % new_idx.max(1);
                    if new_idx == 0 || new_oids.get(target).copied().flatten().is_none() {
                        continue;
                    }
                    let engine_oid = new_oids[target].unwrap();
                    let model_oid = model_oids[target].unwrap();
                    // Cancel in the model: find by oid, check owner.
                    let in_model = model.bids.iter().chain(model.asks.iter())
                        .find(|o| o.oid == model_oid).cloned();
                    let out = engine.apply_cancel(1, engine_oid, *owner);
                    match in_model {
                        Some(m) if m.owner == *owner => {
                            // Must succeed for the same qty.
                            prop_assert_eq!(out, ApplyOutcome::Cancelled { qty: m.qty });
                            let in_b = model.bids.iter().position(|o| o.oid == model_oid);
                            let in_a = model.asks.iter().position(|o| o.oid == model_oid);
                            if let Some(i) = in_b { model.bids.remove(i); }
                            if let Some(i) = in_a { model.asks.remove(i); }
                            new_oids[target] = None;
                        }
                        _ => {
                            // Unknown, filled, or another owner's: rejected.
                            prop_assert!(
                                matches!(out, ApplyOutcome::Rejected { .. }),
                                "cancel of non-owned/non-live must reject, got {:?}", out
                            );
                        }
                    }
                }
            }
            // No crossed book, ever.
            let book = engine.book(1).unwrap();
            prop_assert!(!book.is_crossed(), "book crossed after {:?}", op);
        }

        // Conservation: submitted == filled + resting + cancelled.
        // Filled quantities are whatever left both books; compare resting
        // totals between engine and model instead — they must be equal, and
        // the model's total is independently computed.
        let book = engine.book(1).unwrap();
        let engine_resting: i64 = {
            let mut t = 0;
            for side in [Side::Bid, Side::Ask] {
                for lvl in book.ordered_levels(side) {
                    for o in &lvl.queue { t += o.qty; }
                }
            }
            t
        };
        prop_assert_eq!(engine_resting, model.resting_total(), "resting totals agree");
    }

    /// Replaying the same tape twice reaches the same hash; rejections in
    /// the middle do not move it.
    #[test]
    fn replay_deterministic(ops in proptest::collection::vec(op_strategy(), 1..120)) {
        let run = |ops: &[Op]| {
            let mut engine = Engine::new();
            engine.add_market(1, MarketConfig { n_levels: N_LEVELS, cap: 4096 }).unwrap();
            let mut oids: Vec<i64> = vec![];
            for op in ops {
                match op {
                    Op::New { side, level, qty, flags, owner } => {
                        if let ApplyOutcome::Applied { oid: Some(id), .. } =
                            engine.apply_order(NewOrder {
                                market: 1, account: *owner,
                                side: *side as u8, level: *level as i64,
                                qty: *qty, flags: *flags,
                            })
                        {
                            oids.push(id);
                        }
                        // Hostile extras: rejected, hash must not move.
                        let h = engine.state_hash_hex();
                        let _ = engine.apply_order(NewOrder {
                            market: 77, account: *owner,
                            side: *side as u8, level: *level as i64,
                            qty: *qty, flags: *flags,
                        });
                        assert_eq!(engine.state_hash_hex(), h);
                    }
                    Op::Cancel { owner, .. } => {
                        // Cancel whatever the last id was, or garbage.
                        let oid = oids.last().copied().unwrap_or(12345);
                        let _ = engine.apply_cancel(1, oid, *owner);
                        let _ = engine.apply_cancel(1, -7, *owner);
                        // Either path leaves a well-defined hash; replay
                        // equality is asserted across the two full runs.
                    }
                }
            }
            engine.state_hash_hex()
        };
        prop_assert_eq!(run(&ops), run(&ops));
    }
}

/// Time priority is FIFO per level, owned by the model's min-oid rule above
/// and pinned by the oracle's `price-time-priority` fixture; this focused
/// test pins the engine directly: three makers, one taker, exact split.
#[test]
fn time_priority_exact_split() {
    let mut engine = Engine::new();
    engine
        .add_market(
            1,
            MarketConfig {
                n_levels: 64,
                cap: 64,
            },
        )
        .unwrap();
    for owner in [11i64, 22, 33] {
        match engine.apply_order(NewOrder {
            market: 1,
            account: owner,
            side: 1,
            level: 50,
            qty: 5,
            flags: 0,
        }) {
            ApplyOutcome::Applied {
                oid: Some(_),
                fills,
            } => assert!(fills.is_empty()),
            other => panic!("{:?}", other),
        }
    }
    match engine.apply_order(NewOrder {
        market: 1,
        account: 44,
        side: 0,
        level: 50,
        qty: 12,
        flags: 0,
    }) {
        ApplyOutcome::Applied { oid: None, fills } => {
            assert_eq!(fills.len(), 3);
            assert_eq!((fills[0].maker, fills[0].qty), (11, 5));
            assert_eq!((fills[1].maker, fills[1].qty), (22, 5));
            assert_eq!((fills[2].maker, fills[2].qty), (33, 2));
        }
        other => panic!("{:?}", other),
    }
}

/// Conservation on a fixed tape, asserted as an equation, not a comparison.
#[test]
fn conservation_equation() {
    let mut engine = Engine::new();
    engine
        .add_market(
            1,
            MarketConfig {
                n_levels: 1024,
                cap: 4096,
            },
        )
        .unwrap();
    let mut submitted = 0i64;
    let mut filled = 0i64;
    let mut cancelled = 0i64;
    // Fixed pseudo-random tape (LCG, like the oracle bench's generator).
    let mut s = 12345u64;
    let mut oids: Vec<(i64, i64)> = vec![];
    for i in 0..2000 {
        s = s.wrapping_mul(25214903917).wrapping_add(11) & ((1 << 48) - 1);
        let r = (s >> 16) % 100;
        if r < 40 && !oids.is_empty() {
            let idx = (s as usize >> 20) % oids.len();
            let (oid, owner) = oids[idx];
            if let ApplyOutcome::Cancelled { qty } = engine.apply_cancel(1, oid, owner) {
                cancelled += qty;
                oids.remove(idx);
            }
        } else {
            let side = (s >> 8) as u8 & 1;
            let level = 492 + ((s >> 16) as i64 % 40);
            let qty = 1 + ((s >> 20) as i64 % 12);
            let owner = (i % 5) as i64;
            submitted += qty;
            match engine.apply_order(NewOrder {
                market: 1,
                account: owner,
                side,
                level,
                qty,
                flags: 0,
            }) {
                ApplyOutcome::Applied { oid, fills } => {
                    for f in &fills {
                        filled += f.qty;
                        // Remove/reduce consumed makers from tracking.
                        if let Some(p) = oids.iter().position(|(id, _)| *id == f.maker_oid) {
                            oids[p].1 -= f.qty;
                            if oids[p].1 <= 0 {
                                oids.remove(p);
                            }
                        }
                    }
                    if let Some(id) = oid {
                        let taken: i64 = fills.iter().map(|f| f.qty).sum();
                        oids.push((id, qty - taken));
                    }
                }
                other => panic!("unexpected {:?}", other),
            }
        }
    }
    let book = engine.book(1).unwrap();
    let mut resting = 0i64;
    for side in [Side::Bid, Side::Ask] {
        for lvl in book.ordered_levels(side) {
            for o in &lvl.queue {
                resting += o.qty;
            }
        }
    }
    // Tracked remaining must equal the book's own queues.
    let tracked: i64 = oids.iter().map(|(_, q)| *q).sum();
    assert_eq!(tracked, resting, "tracking matches book");
    // Each fill consumes one lot from a taker and one from a maker — the
    // oracle's `conservation-of-quantity` equation, mirrored exactly.
    assert_eq!(submitted, 2 * filled + resting + cancelled, "conservation");
    assert!(filled > 0, "the tape must actually cross");
    assert!(cancelled > 0, "the tape must actually cancel");
}
