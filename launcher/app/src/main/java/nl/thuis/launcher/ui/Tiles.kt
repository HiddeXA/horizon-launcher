package nl.thuis.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import nl.thuis.launcher.AppEntry
import nl.thuis.launcher.data.Movie

private val TileShape = RoundedCornerShape(10.dp)

/**
 * App-tegel in tvOS-stijl: afgeronde 16:9 tegel, vergroot bij focus,
 * en de naam verschijnt alleen onder de tegel die focus heeft.
 */
@Composable
fun AppTile(
    app: AppEntry,
    width: Dp,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            shape = CardDefaults.shape(TileShape),
            scale = CardDefaults.scale(focusedScale = 1.12f),
            border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Palette.Text), shape = TileShape)),
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                },
        ) {
            Box(Modifier.fillMaxSize()) {
                if (app.isBanner) {
                    Image(app.image, app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().background(Palette.Surface), contentAlignment = Alignment.Center) {
                        Image(app.image, app.label, modifier = Modifier.size(width * 0.28f))
                    }
                }
                if (pinned) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(6.dp).size(8.dp)
                            .background(Palette.Accent, CircleShape)
                    )
                }
            }
        }
        Text(
            text = app.label,
            fontSize = 13.sp,
            color = Palette.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp).alpha(if (focused) 1f else 0f),
        )
    }
}

/** Filmposter (2:3). Een hartje rechtsboven als je hem leuk vindt. */
@Composable
fun PosterCard(
    movie: Movie,
    liked: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 112.dp,
    onLongClick: (() -> Unit)? = null,
    available: Boolean = false,
) {
    Card(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = CardDefaults.shape(TileShape),
        scale = CardDefaults.scale(focusedScale = 1.1f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Palette.Text), shape = TileShape)),
        modifier = modifier
            .width(width)
            .aspectRatio(2f / 3f)
            .onFocusChanged { if (it.isFocused) onFocused() },
    ) {
        Box(Modifier.fillMaxSize().background(Palette.Surface)) {
            AsyncImage(
                model = movie.posterUrl,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (available) {
                // Rood label: deze film kun je direct kijken via Bios.
                Box(
                    Modifier.align(Alignment.BottomStart).padding(6.dp)
                        .background(Color(0xFFE50914), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text("▶ Kijk", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (liked) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp)
                        .background(Palette.Background.copy(alpha = 0.75f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("♥", color = Palette.Like, fontSize = 14.sp)
                }
            }
        }
    }
}
