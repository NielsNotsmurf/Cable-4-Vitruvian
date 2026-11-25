package nl.nielsnotsmurf.cleanui.data

import androidx.compose.runtime.Composable
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private fun jsDownload(fileName: String, text: String): Unit = js("""{
    const url = URL.createObjectURL(new Blob([text], { type: 'application/json' }));
    const a = document.createElement('a');
    a.href = url; a.download = fileName; document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}""")

private fun jsPick(): kotlin.js.Promise<JsString?> = js("""
    new Promise(resolve => {
        const input = document.createElement('input');
        input.type = 'file';
        input.onchange = () => input.files[0] ? input.files[0].text().then(resolve, () => resolve(null)) : resolve(null);
        input.oncancel = () => resolve(null);
        input.click();
    })
""")

private object WebDataFiles : DataFiles {
    override suspend fun save(fileName: String, text: String): Boolean {
        jsDownload(fileName, text)
        return true
    }

    private val state = MutableStateFlow<PickedFile?>(null)

    override fun pick() {
        MainScope().launch {
            val text = runCatching { jsPick().await<JsString?>()?.toString() }.getOrNull() ?: return@launch
            state.value = PickedFile(text)
        }
    }

    override val picked: StateFlow<PickedFile?> get() = state

    override fun clearPicked() { state.value = null }
}

@Composable
actual fun rememberDataFiles(): DataFiles = WebDataFiles
