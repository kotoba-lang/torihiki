//! One market's limit order book: price-time priority, integer prices and
//! quantities, bounded memory.
//!
//! Mirrors `torihiki.book`:
//!
//! - Price-time priority: resting orders queue FIFO per price level
//!   (intrusive doubly-linked list through the slot array, so cancel unlinks
//!   in O(1)); a taker sweeps the best opposing level first, then outward.
//! - The fill price is always the maker's level; fills consume the head of
//!   the queue first (`price-time-priority`, `price-priority-across-levels`).
//! - Order ids are `slot * GEN_MOD + generation`, so cancel resolves with
//!   arithmetic and a stale id can never catch whoever inherited its slot
//!   (`stale-id-cannot-cancel-a-stranger`).
//! - A fresh slot is always the LOWEST free one, a pure function of which
//!   slots are occupied — never of the order they were freed in — so the
//!   same input sequence hands out the same ids (`take-slot!`).
//! - A full book takes what crosses and rests nothing, like an IOC
//!   (`take-slot!`).
//! - `cancel` compares owners and answers 0 for anything that is not a live
//!   order of the signer (`cancel!` requires `owner`).
//! - No self-trade prevention: a taker fills its own resting orders like
//!   anyone else's. The oracle has none either (verified by searching the
//!   oracle sources for self-trade/wash handling: none exists).
//!
//! Differences from the oracle (all documented in `engine-rs/README.md`):
//!
//! - Best-level lookup is an ordered set of occupied levels per side rather
//!   than the oracle's four-level bit ladder. Same answers, different
//!   structure; the set is iterated in level order only, never hashed.
//! - No fill event buffer with a cap: fills are returned to the caller of
//!   the order that produced them. A single taker can still only consume
//!   what rests, so memory per call is bounded by the book that already
//!   exists.

use crate::domain::{order_flags, sadd, Fill, Rejection, Side, GEN_MOD};
use std::cmp::Reverse;
use std::collections::{BTreeSet, BinaryHeap};

/// One resting order slot.
#[derive(Clone, Copy, Debug)]
struct Slot {
    owner: i64,
    qty: i64,
    side: u8,
    level: u32,
    prev: Option<u32>,
    next: Option<u32>,
    generation: u32,
}

/// One price level's FIFO queue.
#[derive(Clone, Debug)]
struct Level {
    head: Option<u32>,
    tail: Option<u32>,
    /// Total resting quantity. Saturates like `torihiki.fixed/acc`.
    qty: i64,
}

/// A single market's book. All memory is preallocated at construction
/// (`cap` order slots, `2 * n_levels` level queues); placing, matching and
/// cancelling allocate nothing.
#[derive(Debug)]
pub struct Book {
    n_levels: u32,
    cap: u32,
    occupied: [BTreeSet<u32>; 2],
    levels: Vec<Level>,
    slots: Vec<Slot>,
    free: BinaryHeap<Reverse<u32>>,
    resting: u64,
    last_price: i64,
}

impl Book {
    pub fn new(n_levels: u32, cap: u32) -> Book {
        assert!(n_levels > 0, "n_levels must be positive");
        assert!(cap > 0, "cap must be positive");
        let levels = vec![
            Level {
                head: None,
                tail: None,
                qty: 0,
            };
            2 * n_levels as usize
        ];
        let slots = vec![
            Slot {
                owner: 0,
                qty: 0,
                side: 0,
                level: 0,
                prev: None,
                next: None,
                generation: 0,
            };
            cap as usize
        ];
        let free: BinaryHeap<Reverse<u32>> = (0..cap).map(Reverse).collect();
        Book {
            n_levels,
            cap,
            occupied: [BTreeSet::new(), BTreeSet::new()],
            levels,
            slots,
            free,
            resting: 0,
            last_price: -1,
        }
    }

    pub fn n_levels(&self) -> u32 {
        self.n_levels
    }

    pub fn cap(&self) -> u32 {
        self.cap
    }

    pub fn resting_count(&self) -> u64 {
        self.resting
    }

    pub fn last_price(&self) -> i64 {
        self.last_price
    }

    fn level_idx(side: u8, level: u32, n_levels: u32) -> usize {
        side as usize * n_levels as usize + level as usize
    }

    /// Best price level on a side: highest bid, lowest ask, or -1 when the
    /// side is empty (`torihiki.book/best`).
    pub fn best(&self, side: Side) -> i64 {
        let set = &self.occupied[side as usize];
        match side {
            Side::Bid => set.iter().next_back().map(|&l| l as i64).unwrap_or(-1),
            Side::Ask => set.iter().next().map(|&l| l as i64).unwrap_or(-1),
        }
    }

