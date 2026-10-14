# Getting the app onto a phone

Every push to `master` builds a signed APK and publishes it as a GitHub release
(`.github/workflows/release.yml`). Phones install it from there, and Obtainium
keeps it updated.

iOS is not supported: the app is native Android (Kotlin, Jetpack Compose), so it
would need a rewrite, and a free Apple ID only lets a sideloaded app run for 7 days
before it has to be re-installed from a computer.

## One-time setup: the signing key

Android only installs an update when it is signed with the same key as the
installed app, so CI needs one permanent key. Make it once and keep a backup:
if the key is lost, every phone has to uninstall the app (back up data first)
to move to a new key.

1. Create the key (`keytool` ships with Android Studio, in its `jbr/bin` folder):

   ```sh
   keytool -genkeypair -v -keystore phoenix-release.jks -alias phoenix \
     -keyalg RSA -keysize 4096 -validity 36500
   ```

   Pick a password when asked and answer the name questions with anything (or press Enter).

2. Copy it to the clipboard as text:

   ```sh
   base64 -i phoenix-release.jks | pbcopy   # macOS
   ```

   On Windows (PowerShell):
   `[Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\phoenix-release.jks")) | Set-Clipboard`

3. In the repo on GitHub, open Settings > Secrets and variables > Actions and add
   two repository secrets:

   | Name | Value |
   | --- | --- |
   | `SIGNING_KEYSTORE_BASE64` | paste from the clipboard |
   | `SIGNING_STORE_PASSWORD` | the password |

   Only if you used a different alias than `phoenix`, or a separate key password,
   also add `SIGNING_KEY_ALIAS` / `SIGNING_KEY_PASSWORD`.

4. Store `phoenix-release.jks` and the password in a password manager. Never commit it.

5. Run the "Release APK" workflow from the Actions tab (or push to `master`).

## On the phone

**Obtainium (auto-updates).** No account or token is needed:

1. Install Obtainium from https://github.com/ImranR98/Obtainium/releases.
2. Tap Add app, paste `https://github.com/NielsNotsmurf/vitruvian-app` and tap Add.
3. Tap Install. Obtainium checks for new releases on its own.

**Without Obtainium.** Open the repo's Releases page in the phone's browser and
tap the `.apk` of the latest release.

The first install of a CI-signed build over a copy that was built locally fails
with "app not installed" because the signatures differ. Export a backup in the
app, uninstall, install the release, and restore the backup. After that, updates
install over the top.
