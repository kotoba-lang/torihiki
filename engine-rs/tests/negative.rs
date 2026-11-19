//! Negative controls: every rejection leaves the state hash unchanged.
//!
//! Each case snapshots the state hash, applies the hostile input, asserts
//! the exact rejection reason, and asserts the hash is identical afterwards.
//! Reasons mirror `torihiki.api/reasons` keywords (see
//! `engine-rs/evidence/oracle-runs.md` (raw probes in `evidence/doors.json`) for the oracle's own verdicts on the
//! same inputs, produced by running `torihiki.api/order-tx-shape` and
//! `cancel-tx-shape`).

use torihiki_engine::auth::{sign_cancel, sign_order};
use torihiki_engine::domain::{order_flags, NewOrder, Rejection, I53_MAX, I53_MIN};
use torihiki_engine::engine::{ApplyOutcome, Engine, MarketConfig};

fn engine() -> Engine {
    let mut e = Engine::new();
    e.add_market(
        1,
        MarketConfig {
            n_levels: 64,
            cap: 32,
        },
    )
    .unwrap();
    e
}

fn order(market: u64, account: i64, side: u8, level: i64, qty: i64, flags: u8) -> NewOrder {
    NewOrder {
        market,
        account,
        side,
        level,
        qty,
        flags,
    }
}

/// Assert `apply` rejects with `reason` and the state hash does not move.
fn assert_rejected_unchanged(
    e: &mut Engine,
    apply: impl FnOnce(&mut Engine) -> ApplyOutcome,
    reason: Rejection,
) {
    let before = e.state_hash_hex();
    let out = apply(e);
    assert_eq!(out, ApplyOutcome::Rejected { reason }, "reason");
    assert_eq!(
        e.state_hash_hex(),
        before,
        "rejection must leave state unchanged"
    );
}

#[test]
fn unknown_market_rejected() {
    let mut e = engine();
    assert_rejected_unchanged(
        &mut e,
        |e| e.apply_order(order(9, 1, 0, 10, 5, 0)),
        Rejection::UnknownMarket,
    );
    assert_rejected_unchanged(
        &mut e,
        |e| e.apply_cancel(9, 0, 1),
        Rejection::UnknownMarket,
    );
}

#[test]
fn bad_account_rejected() {
    let mut e = engine();
    // Fractional ids cannot exist in Rust; the out-of-domain integers are
    // the hostile inputs here (oracle: `account-ok?`).
    for bad in [I53_MAX + 1, I53_MIN - 1] {
        assert_rejected_unchanged(
            &mut e,
            |e| e.apply_order(order(1, bad, 0, 10, 5, 0)),
            Rejection::BadAccount,
        );
        assert_rejected_unchanged(&mut e, |e| e.apply_cancel(1, 0, bad), Rejection::BadAccount);
    }
}

#[test]
fn shape_doors_reject_in_oracle_order() {
    let mut e = engine();
    // side before quantity before level before notional before flags
    // (`torihiki.api/validate-order-shape`): each row trips exactly one door.
    let cases = [
        (order(1, 1, 2, 10, 5, 0), Rejection::BadSide),
        (order(1, 1, 0, 10, 0, 0), Rejection::BadQuantity),
        (order(1, 1, 0, 10, -3, 0), Rejection::BadQuantity),
        (order(1, 1, 0, 10, I53_MAX + 1, 0), Rejection::BadQuantity),
        (order(1, 1, 0, 64, 5, 0), Rejection::BadPriceLevel),
        (order(1, 1, 0, -1, 5, 0), Rejection::BadPriceLevel),
        // level * qty escapes i53: the door divides, it never multiplies.
        (order(1, 1, 0, 32, 281474976710657, 0), Rejection::BadAmount),
        (order(1, 1, 0, 10, 5, 8), Rejection::BadFlags),
    ];
    for (o, reason) in cases {
        assert_rejected_unchanged(&mut e, |e| e.apply_order(o), reason);
    }
}

#[test]
fn zero_level_rests() {
    // Level 0 is a valid resting level (the oracle's funding walk handles it
    // explicitly); only a crossing check decides.
    let mut e = engine();
    match e.apply_order(order(1, 1, 0, 0, 5, 0)) {
        ApplyOutcome::Applied { oid, .. } => assert!(oid.is_some()),
        other => panic!("level 0 must rest, got {:?}", other),
    }
}

