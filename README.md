# E-Reader

A phone-first, offline-first Android reading companion for long, comfortable reading sessions.

## Included in this build

- Offline shelf and local reading progress
- Import EPUB, PDF, DOCX, TXT, HTML and RTF from the Android file picker
- Editable title, author, genre and synopsis
- Genre-based shelves and a private Vault
- Vault encryption using Android Keystore + AES/GCM
- Scroll or flip reading modes
- 3D paper-style page transition in Flip mode
- Volume Down = next page; Volume Up = previous page while reading
- Automatic page turn with adjustable 5–60 second speed
- Last reading position saved automatically
- While reading HUD: progress %, estimated minutes left, WPM, current page and mode
- WPM calibration test
- Reading-speed telemetry that treats very fast reversals as likely accidental corrections
- Bookmarks, highlights and notes
- Long-press Define: Android text-processing apps first, Merriam-Webster fallback
- Long-press Character: local passage-based character memory cards
- Cream, Sepia and Night themes
- Serif, Sans and Mono fonts, with adjustable font size
- Reading Focus using Android interruption-control / DND access
- Quick Read app shortcut and Quick Settings tile

## Android constraints

### Triple power-button launch
A normal third-party Android app cannot rely on a universal API that lets it intercept the hardware power button three times. The project therefore provides a Quick Read launcher shortcut and Quick Settings tile instead of pretending the 3× power gesture is universally available.

### Focus mode
The app can use Android interruption-control / Do Not Disturb APIs when the user grants policy access. This is not guaranteed to be identical to the phone's Digital Wellbeing Focus mode. Android 15+ also changes how apps can modify the global interruption filter.

### PDF WPM
PDFs are rendered as native PDF pages. The lightweight offline core uses an estimated 450 words per PDF page for WPM/minutes-left calculations. EPUB/DOCX/TXT/HTML/RTF use extracted text.

## Build

Open the repository in Android Studio and let Gradle sync. Build the debug APK with:

    gradle assembleDebug

The repository also includes a GitHub Actions workflow at .github/workflows/android.yml for an Android debug build.

## Privacy

Book files, metadata, progress, WPM, bookmarks, highlights and notes are stored locally by default. Network access is only used when the user chooses the external Merriam-Webster lookup.