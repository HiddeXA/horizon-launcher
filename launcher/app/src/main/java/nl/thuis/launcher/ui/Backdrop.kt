package nl.thuis.launcher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import nl.thuis.launcher.AppEntry
import nl.thuis.launcher.brandColorsOf
import nl.thuis.launcher.data.ChannelItem
import nl.thuis.launcher.data.Movie

/** Wat er in de top shelf staat: een film of een app (eventueel met een beeld uit het kanaal van die app). */
@Immutable
sealed interface Highlight {
    data class Film(val movie: Movie) : Highlight
    data class App(val app: AppEntry, val channel: ChannelItem? = null) : Highlight
}

/**
 * Houdt bij wat focus heeft ([focused], direct) en wat getoond wordt ([shown], na een korte pauze).
 * Alleen de top shelf leest deze waarden, dus een focuswissel tekent niet het hele scherm opnieuw.
 */
@Stable
class HeroState {
    var focused by mutableStateOf<Highlight?>(null)
    var shown by mutableStateOf<Highlight?>(null)
}

private val CardShape = RoundedCornerShape(18.dp)

/**
 * Achtergrond in de stijl van de Apple TV top shelf:
 *  - film met still: still paginabreed;
 *  - app met eigen kanaalbeelden (Netflix, YouTube, ...): dat beeld paginabreed;
 *  - anders een "merkpodium": egale merkkleur + zachte gloed + scherpe, grote banner/poster als kaart.
 * Geen realtime blur: dat is op de GPU van de Chromecast duur.
 */
@Composable
fun Backdrop(
    hero: () -> Highlight?,
    posterColors: SnapshotStateMap<Int, BrandColors>,
    loadHeroBanner: suspend (AppEntry) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    LightCrossfade(target = hero(), modifier = modifier) { h ->
        when (h) {
            is Highlight.Film -> FilmStage(h.movie, posterColors)
            is Highlight.App -> AppStage(h, loadHeroBanner)
            null -> Unit
        }
    }
}

/**
 * Crossfade zonder offscreen-buffer: ModulateAlpha past de transparantie per tekenopdracht toe
 * in plaats van eerst het hele (schermbrede) scherm in een tussenlaag te tekenen.
 * Op de Mali-GPU van de Chromecast scheelt dat twee volle schermvullingen per beeld.
 */
@Composable
private fun <T> LightCrossfade(target: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    var current by remember { mutableStateOf(target) }
    var previous by remember { mutableStateOf<T?>(null) }
    var hasPrevious by remember { mutableStateOf(false) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(target) {
        if (target == current) return@LaunchedEffect
        previous = current
        hasPrevious = true
        current = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(260))
        hasPrevious = false
        previous = null
    }

    Box(modifier) {
        if (hasPrevious) {
            @Suppress("UNCHECKED_CAST")
            val prev = previous as T
            key(prev) {
                Box(Modifier.fillMaxSize().graphicsLayer {
                    alpha = 1f - progress.value
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }) { content(prev) }
            }
        }
        key(current) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = progress.value
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }) { content(current) }
        }
    }
}

@Composable
private fun FilmStage(movie: Movie, posterColors: SnapshotStateMap<Int, BrandColors>) {
    if (movie.backdropUrl != null) {
        FullBleed(movie.backdropUrl!!)
        return
    }
    // Geen still: poster als kaart op een podium in de kleuren van de poster.
    val colors = posterColors[movie.id] ?: BrandColors.Neutral
    Stage(Color(colors.base), Color(colors.glow)) {
        val context = LocalContext.current
        AsyncImage(
            model = ImageRequest.Builder(context).data(movie.posterUrl).allowHardware(false).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onSuccess = { s ->
                if (movie.id !in posterColors) posterColors[movie.id] = brandColorsOf(s.result.image.toBitmap())
            },
            modifier = Modifier.fillMaxHeight().aspectRatio(2f / 3f).cardLook(),
        )
    }
}

@Composable
private fun AppStage(h: Highlight.App, loadHeroBanner: suspend (AppEntry) -> ImageBitmap?) {
    val app = h.app
    if (h.channel != null) {
        FullBleed(h.channel.imageUri)
        return
    }
    Stage(app.brand, app.glow) {
        if (app.isBanner) {
            val big by produceState<ImageBitmap?>(null, app.pinKey) { value = loadHeroBanner(app) }
            Image(
                bitmap = big ?: app.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxHeight().aspectRatio(16f / 9f).cardLook(),
            )
        } else {
            Box(
                Modifier.fillMaxHeight().aspectRatio(1f).cardLook().background(lerp(app.brand, Color.Black, 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Image(app.image, null, modifier = Modifier.fillMaxSize(0.62f))
            }
        }
    }
}

/**
 * Egale, verdonkerde merkkleur met een zachte gloed rechts. De kaart staat rechtsboven in de
 * top shelf: onder de klok en boven de eerste rij, zodat niets overlapt.
 */
@Composable
private fun Stage(brand: Color, glow: Color, card: @Composable () -> Unit) {
    val bg = remember(brand, glow) { StageBaker.stage(brand.toArgb(), glow.toArgb()) }
    Box(Modifier.fillMaxSize()) {
        Image(bg, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = CardTop, end = 48.dp)
                .height(CardHeight),
            contentAlignment = Alignment.CenterEnd,
        ) { card() }
    }
}

/** Ruimte voor de klok erboven en de eerste rij eronder (top shelf is 250 dp hoog). */
private val CardTop = 78.dp
private val CardHeight = 156.dp

@Composable
private fun FullBleed(url: String) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context).data(url).size(1280, 720).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = Alignment.TopEnd,
        modifier = Modifier.fillMaxSize(),
    )
    Image(StageBaker.scrim, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
}

private fun Modifier.cardLook() = this
    .clip(CardShape)
    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), CardShape)

/**
 * Bakt de achtergrondverlopen één keer vooraf in een klein plaatje (480x180, zelfde 8:3-verhouding
 * als de top shelf). De GPU tekent dan één opgeschaald plaatje i.p.v. vier schermvullende verlopen.
 * Zachte verlopen worden bij opschalen niet onscherp, dus dit ziet er hetzelfde uit.
 */
internal object StageBaker {
    private const val W = 480
    private const val H = 180
    private val bg = Palette.Background.toArgb()
    private val cache = LruCache<Long, ImageBitmap>(24)

    val scrim: ImageBitmap by lazy { bake(null, null) }

    fun stage(brand: Int, glow: Int): ImageBitmap {
        val key = (brand.toLong() shl 32) or (glow.toLong() and 0xFFFFFFFFL)
        return cache.get(key) ?: bake(brand, glow).also { cache.put(key, it) }
    }

    private fun bake(brand: Int?, glow: Int?): ImageBitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        if (brand != null && glow != null) {
            c.drawColor(lerp(Color(brand), Palette.Background, 0.55f).toArgb())
            p.shader = RadialGradient(
                W * 0.78f, H * 0.36f, W * 0.36f,
                Color(glow).copy(alpha = 0.5f).toArgb(), 0, Shader.TileMode.CLAMP,
            )
            c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        }
        p.shader = LinearGradient(
            0f, 0f, W.toFloat(), 0f,
            intArrayOf(bg, Palette.Background.copy(alpha = 0.72f).toArgb(), 0),
            floatArrayOf(0f, 0.38f, 0.62f), Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        p.shader = LinearGradient(
            0f, 0f, 0f, H.toFloat(),
            intArrayOf(0, 0, bg), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
        return bmp.asImageBitmap()
    }
}
