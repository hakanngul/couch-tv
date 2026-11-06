package app.couchtv.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * The remote's gear (Settings) button. On some TVs (seen on an ONVO / MediaTek Google TV) that
 * button never reaches any app: Android's global-key table hands it to the maker's hot-key agent or
 * to the Google TV home, and with the Google TV home switched off nothing happens. An
 * accessibility service sees keys before that hand-off, so this one catches only the gear button
 * and opens couch-tv's own gear menu (Home settings or TV settings). Every other key is passed on
 * untouched, and it reads no screen content.
 *
 * Off until a computer switches it on over ADB (settings put secure enabled_accessibility_services).
 * The undo takes it away again (couch-tv-backup/UNDO.md).
 */
class KeyWatch : AccessibilityService() {
    override fun onAccessibilityEvent(e: AccessibilityEvent) {}
    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (isNetflix(event)) return netflixKey(event)
        if (!isGear(event)) return false   // not ours: Android gets it as usual
        Log.i("couchtv-keywatch", "gear keyCode=${event.keyCode} scan=${event.scanCode} action=${event.action}")
        if (event.action == KeyEvent.ACTION_UP && event.repeatCount == 0) {
            runCatching {
                startActivity(Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra("gear", true))
            }
        }
        return true   // both the press and the release, so the old (dead) route never sees it
    }

    /** The remote's Netflix button (KEYCODE_BUTTON_4, handed to MediaTek's Netflix agent): opens
     *  the household's IPTV player instead. If that app is not installed, the key goes on as before. */
    private fun netflixKey(event: KeyEvent): Boolean {
        val launch = iptvLaunch()
        Log.i("couchtv-keywatch", "netflix key -> ${launch?.component?.flattenToShortString() ?: "no IPTV app, passed on"} action=${event.action}")
        if (launch == null) return false
        if (event.action == KeyEvent.ACTION_UP && event.repeatCount == 0) {
            runCatching { startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        return true
    }

    /** The household's IPTV player (OpenTVPlayer, also shipped as AURIQ under other application
     *  ids): the TV app whose main class lives in org.opentvplayer.tv, whatever its package name. */
    private fun iptvLaunch(): Intent? {
        val tvApps = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
        val hit = tvApps.firstOrNull { it.activityInfo.name.startsWith(IPTV_CLASS_PREFIX) }
            ?: tvApps.firstOrNull { it.activityInfo.packageName in IPTV_PACKAGES }
            ?: return null
        return Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .setClassName(hit.activityInfo.packageName, hit.activityInfo.name)
    }

    companion object {
        /** What the remote's Netflix button opens on this TV: the IPTV player (OpenTVPlayer / AURIQ). */
        const val IPTV_CLASS_PREFIX = "org.opentvplayer.tv."
        val IPTV_PACKAGES = setOf("com.lamputsoftware.auriq", "org.opentvplayer.tv")

        fun isNetflix(e: KeyEvent): Boolean = e.keyCode == KeyEvent.KEYCODE_BUTTON_4

        /** This ONVO remote's gear: KEYCODE_BUTTON_14 (201), raw Linux KEY_TAPE (0x180 = 384), which
         *  the global-key table hands to MediaTek's hot-key agent. 176 is Android's own Settings key,
         *  kept for remotes that send it. */
        const val GEAR_SCAN = 384

        fun isGear(e: KeyEvent): Boolean = e.keyCode == KeyEvent.KEYCODE_BUTTON_14 ||
            e.scanCode == GEAR_SCAN || e.keyCode == KeyEvent.KEYCODE_SETTINGS
    }
}
