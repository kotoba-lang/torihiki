//! Order authentication boundary: Ed25519 signatures, separable from matching.
//!
//! Mirrors the oracle's posture (`torihiki.auth/check`, `signing-payload`):
//! the signature covers a canonical field-per-line payload with a domain
//! separator, so two different orders cannot collide by juxtaposition.
//!
//! Separable by construction: [`SignedOrder::verify`] checks the signature
//! and returns the order; [`crate::engine::Engine::apply_order`] applies a
//! pre-verified order without touching cryptography. A later stage can
//! verify a block's signatures in parallel and replay the verified orders
//! through the single-threaded matcher. The bench measures both paths.
//!
//! Out of scope for stage 1 (documented in `engine-rs/README.md`): nonces
//! and replay protection, key binding (`account-keys`), agent keys, and the
//! chain envelope (`chain-id`). The signed payload carries the account id,
//! so a signature cannot be re-aimed at another account; replay within the
//! engine is the later stage's nonce state to prevent.

use crate::domain::{account_ok, NewOrder, Rejection};
use ed25519_dalek::{Signature, Signer, SigningKey, Verifier, VerifyingKey};

const DOMAIN: &str = "torihiki-eng/v1";

/// Canonical bytes covered by an order signature.
pub fn order_payload(order: &NewOrder) -> Vec<u8> {
    format!(
        "{DOMAIN}\nmarket={}\naccount={}\nside={}\nlevel={}\nqty={}\nflags={}\n",
        order.market, order.account, order.side, order.level, order.qty, order.flags
    )
    .into_bytes()
}

/// Canonical bytes covered by a cancel signature.
pub fn cancel_payload(market: u64, oid: i64, account: i64) -> Vec<u8> {
    format!("{DOMAIN}\ncancel\nmarket={market}\noid={oid}\naccount={account}\n",).into_bytes()
}

/// An order plus its Ed25519 signature and the signer's public key.
pub struct SignedOrder {
    pub order: NewOrder,
    pub pubkey: [u8; 32],
    pub signature: [u8; 64],
}

impl SignedOrder {
    /// Verify the signature. On success returns the order, ready for
    /// [`crate::engine::Engine::apply_order`]. On failure the engine is
    /// untouched — the caller must not apply anything.
    pub fn verify(&self) -> Result<NewOrder, Rejection> {
        if !account_ok(self.order.account) {
            // The payload embeds the account; an un-storable id cannot have
            // been signed for this engine. Refuse before cryptography. The
            // domain is the whole i53 range, negatives included (the oracle's
            // backstop vault is account -1) — matching `Engine::apply_order`.
            return Err(Rejection::BadAccount);
        }
        let key = VerifyingKey::from_bytes(&self.pubkey).map_err(|_| Rejection::BadSignature)?;
        let sig = Signature::from_bytes(&self.signature);
        key.verify(&order_payload(&self.order), &sig)
            .map_err(|_| Rejection::BadSignature)?;
        Ok(self.order)
    }
}

/// A cancel plus its Ed25519 signature and the signer's public key.
pub struct SignedCancel {
    pub market: u64,
    pub oid: i64,
    pub account: i64,
    pub pubkey: [u8; 32],
    pub signature: [u8; 64],
}

impl SignedCancel {
    pub fn verify(&self) -> Result<(u64, i64, i64), Rejection> {
        if !account_ok(self.account) {
            return Err(Rejection::BadAccount);
        }
        let key = VerifyingKey::from_bytes(&self.pubkey).map_err(|_| Rejection::BadSignature)?;
        let sig = Signature::from_bytes(&self.signature);
        key.verify(&cancel_payload(self.market, self.oid, self.account), &sig)
            .map_err(|_| Rejection::BadSignature)?;
        Ok((self.market, self.oid, self.account))
    }
}

/// Test helper: sign an order with a fixed 32-byte seed (no RNG anywhere).
pub fn sign_order(seed: &[u8; 32], order: NewOrder) -> SignedOrder {
    let sk = SigningKey::from_bytes(seed);
    let sig = sk.sign(&order_payload(&order));
    SignedOrder {
        order,
        pubkey: sk.verifying_key().to_bytes(),
        signature: sig.to_bytes(),
    }
}

/// Test helper: sign a cancel with a fixed 32-byte seed.
pub fn sign_cancel(seed: &[u8; 32], market: u64, oid: i64, account: i64) -> SignedCancel {
    let sk = SigningKey::from_bytes(seed);
    let sig = sk.sign(&cancel_payload(market, oid, account));
    SignedCancel {
        market,
        oid,
        account,
        pubkey: sk.verifying_key().to_bytes(),
        signature: sig.to_bytes(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const SEED: [u8; 32] = [7u8; 32];
    const OTHER: [u8; 32] = [9u8; 32];

    fn order() -> NewOrder {
        NewOrder {
            market: 1,
            account: 42,
            side: 0,
            level: 100,
            qty: 5,
            flags: 0,
        }
    }

    #[test]
    fn round_trip_verifies() {
        let s = sign_order(&SEED, order());
        assert_eq!(s.verify().unwrap(), order());
    }

    #[test]
    fn wrong_key_rejected() {
        let mut s = sign_order(&SEED, order());
        s.pubkey = SigningKey::from_bytes(&OTHER).verifying_key().to_bytes();
        assert_eq!(s.verify().unwrap_err(), Rejection::BadSignature);
    }

    #[test]
    fn tampered_order_rejected() {
        let mut s = sign_order(&SEED, order());
        s.order.qty = 6;
        assert_eq!(s.verify().unwrap_err(), Rejection::BadSignature);
    }

    #[test]
    fn cancel_round_trip_verifies() {
        let s = sign_cancel(&SEED, 1, 3, 42);
        assert_eq!(s.verify().unwrap(), (1, 3, 42));
    }
}
