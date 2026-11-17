package nl.thuis.launcher.bios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Filter: links de keuzes als pillen (genres, periode, score, sortering), rechts direct de films
 * uit je aanbod die erbij passen. Met genres zoekt TMDB mee; zonder genres filtert Bios zelf.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BiosFilterScreen(
    index: VodIndex,
    series: SeriesIndex?,
    store: SeriesStore,
    filter: BiosFilter,
    onFilter: (BiosFilter) -> Unit,
    onOpen: (VodItem) -> Unit,
    onOpenSeries: (SeriesItem) -> Unit,
    firstFocus: FocusRequester,
) {
    var results by remember { mutableStateOf<List<Listing>?>(null) }
    LaunchedEffect(filter, index, series) {
        results = null
        delay(350) // wacht tot je klaar bent met kiezen
        results = if (filter.series) {
            val idx = series ?: return@LaunchedEffect
            val ids = filter.tvGenres.map { it.id }
            (if (ids.isNotEmpty() && SeriesGenres.hasTmdb) SeriesGenres.filter(idx, ids, filter.period, filter.minScore, filter.sort)
            else withContext(Dispatchers.Default) { SeriesGenres.filterCatalog(idx, filter) }).map { Listing.Show(it) }
        } else {
            (if (filter.genres.isNotEmpty() && BiosGenres.hasTmdb) BiosGenres.filter(index, filter)
            else withContext(Dispatchers.Default) { filterCatalog(index, filter) }).map { Listing.Film(it) }
        }
    }
    val what = if (filter.series) "series" else "films"

    Row(Modifier.fillMaxSize().padding(start = ContentStart, top = NavHeight + 4.dp, end = 32.dp)) {
        Column(Modifier.width(430.dp).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Group("Soort")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Films", !filter.series, modifier = if (!filter.series) Modifier.focusRequester(firstFocus) else Modifier) { onFilter(filter.switchTo(false)) }
                Pill("Series", filter.series, modifier = if (filter.series) Modifier.focusRequester(firstFocus) else Modifier) { onFilter(filter.switchTo(true)) }
            }
            val chosen = if (filter.series) filter.tvGenres.size else filter.genres.size
            Group("Genres" + if (chosen > 1) "  ·  $what die alle gekozen genres hebben" else "")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (filter.series) {
                    TvGenre.entries.forEach { g ->
                        Pill(g.label, selected = g in filter.tvGenres) {
                            onFilter(filter.copy(tvGenres = if (g in filter.tvGenres) filter.tvGenres - g else filter.tvGenres + g))
                        }
                    }
                } else {
                    Genre.entries.forEach { g ->
                        Pill(g.label, selected = g in filter.genres) {
                            onFilter(filter.copy(genres = if (g in filter.genres) filter.genres - g else filter.genres + g))
                        }
                    }
                }
            }
            Group("Periode")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPeriod.entries.forEach { p -> Pill(p.label, p == filter.period) { onFilter(filter.copy(period = p)) } }
            }
            Group("Minimale score")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.0 to "Alles", 6.0 to "6+", 7.0 to "7+", 8.0 to "8+").forEach { (v, l) ->
                    Pill(l, filter.minScore == v) { onFilter(filter.copy(minScore = v)) }
                }
            }
            Group("Sorteren")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterSort.entries.forEach { s -> Pill(s.label, s == filter.sort) { onFilter(filter.copy(sort = s)) } }
            }
            if (!filter.isEmpty || filter.sort != FilterSort.POPULAR) {
                Row(Modifier.padding(top = 18.dp)) { Pill("✕  Alles wissen", false) { onFilter(BiosFilter(series = filter.series)) } }
            }
        }

        Column(Modifier.padding(start = 28.dp).fillMaxSize()) {
            val r = results
            Text(
                when {
                    r == null && filter.series && series == null -> "Series laden…"
                    r == null -> "Zoeken…"
                    r.isEmpty() -> "Geen $what gevonden met deze keuzes"
                    else -> "${r.size} $what" + if (r.size >= 60 && (filter.genres.isNotEmpty() || filter.tvGenres.isNotEmpty())) " (de bekendste)" else ""
                },
                color = HorizonColors.Dim, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            )
            if ((filter.genres.isNotEmpty() || filter.tvGenres.isNotEmpty()) && !BiosGenres.hasTmdb) {
                Text("Voor filteren op genre is een TMDB-token nodig (Instellingen).", color = HorizonColors.Accent, fontSize = 13.sp)
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(r.orEmpty(), key = { it.listKey }) { item ->
                    ListingTile(item, store, width = 100.dp, onOpen = onOpen, onOpenSeries = onOpenSeries)
                }
            }
        }
    }
}

@Composable
private fun Group(title: String) {
    Text(title, color = HorizonColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}

@Composable
private fun Pill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) HorizonColors.Accent else HorizonColors.Surface,
            contentColor = if (selected) HorizonColors.OnAccent else HorizonColors.Text,
            focusedContainerColor = HorizonColors.Text,
            focusedContentColor = HorizonColors.Background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        modifier = modifier,
    ) {
        Text(
            (if (selected) "✓ " else "") + label, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}
