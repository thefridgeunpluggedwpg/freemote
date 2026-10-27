package app.freemote.protocol

import app.freemote.proto.remote.RemoteProto.RemoteKeyCode

/** Buttons FreeMote offers, mapped to Android TV key codes from remotemessage.proto. */
enum class TvKey(val code: RemoteKeyCode) {
    UP(RemoteKeyCode.KEYCODE_DPAD_UP),
    DOWN(RemoteKeyCode.KEYCODE_DPAD_DOWN),
    LEFT(RemoteKeyCode.KEYCODE_DPAD_LEFT),
    RIGHT(RemoteKeyCode.KEYCODE_DPAD_RIGHT),
    OK(RemoteKeyCode.KEYCODE_DPAD_CENTER),
    BACK(RemoteKeyCode.KEYCODE_BACK),
    HOME(RemoteKeyCode.KEYCODE_HOME),
    VOLUME_UP(RemoteKeyCode.KEYCODE_VOLUME_UP),
    VOLUME_DOWN(RemoteKeyCode.KEYCODE_VOLUME_DOWN),
    MUTE(RemoteKeyCode.KEYCODE_VOLUME_MUTE),
    POWER(RemoteKeyCode.KEYCODE_POWER),
    PLAY_PAUSE(RemoteKeyCode.KEYCODE_MEDIA_PLAY_PAUSE),
    REWIND(RemoteKeyCode.KEYCODE_MEDIA_REWIND),
    FAST_FORWARD(RemoteKeyCode.KEYCODE_MEDIA_FAST_FORWARD),
    NEXT(RemoteKeyCode.KEYCODE_MEDIA_NEXT),
    PREVIOUS(RemoteKeyCode.KEYCODE_MEDIA_PREVIOUS),
    ENTER(RemoteKeyCode.KEYCODE_ENTER),
    DELETE(RemoteKeyCode.KEYCODE_DEL),
}

/** Touchpad: a swipe becomes a D-pad press, a tap becomes OK. */
object Gestures {
    fun swipeToKey(dx: Float, dy: Float, threshold: Float): TvKey {
        val ax = kotlin.math.abs(dx)
        val ay = kotlin.math.abs(dy)
        if (ax < threshold && ay < threshold) return TvKey.OK
        return if (ax >= ay) (if (dx > 0) TvKey.RIGHT else TvKey.LEFT)
        else (if (dy > 0) TvKey.DOWN else TvKey.UP)
    }
}

/** Key-event fallback for typing when the TV doesn't accept IME text. */
object CharKeys {
    fun keyFor(c: Char): RemoteKeyCode? = when (c.lowercaseChar()) {
        in 'a'..'z' -> RemoteKeyCode.forNumber(RemoteKeyCode.KEYCODE_A_VALUE + (c.lowercaseChar() - 'a'))
        in '0'..'9' -> RemoteKeyCode.forNumber(RemoteKeyCode.KEYCODE_0_VALUE + (c - '0'))
        ' ' -> RemoteKeyCode.KEYCODE_SPACE
        '.' -> RemoteKeyCode.KEYCODE_PERIOD
        ',' -> RemoteKeyCode.KEYCODE_COMMA
        '-' -> RemoteKeyCode.KEYCODE_MINUS
        '@' -> RemoteKeyCode.KEYCODE_AT
        '\n' -> RemoteKeyCode.KEYCODE_ENTER
        else -> null
    }
}

/** One-tap app launch links (from the Home Assistant / androidtvremote2 docs). */
enum class TvApp(val label: String, val link: String, val color: Long) {
    NETFLIX("Netflix", "https://www.netflix.com/title", 0xFFE50914),
    YOUTUBE("YouTube", "https://www.youtube.com", 0xFFFF0000),
    PRIME("Prime Video", "https://app.primevideo.com", 0xFF00A8E1),
    DISNEY("Disney+", "https://www.disneyplus.com", 0xFF113CCF),
    SPOTIFY("Spotify", "spotify://", 0xFF1DB954),
    PLEX("Plex", "plex://", 0xFFE5A00D),
}
