package nl.thuis.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.IntentFilter
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import nl.thuis.launcher.data.ChannelArtRepository
import nl.thuis.launcher.data.ChannelItem
import nl.thuis.launcher.data.LikeStore
import nl.thuis.launcher.data.Movie
import nl.thuis.launcher.data.MovieApi
import nl.thuis.launcher.data.MovieDetails
import nl.thuis.launcher.data.PinStore
import nl.thuis.launcher.data.Recommender
import nl.thuis.launcher.ui.AppMenu
import nl.thuis.launcher.ui.HomeScreen
import nl.thuis.launcher.ui.LikedScreen
import nl.thuis.launcher.ui.MovieDetailScreen
import nl.thuis.launcher.ui.SettingsScreen
import nl.thuis.launcher.data.Secrets
import nl.thuis.launcher.bios.Bios
import nl.thuis.launcher.bios.BiosActivity
import nl.thuis.launcher.bios.PlayerActivity
import nl.thuis.launcher.bios.VodItem
import nl.thuis.launcher.bios.openYouTube
import androidx.compose.runtime.collectAsState
import nl.thuis.launcher.ui.ThuisTheme


class MainActivity : ComponentActivity() {

    private enum class Screen { HOME, LIKED }

    private lateinit var appRepo: AppRepository
    private lateinit var pins: PinStore
    private lateinit var likes: LikeStore
    private lateinit var recommender: Recommender
    private lateinit var channels: ChannelArtRepository
    private lateinit var movieApi: MovieApi

    private var apps by mutableStateOf(emptyList<AppEntry>())
    private var recs by mutableStateOf(emptyList<Movie>())
    private var recMessage by mutableStateOf<String?>(null)
    private var screen by mutableStateOf(Screen.HOME)
    private var menuApp by mutableStateOf<AppEntry?>(null)
    private var detailMovie by mutableStateOf<Movie?>(null)
    private var settingsOpen by mutableStateOf(false)
    private var channelArt by mutableStateOf<Map<String, List<ChannelItem>>>(emptyMap())

    /** Apps alleen opnieuw inlezen na een (de)installatie, niet bij elke druk op Home. */
    private var appsDirty = true

    private var appsJob: Job? = null
    private var recsJob: Job? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            appsDirty = true
            reloadApps()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appRepo = AppRepository(this)
        pins = PinStore(this)
        likes = LikeStore(this)
        movieApi = MovieApi({ Secrets.get(Secrets.Key.TMDB) }, { Secrets.get(Secrets.Key.OMDB) })
        recommender = Recommender(this, movieApi)
        channels = ChannelArtRepository(this)
        recs = recommender.cached()

