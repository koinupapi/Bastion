# Patch 1 — RC2

Build validation for commits e3004d0 and b98bc32.

Verify compilation, unit tests, signed APK assembly, and release publishing.

Device retests:
- Search query and scroll after device rotation.
- Search → Details dismisses software keyboard.
- Search → Details → Back preserves query and scroll.
- Search → Discover preserves Discover scroll.
- App background/foreground preserves Search state.

Issues: #86, #87, #89. Do not close until device verification.
