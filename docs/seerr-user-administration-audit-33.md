# Issue #33 — Seerr user administration audit

Source: official Seerr OpenAPI specification at https://github.com/seerr-team/seerr/blob/develop/seerr-api.yml (reviewed 2026-10-29).
Baseline: Bastion main after PR #40 merged.

## Existing implementation
- `SeerrClient.createLocalUser` creates a local account and sets its password.
- `SeerrClient.currentIssueUser` fetches the authenticated user's ID and permissions.
- No complete admin user list, Plex import, per-user quota editor, or user-permissions editor found in the client.

## Documented API surface
- `GET /user`: paginated user list.
- `POST /user`: create user; existing Bastion local-user workflow uses this.
- `PUT /user`: batch updates.
- `POST /user/import-from-plex` and `POST /user/import-from-jellyfin`: imports.
- `GET /user/{userId}`, `PUT /user/{userId}`, `DELETE /user/{userId}`: account details and management.
- `GET /user/{userId}/requests`: request history.
- `GET /user/{userId}/quota`: quota usage.
- `GET/POST /user/{userId}/settings/main`: per-user settings, including quota limits.
- `GET/POST /user/{userId}/settings/permissions`: permissions.
- `GET /settings/plex/users`: Plex import candidates (verify availability and response shape).

## Recommended implementation
1. Read-only user directory, pagination and user detail inspection.
2. Plex import selection after confirming API shape and permissions.
3. Per-user quotas and permissions with explicit review/confirmation before saving.
4. Account edits, request history, and guarded deletion only after device validation.

Respect MANAGE_USERS authorization. Never log passwords, tokens or API keys. Avoid exposing read-only Plex tokens. Test deployed Seerr API compatibility before modifying real accounts. Keep the PR draft until the user tests and approves merging.
