package nl.thuis.launcher.bios

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/** Vaste kleuren uit het ontwerp (design.md), naast HorizonColors. */
internal object FilterTokens {
    val SurfaceAlt = Color(0xFF222C4D)
    val CheckBorder = Color(0xFF5A6588)
    val TagOnFocus = Color(0xFF3A4566)
    val PanelShape = RoundedCornerShape(14.dp)
    val OptionShape = RoundedCornerShape(10.dp)
}

/** Paneel boven de resultaten: rand, schaduw, en de focus kan er niet uit (Terug sluit het). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PanelBox(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .shadow(24.dp, FilterTokens.PanelShape, ambientColor = Color(0xFF060B1A), spotColor = Color(0xFF060B1A))
            .background(HorizonColors.Surface, FilterTokens.PanelShape)
            .border(BorderStroke(1.dp, HorizonColors.SurfaceHigh), FilterTokens.PanelShape)
            .focusProperties { exit = { FocusRequester.Cancel } }
            .focusGroup(),
    ) { content() }
}

/** Genrepaneel: Alles en Films in 4 kolommen, Series in 5. OK zet een genre aan of uit. */
@Composable
fun GenrePanel(filter: BiosFilter, onToggle: (GenreOption) -> Unit, modifier: Modifier = Modifier) {
    val options = FilterGenres.forKind(filter.kind)
    val columns = if (filter.kind == FilterKind.SERIES) 5 else 4
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { repeat(6) { delay(40); if (runCatching { first.requestFocus() }.isSuccess) return@LaunchedEffect } }
    val (title, sub) = when (filter.kind) {
        FilterKind.ALL -> "Genres" to "Kies er een of meer: je ziet titels die alle gekozen genres hebben"
        FilterKind.FILMS -> "Genres voor films" to "Je ziet films die alle gekozen genres hebben"
        FilterKind.SERIES -> "Genres voor series" to "Je ziet series die alle gekozen genres hebben"
    }
    // Focus begint op het eerste gekozen genre (of het eerste genre).
    val focusIndex = options.indexOfFirst { it.key in filter.genres }.coerceAtLeast(0)
    PanelBox(modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = HorizonColors.Text)
                Spacer(Modifier.width(12.dp))
                Text(sub, fontSize = 13.sp, color = HorizonColors.Dim, modifier = Modifier.weight(1f).padding(bottom = 2.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${filter.genres.size} gekozen", fontSize = 13.sp, color = HorizonColors.Dim, modifier = Modifier.padding(bottom = 2.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.chunked(columns).forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { c, o ->
                            val i = r * columns + c
                            GenreOptionButton(
                                o, on = o.key in filter.genres, showTag = filter.kind == FilterKind.ALL, narrow = columns == 5,
                                modifier = Modifier.weight(1f).let { if (i == focusIndex) it.focusRequester(first) else it },
                            ) { onToggle(o) }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("OK zet een genre aan of uit", fontSize = 12.sp, color = HorizonColors.Dim)
                Text("Terug sluit het paneel", fontSize = 12.sp, color = HorizonColors.Dim)
            }
        }
    }
}

@Composable
private fun GenreOptionButton(o: GenreOption, on: Boolean, showTag: Boolean, narrow: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(FilterTokens.OptionShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = FilterTokens.SurfaceAlt, contentColor = HorizonColors.Text,
            focusedContainerColor = HorizonColors.Text, focusedContentColor = HorizonColors.Background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        modifier = modifier.height(48.dp).onFocusChanged { focused = it.isFocused },
    ) {
        // Bij 5 kolommen (series) iets krapper, zodat lange namen als "Documentaire" heel blijven.
        Row(Modifier.padding(horizontal = if (narrow) 10.dp else 12.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(18.dp)
                    .background(if (on) HorizonColors.Accent else Color.Transparent, RoundedCornerShape(5.dp))
                    .let { if (on) it else it.border(2.dp, if (focused) HorizonColors.Background else FilterTokens.CheckBorder, RoundedCornerShape(5.dp)) },
                contentAlignment = Alignment.Center,
            ) {
                if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = HorizonColors.OnAccent, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(if (narrow) 8.dp else 10.dp))
            Column {
                Text(
                    o.label, fontSize = if (narrow) 13.sp else 14.sp, lineHeight = 17.sp,
                    fontWeight = if (on || focused) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                val tag = when {
                    !showTag -> null
                    o.onlyFilms -> "alleen films"
                    o.onlySeries -> "alleen series"
                    else -> null
                }
                if (tag != null) Text(tag, fontSize = 11.sp, lineHeight = 13.sp, color = if (focused) FilterTokens.TagOnFocus else HorizonColors.Dim)
            }
        }
    }
}

/** Keuzepaneel met één keuze (Periode, Score, Sorteren): OK kiest en sluit. */
@Composable
fun <T> ChoicePanel(title: String, options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { repeat(6) { delay(40); if (runCatching { focus.requestFocus() }.isSuccess) return@LaunchedEffect } }
    PanelBox(modifier.width(240.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HorizonColors.Dim, modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 8.dp))
            options.forEach { (value, label) ->
                val on = value == selected
                var focused by remember { mutableStateOf(false) }
                Surface(
                    onClick = { onPick(value) },
                    shape = ClickableSurfaceDefaults.shape(FilterTokens.OptionShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent, contentColor = HorizonColors.Text,
                        focusedContainerColor = HorizonColors.Text, focusedContentColor = HorizonColors.Background,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                    modifier = Modifier.fillMaxWidth().height(44.dp).onFocusChanged { focused = it.isFocused }
                        .let { if (on) it.focusRequester(focus) else it },
                ) {
                    Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            label, fontSize = 15.sp, modifier = Modifier.weight(1f),
                            fontWeight = if (on || focused) FontWeight.Bold else FontWeight.Medium,
                            color = if (on && !focused) HorizonColors.Accent else Color.Unspecified,
                        )
                        if (on) Icon(Icons.Filled.Check, contentDescription = "gekozen", tint = HorizonColors.Accent, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Text("OK kiest en sluit  ·  Terug sluit", fontSize = 12.sp, color = HorizonColors.Dim, modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp))
        }
    }
}

/** Kleine draaiende ring (laden). */
@Composable
fun FilterSpinner(size: Dp = 16.dp) {
    val t = rememberInfiniteTransition(label = "ring")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "hoek")
    val track = HorizonColors.SurfaceHigh
    val accent = HorizonColors.Accent
    Canvas(Modifier.size(size).rotate(angle)) {
        val w = this.size.minDimension * 0.17f
        val inset = w / 2
        val arcSize = Size(this.size.width - w, this.size.height - w)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
        drawArc(accent, -90f, 90f, false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
    }
}
