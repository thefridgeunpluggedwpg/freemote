package app.freemote.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed interface PairState {
    data object Starting : PairState
    data class EnterCode(val error: String?) : PairState
    data object Checking : PairState
    data class Failed(val message: String) : PairState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairScreen(tvName: String, state: PairState, onSubmit: (String) -> Unit, onRetry: () -> Unit, onBack: () -> Unit) {
    var code by remember { mutableStateOf("") }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Pair with $tvName") },
                navigationIcon = { IconButton(onClick = onBack, Modifier.size(56.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (state) {
                PairState.Starting -> {
                    Spacer(Modifier.height(40.dp))
                    CircularProgressIndicator(Modifier.size(56.dp))
                    Text("Asking your TV for a code…", fontSize = 20.sp)
                }
                is PairState.Failed -> {
                    Text(state.message, fontSize = 18.sp, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Button(onClick = haptic(onRetry), Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(20.dp)) {
                        Text("Try again", fontSize = 20.sp)
                    }
                }
                else -> {
                    Text("Type the 6-character code shown on your TV", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    val ok = code.length == 6
                    OutlinedTextField(
                        value = code,
                        onValueChange = { v -> code = v.uppercase().filter { it in "0123456789ABCDEF" }.take(6) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 40.sp, fontFamily = FontFamily.Monospace, letterSpacing = 8.sp, textAlign = TextAlign.Center,
                        ),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrect = false, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (ok) onSubmit(code) }),
                        isError = state is PairState.EnterCode && state.error != null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                    )
                    if (state is PairState.EnterCode && state.error != null) {
                        Text(state.error, color = MaterialTheme.colorScheme.error, fontSize = 16.sp, textAlign = TextAlign.Center)
                    }
                    Text("Letters are A–F and numbers 0–9.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = haptic { onSubmit(code) }, enabled = ok && state !is PairState.Checking,
                        modifier = Modifier.fillMaxWidth().height(68.dp), shape = RoundedCornerShape(20.dp),
                    ) {
                        if (state is PairState.Checking) CircularProgressIndicator(Modifier.size(28.dp))
                        else Text("Pair", fontSize = 22.sp)
                    }
                }
            }
        }
    }
}
