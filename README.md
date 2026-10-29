# MARK LVI Android Companion

The floating JARVIS indicator uses the custom image in `app/src/main/res/drawable-nodpi/jarvis_icon.png` (based on the image supplied by the user), cropped to a circular icon.


Native Android companion source code for the MARK LVI desktop assistant. This is a source project, not a verified or signed APK. Android does not permit unrestricted control of every app/system feature to an ordinary app.

## Features in this source

- Android speech recognition input and Text-to-Speech responses, with automatic Sinhala / English / Singlish response-style selection.
- Optional user-started foreground microphone listener for the wake word **“Jarvis”**. Say “Jarvis, open YouTube” or say “Jarvis” and then speak a command.
- Launch installed apps by visible name.
- Home, Back and Recents through an opt-in Accessibility Service.
- Read recent notification previews after the user enables Notification Access.
- Media volume up/down/mute and flashlight toggle on supported phones.
- Shortcuts to Wi-Fi, Bluetooth, Display and general Settings.
- Open camera, web search and maps/directions.
- Set an alarm or timer through Android's clock app.
- Start calls through the system dialer and compose SMS through the SMS app; user reviews and confirms.
- Optional desktop AI bridge: send command text to MARK LVI's `/api/command` endpoint using an HTTPS URL and already-paired bearer token. Unknown commands are forwarded only if the bridge is configured.

## Build and setup

1. Install Android Studio, Android SDK Platform 35 and JDK 17.
2. Open this `android-companion` directory in Android Studio.
3. Allow Gradle sync and build/run on a phone. This source has not yet been verified by building it in this environment.
4. Grant microphone permission for speech input.
5. To use Jarvis, tap **Start / Stop ‘Jarvis’ wake word listener** in the app. A persistent notification appears while listening; use its **Stop listening** action to stop the microphone service.
6. For screen-off listening, the active foreground microphone service holds a partial CPU wake lock and the app includes a shortcut to Android battery-optimization settings. This can increase battery use. If the phone maker aggressively limits background apps, listening may still pause or stop. Keep the persistent notification visible and start listening while the app is open.
6. Enable **Settings → Accessibility → MARK LVI Phone Actions** for Home/Back/Recents.
7. Enable **Settings → Notification access → MARK LVI Notifications** to read notification previews.
8. Choose **Connect desktop AI (server + token)** and enter the HTTPS base URL plus a bearer token from an already paired session. The token is stored in app-private preferences; clear it from the app when no longer needed.

## Example commands

- `open YouTube`, `launch WhatsApp`
- `home`, `back`, `recent apps`, `read notifications`
- `volume up`, `volume down`, `mute`, `turn on flashlight`
- `Wi-Fi settings`, `Bluetooth settings`, `display settings`
- `open camera`, `search weather in Colombo`, `navigate to Colombo Fort`
- `set alarm 6:30 am`, `set timer 5 minutes`
- `call 0771234567` (opens dialer; user confirms)
- `sms 0771234567 Hello there` (opens SMS app; user reviews and sends)
- Other natural-language requests are sent to desktop MARK LVI only after bridge configuration.

## Important limitations

- The bridge queues command text at the desktop `/api/command` endpoint. This version does not yet stream the desktop AI's response back into the Android app as a spoken reply.
- Use HTTPS and a valid paired token. Self-signed certificates may be rejected; do not disable TLS verification. Do not expose the dashboard to the public internet or share the token.
- Calls/SMS open Android's UI and are not silently placed/sent. Alarm/timer opens the clock app for review.
- Jarvis listening is opt-in and runs as a visible foreground microphone service. While active, it holds a partial CPU wake lock to improve screen-off continuity; this can increase battery drain. It uses Android SpeechRecognizer rather than a dedicated low-power hotword engine, so battery use, language recognition and long-running reliability vary by phone. Use the in-app battery-optimization shortcut only if needed. Start it while the app is visible; it is not guaranteed to resume after force-stop or reboot. Stop it from its persistent notification when not needed.
- Android restricts protected settings and arbitrary interaction with other apps. Accessibility does not mean unrestricted control. Features vary by manufacturer and Android version.


## Jarvis wake-word notes

