package nl.thuis.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.thuis.launcher.ui.BrandColors

/**
 * Eén app op het homescherm. @Immutable zodat Compose tegels kan overslaan als er niets veranderde.
 */
@Immutable
data class AppEntry(
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val image: ImageBitmap,
    /** true = echte 16:9 TV-banner, false = vierkant icoon (sideloaded/telefoon-app). */
    val isBanner: Boolean,
    /** Merkkleur (achtergrond) en gloedkleur, eenmalig uitgerekend. */
    val brand: Color,
    val glow: Color,
    val versionKey: Long,
    /**
     * Sleutel voor vastzetten. Normaal de pakketnaam, maar binnen ons eigen pakket (Horizon en
     * Horizon TV zitten in dezelfde app) per scherm, anders gaan ze samen vast of los.
     */
    val pinKey: String = packageName,
)

class AppRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    /** Cache per component: icoon, banner en kleuren worden maar één keer per app-versie gemaakt. */
    private val cache = HashMap<String, AppEntry>()

    /** Grote, scherpe banners voor de top shelf (alleen voor apps die focus hadden). */
    private val heroCache = LruCache<String, ImageBitmap>(8)

    /** Alle startbare apps, gesorteerd, zonder onszelf. Hergebruikt bestaande objecten. */
    suspend fun loadApps(): List<AppEntry> = withContext(Dispatchers.Default) {
        val tv = query(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val phone = query(Intent.CATEGORY_LAUNCHER)

        val result = (tv + phone)
            .distinctBy {
                // Eén tegel per app; alleen binnen ons eigen pakket (launcher + Bios) per scherm.
                if (it.activityInfo.packageName == context.packageName) it.activityInfo.name else it.activityInfo.packageName
            }
            .filter { it.activityInfo.packageName != context.packageName || !it.activityInfo.name.endsWith(".MainActivity") }
            .map { info ->
                val key = "${info.activityInfo.packageName}/${info.activityInfo.name}"
                val version = runCatching {
                    pm.getPackageInfo(info.activityInfo.packageName, 0).lastUpdateTime
                }.getOrDefault(0L)
                synchronized(cache) {
                    cache[key]?.takeIf { it.versionKey == version }
                } ?: toEntry(info, version).also { synchronized(cache) { cache[key] = it } }
            }
            .sortedBy { it.label.lowercase() }

        val alive = result.map { "${it.component.packageName}/${it.component.className}" }.toSet()
        synchronized(cache) { cache.keys.retainAll(alive) }
        result
    }

    /** Banner in hoge resolutie (960x540) voor de grote kaart in de top shelf. */
    suspend fun heroBanner(app: AppEntry): ImageBitmap? {
        if (!app.isBanner) return null
        heroCache.get(app.packageName)?.let { return it }
        return withContext(Dispatchers.Default) {
            runCatching {
                val ai = pm.getActivityInfo(app.component, 0)
                val d = ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm)
                d?.toBitmap(960, 540)?.asImageBitmap()
            }.getOrNull()?.also { heroCache.put(app.packageName, it) }
        }
    }

    fun launchIntent(app: AppEntry): Intent =
        Intent(Intent.ACTION_MAIN)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    private fun query(category: String): List<ResolveInfo> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)

    private fun toEntry(info: ResolveInfo, version: Long): AppEntry {
        val ai = info.activityInfo
        val banner = ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm)
        val bitmap = banner?.toBitmap(320, 180) ?: ai.loadIcon(pm).toBitmap(160, 160)
        val colors = brandColorsOf(bitmap)
        return AppEntry(
            label = info.loadLabel(pm).toString(),
            packageName = ai.packageName,
            component = ComponentName(ai.packageName, ai.name),
            image = bitmap.asImageBitmap(),
            isBanner = banner != null,
            brand = Color(colors.base),
            glow = Color(colors.glow),
            versionKey = version,
            pinKey = if (ai.packageName == context.packageName) "${ai.packageName}/${ai.name}" else ai.packageName,
        )
    }
}

/** Kleuren uitrekenen op een mini-versie (48 px breed): snel genoeg om per app te doen. */
fun brandColorsOf(bitmap: Bitmap): BrandColors = runCatching {
    val src = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
    val w = 48
    val h = (48f * src.height / src.width).toInt().coerceAtLeast(1)
    val small = src.scale(w, h, filter = true)
    val px = IntArray(w * h)
    small.getPixels(px, 0, w, 0, 0, w, h)
    BrandColors.from(px, w, h)
}.getOrDefault(BrandColors.Neutral)
