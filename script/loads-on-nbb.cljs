;; Every namespace, required under nbb.
;;
;;   npx nbb -cp "src:$(clojure -Spath)" script/loads-on-nbb.cljs
;;
;; ## Why this exists
;;
;; The JVM tests are green and the node does not start. That is not a
;; hypothetical — it happened three times in one day, and each time the tests
;; had nothing to say because the tests do not run on the runtime that broke:
;;
;;   `bigint` in `torihiki.evm` — Clojure's, absent in ClojureScript. 305 JVM
;;   tests passed; `nbb` could not resolve the symbol.
;;
;;   `(rem a b)` on a BigInt — compiles, then throws `Cannot convert a BigInt
;;   value to a number` at runtime. The JVM took the other branch.
;;
;;   A bare `"` inside a docstring — the reader ends the string early and reads
;;   the prose as code. `bring is not ISeqable`, from a paragraph about testing.
;;   The JVM reader had already accepted the same file in an earlier form.
;;
;; A `.cljc` file that only loads on one runtime is a `.cljc` file in name.
;; This is the smallest thing that says so, and it is cheap: requiring is not
;; running, so it costs a second and catches the whole class — unresolvable
;; symbols, reader damage, and anything a macro expands into on the other side.
;;
;; It does NOT catch behaviour that differs between the runtimes. That is what
;; `torihiki.parity` is for. This one only insists that both can read the file.
(ns loads-on-nbb
  (:require [torihiki.address]
            [torihiki.api]
            [torihiki.auth]
            [torihiki.book]
            [torihiki.clearing]
            [torihiki.commit]
            [torihiki.evm]
            [torihiki.evm.interp]
            [torihiki.fixed]
            [torihiki.funding]
            [torihiki.keccak]
            [torihiki.liquidation]
            [torihiki.log]
            [torihiki.mark]
            [torihiki.oracle]
            [torihiki.slab]
            [torihiki.snapshot]
            [torihiki.state]
            [torihiki.thorchain]
            [torihiki.trigger]))

(println "LOADS-ON-NBB: pass — every namespace read and required on ClojureScript")
