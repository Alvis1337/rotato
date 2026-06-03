# Rotato UI Redesign Plan

A staged plan to modernize Rotato's UI on top of Material 3, AMOLED-aware
theming, and a cohesive type/shape system. Items are crossed off as they land.

## Wave 1 — Theme foundation ✅
- [x] Full Material 3 color scheme (light + dark + dynamic color on Android 12+)
- [x] True-black AMOLED dark variant
- [x] Theme mode picker (System / Light / Dark / AMOLED) persisted in `RotatoPreferences`
- [x] Expanded type scale (display/headline/title/body/label)
- [x] Shared `Shape` system (small/medium/large/extraLarge corner radii)
- [x] Settings screen entry point for theme selection
- [x] Wire `RotatoTheme` to read user preference and apply AMOLED override

## Wave 2 — Navigation & app scaffold
- [ ] Replace ad-hoc screen switching in `MainActivity` with a single
      `NavHost` (type-safe routes) covering Home, Browse, Schedule, History,
      Stats, Settings, Source Health, Plugin Store, Setup
- [ ] Adopt M3 `NavigationBar` for primary destinations and
      `NavigationRail` on width ≥ 600dp
- [ ] Consistent `TopAppBar` (large/center-aligned) per screen with proper
      scroll behavior and back affordance
- [ ] Predictive-back support and shared element / fade transitions between
      destinations

## Wave 3 — Home & Browse polish
- [ ] Rework `HomeScreen` hero/header using `LargeTopAppBar` + collapsing layout
- [ ] Card-based content rows with M3 elevation tokens and rounded shapes
- [ ] `BrowseScreen` grid: staggered grid, proper image placeholders, shimmer
      loading, and content padding that respects insets
- [ ] Empty/error/loading states standardized via a reusable `StateSlot`
- [ ] Pull-to-refresh on Home and Browse

## Wave 4 — Forms, dialogs & inputs
- [ ] Replace custom dialogs in `SetupScreen` and tag pickers with M3
      `AlertDialog` / `ModalBottomSheet`
- [ ] Switch text fields to `OutlinedTextField` with consistent supporting
      text, error states, and leading/trailing icons
- [ ] `SettingsScreen`: group settings into M3 list items with section
      headers, dividers, and switch/radio components
- [ ] Snackbar host wired at the scaffold level for global messaging

## Wave 5 — Schedule, History, Stats
- [ ] `ScheduleScreen`: timeline view with M3 chips for filters
- [ ] `HistoryScreen`: swipe-to-dismiss with undo snackbar
- [ ] `StatsScreen`: redesigned cards/charts using theme color roles
      (primary/secondary/tertiary containers)
- [ ] `SourceHealthScreen`: status badges using semantic colors

## Wave 6 — Hands-free & overlay surfaces
- [ ] `HandsFreeOverlay` and `BrainrotScreen`: edge-to-edge, hidden system
      bars, gesture areas that respect cutouts/insets
- [ ] Larger touch targets and high-contrast controls for at-a-distance use

## Wave 7 — Accessibility & polish
- [ ] Audit content descriptions and semantics across all screens
- [ ] Verify contrast on AMOLED and dynamic color schemes (WCAG AA)
- [ ] Respect system font scale; no hard-coded sp where avoidable
- [ ] Reduced-motion handling for transitions and shimmer
- [ ] RTL pass

## Validation
- [ ] All changes verified via GitHub Actions CI (`./gradlew` is not run
      locally — it hangs on the dev machine)
- [ ] Screenshot diffs captured for Home, Browse, Settings in
      Light / Dark / AMOLED
