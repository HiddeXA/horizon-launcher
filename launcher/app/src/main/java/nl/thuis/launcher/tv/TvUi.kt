package nl.thuis.launcher.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import nl.thuis.launcher.bios.BiosButton
import nl.thuis.launcher.bios.HorizonColors
import nl.thuis.launcher.bios.HorizonIntro
import nl.thuis.launcher.bios.HorizonLogo
import nl.thuis.launcher.bios.LoadingScreen
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")
/** Beeld in de gids: rechtsboven, met een uitsparing in de gidsachtergrond. */
private val GV_W = 352.dp
private val GV_H = 198.dp
private val GV_TOP = 22.dp
private val GV_END = 32.dp

private fun clock(sec: Long): String = hhmm.format(Instant.ofEpochSecond(sec).atZone(ZoneId.systemDefault()))
private fun dayLabel(sec: Long): String {
    val d = Instant.ofEpochSecond(sec).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (d) { today -> "vandaag"; today.minusDays(1) -> "gisteren"; today.plusDays(1) -> "morgen"; else -> DateTimeFormatter.ofPattern("EEE d MMM").format(d) }
}

/** Huidige tijd in seconden, ververst elke 15 s (genoeg voor voortgangsbalken en de klok). */
@Composable
private fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis() / 1000) { while (true) { delay(15_000); value = System.currentTimeMillis() / 1000 } }
    return now
}

@Composable
fun TvApp(a: TvActivity) {
    val state by LiveRepo.state.collectAsState()
    val epgVersion by EpgStore.version.collectAsState()
    val now = rememberNow()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Het beeld: schermvullend, of klein rechtsboven in de gids.
        val videoMod = if (a.mode == TvMode.GUIDE) Modifier.align(Alignment.TopEnd).padding(top = GV_TOP, end = GV_END).size(GV_W, GV_H).clip(RoundedCornerShape(12.dp))
        else Modifier.fillMaxSize()
        AndroidView({ a.playerView }, videoMod)

        val lineup = (state as? LiveState.Ready)?.lineup
        when (val s = state) {
            LiveState.NotConfigured -> Prompt("Horizon TV is nog niet ingesteld", "Vul de gegevens van je IPTV-aanbieder in (dezelfde als in TiviMate).", "Instellingen") { a.settingsOpen = true }
            is LiveState.Error -> Prompt("Er ging iets mis", s.message, "Opnieuw") { a.recreate() }
            is LiveState.Loading -> Box(Modifier.fillMaxSize().background(HorizonColors.Background)) { LoadingScreen(s.phase, null, 0, badge = "TV", unit = "zenders") }
            is LiveState.Ready -> {}
        }

        if (lineup != null) {
            key(epgVersion) {
                // Info-balk onderin
                AnimatedVisibility(
                    a.mode == TvMode.WATCH && (a.bannerVisible || a.playerError != null) && a.current != null,
                    Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 3 },
                    exit = fadeOut(tween(250)) + slideOutVertically(tween(250)) { it / 3 },
                ) { a.current?.let { InfoBanner(it, a.catchup, a.playerError, a.buffering, now, a.prefs.favorites.contains(it.id)) } }

                if (a.mode == TvMode.LIST) ChannelList(a, lineup, now)
                if (a.mode == TvMode.GUIDE) Guide(a, lineup, now)
            }
        }

        // Zendernummer intikken
        if (a.digits.isNotEmpty()) {
            Box(Modifier.align(Alignment.TopEnd).padding(40.dp).background(HorizonColors.Surface.copy(alpha = 0.92f), RoundedCornerShape(16.dp)).padding(horizontal = 26.dp, vertical = 12.dp)) {
                Text(a.digits, fontSize = 44.sp, fontWeight = FontWeight.Bold, color = HorizonColors.Text)
            }
        }
        a.toast?.let {
            Box(Modifier.align(Alignment.TopCenter).padding(top = 36.dp).background(HorizonColors.Surface.copy(alpha = 0.95f), RoundedCornerShape(50)).padding(horizontal = 24.dp, vertical = 10.dp)) {
                Text(it, fontSize = 16.sp, color = HorizonColors.Text)
            }
        }

        if (a.settingsOpen) {
            nl.thuis.launcher.ui.SettingsScreen(
                onDismiss = { a.settingsOpen = false },
                onSaved = { changed -> if (changed.any { it.name.startsWith("X") }) { LiveRepo.reset(a); a.recreate() } },
            )
        }

        if (a.introVisible) HorizonIntro(skip = a.introSkip, badge = "TV") { a.introDone() }
    }
}

