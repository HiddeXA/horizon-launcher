package nl.thuis.launcher.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.focus.focusProperties
import androidx.tv.material3.Border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.layout.size
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import nl.thuis.launcher.AppEntry
import nl.thuis.launcher.data.ChannelItem
import nl.thuis.launcher.data.Movie
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val Gutter = 48.dp
private val HeroHeight = 250.dp
private const val GRID_COLUMNS = 5

/**
 * Indeling zoals tvOS:
 *  - top shelf bovenin (vast, met achtergrond die de focus volgt)
 *  - rij 1: vastgezette apps (dock), rij 2: aanbevelingen, daarna alle apps
 *  - bij omlaag scrollen schuift en vervaagt de top shelf (zonder het scherm opnieuw op te bouwen)
 *
 * Performance: de top shelf leest de focus via [HeroState]; de rijen schrijven alleen.
 * Daardoor tekent een focuswissel alleen de top shelf opnieuw, niet alle tegels.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    pins: List<String>,
    recs: List<Movie>,
    recMessage: String?,
    channelArt: () -> Map<String, List<ChannelItem>>,
    isLiked: (Int) -> Boolean,
    loadHeroBanner: suspend (AppEntry) -> ImageBitmap?,
    onLaunch: (AppEntry) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
    onToggleLike: (Movie) -> Unit,
    onOpenMovie: (Movie) -> Unit,
    onOpenLiked: () -> Unit,
    isAvailable: (Movie) -> Boolean = { false },
    onOpenSettings: () -> Unit = {},
) {
    val hero = remember { HeroState() }
    val posterColors = remember { mutableStateMapOf<Int, BrandColors>() }
    val listState = rememberLazyListState()

    // Toon pas na 150 ms wat focus heeft, zodat snel scrollen niet bij elke tegel een nieuwe
    // achtergrond laadt. collectLatest annuleert de vorige wachttijd.
    LaunchedEffect(hero) {
        snapshotFlow { hero.focused }.collectLatest {
            if (hero.shown != null) delay(150)
            hero.shown = it
        }
    }
    LaunchedEffect(recs) {
        if (hero.focused == null) recs.firstOrNull()?.let { hero.focused = Highlight.Film(it) }
    }

    val byPin = remember(apps) { apps.associateBy { it.pinKey } }
    val pinned by remember(byPin) { derivedStateOf { pins.mapNotNull { byPin[it] } } }
    val rows = remember(apps) { apps.chunked(GRID_COLUMNS) }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(15) {
            delay(200)
            if (runCatching { firstFocus.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    // "Balk voor balk": zodra een andere rij focus krijgt, schuift die rij in zijn geheel bovenaan.
    val scope = rememberCoroutineScope()
    fun snapTo(index: Int) {
        scope.launch { listState.animateScrollToItem(index) }
    }
    fun Modifier.snapRow(index: Int) = onFocusChanged { if (it.hasFocus) snapTo(index) }
    fun focusApp(app: AppEntry) {
        hero.focused = Highlight.App(app, channelArt()[app.packageName]?.firstOrNull())
    }

    // Geen achtergrondkleur hier: het venster heeft die al (scheelt een schermvullende laag).
    Box(Modifier.fillMaxSize()) {
        // Achtergrond van de top shelf: vast, loopt zacht door achter de eerste rij.
        Backdrop({ hero.shown }, posterColors, loadHeroBanner, Modifier.fillMaxWidth().height(HeroHeight + 110.dp))

        Column(Modifier.fillMaxSize()) {
            // Sticky top shelf: naam + info van wat focus heeft blijft altijd staan.
            Box(Modifier.fillMaxWidth().height(HeroHeight)) {
                HeroInfo({ hero.shown }, isLiked, pins)
                TopBar(onOpenLiked, onOpenSettings, downTarget = firstFocus)
            }

            // Rijen eronder klikken "balk voor balk": de rij met focus schuift steeds bovenaan.
            // Binnen een rij (horizontaal) houden we het standaard tv-gedrag.
            val rowSpec = LocalBringIntoViewSpec.current
            CompositionLocalProvider(LocalBringIntoViewSpec provides NoAutoScroll) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 4.dp, bottom = 210.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            item(key = "pins", contentType = "row") {
                Section("Vastgezet", Modifier.snapRow(0)) {
                    if (pinned.isEmpty()) {
                        Hint("Houd OK ingedrukt op een app om hem hier vast te zetten.")
                    } else {
                        CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Gutter, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                        ) {
                            itemsIndexed(pinned, key = { _, a -> a.pinKey }) { i, app ->
                                AppTile(
                                    app = app, width = 168.dp, pinned = false,
                                    onClick = { onLaunch(app) },
                                    onLongClick = { onAppMenu(app) },
                                    onFocused = { focusApp(app) },
                                    modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                                )
                            }
                        }
                        }
                    }
                }
            }

            item(key = "recs", contentType = "row") {
                Section("Aanbevolen voor jou", Modifier.snapRow(1)) {
                    if (recs.isEmpty()) {
                        Hint(recMessage ?: "Aanbevelingen laden…")
                    } else {
                        CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Gutter, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            itemsIndexed(recs, key = { _, m -> m.id }) { i, movie ->
                                PosterCard(
                                    movie = movie,
                                    liked = isLiked(movie.id),
                                    onClick = { onOpenMovie(movie) },
                                    onLongClick = { onToggleLike(movie) },
                                    available = isAvailable(movie),
                                    onFocused = { hero.focused = Highlight.Film(movie) },
                                    width = 100.dp,
                                    modifier = if (i == 0 && pinned.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier,
                                )
                            }
                        }
                        }
                    }
                }
            }

            // De titel "Alle apps" zit in de eerste rij, zodat hij meeschuift met die balk.
            itemsIndexed(rows, key = { _, row -> "row:" + row.first().pinKey }, contentType = { _, _ -> "grid" }) { i, row ->
                Column(Modifier.snapRow(2 + i)) {
                if (i == 0) SectionTitle("Alle apps")
                Row(
                    Modifier.padding(horizontal = Gutter, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    row.forEach { app ->
                        AppTile(
                            app = app, width = 152.dp, pinned = app.pinKey in pins,
                            onClick = { onLaunch(app) },
                            onLongClick = { onAppMenu(app) },
                            onFocused = { focusApp(app) },
                            modifier = if (recs.isEmpty() && pinned.isEmpty() && app == apps.first())
                                Modifier.focusRequester(firstFocus) else Modifier,
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

/**
 * Verticaal scrollt de lijst nooit "vanzelf" naar een item: dat doet snapTo() per hele rij.
 * Anders overschrijft het standaardgedrag (net genoeg scrollen) de rij-voor-rij-animatie.
 */
