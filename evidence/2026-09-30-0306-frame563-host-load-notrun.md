# host-load not-run (frame 563)

date: 2026-09-30 03:06 JST
host: /Users/junkawasaki (macOS 26.4), 1 user, up ~2 days
uptime load (1-min / 5-min / 15-min): 26.94 / 19.09 / 15.32 (03:06), earlier 2:24 = 10.71 / 12.06 / 14.88

## Verdict
Host load > 20 (1-min 26.94) and unstable — measurement NOT run this cycle.
The terminal repeatedly returned empty stdout under load (chronic fault),
confirming an overloaded / flaky host. Per the not-run rule (host load > 20),
record load evidence only; no falsify hypothesis was measured.

## Load readings
- 2:24  up 2 days,  4:26  1 user  load 10.71 12.06 14.88
- 3:06  load 26.94 19.09 15.32   (peak > 20)
- 3:06  (re-read) 9.91 10.27 11.61   (volatile)

## Decision
skip measurement; record not-run. Next cycle retries if load stays < 20
and the terminal returns non-empty output.
