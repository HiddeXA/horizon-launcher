package nl.thuis.launcher.bios

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.thuis.launcher.data.Recommender

/**
 * Bios: je filmaanbod (Xtream) als streamingapp. Eigen tegel op het homescherm.
 * Kan ook vanuit de launcher geopend worden met een zoekopdracht (EXTRA_SEARCH).
 */
class BiosActivity : ComponentActivity() {

    private var section by mutableStateOf(BiosSection.HOME)
    private var searchQuery by mutableStateOf("")
    private var introVisible by mutableStateOf(false)
    private var introSkip by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HorizonColors.palette = HorizonPalette.Twilight   // Roze Hoek kan het palet op roze hebben gezet
        // Startanimatie bij een koude start, behalve als we vanuit de launcher met een zoekopdracht komen.
        if (intent?.getStringExtra(EXTRA_SEARCH) == null && intent?.getBooleanExtra("probe", false) != true && intent?.getBooleanExtra("probelive", false) != true) showIntro()
        handle(intent)
        lifecycleScope.launch { Bios.load(this@BiosActivity) }

        setContent {
            HorizonTheme {
                BackHandler(enabled = section != BiosSection.HOME) { section = BiosSection.HOME }
                Box(Modifier.fillMaxSize()) {
                    BiosApp(section = section, onSection = { section = it }, searchQuery = searchQuery, context = this@BiosActivity, introVisible = introVisible)
                    if (introVisible) HorizonIntro(skip = introSkip) { introVisible = false }
                }
            }
        }
    }

    private fun showIntro() { introSkip = false; introVisible = true }

    override fun onStart() {
        super.onStart()
        HorizonColors.palette = HorizonPalette.Twilight
        // Na een tijd niet gebruikt: opnieuw de startanimatie.
        if (lastStopped > 0 && SystemClock.elapsedRealtime() - lastStopped > INTRO_AFTER_MS) showIntro()
    }

    override fun onStop() {
        super.onStop()
        lastStopped = SystemClock.elapsedRealtime()
    }

    /** Tijdens de startanimatie: elke toets slaat hem over (en gaat niet naar de app eronder). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (introVisible) {
            if (event.action == KeyEvent.ACTION_DOWN) introSkip = true
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        // Alleen voor onderhoud via adb: welke velden stuurt de aanbieder mee?
        if (intent?.getBooleanExtra("probe", false) == true) lifecycleScope.launch { Bios.probeFields() }
        if (intent?.getBooleanExtra("probelive", false) == true) lifecycleScope.launch { Bios.probeLive() }
        intent?.getStringExtra(EXTRA_SEARCH)?.let {
            searchQuery = it
            section = BiosSection.SEARCH
        }
    }

    companion object {
        const val EXTRA_SEARCH = "search"
        /** Na zo lang niet gebruikt speelt de startanimatie opnieuw. */
        private const val INTRO_AFTER_MS = 20 * 60 * 1000L
        private var lastStopped = 0L
        /** De speler hoort bij de app: na een film kijken geen startanimatie. */
        fun markActive() { if (lastStopped > 0) lastStopped = SystemClock.elapsedRealtime() }
        fun search(context: Context, query: String) =
            Intent(context, BiosActivity::class.java).putExtra(EXTRA_SEARCH, query).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

