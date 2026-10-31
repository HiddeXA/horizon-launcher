package nl.thuis.launcher.bios

import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusGroup
import android.content.Context
import android.content.Intent
import android.app.SearchManager
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.tv.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.TextStyle
import androidx.tv.material3.ProvideTextStyle
import nl.thuis.launcher.R
import androidx.compose.runtime.CompositionLocalProvider
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Typography
import coil3.compose.AsyncImage

/**
 * Huisstijl Horizon, "Deep Space / Twilight": rustig, chic en premium.
 * Inktblauw als basis, donker marineblauw voor vlakken, zacht hemelsblauw als accent.
 */
/**
 * Een kleurenset. Horizon en Horizon TV gebruiken [Twilight]; Roze Hoek gebruikt [Roze90s].
 * [retro] = true schakelt ook het 90s-logo, de Memphis-patronen en de 90s-startanimatie in.
 */
data class HorizonPalette(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val accent: Color,
    val mint: Color,
    val text: Color,
    val dim: Color,
    val onAccent: Color,
    /** Extra 90s-kleuren (alleen gebruikt als [retro]). */
    val pop: Color = accent,
    val violet: Color = mint,
    val retro: Boolean = false,
    /** Het woord in het logo. */
    val wordmark: String = "Horizon",
    /** Onderregel onder het logo (alleen bij grotere logo's: startanimatie, laadscherm). */
    val tagline: String? = null,
) {
    companion object {
        /** Deep Space / Twilight: de Horizon-huisstijl. */
        val Twilight = HorizonPalette(
            background = Color(0xFF0B132B),   // inktblauw
            surface = Color(0xFF1C2541),      // donker marineblauw (kaarten, kaders)
            surfaceHigh = Color(0xFF26315A),  // iets lichter, voor knoppen zonder focus
            accent = Color(0xFF48CAE4),       // zacht hemelsblauw
            mint = Color(0xFF5BC0BE),         // mintgoud
            text = Color(0xFFE0E1DD),         // off-white
            dim = Color(0xFF9AA3B8),          // gedempte tekst, blauwgrijs
            onAccent = Color(0xFF0B132B),     // tekst op een accentvlak
        )

        /** Roze Hoek: 90s-roze (hot pink met turquoise, citroengeel en paars, zoals Memphis-design). */
        val Roze90s = HorizonPalette(
            background = Color(0xFF1E0A1E),   // diep pruim
            surface = Color(0xFF3A1236),      // donker framboos
            surfaceHigh = Color(0xFF52194C),
            accent = Color(0xFFFF3EA5),       // hot pink
            mint = Color(0xFF2DE2C8),         // turquoise
            text = Color(0xFFFFF0F7),         // roze-wit
            dim = Color(0xFFD29BBE),          // zacht oudroze
            onAccent = Color(0xFF1E0A1E),
            pop = Color(0xFFFFE14D),          // citroengeel
            violet = Color(0xFF9B5CFF),       // paars
            retro = true,
            wordmark = "Roze Hoek",
            tagline = "Inspired By Eppie Bos",
        )
    }
}

/**
 * De kleuren van het huidige scherm. Elke activity zet bij het starten en terugkomen zijn eigen
 * [palette] (Horizon/Horizon TV: Twilight, Roze Hoek: Roze90s). Omdat het Compose-state is, kleurt
 * alles direct mee om. Alle bestaande HorizonColors.X-aanroepen blijven gewoon werken.
 */
object HorizonColors {
    var palette by mutableStateOf(HorizonPalette.Twilight)

    val Background get() = palette.background
    val Surface get() = palette.surface
    val SurfaceHigh get() = palette.surfaceHigh
    val Accent get() = palette.accent
    val Mint get() = palette.mint
    val Text get() = palette.text
    val Dim get() = palette.dim
    val OnAccent get() = palette.onAccent
    val Pop get() = palette.pop
    val Violet get() = palette.violet
    val retro get() = palette.retro
}

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
    Font(R.font.poppins_bold, FontWeight.ExtraBold),
    Font(R.font.poppins_bold, FontWeight.Black),
)

/** Alle tekststijlen in Poppins (knoppen van tv-material zetten hun eigen stijl, dus via de typografie). */
private val HorizonTypography: Typography = Typography().let { t ->
    fun TextStyle.p() = copy(fontFamily = Poppins)
    Typography(
        displayLarge = t.displayLarge.p(), displayMedium = t.displayMedium.p(), displaySmall = t.displaySmall.p(),
        headlineLarge = t.headlineLarge.p(), headlineMedium = t.headlineMedium.p(), headlineSmall = t.headlineSmall.p(),
        titleLarge = t.titleLarge.p(), titleMedium = t.titleMedium.p(), titleSmall = t.titleSmall.p(),
        bodyLarge = t.bodyLarge.p(), bodyMedium = t.bodyMedium.p(), bodySmall = t.bodySmall.p(),
        labelLarge = t.labelLarge.p(), labelMedium = t.labelMedium.p(), labelSmall = t.labelSmall.p(),
    )
}

