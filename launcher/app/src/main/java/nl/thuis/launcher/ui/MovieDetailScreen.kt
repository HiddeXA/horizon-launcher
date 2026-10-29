package nl.thuis.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import nl.thuis.launcher.data.Movie
import nl.thuis.launcher.data.MovieDetails

/**
 * Filmscherm: grote achtergrond, alle info en drie knoppen.
 * Een schermvullende Dialog, zodat de focus op het homescherm bewaard blijft:
 * na "terug" sta je weer op dezelfde poster.
 */
@Composable
fun MovieDetailScreen(
    movie: Movie,
    loadDetails: suspend (Int) -> MovieDetails?,
    liked: Boolean,
    playable: Boolean,
    resumeMs: Long,
    biosConfigured: Boolean,
    onPlay: (Movie) -> Unit,
    onSearchBios: (Movie) -> Unit,
    onTrailer: (Movie, MovieDetails?) -> Unit,
    onToggleLike: (Movie) -> Unit,
    onDismiss: () -> Unit,
) {
    val details by produceState<MovieDetails?>(null, movie.id) { value = loadDetails(movie.id) }
    val firstButton = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstButton.requestFocus() } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Palette.Background)) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context).data(movie.backdropUrl ?: movie.posterUrl).size(1280, 720).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopEnd,
                modifier = Modifier.fillMaxSize(),
            )
            // Zelfde voorgebakken overloop als de top shelf: één goedkope laag.
            Image(StageBaker.scrim, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())

            Column(
                Modifier.padding(start = 48.dp, end = 48.dp, bottom = 44.dp).widthIn(max = 560.dp).align(Alignment.BottomStart),
            ) {
                val d = details
                Text(
                    (d?.tagline ?: movie.reason).uppercase(),
                    fontSize = 11.sp, letterSpacing = 1.5.sp, color = Palette.Accent, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    movie.title, fontSize = 40.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 44.sp, modifier = Modifier.padding(top = 4.dp),
                )
                if (d?.originalTitle != null && d.originalTitle != movie.title) {
                    Text(d.originalTitle, fontSize = 14.sp, color = Palette.TextDim)
                }

                // Jaar · duur · genres · scores
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val meta = listOfNotNull(
                        movie.year.takeIf { it.isNotBlank() },
                        d?.runtimeMinutes?.let { "${it / 60}u ${it % 60}m" },
                        d?.genres?.take(3)?.joinToString(", ")?.takeIf { it.isNotBlank() },
                    ).joinToString("  ·  ")
                    Text(meta, fontSize = 14.sp, color = Palette.TextDim)
                    movie.imdbRating?.let { ScoreBadge("IMDb", it, Palette.Imdb, Color.Black) }
                    movie.rtScore?.let { ScoreBadge("RT", it, Palette.Tomato, Color.White) }
                }

                Text(
                    movie.overview.ifBlank { "Geen beschrijving beschikbaar." },
                    fontSize = 15.sp, color = Palette.Text.copy(alpha = 0.85f), lineHeight = 22.sp,
                    maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 14.dp),
                )

                val people = listOfNotNull(
                    d?.director?.let { "Regie: $it" },
                    d?.cast?.takeIf { it.isNotEmpty() }?.let { "Met: ${it.joinToString(", ")}" },
                ).joinToString("   ")
                if (people.isNotBlank()) {
                    Text(people, fontSize = 13.sp, color = Palette.TextDim, maxLines = 2, modifier = Modifier.padding(top = 10.dp))
                }

                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (playable) {
                        DetailButton(
                            if (resumeMs > 0) "▶  Verder kijken" else "▶  Afspelen",
                            primary = true,
                            modifier = Modifier.focusRequester(firstButton),
                        ) { onPlay(movie) }
                    }
                    DetailButton(
                        label = if (d?.trailerKey != null || d == null) "Trailer" else "Zoek trailer",
                        primary = !playable,
                        modifier = if (playable) Modifier else Modifier.focusRequester(firstButton),
                    ) { onTrailer(movie, d) }
                    if (!playable && biosConfigured) {
                        DetailButton("Zoek in Horizon") { onSearchBios(movie) }
                    }
                    DetailButton(if (liked) "♥  Vind ik leuk" else "♡  Vind ik leuk", accent = if (liked) Palette.Like else null) {
                        onToggleLike(movie)
                    }
                }
                if (playable) {
                    Text(
                        "Beschikbaar in je filmaanbod" + if (resumeMs > 0) " · je was bij ${nl.thuis.launcher.bios.timecode(resumeMs)}" else "",
                        fontSize = 13.sp, color = Color(0xFF46D369), modifier = Modifier.padding(top = 12.dp),
                    )
                }
                Text(
                    "Bron: TMDB · scores via OMDb",
                    fontSize = 11.sp, color = Palette.TextDim.copy(alpha = 0.7f), modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun DetailButton(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    accent: Color? = null,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ButtonDefaults.colors(
            containerColor = if (primary) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.10f),
            contentColor = accent ?: Palette.Text,
            focusedContainerColor = Palette.Text,
            focusedContentColor = Palette.Background,
        ),
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ScoreBadge(label: String, value: String, bg: Color, fg: Color) {
    Spacer(Modifier.width(14.dp))
    Box(Modifier.background(bg, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = fg)
    }
    Text(" $value", fontSize = 14.sp, color = Palette.Text)
}
