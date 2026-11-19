//! Domain constants, order types, and the validation doors.
//!
//! Every rule here mirrors the oracle:
//!
//! - `I53_MAX` / `I53_MIN`: `torihiki.fixed/i53-max` (`2^53 - 1`). The whole
//!   engine stores integers inside this domain so two runtimes agree bit for
//!   bit. A value outside it is refused at the door, never stored.
//! - `NewOrder` shape checks: `torihiki.api/validate-order-shape`
//!   (`:bad-side`, `:bad-quantity`, `:bad-price-level`, `:bad-amount`,
//!   `:bad-flags`, in that order).
//! - `GEN_MOD` order ids: `slot * gen-mod + generation`
//!   (`torihiki.book/oid-of`, `gen-mod = 2^20`).
//! - Flags: `flag-ioc = 1`, `flag-post-only = 2`, `flag-reduce-only = 4`
//!   (`torihiki.book`). The book ignores reduce-only; clamping it against a
//!   position is the clearinghouse's job, which is out of scope for stage 1.

/// Largest storable integer: `2^53 - 1` (`torihiki.fixed/i53-max`).
pub const I53_MAX: i64 = 9_007_199_254_740_991;
/// Smallest storable integer: `-(2^53 - 1)`.
pub const I53_MIN: i64 = -9_007_199_254_740_991;
/// Order-id generation modulus: `2^20` (`torihiki.book/gen-mod`).
pub const GEN_MOD: i64 = 1_048_576;

/// Market side. Discriminants match the oracle: bid = 0, ask = 1.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(u8)]
pub enum Side {
    Bid = 0,
    Ask = 1,
}

impl Side {
    pub fn opposite(self) -> Side {
        match self {
            Side::Bid => Side::Ask,
            Side::Ask => Side::Bid,
        }
    }

    pub fn from_u8(v: u8) -> Option<Side> {
        match v {
            0 => Some(Side::Bid),
            1 => Some(Side::Ask),
            _ => None,
        }
    }
}

/// Order behaviour flags. Bit values mirror `torihiki.book`.
pub mod order_flags {
    /// Cancel whatever does not fill immediately.
    pub const IOC: u8 = 1;
    /// Reject if the order would take liquidity.
    pub const POST_ONLY: u8 = 2;
    /// Ignored by the book (clearinghouse concern, out of scope for stage 1).
    pub const REDUCE_ONLY: u8 = 4;
    /// All defined flag bits.
    pub const ALL: u8 = IOC | POST_ONLY | REDUCE_ONLY;
}

/// A validated-or-not new-order request, in storable integers.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct NewOrder {
    pub market: u64,
    pub account: i64,
    pub side: u8,
    pub level: i64,
    pub qty: i64,
    pub flags: u8,
}

/// One fill, mirroring `torihiki.book/fills` map keys.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Fill {
    /// The account that submitted the taking order.
    pub taker: i64,
    /// The account whose resting order was consumed.
    pub maker: i64,
    /// Resting order id consumed (`slot * GEN_MOD + generation`).
    pub maker_oid: i64,
    /// Price level the fill printed at: always the maker's level.
    pub level: u32,
    /// Quantity transferred.
    pub qty: i64,
    /// Side of the taker: 0 = bid, 1 = ask.
    pub taker_side: u8,
}

/// Rejection reasons. Names mirror `torihiki.api/reasons` keywords so parity
/// fixtures can compare them directly. Reasons that exist only in stage 1
/// (finer-grained than the oracle's silent no-ops) are marked.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Rejection {
    UnknownMarket,
    BadAccount,
    BadSide,
    BadQuantity,
    BadPriceLevel,
    BadFlags,
    /// `level * qty` would leave the i53 domain (oracle checks `qty >
    /// `quot i53-max level`` — division, so the check itself cannot overflow).
    BadAmount,
    /// A resting-only order on a full book that cannot cross.
    BookFull,
    /// Malformed order id on cancel (not a natural integer in the domain).
    MissingField,
    /// No live order under this id: unknown, already filled, already
    /// cancelled, or a stale id from a reused slot. The oracle answers all of
    /// these as a silent no-op (`cancel!` returns 0); stage 1 reports it
    /// explicitly. Same state effect (nothing changes), louder surfacing.
    UnknownOrder,
    /// A live order exists under this id but belongs to another account. The
    /// oracle no-ops here too; stage 1 rejects explicitly.
    NotOwner,
    /// Ed25519 signature did not verify over the canonical payload.
    BadSignature,
}