@Composable
fun HorizonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        typography = HorizonTypography,
        colorScheme = darkColorScheme(
            primary = HorizonColors.Accent, onPrimary = HorizonColors.OnAccent,
            background = HorizonColors.Background, surface = HorizonColors.Surface,
            onBackground = HorizonColors.Text, onSurface = HorizonColors.Text,
        ),
    ) {
        // Let op: geen kleur in de tekststijl, anders wint die van de inhoudskleur van knoppen met focus.
        CompositionLocalProvider(LocalContentColor provides HorizonColors.Text) {
            ProvideTextStyle(TextStyle(fontFamily = Poppins), content)
        }
    }
}

enum class BiosSection(val label: String, val icon: ImageVector?) {
    SEARCH("Zoeken", Icons.Filled.Search),
    HOME("Home", null),
    FILTER("Filter", null),
    MYLIST("Mijn lijst", null),
}

/** Linkerrand van de inhoud en hoogte van het bovenmenu. */
val ContentStart = 48.dp
val NavHeight = 76.dp

/**
 * Bovenmenu met pilvormige knoppen (zoals moderne streamingapps op tv).
 * "Omlaag" gaat altijd naar de inhoud ([contentFocus]); links/rechts blijft binnen het menu.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TopNav(
    current: BiosSection,
    onSelect: (BiosSection) -> Unit,
    contentFocus: FocusRequester,
    filterActive: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(NavHeight)
            .background(Brush.verticalGradient(0f to HorizonColors.Background.copy(alpha = 0.85f), 1f to Color.Transparent))
            .padding(start = ContentStart, end = ContentStart, top = 18.dp),
        verticalAlignment = Alignment.Top,
    ) {
        HorizonLogo(Modifier.padding(end = 30.dp, top = 2.dp).height(36.dp))
        // Omhoog vanuit de inhoud: altijd uitkomen op de pil van het huidige scherm.
        val pills = remember { BiosSection.entries.map { FocusRequester() } }
        Row(
            Modifier.focusProperties { enter = { pills[current.ordinal] } }.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BiosSection.entries.forEachIndexed { i, s ->
                val selected = s == current
                Surface(
                    onClick = { onSelect(s) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (selected) HorizonColors.Surface else Color.Transparent,
                        contentColor = if (selected) HorizonColors.Text else HorizonColors.Dim,
                        focusedContainerColor = HorizonColors.Text,
                        focusedContentColor = HorizonColors.Background,
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                    modifier = Modifier.focusRequester(pills[i]).focusProperties {
                        down = contentFocus
                        up = FocusRequester.Cancel
                        if (i == 0) left = FocusRequester.Cancel
                        if (i == BiosSection.entries.lastIndex) right = FocusRequester.Cancel
                    },
                ) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        s.icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp).padding(end = 0.dp)); Spacer(Modifier.width(6.dp)) }
                        Text(s.label, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                        if (s == BiosSection.FILTER && filterActive) {
                            Spacer(Modifier.width(6.dp))
                            Box(Modifier.size(7.dp).background(HorizonColors.Accent, RoundedCornerShape(50)))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Laadscherm: het logo staat precies waar de startanimatie eindigt, en de laadbalk ligt op de plek
 * van de horizonlijn. Daaronder wat er gebeurt, hoe ver, en hoeveel films er al binnen zijn.
 */
