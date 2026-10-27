package app.freemote.protocol

import app.freemote.proto.remote.RemoteProto.RemoteKeyCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvKeyTest {
    @Test fun keyCodesMatchAndroidKeyEvent() {
        // Values from android.view.KeyEvent / remotemessage.proto (androidtvremote2 TvKeys).
        val expected = mapOf(
            TvKey.HOME to 3, TvKey.BACK to 4, TvKey.UP to 19, TvKey.DOWN to 20, TvKey.LEFT to 21,
            TvKey.RIGHT to 22, TvKey.OK to 23, TvKey.VOLUME_UP to 24, TvKey.VOLUME_DOWN to 25,
            TvKey.POWER to 26, TvKey.ENTER to 66, TvKey.DELETE to 67, TvKey.PLAY_PAUSE to 85,
            TvKey.NEXT to 87, TvKey.PREVIOUS to 88, TvKey.REWIND to 89, TvKey.FAST_FORWARD to 90,
            TvKey.MUTE to 164,
        )
        assertEquals(TvKey.entries.toSet(), expected.keys)
        expected.forEach { (k, v) -> assertEquals(k.name, v, k.code.number) }
    }

    @Test fun swipes() {
        val t = 40f
        assertEquals(TvKey.RIGHT, Gestures.swipeToKey(100f, 10f, t))
        assertEquals(TvKey.LEFT, Gestures.swipeToKey(-100f, 30f, t))
        assertEquals(TvKey.DOWN, Gestures.swipeToKey(5f, 80f, t))
        assertEquals(TvKey.UP, Gestures.swipeToKey(-20f, -80f, t))
        assertEquals(TvKey.OK, Gestures.swipeToKey(3f, -4f, t))
    }

    @Test fun charFallback() {
        assertEquals(RemoteKeyCode.KEYCODE_A, CharKeys.keyFor('a'))
        assertEquals(RemoteKeyCode.KEYCODE_Z, CharKeys.keyFor('Z'))
        assertEquals(RemoteKeyCode.KEYCODE_0, CharKeys.keyFor('0'))
        assertEquals(RemoteKeyCode.KEYCODE_9, CharKeys.keyFor('9'))
        assertEquals(RemoteKeyCode.KEYCODE_SPACE, CharKeys.keyFor(' '))
        assertNull(CharKeys.keyFor('€'))
    }

    @Test fun appLinksArePresent() {
        assertEquals(listOf("Netflix", "YouTube", "Prime Video", "Disney+", "Spotify", "Plex"), TvApp.entries.map { it.label })
        TvApp.entries.forEach { assert(it.link.contains("://")) }
    }
}
