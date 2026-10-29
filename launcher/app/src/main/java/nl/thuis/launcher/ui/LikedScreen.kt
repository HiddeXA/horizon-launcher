package nl.thuis.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import nl.thuis.launcher.data.Movie

/**
 * Verstopt scherm: films die je leuk vond. Openen via lang OK op de klok.
 * OK op een poster = details, lang OK = (niet meer) leuk. Terug = homescherm.
 */
@Composable
fun LikedScreen(
    liked: List<Movie>,
    isLiked: (Int) -> Boolean,
    onToggleLike: (Movie) -> Unit,
    onOpenMovie: (Movie) -> Unit,
) {
    // Lokale kopie, zodat een poster niet meteen verdwijnt als je hem "ontliket".
    val shown = remember { liked.toList() }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Column(Modifier.fillMaxSize().background(Palette.Background).padding(horizontal = 48.dp, vertical = 32.dp)) {
        Text("Vind ik leuk", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
        Text(
            "${shown.size} films · deze lijst stuurt je aanbevelingen · OK = details · lang OK = (niet meer) leuk",
            fontSize = 13.sp, color = Palette.TextDim, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        if (shown.isEmpty()) {
            Text("Nog niets. Houd OK ingedrukt op een aanbeveling, of kies ♥ op het filmscherm.", color = Palette.TextDim)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(shown, key = { _, m -> m.id }) { i, movie ->
                PosterCard(
                    movie = movie,
                    liked = isLiked(movie.id),
                    onClick = { onOpenMovie(movie) },
                    onLongClick = { onToggleLike(movie) },
                    onFocused = {},
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
        // Verplichte bronvermelding van TMDB.
        Text(
            "Filmdata en posters: TMDB. Dit product gebruikt de TMDB API maar is niet goedgekeurd of gecertificeerd door TMDB. Scores: OMDb.",
            fontSize = 11.sp, color = Palette.TextDim,
        )
    }
}
