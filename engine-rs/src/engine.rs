//! The matching engine: per-market books behind the validation doors.
//!
//! Mirrors `torihiki.state/apply-tx` for `:order` and `:cancel`, minus
//! everything that is not matching: no margin, no positions, no triggers,
//! no oracle. The validation order matches the oracle (`market!` first,
//! then the account, then the shape, then capacity):
//!
//! - `:unknown-market` when the market does not exist
//!   (`torihiki.api/validate*`, first clause).
//! - `:bad-account` when the signer id is not slab-storable
//!   (`torihiki.auth/account-ok?`).
//! - The order shape door (`torihiki.api/validate-order-shape`).
//! - `:book-full` for a resting-only order on a full book that cannot cross
//!   (`torihiki.api/validate*` `:order` clause: crossing orders are never
//!   refused for capacity).
//!
//! A rejection changes nothing: the state hash before and after are
//! identical. The oracle records rejections in its state root; stage 1
//! instead returns the reason to the caller and commits only to books.

use crate::book::{Book, PlaceOutcome};
use crate::domain::{account_ok, validate_order_shape, Fill, NewOrder, Rejection, Side};
use std::collections::BTreeMap;

/// Per-market configuration. Each market gets its own slot slab, so memory
/// is per market (`torihiki.state/new-exchange`: `book-opts` applies to every
/// book).
#[derive(Clone, Copy, Debug)]
pub struct MarketConfig {
    pub n_levels: u32,
    pub cap: u32,
}

impl Default for MarketConfig {
    fn default() -> Self {
        MarketConfig {
            n_levels: 65_536,
            cap: 1_048_576,
        }
    }
}

/// What applying one order or cancel did.
#[derive(Debug, PartialEq, Eq)]
pub enum ApplyOutcome {
    /// The order or cancel applied. `oid` is the resting order id, or `None`
    /// when nothing rested (filled, IOC remainder, post-only refusal, full
    /// book that took what crossed).
    Applied { oid: Option<i64>, fills: Vec<Fill> },
    /// The cancel removed `qty` from the book.
    Cancelled { qty: i64 },
    /// The request was refused; the engine is unchanged.
    Rejected { reason: Rejection },
}

/// The engine. `BTreeMap` (ordered), never a hash map, in anything that
/// reaches the hash or the output.
#[derive(Debug)]
pub struct Engine {
    books: BTreeMap<u64, Book>,
}

impl Engine {
    pub fn new() -> Engine {
        Engine {
            books: BTreeMap::new(),
        }
    }

    /// List a market. Duplicate ids are refused: two markets sharing an id
    /// is one market that behaves like two (`torihiki.state/new-exchange`).
    pub fn add_market(&mut self, market: u64, cfg: MarketConfig) -> Result<(), Rejection> {
        if self.books.contains_key(&market) {
            return Err(Rejection::UnknownMarket);
        }
        self.books.insert(market, Book::new(cfg.n_levels, cfg.cap));
        Ok(())
    }

    pub fn market_ids(&self) -> Vec<u64> {
        self.books.keys().copied().collect()
    }

    pub fn book(&self, market: u64) -> Option<&Book> {
        self.books.get(&market)
    }

    /// Submit an order. Assumes the signature (if any) already verified —
    /// use [`crate::auth`] to verify first, or `apply_signed_order`.
    pub fn apply_order(&mut self, order: NewOrder) -> ApplyOutcome {
        let Some(book) = self.books.get_mut(&order.market) else {
            return ApplyOutcome::Rejected {
                reason: Rejection::UnknownMarket,
            };
        };
        if !account_ok(order.account) {
            return ApplyOutcome::Rejected {
                reason: Rejection::BadAccount,
            };
        }
        if let Some(reason) = validate_order_shape(
            order.side as i64,
            order.level,
            order.qty,
            order.flags as i64,
            book.n_levels(),
        ) {
            return ApplyOutcome::Rejected { reason };
        }
        let side = match Side::from_u8(order.side) {
            Some(s) => s,
            None => {
                return ApplyOutcome::Rejected {
                    reason: Rejection::BadSide,
                }
            }
        };
        // Capacity, mirroring the oracle: a full book refuses only the order
        // that cannot cross (`torihiki.api/validate*` `:book-full` clause).
        if book.is_full() && !book.crosses(side, order.level) {
            return ApplyOutcome::Rejected {
                reason: Rejection::BookFull,
            };
        }
        match book.place(
            side,
            order.level as u32,
            order.qty,
            order.flags,
            order.account,
        ) {
            Ok(PlaceOutcome { oid, fills }) => ApplyOutcome::Applied { oid, fills },
            Err(reason) => ApplyOutcome::Rejected { reason },
        }
    }

    /// Cancel a resting order. A cancel that names nothing cancellable, or
    /// somebody else's order, is rejected and changes nothing. (The oracle
    /// no-ops these with `cancel!` returning 0; same state effect, louder
    /// surfacing — see `engine-rs/README.md`.)
    pub fn apply_cancel(&mut self, market: u64, oid: i64, account: i64) -> ApplyOutcome {
        let Some(book) = self.books.get_mut(&market) else {
            return ApplyOutcome::Rejected {
                reason: Rejection::UnknownMarket,
            };
        };
        if !account_ok(account) {
            return ApplyOutcome::Rejected {
                reason: Rejection::BadAccount,
            };
        }
        if !(0..=crate::domain::I53_MAX).contains(&oid) {
            return ApplyOutcome::Rejected {
                reason: Rejection::MissingField,
            };
        }
        match book.cancel(oid, account) {
            Ok(qty) => ApplyOutcome::Cancelled { qty },
            Err(reason) => ApplyOutcome::Rejected { reason },
        }
    }

    /// Submit a signed order: verify the Ed25519 signature first, apply only
    /// on success. Verification is separable from matching — a later stage
    /// can verify in parallel and call [`Engine::apply_order`] directly.
    pub fn apply_signed_order(&mut self, signed: &crate::auth::SignedOrder) -> ApplyOutcome {
        match signed.verify() {
            Ok(order) => self.apply_order(order),
            Err(reason) => ApplyOutcome::Rejected { reason },
        }
    }

    /// Submit a signed cancel: verify first, apply only on success.
    pub fn apply_signed_cancel(&mut self, signed: &crate::auth::SignedCancel) -> ApplyOutcome {
        match signed.verify() {
            Ok((market, oid, account)) => self.apply_cancel(market, oid, account),
            Err(reason) => ApplyOutcome::Rejected { reason },
        }
    }

    /// SHA-256 over the canonical state encoding, lowercase hex.
    pub fn state_hash_hex(&self) -> String {
        hex_of(&crate::hash::state_hash(self))
    }

    /// Raw 32-byte state hash.
    pub fn state_hash(&self) -> [u8; 32] {
        crate::hash::state_hash(self)
    }
}

impl Default for Engine {
    fn default() -> Self {
        Engine::new()
    }
}

fn hex_of(bytes: &[u8; 32]) -> String {
    const HEX: &[u8; 16] = b"0123456789abcdef";
    let mut out = String::with_capacity(64);
    for b in bytes {
        out.push(HEX[(b >> 4) as usize] as char);
        out.push(HEX[(b & 0xf) as usize] as char);
    }
    out
}