The listener recognises the word “Jarvis”. You can say “Jarvis, open YouTube” in one phrase, or say “Jarvis” and then speak the next command. The second phrase window expires after about 12 seconds. Recognition depends on the installed Android speech service and selected language; wake-word detection may be less reliable if Sinhala recognition does not recognise the English word “Jarvis”. This is an experimental implementation and must be built/tested on the target phone.


## Jarvis wake-word behaviour

The listener recognises the English wake word “Jarvis”. You can say “Jarvis, open YouTube” in one phrase, or say “Jarvis” and then speak a command within about 12 seconds. Recognition depends on the installed Android speech service; if Sinhala recognition fails to recognise the English word “Jarvis”, select system/English recognition or use the manual voice button. This is an experimental implementation and must be built and tested on the target phone.

## Automatic language matching

The companion now detects whether the latest typed or recognized phrase looks like Sinhala script, English, or Singlish/mixed Sinhala-English. Local phone-action replies are selected to match that detected style, and TTS switches between Sinhala (`si-LK`) and the phone's default language where supported. The UI has an automatic-language setting instead of a fixed Sinhala/system toggle. On Android 14+ the app requests speech-recognizer language detection/switching for Sinhala and English where the installed recognition service supports those extras. `SinhalaLanguage.kt` maps common Sinhala-script commands to phone actions and provides a few Singlish acknowledgements.

The desktop `core/prompt.txt` instructs the desktop model to follow the user's most recent language and naturally mirror Sinhala, English, or mixed Singlish style. Unknown requests are preserved and forwarded to the configured desktop bridge.

Limitations: speech-language detection and Sinhala TTS depend on the Android device's installed recognition/TTS service and downloaded language data. On older Android versions or unsupported recognition services, automatic detection may fall back to the phone's default recognition language. The desktop bridge endpoint still queues commands and returns only an acknowledgement; it does not stream the generated AI answer back into the Android app. Therefore this update improves language matching but does not complete the desktop AI reply-to-phone conversation bridge.

## Experimental speaker identity / owner voice profile

The Android companion now includes `Register my voice as OWNER` and `Identify current speaker` actions. The owner enrollment records a short local sample and stores a normalized acoustic-spectrum profile in app-private preferences. The identify action records a new sample and compares profile similarity, reporting an owner-like match or guest/uncertain result. Raw audio is not stored by this feature.

Important limitations: this is a lightweight acoustic-feature prototype, not a trained speaker-embedding model. It can confuse similar voices and can be affected by microphone, distance, room noise, and speaking style. The similarity threshold is a rough heuristic and has not been calibrated on real devices. Do not use it to unlock the phone, approve payments, or protect sensitive actions. Re-enroll in similar conditions if results are poor. This check is currently a manual identification action; it is not yet run automatically on every speech command or in the Jarvis wake-word service, because Android's standard SpeechRecognizer does not expose its captured audio to this app. Automatic per-command identification needs a unified audio-capture + speech-to-text pipeline and a more robust speaker-embedding model.

## Multi-speaker voice profiles (experimental)

- Register the OWNER voice first using **Register my voice as OWNER**.
- Use **Speak to MARK LVI**. The app captures a short speaker sample before opening Android speech recognition.
- If the sample resembles OWNER or a saved speaker profile, it greets that profile and proceeds to the command.
- If it does not match a saved profile, it asks for the speaker's name and saves a local profile for later best-effort matching.
- **Manage known speakers** lists saved non-owner profiles and lets you remove them.
- Profiles are stored in this app's private SharedPreferences on the phone. Raw audio is not saved by this prototype.

Limitations: this uses simple spectral features, not a trained speaker-embedding model. Accuracy can be poor across different phrases, microphones, rooms, and noise; it may confuse people or fail to recognize the same person. A name is self-reported and not verified. Do not use it to authorize sensitive actions. The always-listening Jarvis wake-word service currently sends recognized text but does not pass the original audio to the speaker matcher, so the automatic name/voice workflow is currently available from the **Speak to MARK LVI** button, not every wake-word command. The Android APK has not been built or device-tested in this environment.

