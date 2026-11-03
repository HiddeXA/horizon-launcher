package nl.thuis.launcher.bios

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/*
 * De 90s-stijl van Roze Hoek (actief als HorizonColors.retro):
 *  - een woordmerk in schuine, vette letters met een getrapte turquoise schaduw en een gele "18+"-sticker;
 *  - Memphis-patronen (krulletjes, driehoekjes, puntjes, zigzags) op de achtergronden;
 *  - een startanimatie als een videoband die begint: PLAY-tekst, beeldlijnen, vormen die erin ploppen.
 * Het woordmerk heeft dezelfde maten als het Horizon-logo, zodat laadscherm en startanimatie op elkaar aansluiten.
 */

// --- Woordmerk ---

private val retroCache = HashMap<String, TextLayoutResult>()

private fun retroStyle(sizePx: Float, density: Float) = TextStyle(
    fontFamily = Poppins, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic,
    fontSize = (sizePx / density).sp, letterSpacing = (-0.5).sp,
)

/** Woord zo groot mogelijk (maximaal 0,66 × de hoogte) dat nog in [maxW] past. */
private fun fitWord(measurer: TextMeasurer, word: String, h: Float, maxW: Float, density: Float): TextLayoutResult {
    val key = "$word/${h.toInt()}/${maxW.toInt()}"
    return retroCache.getOrPut(key) {
        val first = measurer.measure(word, retroStyle(h * 0.66f, density))
        if (first.size.width <= maxW) first
        else measurer.measure(word, retroStyle(h * 0.66f * maxW / first.size.width * 0.98f, density))
    }
}

private fun taglineLayout(measurer: TextMeasurer, text: String, h: Float, density: Float): TextLayoutResult =
    retroCache.getOrPut("tag/$text/${h.toInt()}") {
        measurer.measure(text, TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontStyle = FontStyle.Italic, fontSize = (h * 0.15f / density).sp, letterSpacing = 1.5.sp))
    }

private fun stickerLayout(measurer: TextMeasurer, text: String, h: Float, density: Float): TextLayoutResult =
    retroCache.getOrPut("sticker/$text/${h.toInt()}") {
        measurer.measure(text, TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = (h * 0.24f / density).sp))
    }

/**
 * Tekent het 90s-woordmerk in een vak van [boxW] breed, verticaal gecentreerd op [cy].
 * [shadow] 0..1 = hoe ver de schaduw al "uitgeschoven" is, [wordDx] schuift het woord (voor de animatie).
 */
