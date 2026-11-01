# Patch 1 — RC4

Trigger signed APK build and tests for commits `e00b913` (Discover/Media Details rotation scroll checkpoint restoration) and `9192876` (Search keyboard dismissal on user scroll).

## Device validation
- Discover scroll halfway → rotate both directions; verify scroll restoration.
- Media Details scroll halfway → rotate both directions; verify scroll restoration.
- Search scroll halfway → rotate both directions; verify RC3 passing behavior remains intact.
- Search with keyboard open → scroll results; verify keyboard dismissal.
- Search → Details → Back and nested details; verify previous scroll positions.
- Confirm no unexpected search results refresh on rotation.

Refs #86 #87 #89. Keep issues open pending device verification.
