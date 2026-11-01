# Livo

Live TV player for Android TV, phones and tablets. Kotlin, Jetpack Compose, Media3 (ExoPlayer), IPTV M3U.
Package: `com.kdevs.livo` · minSdk 26 · targetSdk 35

## Open it
1. Android Studio (Ladybug or newer) > File > Open > select the `Livo` folder.
2. Let Gradle sync (it downloads Gradle 8.9 and the libraries; internet needed).
3. Run on a phone, tablet or Android TV emulator/device.

## Features
- Opens on Home: your favorites first, then all online channels
- Filters: Country, Language, Category (from the M3U tags)
- Offline channels are hidden; Refresh re-checks favorites + the current filter (up to 400 channels), offline ones included
- A channel that fails to play is hidden automatically
- Player: Previous/Next channel (D-pad Up/Down or CH+/CH-), audio language switch, favorite toggle
- Press and hold a channel to favorite it (works with a TV remote's OK button)
- Settings: change the M3U playlist link (default: iptv-org index)
- White adaptive launcher icon (`ic_launcher`) + TV banner

## Notes
- Country comes from `tvg-country`, or from the `tvg-id` suffix (for example `.uk`). Language comes from `tvg-language`.
  If your playlist has neither tag, those filters only show "All" / "Other".
- Only use playlists you have the right to stream.
