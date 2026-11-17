package nl.thuis.launcher.bios

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * Afleveringenscherm: links de seizoenen (Specials achteraan), rechts de afleveringen van het gekozen
 * seizoen. Opent op het seizoen van je volgende aflevering, met de focus op die aflevering.
 * Bij één seizoen is er geen seizoenkolom.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EpisodesScreen(
    item: SeriesItem,
    detail: SeriesDetail,
    store: SeriesStore,
    onPlay: (Episode) -> Unit,
    onDismiss: () -> Unit,
    onEpisodeMenu: ((Episode) -> Unit)? = null,
    onSeasonMenu: ((Season) -> Unit)? = null,
    newSince: Long? = null,
) {
    val seasons = detail.seasons
    // Alleen bij het openen bepaald: daarna blijf je staan waar je staat, ook als de voortgang verandert.
    val startEpisode = remember(item.id) { NextUp.resolve(seasons, store.episodes, store.lastEpisode(item.id))?.episode }
    val startSeason = remember(item.id) { seasons.indexOfFirst { s -> s.episodes.any { it.id == startEpisode?.id } }.coerceAtLeast(0) }
    var selected by remember(item.id) { mutableIntStateOf(startSeason) }
    val season = seasons[selected.coerceIn(0, seasons.lastIndex)]

    val seasonFocus = remember(item.id) { seasons.map { FocusRequester() } }
    val startFocus = remember { FocusRequester() }
    val listState = remember(selected) {
        val i = if (selected == startSeason) season.episodes.indexOfFirst { it.id == startEpisode?.id } else 0
        LazyListState((i - 1).coerceAtLeast(0))
    }
    LaunchedEffect(item.id) {
        repeat(12) {
            delay(120)
            if (runCatching { startFocus.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(HorizonColors.Background).padding(start = 48.dp, top = 36.dp, end = 48.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    item.title, fontSize = 28.sp, fontWeight = FontWeight.Black, color = HorizonColors.Text,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                Text("Afleveringen", fontSize = 14.sp, color = HorizonColors.Dim, modifier = Modifier.padding(start = 14.dp, bottom = 4.dp))
            }

            Row(Modifier.padding(top = 20.dp).fillMaxSize()) {
                if (seasons.size > 1) {
                    Column(
                        Modifier.width(200.dp).fillMaxHeight().verticalScroll(rememberScrollState())
                            .focusProperties { enter = { seasonFocus[selected.coerceIn(0, seasons.lastIndex)] } }.focusGroup(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        seasons.forEachIndexed { i, s ->
                            SeasonButton(
                                season = s,
                                selected = i == selected,
                                seen = Watched.count(s, store.episodes),
                                onFocused = { selected = i },
                                onLongClick = onSeasonMenu?.let { m -> { m(s) } },
                                modifier = Modifier.focusRequester(seasonFocus[i]),
                            )
                        }
                        Spacer(Modifier.height(40.dp))
                    }
                    Spacer(Modifier.width(28.dp))
                }

                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 2.dp, bottom = 60.dp, start = 4.dp, end = 4.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    itemsIndexed(season.episodes, key = { _, e -> e.id }) { i, ep ->
                        val isStart = if (selected == startSeason && startEpisode != null) ep.id == startEpisode.id else i == 0
                        EpisodeRow(
                            ep = ep,
                            state = store.episodes[ep.id],
                            isNew = NewEpisodes.isNew(ep, newSince),
                            onClick = { onPlay(ep) },
                            onLongClick = onEpisodeMenu?.let { m -> { m(ep) } },
                            modifier = if (isStart) Modifier.focusRequester(startFocus) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonButton(
    season: Season,
    selected: Boolean,
    seen: Int,
    onFocused: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val done = season.episodes.isNotEmpty() && seen == season.episodes.size
    Surface(
        onClick = onFocused,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) HorizonColors.Surface else Color.Transparent,
            contentColor = if (selected) HorizonColors.Text else HorizonColors.Dim,
            focusedContainerColor = HorizonColors.Text,
            focusedContentColor = HorizonColors.Background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth().height(46.dp).onFocusChanged { if (it.isFocused) onFocused() },
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(season.label, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.weight(1f))
            when {
                done -> Text("✓", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                seen > 0 -> Text("$seen van ${season.episodes.size}", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    ep: Episode,
    state: EpState?,
    isNew: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val watched = state?.watched == true
    val progress = state?.takeIf { it.inProgress }
    val rowShape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(rowShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = HorizonColors.Text,
            focusedContainerColor = HorizonColors.Surface,
            focusedContentColor = HorizonColors.Text,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, HorizonColors.Accent), shape = rowShape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        modifier = modifier.fillMaxWidth().height(88.dp),
    ) {
        Row(Modifier.fillMaxSize().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(128.dp).height(72.dp).clip(RoundedCornerShape(6.dp)).background(HorizonColors.SurfaceHigh)) {
                if (ep.image != null) {
                    AsyncImage(ep.image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(
                        ep.number.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = HorizonColors.Dim,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                if (watched) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(5.dp).size(20.dp).background(HorizonColors.Mint, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("✓", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = HorizonColors.OnAccent) }
                }
                if (isNew) {
                    Text(
                        "Nieuw", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = HorizonColors.OnAccent,
                        modifier = Modifier.align(Alignment.TopStart).padding(5.dp).background(HorizonColors.Accent, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                if (progress != null) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).background(HorizonColors.Background.copy(alpha = 0.7f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.fraction).background(HorizonColors.Accent))
                    }
                }
            }
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    "${ep.number}. ${ep.title}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    color = if (watched) HorizonColors.Dim else HorizonColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val left = progress?.let { ((it.durationMs - it.positionMs) / 1000).toInt() }?.takeIf { it > 0 }
                val meta = listOfNotNull(ep.durationSecs?.let { duration(it) }, left?.let { "nog ${duration(it)}" }).joinToString("  ·  ")
                if (meta.isNotBlank()) Text(meta, fontSize = 12.sp, color = HorizonColors.Dim)
                if (!ep.plot.isNullOrBlank()) {
                    Text(
                        ep.plot, fontSize = 12.sp, color = if (watched) HorizonColors.Dim else HorizonColors.Text.copy(alpha = 0.85f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