@Composable
private fun BiosApp(section: BiosSection, onSection: (BiosSection) -> Unit, searchQuery: String, context: Context, introVisible: Boolean) {
    val state by Bios.state.collectAsState()
    val progress = remember { Bios.progress(context) }
    val myList = remember { Bios.myList(context) }
    var detail by remember { mutableStateOf<VodItem?>(null) }
    var settings by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(BiosFilter()) }
    val scope = rememberCoroutineScope()
    val contentFocus = remember { FocusRequester() }

    Box(Modifier.fillMaxSize().background(HorizonColors.Background)) {
        when (val s = state) {
            CatalogState.NotConfigured -> SetupPrompt(
                "Horizon is nog niet ingesteld",
                "Vul de gegevens van je IPTV-aanbieder in (dezelfde als in TiviMate).",
                Modifier.align(Alignment.Center),
            ) { settings = true }
            is CatalogState.Loading -> LoadingScreen(s.phase, s.progress, s.count)
            is CatalogState.Error -> SetupPrompt("Er ging iets mis", s.message, Modifier.align(Alignment.Center)) { settings = true }
            is CatalogState.Ready -> {
                val idx = s.index

                // Genre-rijen via TMDB: eerst de persoonlijke rijen tonen, genres komen erbij zodra ze er zijn.
                var genreRows by remember(idx) { mutableStateOf<Map<String, List<Int>>?>(null) }
                LaunchedEffect(idx) { genreRows = runCatching { BiosGenres.shelves(context, idx) }.getOrDefault(emptyMap()) }

                when (section) {
                    BiosSection.HOME -> {
                        var rows by remember { mutableStateOf<List<BiosRow>?>(null) }
                        val tasteVersion by Bios.tasteVersion.collectAsState()
                        LaunchedEffect(idx, genreRows, progress.entries.size, myList.ids.size, tasteVersion) {
                            rows = withContext(Dispatchers.Default) { buildRows(context, idx, genreRows, progress, myList) }
                        }
                        rows?.let { BiosHome(it, progress.entries, onOpen = { detail = it }, firstFocus = contentFocus) }
                    }
                    BiosSection.SEARCH -> BiosSearch(idx, searchQuery, onOpen = { detail = it }, firstFocus = contentFocus)
                    BiosSection.FILTER -> BiosFilterScreen(idx, filter, onFilter = { filter = it }, onOpen = { detail = it }, firstFocus = contentFocus)
                    BiosSection.MYLIST -> BiosMyList(myList.ids.mapNotNull { idx.byId[it] }, onOpen = { detail = it }, firstFocus = contentFocus)
                }
                // Na het wisselen van tab: focus naar de inhoud (zodat je nooit "vast" zit).
                LaunchedEffect(section, idx, introVisible) {
                    if (introVisible) return@LaunchedEffect
                    repeat(12) {
                        delay(150)
                        if (runCatching { contentFocus.requestFocus() }.isSuccess) return@LaunchedEffect
                    }
                }

                TopNav(section, onSelect = onSection, contentFocus = contentFocus, filterActive = !filter.isEmpty)
            }
        }

        if (settings) {
            nl.thuis.launcher.ui.SettingsScreen(
                onDismiss = { settings = false },
                onSaved = { changed ->
                    if (changed.any { it.name.startsWith("X") }) {
                        Bios.reset(context)
                        scope.launch { Bios.load(context, force = true) }
                    }
                },
            )
        }

        detail?.let { item ->
            LaunchedEffect(item.id) { Bios.recordTaste(context, item, Taste.OPEN_DETAIL) }
            BiosDetail(
                item = item,
                resumeMs = progress.entries[item.id]?.positionMs ?: 0L,
                inMyList = myList.contains(item.id),
                onPlay = { fromStart -> context.startActivity(PlayerActivity.intent(context, item, fromStart)) },
                onToggleList = {
                    if (!myList.contains(item.id)) Bios.recordTaste(context, item, Taste.ADD_TO_LIST)
                    myList.toggle(item.id)
                },
                onTrailer = { info ->
                    if (!openYouTube(context, info?.trailerKey, "${item.title} ${item.year.orEmpty()} trailer")) {
                        Toast.makeText(context, "YouTube kon de trailer niet openen", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { detail = null },
            )
        }
    }
}

@Composable
private fun SetupPrompt(title: String, body: String, modifier: Modifier, onSettings: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(200); runCatching { focus.requestFocus() } }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyMessage(title, body)
        BiosButton("Instellingen", primary = true, modifier = Modifier.focusRequester(focus)) { onSettings() }
    }
}

/**
 * Rijen voor het startscherm:
 *   1. Nieuwe films en Top 100: altijd bovenaan.
 *   2. Persoonlijk: Verder kijken, Aanbevolen voor jou, Mijn lijst.
 *   3. Categorieën (Populair nu, Klassiekers, Actie, Horror …), gesorteerd op je kijkgedrag.
 * Zonder TMDB: de categorieën van je aanbieder.
 */
private fun buildRows(
    context: Context,
    idx: VodIndex,
    genreRows: Map<String, List<Int>>?,
    progress: ProgressStore,
    myList: MyList,
): List<BiosRow> {
    val rows = ArrayList<BiosRow>()
    val thisYear = java.time.LocalDate.now().year

    // Nieuwe films: uitgebracht dit of vorig jaar, nieuwst toegevoegd eerst.
    val fresh = idx.items.asSequence()
        .filter { (it.year?.toIntOrNull() ?: 0) >= thisYear - 1 }
        .sortedByDescending { it.added }
        .distinctBy { it.key }
        .take(40).toList()
    rows += BiosRow("new", "Nieuwe films", fresh.takeIf { it.size >= 6 } ?: idx.newest)

    val shelves = GenreShelves
    val top = genreRows?.get("top100").orEmpty().mapNotNull { idx.byId[it] }
    if (top.size >= 6) rows += BiosRow("genre:top100", "Top 100", top)

    val continueItems = progress.entries.entries.sortedByDescending { it.value.updated }.mapNotNull { idx.byId[it.key] }
    if (continueItems.isNotEmpty()) rows += BiosRow("continue", "Verder kijken", continueItems, showProgress = true)

    val fromRecs = Recommender.readCache(context).mapNotNull { m ->
        idx.find(null, listOfNotNull(m.title, m.originalTitle), m.year)
    }.distinctBy { it.id }
    if (fromRecs.isNotEmpty()) rows += BiosRow("recs", "Aanbevolen voor jou", fromRecs)

    val list = myList.ids.mapNotNull { idx.byId[it] }
    if (list.isNotEmpty()) rows += BiosRow("mylist", "Mijn lijst", list)

    if (genreRows != null && genreRows.values.any { it.size >= 6 }) {
        val scores = TasteRanking.scores(Bios.taste(context).events, shelves, System.currentTimeMillis(), thisYear)
        TasteRanking.order(shelves, scores).filterNot { it.pinned }.forEach { shelf ->
            val items = genreRows[shelf.key].orEmpty().mapNotNull { idx.byId[it] }
            if (items.size >= 6) rows += BiosRow("genre:${shelf.key}", shelf.title, items)
        }
    } else if (genreRows != null) {
        // Geen TMDB of niets gevonden: dan de categorieën van je aanbieder.
        idx.categories.forEach { c ->
            val items = idx.byCategory[c.id]?.take(40).orEmpty()
            if (items.isNotEmpty()) rows += BiosRow("cat:${c.id}", c.name, items)
        }
    }
    return rows
}
