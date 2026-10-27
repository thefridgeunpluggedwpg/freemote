# AGENTS.md — FreeMote

## What this is
FreeMote is a free, open-source (MIT) Android app that works as a remote for
Google TV / Android TV. No account, no ads, no tracking. Owner: Kyler (non-programmer).
It speaks the Android TV Remote protocol v2 (same as Google's official remote):
- Pairing: TLS port 6467, polo protobuf, 6-character hex code shown on the TV.
- Control: TLS port 6466, varint-length-prefixed `RemoteMessage` protobufs.
- Discovery: mDNS `_androidtvremote2._tcp` via NsdManager, plus "Add by IP".

## Stack
Kotlin 2.0, Jetpack Compose (Material 3, dark only), protobuf-lite (Gradle protobuf
plugin), Bouncy Castle for the self-signed client cert, Gradle wrapper 8.9, AGP 8.5,
minSdk 24, JDK 17. Paparazzi for screenshots.

## Layout
- `app/src/main/proto/` — protocol definitions copied from androidtvremote2 (do not edit by hand)
- `app/src/main/java/app/freemote/protocol/` — framing, pairing hash, TLS, clients, key map
- `app/src/main/java/app/freemote/data/` — saved TVs, mDNS discovery
- `app/src/main/java/app/freemote/ui/` — Compose screens (TV list, pairing, remote)
- `tools/make_pairing_vector.py` — regenerates the pairing hash test vector

## Build
    ./gradlew assembleDebug          # debug APK
    ./gradlew assembleRelease        # signed only if FREEMOTE_KEYSTORE etc. env vars are set

## Test
    ./gradlew testDebugUnitTest      # framing, pairing hash vector, keys, fake-TV TLS tests
    ./gradlew recordPaparazziDebug   # re-render screenshots into app/src/test/snapshots

## Rules
1. Don't invent protocol details: follow androidtvremote2 / louis49 and cite them.
2. Keep attribution in NOTICE and README when copying code.
3. Never commit keystores, passwords or `local.properties`. Signing comes from CI secrets.
4. Every change must keep `testDebugUnitTest` and the Actions build green.
5. Big buttons (>= 56dp), haptics on press, dark theme, portrait.
6. Small commits with clear messages.

## Docs
- Spec + acceptance checks: [docs/spec.md](docs/spec.md)
- Plan: [docs/plan.md](docs/plan.md)
- To-do: [docs/todo.md](docs/todo.md)