@Composable
fun LoadingScreen(phase: String, progress: Float?, count: Int, modifier: Modifier = Modifier, badge: String? = null, unit: String = "films") {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val h = with(androidx.compose.ui.platform.LocalDensity.current) {
            introLogoHeight(maxWidth.toPx(), maxHeight.toPx()).toDp()
        }
        val lineW = h * logoAspect(badge) * 1.15f
        HorizonLogo(Modifier.align(Alignment.Center).height(h), badge = badge)
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = maxHeight / 2 + h * HORIZON_LINE_Y - 1.5.dp).width(lineW),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(HorizonColors.Surface)) {
                if (progress != null) {
                    val shown by animateFloatAsState(progress, tween(250), label = "voortgang")
                    Box(Modifier.fillMaxHeight().fillMaxWidth(shown).background(Brush.horizontalGradient(listOf(HorizonColors.Mint, HorizonColors.Accent))))
                } else {
                    // Onbekende grootte: een glimmend stukje dat heen en weer glijdt.
                    val t = rememberInfiniteTransition(label = "laden")
                    val x by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        Box(
                            Modifier.fillMaxHeight().width(maxWidth * 0.3f).offset(x = maxWidth * (x * 1.3f - 0.3f))
                                .background(Brush.horizontalGradient(listOf(Color.Transparent, HorizonColors.Accent, Color.Transparent)))
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Text(
                    if (count > 0) "${"%,d".format(count).replace(',', '.')} $unit" else "",
                    color = HorizonColors.Dim, fontSize = 13.sp, modifier = Modifier.weight(1f),
                )
                if (progress != null) Text("${(progress * 100).toInt()}%", color = HorizonColors.Dim, fontSize = 13.sp)
            }
            Text(phase, color = HorizonColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 14.dp))
            Text(
                "De eerste keer duurt dit even; daarna gaat het direct.",
                color = HorizonColors.Dim.copy(alpha = 0.75f), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private val TileShape = RoundedCornerShape(6.dp)

/** Poster-tegel met optionele voortgangsbalk (bij "Verder kijken"). */
@Composable
fun VodTile(
    item: VodItem,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 104.dp,
    progress: Float? = null,
) {
    Column(Modifier.width(width)) {
        Card(
            onClick = onClick,
            shape = CardDefaults.shape(TileShape),
            scale = CardDefaults.scale(focusedScale = 1.08f),
            border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, HorizonColors.Accent), shape = TileShape)),
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .onFocusChanged { if (it.isFocused) onFocused() },
        ) {
            Box(Modifier.fillMaxSize().background(HorizonColors.Surface)) {
                // Zonder poster: titel als tekst, zodat de tegel nooit leeg is.
                Text(
                    item.title, color = HorizonColors.Dim, fontSize = 12.sp, textAlign = TextAlign.Center,
                    maxLines = 4, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(8.dp),
                )
                if (item.poster != null) {
                    AsyncImage(item.poster, item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                if (progress != null && progress > 0f) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).background(HorizonColors.Background.copy(alpha = 0.7f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(HorizonColors.Accent))
                    }
                }
            }
        }
    }
}

/** "2021 · 2u 15m · Actie, Drama" + score */
@Composable
fun VodMeta(item: VodItem, info: VodInfo?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val rating = item.rating ?: BiosGenres.meta[item.id]?.vote?.takeIf { it > 0 }
        if (rating != null) {
            Text("★ ${"%.1f".format(rating)}", color = HorizonColors.Mint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
        }
        val tmdbGenres = BiosGenres.meta[item.id]?.genres?.mapNotNull { Genre.byId[it]?.label }?.take(2)?.joinToString(", ")
        val parts = listOfNotNull(
            // TMDB weet het echte jaar; de aanbieder zet soms het jaar van toevoegen in de naam.
            BiosGenres.meta[item.id]?.year ?: item.year ?: info?.releaseDate?.take(4),
            // Korte duur (<10 min) is bij sommige aanbieders onzin ("1m"): dan weglaten.
            info?.durationSecs?.takeIf { it >= 600 }?.let { duration(it) },
            // TMDB-genres zijn betrouwbaarder en Nederlands; die van de aanbieder alleen als reserve.
            (tmdbGenres?.takeIf { it.isNotBlank() } ?: info?.genre?.split(',', '/')?.take(2)?.joinToString(", ") { it.trim() })?.takeIf { it.isNotBlank() },
        )
        Text(parts.joinToString("  ·  "), color = HorizonColors.Dim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

fun duration(secs: Int): String = if (secs >= 3600) "${secs / 3600}u ${(secs % 3600) / 60}m" else "${secs / 60}m"

fun timecode(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun EmptyMessage(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = HorizonColors.Text)
        Text(body, fontSize = 15.sp, color = HorizonColors.Dim, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(4.dp)))
    }
}

/** Trailer: direct afspelen als we de YouTube-code weten, anders laat YouTube er zelf één zoeken. */
fun openYouTube(context: Context, key: String?, query: String): Boolean {
    val yt = "com.google.android.youtube.tv"
    val intents = listOfNotNull(
        key?.let { Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$it")).setPackage(yt) },
        Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).setPackage(yt).putExtra(SearchManager.QUERY, query),
    )
    for (i in intents) {
        try {
            context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true
        } catch (_: Exception) {
        }
    }
    return false
}
