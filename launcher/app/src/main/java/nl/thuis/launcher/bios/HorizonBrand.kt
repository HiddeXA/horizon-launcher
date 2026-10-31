package nl.thuis.launcher.bios

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/*
 * Het Horizon-logo: een planeet (cirkel) met een schuine ring eromheen, en het woord "Horizon".
 * Getekend als vector, zodat het op elk formaat scherp is en we elk onderdeel apart kunnen animeren.
 */

private const val WORD = "Horizon"
private const val RING_TILT = -28f            // graden; ring loopt rechtsboven omhoog
const val LOGO_ASPECT = 3.32f                 // breedte / hoogte van het hele logo

/** Extra breedte voor een label achter het woord, zoals "TV" bij Horizon TV. */
const val BADGE_EXTRA = 0.98f
fun logoAspect(badge: String?) = LOGO_ASPECT + if (badge != null) BADGE_EXTRA else 0f

/** Label (bijv. "TV") als hemelsblauw pilletje achter het woord; [left] = linkerkant van het hele logo. */
private fun DrawScope.drawBadge(measurer: TextMeasurer, text: String, left: Float, cy: Float, h: Float, sc: Float = 1f, al: Float = 1f) {
    if (sc <= 0f) return
    val w = h * 0.82f; val bh = h * 0.44f
    val x = left + h * 3.36f
    withTransform({ scale(sc, sc, Offset(x + w / 2f, cy)) }) {
        drawRoundRect(HorizonColors.Accent.copy(alpha = al), Offset(x, cy - bh / 2f), Size(w, bh), androidx.compose.ui.geometry.CornerRadius(bh / 2f))
        val l = measurer.measure(text, TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = (h * 0.27f / density).sp, letterSpacing = 0.5.sp))
        drawText(l, HorizonColors.OnAccent, topLeft = Offset(x + (w - l.size.width) / 2f, cy - l.size.height / 2f), alpha = al)
    }
}

/** Logohoogte op het startscherm; het laadscherm gebruikt dezelfde maat, zodat de overgang naadloos is. */
fun introLogoHeight(width: Float, height: Float) = min(height * 0.24f, width * 0.105f)

/** Hoe ver de horizonlijn (en de laadbalk) onder het midden van het logo ligt, in logohoogtes. */
const val HORIZON_LINE_Y = 0.78f

/** Hoe ver elk onderdeel van het logo is (0 = nog niet te zien, 1 = klaar). */
private class LogoState(
    val planetSweep: Float = 1f,
    val planetScale: Float = 1f,
    val ringSweep: Float = 1f,
    val ringTilt: Float = RING_TILT,
    val ringStretch: Float = 1f,
    val moonT: Float? = null,          // positie van het maantje op de ring (radialen), null = geen maan
    val moonAlpha: Float = 0f,
)

/**
 * Tekent de planeet met ring, gecentreerd op [c] met straal [r].
 * De ring gaat achter de planeet langs (rechtsboven) en ervoor langs (linksonder);
 * waar hij ervoor langs gaat, wordt een smalle spleet uitgespaard, net als in het logo.
 * Vereist een offscreen-laag (BlendMode.Clear).
 */
