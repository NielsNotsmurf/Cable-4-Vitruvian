package nl.nielsnotsmurf.cleanui.ui.components

import android.app.Activity
import android.app.KeyguardManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import nl.nielsnotsmurf.cleanui.sound.clickSounds
import nl.nielsnotsmurf.cleanui.theme.CleanColors

/**
 * Returns an action that asks for the phone's own screen lock (PIN, pattern, fingerprint)
 * and runs [onConfirmed] when it's given. This is how a forgotten account PIN gets reset:
 * whoever can unlock the phone owns the data on it anyway. A phone without a screen lock
 * gets a plain "are you sure" instead.
 */
@Composable
fun rememberScreenLockCheck(title: String, onConfirmed: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val confirmed by rememberUpdatedState(onConfirmed)
    var askPlain by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) confirmed()
    }
    if (askPlain) {
        AlertDialog(
            onDismissRequest = { askPlain = false },
            modifier = Modifier.clickSounds(),
            containerColor = CleanColors.Background,
            title = { Text(title) },
            text = { Text("This phone has no screen lock, so the PIN can be removed without one.") },
            confirmButton = { TextButton(onClick = { askPlain = false; confirmed() }) { Text("Remove PIN", color = CleanColors.Danger) } },
            dismissButton = { TextButton(onClick = { askPlain = false }) { Text("Cancel") } }
        )
    }
    return {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        @Suppress("DEPRECATION")
        val intent = if (keyguard?.isDeviceSecure == true) {
            keyguard.createConfirmDeviceCredentialIntent(title, "Use this phone's screen lock to remove the PIN.")
        } else {
            null
        }
        if (intent != null) launcher.launch(intent) else askPlain = true
    }
}
