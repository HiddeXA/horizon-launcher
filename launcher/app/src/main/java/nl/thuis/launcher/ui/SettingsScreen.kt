package nl.thuis.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import nl.thuis.launcher.data.KeyTester
import nl.thuis.launcher.data.Secrets
import nl.thuis.launcher.data.Secrets.Key
import nl.thuis.launcher.data.SetupServer

/**
 * Instellingen: API-sleutels en IPTV-inlog wijzigen, testen en opslaan op het kastje.
 * Lang typen? Open het getoonde adres op je telefoon of pc en plak het daar.
 */
@Composable
fun SettingsScreen(onDismiss: () -> Unit, onSaved: (Set<Key>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Lege velden = niets wijzigen. Bestaande waarden tonen we gemaskeerd als placeholder.
    val edits = remember { mutableStateMapOf<Key, String>() }
    val results = remember { mutableStateMapOf<Key, KeyTester.Result>() }
    var testing by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    suspend fun testAll() {
        testing = true
        fun cur(k: Key) = edits[k]?.takeIf { it.isNotBlank() } ?: Secrets.get(k)
        results[Key.TMDB] = KeyTester.tmdb(cur(Key.TMDB))
        results[Key.OMDB] = KeyTester.omdb(cur(Key.OMDB))
        val x = KeyTester.xtream(cur(Key.XSERVER), cur(Key.XUSER), cur(Key.XPASS))
        results[Key.XSERVER] = x; results[Key.XUSER] = x; results[Key.XPASS] = x
        testing = false
    }

    fun save(values: Map<Key, String>, from: String) {
        val clean = values.filterValues { it.isNotBlank() }
        if (clean.isEmpty()) { note = "Niets gewijzigd."; return }
        val before = Key.entries.associateWith { Secrets.get(it) }
        Secrets.set(clean)
        val changed = clean.filter { (k, v) -> before[k] != v.trim() }.keys
        edits.clear()
        note = "Opgeslagen$from: ${changed.joinToString(", ") { it.label }.ifEmpty { "niets veranderd" }}."
        if (changed.isNotEmpty()) onSaved(changed)
        scope.launch { testAll() }
    }

    // Webpagina voor telefoon/pc, alleen zolang dit scherm open is.
    val server = remember { SetupServer { values -> save(values, " vanaf je telefoon/pc") } }
    val ip = remember { SetupServer.localIp(context) }
    DisposableEffect(Unit) {
        server.start()
        onDispose { server.stop() }
    }
    LaunchedEffect(Unit) { testAll() }

    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Row(Modifier.fillMaxSize().background(Palette.Background).padding(horizontal = 48.dp, vertical = 32.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text("Instellingen", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
                Text(
                    "Leeg laten = huidige waarde houden. Opgeslagen op dit kastje; gaat voor wat bij het bouwen is ingesteld.",
                    fontSize = 13.sp, color = Palette.TextDim, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsButton("Opslaan", primary = true, modifier = Modifier.focusRequester(first)) { save(edits.toMap(), "") }
                    SettingsButton(if (testing) "Testen…" else "Testen") { scope.launch { testAll() } }
                    SettingsButton("Sluiten") { onDismiss() }
                }
                note?.let { Text(it, fontSize = 14.sp, color = Palette.Accent, modifier = Modifier.padding(top = 10.dp)) }

                Section("Films en scores")
                Field(Key.TMDB, edits, results[Key.TMDB])
                Field(Key.OMDB, edits, results[Key.OMDB])
                Section("Horizon (IPTV, Xtream)")
                Field(Key.XSERVER, edits, null, keyboard = KeyboardType.Uri)
                Field(Key.XUSER, edits, null)
                Field(Key.XPASS, edits, results[Key.XPASS])

            }

            // Rechts: invullen via telefoon of pc
            Column(
                Modifier.padding(start = 36.dp).width(300.dp)
                    .background(Palette.Surface, RoundedCornerShape(14.dp)).padding(20.dp),
            ) {
                Text("Typen via je telefoon of pc", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text)
                Text(
                    "Open dit adres in een browser op een apparaat in hetzelfde wifi-netwerk:",
                    fontSize = 13.sp, color = Palette.TextDim, modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    ip?.let { "http://$it:${server.port}" } ?: "Geen netwerkadres gevonden",
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Accent, modifier = Modifier.padding(top = 10.dp),
                )
                Text("Code", fontSize = 13.sp, color = Palette.TextDim, modifier = Modifier.padding(top = 14.dp))
                Text(server.pin, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp, color = Palette.Text)
                Text(
                    "Werkt alleen zolang dit scherm open is. Bestaande sleutels worden daar nooit getoond.",
                    fontSize = 12.sp, color = Palette.TextDim, modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), fontSize = 11.sp, letterSpacing = 1.5.sp, color = Palette.Accent, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
}

/**
 * Een instelling. Op de tv eerst een gewone knop (zo springt het toetsenbord niet ongevraagd open);
 * pas bij OK wordt het een tekstveld en verschijnt het toetsenbord.
 */
@Composable
private fun Field(
    key: Key,
    edits: MutableMap<Key, String>,
    result: KeyTester.Result?,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    var editing by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    var refocus by remember { mutableStateOf(false) }
    val value = edits[key].orEmpty()
    val input = remember { FocusRequester() }
    val button = remember { FocusRequester() }
    val shape = RoundedCornerShape(8.dp)

    Column(Modifier.padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(key.label, fontSize = 13.sp, color = Palette.TextDim)
            result?.let {
                Text(
                    (if (it.ok) "  ✓ " else "  ✗ ") + it.message, fontSize = 13.sp,
                    color = if (it.ok) Color(0xFF46D369) else Palette.Like,
                )
            }
        }
        val shown = when {
            value.isNotEmpty() && key.secret -> "•".repeat(minOf(value.length, 12)) + value.takeLast(4)
            value.isNotEmpty() -> value
            else -> Secrets.masked(key).ifEmpty { key.hint }
        }
        if (!editing) {
            // Na typen: focus terug op dit veld, zodat je verder kunt met de afstandsbediening.
            LaunchedEffect(refocus) { if (refocus) { runCatching { button.requestFocus() }; refocus = false } }
            Surface(
                onClick = { editing = true },
                shape = ClickableSurfaceDefaults.shape(shape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Palette.Surface, contentColor = if (value.isEmpty()) Palette.TextDim else Palette.Text,
                    focusedContainerColor = Palette.SurfaceFocused, focusedContentColor = Palette.Text,
                ),
                border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Palette.Text), shape = shape)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                modifier = Modifier.padding(top = 4.dp).fillMaxWidth().focusRequester(button),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(shown, fontSize = 16.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    Text("OK = wijzigen", fontSize = 12.sp, color = Palette.TextDim)
                }
            }
        } else {
            LaunchedEffect(Unit) { runCatching { input.requestFocus() } }
            BasicTextField(
                value = value,
                onValueChange = { edits[key] = it },
                singleLine = true,
                textStyle = TextStyle(color = Palette.Text, fontSize = 16.sp),
                cursorBrush = SolidColor(Palette.Accent),
                visualTransformation = if (key == Key.XPASS) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (key == Key.XPASS) KeyboardType.Password else keyboard,
                    imeAction = ImeAction.Done, autoCorrectEnabled = false,
                ),
                keyboardActions = KeyboardActions(onDone = { refocus = true; editing = false }),
                modifier = Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .focusRequester(input)
                    .onFocusChanged {
                        if (it.isFocused) hadFocus = true
                        else if (hadFocus) { hadFocus = false; editing = false }
                    }
                    .background(Palette.Surface, shape)
                    .border(2.dp, Palette.Accent, shape)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text(key.hint, fontSize = 16.sp, color = Palette.TextDim.copy(alpha = 0.6f), maxLines = 1)
                        inner()
                    }
                },
            )
        }
    }
}

@Composable
private fun SettingsButton(label: String, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = ButtonDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ButtonDefaults.colors(
            containerColor = if (primary) Palette.Accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.10f),
            contentColor = Palette.Text,
            focusedContainerColor = Palette.Text,
            focusedContentColor = Palette.Background,
        ),
    ) { Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
}