@Composable
private fun Prompt(title: String, body: String, button: String, onClick: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(250); runCatching { focus.requestFocus() } }
    Box(Modifier.fillMaxSize().background(HorizonColors.Background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HorizonLogo(Modifier.height(56.dp), badge = "TV")
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = HorizonColors.Text, modifier = Modifier.padding(top = 28.dp))
            Text(body, fontSize = 15.sp, color = HorizonColors.Dim, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
            BiosButton(button, Modifier.focusRequester(focus), primary = true, onClick = onClick)
        }
    }
}

// --- Kleine bouwstenen ---

@Composable
fun ChannelLogo(ch: LiveChannel, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(10.dp)).background(HorizonColors.Surface), contentAlignment = Alignment.Center) {
        Text(ch.name.take(3), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = HorizonColors.Dim)
        if (ch.logo != null) AsyncImage(ch.logo, ch.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp))
    }
}

@Composable
private fun LivePill(text: String = "LIVE", color: Color = HorizonColors.Mint) {
    val t = rememberInfiniteTransition(label = "live")
    val pulse by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "p")
    Row(
        Modifier.background(color.copy(alpha = 0.16f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).alpha(pulse).background(color, RoundedCornerShape(50)))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 1.sp)
    }
}

@Composable
private fun Progress(f: Float, modifier: Modifier, height: Dp = 4.dp) {
    Box(modifier.height(height).clip(RoundedCornerShape(50)).background(HorizonColors.Text.copy(alpha = 0.15f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f.coerceIn(0f, 1f)).background(Brush.horizontalGradient(listOf(HorizonColors.Mint, HorizonColors.Accent))))
    }
}

// --- Info-balk tijdens het kijken ---

@Composable
private fun InfoBanner(ch: LiveChannel, catchup: Programme?, error: String?, buffering: Boolean, now: Long, favorite: Boolean) {
    val guide = EpgStore.guide(ch)
    val cur = catchup ?: guide?.at(now)
    val next = if (catchup == null) guide?.next(now) else null
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, HorizonColors.Background.copy(alpha = 0.88f), HorizonColors.Background.copy(alpha = 0.97f))))
            .padding(start = 48.dp, end = 48.dp, top = 56.dp, bottom = 30.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChannelLogo(ch, Modifier.size(104.dp, 66.dp))
            Spacer(Modifier.width(22.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${ch.num}", fontSize = 14.sp, color = HorizonColors.Dim, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(10.dp))
                    Text(ch.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = HorizonColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (favorite) Text("  ★", fontSize = 18.sp, color = HorizonColors.Accent)
                    Spacer(Modifier.width(14.dp))
                    if (catchup != null) LivePill("TERUGKIJKEN", HorizonColors.Accent) else LivePill()
                    if (buffering && error == null) { Spacer(Modifier.width(10.dp)); Text("laden…", fontSize = 13.sp, color = HorizonColors.Dim) }
                }
                Text(
                    cur?.title ?: "Geen programma-informatie",
                    fontSize = 24.sp, fontWeight = FontWeight.Bold, color = HorizonColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (cur != null) Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${clock(cur.start)} – ${clock(cur.end)}", fontSize = 13.sp, color = HorizonColors.Dim)
                    Spacer(Modifier.width(16.dp))
                    if (catchup == null) {
                        Progress(cur.progress(now), Modifier.width(260.dp))
                        Spacer(Modifier.width(14.dp))
                        Text("nog ${((cur.end - now) / 60).coerceAtLeast(0)} min", fontSize = 13.sp, color = HorizonColors.Dim)
                    } else Text("${dayLabel(cur.start)} · terug naar live: terug", fontSize = 15.sp, color = HorizonColors.Dim)
                }
                if (next != null) Text(
                    "Straks ${clock(next.start)}   ${next.title}", fontSize = 13.sp, color = HorizonColors.Dim, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp),
                )
                if (error != null) Text("$error  OK = opnieuw proberen", fontSize = 15.sp, color = HorizonColors.Accent, modifier = Modifier.padding(top = 10.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(clock(now), fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = HorizonColors.Text)
                Text("▲▼ zappen · OK zenders · ► gids", fontSize = 11.sp, color = HorizonColors.Dim.copy(alpha = 0.8f))
            }
        }
    }
}

