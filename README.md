# Rotato

Discover, collect and rotate wallpapers on Android, with extra polish for foldables.

**[Download the latest APK](https://github.com/Alvis1337/rotato/releases/latest)** ·
[Website](https://alvis1337.github.io/rotato/) ·
[iPhone version](https://github.com/Alvis1337/rotato-ios)

## What it does

- **Discover:** a feed from the sources you enable, ranked toward what you save, set and skip.
- **Collections:** save to several at once, build smart collections from tag rules, lock private
  ones behind biometrics, fill them from your sources.
- **Rotation:** wallpapers change on your schedule with per-screen pools, smart crops that keep
  the subject clear of the clock, and optional night or time-of-day rules.
- **Live wallpaper, screen saver, widget, Quick Settings tiles,** Tasker and Routines actions,
  and fold-aware crops and fold pairs on foldables.
- **Content filter:** one switch hides every NSFW feature, image and locked collection.

## Sources and plugins

Sources are plugins: small JSON manifests describing a site's API (Gelbooru, Danbooru, Moebooru,
Wallhaven, Reddit and Zerochan families). The built-in ones live in
[`app/src/main/assets/plugins`](app/src/main/assets/plugins), and the official store index is
[`plugin-store/index.json`](plugin-store/index.json). The app can also install a manifest from
any URL or add your own store index.

The iPhone app uses the same store index and manifests from this repo, so keep their paths
stable.

## Backups

Settings → About & Data exports sources (with API keys), preferences, collections and installed
plugins to a JSON file. The iPhone app restores the same file, and its backups restore here.

## Building

Android Studio, JDK 21, `./gradlew assembleDebug`. Release builds are signed in CI from the
repository secrets; every push to `main` publishes a release APK (docs-only changes don't).
MyAnimeList needs `mal.clientId` / `mal.clientSecret` in `local.properties`.

## License

See [LICENSE](LICENSE).
