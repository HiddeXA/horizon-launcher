package nl.thuis.launcher.bios

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import nl.thuis.launcher.ui.StageBaker

@Immutable
data class SeriesRow(val key: String, val title: String, val items: List<SeriesItem>, val showProgress: Boolean = false)

private val SeriesBillboardHeight = 290.dp
private val SeriesTileShape = RoundedCornerShape(6.dp)

/**
 * Rijen voor de tab Series, in vaste volgorde:
 * Nieuwe series, Top series, Verder kijken, (Aanbevolen series), Mijn lijst, en daarna de genres.
 * Zonder TMDB: de categorieën van je aanbieder.
 */
fun buildSeriesRows(idx: SeriesIndex, genreRows: Map<String, List<Int>>?, store: SeriesStore): List<SeriesRow> {
    val rows = ArrayList<SeriesRow>()
    val thisYear = java.time.LocalDate.now().year

    val fresh = idx.items.asSequence()
        .filter { (it.year?.toIntOrNull() ?: 0) >= thisYear - 1 }
        .sortedByDescending { it.modified }
        .distinctBy { it.key }
        .take(40).toList()
    rows += SeriesRow("new", "Nieuwe series", fresh.takeIf { it.size >= 6 } ?: idx.newest)

    val top = genreRows?.get("top").orEmpty().mapNotNull { idx.byId[it] }
    if (top.size >= 6) rows += SeriesRow("genre:top", "Top series", top)

    val going = store.summaries.entries.filter { !it.value.finished }.sortedByDescending { it.value.updated }.mapNotNull { idx.byId[it.key] }
    if (going.isNotEmpty()) rows += SeriesRow("continue", "Verder kijken", going, showProgress = true)

    val list = store.myList.mapNotNull { idx.byId[it.first] }
    if (list.isNotEmpty()) rows += SeriesRow("mylist", "Mijn lijst", list)

    if (genreRows != null && genreRows.values.any { it.size >= 6 }) {
        SeriesShelves.filterNot { it.pinned }.forEach { shelf ->
            val items = genreRows[shelf.key].orEmpty().mapNotNull { idx.byId[it] }
            if (items.size >= 6) rows += SeriesRow("genre:${shelf.key}", shelf.title, items)
        }
    } else if (genreRows != null) {
        idx.categories.forEach { c ->
            val items = idx.byCategory[c.id]?.distinctBy { it.key + (it.year ?: "") }?.take(40).orEmpty()
            if (items.isNotEmpty()) rows += SeriesRow("cat:${c.id}", c.name, items)
        }
    }
    return rows
}

/** Poster-tegel voor een serie. [label] ("S2 · A4") en [progress] bij Verder kijken; [band] voor "Nieuwe aflevering". */
@Composable
fun SeriesTile(
    item: SeriesItem,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 104.dp,
    label: String? = null,
    progress: Float? = null,
    band: String? = null,
) {
    Column(Modifier.width(width)) {
        Card(
            onClick = onClick,
            shape = CardDefaults.shape(SeriesTileShape),
            scale = CardDefaults.scale(focusedScale = 1.08f),
            border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, HorizonColors.Accent), shape = SeriesTileShape)),
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .onFocusChanged { if (it.isFocused) onFocused() },
        ) {
            Box(Modifier.fillMaxSize().background(HorizonColors.Surface)) {
                Text(
                    item.title, color = HorizonColors.Dim, fontSize = 12.sp, textAlign = TextAlign.Center,
                    maxLines = 4, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp),
                )
                if (item.poster != null) {
                    AsyncImage(item.poster, item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                if (band != null) {
                    Text(
                        band, color = HorizonColors.OnAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        maxLines = 1, modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().background(HorizonColors.Accent).padding(vertical = 3.dp),
                    )
                }
                if (!label.isNullOrBlank()) {
                    Text(
                        label, color = HorizonColors.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).fillMaxWidth()
                            .background(HorizonColors.Background.copy(alpha = 0.82f)).padding(vertical = 3.dp),
                    )
                }
                if (progress != null) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).background(HorizonColors.Background.copy(alpha = 0.7f))) {
                        if (progress > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(HorizonColors.Accent))
                    }
                }
            }
        }
    }
}