#[test]
fn double_cancel_rejected() {
    let mut e = engine();
    let oid = match e.apply_order(order(1, 1, 0, 10, 5, 0)) {
        ApplyOutcome::Applied { oid: Some(oid), .. } => oid,
        other => panic!("setup failed: {:?}", other),
    };
    assert_eq!(
        e.apply_cancel(1, oid, 1),
        ApplyOutcome::Cancelled { qty: 5 }
    );
    assert_rejected_unchanged(
        &mut e,
        |e| e.apply_cancel(1, oid, 1),
        Rejection::UnknownOrder,
    );
}

#[test]
fn cancel_unknown_and_filled_rejected() {
    let mut e = engine();
    // Never existed.
    assert_rejected_unchanged(
        &mut e,
        |e| e.apply_cancel(1, 41, 1),
        Rejection::UnknownOrder,
    );
    // Filled orders are gone: fill one completely, then cancel its id.
    let _ = e.apply_order(order(1, 1, 1, 10, 5, 0));
    match e.apply_order(order(1, 2, 0, 10, 5, 0)) {
        ApplyOutcome::Applied { oid: None, fills } => assert_eq!(fills.len(), 1),
        other => panic!("setup failed: {:?}", other),
    }
    // The maker's oid was slot 0 gen 0 = 0.
    assert_rejected_unchanged(&mut e, |e| e.apply_cancel(1, 0, 1), Rejection::UnknownOrder);
}

#[test]
fn cancel_wrong_owner_rejected() {
    let mut e = engine();
    let oid = match e.apply_order(order(1, 20, 0, 10, 5, 0)) {
        ApplyOutcome::Applied { oid: Some(oid), .. } => oid,
        other => panic!("setup failed: {:?}", other),
    };
    assert_rejected_unchanged(&mut e, |e| e.apply_cancel(1, oid, 99), Rejection::NotOwner);
    // The order is still there for its owner.
    assert_eq!(
        e.apply_cancel(1, oid, 20),
        ApplyOutcome::Cancelled { qty: 5 }
    );
}

#[test]
fn cancel_malformed_oid_rejected() {
    let mut e = engine();
    for bad in [-1i64, I53_MAX + 1] {
        assert_rejected_unchanged(
            &mut e,
            move |e| e.apply_cancel(1, bad, 1),
            Rejection::MissingField,
        );
    }
}

#[test]
fn book_full_rejects_rest_only() {
    let mut e = Engine::new();
    e.add_market(
        1,
        MarketConfig {
            n_levels: 32,
            cap: 32,
        },
    )
    .unwrap();
    for _ in 0..32 {
        match e.apply_order(order(1, 1, 0, 5, 1, 0)) {
            ApplyOutcome::Applied { oid: Some(_), .. } => {}
            other => panic!("setup failed: {:?}", other),
        }
    }
    // Non-crossing on a full book: refused with its reason (oracle
    // `validate*` `:book-full`; the book itself would rest nothing).
    assert_rejected_unchanged(
        &mut e,
        |e| e.apply_order(order(1, 2, 1, 6, 3, 0)),
        Rejection::BookFull,
    );
    // Crossing on a full book still trades (frees slots as makers fill).
    match e.apply_order(order(1, 2, 1, 5, 2, 0)) {
        ApplyOutcome::Applied { oid: None, fills } => assert_eq!(fills.len(), 2),
        other => panic!("crossing order on full book must fill, got {:?}", other),
    }
}

