package app.freemote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.freemote.ui.FreeMoteTheme
import app.freemote.ui.PairScreen
import app.freemote.ui.RemoteScreen
import app.freemote.ui.TvListScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm.scan()
        setContent {
            FreeMoteTheme {
                val screen by vm.screen.collectAsState()
                val saved by vm.saved.collectAsState()
                val found by vm.discovery.found.collectAsState()
                val scanning by vm.discovery.scanning.collectAsState()
                val pair by vm.pairState.collectAsState()
                val conn by vm.conn.collectAsState()
                if (screen !is Screen.List) BackHandler { vm.back() }
                when (val s = screen) {
                    Screen.List -> TvListScreen(
                        saved = saved, found = found, scanning = scanning,
                        onScan = vm::scan, onPick = vm::pick, onForget = vm::forget,
                    )
                    is Screen.Pair -> PairScreen(
                        tvName = s.tv.name, state = pair,
                        onSubmit = { vm.submitCode(s.tv, it) },
                        onRetry = { vm.startPairing(s.tv) }, onBack = vm::back,
                    )
                    is Screen.Remote -> RemoteScreen(
                        tvName = s.tv.name, status = conn,
                        onKey = vm::key, onApp = vm::app, onText = vm::text,
                        onReconnect = { vm.connect(s.tv) }, onRepair = { vm.startPairing(s.tv) },
                        onBack = vm::back,
                    )
                }
            }
        }
    }
}
