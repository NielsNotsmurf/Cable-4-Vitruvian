package nl.nielsnotsmurf.cleanui.data

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun rememberDataFiles(): DataFiles {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<CompletableDeferred<Uri?>>(1) }
    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { pending[0]?.complete(it) }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { pending[0]?.complete(it) }
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

            override suspend fun open(): String? {
                val result = CompletableDeferred<Uri?>().also { pending[0] = it }
                openLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                val uri = result.await() ?: return null
                return withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }.getOrNull()
                }
            }
        }
    }
}
