package nl.thuis.launcher.bios

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import nl.thuis.launcher.ui.StageBaker

/**
 * Filmscherm in Bios: groot filmbeeld, info en knoppen (Afspelen / Verder kijken, Vanaf begin,
 * Mijn lijst, Trailer). Een Dialog, zodat je na "terug" op dezelfde tegel staat.
 */
@Composable
fun BiosDetail(
    item: VodItem,
    resumeMs: Long,
    inMyList: Boolean,
    onPlay: (fromStart: Boolean) -> Unit,
    onToggleList: () -> Unit,
    onTrailer: (VodInfo?) -> Unit,
    onDismiss: () -> Unit,
) {
    val info by produceState<VodInfo?>(null, item.id) { value = Bios.info(item) }
    val play = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { play.requestFocus() } }

    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(HorizonColors.Background)) {
            val context = LocalContext.current
            val backdrop = info?.backdrop
            if (backdrop != null) {
                AsyncImage(
                    ImageRequest.Builder(context).data(backdrop).size(1280, 720).build(), null,
                    contentScale = ContentScale.Crop, alignment = Alignment.TopEnd, modifier = Modifier.fillMaxSize(),
                )
            } else if (item.poster != null) {
                AsyncImage(
                    item.poster, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 72.dp).fillMaxWidth(0.26f)
                        .aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
                )
            }
            Image(StageBaker.scrim, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())

            Column(Modifier.align(Alignment.BottomStart).padding(start = 56.dp, bottom = 48.dp, end = 48.dp).widthIn(max = 600.dp)) {
                Text(
                    item.title, fontSize = 42.sp, fontWeight = FontWeight.Black, color = HorizonColors.Text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 46.sp,
                )
                VodMeta(item, info, Modifier.padding(top = 10.dp))
                if (resumeMs > 0) {
                    Text(
                        "Je was bij ${timecode(resumeMs)}", fontSize = 13.sp, color = HorizonColors.Dim,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Text(
                    info?.plot ?: if (info == null) "" else "Geen beschrijving beschikbaar.",
                    fontSize = 15.sp, color = HorizonColors.Text.copy(alpha = 0.9f), lineHeight = 22.sp,
                    maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 14.dp),
                )
                val people = listOfNotNull(
                    info?.director?.let { "Regie: $it" },
                    info?.cast?.split(',')?.take(4)?.joinToString(", ") { it.trim() }?.let { "Met: $it" },
                ).joinToString("   ")
                if (people.isNotBlank()) {
                    Text(people, fontSize = 13.sp, color = HorizonColors.Dim, maxLines = 2, modifier = Modifier.padding(top = 10.dp))
                }

                Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BiosButton(
                        if (resumeMs > 0) "▶  Verder kijken" else "▶  Afspelen", primary = true,
                        modifier = Modifier.focusRequester(play),
                    ) { onPlay(false) }
                    if (resumeMs > 0) BiosButton("↺  Vanaf begin") { onPlay(true) }
                    BiosButton(if (inMyList) "✓  Mijn lijst" else "+  Mijn lijst") { onToggleList() }
                    BiosButton("Trailer") { onTrailer(info) }
                }
            }
        }
    }
}

@Composable
fun BiosButton(label: String, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = ButtonDefaults.shape(RoundedCornerShape(50)),
        colors = ButtonDefaults.colors(
            containerColor = if (primary) HorizonColors.Accent.copy(alpha = 0.85f) else HorizonColors.SurfaceHigh.copy(alpha = 0.85f),
            contentColor = if (primary) HorizonColors.OnAccent else HorizonColors.Text,
            focusedContainerColor = if (primary) HorizonColors.Accent else HorizonColors.Text,
            focusedContentColor = HorizonColors.OnAccent,
        ),
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = Poppins, modifier = Modifier.padding(horizontal = 6.dp))
    }
}
