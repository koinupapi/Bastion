# Patch 1 RC5 — Search toolbar follow-up build

Build after correcting the Discover scroll-state lookup key in `16a9c5e`.

Focused on-device check: scroll Discover until its search field is offscreen, verify the toolbar Search action appears, open Search, then Back and verify Discover scroll position remains unchanged.

Media Details rotation was reported passing on RC5; avoid altering that behavior. Refs #87 #86.
