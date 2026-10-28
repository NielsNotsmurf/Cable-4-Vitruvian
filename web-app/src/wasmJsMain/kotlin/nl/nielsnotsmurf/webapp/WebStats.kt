package nl.nielsnotsmurf.webapp

/**
 * Anonymous usage counts for the web app, through GoatCounter: no cookies, no IDs, no
 * personal data, nothing from the workouts themselves. Only a handful of named events are
 * counted (app opened, how, on what kind of device and browser, workout finished, trainer
 * connection result). Counts only on the hosted beta, never locally or in previews, and not
 * when the browser asks not to be tracked or the user turned it off in Settings.
 * The totals are on docs/stats.
 */
object WebStats {
    const val SITE = "https://cable-web.goatcounter.com"
    private const val HOST = "nielsnotsmurf.github.io"
    private const val OFF_KEY = "web_stats_off"

    var enabled: Boolean
        get() = LocalStorage[OFF_KEY] == null
        set(value) { if (value) LocalStorage.remove(OFF_KEY) else LocalStorage[OFF_KEY] = "1" }

    fun event(name: String) {
        if (!enabled || jsHost() != HOST || jsDoNotTrack()) return
        jsBeacon("$SITE/count?p=${jsEncode("/web/$name")}&t=${jsEncode(name)}&e=true&rnd=${randomId().take(8)}")
    }

    /** Sent once per page load. */
    fun opened() {
        event(if (isInstalled()) "open/installed" else "open/browser")
        event("device/${jsDeviceClass()}")
        event("browser/${jsBrowser()}")
    }
}

private fun jsHost(): String = js("window.location.hostname")

private fun jsDoNotTrack(): Boolean = js("(navigator.doNotTrack === '1' || window.doNotTrack === '1' || navigator.globalPrivacyControl === true)")

private fun jsEncode(s: String): String = js("encodeURIComponent(s)")

private fun jsBeacon(url: String): Unit = js("{ try { if (!(navigator.sendBeacon && navigator.sendBeacon(url))) fetch(url, { mode: 'no-cors', keepalive: true }); } catch (e) {} }")

private fun jsDeviceClass(): String = js("""
    (() => { const w = Math.min(screen.width, screen.height); return w < 600 ? 'phone' : (w < 1000 || navigator.maxTouchPoints > 1 ? 'tablet' : 'desktop'); })()
""")

private fun jsBrowser(): String = js("""
    (() => {
        const u = navigator.userAgent;
        if (/Bluefy/i.test(u)) return 'bluefy';
        if (/Edg\//.test(u)) return 'edge';
        if (/SamsungBrowser/.test(u)) return 'samsung';
        if (/Firefox|FxiOS/.test(u)) return 'firefox';
        if (/Chrome|CriOS/.test(u)) return 'chrome';
        if (/Safari/.test(u)) return 'safari';
        return 'other';
    })()
""")