        ContextCompat.registerReceiver(this, packageReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }, ContextCompat.RECEIVER_NOT_EXPORTED)

        setContent {
            // Op het homescherm doet "terug" niets; in de likes-lijst ga je terug naar home.
            BackHandler(enabled = true) {
                if (screen == Screen.LIKED) {
                    screen = Screen.HOME
                    refreshRecs()
                }
            }

            // Je filmaanbod (Bios): welke aanbevelingen kun je direct kijken?
            val biosState by Bios.state.collectAsState()
            val vodIndex = (biosState as? nl.thuis.launcher.bios.CatalogState.Ready)?.index
            val vodFor: (Movie) -> VodItem? = { m -> vodIndex?.find(null, listOfNotNull(m.title, m.originalTitle), m.year) }

            ThuisTheme {
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        apps = apps,
                        pins = pins.pins,
                        recs = recs,
                        recMessage = recMessage,
                        channelArt = { channelArt },
                        isLiked = likes::isLiked,
                        loadHeroBanner = appRepo::heroBanner,
                        onLaunch = { startActivity(appRepo.launchIntent(it)) },
                        onAppMenu = { menuApp = it },
                        onToggleLike = likes::toggle,
                        onOpenMovie = { detailMovie = it },
                        onOpenLiked = { screen = Screen.LIKED },
                        isAvailable = { vodFor(it) != null },
                        onOpenSettings = { settingsOpen = true },
                    )
                    Screen.LIKED -> LikedScreen(
                        liked = likes.liked,
                        isLiked = likes::isLiked,
                        onToggleLike = likes::toggle,
                        onOpenMovie = { detailMovie = it },
                    )
                }

                if (settingsOpen) {
                    SettingsScreen(
                        onDismiss = { settingsOpen = false },
                        onSaved = ::applySettings,
                    )
                }

                detailMovie?.let { movie ->
                    MovieDetailScreen(
                        movie = movie,
                        loadDetails = { id -> runCatching { movieApi.details(id) }.getOrNull() },
                        liked = likes.isLiked(movie.id),
                        playable = vodFor(movie) != null,
                        resumeMs = vodFor(movie)?.let { Bios.progress(this).entries[it.id]?.positionMs } ?: 0L,
                        biosConfigured = Bios.config != null,
                        onPlay = { m -> vodFor(m)?.let { startActivity(PlayerActivity.intent(this, it)) } },
                        onSearchBios = { m -> startActivity(BiosActivity.search(this, m.title)) },
                        onTrailer = ::openTrailer,
                        onToggleLike = likes::toggle,
                        onDismiss = { detailMovie = null },
                    )
                }

                menuApp?.let { app ->
                    val index = pins.pins.indexOf(app.pinKey)
                    AppMenu(
                        app = app,
                        pinned = index >= 0,
                        canMoveLeft = index > 0,
                        canMoveRight = index in 0 until pins.pins.lastIndex,
                        onTogglePin = { pins.toggle(app.pinKey) },
                        onMove = { pins.move(app.pinKey, it) },
                        onAppInfo = { openAppInfo(app.packageName) },
                        onDismiss = { menuApp = null },
                    )
                }
            }
        }
    }

    /** Home-knop: altijd terug naar het hoofdscherm. (Terugkomen uit YouTube laat het filmscherm open.) */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        screen = Screen.HOME
        menuApp = null
        detailMovie = null
        settingsOpen = false
    }

    override fun onResume() {
        super.onResume()
        if (appsDirty) reloadApps()
        refreshRecs()
        lifecycleScope.launch { channelArt = channels.load() }
        lifecycleScope.launch { Bios.load(this@MainActivity) }
    }

    private fun reloadApps() {
        appsJob?.cancel()
        appsJob = lifecycleScope.launch {
            val loaded = appRepo.loadApps()
            if (loaded != apps) apps = loaded
            appsDirty = false
            if (apps.isNotEmpty()) pins.retainInstalled(apps.map { it.pinKey }.toSet())
        }
    }

    /** Nieuwe sleutels of inlog: meteen opnieuw laden, zonder opnieuw te bouwen. */
    private fun applySettings(changed: Set<Secrets.Key>) {
        movieApi.clearCache()
        if (changed.any { it == Secrets.Key.TMDB || it == Secrets.Key.OMDB }) {
            recMessage = null
            refreshRecs(force = true)
        }
        if (changed.any { it == Secrets.Key.XSERVER || it == Secrets.Key.XUSER || it == Secrets.Key.XPASS }) {
            Bios.reset(this)
            lifecycleScope.launch { Bios.load(this@MainActivity, force = true) }
        }
    }

    /** Haalt nieuwe aanbevelingen op als je likes veranderd zijn of de cache oud is. */
    private fun refreshRecs(force: Boolean = false) {
        if (recsJob?.isActive == true) { if (!force) return; recsJob?.cancel() }
        recsJob = lifecycleScope.launch {
            val result = recommender.load(likes.liked.toList(), likes.signature(), force = force)
            if (result.movies.isNotEmpty()) recs = result.movies
            recMessage = result.message
        }
    }

    /** Speelt de trailer direct af in YouTube; zonder trailer zoekt YouTube er zelf een. */
    private fun openTrailer(movie: Movie, known: MovieDetails?) {
        lifecycleScope.launch {
            val key = known?.trailerKey ?: runCatching { movieApi.details(movie.id).trailerKey }.getOrNull()
            if (!openYouTube(this@MainActivity, key, "${movie.title} ${movie.year} trailer")) {
                Toast.makeText(this@MainActivity, "YouTube kon de trailer niet openen", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isInstalled(pkg: String) =
        runCatching { packageManager.getPackageInfo(pkg, 0) }.isSuccess

    private fun openAppInfo(pkg: String) {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }
}
