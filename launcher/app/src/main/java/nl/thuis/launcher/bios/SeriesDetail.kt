package nl.thuis.launcher.bios

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import nl.thuis.launcher.ui.StageBaker

/**
 * Seriescherm: zelfde opzet als het filmscherm (groot beeld, info, knoppen), met een extra knop
 * Afleveringen. De afspeelknop gaat direct verder waar je was. Een Dialog, zodat je na "terug"
 * op dezelfde tegel staat.
 *
 * [openEpisodes]: meteen het afleveringenscherm tonen (vanuit de launcher).
 */
@Composable
fun SeriesDetailScreen(
    item: SeriesItem,
    onPlay: (episode: Episode, fromStart: Boolean) -> Unit,
    onTrailer: (SeriesDetail?) -> Unit,
    onDismiss: () -> Unit,
    openEpisodes: Boolean = false,
) {
    val context = LocalContext.current
    val store = remember { Series.store(context) }
    var attempt by remember { mutableIntStateOf(0) }
    var result by remember(item.id) { mutableStateOf<Result<SeriesDetail>?>(Series.cachedDetail(item.id)?.let { Result.success(it) }) }
    var episodesOpen by remember(item.id) { mutableStateOf(openEpisodes) }

    LaunchedEffect(item.id, attempt) {
        if (result?.isSuccess != true || attempt > 0) {
            if (attempt > 0) result = null
            result = Series.detail(item, force = attempt > 0)
        }
        // Onthouden dat je deze serie hebt bekeken tot hier (voor het label Nieuwe aflevering).
        result?.getOrNull()?.let { store.refreshSummary(item, it, seen = true) }
    }

    val detail = result?.getOrNull()
    val failed = result?.isFailure == true
    val hasEpisodes = detail != null && detail.episodeCount > 0
    // Leest de posities en vinkjes: verandert er iets (na het kijken), dan klopt de knop meteen weer.
    val next = if (detail != null) NextUp.resolve(detail.seasons, store.episodes, store.lastEpisode(item.id)) else null
    val inMyList = store.inMyList(item.id)

    val play = remember { FocusRequester() }
    val list = remember { FocusRequester() }
    LaunchedEffect(result) {
        delay(60)
        val target = if (result == null || hasEpisodes || failed) play else list
        repeat(5) { if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect; delay(80) }
    }

    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(HorizonColors.Background)) {
            val backdrop = SeriesGenres.meta[item.id]?.backdrop ?: detail?.backdrop
            if (backdrop != null) {
                AsyncImage(
                    ImageRequest.Builder(context).data(backdrop).size(1280, 720).build(), null,
                    contentScale = ContentScale.Crop, alignment = Alignment.TopEnd, modifier = Modifier.fillMaxSize(),
                )
            } else if (item.poster != null) {
                AsyncImage(
                    item.poster, null, contentScale = ContentScale.Crop,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 72.dp).fillMaxWidth(0.26f)
                        .aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
                )
            }
            Image(StageBaker.scrim, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())

            Column(Modifier.align(Alignment.BottomStart).padding(start = 56.dp, bottom = 48.dp, end = 48.dp).widthIn(max = 640.dp)) {
                Text(
                    item.title, fontSize = 42.sp, fontWeight = FontWeight.Black, color = HorizonColors.Text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 46.sp,
                )
                SeriesMeta(item, detail, Modifier.padding(top = 10.dp))
                if (next != null && next.kind == NextUp.Kind.RESUME) {
                    Text(
                        "Je was bij ${timecode(next.resumeMs)} van ${next.episode.label}, ${next.episode.title}",
                        fontSize = 13.sp, color = HorizonColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                val plot = SeriesGenres.meta[item.id]?.overview ?: detail?.plot
                Text(
                    when {
                        plot != null -> plot
                        result == null -> ""
                        else -> "Geen beschrijving beschikbaar."
                    },
                    fontSize = 15.sp, color = HorizonColors.Text.copy(alpha = 0.9f), lineHeight = 22.sp,
                    maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 14.dp),
                )
                detail?.cast?.split(',')?.take(4)?.joinToString(", ") { it.trim() }?.takeIf { it.isNotBlank() }?.let {
                    Text("Met: $it", fontSize = 13.sp, color = HorizonColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
                }
                when {
                    failed -> Text(
                        "Afleveringen konden niet geladen worden. Mijn lijst en Trailer werken wel.",
                        fontSize = 13.sp, color = HorizonColors.Dim, modifier = Modifier.padding(top = 10.dp),
                    )
                    detail != null && !hasEpisodes -> Text(
                        "Nog geen afleveringen beschikbaar", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        color = HorizonColors.Text, modifier = Modifier.padding(top = 10.dp),
                    )
                }

                Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    when {
                        failed -> BiosButton("Opnieuw proberen", primary = true, modifier = Modifier.focusRequester(play)) { attempt++ }
                        result == null -> BiosButton("Afleveringen laden…", primary = true, modifier = Modifier.focusRequester(play)) { }
                        next != null -> {
                            BiosButton("▶  ${next.buttonLabel}", primary = true, modifier = Modifier.focusRequester(play)) {
                                onPlay(next.episode, next.resumeMs <= 0)
                            }
                            BiosButton("Afleveringen") { episodesOpen = true }
                        }
                    }
                    BiosButton(if (inMyList) "✓  Mijn lijst" else "+  Mijn lijst", modifier = Modifier.focusRequester(list)) { store.toggleMyList(item.id) }
                    BiosButton("Trailer") { onTrailer(detail) }
                }
            }
        }
    }

    if (episodesOpen && detail != null && hasEpisodes) {
        EpisodesScreen(
            item = item,
            detail = detail,
            store = store,
            onPlay = { ep -> onPlay(ep, false) },
            onDismiss = { episodesOpen = false },
        )
    }
}
