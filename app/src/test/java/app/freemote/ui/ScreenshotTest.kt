package app.freemote.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.freemote.data.Tv
import org.junit.Rule
import org.junit.Test

/** Renders the three screens to PNGs (Paparazzi, no emulator needed). */
class ScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, showSystemUi = false)

    @Test fun tvList() = paparazzi.snapshot {
        FreeMoteTheme {
            TvListScreen(
                saved = listOf(Tv("Hisense Google TV", "192.168.1.50", paired = true)),
                found = listOf(Tv("Bedroom TV", "192.168.1.73")),
                scanning = true, onScan = {}, onPick = {}, onForget = {},
            )
        }
    }

    @Test fun pairing() = paparazzi.snapshot {
        FreeMoteTheme { PairScreen("Hisense Google TV", PairState.EnterCode(null), {}, {}, {}) }
    }

    @Test fun remote() = paparazzi.snapshot {
        FreeMoteTheme { RemoteScreen("Hisense Google TV", ConnStatus.Connected, {}, {}, { _, _ -> }, {}, {}, {}) }
    }

    @Test fun touchpad() = paparazzi.snapshot {
        FreeMoteTheme { RemoteScreen("Hisense Google TV", ConnStatus.Connected, {}, {}, { _, _ -> }, {}, {}, {}, RemoteTab.TOUCHPAD) }
    }

    @Test fun apps() = paparazzi.snapshot {
        FreeMoteTheme { RemoteScreen("Hisense Google TV", ConnStatus.Connected, {}, {}, { _, _ -> }, {}, {}, {}, RemoteTab.APPS) }
    }
}
