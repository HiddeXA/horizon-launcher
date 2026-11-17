package nl.thuis.launcher.bios

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage

/**
 * Kaart rechtsonder in de speler, in de laatste 10 s van een aflevering (ontwerp bord F).
 * De knoppen krijgen geen echte focus: de speler stuurt de toetsen zelf ([focused] 0 = Nu afspelen,
 * 1 = Annuleren), zodat de bediening van de speler niet met de kaart om de focus vecht.
 */
@Composable
fun NextEpisodeCard(next: Episode, secondsLeft: Int, focused: Int) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .width(380.dp)
            .background(HorizonColors.Surface.copy(alpha = 0.96f), shape)
            .border(BorderStroke(1.dp, HorizonColors.Accent.copy(alpha = 0.4f)), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(112.dp).height(63.dp).clip(RoundedCornerShape(6.dp)).background(HorizonColors.SurfaceHigh)) {
                if (next.image != null) AsyncImage(next.image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(
                    buildAnnotatedString {
                        append("Volgende aflevering over ")
                        withStyle(SpanStyle(color = HorizonColors.Accent, fontWeight = FontWeight.Bold)) { append("$secondsLeft s") }
                    },
                    fontSize = 12.sp, color = HorizonColors.Dim,
                )
                Text(
                    "${next.label}  ${next.title}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = HorizonColors.Text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                next.durationSecs?.let { Text(duration(it), fontSize = 12.sp, color = HorizonColors.Dim) }
            }
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(HorizonColors.Text.copy(alpha = 0.2f), RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(NextCard.fraction(secondsLeft)).background(HorizonColors.Accent, RoundedCornerShape(2.dp)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CardButton("▶  Nu afspelen", primary = true, focused = focused == 0, modifier = Modifier.weight(1f))
            CardButton("Annuleren", primary = false, focused = focused == 1, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CardButton(label: String, primary: Boolean, focused: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(50)
    val bg = when {
        primary -> HorizonColors.Accent
        focused -> HorizonColors.Text
        else -> HorizonColors.SurfaceHigh
    }
    val fg = if (primary || focused) HorizonColors.OnAccent else HorizonColors.Text
    Box(
        modifier
            .height(44.dp)
            .then(if (focused) Modifier.border(BorderStroke(2.dp, HorizonColors.Text), shape).padding(3.dp) else Modifier)
            .background(bg, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = Poppins, color = fg)
    }
}
