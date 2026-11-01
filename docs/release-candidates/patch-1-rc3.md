# Patch 1 — RC3

Trigger signed APK validation for Search result state restoration commit `82f7254`.

## Validation
- Compile Kotlin and run JVM unit tests.
- Assemble and verify signed release APK.
- Rotate Search results portrait → landscape → portrait and check query, rows, and scroll position.
- Check no unnecessary search request on rotation when results are already restored.
- Recheck Search → Details → Back and keyboard dismissal.

Related: #86, #87, #89. Keep issues open until device verification.
