# E-Reader

A phone-first, offline-first Android reading companion designed for long, comfortable reading sessions.

## Product direction

- Warm, eye-friendly reading canvas with cream/sepia/dark themes
- Bookshelf with genre organization and editable metadata
- Offline library and local reading progress
- EPUB/PDF/DOCX import architecture
- Scroll or page-flip reading modes
- Volume buttons for previous/next page
- Adjustable auto-page-turn
- Exact reading-position resume
- While Reading HUD: progress %, estimated minutes left, WPM and streaks
- WPM calibration test and adaptive estimates
- Highlights, notes, bookmarks and annotations
- Long-press dictionary and optional character-memory assistant
- Private Vault with local encryption architecture
- Reading Focus mode / Android accessibility guidance
- Optional launch shortcut/accessibility integration for quick reading
- Reading rewards: streaks, milestones, gentle progress celebrations and session goals

## UX principles

The app should feel warm, quiet and rewarding rather than gamified in a distracting way. Use soft surfaces, generous margins, readable serif typography, restrained animations, subtle haptics and small milestone celebrations. Never sacrifice readability for decoration.

## Android constraints

The app cannot universally intercept the hardware power button or silently toggle Android Digital Wellbeing Focus Mode on every device. Where Android/OEM APIs do not permit those actions, provide an accessibility/launcher shortcut and a clear one-tap path to the relevant system settings.

## Privacy

Reading history, annotations, WPM and book metadata should remain local by default. Network access is optional and only used for user-enabled dictionary/AI integrations.
