# Patch 1 — RC1

Branch: `feature/seerr-discover-batch-5`

Scope: retained Search destination, independent scroll position, Search session identity, navigation regression tests, and isolation of Discover-only loading effects.

This commit requests the existing GitHub Actions signed APK workflow via `[build]`. The workflow runs JVM unit tests before assembling and signing the release APK.

Device acceptance checks:
- Discover scroll position remains unchanged after entering and closing Search.
- Search results retain query and scroll after opening a media detail and returning.
- Search from Browse returns to Browse without changing Browse scroll position.
- Reopening Search starts a new session, rather than restoring a previous query.
- Back button and toolbar close icon agree.

Related issues: Refs #59 #77 #82 #86. Do not close issues until verified.
