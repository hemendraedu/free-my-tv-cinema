# Install Free My TV Cinema on your TV

This guide installs **this repository's Cinema edition**: the scenic home screen, Continue Watching, movie/show recommendations, and daily online backgrounds.

It is for **Android TV and Google TV** and has been tested on Android TV 12. Manufacturers can have different developer menus and launcher restrictions. The LG/webOS and Samsung/Tizen code in this repository retains the upstream interface; it does not include this Android Cinema redesign. See the [main README](README.md) for those platforms.

No root is required. You can preview the app before making it your home screen.

## 1. What you need

- A computer and TV connected to the same trusted local network.
- Access to this GitHub repository. If it is private, the owner must give your GitHub account access before cloning.
- Git, JDK **17**, and Android SDK **Platform 34**, **Build-Tools 34.0.0**, and **Platform-Tools** (ADB).
- On Windows, **Git Bash** to run the build commands. The commands below use Bash, including on Windows.
- Optional: your own [TMDB API key or read-access token](https://www.themoviedb.org/settings/api) for recommendations. The launcher and online backgrounds do not need this key.

Install the SDK packages through Android Studio's SDK Manager. If you already use the Android SDK command-line tools, the equivalent command is:

```bash
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
sdkmanager --licenses
```

Set `JAVA_HOME` to your JDK directory and `ANDROID_HOME` to your Android SDK directory. Replace the example paths with your own.

**Windows — Git Bash:**

```bash
export JAVA_HOME="C:/Program Files/Java/jdk-17"
export ANDROID_HOME="C:/Users/YOUR_NAME/AppData/Local/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

**macOS — Bash or Zsh:**

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

**Linux — Bash:**

```bash
export JAVA_HOME="/path/to/your/jdk-17"
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

Verify `java -version`, `javac -version`, and `adb version`. If you use the command-line SDK installer, set these paths before running `sdkmanager`. Official references: [ADB](https://developer.android.com/tools/adb) and [SDK Manager](https://developer.android.com/tools/sdkmanager).

## 2. Prepare the TV

1. Open **Settings → System → About** (some TVs use **Device Preferences → About**).
2. Select **Android TV OS build** or **Build** seven times to enable Developer options.
3. Open **Developer options** and enable **USB debugging** or the manufacturer's network-debugging option.
4. Find the TV's IP address in **Network settings**.

The commands below use `192.168.1.50` as an example. **Replace it with your TV's address.**

```bash
adb connect 192.168.1.50:5555
adb devices
```

Accept **Allow USB debugging?** on the TV. Choose **Always allow from this computer** if it is your trusted computer. The device should appear with status `device`, rather than `unauthorized`.

### If the TV uses Wireless debugging with pairing

Open **Wireless debugging → Pair device with pairing code**. Use the pairing address/port shown there:

```bash
adb pair 192.168.1.50:PAIRING_PORT
```

Enter the pairing code when prompted. Then connect using the **connection port** shown on the main Wireless debugging screen; it can differ from the pairing port:

```bash
adb connect 192.168.1.50:CONNECTION_PORT
adb -s 192.168.1.50:CONNECTION_PORT tcpip 5555
adb connect 192.168.1.50:5555
```

The app's launcher-switching machinery uses port 5555. Some TVs, including certain Chromecast Android 14 versions, close that port after a restart; repeat the connection and `tcpip 5555` steps when necessary. Not every TV supports the same wireless-debugging flow. Keep ADB on your local network; do not forward it through your router to the internet.

## 3. Get the Cinema source and build

```bash
git clone https://github.com/hemendraedu/free-my-tv-cinema.git
cd free-my-tv-cinema
bash build.sh --generic
```

The output is **`FreeMyTV.apk`** in the repository directory. The generic build does not embed personal API keys. You can add your TMDB key privately on the TV later.

Use this repository's source to obtain the Cinema features. An APK downloaded from the original upstream developer's releases contains the upstream interface instead.

### Optional: build with your location and configuration

For a fresh checkout, copy the example once. Do not overwrite an existing personal configuration:

```bash
cp config.example.properties config.properties
```

Edit `config.properties` in a text editor and save as **UTF-8**. For example, for New York:

```properties
city=New York
latitude=40.7128
longitude=-74.0060
tmdb_region=US
temperature=F
show_crypto=false
tmdb_key=
```

Then build with:

```bash
bash build.sh
```

Leave `tmdb_key` blank to enter it on the TV, or put your own key in this local file. A key compiled into an APK can be extracted from that APK, so use `--generic` for builds you share. `config.properties` and the generated **`freemytv.keystore`** are ignored by Git. Keep both private, and back up the keystore: future in-place updates must use the same signing key.

## 4. Install and preview

From the repository directory:

```bash
adb -s 192.168.1.50:5555 install -r FreeMyTV.apk
adb -s 192.168.1.50:5555 shell am start -n io.freemytv/.HomeActivity
```

This previews Cinema without selecting it as the default home screen. Pressing the remote's Home button can still return to the original launcher.

If installation waits, check the TV for an installation or Play Protect prompt. Install only an APK you built or trust. If Android reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, see the signing-key instructions below rather than immediately uninstalling the current launcher.

## 5. Enable the features

### Movie and show suggestions

Open **Settings → Home Settings → TMDB key** in Free My TV. Use the QR code to enter your own key privately from your phone, or enter it on the TV. Select your streaming services under **Picks** if needed. Movies and shows get separate rows; availability depends on your region and installed services.

The optional Watchmode key improves title-specific links for recommendations. Without an available title link, a recommendation may open the streaming app rather than a particular title.

### Continue Watching

This feature reads the titles and playback progress that streaming apps share with Android TV. Only enable it with the TV owner's permission.

Choose **Home Settings → Top of the screen → Continue watching** and approve the requested access. If the firmware needs an explicit ADB grant, the owner can run:

```bash
adb -s 192.168.1.50:5555 shell pm grant io.freemytv android.permission.READ_TV_LISTINGS
```

Cinema shows up to **six** recent shared titles, excludes YouTube/SmartTube, and refreshes on return and every minute. Apps that do not publish their viewing history cannot appear. The originating streaming app controls exact playback resume; a missing or unusable deep link falls back to opening that app. Restoring **Original** mode revokes viewing-history access.

Choose **Show artwork** in the Continue Watching row to enable matching TMDB show/movie pictures. This sends the title to TMDB, not account details or playback progress, and requires your configured TMDB key. It is off until approved on each installation. Hold OK on a Continue Watching card to turn it off again. Exact-title matching is used; remakes with the same name may be ambiguous. Unavailable artwork falls back to the streaming app's picture/banner. These thumbnails are cached only in memory and do not add files to the five-photo offline wallpaper cache.

### Daily backgrounds

No key or additional setup is needed. With internet access, Cinema checks Bing's daily feed for new scenic photographs while home is open. It changes backgrounds every **five minutes** and keeps at most **five downloaded photos** offline, deleting the oldest download first (FIFO). The bundled landscapes are fallback assets inside the APK, separate from the downloaded-photo cache.

Select the background-title button and press **OK** for the next image. **Hold OK** for the photo credits. New scenery depends on the provider's daily selection; downloaded images and bundled fallbacks keep the screen usable without internet.

### Featured trailers with sound (Android Cinema)

The top of Home, Movies and Shows offers up to three featured titles from your current recommendations. Select a numbered title to switch immediately. After about two seconds in the featured area, an available official YouTube trailer autoplays with sound at the TV's current volume. Previews run for at most 90 seconds and finish before the closing section (20 seconds early for videos longer than 45 seconds, otherwise two seconds early). This reduces exposure to end recommendations; YouTube itself does not support disabling all suggestions. Moving down to Apps or Continue Watching, opening another app, or leaving home stops playback.

Use **Trailers: sound on • Pause** below the featured choices to turn autoplay off; **Trailers paused • Play** turns it back on. The choice survives restarts. No additional YouTube API key is needed: trailer IDs come from TMDB and playback uses the official embedded YouTube player. This requires an up-to-date Android System WebView and internet access to TMDB and YouTube. Missing, blocked or unavailable trailers fall back to title artwork and the next featured choice. The larger 16:9 cinema panel uses YouTube's supported controls-free embed mode, with the launcher's Pause / Play control beside it and a source/status label below it. Artwork stays visible until the playback clock advances, then fades into playback. A player that never starts or stops making progress is destroyed and falls back to artwork. Selectable captions are disabled on ready/play and caption-module changes where supported; text burned into the source video cannot be removed. Known incompatible video candidates are skipped, and a different TMDB candidate can be selected when available. YouTube branding and any provider-served advertising remain. These are YouTube-hosted trailers; the Open button launches the streaming app separately.

## 6. Make Cinema your home screen

When you are happy with the preview:

1. Open **Free My TV → Settings → Home Screen → Free My TV**. Opening the Free My TV app icon may take you directly to its settings.
2. Accept the TV's new USB-debugging prompt for **Free My TV itself** and select **Always allow**. This is separate from authorizing your computer.
3. Wait for the switch to finish, then press the remote's **Home** button to verify it.

On Android 8–10, disconnect the computer before accepting the app's debugging prompt if the prompt is being assigned to the wrong connection:

```bash
adb disconnect 192.168.1.50:5555
```

Use the app's own switch and restoration controls. Do not manually uninstall or disable system launcher/setup packages. Launcher replacement can also disable supported promotional components; it does not remove advertisements inside streaming apps.

## 7. Update an existing Cinema installation

Use the same checkout or restore its original `freemytv.keystore` first. With a clean working tree:

```bash
git pull --ff-only
bash build.sh --generic
adb connect 192.168.1.50:5555
adb -s 192.168.1.50:5555 install -r FreeMyTV.apk
adb -s 192.168.1.50:5555 shell am start -n io.freemytv/.HomeActivity
```

Use `bash build.sh` instead of `--generic` if you deliberately use local `config.properties`. An in-place update preserves the TV's app settings. Rebuild from this fork for Cinema updates; upstream releases are not a replacement for this custom build.

For contributor validation before pushing, run `bash tools/check.sh`. In addition to Android build tools, the full checks need Python 3 and Node/npm. `bash tools/check.sh android` runs only the Android build and its remote, ordering, and wallpaper FIFO tests.

## 8. Restore the original home screen or remove the app

Open **Free My TV → Settings → Home Screen → Original**. Wait for restoration to complete and press **Home** to confirm the original launcher works. If needed, reopen settings through ADB:

```bash
adb -s 192.168.1.50:5555 shell am start -n io.freemytv/.SettingsActivity
```

Only **after restoring and verifying Original mode**, optionally remove the app:

```bash
adb -s 192.168.1.50:5555 uninstall io.freemytv
```

Uninstalling deletes Free My TV's settings and cached photos. If you lost the signing key or are replacing an upstream APK with a differently signed Cinema APK, restore Original first, then uninstall the old APK and install the new one. Keep the original launcher working throughout the transition.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| GitHub says repository not found | Sign in with a GitHub account that has access to this repository. |
| `Android SDK not found` / `JDK not found` | Check `ANDROID_HOME`, `JAVA_HOME`, the SDK package installation, and your shell's `PATH`. On Windows, run the build in Git Bash. |
| ADB connection refused or timed out | Verify the IP, debugging setting, and same local network. Guest Wi-Fi/client isolation can block device-to-device traffic. Use the pairing flow if your TV requires it. |
| ADB says `unauthorized` | Accept the debugging prompt on the TV and retry `adb devices`. |
| More than one device connected | Keep the explicit `-s IP:PORT` argument shown above. |
| Signature/update incompatible | Reuse the original signing keystore, or restore Original before uninstalling and installing a differently signed APK. |
| Home button still opens Google TV | Previewing is separate from choosing Free My TV under Home Screen. Confirm the app's own debugging authorization and wait for the switch to finish. |
| Continue Watching is empty | Verify permission, then play a title in a supporting streaming app and return. Not all apps share this information. |
| Movie/show rows are empty | Check the TMDB key, internet access, region, and selected/installed streaming services. |
| Background stays on an older photo | Cached photos are expected offline. Failed daily feed refreshes retry after an hour while home is active; only scenic wallpaper-enabled entries are selected. |
| TCL launcher switch never starts | Some TCL firmware blocks background startup. See the manufacturer-specific notes in the [README](README.md) before changing app startup permissions. |

For support, include the TV model, Android version, and exact error message. Do not share API keys, pairing codes, personal configuration files, or signing keystores.