@OptIn(ExperimentalFoundationApi::class)
private val NoAutoScroll = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

@Composable
private fun HeroInfo(hero: () -> Highlight?, isLiked: (Int) -> Boolean, pins: List<String>) {
    Column(
        Modifier.padding(start = Gutter, top = 56.dp).widthIn(max = 470.dp).height(HeroHeight - 70.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        when (val h = hero()) {
            is Highlight.Film -> FilmInfo(h.movie, isLiked(h.movie.id))
            is Highlight.App -> AppInfo(h.app, h.channel, h.app.pinKey in pins)
            null -> Unit
        }
    }
}

@Composable
private fun FilmInfo(movie: Movie, liked: Boolean) {
    Text(movie.reason.uppercase(), fontSize = 11.sp, letterSpacing = 1.5.sp, color = Palette.Accent)
    Text(
        movie.title, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text,
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
    )
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(movie.year, fontSize = 14.sp, color = Palette.TextDim)
        movie.imdbRating?.let { Badge("IMDb", it, Palette.Imdb, Color.Black) }
        movie.rtScore?.let { Badge("RT", it, Palette.Tomato, Color.White) }
        if (liked) Text("  ♥ Vind je leuk", fontSize = 14.sp, color = Palette.Like)
    }
    Text(
        movie.overview, fontSize = 14.sp, color = Palette.TextDim, maxLines = 3,
        overflow = TextOverflow.Ellipsis, lineHeight = 20.sp, modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun AppInfo(app: AppEntry, channel: ChannelItem?, pinned: Boolean) {
    Text(if (pinned) "VASTGEZET" else "APP", fontSize = 11.sp, letterSpacing = 1.5.sp, color = Palette.Accent)
    Text(
        app.label, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text,
        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
    )
    Text(
        if (channel != null && channel.title.isNotBlank()) "Nu in ${app.label}: ${channel.title}"
        else "OK om te openen · lang OK voor opties",
        fontSize = 14.sp, color = Palette.TextDim, maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun Badge(label: String, value: String, bg: Color, fg: Color) {
    Spacer(Modifier.width(12.dp))
    Box(Modifier.background(bg, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg)
    }
    Text(" $value", fontSize = 14.sp, color = Palette.Text)
}

/**
 * Klok rechtsboven, ververst één keer per minuut. Verstopte functie: lang OK op de klok
 * opent je lijst met films die je leuk vond.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TopBar(onOpenLiked: () -> Unit, onOpenSettings: () -> Unit, downTarget: FocusRequester) {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000L - value.second * 1_000L)
        }
    }
    val dateFmt = remember { DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.forLanguageTag("nl-NL")) }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }

    Row(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter - 12.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        // Instellingen: API-sleutels en IPTV-inlog
        Surface(
            onClick = onOpenSettings,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = Palette.TextDim,
                focusedContainerColor = Palette.Text,
                focusedContentColor = Palette.Background,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            modifier = Modifier.padding(end = 10.dp).focusProperties {
                down = downTarget
                up = FocusRequester.Cancel
                left = FocusRequester.Cancel
            },
        ) {
            Icon(Icons.Filled.Settings, contentDescription = "Instellingen", modifier = Modifier.padding(10.dp).size(22.dp))
        }
        Surface(
            onClick = {},
            onLongClick = onOpenLiked,
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.White.copy(alpha = 0.16f),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Palette.Text), shape = RoundedCornerShape(8.dp)),
            ),
            // Vanaf de klok altijd terug naar de eerste rij; links/rechts/omhoog doet niets.
            modifier = Modifier.focusProperties {
                down = downTarget
                right = FocusRequester.Cancel
                up = FocusRequester.Cancel
            },
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalAlignment = Alignment.End) {
                Text(now.format(timeFmt), fontSize = 22.sp, color = Palette.Text)
                Text(now.format(dateFmt), fontSize = 12.sp, color = Palette.TextDim)
            }
        }
    }
}

@Composable
private fun Section(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier) {
        SectionTitle(title)
        content()
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Palette.TextDim,
        modifier = Modifier.padding(horizontal = Gutter),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 14.sp, color = Palette.TextDim, modifier = Modifier.padding(horizontal = Gutter, vertical = 12.dp))
}
