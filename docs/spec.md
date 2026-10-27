# FreeMote — Spec (approved by Kyler)

## Scope
A public, free app that anyone with a Google TV or Android TV can use. No account, no ads,
no tracking, MIT licence. Kyler's setup: Hisense Google TV + Android phone.

## How it connects
Phone and TV on the same Wi-Fi. Android TV Remote protocol v2 (same as the official Google TV app):
- Pairing over TLS on port 6467 with a client certificate the app generates itself.
  The TV shows a 6-character code; the user types it in.
- Remote control over TLS on port 6466 with length-prefixed protobuf messages.
- The certificate and paired TVs are saved, so pairing happens once.
- TVs are discovered with mDNS (NsdManager, `_androidtvremote2._tcp`); IP entry by hand also works.

## Day-one features
- D-pad arrows, OK, Back, Home
- Volume up/down, mute, power
- Play/pause, rewind, fast forward, next, previous
- Swipe touchpad: swipes -> D-pad presses, tap -> OK
- Phone keyboard text input (IME batch-edit message) with key-event fallback
- One-tap app launch: Netflix, YouTube, Prime Video, Disney+, Spotify, Plex

## UI
Dark theme, big thumb-friendly buttons, haptic feedback, portrait. Screens: TV list with scan and
"Add by IP"; pairing code entry; remote with Remote / Touchpad / Apps tabs and a keyboard button.

## Distribution
GitHub Actions builds a signed release APK; on `v*` tags it attaches `FreeMote.apk` to a GitHub
Release. minSdk 24.

## Acceptance checks
| # | Check | How verified |
|---|-------|--------------|
| 1 | `./gradlew testDebugUnitTest assembleRelease` passes in CI | Actions run green |
| 2 | Framing: varint length prefix matches protobuf; truncated input fails | FramingTest |
| 3 | Pairing hash equals androidtvremote2's for a fixed cert pair + code | PairingSecretTest (vector from tools/make_pairing_vector.py) |
| 4 | Wrong code is rejected before sending | PairingSecretTest, FakeTvTest |
| 5 | Full pairing exchange over real TLS with client cert | FakeTvTest.pairsWithCorrectCode |
| 6 | Remote handshake (configure, set_active, ping), keys, IME text, app links | FakeTvTest.remoteSessionHandshakeKeysTextAndApps |
| 7 | Key codes match Android KeyEvent values | TvKeyTest |
| 8 | Swipe/tap mapping | TvKeyTest.swipes |
| 9 | Client cert generated once and reused | FakeTvTest.identityIsSavedAndReused |
| 10 | Three screens render in dark theme with big buttons | Paparazzi screenshots in `shots/` |
| 11 | Release `FreeMote.apk` downloadable from GitHub Releases | v0.1.0 release |
| 12 | Pairs with and controls a real Hisense Google TV | **Manual — Kyler** |
| 13 | mDNS finds the TV on real Wi-Fi | **Manual — Kyler** |
| 14 | App links open each app; keyboard types into TV search | **Manual — Kyler** |
