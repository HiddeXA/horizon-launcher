package nl.thuis.launcher.tv

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Roze Hoek (Inspired By Eppie Bos): live-tv met alleen de 18+-zenders uit je Xtream-aanbod.
 *
 * Werkt precies als Horizon TV (zappen, zenderlijst, tv-gids, terugkijken), maar met een eigen
 * zenderlijst, eigen gids-opslag en eigen favorieten. Horizon TV laat deze zenders juist weg.
 *
 * Staat standaard uit (android:enabled="false" in het manifest): dan is er geen tegel en kan de app
 * ook niet gestart worden. Aanzetten gaat via Instellingen, zie [RozeHoek.setEnabled].
 */
class RozeHoekActivity : TvActivity() {
    override val flavor: TvFlavor get() = TvFlavor.ROZE
}

/** Aan/uit van Roze Hoek. De stand wordt door Android zelf bewaard (blijft staan na een update). */
object RozeHoek {
    const val TITLE = "Roze Hoek - Inspired By Eppie Bos"

    private fun component(context: Context) = ComponentName(context, RozeHoekActivity::class.java)

    fun isEnabled(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(component(context)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    /** Zet de tegel aan of uit. De launcher krijgt dit binnen als PACKAGE_CHANGED en ververst zijn lijst. */
    fun setEnabled(context: Context, on: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            component(context),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
            PackageManager.DONT_KILL_APP,
        )
    }
}
