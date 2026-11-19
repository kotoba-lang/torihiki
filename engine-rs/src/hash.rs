//! Canonical state encoding and hash.
//!
//! The full byte layout is documented in `engine-rs/README.md` ("State
//! encoding"). Summary of the rules, mirroring the oracle's encoding
//! discipline (`torihiki.state/canonical-leaves`, `encode-book`,
//! `enc-int`):
//!
//! - Integers encode as the oracle's `enc-int`: a sign byte (0/1) then seven
//!   big-endian magnitude bytes. Seven bytes carry 56 bits, above the 53 the
//!   domain allows, and the form is identical for every value on every
//!   platform.
//! - Every section is tagged (`enc-tag-*` in the oracle), so two different
//!   states cannot collide by juxtaposition.
//! - Iteration is over sorted market ids and best-first levels — never over
//!   an unordered map. `Engine` holds a `BTreeMap`; level order comes from
//!   the occupied-level set.
//! - Orders encode in time (queue) order with their slot and generation, so
//!   restoring the bytes restores cancellability exactly.
//!
//! The hash is SHA-256, lowercase hex — the same digest the oracle's
//! `flat-root`/`state-root` use. The bytes hashed are NOT the oracle's: this
//! root commits to matching-engine state only (books), not to clearing,
//! nonces, rejections, or governance. Rejections in particular are excluded
//! on purpose: a rejected order must leave the state hash unchanged.

use crate::book::Book;
use crate::domain::Side;
use crate::engine::Engine;
use sha2::{Digest, Sha256};

// Tags mirror `torihiki.state/enc-tag-*` where the section exists there.
const TAG_EXCHANGE: i64 = 1;
const TAG_MARKET: i64 = 2;
const TAG_LEVEL: i64 = 3;
const TAG_ORDER: i64 = 4;

/// One i53 integer as 8 bytes: a sign byte then seven big-endian magnitude
/// bytes (`torihiki.state/enc-int`). Division-based, so it is exact on every
/// platform.
pub fn enc_int(v: i64) -> [u8; 8] {
    let neg = if v < 0 { 1u8 } else { 0u8 };
    // `unsigned_abs` of i64::MIN would overflow the domain, but i64::MIN is
    // outside i53 and never stored; saturate defensively rather than panic.
    let mut a = v.unsigned_abs();
    // Clamp magnitudes above 56 bits: unreachable for stored values.
    if a >> 56 != 0 {
        a = (1u64 << 56) - 1;
    }
    let mut out = [0u8; 8];
    out[0] = neg;
    let mut i = 6usize;
    loop {
        let d = 256u64.pow(i as u32);
        out[7 - i] = (a / d) as u8;
        a %= d;
        if i == 0 {
            break;
        }
        i -= 1;
    }
    out
}

fn enc_ints(out: &mut Vec<u8>, xs: &[i64]) {
    for &x in xs {
        out.extend_from_slice(&enc_int(x));
    }
}

fn encode_book(out: &mut Vec<u8>, market: u64, book: &Book) {
    // Market header: which market, how wide its ladder is. The cap is
    // implied by the bytes that follow (slots are never named empty).
    enc_ints(out, &[TAG_MARKET, market as i64, book.n_levels() as i64]);
    for side in [Side::Bid, Side::Ask] {
        let levels = book.ordered_levels(side);
        enc_ints(out, &[TAG_MARKET, side as i64, levels.len() as i64]);
        for lvl in &levels {
            let lvl_qty: i64 = book.level_qty(side, lvl.level);
            enc_ints(
                out,
                &[
                    TAG_LEVEL,
                    side as i64,
                    lvl.level as i64,
                    lvl_qty,
                    lvl.queue.len() as i64,
                ],
            );
            for o in &lvl.queue {
                enc_ints(
                    out,
                    &[
                        TAG_ORDER,
                        o.slot as i64,
                        o.generation as i64,
                        o.owner,
                        o.qty,
                    ],
                );
            }
        }
    }
}

/// The exact byte sequence the state hash digests.
pub fn canonical_bytes(engine: &Engine) -> Vec<u8> {
    let mut out = Vec::new();
    let ids = engine.market_ids();
    enc_ints(&mut out, &[TAG_EXCHANGE, ids.len() as i64]);
    for m in ids {
        // `market_ids` comes from the BTreeMap key order: ascending.
        let book = engine.book(m).expect("market listed twice");
        encode_book(&mut out, m, book);
    }
    out
}

/// SHA-256 over [`canonical_bytes`].
pub fn state_hash(engine: &Engine) -> [u8; 32] {
    let mut h = Sha256::new();
    h.update(canonical_bytes(engine));
    h.finalize().into()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn enc_int_matches_oracle_form() {
        // enc-int: sign byte then seven big-endian magnitude bytes.
        assert_eq!(enc_int(0), [0, 0, 0, 0, 0, 0, 0, 0]);
        assert_eq!(enc_int(1), [0, 0, 0, 0, 0, 0, 0, 1]);
        assert_eq!(enc_int(-1), [1, 0, 0, 0, 0, 0, 0, 1]);
        assert_eq!(enc_int(256), [0, 0, 0, 0, 0, 0, 1, 0]);
        assert_eq!(
            enc_int(9_007_199_254_740_991),
            [0, 0x1F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF]
        );
    }
}
