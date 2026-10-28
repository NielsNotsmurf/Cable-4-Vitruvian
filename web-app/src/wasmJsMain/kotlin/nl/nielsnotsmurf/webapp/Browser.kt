package nl.nielsnotsmurf.webapp

import kotlinx.coroutines.await

// The few browser calls the web app makes itself.

private fun jsFetchText(url: String): kotlin.js.Promise<JsString?> =
    js("fetch(url).then(r => r.ok ? r.text() : null).catch(() => null)")

/** The text at [url] (relative to the page), or null when it can't be loaded. */
suspend fun fetchText(url: String): String? = jsFetchText(url).await<JsString?>()?.toString()

private fun jsGetItem(key: String): String? = js("{ try { return window.localStorage.getItem(key); } catch (e) { return null; } }")

private fun jsSetItem(key: String, value: String): Unit = js("{ try { window.localStorage.setItem(key, value); } catch (e) {} }")

/** localStorage, quietly empty when the browser blocks it (private mode). */
object LocalStorage {
    operator fun get(key: String): String? = jsGetItem(key)
    operator fun set(key: String, value: String) = jsSetItem(key, value)
}

fun hideLoadingText(): Unit = js("{ const e = document.getElementById('loading'); if (e) e.remove(); }")

fun randomId(): String = js("(crypto.randomUUID ? crypto.randomUUID() : String(Date.now()) + Math.random().toString(16).slice(2))")
