package nl.thuis.launcher.bios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Keys = ("abcdefghijklmnopqrstuvwxyz1234567890").map { it.toString() }

/**
 * Zoeken met een eigen toetsenbord voor de afstandsbediening (zoals bij grote streamingapps):
 * links de letters, rechts direct de resultaten terwijl je typt.
 */
@Composable
fun BiosSearch(
    index: VodIndex,
    initialQuery: String,
    onOpen: (VodItem) -> Unit,
    firstFocus: FocusRequester,
) {
    var query by remember(initialQuery) { mutableStateOf(initialQuery.lowercase()) }
    var results by remember { mutableStateOf(emptyList<VodItem>()) }
    LaunchedEffect(query, index) {
        delay(120) // pas zoeken als je even stopt met typen
        results = withContext(Dispatchers.Default) { index.search(query) }
    }

    Row(Modifier.fillMaxSize().padding(start = ContentStart, top = NavHeight + 8.dp, end = 32.dp)) {
        Column(Modifier.width(300.dp)) {
            Box(
                Modifier.fillMaxWidth().height(48.dp).background(HorizonColors.Surface, RoundedCornerShape(6.dp)).padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    if (query.isEmpty()) "Zoek een film…" else query + "▏",
                    color = if (query.isEmpty()) HorizonColors.Dim else HorizonColors.Text,
                    fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Key("spatie", Modifier.weight(1f)) { query += " " }
                    Key("⌫", Modifier.weight(1f)) { query = query.dropLast(1) }
                    Key("wis", Modifier.weight(1f)) { query = "" }
                }
                Keys.chunked(6).forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEachIndexed { c, k ->
                            Key(
                                k,
                                Modifier.weight(1f).then(if (r == 0 && c == 0) Modifier.focusRequester(firstFocus) else Modifier),
                            ) { query += k }
                        }
                        repeat(6 - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }

        Column(Modifier.padding(start = 28.dp).fillMaxSize()) {
            Text(
                when {
                    query.isBlank() -> "Typ een titel om te zoeken in ${index.items.size} films"
                    results.isEmpty() -> "Niets gevonden voor \"$query\""
                    else -> "Resultaten"
                },
                color = HorizonColors.Dim, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(results, key = { it.id }) { item ->
                    VodTile(item, onClick = { onOpen(item) }, onFocused = {}, width = 100.dp)
                }
            }
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = HorizonColors.Text.copy(alpha = 0.06f),
            contentColor = HorizonColors.Text,
            focusedContainerColor = HorizonColors.Text,
            focusedContentColor = HorizonColors.Background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = modifier.height(40.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, fontSize = if (label.length > 1) 13.sp else 17.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** Mijn lijst: een eenvoudig raster. */
@Composable
fun BiosMyList(items: List<VodItem>, onOpen: (VodItem) -> Unit, firstFocus: FocusRequester) {
    Column(Modifier.fillMaxSize().padding(start = ContentStart, top = NavHeight + 8.dp, end = 32.dp)) {
        Text("Mijn lijst", fontSize = 28.sp, fontWeight = FontWeight.Black, color = HorizonColors.Text)
        if (items.isEmpty()) {
            EmptyMessage("Je lijst is nog leeg", "Kies op een filmscherm \"+ Mijn lijst\" om films te bewaren voor later.")
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items.size, key = { items[it].id }) { i ->
                val item = items[i]
                VodTile(
                    item, onClick = { onOpen(item) }, onFocused = {}, width = 110.dp,
                    modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
        }
    }
}
