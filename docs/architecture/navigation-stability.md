# Navigation Stability: Atrium-like continuity

Status: Implementation in progress. Do not merge or trigger APK build until reviewed.

## Target behavior

- Discover -> genre -> details -> related details -> Back must reveal the exact prior screen, content, and scroll position.
- Search -> details -> Back retains query and already loaded results without a loading flash.
- Switching primary tabs preserves each tab's independent back stack and scroll state.
- Rotation and rapid Back do not replace an existing destination with an intermediate blank screen.
- Material 3 Expressive styling remains native Compose.

## Source comparison (2026-10-31)

Atrium-Expressive `development`: `app/lib/src/router.dart` uses `StatefulShellRoute.indexedStack` to retain primary navigation branches, a root navigator for service details, and `NoTransitionPage` for service-detail routes.

Bastion `feature/seerr-discover-batch-5`: `MainActivity.kt` drives `BastionNavigationHost` using a computed `BastionScreenDestination`, a destination-specific LazyColumn and rememberSaveable LazyListState. Media details use `seerrMediaStack`, while Discover and browse are selected by conditional destination rendering.

## Implementation stages

1. **Stabilize outgoing destinations**: derive layout and insets from the rendered destination, not global active state. Commits e9fddbea, 1f5bb0b. Cache repeated search results (f808658).
2. **Persistent destination ownership**: move Seerr Discover, browse, and media-detail screen models into destination-owned entries. Each entry has stable ID, scroll state, filters, query, loaded data, and SaveableStateHolder scope. Do not key a destination by mutable filter hash.
3. **True retained underlay**: use a persistent tab host and an overlay back stack for details. Retain underlying destination composition/state while details are shown; avoid building an entirely new Discover subtree on Back.
4. **Transition policy**: begin with no crossfade on detail pop; optionally animate only the top route after ensuring previous frame is already available. Honor reduced-motion settings.
5. **Async loading policy**: cache successful search, browse and details responses by service and request parameters; deduplicate inflight requests. Explicit refresh bypasses cache.
6. **Carousel policy**: central BastionCarousel handles content padding, edge clipping, stable keys, and per-destination scroll state.

## Acceptance tests

- Open Discover, scroll deep, open media, Back 10x: exact same cards and scroll, no blank frame.
- Open genre, scroll, details, related details, Back twice: correct genre position, no refetch spinner.
- Search, open title, Back: exact query/results, no spinner; close search returns to original Discover scroll.
- Repeat across rotation, light/dark, rapid Back, and service/tab switching.
- Verify with screen recording and frame-by-frame inspection on device; static source inspection cannot prove zero dropped frames.

## Constraints

Do not migrate to Flutter. Preserve native Compose / Material 3 Expressive. Do not merge to develop/main or run [build] commit until user authorizes.

## Consolidated APK verification

Candidate includes search scrolling, rotation browse geometry, Requests caching, and browse filter overlay. Device verification pending.
