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
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
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
 */
class PlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView
    private lateinit var seekLabel: TextView
    private var itemId = 0
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
        val item = Bios.index?.byId?.get(itemId)
        if (item == null || Bios.config == null) {
            finish()
            return
        }

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
        val title = TextView(this).apply {
            text = item.title + (item.year?.let { "  ($it)" } ?: "")
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
        setContentView(root)

        view.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
            title.visibility = v
        })

        val fromStart = intent.getBooleanExtra(EXTRA_FROM_START, false)
        val start = if (fromStart) 0L else Bios.progress(this).resumeAt(itemId)

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
                        .setUri(Uri.parse(Bios.streamUrl(item)))
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).build())
                        .build(),
                    start,
                )
                p.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) = showError(error, item)
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
                            Bios.progress(this@PlayerActivity).remove(itemId)
                            finish()
                        }
                    }
                    override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) =
                        rememberSubtitleChoice(parameters)
                })
                p.playWhenReady = true
                p.prepare()
            }
        handler.postDelayed(saver, 10_000)
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
        val target = listOf(MR.id.exo_subtitle, MR.id.exo_settings).firstNotNullOfOrNull { id ->
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

    private fun showError(error: PlaybackException, item: VodItem) {
        saveProgress()
        val kodi = packageManager.getLaunchIntentForPackage(KODI) != null
        val msg = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Je aanbieder geeft deze film nu niet door. Probeer het later opnieuw."
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                "Dit kastje kan het beeld- of geluidsformaat van deze film niet afspelen."
            else -> "Afspelen lukt niet (${error.errorCodeName})."
        }
        AlertDialog.Builder(this)
            .setTitle(item.title)
            .setMessage(if (kodi) "$msg\n\nKodi kan meer formaten aan." else msg)
            .apply {
                if (kodi) setPositiveButton("Open in Kodi") { _, _ -> openInKodi(item) }
                setNegativeButton("Terug") { _, _ -> finish() }
            }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun openInKodi(item: VodItem) {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse(Bios.streamUrl(item)), "video/*")
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
        val item = Bios.index?.byId?.get(itemId) ?: return
        if (watchedMs >= 3 * 60_000) Bios.recordTaste(this, item, Taste.watchWeight(watchedMs / 60_000.0))
        watchedMs = 0
    }

    private fun saveProgress() {
        countWatched()
        val p = player ?: return
        val dur = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L
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
        private const val KODI = "org.xbmc.kodi"
        private const val SEEK_MS = 30_000L

        fun intent(context: Context, item: VodItem, fromStart: Boolean = false) =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_ID, item.id)
                .putExtra(EXTRA_FROM_START, fromStart)
    }
}
