//! torihiki stage-1 matching engine: a single-threaded, deterministic
//! price-time priority limit order book.
//!
//! Mirrors the matching rules of the existing Clojure-family implementation
//! (`src/torihiki/book.cljk`, `src/torihiki/api.cljk`, `torihiki.fixed` in
//! `lib/kotoba.torihikiCore`). That implementation is the oracle for matching
//! decisions; this crate reimplements the book layer in Rust and documents
//! every deliberate deviation in `engine-rs/README.md`.

pub mod auth;
pub mod book;
pub mod domain;
pub mod engine;
pub mod hash;

pub use domain::{order_flags, Fill, NewOrder, Rejection, Side, I53_MAX, I53_MIN};
pub use engine::{ApplyOutcome, Engine};
