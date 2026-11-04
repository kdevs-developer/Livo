# Livo

Live TV player for Android TV, phones and tablets. Kotlin, Jetpack Compose, Media3 (ExoPlayer), IPTV M3U.
Package: `com.kdevs.livo` · minSdk 26 · targetSdk 35

## Open it
1. Android Studio (Ladybug or newer) > File > Open > select the `Livo` folder.
2. Let Gradle sync (it downloads Gradle 8.9 and the libraries; internet needed).
3. Run on a phone, tablet or Android TV emulator/device.

## Features
- Opens on Live (or Favorites, if you choose that in Settings): favorites first, then all online channels
- Playlists: Livo India (built-in, hard-coded) plus any M3U links you add. A new link is saved as an extra playlist, never replaces the original
- Filters: Language and Category (from the M3U tags)
- Channels are hidden only after a definite failure (404, dead host, invalid stream, or a stream that still fails after a retry). Timeouts and 403s do not hide a channel
- Hidden channels screen (Settings > Channels): see why each channel is hidden, play it anyway, unhide it, or re-check all. A hidden channel that plays fine comes back by itself
- Streams that need a Referer or User-Agent (#EXTVLCOPT lines in the playlist) now work in the player and the checker
- Player: back, language and favorite on top; previous, play/pause, next in the center. CH+/CH- change channel; D-pad keys open the controls and move between buttons
- Settings: playlists, resume last channel, aspect ratio, video quality cap, buffer size, hide offline, check favorites on launch, channel numbers, retry before hiding, card size, open on favorites, accent color, clear hidden list, backup/restore favorites (clipboard)
- All colors live in res/values/colors.xml and all text in res/values/strings.xml
- White adaptive launcher icon (`ic_launcher`) + TV banner

## Notes
- Country comes from `tvg-country`, or from the `tvg-id` suffix (for example `.uk`). Language comes from `tvg-language`.
  If your playlist has neither tag, those filters only show "All" / "Other".
- Only use playlists you have the right to stream.
