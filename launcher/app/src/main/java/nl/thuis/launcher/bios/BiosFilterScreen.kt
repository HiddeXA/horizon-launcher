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
    filter: BiosFilter,
    onFilter: (BiosFilter) -> Unit,
    onOpen: (VodItem) -> Unit,
    firstFocus: FocusRequester,
) {
    var results by remember { mutableStateOf<List<VodItem>?>(null) }
    LaunchedEffect(filter, index) {
        results = null
        delay(350) // wacht tot je klaar bent met kiezen
        results = if (filter.genres.isNotEmpty() && BiosGenres.hasTmdb) BiosGenres.filter(index, filter)
        else withContext(Dispatchers.Default) { filterCatalog(index, filter) }
    }

    Row(Modifier.fillMaxSize().padding(start = ContentStart, top = NavHeight + 4.dp, end = 32.dp)) {
        Column(Modifier.width(430.dp).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            Group("Genres" + if (filter.genres.size > 1) "  ·  films die alle gekozen genres hebben" else "")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Genre.entries.forEachIndexed { i, g ->
                    Pill(
                        g.label, selected = g in filter.genres,
                        modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    ) { onFilter(filter.copy(genres = if (g in filter.genres) filter.genres - g else filter.genres + g)) }
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
                Row(Modifier.padding(top = 18.dp)) { Pill("✕  Alles wissen", false) { onFilter(BiosFilter()) } }
            }
        }

        Column(Modifier.padding(start = 28.dp).fillMaxSize()) {
            val r = results
            Text(
                when {
                    r == null -> "Zoeken…"
                    r.isEmpty() -> "Geen films gevonden met deze keuzes"
                    else -> "${r.size} films" + if (r.size >= 60 && filter.genres.isNotEmpty()) " (de bekendste)" else ""
                },
                color = HorizonColors.Dim, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            )
            if (filter.genres.isNotEmpty() && !BiosGenres.hasTmdb) {
                Text("Voor filteren op genre is een TMDB-token nodig (Instellingen).", color = HorizonColors.Accent, fontSize = 13.sp)
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(r.orEmpty(), key = { it.id }) { item ->
                    VodTile(item, onClick = { onOpen(item) }, onFocused = {}, width = 100.dp)
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
