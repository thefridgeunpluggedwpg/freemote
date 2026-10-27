package app.freemote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.freemote.data.Tv

private val IP = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvListScreen(
    saved: List<Tv>,
    found: List<Tv>,
    scanning: Boolean,
    onScan: () -> Unit,
    onPick: (Tv) -> Unit,
    onForget: (Tv) -> Unit,
) {
    var showIp by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("FreeMote", fontWeight = FontWeight.Bold, fontSize = 26.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        val newOnes = found.filter { f -> saved.none { it.host == f.host } }
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (saved.isNotEmpty()) {
                item { Header("Your TVs") }
                items(saved, key = { "s" + it.host }) { TvRow(it, onPick, onForget) }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Header(if (scanning) "Looking for TVs on your Wi-Fi…" else "TVs on your Wi-Fi")
                    Spacer(Modifier.weight(1f))
                    if (scanning) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp)
                }
            }
            items(newOnes, key = { "f" + it.host }) { TvRow(it, onPick, null) }
            if (newOnes.isEmpty()) item {
                Text(
                    "No new TVs found yet. Make sure your phone and TV are on the same Wi-Fi.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    Button(onClick = haptic(onScan), Modifier.weight(1f).height(64.dp), shape = RoundedCornerShape(20.dp)) {
                        Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Scan again", fontSize = 18.sp)
                    }
                    FilledTonalButton(onClick = haptic { showIp = true }, Modifier.weight(1f).height(64.dp), shape = RoundedCornerShape(20.dp)) {
                        Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Add by IP", fontSize = 18.sp)
                    }
                }
            }
        }
    }
    if (showIp) {
        var ip by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showIp = false },
            title = { Text("Add TV by IP address") },
            text = {
                Column {
                    Text("On the TV, open Settings > Network & Internet and tap your Wi-Fi to see its IP address.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        ip, { ip = it.trim() }, singleLine = true, label = { Text("e.g. 192.168.1.50") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = LocalTextStyle.current.copy(fontSize = 22.sp),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = IP.matches(ip), onClick = { showIp = false; onPick(Tv("TV at $ip", ip)) }) {
                    Text("Connect", fontSize = 18.sp)
                }
            },
            dismissButton = { TextButton(onClick = { showIp = false }) { Text("Cancel", fontSize = 18.sp) } },
        )
    }
}

@Composable
private fun Header(t: String) = Text(t, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)

@Composable
private fun TvRow(tv: Tv, onPick: (Tv) -> Unit, onForget: ((Tv) -> Unit)?) {
    Card(
        Modifier.fillMaxWidth().heightIn(min = 84.dp).clickable(onClick = haptic { onPick(tv) }),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Tv, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(tv.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(if (tv.paired) "Paired · ${tv.host}" else tv.host, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onForget != null) IconButton(onClick = { onForget(tv) }, Modifier.size(56.dp)) {
                Icon(Icons.Filled.Delete, "Forget ${tv.name}")
            }
        }
    }
}
