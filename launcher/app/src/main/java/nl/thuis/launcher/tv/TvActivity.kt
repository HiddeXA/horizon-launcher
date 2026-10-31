package nl.thuis.launcher.tv

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.thuis.launcher.bios.HorizonColors
import nl.thuis.launcher.bios.HorizonPalette
import nl.thuis.launcher.bios.HorizonTheme

enum class TvMode { WATCH, LIST, GUIDE }

/**
 * Welke live-tv-app dit is. Horizon TV en Roze Hoek delen speler, zenderlijst en gids,
 * maar hebben elk hun eigen zenders, gids-opslag, favorieten en naam.
 */
class TvFlavor(
    val name: String,
    /** Label naast het Horizon-logo (startanimatie, laadscherm, zenderlijst, gids). */
    val badge: String,
    val source: LiveSource,
    val epg: EpgStore,
    val prefsName: String,
    val tag: String,
    /** Kleuren en stijl: Horizon-huisstijl of de 90s-roze stijl van Roze Hoek. */
    val palette: HorizonPalette,
) {
    companion object {
        val TV = TvFlavor("Horizon TV", "TV", LiveRepo.tv, EpgStore.tv, "horizon-tv", "HorizonTV", HorizonPalette.Twilight)
        val ROZE = TvFlavor("Roze Hoek", "18+", LiveRepo.roze, EpgStore.roze, "roze-hoek", "RozeHoek", HorizonPalette.Roze90s)
    }
}

/**
 * Horizon TV: live-tv uit je Xtream-aanbod, in de Horizon-huisstijl.
 *
 * Opent direct op de laatst bekeken zender (de startanimatie speelt terwijl de zender al laadt).
 * Afstandsbediening tijdens kijken:
 *   omhoog/omlaag (of CH+/CH−) = zappen, OK of links = zenderlijst, rechts = tv-gids,
 *   cijfers = zendernummer, terug = info weg / nog een keer om te stoppen.
 * Je abonnement staat één stream tegelijk toe: bij zappen wordt de vorige stream eerst gestopt,
 * en bij het verlaten van de app stopt de stream meteen.
 */
open class TvActivity : ComponentActivity() {

    /** Horizon TV; [RozeHoekActivity] zet dit op Roze Hoek. */
    open val flavor: TvFlavor get() = TvFlavor.TV
    val repo: LiveSource get() = flavor.source
    val epg: EpgStore get() = flavor.epg

