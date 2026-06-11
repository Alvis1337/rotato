# Planned Updates

## NSFW Feature Expansion

### Per-image NSFW metadata storage
Before implementing features 2, 3, and 5 below, images need to carry NSFW/SFW provenance. When an image is saved to the rotation pool or a collection, record whether it originated from an NSFW-rated result. This is the shared foundation for the features below.

---

### Blurred previews for NSFW images (Discover + Library)
**Priority: High**

- In Discover grid and Library, render explicit/NSFW-tagged images as blurred/darkened cards
- Tap to reveal (or hold to peek)
- Should be a user toggle — some users want this off entirely
- Also acts as a safety net when an NSFW image slips through while global NSFW mode is off
- **Note:** blur level / reveal gesture TBD

---

### NSFW images forced to home screen only (optional)
**Priority: Medium**

- When enabled, any image tagged NSFW at save time is restricted to `HOME_ONLY` target
- Lock screen stays clean regardless of what's in rotation
- Must be **opt-in** — not every user needs this
- Relies on per-image NSFW metadata (see above)

---

### Blur NSFW notification thumbnails
**Priority: High**

- When the "Keep" (and any other wallpaper change) notification fires, blur the thumbnail if the current wallpaper is tagged NSFW
- Should follow the same user toggle as the Discover blur setting — if the user has turned blurring off, show it unblurred
- Relies on per-image NSFW metadata (see above)

---

### Stealth mode QS tile / action
**Priority: Medium**

- A QS tile (or long-press on existing tile) that temporarily forces global SFW mode
- Functions as a "panic" / "normie" button — one tap to look completely wholesome
- **Resolution for NSFW-only collections:** user designates a "stealth collection" in settings; when stealth is active, rotation switches entirely to that collection regardless of schedule or current pool
- The stealth collection is the leanback — something safe and neutral the user is comfortable with in public
- Stealth stays active until manually toggled off (or optionally auto-clears on next unlock)
