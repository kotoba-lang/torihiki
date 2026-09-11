not-run (host load gate)

frame 406 iteration: NOT RUN.
reason: host load averages 43.35 / 42.02 / 45.62 (all > 20 gate at cron start 2026-09-10); also shell execution was unavailable in this session (terminal returned no output), so neither `kbb -M:test` nor `kbb -M:bench` could be launched.

Gate rule (cron task): host load > 20 -> not-run evidence only. No code changes.

Reference: latest completed suite remains frame 401 (JVM 357 tests / 915 assertions 0 failures, JVM_EXIT=0; nbb identical counts, NBB_EXIT=0) — test-jvm-401.out / test-nbb-401.out.