/**
 * Tab Series: zelfde opbouw als de tab Films. Een groot beeld van de serie met focus bovenaan,
 * daaronder rijen die per balk scrollen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeriesHome(
    rows: List<SeriesRow>,
    summaries: Map<Int, SeriesSummary>,
    onOpen: (SeriesItem) -> Unit,
    firstFocus: FocusRequester,
) {
    var focused by remember { mutableStateOf(rows.firstOrNull()?.items?.firstOrNull()) }
    var shown by remember { mutableStateOf(focused) }
    LaunchedEffect(Unit) {
        snapshotFlow { focused }.collectLatest { delay(220); shown = it }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        SeriesBillboard({ shown }, Modifier.fillMaxWidth().height(SeriesBillboardHeight + 120.dp))

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(SeriesBillboardHeight)) { SeriesBillboardInfo({ shown }, summaries) }

            val rowSpec = LocalBringIntoViewSpec.current
            CompositionLocalProvider(LocalBringIntoViewSpec provides SeriesNoAutoScroll) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    itemsIndexed(rows, key = { _, r -> r.key }, contentType = { _, _ -> "row" }) { index, row ->
                        Column(Modifier.onFocusChanged { if (it.hasFocus) scope.launch { listState.animateScrollToItem(index) } }) {
                            Text(
                                row.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = HorizonColors.Text,
                                modifier = Modifier.padding(start = ContentStart, bottom = 6.dp),
                            )
                            CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                                LazyRow(
                                    contentPadding = PaddingValues(start = ContentStart, end = 48.dp, top = 6.dp, bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    itemsIndexed(row.items, key = { _, it -> it.id }) { i, item ->
                                        val sum = if (row.showProgress) summaries[item.id] else null
                                        SeriesTile(
                                            item = item,
                                            onClick = { onOpen(item) },
                                            onFocused = { focused = item },
                                            label = sum?.nextLabel,
                                            progress = sum?.nextFraction,
                                            modifier = if (index == 0 && i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Achtergrond van het billboard: beeld van de serie paginabreed, anders de poster rechts. */
@Composable
private fun SeriesBillboard(item: () -> SeriesItem?, modifier: Modifier) {
    val it = item() ?: return
    val meta = SeriesGenres.meta[it.id]
    val detail by produceState<SeriesDetail?>(Series.cachedDetail(it.id), it.id) {
        if (value == null && meta?.backdrop == null) { delay(350); value = Series.detail(it).getOrNull() }
    }
    val context = LocalContext.current
    Box(modifier) {
        val backdrop = meta?.backdrop ?: detail?.backdrop
        if (backdrop != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(backdrop).size(1280, 720).build(),
                contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.TopEnd,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (it.poster != null) {
            AsyncImage(
                it.poster, null, contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = NavHeight, end = 64.dp)
                    .height(SeriesBillboardHeight - NavHeight - 10.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
            )
        }
        Image(StageBaker.scrim, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun SeriesBillboardInfo(item: () -> SeriesItem?, summaries: Map<Int, SeriesSummary>) {
    val it = item() ?: return
    val meta = SeriesGenres.meta[it.id]
    // Aantal seizoenen en omschrijving komen uit de afleveringenlijst; die halen we op als je even stilstaat.
    val detail by produceState<SeriesDetail?>(Series.cachedDetail(it.id), it.id) {
        if (value == null) { delay(450); value = Series.detail(it).getOrNull() }
    }
    Column(
        Modifier.padding(start = ContentStart, top = NavHeight).widthIn(max = 520.dp).fillMaxHeight(),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            it.title, fontSize = 36.sp, fontWeight = FontWeight.Black, color = HorizonColors.Text,
            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 40.sp,
        )
        SeriesMeta(it, detail, Modifier.padding(top = 8.dp))
        val sum = summaries[it.id]
        if (sum != null && !sum.finished && sum.nextLabel.isNotBlank()) {
            Text(
                "Verder met ${sum.nextLabel}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HorizonColors.Accent,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Text(
            meta?.overview ?: detail?.plot.orEmpty(), fontSize = 14.sp, color = HorizonColors.Text.copy(alpha = 0.85f), lineHeight = 20.sp,
            maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp, bottom = 12.dp),
        )
    }
}

/** "★ 8,1   2021 · 3 seizoenen · Drama, Misdaad" */
@Composable
fun SeriesMeta(item: SeriesItem, detail: SeriesDetail?, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val meta = SeriesGenres.meta[item.id]
        val rating = item.rating ?: meta?.vote?.takeIf { it > 0 }
        if (rating != null) {
            Text("★ ${"%.1f".format(rating)}", color = HorizonColors.Mint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
        }
        val tmdbGenres = meta?.genres?.mapNotNull { TvGenre.byId[it]?.label }?.take(2)?.joinToString(", ")
        val seasons = detail?.regularSeasons?.takeIf { it > 0 }
        val parts = listOfNotNull(
            meta?.year ?: item.year ?: detail?.releaseDate?.take(4),
            seasons?.let { if (it == 1) "1 seizoen" else "$it seizoenen" },
            (tmdbGenres?.takeIf { it.isNotBlank() } ?: detail?.genre?.split(',', '/')?.take(2)?.joinToString(", ") { it.trim() })?.takeIf { it.isNotBlank() },
        )
        Text(parts.joinToString("  ·  "), color = HorizonColors.Dim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
private val SeriesNoAutoScroll = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}