### Floating JARVIS indicator
When the wake-word listener is started, the app can show a small draggable blue `J` bubble while the display is on. It hides automatically on screen-off and reappears on screen-on, and is removed when the listener stops. Android's **Display over other apps** permission is required; the app opens that settings page before starting the listener if permission is missing. This is a visual indicator only, not proof of speaker identity or command security.

## Privacy-first mode

- Privacy mode is ON by default. Phone-local commands (opening apps, volume, flashlight, settings shortcuts, alarm/timer intents, etc.) are handled on the device.
- While Privacy mode is ON, unknown commands are not sent to the optional desktop AI bridge.
- Turning Privacy mode OFF explicitly allows unknown commands to be sent to the configured HTTPS desktop `/api/command` endpoint. The desktop server and any AI provider configured on that computer may process the text; review that setup before enabling it.
- The app declares `allowBackup=false` and cleartext traffic disabled. Optional desktop connection details are stored in app preferences; uninstalling the app clears its app-private data. Do not share the pairing token.
- Android speech recognition may be handled by the device's selected speech-recognition provider and may use network services depending on device/provider settings. This app cannot guarantee that speech recognition is fully offline. Check Android's speech recognition/privacy settings if this matters.
- Optional accessibility and notification permissions are powerful. Enable them only if you need those features. Notification content can include private messages.
- This is a privacy-focused source build, not a security-audited app. Voice identity is experimental and is not secure authentication. Keep your phone locked with Android screen lock.

## GitHub Self-Update (Android)

The app checks the latest **public** GitHub Release at `heshan-athukorala/JARVIS`. A release must include these two assets with these exact names:

- `MARK-LVI-JARVIS.apk`
- `MARK-LVI-JARVIS.apk.sha256` (a `sha256sum`-formatted checksum file)

In the GitHub repository settings, add these **Actions secrets** before publishing a release:

- `KEYSTORE_BASE64`: base64-encoded Android release keystore
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Keep the keystore and passwords private. Never commit them to Git. The same keystore must sign every update, otherwise Android will refuse to update an existing installation. Create the keystore once and back it up securely; losing it prevents in-place updates for this application ID.

The workflow at `.github/workflows/android-build-release.yml` builds a debug APK on pushes/PRs for testing. When a GitHub Release is published with a semantic version tag such as `v1.0.1`, it builds a signed release APK, calculates its SHA-256 checksum, and attaches both files to that Release. Use a **public repository** for the updater's unauthenticated latest-release API; do not embed a personal GitHub token in the APK.

On the phone, use **Check for JARVIS updates** or say “Jarvis, check for updates” / “Jarvis, update yourself”. The app downloads the APK and checksum, verifies SHA-256, then opens Android's package installer. Android may ask the user to allow installs from MARK LVI and always requires the system's installation confirmation. The checksum detects accidental/corrupt downloads; the APK must also be signed with the same trusted release key.

For an initial release, choose a tag newer than the installed app's displayed version (for example `v1.0.1` if the installed version says `1.0`). Confirm the build passes in the GitHub Actions tab before installing it on the phone.

## Minimal animated-orb interface

The main screen uses a black full-screen layout with a rotating blue particle orb and only three controls: menu, microphone, and chat. The orb pulses in idle/listening states and expands/glows while Android Text-to-Speech is speaking. Utility actions (Wi-Fi, Bluetooth, update, privacy, accessibility, and language settings) are kept inside the menu rather than shown as dashboard cards.

Note: the orb animation is a lightweight Canvas-based 3D-inspired particle effect, not a GPU 3D model. Listening animation is state-based; live microphone amplitude is not yet connected to the orb renderer.

## OpenAI API (session-only key)

Open the app menu → More voice & connection settings → Set OpenAI API key. The key is kept in memory only and is cleared when the activity/process is restarted; it is not written into source code or persistent preferences. To send general questions to OpenAI, turn Privacy Mode off explicitly. Requests use the OpenAI Responses API with `gpt-4.1-mini`; API usage may incur charges. This direct-to-OpenAI mobile approach is for personal testing only: OpenAI recommends routing requests through a backend because keys entered into mobile clients can still be exposed on the device.

Build prerequisites: Android Studio, Android SDK platform 35, and a compatible Gradle installation. This environment does not have the Android SDK/Gradle wrapper available, so the APK has not been built or device-tested here.
