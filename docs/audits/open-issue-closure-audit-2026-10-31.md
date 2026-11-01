# Open-issue closure audit — 2026-10-31

Scope: all 54 issues reported open in `koinupapi/Bastion` at audit time. Compared reported acceptance criteria with existing development history, current `feature/seerr-discover-batch-5` code and October 31 device testing. Do not equate a code change with complete validation.

## Verified complete and missing a closing keyword

- **Fixes #58** — Browse pagination now changes the destination page, which is included in the `BastionScreenDestination.page`/`scrollKey`. This creates a fresh `LazyListState` for each page rather than reusing the prior page's bottom scroll offset. On-device testing on October 31 explicitly confirmed Next/Previous opens at the top and changing filters resets to page 1. Earlier commit references did not provide an explicit closing keyword. This audit commit supplies it.

## Worked on, but keep open

- #15 — Timeout/retry reconciliation implemented and regression tests added, but Seerr downstream Failed status / intermittent server behavior has not been conclusively resolved.
- #23 — Issue CRUD/comment management device-tested; issue explicitly retains broader API coverage and account/permission acceptance criteria.
- #28, #71 — Discover functionality is close, but known studio/network, keyword and navigation gaps remain.
- #51 — Refresh behavior was observed working, but indicator placement and full error lifecycle criteria remain.
- #59, #64, #77, #82, #85, #86 — Navigation/state behavior still under development or awaiting verification.
- #60, #72, #78, #81, #83 — Search/discovery data gaps remain.
- #76, #79, #80, #84 and other UI polish — intentionally deferred.

## Other open issues

Remaining open issues (#1–#6, #8–#9, #11, #14, #20, #22, #27, #29–#30, #32, #34, #42, #44, #46–#47, #49, #52–#53, #62–#63, #65–#70, #74) describe outstanding work, incomplete acceptance criteria, or deferred visual polish; no closing keyword is added for them. The current audit does not claim every historical bug has been reproduced on the latest build.

## Merge policy

Preserve commit history using merge commits (not squash). Use `Refs #N` for partial work and `Fixes #N` only after acceptance criteria are satisfied. GitHub auto-closes when the qualifying commit reaches the repository's default branch. Do not manually close issues as a substitute for the agreed workflow.
