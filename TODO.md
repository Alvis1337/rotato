# Rotato — Open Issues & Ideas

## 🔴 Critical / UX-Breaking

### Collections wallpapers not showing up in Library
- Tapping "Add all to rotation" in a Collection downloads files to `rotato_images/` but
  `HomeViewModel._images` is populated once at startup and doesn't observe the filesystem.
- `LaunchedEffect(Unit) { viewModel.refreshFromFeeds() }` was added to `HomeScreen` and
  `homeViewModel.refreshFromFeeds()` is called on Library nav-tab click — but if Compose
  reuses the composable instance (back-stack restore), `LaunchedEffect(Unit)` may not
  re-fire.
- **Proper fix**: make `ImageRepository.getImages()` return a `Flow<List<File>>` backed by
  a `FileObserver` (API 29+) or a polling coroutine so `HomeViewModel._images` updates
  reactively whenever any file is added/removed from `rotato_images/`.


### Source failure handling on swipe screen
When a source is down (network error, rate limit, 0 results, API auth failure), the app
falls through to "no wallpapers found" even if other enabled sources still work.
- `fetchNext()` in `BrainrotViewModel` iterates all sources and returns null only when ALL fail.
  Make each source failure silent — log it, skip that source, keep trying the rest.
- Inline-fetch retry was added (3 attempts × 800 ms backoff) but if the user's **only**
  enabled source is broken, the retries still end in the error screen.
- **Ideal fix**: show a dismissable banner ("Source X unavailable, skipping") rather than
  blocking the whole screen.

### Danbooru free-account tag limit
Free Danbooru accounts support max 2 query tags. Server-side `-id:X` exclusion was removed
(commit `19fa9cd`) and replaced with client-side dedup. However:
- If the user has a paid Danbooru account (Gold/Platinum), server-side exclusion is more
  efficient (fewer repeats). Detect account tier via `/profile.json` and re-enable
  `-id:X` tags conditionally.
- With `random=true` and no server-side exclusion, the same 20-post window can be returned
  repeatedly. Client-side `seenIds` (cap 30) mitigates this but doesn't fully prevent it
  for queries with < 30 matching posts.

---

## 🟡 Usability / Polish

### Source health indicator in Settings > Sources
- Track `lastSuccess: Long` and `lastError: String?` per `LocalSource` in
  `LocalSourcesPreferences`.
- Show a coloured dot (green / amber / red) next to each source in the sources list.
- Tapping a source row shows last error message (e.g. "HTTP 422 – too many tags",
  "Connection timeout", "0 results for query 'xyz'").

### Better "No wallpapers found" screen
- Show *which* sources were tried and *why* each failed (network error vs zero results vs
  all excluded by seen-IDs).
- Surface active filters so the user knows what to relax.
- Quick actions: "Clear filters", "Go to Sources", "Retry now".

### Wallhaven purity filter
- Currently hardcoded: `sfw+sketchy` when NSFW off, `sfw+sketchy+nsfw` when on.
- Add a Wallhaven-specific purity picker (SFW / Sketchy / NSFW checkboxes) in
  `DiscoverSettingsDialog` or per-source settings.
- Store as a bitmask in `LocalSource.extras` or a new `WallhavenPreferences`.

### Collections → Library workflow clarity
Users are confused about the two concepts. Add:
- Empty-state copy in **Library** tab: "Add wallpapers here via the Collections tab or
  the ↓ button on the Discover screen."
- Empty-state copy in **Collections** tab: "Save wallpapers from Discover using →. Then
  tap any saved wallpaper to add it to your rotation Library."
- A one-time tooltip / coachmark on first launch.

---

## 🟢 Nice-to-Have

### History tab — source & tags metadata
- History entries show thumbnail only. Add source badge + first 3 tags under each card.

### Safebooru NSFW flag is a no-op
- Safebooru is SFW-only by definition. Remove the `nsfw` parameter from `fetchSafebooru`
  to avoid dead code confusion.

### Per-source tag defaults
- Let the user set default tags per source (e.g. Danbooru always searches "scenery",
  Wallhaven searches "nature"). Currently `source.tags` does this but it's not surfaced
  clearly in the UI.

### Swipe undo
- If the user accidentally swipes left (skip), there's no way to get the wallpaper back.
  Keep a small "undo" stack (last 1–3 skipped wallpapers) and show an undo snackbar for
  ~5 seconds after a skip.

### Queue size / prefetch settings
- `queueTargetSize = 10` is hardcoded. Power users on fast connections might want 20;
  users on metered connections might want 3. Expose this in Settings > Discover.
