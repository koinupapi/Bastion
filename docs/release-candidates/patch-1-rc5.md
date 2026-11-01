# Patch 1 — RC5

Build candidate after changes to preserve Seerr Media Details data across rotation and show a persistent Search toolbar action on scrolled Discover.

## On-device checks
- Media Details scroll near bottom → rotate landscape and portrait; check for top flashes and scroll resets.
- Discover scroll down → toolbar Search → search → Back; check Discover scroll restoration.
- Search rotation and results restoration regression.
- Search → Details → Back, nested details, and keyboard dismissal regression.

Keep issues open until device verification. Refs #86 #87 #89.
