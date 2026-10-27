# FreeMote — Plan

1. Skeleton: Gradle wrapper, Kotlin + Compose + protobuf-lite, copy protos from androidtvremote2. ✅
2. Protocol core: varint framing, pairing secret hash, TLS with self-signed client cert. ✅
3. Pairing client (port 6467) and remote client (port 6466) ported from androidtvremote2. ✅
4. Data: saved TVs (SharedPreferences), mDNS discovery (NsdManager). ✅
5. UI: TV list + Add by IP, pairing code screen, remote (Remote/Touchpad/Apps + keyboard). ✅
6. Tests: framing, hash vector, key map, fake-TV TLS server tests, Paparazzi screenshots. ✅
7. CI: GitHub Actions build + tests on push; signed release + GitHub Release on `v*` tag. ✅
8. Release v0.1.0. ✅
9. Kyler's first real-TV pairing test → fix anything found (see todo.md).