internal fun DrawScope.drawRetroWordmark(
    measurer: TextMeasurer, badge: String?, left: Float, cy: Float, h: Float, boxW: Float,
    shadow: Float = 1f, wordAlpha: Float = 1f, wordDx: Float = 0f,
    stickerScale: Float = 1f, stickerAlpha: Float = 1f,
    taglineChars: Int = Int.MAX_VALUE, taglineCursor: Boolean = false,
) {
    val word = HorizonColors.palette.wordmark
    // Met onderregel (alleen bij een groot logo) schuift het woord omhoog en wordt het iets kleiner,
    // zodat woord + onderregel samen binnen de hoogte h passen (het logo wordt in een vak getekend
    // en alles daarbuiten valt weg). Startanimatie en laadscherm gebruiken dezelfde indeling.
    val tagline = HorizonColors.palette.tagline
    val withTag = tagline != null && h >= 40f * density
    val hw = if (withTag) h * 0.74f else h
    val wcy = if (withTag) cy - h * 0.12f else cy
    val sw = if (badge != null) hw * 0.86f else 0f
    val gap = if (badge != null) hw * 0.16f else 0f
    val layout = fitWord(measurer, word, hw, boxW - sw - gap - hw * 0.12f, density)
    val tw = layout.size.width.toFloat()
    val x0 = left + (boxW - (tw + gap + sw)) / 2f
    val ty = wcy - layout.size.height / 2f

    if (wordAlpha > 0f) {
        // Getrapte schaduw (3D-letters zoals op een 90s-videohoes): paars achteraan, turquoise ervoor.
        val step = hw * 0.028f * shadow
        if (step > 0.1f) {
            for (k in 4 downTo 1) {
                val c = if (k >= 3) HorizonColors.Violet else HorizonColors.Mint
                drawText(layout, c, topLeft = Offset(x0 + wordDx + step * k, ty + step * k), alpha = wordAlpha)
            }
        }
        // De letters zelf: citroengeel bovenin dat overloopt in hot pink.
        val brush = Brush.verticalGradient(
            0f to HorizonColors.Pop, 0.42f to HorizonColors.Accent, 1f to HorizonColors.Accent,
            startY = ty + layout.size.height * 0.18f, endY = ty + layout.size.height * 0.82f,
        )
        drawText(layout, brush, topLeft = Offset(x0 + wordDx, ty), alpha = wordAlpha)
    }

    // Onderregel "Inspired By Eppie Bos": turquoise, schuin, gecentreerd onder het woord.
    // Alleen bij een groot logo (startanimatie, laadscherm); in de kleine logo's past het niet.
    if (withTag && tagline != null && wordAlpha > 0f && taglineChars > 0) {
        val full = taglineLayout(measurer, tagline, h, density)
        val shown = tagline.take(taglineChars)
        val part = if (shown.length == tagline.length) full else taglineLayout(measurer, shown, h, density)
        val tx = x0 + tw / 2f - full.size.width / 2f
        val tyTop = cy + h * 0.20f
        drawText(part, HorizonColors.Violet, topLeft = Offset(tx + h * 0.012f, tyTop + h * 0.012f), alpha = wordAlpha * 0.9f)
        drawText(part, HorizonColors.Mint, topLeft = Offset(tx, tyTop), alpha = wordAlpha)
        if (taglineCursor) {
            val cx = tx + part.size.width + h * 0.02f
            drawRect(HorizonColors.Pop.copy(alpha = wordAlpha), Offset(cx, tyTop + part.size.height * 0.22f), Size(h * 0.016f, part.size.height * 0.58f))
        }
    }

    if (badge != null && stickerAlpha > 0f && stickerScale > 0f) {
        val c = Offset(x0 + tw + gap + sw / 2f, wcy - hw * 0.12f)
        val sh = hw * 0.5f
        withTransform({
            rotate(-11f, c)
            scale(stickerScale, stickerScale, c)
        }) {
            val tl = Offset(c.x - sw / 2f, c.y - sh / 2f)
            val r = CornerRadius(sh * 0.22f)
            drawRoundRect(Color.Black.copy(alpha = 0.35f * stickerAlpha), tl + Offset(hw * 0.04f, hw * 0.05f), Size(sw, sh), r)
            drawRoundRect(HorizonColors.Pop.copy(alpha = stickerAlpha), tl, Size(sw, sh), r)
            drawRoundRect(HorizonColors.Accent.copy(alpha = stickerAlpha), tl, Size(sw, sh), r, style = Stroke(hw * 0.035f))
            val l = stickerLayout(measurer, badge, hw, density)
            drawText(l, HorizonColors.OnAccent, topLeft = Offset(c.x - l.size.width / 2f, c.y - l.size.height / 2f), alpha = stickerAlpha)
        }
    }
}

// --- Memphis-patroon ---

internal class MemphisShape(
    val kind: Int, val center: Offset, val size: Float, val rotation: Float, val color: Int, val path: Path?,
    /** Beweging: zweven in een rondje van [drift] px met [speed] rad/s, en langzaam draaien met [spin] graden/s. */
    val phase: Float, val speed: Float, val spin: Float, val drift: Float,
)

/** Zelfde klok voor startanimatie en laadscherm, zodat de vormen naadloos doorzweven. */
internal fun memphisSeconds(frameMs: Long): Float = (frameMs % 3_600_000L) / 1000f

/** Vormen van de startanimatie en het laadscherm (zelfde set, zodat ze op dezelfde plek doorgaan). */
const val MEMPHIS_INTRO_SEED = 1995

