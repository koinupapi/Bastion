# Patch 1 RC5 — persistent Search verification build

Trigger build after `ffa0965`, which makes the Seerr Discover toolbar Search action always available, independently of scroll state.

Device test: open Discover at top and after scrolling down, confirm toolbar Search is visible; open Search, then Back, confirm Discover scroll position preserved.

Refs #87.