#[test]
fn invalid_signature_rejected_before_matching() {
    let mut e = engine();
    let seed = [7u8; 32];
    let other = [9u8; 32];
    // Rest a maker so a forged taker WOULD fill if it got through.
    let _ = e.apply_order(order(1, 1, 1, 10, 5, 0));
    // Tampered qty after signing.
    let mut bad = sign_order(&seed, order(1, 2, 0, 10, 5, 0));
    bad.order.qty = 6;
    assert_rejected_unchanged(
        &mut e,
        |e| e.apply_signed_order(&bad),
        Rejection::BadSignature,
    );
    // Right signature bytes, wrong key.
    let mut wrong_key = sign_order(&seed, order(1, 2, 0, 10, 5, 0));
    wrong_key.pubkey = torihiki_engine::auth::sign_order(&other, order(1, 2, 0, 10, 5, 0)).pubkey;
    assert_rejected_unchanged(
        &mut e,
        |e| e.apply_signed_order(&wrong_key),
        Rejection::BadSignature,
    );
    // A valid signature applies normally.
    let replay = sign_order(&seed, order(1, 2, 0, 10, 5, 0));
    let before = e.state_hash_hex();
    assert_eq!(
        e.apply_signed_order(&replay),
        ApplyOutcome::Applied {
            oid: None,
            fills: vec![torihiki_engine::Fill {
                taker: 2,
                maker: 1,
                maker_oid: 0,
                level: 10,
                qty: 5,
                taker_side: 0
            }]
        },
        "valid signature applies"
    );
    assert_ne!(e.state_hash_hex(), before);
    // Signed cancel with a bad signature never touches the book.
    let mut bad_cancel = sign_cancel(&seed, 1, 0, 2);
    bad_cancel.oid = 999;
    assert_rejected_unchanged(
        &mut e,
        |e| e.apply_signed_cancel(&bad_cancel),
        Rejection::BadSignature,
    );
}

#[test]
fn post_only_crossing_rests_nothing_without_rejection() {
    // Mirrors the oracle: a post-only taker is refused outright at the book
    // (`place!` returns -1 with no fills), not via a rejection reason.
    let mut e = engine();
    let _ = e.apply_order(order(1, 1, 1, 10, 3, 0));
    let before = e.state_hash_hex();
    match e.apply_order(order(1, 2, 0, 10, 5, order_flags::POST_ONLY)) {
        ApplyOutcome::Applied { oid: None, fills } => assert!(fills.is_empty()),
        other => panic!("post-only crossing must rest nothing, got {:?}", other),
    }
    // The refused remainder is not a rejection either: nothing rested, but
    // the book is untouched either way.
    assert_eq!(e.state_hash_hex(), before);
}

#[test]
fn duplicate_market_rejected() {
    let mut e = Engine::new();
    e.add_market(1, MarketConfig::default()).unwrap();
    assert_eq!(
        e.add_market(1, MarketConfig::default()),
        Err(Rejection::UnknownMarket)
    );
}

#[test]
fn signed_negative_account_applies() {
    // The oracle's backstop vault is account -1: an ordinary storable id
    // (`account-ok?` spans the whole i53 domain, negatives included), and
    // the unsigned apply path accepts it. The signed path must too.
    let mut e = engine();
    let seed = [7u8; 32];
    let signed = sign_order(&seed, order(1, -1, 0, 10, 5, 0));
    match e.apply_signed_order(&signed) {
        ApplyOutcome::Applied { oid: Some(_), fills } => assert!(fills.is_empty()),
        other => panic!("signed order for account -1 must apply, got {:?}", other),
    }
}

#[test]
fn signed_cancel_negative_account_applies() {
    let mut e = engine();
    let seed = [7u8; 32];
    // Rest via the unsigned path (account -1 is storable), cancel signed.
    let oid = match e.apply_order(order(1, -1, 0, 10, 5, 0)) {
        ApplyOutcome::Applied { oid: Some(oid), .. } => oid,
        other => panic!("setup failed: {:?}", other),
    };
    let signed = sign_cancel(&seed, 1, oid, -1);
    assert_eq!(
        e.apply_signed_cancel(&signed),
        ApplyOutcome::Cancelled { qty: 5 }
    );
}

#[test]
fn book_rejects_nonpositive_qty_as_bad_quantity() {
    // Direct `Book::place` caller passing qty 0 must get the quantity door,
    // not the price-level door (the engine door normally filters this, but
    // the book is public and the parity harness calls it directly).
    use torihiki_engine::book::Book;
    use torihiki_engine::domain::Side;
    let mut b = Book::new(64, 32);
    assert_eq!(
        b.place(Side::Bid, 10, 0, 0, 1).unwrap_err(),
        Rejection::BadQuantity
    );
}
