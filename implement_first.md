# Implement First

## Tag & Taste System

### Source tag limit auto-exclusion
When a search/fill query contains more tags than a source supports, automatically skip that source rather than sending a bad request or silently dropping tags.
- Add `maxTagCount: Int` field to `PluginManifest` (default = unlimited / Int.MAX_VALUE)
- Gelbooru free tier = 2 tags max (gold required for more; gold is no longer obtainable — effectively always 2)
- In `PluginExecutor` / fill logic, count tags in the query and skip any source where `tagCount > manifest.maxTagCount`
- Surface a small indicator in the source chip / health screen when a source is being skipped for this reason

---

### Tag preference tiers
Instead of a binary blacklist (never/allow), give every tag one of five tiers that influence fill weighting:
- **Love** — heavily boost; prioritize these in every fill
- **Like** — mild boost
- **Neutral** — default; no effect
- **Dislike** — mild suppress (reduce frequency, not eliminate)
- **Never** — hard block (existing blacklist behavior)

Tags can be assigned a tier from the image detail overlay (long-press a tag chip), from the Taste screen, or auto-suggested from liked/saved history.
Stored in DataStore as a `Map<String, TierLevel>`.

---

### Fetish / interest profiles
Named presets that bundle a set of positive and negative tags into a toggleable profile.
- User creates profiles with a name and a list of "include" tags and "exclude" tags
- Profiles can be assigned to specific collections or applied globally to Discover
- Switching profiles instantly adjusts what fills and what the Discover feed surfaces
- Profiles are independent of the global preference tiers — think of them as context-specific lenses (e.g., "Aesthetic Vibes", "Dark Fantasy", "Clean Art Only")

---

### Taste profile (auto-derived from history)
Analyze liked/saved images and their tags to build a ranked tag affinity list automatically.
- Shown as a tag cloud or ranked list in the Taste screen
- Used as soft signals when no explicit tier is set for a tag
- Entirely on-device; no external data sent
- Refreshes on demand or after N new saves

---

### Co-tag suggestions
When the user adds a tag to a search, profile, or tier list, show the top co-occurring tags from their local image history.
- "People who saved images with X also commonly have Y, Z"
- Derived from the tag lists stored on saved images — no API call needed
- Shown as a horizontally scrollable chip row below the tag input

---

### Tag expression collections
Define a collection by a tag query expression instead of manually filling it.
- Syntax: positive tags, negative tags (prefix `-`), OR groups
- Collection auto-refills on schedule by re-running the expression against active sources
- UI: tag expression editor in collection settings, similar to the pinned search editor

---

### Cross-source tag normalization
Map common concept tags to their per-source equivalents so profiles and tier lists work across all sources.
- e.g., "thigh highs" → Danbooru: `thighhighs`, Gelbooru: `thigh_highs`, Rule34: `thigh_highs`
- Stored as a bundled normalization table (JSON asset) that can be updated
- Applied transparently at query-build time in each engine

---

## New "Taste" Tab
All of the above features live in the new **Taste** tab (bottom nav, 5th item).
Sections:
1. Tag Tiers — browse/edit your tag preference tiers
2. Profiles — create, edit, toggle interest profiles
3. My Taste — auto-derived tag cloud from history
4. Co-tag Explorer — explore tag relationships from your saves