// --- Zenderlijst (OK of links) ---

private const val FAV = "__fav"
private const val RECENT = "__recent"

@Composable
private fun ChannelList(a: TvActivity, lineup: LiveLineup, now: Long) {
    BackHandler { a.mode = TvMode.WATCH }
    val cur = a.current
    val cats = remember(lineup, a.prefs.favorites.size, a.prefs.recent.size) {
        buildList {
            if (a.prefs.favorites.isNotEmpty()) add(FAV to "★  Favorieten")
            if (a.prefs.recent.isNotEmpty()) add(RECENT to "Laatst gekeken")
            lineup.visibleCategories.forEach { add(it.id to it.name) }
        }
    }
    fun channelsOf(id: String): List<LiveChannel> = when (id) {
        FAV -> a.prefs.favorites.mapNotNull { lineup.byId[it] }
        RECENT -> a.prefs.recent.mapNotNull { lineup.byId[it] }
        else -> lineup.byCategory[id].orEmpty()
    }
    // Begin in de categorie waar je nu kijkt.
    val startCat = remember { (if (a.zapList.isNotEmpty() && a.zapList.size == a.prefs.favorites.size && a.zapList.all { it.id in a.prefs.favorites }) FAV else cur?.categoryId) ?: cats.firstOrNull()?.first.orEmpty() }
    var selectedCat by remember { mutableStateOf(startCat) }
    val channels = remember(selectedCat, a.prefs.favorites.size, a.prefs.recent.size) { channelsOf(selectedCat) }

    val catState = rememberLazyListState(initialFirstVisibleItemIndex = (cats.indexOfFirst { it.first == startCat } - 4).coerceAtLeast(0))
    val startIdx = remember { channels.indexOfFirst { it.id == cur?.id }.coerceAtLeast(0) }
    val chanState = rememberLazyListState(initialFirstVisibleItemIndex = (startIdx - 3).coerceAtLeast(0))
    LaunchedEffect(selectedCat) { if (selectedCat != startCat) chanState.scrollToItem(0) }
    val firstChannel = remember { FocusRequester() }
    val catFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { firstChannel.requestFocus() } }

    Box(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(0f to HorizonColors.Background.copy(alpha = 0.97f), 0.5f to HorizonColors.Background.copy(alpha = 0.9f), 0.68f to Color.Transparent)
        )
    ) {
        Column(Modifier.fillMaxHeight().padding(start = 40.dp, top = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizonLogo(Modifier.height(26.dp), badge = "TV")
                Spacer(Modifier.width(18.dp))
                Text(clock(now), fontSize = 15.sp, color = HorizonColors.Dim, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(18.dp))
                Text("OK kijken · lang OK favoriet · ► tv-gids", fontSize = 11.sp, color = HorizonColors.Dim.copy(alpha = 0.7f))
            }
            Row(Modifier.padding(top = 16.dp).fillMaxHeight()) {
                // Categorieën
                LazyColumn(
                    state = catState, modifier = Modifier.width(220.dp).fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(cats, key = { _, c -> c.first }) { _, (id, name) ->
                        val selected = id == selectedCat
                        Surface(
                            onClick = { runCatching { firstChannel.requestFocus() } },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (selected) HorizonColors.Surface else Color.Transparent,
                                contentColor = if (selected) HorizonColors.Text else HorizonColors.Dim,
                                focusedContainerColor = HorizonColors.Text, focusedContentColor = HorizonColors.Background,
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                            modifier = Modifier.fillMaxWidth()
                                .then(if (selected) Modifier.focusRequester(catFocus) else Modifier)
                                .onFocusChanged { if (it.isFocused) selectedCat = id }
                                .onPreviewKeyEvent { e ->
                                    if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) { runCatching { firstChannel.requestFocus() }; true } else false
                                },
                        ) {
                            Text(name, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                // Zenders van de gekozen categorie
                LazyColumn(
                    state = chanState, modifier = Modifier.width(380.dp).fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (channels.isEmpty()) item { Text("Geen zenders", color = HorizonColors.Dim, modifier = Modifier.padding(16.dp)) }
                    itemsIndexed(channels, key = { _, c -> c.id }) { i, ch ->
                        val isFocusTarget = if (selectedCat == startCat) i == startIdx else i == 0
                        ChannelRow(
                            ch, now, playing = ch.id == cur?.id, favorite = ch.id in a.prefs.favorites,
                            modifier = Modifier
                                .then(if (isFocusTarget) Modifier.focusRequester(firstChannel) else Modifier)
                                .onPreviewKeyEvent { e ->
                                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (e.key) {
                                        Key.DirectionLeft -> { runCatching { catFocus.requestFocus() }; true }
                                        Key.DirectionRight -> { a.zapList = channels; a.mode = TvMode.GUIDE; true }
                                        else -> false
                                    }
                                },
                            onClick = { a.tune(ch, immediate = true, list = channels); a.mode = TvMode.WATCH },
                            onLongClick = {
                                a.prefs.toggleFavorite(ch.id)
                                a.showToast(if (ch.id in a.prefs.favorites) "${ch.name} staat bij je favorieten" else "${ch.name} is uit je favorieten")
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    ch: LiveChannel, now: Long, playing: Boolean, favorite: Boolean, modifier: Modifier,
    onClick: () -> Unit, onLongClick: () -> Unit,
) {
    val p = EpgStore.guide(ch)?.at(now)
    Surface(
        onClick = onClick, onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (playing) HorizonColors.Surface else HorizonColors.Surface.copy(alpha = 0.55f),
            contentColor = HorizonColors.Text,
            focusedContainerColor = HorizonColors.Text, focusedContentColor = HorizonColors.Background,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = modifier.fillMaxWidth().height(52.dp),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(3.dp, 28.dp).background(if (playing) HorizonColors.Accent else Color.Transparent, RoundedCornerShape(50)))
            Text("${ch.num}", fontSize = 11.sp, modifier = Modifier.width(44.dp).padding(start = 6.dp).alpha(0.7f))
            ChannelLogo(ch, Modifier.size(52.dp, 32.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ch.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (favorite) Text("  ★", fontSize = 12.sp, color = HorizonColors.Accent)
                    if (ch.archiveDays > 0) Text("  ⟲", fontSize = 12.sp, modifier = Modifier.alpha(0.7f))
                }
                if (p != null) {
                    Text(p.title, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(0.75f))
                    Progress(p.progress(now), Modifier.padding(top = 3.dp).width(140.dp), 2.dp)
                }
            }
        }
    }
}

// --- Tv-gids (rechts) ---

private const val WINDOW_MIN = 150L
private const val STEP_MIN = 30L

@Composable
private fun Guide(a: TvActivity, lineup: LiveLineup, now: Long) {
    BackHandler { a.mode = TvMode.WATCH }
    val channels = remember { a.zapList.ifEmpty { a.current?.let { lineup.byCategory[it.categoryId] }.orEmpty() } }
    var row by remember { mutableIntStateOf(channels.indexOfFirst { it.id == a.current?.id }.coerceAtLeast(0)) }
    var cursor by remember { mutableLongStateOf(now) }             // tijdstip van de geselecteerde cel
    var winStart by remember { mutableLongStateOf((now - 30 * 60) / (STEP_MIN * 60) * (STEP_MIN * 60)) }
    val winEnd = winStart + WINDOW_MIN * 60
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (row - 2).coerceAtLeast(0))
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(50); runCatching { focus.requestFocus() } }
    LaunchedEffect(row) {
        val first = listState.firstVisibleItemIndex
        val visible = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(5)
        if (row < first + 1 || row > first + visible - 2) listState.animateScrollToItem((row - 2).coerceAtLeast(0))
    }

    fun guideOf(ch: LiveChannel) = EpgStore.guide(ch)
    val selCh = channels.getOrNull(row)
    val selProg = selCh?.let { guideOf(it)?.at(cursor) }

    // Omschrijving van het geselecteerde programma (van de aanbieder, per zender).
    val details by produceState<List<EpgStore.Detail>>(emptyList(), selCh?.id) { value = selCh?.let { EpgStore.details(it) }.orEmpty() }
    val detail = selProg?.let { p -> details.firstOrNull { kotlin.math.abs(it.start - p.start) < 300 } }

    fun keepVisible(t: Long) {
        if (t < winStart) winStart = t / (STEP_MIN * 60) * (STEP_MIN * 60)
        else if (t >= winEnd - 10 * 60) winStart = (t - (WINDOW_MIN - 60) * 60) / (STEP_MIN * 60) * (STEP_MIN * 60)
    }

    fun act() {
        val ch = selCh ?: return
        val p = selProg
        val archive = ch.archiveDays > 0 && p != null && p.end <= now && p.start >= now - ch.archiveDays * 86400L && (detail?.archive != false)
        when {
            p == null || p.isNow(now) -> { a.tune(ch, immediate = true, list = channels); a.mode = TvMode.WATCH }
            archive -> a.watchCatchup(ch, p!!)
            p.start > now -> a.showToast("${p.title} begint ${dayLabel(p.start)} om ${clock(p.start)}")
            else -> a.showToast("Dit programma is niet terug te kijken")
        }
    }

    Box(
        Modifier.fillMaxSize()
            // Achtergrond overal behalve rechtsboven: daar zit het beeld (een SurfaceView eronder).
            .drawBehind {
                val hole = Path().apply {
                    addRoundRect(RoundRect(
                        left = size.width - (GV_END + GV_W).toPx(), top = GV_TOP.toPx(),
                        right = size.width - GV_END.toPx(), bottom = (GV_TOP + GV_H).toPx(),
                        cornerRadius = CornerRadius(12.dp.toPx()),
                    ))
                }
                clipPath(hole, ClipOp.Difference) { drawRect(HorizonColors.Background.copy(alpha = 0.98f)) }
            }
            .focusRequester(focus).focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionUp -> { if (row > 0) row--; true }
                    Key.DirectionDown -> { if (row < channels.lastIndex) row++; true }
                    Key.DirectionRight -> {
                        val g = selCh?.let { guideOf(it) }
                        val p = g?.at(cursor)
                        cursor = when {
                            p != null -> p.end
                            else -> g?.next(cursor)?.start ?: (cursor + STEP_MIN * 60)
                        }.coerceAtMost(now + 7 * 86400)
                        keepVisible(cursor); true
                    }
                    Key.DirectionLeft -> {
                        val g = selCh?.let { guideOf(it) }
                        val p = g?.at(cursor)
                        val target = (p?.start ?: cursor) - 1
                        val prev = g?.at(target)
                        cursor = (prev?.start?.let { maxOf(it, target - 6 * 3600) } ?: (cursor - STEP_MIN * 60)).coerceAtLeast(now - 2 * 86400)
                        keepVisible(cursor); true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { act(); true }
                    else -> false
                }
            },
    ) {
        Column(Modifier.fillMaxSize().padding(start = 40.dp, top = 22.dp, end = GV_END)) {
            // Bovenin: details van het geselecteerde programma (beeld staat rechts, in de activity).
            Column(Modifier.height(GV_H).padding(end = GV_W + 32.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizonLogo(Modifier.height(24.dp), badge = "TV")
                    Spacer(Modifier.width(16.dp))
                    Text("Tv-gids · ${clock(now)}", fontSize = 13.sp, color = HorizonColors.Dim)
                    Spacer(Modifier.width(16.dp))
                    Text("OK kijken of terugkijken · terug sluiten", fontSize = 11.sp, color = HorizonColors.Dim.copy(alpha = 0.7f))
                }
                if (selCh != null) {
                    Text("${selCh.num}  ${selCh.name}", fontSize = 13.sp, color = HorizonColors.Dim, modifier = Modifier.padding(top = 12.dp))
                    Text(selProg?.title ?: "Geen programma-informatie", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = HorizonColors.Text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    if (selProg != null) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${dayLabel(selProg.start).replaceFirstChar { it.uppercase() }} ${clock(selProg.start)} – ${clock(selProg.end)}", fontSize = 13.sp, color = HorizonColors.Dim)
                        Spacer(Modifier.width(12.dp))
                        when {
                            selProg.isNow(now) -> LivePill()
                            selProg.end <= now && selCh.archiveDays > 0 && selProg.start >= now - selCh.archiveDays * 86400L -> LivePill("TERUGKIJKEN", HorizonColors.Accent)
                            else -> {}
                        }
                    }
                    Text(detail?.description ?: "", fontSize = 13.sp, color = HorizonColors.Text.copy(alpha = 0.85f), maxLines = 3, overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }

            // Het raster
            BoxWithConstraints(Modifier.fillMaxSize().padding(top = 12.dp)) {
                val chanW = 220.dp
                val gridW = maxWidth - chanW
                val perMin = gridW / WINDOW_MIN.toFloat()
                Column {
                    // Tijdbalk
                    Box(Modifier.fillMaxWidth().height(24.dp)) {
                        Text(dayLabel(winStart).replaceFirstChar { it.uppercase() }, fontSize = 12.sp, color = HorizonColors.Dim, modifier = Modifier.width(chanW))
                        var t = winStart
                        while (t < winEnd) {
                            Text(clock(t), fontSize = 12.sp, color = HorizonColors.Dim, modifier = Modifier.offset(x = chanW + perMin * ((t - winStart) / 60f)).padding(start = 6.dp))
                            t += STEP_MIN * 60
                        }
                    }
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                            itemsIndexed(channels, key = { _, c -> c.id }) { i, ch ->
                                GuideRow(ch, guideOf(ch), i == row, cursor, now, winStart, winEnd, chanW, perMin, playing = ch.id == a.current?.id)
                            }
                        }
                        // Lijn op "nu"
                        if (now in winStart until winEnd) {
                            Box(Modifier.offset(x = chanW + perMin * ((now - winStart) / 60f)).width(2.dp).fillMaxHeight().background(HorizonColors.Accent.copy(alpha = 0.85f)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideRow(
    ch: LiveChannel, guide: ChannelGuide?, selected: Boolean, cursor: Long, now: Long,
    winStart: Long, winEnd: Long, chanW: Dp, perMin: Dp, playing: Boolean,
) {
    Row(Modifier.fillMaxWidth().height(46.dp)) {
        // Zender
        Row(
            Modifier.width(chanW).fillMaxHeight().padding(end = 8.dp)
                .background(if (selected) HorizonColors.Surface else Color.Transparent, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${ch.num}", fontSize = 11.sp, color = HorizonColors.Dim, modifier = Modifier.width(38.dp))
            ChannelLogo(ch, Modifier.size(48.dp, 30.dp))
            Spacer(Modifier.width(8.dp))
            Text(ch.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (playing) HorizonColors.Accent else HorizonColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        // Programma's in het venster
        Box(Modifier.fillMaxSize()) {
            val progs = guide?.between(winStart, winEnd).orEmpty()
            if (progs.isEmpty()) {
                Cell("Geen gids", selected, false, false, Modifier.fillMaxSize())
            }
            progs.forEach { p ->
                val s = maxOf(p.start, winStart); val e = minOf(p.end, winEnd)
                val x = perMin * ((s - winStart) / 60f)
                val w = perMin * ((e - s) / 60f)
                Cell(
                    p.title, selected && p.isNow(cursor) || (selected && cursor in p.start until p.end), p.isNow(now), p.end <= now,
                    Modifier.offset(x = x).width(w).fillMaxHeight().padding(end = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Cell(title: String, focused: Boolean, live: Boolean, past: Boolean, modifier: Modifier) {
    val bg = when {
        focused -> HorizonColors.Text
        live -> HorizonColors.Surface
        past -> HorizonColors.Surface.copy(alpha = 0.45f)
        else -> HorizonColors.Surface.copy(alpha = 0.75f)
    }
    Box(
        modifier.clip(RoundedCornerShape(9.dp)).background(bg)
            .then(if (live && !focused) Modifier.border(1.dp, HorizonColors.Accent.copy(alpha = 0.35f), RoundedCornerShape(9.dp)) else Modifier)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            title, fontSize = 12.sp, fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (focused) HorizonColors.Background else if (past) HorizonColors.Dim else HorizonColors.Text,
        )
    }
}
