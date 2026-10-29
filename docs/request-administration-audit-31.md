# Issue #31 — Seerr request administration API audit

Source: https://github.com/seerr-team/seerr/blob/develop/seerr-api.yml (reviewed 2026-10-29).
Code baseline: Bastion `main` after #37 merge. These are API/code findings; live-instance compatibility and UI behavior still require testing.

## Already implemented
- GET /request listing via `requestPage` (pagination and a status filter).
- POST /request submission via `submitRequest`.
- GET /request/{id} details via `requestApprovalDetails` and `editableRequestDestination`.
- PUT /request/{id} pending destination edit via `updateRequestDestination`; #37 device-tested.
- POST /request/{id}/{approve|decline} via `changeRequestStatus`.
- POST /request/{id}/retry via `retryRequest`.
- DELETE /request/{id} via `removeRequest`.

## Candidate gaps to verify
1. GET /request supports status filters: all, approved, available, pending, processing, unavailable, failed, deleted, completed. Compare all with the current UI filter list and API mapping; avoid adding redundant filters.
2. GET /request also supports `requestedBy`, `mediaType`, `sort` (added/modified), `sortDirection` (asc/desc). Current `requestPage(config,take,skip,filter)` exposes none of these.
3. GET /request/count exposes total, movie, tv, pending, approved, declined, processing, available, completed. No dedicated client function found.
4. PUT /request/{id} accepts `userId` for reassignment of a pending request. The current editor intentionally edits destination only. Verify real usefulness, authorization, and side effects before offering reassignment.
5. Review TV-season edit options separately; #37 preserves existing seasons, but does not offer changing requested seasons. Avoid unintended changes to already submitted requests.

## Proposed implementation order
- First: richer admin request-list filtering/sorting and request counts (read-only, low risk).
- Next: assess whether request reassignment or season editing solves a real admin need. Keep destructive operations unchanged.
- Verify on the user's Seerr deployment, build a test APK, and merge only after explicit device approval.

Do not infer that all API endpoints are supported by the user's installed Seerr version. Keep UI redesign outside #31.
