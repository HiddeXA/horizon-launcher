package nl.thuis.launcher.data

import android.content.Context
import android.content.pm.PackageManager
import android.media.tv.TvContract
import android.util.Log
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Eén programma dat een app zelf op het homescherm wil tonen (Android TV "kanaal"). */
@Immutable
data class ChannelItem(val title: String, val imageUri: String)

/**
 * Leest de kanalen die apps (Netflix, Disney+, YouTube, ...) publiceren voor het Android TV-homescherm.
 * Dat is de Android-tegenhanger van de Apple TV "top shelf": per app verse, eigen beelden.
 *
 * Vereist de permissie READ_EPG_DATA. Die wordt gegeven door het installatiescript
 * (adb shell pm grant). Zonder permissie geeft dit gewoon een lege map terug.
 */
class ChannelArtRepository(private val context: Context) {

    private var cached: Map<String, List<ChannelItem>> = emptyMap()
    private var loadedAt = 0L

    val hasPermission: Boolean
        get() = context.checkSelfPermission(READ_EPG) == PackageManager.PERMISSION_GRANTED

    suspend fun load(force: Boolean = false): Map<String, List<ChannelItem>> {
        if (!force && System.currentTimeMillis() - loadedAt < 15 * 60_000 && cached.isNotEmpty()) return cached
        if (!hasPermission) return emptyMap()
        return withContext(Dispatchers.IO) {
            val map = LinkedHashMap<String, MutableList<ChannelItem>>()
            readInto(map, TvContract.WatchNextPrograms.CONTENT_URI)
            readInto(map, TvContract.PreviewPrograms.CONTENT_URI)
            Log.i(TAG, "Kanaalbeelden: ${map.mapValues { it.value.size }}")
            cached = map.mapValues { it.value.take(10) }
            loadedAt = System.currentTimeMillis()
            cached
        }
    }

    private fun readInto(map: MutableMap<String, MutableList<ChannelItem>>, uri: android.net.Uri) {
        val projection = arrayOf(
            "package_name", "title", "poster_art_uri", "thumbnail_uri", "poster_art_aspect_ratio",
        )
        runCatching {
            // Geen WHERE-clausule: die is niet toegestaan bij deze provider.
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val pkg = c.getString(0) ?: continue
                    val poster = c.getString(2)
                    val thumb = c.getString(3)
                    val ratio = if (c.isNull(4)) -1 else c.getInt(4)
                    // Liggend beeld heeft voorkeur (0=16:9, 1=3:2, 2=4:3); anders thumbnail.
                    val image = when {
                        poster != null && ratio in 0..2 -> poster
                        thumb != null -> thumb
                        else -> poster
                    } ?: continue
                    map.getOrPut(pkg) { mutableListOf() } += ChannelItem(c.getString(1).orEmpty(), image)
                }
            }
        }.onFailure { Log.w(TAG, "Kanalen lezen mislukt voor $uri: ${it.message}") }
    }

    companion object {
        const val READ_EPG = "com.android.providers.tv.permission.READ_EPG_DATA"
        private const val TAG = "ThuisChannels"
    }
}