    /// Next occupied level strictly beyond `level`, walking outward from the
    /// top of book (`torihiki.book/next-occupied`). -1 when there is none.
    pub fn next_occupied(&self, side: Side, level: u32) -> i64 {
        let set = &self.occupied[side as usize];
        match side {
            Side::Bid => set
                .range(..level)
                .next_back()
                .map(|&l| l as i64)
                .unwrap_or(-1),
            Side::Ask => set
                .range(level + 1..)
                .next()
                .map(|&l| l as i64)
                .unwrap_or(-1),
        }
    }

    pub fn level_qty(&self, side: Side, level: u32) -> i64 {
        self.levels[Self::level_idx(side as u8, level, self.n_levels)].qty
    }

    /// Whether an order at `level` on `side` would cross the book as it
    /// stands (`torihiki.book/marketable?` + `crosses?`).
    pub fn crosses(&self, side: Side, level: i64) -> bool {
        let opp_best = self.best(side.opposite());
        if opp_best < 0 {
            return false;
        }
        match side {
            // A bid crosses when the best ask is at or under its limit.
            Side::Bid => opp_best <= level,
            // An ask crosses when the best bid is at or over its limit.
            Side::Ask => opp_best >= level,
        }
    }

    pub fn is_full(&self) -> bool {
        self.free.is_empty()
    }

    fn link_tail(&mut self, side: u8, level: u32, slot: u32) {
        let k = Self::level_idx(side, level, self.n_levels);
        let lvl = &mut self.levels[k];
        self.slots[slot as usize].prev = lvl.tail;
        self.slots[slot as usize].next = None;
        if let Some(t) = lvl.tail {
            self.slots[t as usize].next = Some(slot);
        } else {
            lvl.head = Some(slot);
            self.occupied[side as usize].insert(level);
        }
        self.levels[k].tail = Some(slot);
    }

    fn unlink(&mut self, side: u8, level: u32, slot: u32) {
        let k = Self::level_idx(side, level, self.n_levels);
        let (p, n) = {
            let s = &self.slots[slot as usize];
            (s.prev, s.next)
        };
        match p {
            Some(prev) => self.slots[prev as usize].next = n,
            None => self.levels[k].head = n,
        }
        match n {
            Some(next) => self.slots[next as usize].prev = p,
            None => self.levels[k].tail = p,
        }
        if p.is_none() && n.is_none() {
            self.occupied[side as usize].remove(&level);
        }
    }

    fn oid_of(&self, slot: u32) -> i64 {
        slot as i64 * GEN_MOD + self.slots[slot as usize].generation as i64
    }

    /// What ` Book::cancel` reports for one id: the live slot plus its
    /// recorded owner, or the reason the id names nothing cancellable.
    fn resolve(&self, oid: i64) -> Result<(u32, i64), Rejection> {
        if oid < 0 {
            return Err(Rejection::UnknownOrder);
        }
        let slot = (oid / GEN_MOD) as u64;
        let gen = (oid % GEN_MOD) as u64;
        if slot >= self.cap as u64 {
            return Err(Rejection::UnknownOrder);
        }
        let s = &self.slots[slot as usize];
        if s.generation as u64 != gen || s.qty <= 0 {
            // Generation mismatch: stale id, filled order, or a slot that
            // already turned over. Zero quantity: cancelled, never live here.
            return Err(Rejection::UnknownOrder);
        }
        Ok((slot as u32, s.owner))
    }

    /// Submit an order. Returns the resting order id, or `None` when nothing
    /// rested: fully filled, IOC remainder cancelled, post-only refusal, or a
    /// full book that took what crossed (`torihiki.book/place!` returns -1
    /// for all four; the fills that did happen are still reported).
    ///
    /// Caller must have validated shape already; out-of-range levels are
    /// refused defensively rather than panicking.
    pub fn place(
        &mut self,
        side: Side,
        level: u32,
        mut qty: i64,
        flags: u8,
        owner: i64,
    ) -> Result<PlaceOutcome, Rejection> {
        if qty <= 0 {
            return Err(Rejection::BadQuantity);
        }
        if level >= self.n_levels {
            return Err(Rejection::BadPriceLevel);
        }
        let opp = side.opposite();
        if flags & order_flags::POST_ONLY != 0 && self.crosses(side, level as i64) {
            // Post-only that would take is rejected outright: no fills, no
            // rest (`post-only-rejects-a-taker`).
            return Ok(PlaceOutcome {
                oid: None,
                fills: vec![],
            });
        }
        let ioc = flags & order_flags::IOC != 0;
        let mut fills = Vec::new();
        while qty > 0 {
            let bl = self.best(opp);
            let takes = bl >= 0
                && match side {
                    Side::Bid => bl <= level as i64,
                    Side::Ask => bl >= level as i64,
                };
            if !takes {
                break;
            }
            let bl = bl as u32;
            let k = Self::level_idx(opp as u8, bl, self.n_levels);
            let head = match self.levels[k].head {
                Some(h) => h,
                None => {
                    // Level bit set but queue empty: cannot happen, but
                    // clearing the bit and continuing beats looping forever.
                    self.occupied[opp as usize].remove(&bl);
                    continue;
                }
            };
            let (maker, maker_oid, avail) = {
                let m = &self.slots[head as usize];
                (m.owner, self.oid_of(head), m.qty)
            };
            let traded = qty.min(avail);
            fills.push(Fill {
                taker: owner,
                maker,
                maker_oid,
                level: bl,
                qty: traded,
                taker_side: side as u8,
            });
            self.levels[k].qty = sadd(self.levels[k].qty, -traded);
            self.last_price = bl as i64;
            if traded == avail {
                self.unlink(opp as u8, bl, head);
                let s = &mut self.slots[head as usize];
                s.qty = 0;
                s.generation = s.generation.wrapping_add(1) % (GEN_MOD as u32);
                self.free.push(Reverse(head));
                self.resting -= 1;
            } else {
                self.slots[head as usize].qty = avail - traded;
            }
            qty -= traded;
        }
        if qty == 0 || ioc {
            return Ok(PlaceOutcome { oid: None, fills });
        }
        let slot = match self.free.pop() {
            Some(Reverse(s)) => s,
            // Full book: take what crossed, rest nothing — the IOC answer,
            // because it is the only total one.
            None => return Ok(PlaceOutcome { oid: None, fills }),
        };
        {
            let s = &mut self.slots[slot as usize];
            s.owner = owner;
            s.qty = qty;
            s.side = side as u8;
            s.level = level;
        }
        self.link_tail(side as u8, level, slot);
        let k = Self::level_idx(side as u8, level, self.n_levels);
        self.levels[k].qty = sadd(self.levels[k].qty, qty);
        self.resting += 1;
        Ok(PlaceOutcome {
            oid: Some(self.oid_of(slot)),
            fills,
        })
    }

