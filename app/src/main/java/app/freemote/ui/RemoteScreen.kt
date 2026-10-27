package app.freemote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.freemote.protocol.Gestures
import app.freemote.protocol.TvApp
import app.freemote.protocol.TvKey

sealed interface ConnStatus {
    data object Connecting : ConnStatus
    data object Connected : ConnStatus
    data object NeedsPairing : ConnStatus
    data class Lost(val message: String) : ConnStatus
}

enum class RemoteTab(val label: String) { REMOTE("Remote"), TOUCHPAD("Touchpad"), APPS("Apps") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    tvName: String,
    status: ConnStatus,
    onKey: (TvKey) -> Unit,
    onApp: (TvApp) -> Unit,
    onText: (String, Boolean) -> Unit,
    onReconnect: () -> Unit,
    onRepair: () -> Unit,
    onBack: () -> Unit,
    initialTab: RemoteTab = RemoteTab.REMOTE,
) {
    var tab by remember { mutableStateOf(initialTab) }
    var showKeyboard by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(tvName, fontWeight = FontWeight.Bold)
                        Text(
                            when (status) {
                                ConnStatus.Connected -> "Connected"; ConnStatus.Connecting -> "Connecting…"
                                ConnStatus.NeedsPairing -> "Needs pairing"; is ConnStatus.Lost -> "Not connected"
                            },
                            fontSize = 13.sp,
                            color = if (status == ConnStatus.Connected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack, Modifier.size(56.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to TV list") } },
                actions = {
                    BigButton(Icons.Filled.Keyboard, "Keyboard", { showKeyboard = true }, size = 56.dp, shape = CircleShape)
                    Spacer(Modifier.width(8.dp))
                    BigButton(Icons.Filled.PowerSettingsNew, "Power", { onKey(TvKey.POWER) }, size = 56.dp, shape = CircleShape,
                        color = Color(0xFF3A1C22), contentColor = Color(0xFFFF6B6B))
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            when (status) {
                ConnStatus.NeedsPairing -> Banner("This TV needs to be paired again.", "Pair", onRepair)
                is ConnStatus.Lost -> Banner(status.message, "Reconnect", onReconnect)
                else -> {}
            }
            TabRow(selectedTabIndex = tab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
                RemoteTab.entries.forEach { t ->
                    Tab(selected = tab == t, onClick = haptic { tab = t }, modifier = Modifier.height(60.dp),
                        text = { Text(t.label, fontSize = 18.sp) })
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                when (tab) {
                    RemoteTab.REMOTE -> RemotePad(onKey)
                    RemoteTab.TOUCHPAD -> Touchpad(onKey)
                    RemoteTab.APPS -> AppGrid(onApp)
                }
            }
        }
    }
    if (showKeyboard) KeyboardDialog(onText, onKey) { showKeyboard = false }
}

@Composable
private fun Banner(msg: String, action: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF3A2A12)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(msg, Modifier.weight(1f), color = Color(0xFFFFD27A))
        Button(onClick = haptic(onAction), Modifier.height(52.dp)) { Text(action, fontSize = 16.sp) }
    }
}

@Composable
private fun RemotePad(onKey: (TvKey) -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
        // D-pad
        val d = 84.dp
        Box(Modifier.size(d * 3 + 16.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)) {
            BigButton(Icons.Filled.KeyboardArrowUp, "Up", { onKey(TvKey.UP) }, Modifier.align(Alignment.TopCenter).padding(top = 4.dp), d, CircleShape, Color.Transparent)
            BigButton(Icons.Filled.KeyboardArrowDown, "Down", { onKey(TvKey.DOWN) }, Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp), d, CircleShape, Color.Transparent)
            BigButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Left", { onKey(TvKey.LEFT) }, Modifier.align(Alignment.CenterStart).padding(start = 4.dp), d, CircleShape, Color.Transparent)
            BigButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Right", { onKey(TvKey.RIGHT) }, Modifier.align(Alignment.CenterEnd).padding(end = 4.dp), d, CircleShape, Color.Transparent)
            BigButton(null, "OK", { onKey(TvKey.OK) }, Modifier.align(Alignment.Center), d + 8.dp, CircleShape,
                MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BigButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", { onKey(TvKey.BACK) }, showLabel = true)
            BigButton(Icons.Filled.Home, "Home", { onKey(TvKey.HOME) }, showLabel = true)
            BigButton(Icons.AutoMirrored.Filled.VolumeOff, "Mute", { onKey(TvKey.MUTE) }, showLabel = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BigButton(Icons.AutoMirrored.Filled.VolumeDown, "Vol −", { onKey(TvKey.VOLUME_DOWN) }, showLabel = true)
            BigButton(Icons.Filled.PlayArrow, "Play/Pause", { onKey(TvKey.PLAY_PAUSE) }, showLabel = true)
            BigButton(Icons.AutoMirrored.Filled.VolumeUp, "Vol +", { onKey(TvKey.VOLUME_UP) }, showLabel = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val s = 64.dp
            BigButton(Icons.Filled.SkipPrevious, "Previous", { onKey(TvKey.PREVIOUS) }, size = s)
            BigButton(Icons.Filled.FastRewind, "Rewind", { onKey(TvKey.REWIND) }, size = s)
            BigButton(Icons.Filled.FastForward, "Fast forward", { onKey(TvKey.FAST_FORWARD) }, size = s)
            BigButton(Icons.Filled.SkipNext, "Next", { onKey(TvKey.NEXT) }, size = s)
        }
    }
}

@Composable
private fun Touchpad(onKey: (TvKey) -> Unit) {
    val view = LocalView.current
    val threshold = with(LocalDensity.current) { 40.dp.toPx() }
    val send: (TvKey) -> Unit = { view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY); onKey(it) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(28.dp))
                .pointerInput(Unit) { detectTapGestures(onTap = { send(TvKey.OK) }) }
                .pointerInput(Unit) {
                    var total = Offset.Zero
                    detectDragGestures(
                        onDragStart = { total = Offset.Zero },
                        onDrag = { change, amount ->
                            change.consume()
                            total += amount
                            // Each 40dp of travel in one direction = one press, so long swipes repeat.
                            if (kotlin.math.abs(total.x) >= threshold || kotlin.math.abs(total.y) >= threshold) {
                                send(Gestures.swipeToKey(total.x, total.y, threshold)); total = Offset.Zero
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.TouchApp, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Swipe to move · Tap for OK", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            BigButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", { onKey(TvKey.BACK) }, showLabel = true)
            BigButton(Icons.Filled.Home, "Home", { onKey(TvKey.HOME) }, showLabel = true)
            BigButton(Icons.Filled.PlayArrow, "Play/Pause", { onKey(TvKey.PLAY_PAUSE) }, showLabel = true)
        }
    }
}

@Composable
private fun AppGrid(onApp: (TvApp) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvApp.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { app ->
                    Button(
                        onClick = haptic { onApp(app) },
                        modifier = Modifier.weight(1f).height(96.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(app.color), contentColor = Color.White),
                    ) { Text(app.label, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
                }
            }
        }
        Text("Opens the app on your TV. If an app isn't installed on the TV, nothing happens.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun KeyboardDialog(onText: (String, Boolean) -> Unit, onKey: (TvKey) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var keysOnly by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Type on your TV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Select a search box on the TV first, then type here.")
                OutlinedTextField(
                    text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(fontSize = 22.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onText(text, keysOnly); text = "" }),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(keysOnly, { keysOnly = it })
                    Text("Type letter by letter (try this if text doesn't appear)")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton(Icons.AutoMirrored.Filled.Backspace, "Delete", { onKey(TvKey.DELETE) }, size = 64.dp)
                    BigButton(Icons.AutoMirrored.Filled.KeyboardReturn, "Enter", { onKey(TvKey.ENTER) }, size = 64.dp)
                }
            }
        },
        confirmButton = { Button(onClick = haptic { onText(text, keysOnly); text = "" }, Modifier.height(56.dp)) { Text("Send", fontSize = 18.sp) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close", fontSize = 18.sp) } },
    )
}