    // --- Toestand voor de interface (Compose leest deze direct) ---
    var mode by mutableStateOf(TvMode.WATCH)
    var current by mutableStateOf<LiveChannel?>(null)
    var catchup by mutableStateOf<Programme?>(null)
    var zapList by mutableStateOf<List<LiveChannel>>(emptyList())
    var bannerVisible by mutableStateOf(false)
    var digits by mutableStateOf("")
    var playerError by mutableStateOf<String?>(null)
    var buffering by mutableStateOf(false)
    var introVisible by mutableStateOf(false)
    var introSkip by mutableStateOf(false)
    var settingsOpen by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)
    /** Verhoogt bij elke toetsdruk, zodat de info-balk langer blijft staan. */
    var bannerTick by mutableIntStateOf(0)

    lateinit var prefs: LivePrefs
    lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null

    private var tuneJob: Job? = null
    private var bannerJob: Job? = null
    private var digitsJob: Job? = null
    private var triedHls = false
    private var tuneStartedAt = 0L
    private var lastBack = 0L
    private var previous: LiveChannel? = null
    private var stopped = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HorizonColors.palette = flavor.palette
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs = LivePrefs(this, flavor.prefsName)
        playerView = PlayerView(this).apply {
            useController = false
            setShutterBackgroundColor(Color.BLACK)
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            setKeepContentOnPlayerReset(false)
        }
        buildPlayer()
        showIntro()

        setContent { HorizonTheme { TvApp(this) } }

        lifecycleScope.launch {
            repo.load(this@TvActivity)
            val l = repo.lineup ?: return@launch
            if (current == null) startChannel(l)?.let { tune(it, immediate = true) }
            epg.load(this@TvActivity, l)
        }
        // Na nieuwe inloggegevens (instellingen): opnieuw beginnen.
        lifecycleScope.launch {
            repo.state.collect { s ->
                if (s is LiveState.Ready && current == null && !introVisible) startChannel(s.lineup)?.let { tune(it, immediate = true) }
            }
        }
    }

    /** Laatst bekeken zender, anders de eerste Nederlandse. */
    private fun startChannel(l: LiveLineup): LiveChannel? {
        l.byId[prefs.lastChannel]?.let { return it }
        val cat = l.visibleCategories.firstOrNull { it.region == "NL" && !it.name.contains("PPV", true) } ?: l.visibleCategories.firstOrNull()
        return cat?.let { l.byCategory[it.id]?.firstOrNull() }
    }

    private fun buildPlayer() {
        val extractors = DefaultExtractorsFactory().setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
        )
        val http = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(8_000).setReadTimeoutMs(15_000).setAllowCrossProtocolRedirects(true)
        val sources = DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http), extractors)
        // Kleine buffer voor snel zappen: na 0,8 s beeld al beginnen.
        val load = DefaultLoadControl.Builder().setBufferDurationsMs(2_500, 30_000, 800, 2_000).build()
        player = ExoPlayer.Builder(this, DefaultRenderersFactory(this).setEnableDecoderFallback(true))
            .setMediaSourceFactory(sources)
            .setLoadControl(load)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .build().also { p ->
                p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setPreferredAudioLanguage("nl").build()
                p.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        buffering = state == Player.STATE_BUFFERING
                        if (state == Player.STATE_READY) {
                            playerError = null
                            if (tuneStartedAt > 0) {
                                Log.i(flavor.tag, "Zender in beeld na ${SystemClock.elapsedRealtime() - tuneStartedAt} ms${if (triedHls) " (HLS)" else ""}")
                                tuneStartedAt = 0
                            }
                        }
                    }
                    override fun onPlayerError(error: PlaybackException) = handleError(error)
                    override fun onRenderedFirstFrame() { Log.i(flavor.tag, "Eerste beeld getoond") }
                    override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) { Log.i(flavor.tag, "Beeldformaat ${size.width}x${size.height}") }
                    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                        tracks.groups.forEach { g ->
                            for (i in 0 until g.length) {
                                val f = g.getTrackFormat(i)
                                Log.i(flavor.tag, "Spoor ${g.type}: ${f.sampleMimeType} ${f.codecs ?: ""} ${f.width}x${f.height} gekozen=${g.isTrackSelected(i)} ondersteund=${g.isTrackSupported(i)}")
                            }
                        }
                    }
                })
                p.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
                    override fun onVideoCodecError(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, e: Exception) {
                        Log.w(flavor.tag, "Videodecoder-fout: ${e.javaClass.simpleName}: ${e.message?.take(120)}")
                    }
                    override fun onVideoDecoderInitialized(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, decoderName: String, initializedTimestampMs: Long, initializationDurationMs: Long) {
                        Log.i(flavor.tag, "Videodecoder: $decoderName")
                    }
                    override fun onDroppedVideoFrames(eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime, droppedFrames: Int, elapsedMs: Long) {
                        Log.i(flavor.tag, "Weggevallen beelden: $droppedFrames in $elapsedMs ms")
                    }
                })
                p.volume = if (introVisible) 0f else 1f
                playerView.player = p
            }
    }

    // --- Afspelen ---

    fun tune(ch: LiveChannel, immediate: Boolean = false, list: List<LiveChannel>? = null) {
        if (list != null) zapList = list
        else if (zapList.none { it.id == ch.id }) zapList = repo.lineup?.byCategory?.get(ch.categoryId).orEmpty()
        if (current?.id != ch.id) previous = current
        current = ch
        catchup = null
        playerError = null
        showBanner()
        prefs.watched(ch.id)
        tuneJob?.cancel()
        // Bij snel doorzappen pas starten als je even stilstaat: dan gebruiken we niet voor elke
        // tussenliggende zender de ene verbinding die je abonnement toestaat.
        tuneJob = lifecycleScope.launch {
            if (!immediate) delay(380)
            play(LiveRepo.liveUrl(ch), hls = false)
        }
    }

    fun watchCatchup(ch: LiveChannel, p: Programme) {
        current = ch
        catchup = p
        playerError = null
        mode = TvMode.WATCH
        showBanner()
        tuneJob?.cancel()
        tuneJob = lifecycleScope.launch { play(LiveRepo.catchupUrl(ch, p), hls = false) }
    }

    private fun play(url: String, hls: Boolean) {
        val p = player ?: return
        triedHls = hls
        tuneStartedAt = SystemClock.elapsedRealtime()
        p.stop()
        p.setMediaItem(MediaItem.Builder().setUri(url).setMimeType(if (hls) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP2T).build())
        p.prepare()
        p.playWhenReady = true
    }

    private fun retry() {
        val ch = current ?: return
        catchup?.let { watchCatchup(ch, it) } ?: tune(ch, immediate = true)
    }

    private fun handleError(e: PlaybackException) {
        val ch = current ?: return
        // Eerst nog een keer via HLS proberen; sommige zenders werken alleen zo.
        if (!triedHls) {
            Log.i(flavor.tag, "TS lukt niet (${e.errorCodeName}), HLS proberen")
            val url = catchup?.let { LiveRepo.catchupUrl(ch, it, hls = true) } ?: LiveRepo.liveUrl(ch, hls = true)
            play(url, hls = true)
            return
        }
        Log.w(flavor.tag, "Afspelen mislukt: ${e.errorCodeName}")
        val code = (e.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
        playerError = when {
            code == 404 -> "Deze zender is nu niet beschikbaar."
            code != null && code in 400..599 ->
                "Je aanbieder weigert de stream ($code). Kijkt er al iemand anders met je account? Je abonnement staat één stream tegelijk toe."
            e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Geen verbinding met je aanbieder."
            e.errorCode in 4000..4999 -> "Het kastje kan deze zender niet afspelen."
            else -> "Deze zender speelt nu niet af."
        }
        showBanner(long = true)
    }

    fun zap(delta: Int) {
        val list = zapList.ifEmpty { return }
        val i = list.indexOfFirst { it.id == current?.id }.let { if (it < 0) 0 else it }
        tune(list[(i + delta + list.size) % list.size])
    }

    fun showBanner(long: Boolean = false) {
        bannerVisible = true
        bannerTick++
        bannerJob?.cancel()
        bannerJob = lifecycleScope.launch { delay(if (long) 9_000 else 5_000); bannerVisible = false }
    }

    private fun typeDigit(d: Int) {
        digits = (digits + d).takeLast(4)
        digitsJob?.cancel()
        digitsJob = lifecycleScope.launch {
            delay(1_500)
            val n = digits.toIntOrNull()
            digits = ""
            val ch = n?.let { repo.lineup?.byNum?.get(it) }
            if (ch != null) tune(ch, immediate = true) else showToast("Zender $n bestaat niet")
        }
    }

    fun showToast(text: String) {
        toast = text
        lifecycleScope.launch { delay(2_200); if (toast == text) toast = null }
    }

    // --- Startanimatie ---

    private fun showIntro() {
        introSkip = false
        introVisible = true
        player?.volume = 0f   // de zender laadt al, maar het geluid wacht tot de animatie klaar is
    }

    fun introDone() {
        introVisible = false
        player?.volume = 1f
        if (current != null) showBanner()
    }

    // --- Afstandsbediening ---

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (introVisible) {
            if (event.action == KeyEvent.ACTION_DOWN) introSkip = true
            return true
        }
        if (settingsOpen || mode != TvMode.WATCH) return super.dispatchKeyEvent(event)
        if (repo.lineup == null) return super.dispatchKeyEvent(event)

        val down = event.action == KeyEvent.ACTION_DOWN
        val code = event.keyCode
        if (code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) { if (down) typeDigit(code - KeyEvent.KEYCODE_0); return true }
        when (code) {
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_UP -> { if (down && catchup == null) zap(+1); return true }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_DOWN -> { if (down && catchup == null) zap(-1); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (down) { if (playerError != null) retry() else mode = TvMode.LIST }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (down) { if (catchup != null) seek(-30_000) else mode = TvMode.LIST }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (down) { if (catchup != null) seek(30_000) else mode = TvMode.GUIDE }
                return true
            }
            KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_TV -> { if (down) mode = TvMode.GUIDE; return true }
            KeyEvent.KEYCODE_INFO -> { if (down) { if (bannerVisible) bannerVisible = false else showBanner() }; return true }
            KeyEvent.KEYCODE_LAST_CHANNEL -> { if (down) previous?.let { tune(it, immediate = true) }; return true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (down) player?.let { it.playWhenReady = !it.playWhenReady; showBanner() }
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (!down) return true
                when {
                    digits.isNotEmpty() -> { digits = ""; digitsJob?.cancel() }
                    catchup != null -> current?.let { tune(it, immediate = true) }   // terug naar live
                    bannerVisible -> bannerVisible = false
                    SystemClock.elapsedRealtime() - lastBack < 2_000 -> finish()
                    else -> { lastBack = SystemClock.elapsedRealtime(); showToast("Druk nog een keer op terug om te stoppen") }
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun seek(ms: Long) {
        val p = player ?: return
        if (p.isCurrentMediaItemSeekable) { p.seekTo((p.currentPosition + ms).coerceAtLeast(0)); showBanner() }
        else showToast("Spoelen kan niet bij dit programma")
    }

    fun playbackPosition(): Long = player?.currentPosition ?: 0L
    fun isPaused(): Boolean = player?.playWhenReady == false

    // --- Levenscyclus: bij weggaan meteen de stream stoppen (één verbinding) ---

    override fun onStart() {
        super.onStart()
        // Horizon en Roze Hoek delen het kleurenpalet; bij terugkomen weer het eigen palet zetten.
        HorizonColors.palette = flavor.palette
        if (lastStopped > 0 && SystemClock.elapsedRealtime() - lastStopped > INTRO_AFTER_MS) showIntro()
        if (stopped) {
            stopped = false
            if (introVisible) player?.volume = 0f
            retry()
        }
    }

    override fun onStop() {
        super.onStop()
        lastStopped = SystemClock.elapsedRealtime()
        tuneJob?.cancel()
        player?.stop()
        stopped = current != null
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }

    companion object {
        private const val INTRO_AFTER_MS = 20 * 60 * 1000L
        private var lastStopped = 0L
    }
}
