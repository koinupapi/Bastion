# Bastion Phase 1 — Release Candidate 1

Branch: `feature/seerr-discover-batch-5`

This is a **test candidate**, not a verified fix or a merge into develop/main.
All implementation commits and their original `Refs #...` / `Fixes #...` messages remain intact.
This checkpoint intentionally uses `Refs` rather than claiming untested issues are fixed.

## Test on Android
- Launch Bastion, open Seerr Discover, switch tabs, and rotate portrait/landscape/back; confirm the selected service, destination and scroll position remain.
- Scroll Discover until its inline search bar is offscreen; verify the toolbar search icon appears without jitter. Scroll back to reveal the inline field and verify the toolbar icon hides.
- From a scrolled Discover feed, tap toolbar Search; ensure the input is visible. Search for a title, open its details, press Back; verify search query/results are still present. Close Search via X and Android Back separately; verify prior Discover scroll returns and a new search begins empty.
- Open Movie Genres > Comedy (or another genre), scroll, open a title, and press Back; confirm return to the same genre, page, filter and scroll position. Repeat with related titles.
- Browse Trending and independently change All/Movies/TV and Today/This Week; verify each control retains its value, results update, and pagination resets to page 1.
- Navigate through multi-page browse results; verify Next/Previous and changed filters reset scroll appropriately, without blank or duplicated pages.
- Apply combinations of language, region, providers, certification, keywords, rating, date and TV filters; rotate and revisit; verify applied selections persist and results match.
- Scroll movie and TV genre carousels horizontally, visit a genre or title, and return; confirm carousel offsets are preserved.
- Open nested media details, rotate, navigate Back step by step; confirm the full details stack and request actions work.
- Test light/dark themes, slow network, empty results, errors, and Back navigation to the dashboard; report crashes, layout flashes and unexpected state loss.

## Release discipline
Do not close issues until device verification. Do not merge without approval.
Issue references: #28 #58 #59 #77 #82 #85 #86.
