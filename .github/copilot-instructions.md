# Rotato – Copilot Instructions

## Build & CI

**Do not run Gradle builds locally** — `./gradlew` hangs on this machine. Use GitHub Actions for all build verification.

CI workflow: `.github/workflows/release.yml`
- Triggers on push to `main`, version tags (`v*`), or `workflow_dispatch`
- Build command (CI only): `./gradlew assembleRelease --no-daemon`
- `versionCode` is automatically set to `git rev-list --count HEAD`
- Signing requires secrets: `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`

**Local dev credentials** go in `local.properties` (gitignored):
```
mal.clientId=YOUR_MAL_CLIENT_ID
mal.clientSecret=YOUR_MAL_CLIENT_SECRET
```
MAL credentials: env vars take priority (CI), then `local.properties` (dev), then empty string (disabled).

## Architecture

Single-module Android app (`com.chrisalvis.rotato`), Kotlin + Jetpack Compose + Material 3, minSdk 26, compileSdk/targetSdk 36.

### Layer overview

```
app/src/main/kotlin/com/chrisalvis/rotato/
├── data/
│   ├── plugins/          # Source plugin system (see below)
│   ├── RotatoPreferences # All app settings via DataStore
│   ├── LocalSourcesPreferences  # Per-source configs (JSON in DataStore)
│   ├── LocalListsPreferences    # Collections / saved wallpapers (JSON in DataStore)
│   ├── MalRepository     # MyAnimeList OAuth2 + anime list API
│   ├── ImageRepository   # Local file I/O for the rotation queue
│   ├── FeedRepository    # HTTP downloads (OkHttp)
│   └── DirectSourceRepository  # Direct-URL fetching
├── ui/                   # Compose screens + ViewModels (one ViewModel per screen)
└── worker/               # Background execution (WorkManager + AlarmManager)
```

### Navigation routes (plain strings)
`setup` → `discover` (BrainrotScreen) · `home` (Library) · `browse` (Collections) · `settings` · `sources` · `schedule`

### Source plugin system

To add a new wallpaper source: implement `SourcePlugin`, add it to `SourcePluginRegistry.all`. No other code needs changing.

- `SourcePlugin` – abstract class defining `id`, display metadata, credential fields, `fetch()`, and optional `fetchPage()`
- `SourcePluginRegistry` – `object` containing `all: List<SourcePlugin>`; look up by `id` or `SourceType`
- `SourceType` enum – mirrors plugin IDs (e.g. `SourceType.DANBOORU.name == "DANBOORU" == DanbooruPlugin.id`)
- `PluginEntitlement` – premium IAP gating stub; currently grants all plugins. Phase 4 will wire Google Play Billing (product IDs: `source_<id>`, bundle: `source_all`)

### Persistence

All persistence uses a **single DataStore** (`rotato_prefs`, `Context.dataStore`). There is no Room/SQLite database. Complex objects (source configs, collections, history) are serialized as JSON strings and stored as `stringPreferencesKey` values.

### Wallpaper rotation pipeline

1. `WallpaperWorker` (WorkManager `CoroutineWorker`) picks the next image from the local queue
2. Scales/crops the bitmap to exact screen dimensions before calling `WallpaperManager.setBitmap()`
3. For intervals ≤14 min, self-chains via `WorkManager.enqueueUniqueWork(CHAIN_WORK_NAME, REPLACE, …)` instead of periodic work
4. Widget (`RotatoWidgetProvider`) and notification ("Skip" / "Keep" actions) are updated after each rotation
5. `ScheduleManager` uses `AlarmManager.setExactAndAllowWhileIdle` for time-of-day schedules (falls back to `setAndAllowWhileIdle` when exact alarm permission is unavailable)

### MAL integration

OAuth2 PKCE flow. Deep-link callback: `rotato://callback`. The code verifier is persisted in DataStore between `buildAuthUrl()` and `exchangeCode()`. Token refresh is automatic on 401.

## Key Conventions

- **Plugin HTTP calls** – All plugin `fetch()` / `fetchPage()` implementations wrap blocking OkHttp calls in `onIO { }` (defined in `PluginUtils.kt`). Use this pattern for any new blocking I/O inside a plugin.
- **Shared HTTP client** – `PluginUtils.kt` exports a package-internal `http: OkHttpClient` and `BROWSER_UA` string used by all plugins.
- **Query normalisation** – `normalizeBooruQuery()` converts anime titles (spaces → underscores, strip special chars). `normalizeUserQuery()` handles free-text search (space-separated tokens ANDed by the booru API). Use the correct one for the context.
- **DataStore flows** – Always `.catch { emit(emptyPreferences()) }` before `.map { }` to handle corruption gracefully (existing pattern throughout `*Preferences` classes).
- **JSON serialisation** – Complex DataStore values use `org.json` (`JSONObject`/`JSONArray`), not Gson/Moshi/kotlinx.serialization.
- **Filename sanitisation** – Use `sanitizeFilename(sourceId)` when saving wallpaper files so the filename can be matched back to collection entries in history.
- **`WallpaperTarget`** – Enum with `HOME_ONLY`, `LOCK_ONLY`, `BOTH`; maps to `WallpaperManager.FLAG_SYSTEM` / `FLAG_LOCK`.