impl Rejection {
    /// The oracle keyword, for fixture comparison.
    pub fn keyword(self) -> &'static str {
        match self {
            Rejection::UnknownMarket => "unknown-market",
            Rejection::BadAccount => "bad-account",
            Rejection::BadSide => "bad-side",
            Rejection::BadQuantity => "bad-quantity",
            Rejection::BadPriceLevel => "bad-price-level",
            Rejection::BadFlags => "bad-flags",
            Rejection::BadAmount => "bad-amount",
            Rejection::BookFull => "book-full",
            Rejection::MissingField => "missing-field",
            // Stage-1-only refinements of the oracle's silent cancel no-op.
            Rejection::UnknownOrder => "unknown-order",
            Rejection::NotOwner => "not-owner",
            Rejection::BadSignature => "bad-signature",
        }
    }
}

/// Whether an account id is slab-storable (`torihiki.auth/account-ok?`):
/// any integer inside the i53 domain, negatives included (the backstop
/// vault is account -1).
pub fn account_ok(id: i64) -> bool {
    (I53_MIN..=I53_MAX).contains(&id)
}

/// The shared order-shape door (`torihiki.api/validate-order-shape`).
/// Checks run in oracle order: side, quantity, price level, notional, flags.
pub fn validate_order_shape(
    side: i64,
    level: i64,
    qty: i64,
    flags: i64,
    n_levels: u32,
) -> Option<Rejection> {
    if side != 0 && side != 1 {
        return Some(Rejection::BadSide);
    }
    if !(1..=I53_MAX).contains(&qty) {
        return Some(Rejection::BadQuantity);
    }
    if level < 0 || level >= n_levels as i64 {
        return Some(Rejection::BadPriceLevel);
    }
    // Division, not multiplication: `level * qty` may itself overflow.
    if level > 0 && qty > I53_MAX / level {
        return Some(Rejection::BadAmount);
    }
    if flags < 0 || flags > order_flags::ALL as i64 {
        return Some(Rejection::BadFlags);
    }
    None
}

/// Saturating add mirroring `torihiki.fixed/acc` (`sadd`): clamps to the i53
/// domain instead of overflowing. Used for level totals, which accumulate
/// across makers.
pub fn sadd(v: i64, delta: i64) -> i64 {
    v.saturating_add(delta).clamp(I53_MIN, I53_MAX)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn i53_bounds_match_oracle() {
        assert_eq!(I53_MAX, 9_007_199_254_740_991);
        assert_eq!(I53_MAX, (1i64 << 53) - 1);
    }

    #[test]
    fn shape_door_order_matches_oracle() {
        // side first
        assert_eq!(
            validate_order_shape(2, 0, 0, 0, 64),
            Some(Rejection::BadSide)
        );
        // then quantity
        assert_eq!(
            validate_order_shape(0, 5, 0, 0, 64),
            Some(Rejection::BadQuantity)
        );
        // then price level
        assert_eq!(
            validate_order_shape(0, 64, 5, 0, 64),
            Some(Rejection::BadPriceLevel)
        );
        // then notional (bad-amount), via division
        assert_eq!(
            validate_order_shape(0, 1_000_000, I53_MAX, 0, 2_000_000),
            Some(Rejection::BadAmount)
        );
        // then flags
        assert_eq!(
            validate_order_shape(0, 5, 5, 8, 64),
            Some(Rejection::BadFlags)
        );
        assert_eq!(validate_order_shape(0, 5, 5, 7, 64), None);
    }

    #[test]
    fn bad_amount_check_cannot_overflow() {
        // level * qty overflows i64; the door must still refuse, not panic.
        let level = 1_048_575i64;
        let qty = I53_MAX;
        assert_eq!(
            validate_order_shape(0, level, qty, 0, 2_000_000),
            Some(Rejection::BadAmount)
        );
    }

    #[test]
    fn sadd_saturates_like_oracle() {
        assert_eq!(sadd(I53_MAX, 1), I53_MAX);
        assert_eq!(sadd(I53_MIN, -1), I53_MIN);
        assert_eq!(sadd(10, -3), 7);
    }
}