private fun DrawScope.planet(c: Offset, r: Float, s: LogoState, color: Color, moonColor: Color) {
    val w = r * 0.26f                     // lijndikte
    val a = r * 1.62f * s.ringStretch     // ring: halve lange as
    val b = r * 0.52f                     // ring: halve korte as
    val pr = r * s.planetScale
    val planetDisc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, pr + w * 0.5f)) }

    // Ring als lijn van punten; t loopt van 0..2π, waarbij sin(t) > 0 de voorkant is.
    fun ringPath(from: Float, to: Float): Path {
        val p = Path()
        val steps = 72
        val rad = Math.toRadians(s.ringTilt.toDouble())
        val ct = cos(rad).toFloat(); val st = sin(rad).toFloat()
        for (i in 0..steps) {
            val t = from + (to - from) * i / steps
            val x = a * cos(t); val y = b * sin(t)
            val px = c.x + x * ct - y * st
            val py = c.y + x * st + y * ct
            if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
        }
        return p
    }
    fun ringPoint(t: Float): Offset {
        val rad = Math.toRadians(s.ringTilt.toDouble())
        val ct = cos(rad).toFloat(); val st = sin(rad).toFloat()
        val x = a * cos(t); val y = b * sin(t)
        return Offset(c.x + x * ct - y * st, c.y + x * st + y * ct)
    }

    // De ring "groeit" vanaf linksvoor (t = π) in beide richtingen.
    val sweep = s.ringSweep.coerceIn(0f, 1f) * PI.toFloat()
    val start = PI.toFloat() - sweep
    val end = PI.toFloat() + sweep
    val stroke = Stroke(width = w, cap = StrokeCap.Round)

    // 1. Achterkant van de ring (sin t < 0), niet zichtbaar binnen de planeet.
    if (sweep > 0f) {
        clipPath(planetDisc, ClipOp.Difference) {
            val backFrom = maxOf(start, PI.toFloat()); val backTo = minOf(end, 2 * PI.toFloat())
            if (backTo > backFrom) drawPath(ringPath(backFrom, backTo), color, style = stroke)
            // Wraparound voor het deel voorbij 2π niet nodig: de groei stopt op 2π.
        }
    }
    // Maantje achter de planeet
    s.moonT?.let { t ->
        if (sin(t) < 0f) clipPath(planetDisc, ClipOp.Difference) { drawCircle(moonColor.copy(alpha = s.moonAlpha), w * 0.85f, ringPoint(t)) }
    }

    // 2. De planeet zelf: een cirkel die zich tekent (vanaf rechtsboven, met de klok mee).
    if (s.planetSweep > 0f) {
        drawArc(
            color, startAngle = -60f, sweepAngle = 360f * s.planetSweep.coerceIn(0f, 1f), useCenter = false,
            topLeft = Offset(c.x - pr, c.y - pr), size = Size(pr * 2, pr * 2), style = stroke,
        )
    }

    // 3. Voorkant van de ring (sin t > 0), met een uitgespaarde spleet eronder.
    if (sweep > 0f) {
        val frontFrom = maxOf(start, 0f); val frontTo = minOf(end, PI.toFloat())
        if (frontTo > frontFrom) {
            val front = ringPath(frontFrom, frontTo)
            drawPath(front, Color.Black, style = Stroke(width = w * 2.6f, cap = StrokeCap.Round), blendMode = BlendMode.Clear)
            drawPath(front, color, style = stroke)
        }
    }
    s.moonT?.let { t ->
        if (sin(t) >= 0f) {
            val m = ringPoint(t)
            drawCircle(Color.Black, w * 1.5f, m, blendMode = BlendMode.Clear)
            drawCircle(moonColor.copy(alpha = s.moonAlpha), w * 0.85f, m)
        }
    }
}

private fun wordStyle(sizePx: Float, density: Float) = TextStyle(
    fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = (sizePx / density).sp, letterSpacing = 0.sp,
)

/** Het vaste logo (planeet + "Horizon"), bijvoorbeeld in het bovenmenu. Geef een hoogte mee. */
@Composable
fun HorizonLogo(modifier: Modifier = Modifier, showWord: Boolean = true, badge: String? = null) {
    val measurer = rememberTextMeasurer()
    Canvas(
        modifier
            .aspectRatio(if (showWord) logoAspect(badge) else 1f)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val h = size.height
        // Roze Hoek: 90s-woordmerk met sticker, in hetzelfde vak als het Horizon-logo.
        if (showWord && HorizonColors.retro) {
            drawRetroWordmark(measurer, badge, 0f, h / 2f, h, size.width)
            return@Canvas
        }
        planet(Offset(h * 0.5f, h * 0.5f), h * 0.29f, LogoState(), HorizonColors.Accent, HorizonColors.Mint)
        if (showWord) {
            val layout = measurer.measure(WORD, wordStyle(h * 0.56f, density))
            drawText(layout, HorizonColors.Text, topLeft = Offset(h * 1.08f, (h - layout.size.height) / 2f + h * 0.02f))
            if (badge != null) drawBadge(measurer, badge, 0f, h / 2f, h)
        }
    }
}

