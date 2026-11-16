package nl.nielsnotsmurf.webapp

/**
 * Keeps the screen on during a workout with the Screen Wake Lock API (Safari 16.4+, Chrome).
 * The browser drops the lock when the page is hidden, so it is asked for again when the page
 * comes back. Browsers without the API simply keep their normal screen timeout.
 */
private fun jsKeepAwake(on: Boolean): Unit = js(
    """{
        const w = globalThis.__cableWake || (globalThis.__cableWake = { want: false, lock: null, hooked: false });
        w.want = on;
        const ask = () => {
            if (!w.want || w.lock || !navigator.wakeLock || document.visibilityState !== 'visible') return;
            navigator.wakeLock.request('screen').then((l) => {
                w.lock = l;
                l.addEventListener('release', () => { w.lock = null; });
                if (!w.want) { l.release().catch(() => {}); }
            }).catch(() => {});
        };
        if (!w.hooked) { w.hooked = true; document.addEventListener('visibilitychange', ask); }
        if (on) { ask(); } else if (w.lock) { w.lock.release().catch(() => {}); w.lock = null; }
    }"""
)

internal object ScreenWakeLock {
    fun keep(on: Boolean) {
        runCatching { jsKeepAwake(on) }
    }
}
