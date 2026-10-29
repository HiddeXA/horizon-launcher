package nl.thuis.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import nl.thuis.launcher.AppEntry

/** Menu na lang OK indrukken op een app. */
@Composable
fun AppMenu(
    app: AppEntry,
    pinned: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onTogglePin: () -> Unit,
    onMove: (Int) -> Unit,
    onAppInfo: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    // Het menu opent terwijl OK nog ingedrukt is (lang indrukken). Het loslaten van die knop mag
    // geen menuknop activeren: OK-toetsen tellen pas nadat je hem één keer hebt losgelaten.
    var armed by remember { mutableStateOf(false) }
    var sawHeld by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(700); if (!sawHeld) armed = true }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .onPreviewKeyEvent { e ->
                    val ok = e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter
                    if (armed || !ok) return@onPreviewKeyEvent false
                    if (e.type == KeyEventType.KeyDown) sawHeld = true
                    if (e.type == KeyEventType.KeyUp) armed = true
                    true
                }
                .width(320.dp).background(Palette.Surface, RoundedCornerShape(16.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(app.label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text,
                modifier = Modifier.padding(bottom = 8.dp))

            MenuButton(if (pinned) "Losmaken uit dock" else "Vastzetten in dock",
                Modifier.focusRequester(first)) { onTogglePin(); onDismiss() }
            if (pinned && canMoveLeft) MenuButton("Naar links") { onMove(-1) }
            if (pinned && canMoveRight) MenuButton("Naar rechts") { onMove(+1) }
            MenuButton("App-info") { onAppInfo(); onDismiss() }
            MenuButton("Annuleren") { onDismiss() }
        }
    }
}

@Composable
private fun MenuButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.fillMaxWidth()) { Text(label) }
}
