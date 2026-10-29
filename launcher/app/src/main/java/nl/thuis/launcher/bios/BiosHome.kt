package nl.thuis.launcher.bios

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import nl.thuis.launcher.ui.StageBaker

@Immutable
data class BiosRow(val key: String, val title: String, val items: List<VodItem>, val showProgress: Boolean = false)

private val BillboardHeight = 290.dp

/**
 * Startscherm: een grote "billboard" bovenaan die de film met focus toont, met daaronder rijen
 * die per balk scrollen. Het billboard blijft staan; alleen de rijen bewegen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BiosHome(
    rows: List<BiosRow>,
    progress: Map<Int, ProgressStore.Entry>,
    onOpen: (VodItem) -> Unit,
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
        Billboard({ shown }, Modifier.fillMaxWidth().height(BillboardHeight + 120.dp))

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(BillboardHeight)) { BillboardInfo({ shown }) }

            val rowSpec = LocalBringIntoViewSpec.current
            CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScroll) {
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
                                        VodTile(
                                            item = item,
                                            onClick = { onOpen(item) },
                                            onFocused = { focused = item },
                                            progress = if (row.showProgress) progress[item.id]?.fraction else null,
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

/** Achtergrond van het billboard: filmbeeld paginabreed, anders de poster rechts. */
@Composable
private fun Billboard(item: () -> VodItem?, modifier: Modifier) {
    val it = item() ?: return
    val meta = BiosGenres.meta[it.id]
    val info by produceState<VodInfo?>(null, it.id) { if (meta?.backdrop == null) value = Bios.info(it) }
    val context = LocalContext.current
    Box(modifier) {
        val backdrop = meta?.backdrop ?: info?.backdrop
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
                    .height(BillboardHeight - NavHeight - 10.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
            )
        }
        Image(StageBaker.scrim, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun BillboardInfo(item: () -> VodItem?) {
    val it = item() ?: return
    val meta = BiosGenres.meta[it.id]
    val info by produceState<VodInfo?>(null, it.id) { if (meta?.overview == null) value = Bios.info(it) }
    Column(
        Modifier.padding(start = ContentStart, top = NavHeight).widthIn(max = 520.dp).fillMaxHeight(),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(
            it.title, fontSize = 36.sp, fontWeight = FontWeight.Black, color = HorizonColors.Text,
            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 40.sp,
        )
        VodMeta(it, info, Modifier.padding(top = 8.dp))
        Text(
            meta?.overview ?: info?.plot.orEmpty(), fontSize = 14.sp, color = HorizonColors.Text.copy(alpha = 0.85f), lineHeight = 20.sp,
            maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp, bottom = 12.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private val NoAutoScroll = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}
