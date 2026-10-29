package nl.nielsnotsmurf.webapp

import kotlinx.coroutines.await

// The few browser calls the web app makes itself.

private fun jsFetchText(url: String): kotlin.js.Promise<JsString?> =
    js("fetch(url).then(r => r.ok ? r.text() : null).catch(() => null)")

/** The text at [url] (relative to the page), or null when it can't be loaded. */
suspend fun fetchText(url: String): String? = jsFetchText(url).await<JsString?>()?.toString()

private fun jsGetItem(key: String): String? = js("{ try { return window.localStorage.getItem(key); } catch (e) { return null; } }")

private fun jsSetItem(key: String, value: String): Unit = js("{ try { window.localStorage.setItem(key, value); } catch (e) {} }")

private fun jsRemoveItem(key: String): Unit = js("{ try { window.localStorage.removeItem(key); } catch (e) {} }")

/** localStorage, quietly empty when the browser blocks it (private mode). */
object LocalStorage {
    operator fun get(key: String): String? = jsGetItem(key)
    operator fun set(key: String, value: String) = jsSetItem(key, value)
    fun remove(key: String) = jsRemoveItem(key)
}

/** Removes the start text once the first screen is drawn; from then on start errors stay quiet. */
fun hideLoadingText(): Unit = js("{ window.__started = true; const e = document.getElementById('loading'); if (e) e.remove(); }")

fun randomId(): String = js("(crypto.randomUUID ? crypto.randomUUID() : String(Date.now()) + Math.random().toString(16).slice(2))")

private fun jsFetchHex(url: String): kotlin.js.Promise<JsString?> = js("""
    fetch(url).then(r => r.ok ? r.arrayBuffer() : null)
        .then(b => b ? Array.from(new Uint8Array(b), x => x.toString(16).padStart(2, '0')).join('') : null)
        .catch(() => null)
""")

/** The bytes at [url], or null when they can't be loaded. */
suspend fun fetchBytes(url: String): ByteArray? = jsFetchHex(url).await<JsString?>()?.toString()?.let { hex ->
    ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

/** Where the page runs, for the install guide. */
enum class WebPlatform { IOS, ANDROID, DESKTOP }

fun webPlatform(): WebPlatform = when (jsPlatform()) {
    "ios" -> WebPlatform.IOS
    "android" -> WebPlatform.ANDROID
    else -> WebPlatform.DESKTOP
}

// iPadOS reports itself as a Mac; touch points give it away.
private fun jsPlatform(): String = js("""
    (/iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1)) ? 'ios'
        : (/Android/.test(navigator.userAgent) ? 'android' : 'desktop')
""")

/** True when opened from the home screen icon rather than a browser tab. */
fun isInstalled(): Boolean = js("(window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true)")

fun isBluefy(): Boolean = js("/Bluefy/i.test(navigator.userAgent)")

/** Android/desktop Chrome and Edge offer their own install prompt; null elsewhere. */
fun canPromptInstall(): Boolean = js("!!window.__installPrompt")

fun promptInstall(): Unit = js("{ const p = window.__installPrompt; if (p) { window.__installPrompt = null; p.prompt(); } }")

fun pageUrl(): String = js("window.location.href.split('#')[0]")

fun openUrl(url: String): Unit = js("{ window.open(url, '_blank', 'noopener'); }")

private fun jsCopy(text: String): kotlin.js.Promise<JsBoolean> =
    js("(navigator.clipboard ? navigator.clipboard.writeText(text).then(() => true, () => false) : Promise.resolve(false))")

suspend fun copyText(text: String): Boolean = jsCopy(text).await<JsBoolean>().toBoolean()

/** Saves [text] as a file through the browser's download. */
fun downloadText(fileName: String, text: String): Unit = js("""{
    const url = URL.createObjectURL(new Blob([text], { type: 'application/json' }));
    const a = document.createElement('a');
    a.href = url; a.download = fileName; document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}""")

private fun jsPickFile(): kotlin.js.Promise<JsString?> = js("""
    new Promise(resolve => {
        const input = document.createElement('input');
        input.type = 'file'; input.accept = '.json,application/json';
        input.onchange = () => input.files[0] ? input.files[0].text().then(resolve, () => resolve(null)) : resolve(null);
        input.oncancel = () => resolve(null);
        input.click();
    })
""")

/** Lets the user pick a file and returns its text, or null when cancelled. */
suspend fun pickTextFile(): String? = jsPickFile().await<JsString?>()?.toString()

fun reloadPage(): Unit = js("window.location.reload()")