// --- Startanimatie ---

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)
private fun phase(t: Float, from: Float, to: Float) = clamp01((t - from) / (to - from))
private fun easeOutCubic(x: Float) = 1f - (1f - x).pow(3)
private fun easeInOutCubic(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
private fun easeOutBack(x: Float, s: Float = 1.9f): Float { val c3 = s + 1f; return 1f + c3 * (x - 1f).pow(3) + s * (x - 1f).pow(2) }
private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private class Star(val x: Float, val y: Float, val r: Float, val base: Float, val speed: Float, val phase: Float, val tint: Int)

private const val INTRO_MS = 3200L
private const val EXIT_MS = 420L

/**
 * Startanimatie, speels maar rustig:
 * sterren twinkelen op, de planeet tekent zich met een veerkrachtige "plop", de ring zwiept erom heen
 * en kantelt op zijn plek, een maantje maakt één rondje, de letters springen één voor één omhoog,
 * een dunne horizonlijn gloeit op en een glans glijdt over het woord. Daarna vervaagt alles naar de app.
 * Elke toets slaat de animatie over (via [skip]).
 */
@Composable
fun HorizonIntro(skip: Boolean, badge: String? = null, onDone: () -> Unit) {
    if (HorizonColors.retro) { RetroIntro(skip, badge, onDone); return }
    val measurer = rememberTextMeasurer()
    val stars = remember {
        val rnd = Random(11)
        List(170) {
            Star(rnd.nextFloat(), rnd.nextFloat(), 0.6f + rnd.nextFloat() * rnd.nextFloat() * 2.2f,
                0.25f + rnd.nextFloat() * 0.6f, 0.6f + rnd.nextFloat() * 2.2f, rnd.nextFloat() * 6.28f, rnd.nextInt(3))
        }
    }
    var time by remember { mutableLongStateOf(0L) }
    var exitAt by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it } - start
            time = now
            val ex = exitAt ?: (INTRO_MS - EXIT_MS).also { if (now >= it) exitAt = it }
            if (now >= ex + EXIT_MS) { onDone(); break }
        }
    }
    LaunchedEffect(skip) { if (skip && exitAt == null) exitAt = time }

    // Ook de achtergrond vervaagt aan het eind, zodat het laadscherm (zelfde logo, zelfde plek) erdoorheen komt.
    Box(Modifier.fillMaxSize().drawBehind {
        val t = time.toFloat()
        val exit = exitAt?.let { easeInOutCubic(phase(t, it.toFloat(), it.toFloat() + EXIT_MS)) } ?: 0f
        drawRect(HorizonColors.Background.copy(alpha = 1f - exit))
    }) {
        // Laag 1: hemel (gloed, sterren, vallende ster)
        Canvas(Modifier.fillMaxSize()) {
            val t = time.toFloat()
            val exit = exitAt?.let { easeInOutCubic(phase(t, it.toFloat(), it.toFloat() + EXIT_MS)) } ?: 0f
            val fade = 1f - exit

            // Achtergrond: zachte gloed rond het midden, die bij het afsluiten iets opzwelt.
            val glow = easeOutCubic(phase(t, 150f, 1400f))
            drawRect(
                Brush.radialGradient(
                    listOf(HorizonColors.Accent.copy(alpha = 0.16f * glow * fade), HorizonColors.Surface.copy(alpha = 0.35f * glow * fade), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.52f), radius = size.width * (0.42f + 0.06f * exit),
                ),
            )

            // Sterren: twinkelen, drijven heel langzaam omhoog.
            val starsIn = phase(t, 0f, 700f)
            stars.forEach { s ->
                val tw = 0.55f + 0.45f * sin(t / 1000f * s.speed + s.phase)
                val col = when (s.tint) { 0 -> HorizonColors.Text; 1 -> HorizonColors.Accent; else -> HorizonColors.Mint }
                val y = (s.y - t / 1000f * 0.004f * s.r + 1f) % 1f
                drawCircle(col.copy(alpha = clamp01(s.base * tw * starsIn * fade)), s.r * density, Offset(s.x * size.width, y * size.height))
            }

            // Vallende ster linksboven naar rechts, één keer.
            val shoot = phase(t, 380f, 1050f)
            if (shoot in 0.001f..0.999f) {
                val p0 = Offset(size.width * 0.62f, size.height * 0.10f)
                val p1 = Offset(size.width * 0.92f, size.height * 0.30f)
                val head = Offset(lerp(p0.x, p1.x, easeOutCubic(shoot)), lerp(p0.y, p1.y, easeOutCubic(shoot)))
                val tail = Offset(lerp(p0.x, p1.x, easeOutCubic(clamp01(shoot - 0.22f))), lerp(p0.y, p1.y, easeOutCubic(clamp01(shoot - 0.22f))))
                val a = sin(shoot * PI.toFloat())
                drawLine(
                    Brush.linearGradient(listOf(Color.Transparent, HorizonColors.Text.copy(alpha = 0.9f * a)), tail, head),
                    tail, head, strokeWidth = 2.2f * density, cap = StrokeCap.Round,
                )
            }

        }
        // Laag 2: het logo, in een eigen laag zodat de uitsparing bij de ring alleen het logo raakt.
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            val t = time.toFloat()
            val exit = exitAt?.let { easeInOutCubic(phase(t, it.toFloat(), it.toFloat() + EXIT_MS)) } ?: 0f
            val fade = 1f - exit

            // Maten van het logo
            val h = introLogoHeight(size.width, size.height)
            val r = h * 0.29f
            val wordLayout = measurer.measure(WORD, wordStyle(h * 0.56f, density))
            val lockupW = h * logoAspect(badge)
            val cy = size.height / 2f

            // De planeet begint in het midden van het scherm en schuift opzij als de letters komen.
            val shift = easeInOutCubic(phase(t, 1150f, 1800f))
            val finalPlanetX = size.width / 2f - lockupW / 2f + h * 0.5f
            val planetX = lerp(size.width / 2f, finalPlanetX, shift)
            val zoom = 1f   // het logo blijft staan: het laadscherm heeft precies hetzelfde logo op dezelfde plek
            val introScale = lerp(2.0f, 1f, shift)   // eerst groot in het midden, dan op logomaat

            val planetP = phase(t, 180f, 950f)
            val ringP = phase(t, 620f, 1350f)
            val moonP = phase(t, 1050f, 2350f)
            val state = LogoState(
                planetSweep = easeInOutCubic(planetP),
                planetScale = lerp(0.35f, 1f, easeOutBack(planetP, 2.4f)),
                ringSweep = easeOutCubic(ringP),
                ringTilt = lerp(-80f, RING_TILT, easeOutBack(phase(t, 620f, 1500f), 2.2f)),
                ringStretch = lerp(0.55f, 1f, easeOutBack(ringP, 1.6f)),
                moonT = if (moonP > 0f) (PI.toFloat() + moonP * 2.2f * PI.toFloat()) else null,
                moonAlpha = clamp01(phase(t, 1050f, 1250f) - phase(t, 2150f, 2450f)) * fade,
            )

            withTransform({ scale(zoom, zoom, Offset(size.width / 2f, cy)) }) {
                withTransform({ scale(introScale, introScale, Offset(planetX, cy)) }) {
                    planet(Offset(planetX, cy), r, state, HorizonColors.Accent.copy(alpha = fade), HorizonColors.Mint)
                }

                // Letters springen één voor één omhoog.
                val wordX = finalPlanetX + h * 0.58f
                val wordY = cy - wordLayout.size.height / 2f + h * 0.02f
                val shine = phase(t, 2050f, 2750f)
                WORD.forEachIndexed { i, ch ->
                    val lp = phase(t, 1350f + i * 75f, 1350f + i * 75f + 520f)
                    if (lp <= 0f) return@forEachIndexed
                    val box = wordLayout.getBoundingBox(i)
                    val letter = charLayout(measurer, ch, h, density)
                    val dy = (1f - easeOutBack(lp, 2.6f)) * h * 0.45f
                    val sc = lerp(0.5f, 1f, easeOutBack(lp, 2.2f))
                    val x = wordX + box.left
                    val brush = shineBrush(shine, x, wordX, wordLayout.size.width.toFloat(), fade)
                    withTransform({ scale(sc, sc, Offset(x + box.width / 2f, wordY + box.height * 0.75f)) }) {
                        drawText(letter, brush, topLeft = Offset(x, wordY + dy), alpha = clamp01(lp * 1.6f) * fade)
                    }
                }

                // Label (bijv. "TV") ploppen erachteraan.
                if (badge != null) {
                    val bp = phase(t, 1900f, 2350f)
                    drawBadge(measurer, badge, finalPlanetX - h * 0.5f, cy, h, sc = easeOutBack(bp, 3f), al = clamp01(bp * 2f) * fade)
                }

                // Horizonlijn onder het logo: groeit vanuit het midden en gloeit even op.
                val lineP = phase(t, 1850f, 2450f)
                if (lineP > 0f) {
                    val lw = lockupW * 1.15f * easeOutCubic(lineP)
                    val la = (0.85f - 0.45f * phase(t, 2450f, 3000f)) * fade
                    val ly = cy + h * HORIZON_LINE_Y
                    drawLine(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, HorizonColors.Mint.copy(alpha = la), HorizonColors.Accent.copy(alpha = la), Color.Transparent),
                            startX = size.width / 2f - lw / 2f, endX = size.width / 2f + lw / 2f,
                        ),
                        Offset(size.width / 2f - lw / 2f, ly), Offset(size.width / 2f + lw / 2f, ly), strokeWidth = 2f * density,
                    )
                }
            }
        }
    }
}

private val charCache = HashMap<String, TextLayoutResult>()

private fun charLayout(measurer: TextMeasurer, ch: Char, h: Float, density: Float): TextLayoutResult =
    charCache.getOrPut("$ch/${h.toInt()}") { measurer.measure(ch.toString(), wordStyle(h * 0.56f, density)) }

/** Off-white tekst met een glans van hemelsblauw die over het woord glijdt. */
private fun shineBrush(p: Float, letterX: Float, wordX: Float, wordW: Float, alpha: Float): Brush {
    if (p <= 0f || p >= 1f) return Brush.linearGradient(listOf(HorizonColors.Text, HorizonColors.Text))
    // Positie van de glans in de coördinaten van deze letter.
    val center = wordX - wordW * 0.3f + (wordW * 1.6f) * easeInOutCubic(p) - letterX
    val band = wordW * 0.22f
    return Brush.horizontalGradient(
        0f to HorizonColors.Text,
        0.5f to Color(0xFFBFF3FF),
        1f to HorizonColors.Text,
        startX = center - band, endX = center + band,
    )
}
