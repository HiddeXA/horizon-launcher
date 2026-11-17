package nl.thuis.launcher.bios

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Panel { GENRES, PERIOD, SCORE, SORT }

private val ScoreChoices = listOf(0.0 to "Alles", 6.0 to "6+", 7.0 to "7+", 8.0 to "8+")
private val Pill = RoundedCornerShape(50)

/**
 * Filter (ontwerp A, zie docs/features/horizon-filter-redesign/design.md): onder het bovenmenu een
 * vaste balk met Alles / Films / Series en vier keuzeknoppen die hun waarde tonen. OK opent een
 * paneel over de resultaten. Daaronder het aantal (met Wissen) en de titels, 7 per rij.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun BiosFilterScreen(
    index: VodIndex,
    store: SeriesStore,
    filter: BiosFilter,
    onFilter: (BiosFilter) -> Unit,
    onOpen: (VodItem) -> Unit,
    onOpenSeries: (SeriesItem) -> Unit,
    onSettings: () -> Unit,
    firstFocus: FocusRequester,
    /** Film-, serie- of instellingenscherm ligt erover: daarna de focus terug op dezelfde titel. */
    overlayOpen: Boolean,
    onPanelOpen: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val seriesState by Series.state.collectAsState()
    // Alleen opnieuw zoeken als de series klaar zijn (of mislukken), niet bij elke stap van het laden.
    val seriesKey: Any = (seriesState as? SeriesState.Ready)?.index ?: seriesState::class

    var outcome by remember { mutableStateOf<FilterOutcome?>(null) }
    var searched by remember { mutableStateOf<BiosFilter?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var searchedRetry by remember { mutableIntStateOf(0) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(filter, index, seriesKey, retry) {
        val fresh = searched != filter || searchedRetry != retry
        if (fresh) {
            outcome = null
            delay(350) // wacht tot je klaar bent met kiezen
        }
        val o = FilterSearch.run(filter, index, seriesState)
        searched = filter
        searchedRetry = retry
        if (fresh) version++
        outcome = o
    }

    var open by remember { mutableStateOf<Panel?>(null) }
    LaunchedEffect(open) { onPanelOpen(open != null) }
    DisposableEffect(Unit) { onDispose { onPanelOpen(false) } }

    val segReq = remember { List(3) { FocusRequester() } }
    val chipReq = remember { List(4) { FocusRequester() } }
    val clearReq = remember { FocusRequester() }
    val contentDown = remember { FocusRequester() }
    val itemReq = remember { HashMap<String, FocusRequester>() }
    fun req(k: String) = itemReq.getOrPut(k) { FocusRequester() }
    var lastOpened by remember { mutableStateOf<String?>(null) }
    var gridFocused by remember { mutableStateOf(false) }
    var chipX by remember { mutableStateOf(List(4) { 0f }) }

    fun focusSoort() = runCatching { segReq[filter.kind.ordinal].requestFocus() }
    fun close() {
        val p = open ?: return
        open = null
        runCatching { chipReq[p.ordinal].requestFocus() }
    }
    fun clearAll() {
        onFilter(BiosFilter())
        runCatching { segReq[0].requestFocus() }
    }
    BackHandler(enabled = open != null) { close() }
    // Terug in het raster: eerst naar de filters; nog een keer Terug gaat (via de activity) naar de tab.
    BackHandler(enabled = open == null && gridFocused) { focusSoort() }

    // Terug van een film- of seriescherm: dezelfde titel weer focus.
    LaunchedEffect(overlayOpen) {
        if (overlayOpen) return@LaunchedEffect
        val k = lastOpened ?: return@LaunchedEffect
        repeat(8) {
            delay(80)
            if (runCatching { req(k).requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    val o = outcome
    val loading = o == null || (o.items.isEmpty() && o.seriesPending)
    val showGrid = o != null && !loading && !o.allFailed && o.items.isNotEmpty()
    val notice = o != null && showGrid && (o.genreIgnored || o.partlyFailed)
    // Waar "omlaag" vanuit de balk heen gaat: melding, raster, of de eerste knop van de lege/foutstaat.
    val hasDown = o != null && !loading

    val density = LocalDensity.current
    val screenW = LocalConfiguration.current.screenWidthDp.dp
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(start = ContentStart, end = ContentStart, top = NavHeight)) {
            // --- Filterbalk ---
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.alpha(if (open != null) 0.45f else 1f).background(HorizonColors.Surface, Pill).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    FilterKind.entries.forEach { k ->
                        val selected = k == filter.kind
                        Segment(
                            k.label, selected,
                            Modifier.focusRequester(segReq[k.ordinal])
                                .let { if (selected) it.focusRequester(firstFocus) else it }
                                .focusProperties { if (hasDown) down = contentDown },
                        ) { onFilter(filter.switchTo(k)) }
                    }
                }
                Box(Modifier.padding(horizontal = 12.dp).width(1.dp).height(24.dp).background(HorizonColors.SurfaceHigh))
                val chips = listOf(
                    Triple(Panel.GENRES, "Genres", FilterTexts.chipValue(filter.options.map { it.label })),
                    Triple(Panel.PERIOD, "Periode", filter.period.takeIf { it != FilterPeriod.ALL }?.label),
                    Triple(Panel.SCORE, "Score", ScoreChoices.firstOrNull { it.first == filter.minScore && it.first > 0 }?.second),
                    Triple(Panel.SORT, "Sorteren", filter.sort.label),
                )
                chips.forEachIndexed { i, (panel, label, value) ->
                    if (i > 0) Spacer(Modifier.width(8.dp))
                    Chip(
                        label = label,
                        value = value,
                        valueSet = value != null && !(panel == Panel.SORT && filter.sort == FilterSort.POPULAR),
                        isOpen = open == panel,
                        dim = open != null && open != panel,
                        modifier = Modifier
                            .let { if (panel == Panel.GENRES) it.weight(1f, fill = false) else it }
                            .focusRequester(chipReq[i])
                            .onGloballyPositioned { c -> chipX = chipX.toMutableList().also { l -> l[i] = c.positionInRoot().x } }
                            .focusProperties {
                                if (hasDown) down = contentDown
                                if (panel == Panel.SORT && filter.canClear && showGrid) right = clearReq
                            },
                    ) { open = panel }
                }
            }

            // --- Inhoud ---
            Column(Modifier.fillMaxSize().alpha(if (open != null) 0.18f else 1f)) {
                when {
                    loading -> {
                        Row(Modifier.padding(top = 14.dp).height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterSpinner()
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (o == null) "Titels zoeken…" else "Series laden…",
                                fontSize = 14.sp, fontWeight = FontWeight.Medium, color = HorizonColors.Dim,
                            )
                        }
                        SkeletonGrid()
                    }
                    o!!.allFailed -> StateMessage(
                        icon = { OfflineIcon() },
                        title = if (filter.options.isNotEmpty() && !o.genreIgnored) "Genres kunnen nu niet geladen worden"
                        else "${noun(filter.kind).replaceFirstChar { it.uppercase() }} konden niet geladen worden",
                        body = if (filter.options.isNotEmpty()) "TMDB is niet bereikbaar. Controleer de internetverbinding van het kastje en probeer het opnieuw, of bekijk de titels die passen bij je andere filters."
                        else "Controleer de internetverbinding van het kastje en probeer het opnieuw.",
                        buttons = buildList {
                            add("Opnieuw proberen" to {
                                if (seriesState is SeriesState.Error) scope.launch { Series.load(context, force = true) }
                                retry++
                            })
                            if (filter.options.isNotEmpty()) add("Zonder genre tonen" to { onFilter(filter.copy(genres = emptySet())) })
                        },
                        firstFocus = contentDown,
                    )
                    o.items.isEmpty() -> StateMessage(
                        icon = { EmptyPlanet() },
                        title = "Geen ${noun(filter.kind)} gevonden",
                        body = FilterTexts.emptyExplanation(filter),
                        buttons = FilterTexts.suggestions(filter).map { fix ->
                            fix.label to { if (fix == FilterTexts.Fix.CLEAR) clearAll() else onFilter(fix.apply(filter)) }
                        },
                        firstFocus = contentDown,
                    )
                    else -> {
                        val firstKey = o.items.first().listKey
                        if (notice) {
                            Spacer(Modifier.height(8.dp))
                            if (o.genreIgnored) NoticeBar(
                                "Filteren op genre werkt pas met een TMDB-token.", "Periode en sortering zijn wel toegepast.",
                                "Naar Instellingen", Modifier.focusRequester(contentDown).focusProperties { down = req(firstKey) }, onSettings,
                            ) else NoticeBar(
                                if (o.seriesError) "Series${if (filter.options.isNotEmpty()) " met dit genre" else ""} konden niet geladen worden."
                                else "Films${if (filter.options.isNotEmpty()) " met dit genre" else ""} konden niet geladen worden.",
                                if (o.seriesError) "Je ziet nu alleen films." else "Je ziet nu alleen series.",
                                "Opnieuw proberen", Modifier.focusRequester(contentDown).focusProperties { down = req(firstKey) },
                            ) {
                                if (seriesState is SeriesState.Error) scope.launch { Series.load(context, force = true) }
                                retry++
                            }
                        }
                        CountRow(filter, o, clearReq, onClear = { clearAll() })
                        val gridState = remember(version) { LazyGridState() }
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            val tileW = (maxWidth - 14.dp * 6) / 7
                            val focusedKey = remember { mutableStateOf<String?>(null) }
                            val focusedMeta by produceState<String?>(null, focusedKey.value, o) {
                                value = null
                                val k = focusedKey.value ?: return@produceState
                                val l = o.items.firstOrNull { it.listKey == k } ?: return@produceState
                                delay(400)
                                value = when (l) {
                                    is Listing.Film -> Bios.info(l.item)?.durationSecs?.takeIf { it >= 600 }?.let { "Film · ${duration(it)}" }
                                    is Listing.Show -> Series.detail(l.item).getOrNull()?.regularSeasons?.takeIf { it > 0 }
                                        ?.let { "Serie · $it ${if (it == 1) "seizoen" else "seizoenen"}" }
                                }
                            }
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(7),
                                state = gridState,
                                contentPadding = PaddingValues(top = 6.dp, bottom = 40.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp),
                                modifier = Modifier.fillMaxSize()
                                    .onFocusChanged { gridFocused = it.hasFocus }
                                    .let { if (!notice) it.focusRequester(contentDown) else it }
                                    .focusProperties { enter = { if (gridState.firstVisibleItemIndex == 0) req(firstKey) else FocusRequester.Default } }
                                    .focusGroup(),
                            ) {
                                items(o.items, key = { it.listKey }) { l ->
                                    val k = l.listKey
                                    val focused = focusedKey.value == k
                                    Column(
                                        Modifier.width(tileW).onFocusChanged {
                                            if (it.hasFocus) focusedKey.value = k else if (focusedKey.value == k) focusedKey.value = null
                                        },
                                    ) {
                                        ListingTile(
                                            l, store, width = tileW,
                                            onOpen = { lastOpened = k; onOpen(it) },
                                            onOpenSeries = { lastOpened = k; onOpenSeries(it) },
                                            modifier = Modifier.focusRequester(req(k)),
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            l.title, fontSize = 12.sp, color = HorizonColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                                        )
                                        Text(
                                            if (focused) focusedMeta ?: kindYear(l) else year(l).orEmpty(),
                                            fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            color = if (focused) HorizonColors.Mint else HorizonColors.Dim,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Panelen ---
        val panelTop = NavHeight + 48.dp
        when (open) {
            Panel.GENRES -> GenrePanel(
                filter, onToggle = { onFilter(filter.toggle(it)) },
                Modifier.padding(start = ContentStart, end = ContentStart, top = panelTop),
            )
            Panel.PERIOD, Panel.SCORE, Panel.SORT -> {
                val i = open!!.ordinal
                val x = with(density) { chipX[i].toDp() }.coerceIn(ContentStart, screenW - ContentStart - 240.dp)
                val m = Modifier.offset(x = x, y = panelTop)
                when (open) {
                    Panel.PERIOD -> ChoicePanel("Periode", FilterPeriod.entries.map { it to it.label }, filter.period, { onFilter(filter.copy(period = it)); close() }, m)
                    Panel.SCORE -> ChoicePanel("Minimale score", ScoreChoices, filter.minScore, { onFilter(filter.copy(minScore = it)); close() }, m)
                    else -> ChoicePanel("Sorteren", FilterSort.entries.map { it to it.label }, filter.sort, { onFilter(filter.copy(sort = it)); close() }, m)
                }
            }
            null -> Unit
        }
    }
}

private fun noun(kind: FilterKind) = when (kind) {
    FilterKind.ALL -> "titels"
    FilterKind.FILMS -> "films"
    FilterKind.SERIES -> "series"
}

private fun year(l: Listing): String? = when (l) {
    is Listing.Film -> BiosGenres.meta[l.item.id]?.year ?: l.item.year
    is Listing.Show -> l.item.year ?: SeriesGenres.meta[l.item.id]?.year
}

/** Met focus, zolang de duur of het aantal seizoenen nog niet bekend is. */
private fun kindYear(l: Listing): String =
    listOfNotNull(if (l is Listing.Show) "Serie" else "Film", year(l)).joinToString(" · ")

/** Alles / Films / Series. Gekozen: blauw; focus: licht, en gekozen met focus krijgt een blauwe rand. */
@Composable
private fun Segment(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(Pill),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) HorizonColors.Accent else Color.Transparent,
            contentColor = if (selected) HorizonColors.OnAccent else HorizonColors.Text,
            focusedContainerColor = HorizonColors.Text,
            focusedContentColor = HorizonColors.Background,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = if (selected) Border(BorderStroke(2.dp, HorizonColors.Accent), shape = Pill) else Border.None,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        modifier = modifier.height(32.dp),
    ) {
        Box(Modifier.height(32.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

/** Keuzeknop met label, waarde en pijltje. Gekozen waarde in blauw; focus licht met donkere tekst. */
@Composable
private fun Chip(label: String, value: String?, valueSet: Boolean, isOpen: Boolean, dim: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(Pill),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isOpen) HorizonColors.SurfaceHigh else HorizonColors.Surface,
            contentColor = HorizonColors.Text,
            focusedContainerColor = HorizonColors.Text,
            focusedContentColor = HorizonColors.Background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = modifier.height(40.dp).alpha(if (dim) 0.45f else 1f).onFocusChanged { focused = it.isFocused },
    ) {
        Row(Modifier.height(40.dp).padding(start = 14.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                color = if (focused) Color.Unspecified else if (isOpen) HorizonColors.Text else HorizonColors.Dim,
            )
            if (value != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (focused) Color.Unspecified else if (valueSet) HorizonColors.Accent else HorizonColors.Text,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                if (isOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null,
                tint = if (focused) HorizonColors.Background else if (isOpen) HorizonColors.Text else HorizonColors.Dim,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** "38 titels · 29 films, 9 series", met rechts Wissen als er iets te wissen valt. */
@Composable
private fun CountRow(filter: BiosFilter, o: FilterOutcome, clearReq: FocusRequester, onClear: () -> Unit) {
    val c = FilterTexts.count(filter, o.films, o.series, o.capped)
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp).height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(c.main, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HorizonColors.Text)
        if (c.detail.isNotEmpty()) Text("  ·  ${c.detail}", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = HorizonColors.Dim, maxLines = 1)
        if (o.seriesPending) {
            Spacer(Modifier.width(14.dp))
            Row(
                Modifier.height(28.dp).background(HorizonColors.Surface, Pill).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterSpinner(14.dp)
                Spacer(Modifier.width(8.dp))
                Text("Series worden nog geladen en komen er zo bij", fontSize = 13.sp, color = HorizonColors.Text)
            }
        }
        Spacer(Modifier.weight(1f))
        if (filter.canClear) {
            Surface(
                onClick = onClear,
                shape = ClickableSurfaceDefaults.shape(Pill),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.Transparent, contentColor = HorizonColors.Dim,
                    focusedContainerColor = HorizonColors.Text, focusedContentColor = HorizonColors.Background,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                modifier = Modifier.height(32.dp).focusRequester(clearReq),
            ) {
                Row(Modifier.height(32.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Alles wissen", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** Melding boven de resultaten (geen token, of één soort mislukt) met één knop. */
@Composable
private fun NoticeBar(bold: String, rest: String, button: String, buttonModifier: Modifier, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).background(HorizonColors.Surface, RoundedCornerShape(12.dp)).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = HorizonColors.Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Row(Modifier.weight(1f)) {
            Text(bold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = HorizonColors.Text, maxLines = 1)
            Text("  $rest", fontSize = 14.sp, color = HorizonColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BiosButton(button, modifier = buttonModifier, onClick = onClick)
    }
}

/** Lege staat en foutstaat: icoon, titel, uitleg en knoppen; de eerste knop is het doel van "omlaag". */
@Composable
private fun StateMessage(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    buttons: List<Pair<String, () -> Unit>>,
    firstFocus: FocusRequester,
) {
    Column(
        Modifier.fillMaxSize().padding(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Spacer(Modifier.height(12.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = HorizonColors.Text)
        Spacer(Modifier.height(12.dp))
        Text(body, fontSize = 14.sp, lineHeight = 21.sp, color = HorizonColors.Dim, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 540.dp))
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            buttons.forEachIndexed { i, (label, action) ->
                BiosButton(label, modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier, onClick = action)
            }
        }
    }
}

/** Grijze tegels terwijl er gezocht wordt (twee rijen, de tweede vager). */
@Composable
private fun SkeletonGrid() {
    BoxWithConstraints(Modifier.fillMaxSize().padding(top = 6.dp)) {
        val w = (maxWidth - 14.dp * 6) / 7
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            repeat(2) { r ->
                Row(Modifier.alpha(if (r == 0) 0.9f else 0.5f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(7) {
                        Column(Modifier.width(w), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.fillMaxWidth().height(w * 1.5f).background(HorizonColors.Surface, RoundedCornerShape(6.dp)))
                            Box(Modifier.fillMaxWidth(0.8f).height(10.dp).background(HorizonColors.Surface, Pill))
                            Box(Modifier.fillMaxWidth(0.4f).height(8.dp).background(HorizonColors.Surface, Pill))
                        }
                    }
                }
            }
        }
    }
}

/** Planeet met een gestippelde ring: "niets gevonden". */
@Composable
private fun EmptyPlanet(size: Dp = 56.dp) {
    val accent = HorizonColors.Accent
    val mint = HorizonColors.Mint
    Canvas(Modifier.size(width = size, height = size * 0.8f)) {
        val w = this.size.width
        val c = Offset(w / 2, this.size.height / 2)
        val r = this.size.height * 0.3f
        drawCircle(accent, r, c, style = Stroke(w * 0.05f))
        rotate(-18f, c) {
            drawOval(
                mint, topLeft = Offset(c.x - w * 0.45f, c.y - r * 0.55f),
                size = androidx.compose.ui.geometry.Size(w * 0.9f, r * 1.1f),
                style = Stroke(w * 0.04f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 7f))),
            )
        }
    }
}

/** Geen verbinding: drie bogen met een streep erdoor. */
@Composable
private fun OfflineIcon(size: Dp = 44.dp) {
    val accent = HorizonColors.Accent
    val dim = HorizonColors.Dim
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val stroke = Stroke(w * 0.07f, cap = StrokeCap.Round)
        listOf(0.42f, 0.28f, 0.14f).forEach { r ->
            drawArc(accent, 220f, 100f, false, Offset(w / 2 - w * r * 2, w * 0.8f - w * r * 2), androidx.compose.ui.geometry.Size(w * r * 4, w * r * 4), style = stroke)
        }
        drawCircle(accent, w * 0.04f, Offset(w / 2, w * 0.8f))
        drawLine(dim, Offset(w * 0.12f, w * 0.12f), Offset(w * 0.88f, w * 0.88f), w * 0.07f, StrokeCap.Round)
    }
}
