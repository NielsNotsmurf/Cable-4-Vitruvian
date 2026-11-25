package nl.nielsnotsmurf.cleanui.data

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun rememberDataFiles(): DataFiles {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<CompletableDeferred<Uri?>>(1) }
    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { pending[0]?.complete(it) }
    val app = context.applicationContext
    // Handled in the callback itself, which also runs when the activity was recreated meanwhile.
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        PickedState.scope.launch {
            val text = runCatching { app.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }.getOrNull()
            PickedState.flow.value = PickedFile(text)
        }
    }
    return remember(context, createLauncher, openLauncher) {
        object : DataFiles {
            override suspend fun save(fileName: String, text: String): Boolean {
                val result = CompletableDeferred<Uri?>().also { pending[0] = it }
                createLauncher.launch(fileName)
                val uri = result.await() ?: return false
                return withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                    }.isSuccess
                }
            }

            override fun pick() {
                // Any file: downloaded .json files often carry another type (or none), which would grey them out.
                openLauncher.launch(arrayOf("*/*"))
            }

            override val picked: StateFlow<PickedFile?> get() = PickedState.flow

            override fun clearPicked() { PickedState.flow.value = null }
        }
    }
}

/** Lives as long as the process, so a picked file isn't lost when the activity is recreated. */
private object PickedState {
    val flow = MutableStateFlow<PickedFile?>(null)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
