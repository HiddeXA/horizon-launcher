package nl.thuis.launcher.bios

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import androidx.media3.ui.R as MR
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.graphics.drawable.StateListDrawable
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import kotlin.math.abs

/**
 * Speler voor films uit je aanbod (Media3 ExoPlayer).
 * - Hervat waar je was en slaat elke 10 s en bij stoppen je positie op.
 * - Ondertitels: kiezen of uitzetten via de ondertitelknop in de balk; je keuze wordt onthouden.
 * - Spoelen: één tik = 30 s vooruit of terug. Ingedrukt houden begint rustig en gaat steeds sneller.
 * - Titel van de film bovenin zolang de bediening in beeld is.
 * - Lukt afspelen niet (bijv. een formaat dat het kastje niet kent), dan biedt hij Kodi aan.
 * - Bij een aflevering: serienaam en S·A bovenin, een knop Volgende aflevering, en in de laatste 10 s
 *   een kaart die aftelt naar de volgende. Er speelt altijd maar één stream: dezelfde speler wisselt
 *   van aflevering.
 */
class PlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView
    private lateinit var seekLabel: TextView
    private var itemId = 0
    // Bij een aflevering van een serie (anders null: dan is het een film).
    private var episode: Episode? = null
    private var seriesItem: SeriesItem? = null
    private var seriesDetail: SeriesDetail? = null
    private var ended = false
    private var nextEpisode: Episode? = null
    private val card = NextCard()
    private var cardSeconds by mutableStateOf<Int?>(null)
    private var cardFocus by mutableIntStateOf(0)
    private lateinit var titleView: TextView
    private var nextButton: TextView? = null

    private val cardTicker = object : Runnable {
        override fun run() {
            updateCard()
            handler.postDelayed(this, 250)
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("bios-player", MODE_PRIVATE) }

    private val saver = object : Runnable {
        override fun run() {
            saveProgress()
            handler.postDelayed(this, 10_000)
        }
    }

    // --- Spoelen ---
    private var pendingTarget: Long? = null
    private var holdStart = 0L
    private var lastRepeat = 0L
    private val commitSeek = Runnable { commit() }
    private val hideLabel = Runnable { seekLabel.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        itemId = intent.getIntExtra(EXTRA_ID, 0)
        val seriesId = intent.getIntExtra(EXTRA_SERIES, 0)
        val item = if (seriesId == 0) Bios.index?.byId?.get(itemId) else null
        if (seriesId != 0) {
            seriesItem = Series.index?.byId?.get(seriesId)
            seriesDetail = Series.cachedDetail(seriesId)
            episode = seriesDetail?.let { EpisodeOrder.find(it.seasons, intent.getIntExtra(EXTRA_EPISODE, 0)) }
        }
        val ep = episode
        val serie = seriesItem
        if (Bios.config == null || (item == null && (ep == null || serie == null))) {
            finish()
            return
        }
        // Naam voor meldingen en het adres van de stream (wordt nooit gelogd).
        val name = if (ep != null) "${serie!!.title} · ${ep.label}" else item!!.title
        val url = if (ep != null) Series.episodeUrl(ep) else Bios.streamUrl(item!!)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        view = PlayerView(this).apply {
            useController = true
            setShowNextButton(false)
            setShowPreviousButton(false)
            setShowSubtitleButton(true) // ondertitels kiezen of uitzetten
            setShowRewindButton(true)
            setShowFastForwardButton(true)
            controllerShowTimeoutMs = 4000
            keepScreenOn = true
        }
        root.addView(view, FrameLayout.LayoutParams(-1, -1))

        // Horizon-huisstijl: Poppins, off-white tekst, hemelsblauwe tijdbalk.
        val poppins = runCatching { androidx.core.content.res.ResourcesCompat.getFont(this, nl.thuis.launcher.R.font.poppins_semibold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
        view.findViewById<androidx.media3.ui.DefaultTimeBar>(MR.id.exo_progress)?.apply {
            setPlayedColor(ACCENT); setScrubberColor(ACCENT); setBufferedColor(0x66E0E1DD); setUnplayedColor(0x33E0E1DD)
        }

        // Titelbalk bovenin, alleen zichtbaar samen met de bediening.
        val title = TextView(this).also { titleView = it }.apply {
            text = if (ep != null) episodeHeading(serie!!.title, ep) else item!!.title + (item.year?.let { "  ($it)" } ?: "")
            setTextColor(TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = poppins
            setPadding(dp(48), dp(28), dp(48), dp(48))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xDD0B132B.toInt(), 0x000B132B))
            visibility = View.GONE
        }
        root.addView(title, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))

        // Spoel-indicator in het midden onderin
        seekLabel = TextView(this).apply {
            setTextColor(TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = poppins
            setPadding(dp(24), dp(10), dp(24), dp(10))
            background = GradientDrawable().apply {
                setColor(0xE61C2541.toInt()); cornerRadius = dp(30).toFloat(); setStroke(dp(1), 0x6648CAE4)
            }
            visibility = View.GONE
        }
        root.addView(seekLabel, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM).apply { bottomMargin = dp(150) })
        root.addView(
            ComposeView(this).apply {
                setContent {
                    val n = nextEpisode
                    val secs = cardSeconds
                    if (n != null && secs != null) HorizonTheme { NextEpisodeCard(n, secs, cardFocus) }
                }
            },
            FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { rightMargin = dp(48); bottomMargin = dp(44) },
        )
        setContentView(root)
        if (ep != null) addNextButton(poppins)

        view.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
            title.visibility = v
        })

        val fromStart = intent.getBooleanExtra(EXTRA_FROM_START, false)
        val start = when {
            fromStart -> 0L
            ep != null -> Series.store(this).resumeAt(ep.id)
            else -> Bios.progress(this).resumeAt(itemId)
        }

        val selector = DefaultTrackSelector(this).apply { setParameters(savedSubtitleChoice(buildUponParameters())) }
        val renderers = DefaultRenderersFactory(this).setEnableDecoderFallback(true)
        player = ExoPlayer.Builder(this, renderers)
            .setTrackSelector(selector)
            .setSeekBackIncrementMs(SEEK_MS)
            .setSeekForwardIncrementMs(SEEK_MS)
            .build().also { p ->
                view.player = p
                p.setMediaItem(
                    MediaItem.Builder()
                        .setUri(Uri.parse(url))
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(name).build())
                        .build(),
                    start,
                )
                p.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        // De aflevering die nu speelt (kan na doorkijken een andere zijn dan bij het openen).
                        val e = episode
                        val serie2 = seriesItem
                        if (e != null && serie2 != null) showError(error, "${serie2.title} · ${e.label}", Series.episodeUrl(e), true)
                        else showError(error, name, url, false)
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
                            if (episode != null) episodeEnded() else {
                                Bios.progress(this@PlayerActivity).remove(itemId)
                                finish()
                            }
                        }
                    }
                    override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) =
                        rememberSubtitleChoice(parameters)
                })
                p.playWhenReady = true
                p.prepare()
            }
        handler.postDelayed(saver, 10_000)
        if (ep != null) {
            nextEpisode = seriesDetail?.let { EpisodeOrder.next(it.seasons, ep.id) }
            nextButton?.visibility = if (nextEpisode != null) View.VISIBLE else View.GONE
            handler.post(cardTicker)
        }
    }

    // --- Afleveringen: doorkijken ---

    /** Knop "Volgende aflevering" in de knoppenrij onderaan, naast Ondertitels (ontwerp bord E). */
    private fun addNextButton(font: Typeface) {
        val row = view.findViewById<ViewGroup>(MR.id.exo_basic_controls) ?: return
        val pill = { color: Int -> GradientDrawable().apply { setColor(color); cornerRadius = dp(22).toFloat() } }
        val b = TextView(this).apply {
            id = View.generateViewId()
            text = "Volgende aflevering"
            setTextColor(android.content.res.ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                intArrayOf(0xFF0B132B.toInt(), TEXT),
            ))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = font
            gravity = Gravity.CENTER
            setPadding(dp(18), 0, dp(18), 0)
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), pill(TEXT))
                addState(intArrayOf(), pill(0xFF26315A.toInt()))
            }
            isFocusable = true
            isClickable = true
            setOnClickListener { playNext() }
        }
        row.addView(b, 0, LinearLayout.LayoutParams(-2, dp(44)).apply { marginEnd = dp(12); gravity = Gravity.CENTER_VERTICAL })
        nextButton = b
    }

    /** Elke 250 ms: hoort de kaart in beeld, en met hoeveel seconden? */
    private fun updateCard() {
        val p = player ?: return
        val dur = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        val now = SystemClock.uptimeMillis()
        val before = cardSeconds
        cardSeconds = card.secondsLeft(p.currentPosition, dur, nextEpisode != null, now)
        if (before == null && cardSeconds != null) {
            cardFocus = 0
            view.hideController()
        }
        // Afgelopen zonder bekende duur: na de aftelling door naar de volgende.
        if (card.expired(now) && !card.dismissed) playNext()
    }

    private fun episodeEnded() {
        markEpisodeWatched()
        val hasNext = nextEpisode != null
        when {
            !hasNext || card.dismissed -> finish() // terug naar het seriescherm, dat al naar de volgende wijst
            cardSeconds != null -> playNext() // aftelling op 0
            else -> { card.endedWithoutDuration(SystemClock.uptimeMillis()); updateCard() }
        }
    }

    /** Naar de volgende aflevering: de huidige telt als gezien, de volgende begint vooraan. */
    private fun playNext() {
        val next = nextEpisode ?: return
        if (!ended) markEpisodeWatched()
        startEpisode(next)
    }

    private fun startEpisode(ep: Episode) {
        val p = player ?: return
        val s = seriesItem ?: return
        flushTaste()
        episode = ep
        ended = false
        lastPos = -1
        card.reset()
        cardSeconds = null
        nextEpisode = seriesDetail?.let { EpisodeOrder.next(it.seasons, ep.id) }
        nextButton?.visibility = if (nextEpisode != null) View.VISIBLE else View.GONE
        titleView.text = episodeHeading(s.title, ep)
        // Eerst de oude stream stoppen: het abonnement staat maar één verbinding toe.
        p.stop()
        p.clearMediaItems()
        p.setMediaItem(
            MediaItem.Builder()
                .setUri(Uri.parse(Series.episodeUrl(ep)))
                .setMediaMetadata(MediaMetadata.Builder().setTitle("${s.title} · ${ep.label}").build())
                .build(),
            0L,
        )
        p.prepare()
        p.playWhenReady = true
        view.showController()
    }

    // --- Ondertitels: keuze onthouden voor de volgende film ---

    private fun savedSubtitleChoice(b: DefaultTrackSelector.Parameters.Builder): DefaultTrackSelector.Parameters {
        return when (val choice = prefs.getString("subs", null)) {
            "off" -> b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
            null -> b.setPreferredTextLanguage("nl").build() // standaard: Nederlands als die er is
            else -> b.setPreferredTextLanguage(choice).build()
        }
    }

    private fun rememberSubtitleChoice(p: TrackSelectionParameters) {
        val choice = when {
            C.TRACK_TYPE_TEXT in p.disabledTrackTypes -> "off"
            else -> p.overrides.values.firstOrNull { it.type == C.TRACK_TYPE_TEXT }
                ?.let { o -> o.trackIndices.firstOrNull()?.let { o.mediaTrackGroup.getFormat(it).language } }
        } ?: return
        prefs.edit().putString("subs", choice).apply()
    }

    // --- Afstandsbediening ---

    /** Zit de focus in de onderste knoppenrij (ondertitels, instellingen)? */
    private fun focusInButtonRow(): Boolean {
        var v: View? = currentFocus
        while (v != null && v !== view) {
            if (v.id == MR.id.exo_basic_controls || v.id == MR.id.exo_extra_controls) return true
            v = v.parent as? View
        }
        return false
    }

    private fun focusButtonRow(): Boolean {
        val ids = listOfNotNull(nextButton?.id, MR.id.exo_subtitle, MR.id.exo_settings)
        val target = ids.firstNotNullOfOrNull { id ->
            view.findViewById<View>(id)?.takeIf { it.visibility == View.VISIBLE && it.isFocusable }
        }
        return target?.requestFocus() == true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val p = player ?: return super.dispatchKeyEvent(event)
        val controllerUp = view.isControllerFullyVisible
        val inButtons = controllerUp && focusInButtonRow()
        val onTimeBar = currentFocus?.id == MR.id.exo_progress
        val down = event.action == KeyEvent.ACTION_DOWN

        // Kaart Volgende aflevering in beeld: links/rechts kiest, OK doet, terug = Annuleren.
        if (cardSeconds != null) when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (down) cardFocus = if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else 0
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (event.action == KeyEvent.ACTION_UP) { if (cardFocus == 0) playNext() else cancelCard() }
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (event.action == KeyEvent.ACTION_UP) cancelCard()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> return true
        }

        when (event.keyCode) {
            KeyEvent.KEYCODE_BACK -> if (controllerUp && down) {
                view.hideController(); return true // eerst de bediening weg, pas daarna terug
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> if (down) {
                p.playWhenReady = !p.playWhenReady; view.showController(); return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER ->
                if (!controllerUp || onTimeBar) {
                    if (down) { p.playWhenReady = !p.playWhenReady; view.showController() }
                    return true
                }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                // Links/rechts = spoelen, behalve in de knoppenrij onderaan (daar beweeg je tussen de knoppen).
                if (!inButtons) { scrub(event, event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT); return true }
            }
            // Omlaag: naar ondertitels/instellingen. Omhoog: terug naar het midden (spoelen).
            KeyEvent.KEYCODE_DPAD_DOWN -> if (controllerUp && !inButtons) {
                if (down) focusButtonRow(); return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> if (inButtons) {
                if (down) view.findViewById<View>(MR.id.exo_play_pause)?.requestFocus(); return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                scrub(event, event.keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD); return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * Eén tik = 30 s. Ingedrukt houden: rustig beginnen en steeds sneller,
     * we zoeken pas echt als je loslaat (anders moet de stream bij elke stap opnieuw laden).
     */
    private fun scrub(event: KeyEvent, forward: Boolean) {
        val p = player ?: return
        val dir = if (forward) 1 else -1
        val now = SystemClock.uptimeMillis()
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                handler.removeCallbacks(commitSeek)
                val base = pendingTarget ?: p.currentPosition
                val delta = if (event.repeatCount == 0) {
                    holdStart = now; lastRepeat = now
                    SEEK_MS
                } else {
                    val held = now - holdStart
                    // Film-seconden per echte seconde ingedrukt: 20 → 60 → 180 → 480
                    val rate = when {
                        held < 1500 -> 20_000L
                        held < 3000 -> 60_000L
                        held < 5000 -> 180_000L
                        else -> 480_000L
                    }
                    val dt = (now - lastRepeat).coerceIn(10, 200)
                    lastRepeat = now
                    rate * dt / 1000
                }
                val dur = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: Long.MAX_VALUE
                pendingTarget = (base + dir * delta).coerceIn(0, (dur - 5_000).coerceAtLeast(0))
                showSeekLabel(p.currentPosition, pendingTarget!!)
                view.showController()
            }
            KeyEvent.ACTION_UP -> handler.postDelayed(commitSeek, if (now - holdStart > 600) 150 else 450)
        }
    }

    private fun commit() {
        val target = pendingTarget ?: return
        player?.seekTo(target)
        pendingTarget = null
        handler.removeCallbacks(hideLabel)
        handler.postDelayed(hideLabel, 900)
    }

    private fun showSeekLabel(current: Long, target: Long) {
        val diff = target - current
        val arrow = if (diff >= 0) "»" else "«"
        val text = android.text.SpannableString("$arrow  ${timecode(target)}   ${if (diff >= 0) "+" else "−"}${timecode(abs(diff))}")
        text.setSpan(android.text.style.ForegroundColorSpan(ACCENT), 0, 1, 0)
        text.setSpan(android.text.style.ForegroundColorSpan(0xFF9AA3B8.toInt()), text.lastIndexOf(' ') + 1, text.length, 0)
        seekLabel.text = text
        seekLabel.visibility = View.VISIBLE
        handler.removeCallbacks(hideLabel)
    }

    /** Titel bovenin bij een aflevering: de serie groot, daaronder "S2 · A4 · titel". */
    private fun episodeHeading(series: String, ep: Episode): CharSequence {
        val text = android.text.SpannableString("$series\n${ep.label}  ·  ${ep.title}")
        text.setSpan(android.text.style.RelativeSizeSpan(0.62f), series.length + 1, text.length, 0)
        return text
    }

    /** Annuleren: de kaart weg en de aflevering speelt uit. Was hij al afgelopen, dan terug naar het seriescherm. */
    private fun cancelCard() {
        card.dismiss()
        cardSeconds = null
        if (ended) finish()
    }

    /** De aflevering is afgelopen: gezien, en de serie wijst naar de volgende. */
    private fun markEpisodeWatched() {
        val ep = episode ?: return
        val s = seriesItem ?: return
        val d = seriesDetail ?: return
        ended = true
        Series.store(this).savePosition(s, d, ep, 1, 1)
    }

    private fun showError(error: PlaybackException, name: String, url: String, isEpisode: Boolean) {
        val what = if (isEpisode) "aflevering" else "film"
        // Niet vanzelf doorgaan naar een volgende aflevering na een fout.
        handler.removeCallbacks(cardTicker)
        cardSeconds = null
        saveProgress()
        val kodi = packageManager.getLaunchIntentForPackage(KODI) != null
        val msg = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Je aanbieder geeft deze $what nu niet door. Probeer het later opnieuw."
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                "Dit kastje kan het beeld- of geluidsformaat van deze $what niet afspelen."
            else -> "Afspelen lukt niet (${error.errorCodeName})."
        }
        AlertDialog.Builder(this)
            .setTitle(name)
            .setMessage(if (kodi) "$msg\n\nKodi kan meer formaten aan." else msg)
            .apply {
                if (kodi) setPositiveButton("Open in Kodi") { _, _ -> openInKodi(url) }
                setNegativeButton("Terug") { _, _ -> finish() }
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun openInKodi(url: String) {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse(url), "video/*")
                    .setPackage(KODI)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        finish()
    }

    // Hoeveel er echt gekeken is (voor je kijkprofiel): telt alleen gewone voortgang, geen sprongen.
    private var lastPos = -1L
    private var watchedMs = 0L

    private fun countWatched() {
        val pos = player?.currentPosition ?: return
        val d = pos - lastPos
        if (lastPos >= 0 && d in 1..60_000) watchedMs += d
        lastPos = pos
    }

    private fun flushTaste() {
        countWatched()
        if (episode != null) { watchedMs = 0; return }
        val item = Bios.index?.byId?.get(itemId) ?: return
        if (watchedMs >= 3 * 60_000) Bios.recordTaste(this, item, Taste.watchWeight(watchedMs / 60_000.0))
        watchedMs = 0
    }

    private fun saveProgress() {
        countWatched()
        val p = player ?: return
        val dur = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        val ep = episode
        if (ep != null) {
            val s = seriesItem
            val d = seriesDetail
            if (!ended && s != null && d != null) Series.store(this).savePosition(s, d, ep, p.currentPosition, dur)
            return
        }
        Bios.progress(this).save(itemId, p.currentPosition, dur)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onPause() {
        super.onPause()
        BiosActivity.markActive()
        flushTaste()
        saveProgress()
        player?.pause()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacksAndMessages(null)
        saveProgress()
        player?.release()
        player = null
        if (!isChangingConfigurations) finish()
    }

    companion object {
        private const val TEXT = 0xFFE0E1DD.toInt()
        private const val ACCENT = 0xFF48CAE4.toInt()
        private const val EXTRA_ID = "id"
        private const val EXTRA_FROM_START = "fromStart"
        private const val EXTRA_SERIES = "series"
        private const val EXTRA_EPISODE = "episode"
        private const val KODI = "org.xbmc.kodi"
        private const val SEEK_MS = 30_000L

        fun intent(context: Context, item: VodItem, fromStart: Boolean = false) =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_ID, item.id)
                .putExtra(EXTRA_FROM_START, fromStart)

        /** Een aflevering afspelen. De afleveringen van de serie moeten al zijn opgehaald ([Series.detail]). */
        fun episodeIntent(context: Context, series: SeriesItem, episode: Episode, fromStart: Boolean = false) =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_SERIES, series.id)
                .putExtra(EXTRA_EPISODE, episode.id)
                .putExtra(EXTRA_FROM_START, fromStart)
    }
}
