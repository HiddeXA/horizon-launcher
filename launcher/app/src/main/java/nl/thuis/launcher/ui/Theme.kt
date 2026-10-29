package nl.thuis.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Kleuren: rustig donker, zoals tvOS, met één warme accentkleur. */
object Palette {
    val Background = Color(0xFF0B0D10)
    val Surface = Color(0xFF1C1F24)
    val SurfaceFocused = Color(0xFF2A2E35)
    val Text = Color(0xFFF2F2F2)
    val TextDim = Color(0xFFA7ACB4)
    val Accent = Color(0xFFF2B84B)
    val Like = Color(0xFFFF5A6E)
    val Imdb = Color(0xFFF5C518)
    val Tomato = Color(0xFFFA6A3C)
}

@Composable
fun ThuisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            background = Palette.Background,
            surface = Palette.Surface,
            onBackground = Palette.Text,
            onSurface = Palette.Text,
        ),
        content = content,
    )
}