private fun squiggle(c: Offset, len: Float, amp: Float): Path = Path().apply {
    val n = 28
    for (i in 0..n) {
        val f = i / n.toFloat()
        val x = c.x - len / 2f + len * f
        val y = c.y + sin(f * 2.5f * 2f * PI.toFloat()) * amp
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
}

private fun zigzag(c: Offset, len: Float, amp: Float): Path = Path().apply {
    val n = 6
    for (i in 0..n) {
        val x = c.x - len / 2f + len * i / n
        val y = c.y + if (i % 2 == 0) -amp else amp
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
}

private fun triangle(c: Offset, s: Float): Path = Path().apply {
    moveTo(c.x, c.y - s * 0.58f); lineTo(c.x + s * 0.5f, c.y + s * 0.29f); lineTo(c.x - s * 0.5f, c.y + s * 0.29f); close()
}

/** Vaste (per [seed]) verzameling vormen, verspreid over het vlak in een grof raster zodat ze niet klonteren. */
internal fun memphisShapes(size: Size, seed: Int, unit: Float): List<MemphisShape> {
    val rnd = Random(seed)
    val cols = 6; val rows = 4
    val out = ArrayList<MemphisShape>(cols * rows)
    for (r in 0 until rows) for (c in 0 until cols) {
        if (rnd.nextFloat() < 0.18f) continue
        val cx = size.width * (c + 0.2f + rnd.nextFloat() * 0.6f) / cols
        val cy = size.height * (r + 0.2f + rnd.nextFloat() * 0.6f) / rows
        // Het midden vrijhouden: daar staan het logo en de laadbalk.
        if (kotlin.math.abs(cx - size.width / 2f) < size.width * 0.3f && kotlin.math.abs(cy - size.height / 2f) < size.height * 0.2f) continue
        val center = Offset(cx, cy)
        val s = unit * (26f + rnd.nextFloat() * 22f)
        val kind = rnd.nextInt(6)
        val path = when (kind) {
            0 -> squiggle(center, s * 2.2f, s * 0.22f)
            1, 5 -> triangle(center, s)
            4 -> zigzag(center, s * 1.8f, s * 0.2f)
            else -> null
        }
        out += MemphisShape(
            kind, center, s, rnd.nextFloat() * 360f, rnd.nextInt(4), path,
            phase = rnd.nextFloat() * 2f * PI.toFloat(),
            speed = 0.45f + rnd.nextFloat() * 0.7f,
            spin = (rnd.nextFloat() - 0.5f) * 36f,
            drift = unit * (8f + rnd.nextFloat() * 12f),
        )
    }
    return out
}

private fun memphisColor(i: Int): Color = when (i) {
    0 -> HorizonColors.Accent
    1 -> HorizonColors.Mint
    2 -> HorizonColors.Pop
    else -> HorizonColors.Violet
}

/** Tekent één vorm; [scale] en [alpha] voor het erin ploppen, [t] (seconden) voor zweven en draaien (0 = stil). */
internal fun DrawScope.drawMemphis(m: MemphisShape, alpha: Float, unit: Float, scale: Float = 1f, t: Float = 0f) {
    if (alpha <= 0f || scale <= 0f) return
    val col = memphisColor(m.color).copy(alpha = alpha)
    val stroke = Stroke(width = unit * 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val dx = if (t == 0f) 0f else sin(t * m.speed + m.phase) * m.drift
    val dy = if (t == 0f) 0f else cos(t * m.speed * 0.8f + m.phase) * m.drift
    withTransform({
        translate(dx, dy)
        rotate(m.rotation + t * m.spin, m.center)
        scale(scale, scale, m.center)
    }) {
        when (m.kind) {
            0, 4 -> drawPath(m.path!!, col, style = stroke)
            1 -> drawPath(m.path!!, col, style = stroke)
            5 -> drawPath(m.path!!, col)
            2 -> {   // puntjesraster
                val d = m.size * 0.28f
                for (i in -1..1) for (j in -1..1) drawCircle(col, unit * 3f, m.center + Offset(i * d, j * d))
            }
            else -> drawCircle(col, m.size * 0.42f, m.center, style = stroke)   // ring
        }
    }
}

/**
 * Memphis-patroon achter de inhoud. Doet niets bij de Horizon-huisstijl, dus overal veilig te gebruiken.
 * De vormen worden één keer per schermformaat gemaakt.
 */
fun Modifier.memphis(alpha: Float = 0.16f, seed: Int = 90, widthFraction: Float = 1f): Modifier = drawWithCache {
    val unit = density
    // widthFraction < 1: alleen links tekenen (bijvoorbeeld niet over het tv-beeld rechts).
    val shapes = memphisShapes(Size(size.width * widthFraction, size.height), seed, unit)
    onDrawBehind { if (HorizonColors.retro) shapes.forEach { drawMemphis(it, alpha, unit) } }
}

// --- Startanimatie (90s) ---

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)
private fun phase(t: Float, from: Float, to: Float) = clamp01((t - from) / (to - from))
private fun easeOutCubic(x: Float) = 1f - (1f - x).pow(3)
private fun easeInOutCubic(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
private fun easeOutBack(x: Float, s: Float = 1.9f): Float { val c3 = s + 1f; return 1f + c3 * (x - 1f).pow(3) + s * (x - 1f).pow(2) }

private const val RETRO_MS = 3300L
private const val RETRO_EXIT_MS = 420L

/**
 * 90s-startanimatie, alsof er een videoband begint: beeldlijnen en een korte storingsstreep,
 * "▶ PLAY" linksboven, de Memphis-vormen ploppen erin, het woord schuift binnen met een veer,
 * de schaduw schuift uit en de 18+-sticker wordt erop geplakt. Elke toets slaat hem over.
 * Eindigt op exact dezelfde plek als het logo op het laadscherm.
 */
@Composable
fun RetroIntro(skip: Boolean, badge: String? = null, onDone: () -> Unit) {
    val measurer = rememberTextMeasurer()
    var time by remember { mutableLongStateOf(0L) }
    var startMs by remember { mutableLongStateOf(0L) }
    var exitAt by remember { mutableStateOf<Long?>(null) }
    // Vormen per schermformaat; gewone (geen state) opslag, want ze worden tijdens het tekenen gemaakt.
    val shapeCache = remember { arrayOfNulls<Pair<Size, List<MemphisShape>>>(1) }

    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        startMs = start
        while (true) {
            val now = withFrameMillis { it } - start
            time = now
            val ex = exitAt ?: (RETRO_MS - RETRO_EXIT_MS).also { if (now >= it) exitAt = it }
            if (now >= ex + RETRO_EXIT_MS) { onDone(); break }
        }
    }
    LaunchedEffect(skip) { if (skip && exitAt == null) exitAt = time }

    Canvas(Modifier.fillMaxSize()) {
        val t = time.toFloat()
        val exit = exitAt?.let { easeInOutCubic(phase(t, it.toFloat(), it.toFloat() + RETRO_EXIT_MS)) } ?: 0f
        val fade = 1f - exit
        val unit = density

        drawRect(HorizonColors.Background.copy(alpha = fade))

        // Zachte roze gloed in het midden.
        val glow = easeOutCubic(phase(t, 100f, 1200f))
        drawRect(
            Brush.radialGradient(
                listOf(HorizonColors.Accent.copy(alpha = 0.20f * glow * fade), HorizonColors.Violet.copy(alpha = 0.10f * glow * fade), Color.Transparent),
                center = center, radius = size.minDimension * 0.9f,
            )
        )

        // Memphis-vormen ploppen één voor één erin.
        val shapes = shapeCache[0]?.takeIf { it.first == size }?.second
            ?: memphisShapes(size, MEMPHIS_INTRO_SEED, unit).also { shapeCache[0] = size to it }
        // Ze blijven daarna zweven en langzaam draaien (zelfde klok als het laadscherm).
        val clock = memphisSeconds(startMs + time)
        shapes.forEachIndexed { i, m ->
            val p = phase(t, 150f + i * 45f, 150f + i * 45f + 380f)
            drawMemphis(m, 0.42f * clamp01(p * 2f) * fade, unit, scale = easeOutBack(p, 2.6f), t = clock)
        }

        // Woordmerk: schuift van links binnen met een veer, daarna schuift de schaduw uit en komt de sticker.
        val h = introLogoHeight(size.width, size.height)
        val boxW = h * logoAspect(badge)
        val wp = phase(t, 550f, 1150f)
        drawRetroWordmark(
            measurer, badge, (size.width - boxW) / 2f, size.height / 2f, h, boxW,
            shadow = easeOutBack(phase(t, 1100f, 1500f), 2.2f),
            wordAlpha = clamp01(wp * 2.5f) * fade,
            wordDx = -(1f - easeOutBack(wp, 1.4f)) * size.width * 0.6f,
            stickerScale = if (t < 1550f) 0f else 1f + 1.4f * (1f - easeOutBack(phase(t, 1550f, 1900f), 1.6f)),
            stickerAlpha = clamp01(phase(t, 1550f, 1700f)) * fade,
            // Daarna wordt "Inspired By Eppie Bos" erbij getypt, met een knipperend geel cursortje.
            taglineChars = (phase(t, 1850f, 2350f) * (HorizonColors.palette.tagline?.length ?: 0)).toInt(),
            taglineCursor = t in 1850f..2400f && (t / 120f).toInt() % 2 == 0,
        )

        // Videoband: "▶ PLAY" linksboven (knippert eerst), tellertje linksonder.
        val osd = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = (size.height * 0.032f / unit).sp, letterSpacing = 2.sp)
        if (t < RETRO_MS - RETRO_EXIT_MS && (t > 900f || (t / 180f).toInt() % 2 == 0)) {
            val play = measurer.measure("▶ PLAY", osd)
            drawText(play, HorizonColors.Text, topLeft = Offset(size.width * 0.06f, size.height * 0.08f), alpha = 0.85f * fade)
            val secs = (t / 1000f).toInt(); val frames = ((t % 1000f) / 40f).toInt()
            val counter = measurer.measure("SP  0:00:%02d:%02d".format(secs, frames), osd)
            drawText(counter, HorizonColors.Text, topLeft = Offset(size.width * 0.06f, size.height * 0.86f), alpha = 0.7f * fade)
        }

        // Storingsstreep aan het begin: een roze band die over het beeld rolt.
        val gp = phase(t, 0f, 420f)
        if (gp in 0.001f..0.999f) {
            val y = size.height * (1.1f - 1.3f * gp)
            drawRect(HorizonColors.Accent.copy(alpha = 0.18f * fade), Offset(0f, y), Size(size.width, size.height * 0.06f))
            drawRect(HorizonColors.Mint.copy(alpha = 0.12f * fade), Offset(0f, y + size.height * 0.07f), Size(size.width, size.height * 0.015f))
        }

        // Beeldlijnen over alles (zoals op een oude buis-tv).
        val line = 3f * unit
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent, 0.5f to Color.Transparent, 0.5f to Color.Black.copy(alpha = 0.16f), 1f to Color.Black.copy(alpha = 0.16f),
                startY = 0f, endY = line, tileMode = TileMode.Repeated,
            ),
            alpha = fade,
        )
    }
}

/**
 * Zwevende Memphis-vormen voor het laadscherm na de startanimatie: dezelfde vormen op dezelfde klok,
 * dus ze bewegen naadloos door als de animatie wegvalt. Doet niets bij de Horizon-huisstijl.
 */
@Composable
fun MovingMemphis(modifier: Modifier = Modifier, alpha: Float = 0.3f, seed: Int = MEMPHIS_INTRO_SEED) {
    if (!HorizonColors.retro) return
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) frame = withFrameMillis { it } }
    val cache = remember { arrayOfNulls<Pair<Size, List<MemphisShape>>>(1) }
    Canvas(modifier.fillMaxSize()) {
        val unit = density
        val shapes = cache[0]?.takeIf { it.first == size }?.second
            ?: memphisShapes(size, seed, unit).also { cache[0] = size to it }
        val t = memphisSeconds(frame)
        shapes.forEach { drawMemphis(it, alpha, unit, t = t) }
    }
}
