(require '["fs" :as fs])
(require '[torihiki.state :as st] '[torihiki.book :as bk] '[torihiki.snapshot :as snap])
(load-string (fs/readFileSync "evidence/fuzz-seeded.cljc" "utf8"))
(fuzz-seeded/run)