    /// Remove a resting order. Returns the cancelled quantity, or a reason:
    /// unknown/filled/stale ids (`UnknownOrder`), a live order of somebody
    /// else (`NotOwner`). Mirrors `torihiki.book/cancel!`, which compares
    /// owners and answers 0 for all of these; stage 1 names the reason.
    pub fn cancel(&mut self, oid: i64, owner: i64) -> Result<i64, Rejection> {
        let (slot, recorded) = self.resolve(oid)?;
        if recorded != owner {
            return Err(Rejection::NotOwner);
        }
        let (side, level, q) = {
            let s = &self.slots[slot as usize];
            (s.side, s.level, s.qty)
        };
        self.unlink(side, level, slot);
        {
            let s = &mut self.slots[slot as usize];
            s.qty = 0;
            s.generation = s.generation.wrapping_add(1) % (GEN_MOD as u32);
        }
        let k = Self::level_idx(side, level, self.n_levels);
        self.levels[k].qty = sadd(self.levels[k].qty, -q);
        self.free.push(Reverse(slot));
        self.resting -= 1;
        Ok(q)
    }

    /// Every resting order queue in time order, for hashing and tests.
    /// Sides ascending; levels best-first (bid descending, ask ascending).
    pub fn ordered_levels(&self, side: Side) -> Vec<OccupiedLevel> {
        let mut levels: Vec<u32> = self.occupied[side as usize].iter().copied().collect();
        match side {
            Side::Bid => levels.sort_by(|a, b| b.cmp(a)),
            Side::Ask => levels.sort(),
        }
        levels
            .into_iter()
            .map(|l| {
                let k = Self::level_idx(side as u8, l, self.n_levels);
                let mut queue = Vec::new();
                let mut cur = self.levels[k].head;
                while let Some(slot) = cur {
                    let s = &self.slots[slot as usize];
                    queue.push(RestingOrder {
                        slot,
                        owner: s.owner,
                        qty: s.qty,
                        generation: s.generation,
                    });
                    cur = s.next;
                }
                OccupiedLevel { level: l, queue }
            })
            .collect()
    }

    /// No crossed book: best bid strictly below best ask whenever both sides
    /// rest. Used by property tests.
    pub fn is_crossed(&self) -> bool {
        let bb = self.best(Side::Bid);
        let ba = self.best(Side::Ask);
        bb >= 0 && ba >= 0 && bb >= ba
    }
}

/// One resting order as reported by [`Book::ordered_levels`]: its slot, owner,
/// quantity, and slot generation — everything a restore needs to rebuild
/// cancellability exactly.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct RestingOrder {
    pub slot: u32,
    pub owner: i64,
    pub qty: i64,
    pub generation: u32,
}

/// One occupied level with its FIFO queue in time order.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct OccupiedLevel {
    pub level: u32,
    pub queue: Vec<RestingOrder>,
}

/// The result of submitting an order to a book.
#[derive(Debug, PartialEq, Eq)]
pub struct PlaceOutcome {
    /// Resting order id, or `None` when nothing rested (filled, IOC
    /// remainder, post-only refusal, full book). Mirrors `place!`'s -1.
    pub oid: Option<i64>,
    pub fills: Vec<Fill>,
}
