package nl.thuis.launcher.bios

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/** Eén keuze in het menu. */
data class MenuChoice(val label: String, val action: () -> Unit)

/**
 * Menu na lang OK op een aflevering of seizoen (ontwerp bord D).
 * Het menu opent terwijl OK nog is ingedrukt: het loslaten van die knop mag geen keuze activeren.
 * OK telt pas nadat je hem één keer hebt losgelaten (zelfde aanpak als het app-menu van de launcher).
 */
@Composable
fun EpisodeMenu(kicker: String, title: String, subtitle: String, choices: List<MenuChoice>, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { repeat(5) { if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect; delay(60) } }

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
                .width(360.dp)
                .background(HorizonColors.Surface, RoundedCornerShape(14.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(kicker, fontSize = 12.sp, color = HorizonColors.Dim)
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = HorizonColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 13.sp, color = HorizonColors.Dim, modifier = Modifier.padding(bottom = 8.dp))
            choices.forEachIndexed { i, c ->
                BiosButton(
                    c.label,
                    modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                ) { c.action(); onDismiss() }
            }
            BiosButton("Sluiten", modifier = Modifier.fillMaxWidth()) { onDismiss() }
        }
    }
}
