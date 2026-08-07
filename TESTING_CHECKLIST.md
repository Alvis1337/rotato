# Testing Checklist — Video Support + NSFW Feature Expansion

Everything below shipped via CI (green builds) but has **not been tap-tested on a real device**.
This is the manual test pass to run before trusting it.

## Video support

### Playback
- [ ] Gelbooru video posts actually play (this needed a Referer header fix — the thing most likely to still be broken if untested)
- [ ] Danbooru, Moebooru, Wallhaven, Reddit video posts play
- [ ] Zerochan (no video support expected — confirm it doesn't break anything)
- [ ] Video autoplays muted in Discover grid as you scroll
- [ ] Video autoplays muted in Browse (collection) grid as you scroll
- [ ] Autoplay only kicks in once a tile is actually on-screen, not just mounted in the scroll buffer
- [ ] No more than ~3 videos autoplay at once even in a video-heavy feed
- [ ] Video thumbnail shows something reasonable when a source gives no static preview (video-frame decode fallback)
- [ ] Settings → Video Previews: Autoplay / Static thumbnail / Off all behave as labeled

### Full-screen player (Discover detail view, Browse detail view/sheet)
- [ ] Tap toggles play/pause
- [ ] Mute button is NOT hidden under the status bar
- [ ] Unmuting one video keeps the next video you open unmuted too (session-persisted)
- [ ] Seek bar shows correct current time / duration, drag-to-scrub works
- [ ] Double-tap left half of video = -10s, right half = +10s, with the +/-10s indicator
- [ ] Buffering spinner shows before first frame, not just a black screen
- [ ] A broken/dead video URL shows an error state, not silent nothing
- [ ] Swiping to the next/prev item in the pager doesn't leave the previous video still playing in the background
- [ ] "Set as wallpaper" / add-to-rotation actions are disabled or hidden for video items (can't set video as a static wallpaper)
- [ ] Save-to-gallery on a video saves an actual playable video file (Movies/Rotato) with correct extension, not a mislabeled image

## NSFW feature expansion

### Per-image tagging (mostly invisible — verify indirectly)
- [ ] NSFW mode ON in a source that supports explicit content → images you'd expect to be flagged NSFW are the ones that get blurred/restricted below
- [ ] SFW-only browsing doesn't blur anything (nothing should be incorrectly flagged NSFW)

### Blur
- [ ] Settings → NSFW → "Blur NSFW previews" toggle: off disables blur everywhere; on re-enables it
- [ ] Discover grid blurs NSFW tiles, tap reveals
- [ ] Library grid blurs NSFW tiles, tap reveals
- [ ] Browse (collection) grid blurs NSFW tiles, tap reveals
- [ ] Revealing a tile, scrolling it off-screen, and scrolling back — it should STAY revealed (not re-blur)
- [ ] Browse collection "⋮" menu → "Skip NSFW blur for this collection" — once set, that collection's grid shows no blur regardless of the global toggle; toggling it back restores blur

### Home-screen-only
- [ ] Settings → NSFW → "NSFW → home screen only" OFF: NSFW wallpapers respect your normal Wallpaper Target (Home/Lock/Both) same as anything else
- [ ] Toggle ON, let an NSFW wallpaper rotate in: it should land on Home only, lock screen should show something else (or stay unaffected) even if your Wallpaper Target is set to Both
- [ ] Manually "Set as wallpaper" on an NSFW image from Discover also respects this setting

### Notification blur
- [ ] With blur enabled and an NSFW wallpaper set, the "Wallpaper changed" notification's thumbnail/big-picture image is visibly blurred
- [ ] With an SFW wallpaper, notification looks normal (not blurred)

### Stealth Mode QS tile
- [ ] Settings → NSFW → "Stealth collection" dropdown: pick a collection
- [ ] Add the "Stealth Mode" tile to your quick settings (separate tile from the existing rotation on/off tile)
- [ ] Tapping it ON: rotation should switch to *only* pulling from the stealth collection, and NSFW mode should force off
- [ ] Tapping it OFF: rotation and NSFW mode should return to whatever they were before you activated stealth
- [ ] Tile shows "Not set up" (or similar) if no stealth collection has been chosen yet, and tapping it does nothing destructive in that state

## Known gaps / things I'm least confident about
- Stealth mode's "remember and restore previous NSFW mode" logic — the trickiest bit of state to get right, most worth double-checking by toggling it on/off a few times in a row
- The downscale/upscale "blur" used for the notification thumbnail — never seen the actual rendered result, could look worse than intended
- NSFW-blur reveal state is in-memory only — force-closing the app and reopening will re-blur everything (expected, but confirm it doesn't feel broken)
