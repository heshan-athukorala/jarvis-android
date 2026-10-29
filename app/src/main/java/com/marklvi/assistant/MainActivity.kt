package com.marklvi.assistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var tts: TextToSpeech
    private lateinit var orb: JarvisOrbView
    private var torchOn = false
    // Kept in memory only; user must re-enter after app restart. Never ship a key in the APK.
    @Volatile private var openAiApiKey: String = ""
    private val speechRequest = 2026
    private var speechRecognizer: SpeechRecognizer? = null
    private val prefs by lazy { getSharedPreferences("mark_lvi", Context.MODE_PRIVATE) }
    private val network = Executors.newSingleThreadExecutor()
    private var wakeReceiverRegistered = false
    private val wakeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val command = intent?.getStringExtra(JarvisWakeWordService.EXTRA_COMMAND).orEmpty()
            if (command.isNotBlank()) runCommand(command)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        buildUi()
        val filter = IntentFilter(JarvisWakeWordService.ACTION_COMMAND)
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(wakeReceiver, filter, Context.RECEIVER_NOT_EXPORTED) else @Suppress("DEPRECATION") registerReceiver(wakeReceiver, filter)
        wakeReceiverRegistered = true
        if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 40)
        }
    }

    override fun onInit(result: Int) {
        if (result == TextToSpeech.SUCCESS) {
            val locale = Locale.getDefault()
            val supported = tts.setLanguage(locale)
            if (supported == TextToSpeech.LANG_MISSING_DATA || supported == TextToSpeech.LANG_NOT_SUPPORTED) tts.language = Locale.getDefault()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { runOnUiThread { if (::orb.isInitialized) orb.setMode(JarvisOrbView.Mode.SPEAKING) } }
                override fun onDone(utteranceId: String?) { runOnUiThread { if (::orb.isInitialized) orb.setMode(JarvisOrbView.Mode.IDLE) } }
                @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { runOnUiThread { if (::orb.isInitialized) orb.setMode(JarvisOrbView.Mode.IDLE) } }
            })
        }
    }

    private fun buildUi() {
        // Minimal full-screen JARVIS interface: black canvas, animated orb, three controls.
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(14))
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        setContentView(root)

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val menu = controlText("☰", 28f) { showJarvisMenu() }
        topBar.addView(menu, LinearLayout.LayoutParams(dp(52), dp(52)))
        topBar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        topBar.addView(controlText("🎙", 22f) { listen() }, LinearLayout.LayoutParams(dp(52), dp(52)))
        topBar.addView(controlText("▢", 25f) { showChatDialog() }, LinearLayout.LayoutParams(dp(52), dp(52)))
        root.addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))

        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        orb = JarvisOrbView(this)
        root.addView(orb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 5f))

        status = TextView(this).apply {
            text = "READY"
            textSize = 11f
            gravity = Gravity.CENTER
            letterSpacing = 0.24f
            setTextColor(0xFF78CFFF.toInt())
            setPadding(dp(8), dp(12), dp(8), dp(8))
        }
        root.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
    }

    private fun controlText(symbol: String, size: Float, action: () -> Unit): TextView = TextView(this).apply {
        text = symbol
        textSize = size
        gravity = Gravity.CENTER
        setTextColor(0xFFD9F1FF.toInt())
        isClickable = true
        isFocusable = true
        contentDescription = when (symbol) { "☰" -> "Open JARVIS menu"; "🎙" -> "Talk to JARVIS"; else -> "Open chat" }
        setOnClickListener { action() }
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.TRANSPARENT)
            cornerRadius = dp(26).toFloat()
        }
    }

    private fun showJarvisMenu() {
        val options = arrayOf(
            "Talk to JARVIS", "Start / Stop wake-word listener", "Wi-Fi settings", "Bluetooth devices",
            "Self Update", "Device controls & commands", "Privacy mode: toggle", "More voice & connection settings",
            "Auto language: Sinhala / English / Singlish", "Enable Home / Back / Recents",
            "Enable notification access", "Background listening / battery settings"
        )
        AlertDialog.Builder(this).setTitle("MARK LVI · JARVIS").setItems(options) { _, which ->
            when (which) {
                0 -> listen()
                1 -> toggleJarvisWakeWord()
                2 -> openSettings(Settings.ACTION_WIFI_SETTINGS, "Opening Wi-Fi settings.")
                3 -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Opening Bluetooth settings.")
                4 -> checkForUpdates(false)
                5 -> showChatDialog()
                6 -> togglePrivacyMode()
                7 -> showMoreSettings()
                8 -> { prefs.edit().putString("voice_language", "auto").apply(); say("Automatic reply language is enabled.") }
                9 -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                10 -> startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                11 -> try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            }
        }.setNegativeButton("Close", null).show()
    }

    private fun showMoreSettings() {
        val options = arrayOf("Register owner voice", "Identify speaker", "Connect desktop AI", "Open PC dashboard", "Phone settings", "Privacy & permissions", "Read notifications", "Set OpenAI API key (session only)")
        AlertDialog.Builder(this).setTitle("JARVIS settings").setItems(options) { _, which ->
            when (which) {
                0 -> enrollOwnerVoice(); 1 -> identifyCurrentSpeaker(); 2 -> bridgeDialog(); 3 -> dashboardDialog()
                4 -> startActivity(Intent(Settings.ACTION_SETTINGS)); 5 -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); 6 -> readNotifications(); 7 -> showOpenAiKeyDialog()
            }
        }.show()
    }

    private fun showChatDialog() {
        val command = EditText(this).apply {
            hint = "Message JARVIS…"
            setSingleLine(false)
            minLines = 1
            maxLines = 4
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(0xFF7890A8.toInt())
        }
        AlertDialog.Builder(this).setTitle("Talk to JARVIS").setView(command)
            .setPositiveButton("Send") { _, _ ->
                val text = command.text.toString().trim()
                if (text.isNotEmpty()) runCommand(text)
            }.setNeutralButton("Speak") { _, _ -> listen() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showOpenAiKeyDialog() {
        val input = EditText(this).apply {
            hint = "sk-… / OpenAI project key"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(0xFF7890A8.toInt())
        }
        AlertDialog.Builder(this)
            .setTitle("OpenAI API key")
            .setMessage("Enter your key on this phone. It stays in memory only and is cleared when JARVIS closes. Do not share it. API usage may incur charges. Turn Privacy Mode OFF only when you want messages sent to OpenAI.")
            .setView(input)
            .setPositiveButton("Save for this session") { _, _ ->
                val key = input.text.toString().trim()
                if (key.startsWith("sk-") && key.length > 20) {
                    openAiApiKey = key
                    say("OpenAI key saved for this session. Turn Privacy Mode off before asking online questions.")
                } else say("That key format does not look right. Please check it and try again.")
            }
            .setNeutralButton("Clear key") { _, _ -> openAiApiKey = ""; say("OpenAI key cleared from memory.") }
            .setNegativeButton("Cancel", null).show()
    }

    private fun askOpenAI(prompt: String) {
        val key = openAiApiKey
        if (key.isBlank()) { say("OpenAI key is not set."); return }
        status.text = "JARVIS is thinking…"
        network.execute {
            var conn: HttpURLConnection? = null
            try {
                val payload = JSONObject().apply {
                    put("model", "gpt-4.1-mini")
                    put("instructions", "You are JARVIS, a helpful mobile voice assistant. Reply concisely. Match the user's language; support Sinhala, English and Singlish. Do not claim to have performed phone actions unless the app confirms it.")
                    put("input", prompt)
                }
                conn = (URL("https://api.openai.com/v1/responses").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 30000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $key")
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream.bufferedReader().use { it.readText() }
                if (code !in 200..299) {
                    val msg = try { JSONObject(body).optJSONObject("error")?.optString("message") ?: "HTTP $code" } catch (_: Exception) { "HTTP $code" }
                    runOnUiThread { say("OpenAI request failed: ${msg.take(220)}") }
                    return@execute
                }
                val json = JSONObject(body)
                val output = json.optJSONArray("output")
                val pieces = mutableListOf<String>()
                if (output != null) for (i in 0 until output.length()) {
                    val content = output.optJSONObject(i)?.optJSONArray("content") ?: continue
                    for (j in 0 until content.length()) {
                        val item = content.optJSONObject(j) ?: continue
                        if (item.optString("type") == "output_text") pieces.add(item.optString("text"))
                    }
                }
                val answer = pieces.joinToString("\n").trim().ifBlank { "I could not find a text answer in the response." }
                runOnUiThread { say(answer) }
            } catch (e: Exception) {
                runOnUiThread { say("Could not connect to OpenAI. Check internet and API billing/settings.") }
            } finally { conn?.disconnect() }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun enrollOwnerVoice() {
        if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 43)
            return
        }
        AlertDialog.Builder(this).setTitle("Register OWNER voice")
            .setMessage("Speak naturally for about 5 seconds in a quiet room. This creates a local experimental voice profile on this phone. It is not secure biometric authentication.")
            .setPositiveButton("Start recording") { _, _ ->
                status.text = "Recording owner voice… speak naturally."
                network.execute {
                    try {
                        val profile = VoiceIdentity.recordAndExtract(5)
                        prefs.edit().putString("owner_voice_profile", VoiceIdentity.encode(profile)).apply()
                        runOnUiThread { say("Owner voice profile saved on this phone. You can now identify a speaker for a test.") }
                    } catch (e: Exception) {
                        runOnUiThread { say("Could not register voice: ${e.message ?: "please try again"}") }
                    }
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun identifyCurrentSpeaker() {
        if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 44)
            return
        }
        val owner = VoiceIdentity.decode(prefs.getString("owner_voice_profile", null))
        if (owner == null) {
            say("No owner voice profile yet. Tap Register my voice as OWNER first.")
            return
        }
        AlertDialog.Builder(this).setTitle("Identify speaker")
            .setMessage("The current speaker should talk naturally for about 4 seconds. This is an experimental voice similarity check, not a secure identity guarantee.")
            .setPositiveButton("Identify") { _, _ ->
                status.text = "Listening to the speaker…"
                network.execute {
                    try {
                        val sample = VoiceIdentity.recordAndExtract(4)
                        val score = VoiceIdentity.similarity(owner, sample)
                        // Conservative prototype threshold; device/mic/noise can change results.
                        val isOwner = score >= 0.90f
                        runOnUiThread {
                            if (isOwner) say("Speaker resembles the registered OWNER voice. Similarity ${"%.2f".format(score)}.")
                            else say("Speaker is not a confident OWNER match. Similarity ${"%.2f".format(score)}. Treat as guest.")
                        }
                    } catch (e: Exception) {
                        runOnUiThread { say("Could not identify speaker: ${e.message ?: "please try again"}") }
                    }
                }
            }.setNegativeButton("Cancel", null).show()
    }

    /** Voice matching is a best-effort prototype. It must not be used as secure authentication. */
    private fun listen() {
        if (::orb.isInitialized) orb.setMode(JarvisOrbView.Mode.LISTENING)
        if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 40); return
        }
        val owner = VoiceIdentity.decode(prefs.getString("owner_voice_profile", null))
        // Until OWNER is registered, retain the normal speech-command experience.
        if (owner == null) { startSpeechRecognition(); return }
        status.text = "Checking who is speaking… / කතා කරන්නේ කවුදැයි හඳුනාගනිමින්…"
        network.execute {
            try {
                val sample = VoiceIdentity.recordAndExtract(3)
                val ownerScore = VoiceIdentity.similarity(owner, sample)
                if (ownerScore >= 0.90f) {
                    runOnUiThread {
                        prefs.edit().putString("last_speaker_name", "OWNER").apply()
                        status.text = "Speaker: OWNER"
                        startSpeechRecognition()
                    }
                    return@execute
                }
                val people = loadSpeakerProfiles()
                var bestName: String? = null
                var bestScore = -1f
                for (person in people) {
                    val profile = VoiceIdentity.decode(person.optString("profile")) ?: continue
                    val score = VoiceIdentity.similarity(profile, sample)
                    if (score > bestScore) { bestScore = score; bestName = person.optString("name") }
                }
                if (bestName != null && bestScore >= 0.90f) {
                    val name = bestName
                    runOnUiThread {
                        prefs.edit().putString("last_speaker_name", name).apply()
                        say("Hi $name. I recognize your voice. Tell me your command.")
                        startSpeechRecognition()
                    }
                } else {
                    runOnUiThread { askUnknownSpeakerName(sample) }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Voice check unavailable; continuing without speaker identification."
                    startSpeechRecognition()
                }
            }
        }
    }

    private fun loadSpeakerProfiles(): JSONArray {
        return try { JSONArray(prefs.getString("speaker_profiles", "[]")) } catch (_: Exception) { JSONArray() }
    }

    private fun askUnknownSpeakerName(profile: FloatArray) {
        val nameInput = EditText(this).apply { hint = "Your name / ඔයාගේ නම"; setSingleLine(true) }
        AlertDialog.Builder(this)
            .setTitle("I don't recognize this voice yet")
            .setMessage("What is your name? I'll save a voice profile on this phone so I can try to recognize you next time. This is experimental and can make mistakes.")
            .setView(nameInput)
            .setPositiveButton("Save name and continue") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isBlank()) {
                    say("No name entered. Continuing as guest.")
                } else {
                    val people = loadSpeakerProfiles()
                    // Update a same-name profile instead of creating duplicates.
                    val updated = JSONArray()
                    for (i in 0 until people.length()) {
                        val item = people.getJSONObject(i)
                        if (!item.optString("name").equals(name, ignoreCase = true)) updated.put(item)
                    }
                    updated.put(JSONObject().put("name", name).put("profile", VoiceIdentity.encode(profile)))
                    prefs.edit().putString("speaker_profiles", updated.toString()).putString("last_speaker_name", name).apply()
                    say("Nice to meet you, $name. I'll try to recognize your voice next time.")
                }
                startSpeechRecognition()
            }
            .setNegativeButton("Skip") { _, _ ->
                prefs.edit().putString("last_speaker_name", "Guest").apply()
                startSpeechRecognition()
            }.show()
    }

    private fun showKnownSpeakers() {
        val people = loadSpeakerProfiles()
        val names = mutableListOf<String>()
        names.add("OWNER (registered separately)")
        for (i in 0 until people.length()) names.add(people.optJSONObject(i)?.optString("name").orEmpty())
        AlertDialog.Builder(this).setTitle("Known voice profiles")
            .setItems(names.toTypedArray()) { _, which ->
                if (which == 0) {
                    AlertDialog.Builder(this).setMessage("OWNER voice profile is stored separately. Re-register it using the OWNER button.")
                        .setPositiveButton("OK", null).show()
                } else {
                    val index = which - 1
                    val selected = people.optJSONObject(index)?.optString("name").orEmpty()
                    AlertDialog.Builder(this).setTitle("Remove $selected?")
                        .setMessage("This removes the saved voice profile from this phone.")
                        .setPositiveButton("Remove") { _, _ ->
                            val updated = JSONArray()
                            for (i in 0 until people.length()) if (i != index) updated.put(people.getJSONObject(i))
                            prefs.edit().putString("speaker_profiles", updated.toString()).apply()
                            say("Voice profile removed.")
                        }.setNegativeButton("Cancel", null).show()
                }
            }.setPositiveButton("Close", null).show()
    }

    private fun startSpeechRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            if (::orb.isInitialized) orb.setMode(JarvisOrbView.Mode.IDLE)
            say("Speech recognition is not available on this phone.")
            return
        }
        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).also { recognizer ->
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        status.text = "Listening… Speak to JARVIS."
                        if (::orb.isInitialized) orb.setMode(JarvisOrbView.Mode.LISTENING)
                    }
                    override fun onBeginningOfSpeech() { if (::orb.isInitialized) orb.setMode(JarvisOrbView.Mode.LISTENING) }
                    override fun onRmsChanged(rmsdB: Float) { if (::orb.isInitialized) orb.setAudioLevel(rmsdB) }
                    override fun onEndOfSpeech() { status.text = "Processing your voice…" }
                    override fun onError(error: Int) {
                        if (::orb.isInitialized) orb.setMode(JarvisOrbView.Mode.IDLE)
                        status.text = "Didn't catch that. Tap Talk to try again."
                        speechRecognizer?.destroy(); speechRecognizer = null
                    }
                    override fun onResults(results: Bundle?) {
                        if (::orb.isInitialized) orb.setMode(JarvisOrbView.Mode.IDLE)
                        val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        speechRecognizer?.destroy(); speechRecognizer = null
                        if (words.isNotBlank()) { status.text = "Heard: $words"; runCommand(words) }
                        else status.text = "No speech detected. Tap Talk to try again."
                    }
                    override fun onPartialResults(partialResults: Bundle?) { }
                    override fun onBufferReceived(buffer: ByteArray?) { }
                    override fun onEvent(eventType: Int, params: Bundle?) { }
                })
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell MARK LVI what to do")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    if (android.os.Build.VERSION.SDK_INT >= 34) {
                        putExtra("android.speech.extra.ENABLE_LANGUAGE_DETECTION", true)
                        putExtra("android.speech.extra.ENABLE_LANGUAGE_SWITCH", true)
                        putStringArrayListExtra("android.speech.extra.LANGUAGE_DETECTION_ALLOWED_LANGUAGES", arrayListOf("si-LK", "en-US", "en-GB"))
                        putStringArrayListExtra("android.speech.extra.LANGUAGE_SWITCH_ALLOWED_LANGUAGES", arrayListOf("si-LK", "en-US", "en-GB"))
                    }
                }
                recognizer.startListening(intent)
            }
        } catch (_: Exception) {
            if (::orb.isInitialized) orb.setMode(JarvisOrbView.Mode.IDLE)
            speechRecognizer?.destroy(); speechRecognizer = null
            say("Could not start speech recognition. Please try again.")
        }
    }

    private fun toggleJarvisWakeWord() {
        if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 42)
            return
        }
        val active = getSharedPreferences("jarvis_wake", MODE_PRIVATE).getBoolean("active", false)
        val intent = Intent(this, JarvisWakeWordService::class.java)
        if (active) {
            stopService(intent)
            getSharedPreferences("jarvis_wake", MODE_PRIVATE).edit().putBoolean("active", false).apply()
            say("Jarvis wake word listener stopped.")
        } else {
            if (android.os.Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                say("Please allow Display over other apps for the floating Jarvis icon, then tap start again.")
                try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) } catch (_: Exception) { }
                return
            }
            try {
                if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
                getSharedPreferences("jarvis_wake", MODE_PRIVATE).edit().putBoolean("active", true).apply()
                say("Jarvis wake word listener starting. Keep the notification visible to know it is listening.")
            } catch (_: Exception) {
                say("Could not start Jarvis listener. Open the app, grant microphone permission and try again.")
            }
        }
    }

    private fun match() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun addButton(label: String, action: () -> Unit) {
        val b = Button(this).apply {
            text = label; isAllCaps = false; textSize = 14f
            setTextColor(0xFFEAF5FF.toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF0A1728.toInt()); setStroke(dp(1), 0xFF214D78.toInt()); cornerRadius = dp(14).toFloat()
            }
            setPadding(dp(14), dp(9), dp(14), dp(9))
            setOnClickListener { action() }
        }
        root.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        })
    }

    @Deprecated("Legacy speech callback; in-app SpeechRecognizer is used now")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) toggleTorch()
            else say("Camera permission is required for flashlight control on this phone.")
        } else if ((requestCode == 40 || requestCode == 42) && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == 42) toggleJarvisWakeWord()
        } else if ((requestCode == 43 || requestCode == 44) && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == 43) enrollOwnerVoice() else identifyCurrentSpeaker()
        } else if ((requestCode == 40 || requestCode == 42 || requestCode == 43 || requestCode == 44) && grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            say("Microphone permission was not granted. You can still type commands.")
        }
    }

    private fun runCommand(raw: String) {
        val detectedLanguage = LanguageMode.detect(raw)
        prefs.edit().putString("last_input_language", detectedLanguage).apply()
        val cleaned = raw.trim().replace(Regex("(?i)^\\s*jarvis[ ,:]*"), "").trim()
        val localizedCommand = SinhalaLanguage.toCommand(cleaned)
        val c = localizedCommand.lowercase(Locale.ROOT)
        if (c.isBlank()) { say("Please say or type a command."); return }
        when {
            c in listOf("home", "go home", "go to home screen", "home screen", "gedara yanna", "home ekata yanna") -> global(PhoneAccessibilityService.GLOBAL_ACTION_HOME, "Going home.")
            c in listOf("back", "go back", "previous screen", "pasupasata yanna", "back yanna") -> global(PhoneAccessibilityService.GLOBAL_ACTION_BACK, "Going back.")
            c.contains("recent apps") || c == "recents" || c == "show recent apps" || c.contains("recent apps pennanna") -> global(PhoneAccessibilityService.GLOBAL_ACTION_RECENTS, "Showing recent apps.")
            c.contains("update yourself") || c.contains("update jarvis") || c.contains("update mark lvi") || c.contains("self update") || c.contains("update karaganna") -> checkForUpdates(true)
            c.contains("check for updates") || c.contains("check updates") || c.contains("update thiyenawada") || c.contains("update balanna") -> checkForUpdates(false)
            c.contains("notification") -> readNotifications()
            c.contains("flashlight") || c.contains("torch") || c.contains("ටෝච්") -> {
                if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.CAMERA), 41)
                } else if (c.contains("off") || c.contains("නිවන්න")) setTorch(false)
                else if (c.contains("on") || c.contains("දල්වන්න")) setTorch(true)
                else toggleTorch()
            }
            c.contains("volume up") || c.contains("increase volume") || c.contains("volume eka wadi karanna") -> volume(AudioManager.ADJUST_RAISE)
            c.contains("volume down") || c.contains("decrease volume") || c.contains("volume eka adu karanna") -> volume(AudioManager.ADJUST_LOWER)
            c.contains("mute") -> volume(AudioManager.ADJUST_MUTE)
            c.contains("wifi settings") || c.contains("wi-fi settings") || c.contains("wifi settings open karanna") -> openSettings(Settings.ACTION_WIFI_SETTINGS, "Opening Wi-Fi settings.")
            c.contains("bluetooth settings") -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Opening Bluetooth settings.")
            c.contains("display settings") -> openSettings(Settings.ACTION_DISPLAY_SETTINGS, "Opening display settings.")
            c.contains("phone settings") || c == "settings" -> openSettings(Settings.ACTION_SETTINGS, "Opening phone settings.")
            c.contains("open camera") || c == "camera" -> openIntent(Intent(MediaStore.ACTION_IMAGE_CAPTURE), "Opening camera.")
            c.startsWith("search ") -> webSearch(localizedCommand.substringAfter(' ').trim())
            c.startsWith("navigate to ") || c.startsWith("directions to ") || c.startsWith("map ") -> openMaps(localizedCommand.substringAfter(' ').trim())
            c.startsWith("call ") || c.startsWith("dial ") -> dialNumber(localizedCommand.substringAfter(' ').trim())
            c.startsWith("sms ") || c.startsWith("text ") || c.startsWith("message ") -> composeSms(localizedCommand)
            c.contains("set alarm") || c.startsWith("alarm ") -> setAlarm(localizedCommand)
            c.contains("set timer") || c.startsWith("timer ") -> setTimer(localizedCommand)
            c.contains("play music") || c.contains("open music") -> openIntent(Intent("android.intent.action.MUSIC_PLAYER"), "Opening music player.")
            c.startsWith("open ") || c.startsWith("launch ") || c.startsWith("start ") -> launchApp(localizedCommand.substringAfter(' ').trim())
            c.contains("dashboard") -> dashboardDialog()
            else -> {
                if (prefs.getBoolean("private_mode", true)) {
                    say("Privacy mode is ON. This command stays on your phone and was not sent over the internet. Turn privacy mode off only if you want to use the optional desktop AI connection.")
                } else if (prefs.getString("bridge_url", "").orEmpty().isNotBlank() && prefs.getString("bridge_token", "").orEmpty().isNotBlank()) {
                    sendToDesktop(cleaned)
                } else if (openAiApiKey.isNotBlank()) {
                    askOpenAI(cleaned)
                } else {
                    say("No AI connection is configured. Open the menu, choose More voice & connection settings, then Set OpenAI API key.")
                }
            }
        }
    }

    private fun checkForUpdates(installIfAvailable: Boolean) {
        say("Checking GitHub for MARK LVI updates.")
        status.text = "GitHub update පරීක්ෂා කරමින්…"
        network.execute {
            try {
                val release = UpdateManager.latestRelease()
                runOnUiThread {
                    if (release == null) {
                        status.text = "No stable release with APK and checksum found."
                        say("No published stable update was found. Please check the JARVIS GitHub Releases page.")
                        return@runOnUiThread
                    }
                    val currentName = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
                    if (!UpdateManager.isNewer(release.tag, currentName)) {
                        status.text = "MARK LVI is up to date (${currentName.ifBlank { "unknown" }})."
                        say("MARK LVI is already up to date.")
                        return@runOnUiThread
                    }
                    val message = "New version: ${release.tag}\nInstalled version: ${currentName.ifBlank { "unknown" }}\n\n${release.notes.take(1800)}\n\nDownload and verify this APK?"
                    AlertDialog.Builder(this)
                        .setTitle("MARK LVI update available")
                        .setMessage(message)
                        .setPositiveButton(if (installIfAvailable) "Download & install" else "Download & install") { _, _ -> downloadUpdate(release) }
                        .setNegativeButton("Later", null)
                        .show()
                    if (installIfAvailable) say("A new version is available. Confirm in the update dialog to download and install it.")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Update check failed: ${e.message ?: "network error"}"
                    say("I could not check GitHub updates. Check your internet connection and the repository release settings.")
                }
            }
        }
    }

    private fun downloadUpdate(release: UpdateManager.ReleaseInfo) {
        status.text = "Downloading update ${release.tag}…"
        say("Downloading update. I will verify the file before opening Android installer.")
        network.execute {
            try {
                val apkFile = UpdateManager.downloadAndVerify(this, release)
                runOnUiThread {
                    try {
                        val message = UpdateManager.openInstaller(this, apkFile)
                        status.text = message
                        say(message)
                    } catch (e: Exception) {
                        status.text = "Could not open installer: ${e.message ?: "unknown error"}"
                        say("Could not open Android installer. Please try again from the app.")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Update failed: ${e.message ?: "download or verification error"}"
                    say("Update failed or checksum verification did not pass. I cancelled installation.")
                }
            }
        }
    }

    private fun global(action: Int, message: String) {
        val service = PhoneAccessibilityService.instance
        if (service == null) say("Please enable MARK LVI Phone Actions in Android Accessibility settings first.")
        else if (service.doGlobalAction(action)) say(message) else say("Android did not allow that navigation action.")
    }

    private fun launchApp(query: String) {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val hit = apps.firstOrNull { app ->
            val label = pm.getApplicationLabel(app).toString().lowercase(Locale.ROOT)
            label == query || label.contains(query)
        }
        if (hit == null) { say("I couldn't find an app matching $query."); return }
        val intent = pm.getLaunchIntentForPackage(hit.packageName)
        if (intent == null) say("That app does not provide a launch screen.")
        else { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(intent); say("Opening ${pm.getApplicationLabel(hit)}.") }
    }

    private fun openSettings(action: String, message: String) {
        try { startActivity(Intent(action)); say(message) } catch (_: Exception) { say("That settings page is not available on this phone.") }
    }

    private fun volume(direction: Int) {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        say("Volume command sent.")
    }

    private fun toggleTorch() = setTorch(!torchOn)

    private fun setTorch(enabled: Boolean) {
        try {
            val manager = getSystemService(CAMERA_SERVICE) as CameraManager
            val camera = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            if (camera == null) { say("This phone has no available flashlight."); return }
            torchOn = enabled
            manager.setTorchMode(camera, torchOn)
            say(if (torchOn) "Flashlight on." else "Flashlight off.")
        } catch (_: Exception) { say("Couldn't change the flashlight. Check camera permission and phone support.") }
    }

    private fun readNotifications() {
        val notices = PhoneNotificationListener.latest.take(8)
        if (notices.isEmpty()) { say("No recent notifications available. Enable notification access and try again."); return }
        val message = notices.joinToString(". ") { n -> listOf(n.app.substringAfterLast('.'), n.title, n.text).filter { it.isNotBlank() }.joinToString(": ") }
        status.text = message
        say(message)
    }


    private fun openIntent(intent: Intent, success: String) {
        try {
            if (intent.resolveActivity(packageManager) == null) { say("No compatible app is available."); return }
            startActivity(intent); say(success)
        } catch (_: Exception) { say("Android could not open that action.") }
    }

    private fun webSearch(query: String) {
        if (query.isBlank()) { say("Tell me what to search for."); return }
        openIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")), "Opening web search.")
    }

    private fun openMaps(place: String) {
        if (place.isBlank()) { say("Tell me the destination."); return }
        openIntent(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(place)}")), "Opening maps.")
    }

    private fun dialNumber(number: String) {
        if (number.isBlank()) { say("Say call followed by a phone number."); return }
        openIntent(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")), "Review the number in the dialer before calling.")
    }

    private fun composeSms(raw: String) {
        val match = Regex("^(?:sms|text|message)\\s+(\\+?[0-9][0-9 -]{5,})\\s+(.+)$", RegexOption.IGNORE_CASE).find(raw.trim())
        if (match == null) { say("Try: sms 0771234567 Hello there"); return }
        val number = match.groupValues[1].replace(" ", "").replace("-", "")
        openIntent(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")).putExtra("sms_body", match.groupValues[2]), "Review the message and send it in your SMS app.")
    }

    private fun setAlarm(raw: String) {
        val m = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE).find(raw)
        if (m == null) { say("Try: set alarm 6:30 am"); return }
        var hour = m.groupValues[1].toIntOrNull() ?: 7
        val minute = m.groupValues[2].toIntOrNull() ?: 0
        when (m.groupValues[3].lowercase(Locale.ROOT)) { "pm" -> if (hour in 1..11) hour += 12; "am" -> if (hour == 12) hour = 0 }
        if (hour !in 0..23 || minute !in 0..59) { say("That alarm time is invalid."); return }
        openIntent(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, hour).putExtra(AlarmClock.EXTRA_MINUTES, minute).putExtra(AlarmClock.EXTRA_SKIP_UI, false), "Review the alarm in your clock app.")
    }

    private fun setTimer(raw: String) {
        val m = Regex("(\\d+)\\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)", RegexOption.IGNORE_CASE).find(raw)
        if (m == null) { say("Try: set timer 5 minutes"); return }
        val n = m.groupValues[1].toIntOrNull() ?: 0
        val u = m.groupValues[2].lowercase(Locale.ROOT)
        val seconds = when { u.startsWith("hour") || u.startsWith("hr") -> n * 3600; u.startsWith("sec") -> n; else -> n * 60 }
        if (seconds <= 0) { say("Timer duration must be positive."); return }
        openIntent(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, seconds).putExtra(AlarmClock.EXTRA_SKIP_UI, false), "Review the timer in your clock app.")
    }


    private fun togglePrivacyMode() {
        val enabled = prefs.getBoolean("private_mode", true)
        if (!enabled) {
            AlertDialog.Builder(this)
                .setTitle("Turn privacy mode off?")
                .setMessage("With privacy mode OFF, unknown commands may be sent to the optional desktop MARK LVI server. That server or its configured AI provider may process the text. Continue only if you trust that setup.")
                .setPositiveButton("Turn off") { _, _ ->
                    prefs.edit().putBoolean("private_mode", false).apply()
                    say("Privacy mode is OFF. Unknown commands may be sent to the configured desktop AI.")
                }
                .setNegativeButton("Keep ON", null).show()
        } else {
            prefs.edit().putBoolean("private_mode", true).apply()
            say("Privacy mode is ON. Unknown commands will not be sent to the desktop AI.")
        }
    }

    private fun bridgeDialog() {
        val server = EditText(this).apply { hint = "https://computer-host:8001"; setSingleLine(true); setText(prefs.getString("bridge_url", "")) }
        val token = EditText(this).apply { hint = "Paired MARK LVI bearer token"; setSingleLine(true); setText(prefs.getString("bridge_token", "")) }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 0); addView(server, match()); addView(token, match()) }
        AlertDialog.Builder(this).setTitle("Connect phone to desktop AI")
            .setMessage("Optional connection only. When Privacy mode is OFF, unknown commands can be sent to this desktop server and may then be processed by its configured AI provider. Use HTTPS and a trusted server; keep the pairing token private. Self-signed TLS certificates may be rejected; do not disable certificate verification.")
            .setView(box).setPositiveButton("Save and test") { _, _ ->
                val url = server.text.toString().trim().trimEnd('/'); val tok = token.text.toString().trim()
                if (!url.startsWith("https://") || tok.isBlank()) say("Enter an HTTPS URL and a paired token.")
                else { prefs.edit().putString("bridge_url", url).putString("bridge_token", tok).apply(); sendToDesktop("hello MARK LVI") }
            }.setNeutralButton("Clear") { _, _ -> prefs.edit().remove("bridge_url").remove("bridge_token").apply(); say("Desktop connection details cleared.") }
            .setNegativeButton("Cancel", null).show()
    }

    private fun sendToDesktop(command: String) {
        val baseUrl = prefs.getString("bridge_url", "").orEmpty().trimEnd('/')
        val token = prefs.getString("bridge_token", "").orEmpty()
        if (!baseUrl.startsWith("https://") || token.isBlank()) { say("Configure the desktop AI bridge first."); return }
        status.text = "Sending command to desktop MARK LVI…"
        network.execute {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL("$baseUrl/api/command").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 8000; readTimeout = 8000; doOutput = true
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                val payload = JSONObject().put("text", command).toString().toByteArray(Charsets.UTF_8)
                conn.outputStream.use { it.write(payload) }
                val code = conn.responseCode
                runOnUiThread { if (code in 200..299) say("Command sent to desktop MARK LVI.") else say("Desktop rejected request (HTTP $code). Check pairing token and address.") }
            } catch (_: Exception) {
                runOnUiThread { say("Could not connect. Check HTTPS certificate, server address, network and token.") }
            } finally { conn?.disconnect() }
        }
    }

    private fun dashboardDialog() {
        val input = EditText(this).apply {
            hint = "https://PC-IP:8001 or paired dashboard URL"
            setSingleLine(true)
            setText(prefs.getString("dashboard_url", ""))
            setSelection(text.length)
        }
        AlertDialog.Builder(this).setTitle("Connect to MARK LVI on your computer")
            .setMessage("Start MARK LVI on the computer, then use its Remote Control / QR pairing feature. Enter the dashboard URL shown there. Keep both devices on a trusted network; do not expose the dashboard to the public internet.")
            .setView(input)
            .setPositiveButton("Open securely") { _, _ ->
                val url = input.text.toString().trim()
                if (url.startsWith("https://")) {
                    prefs.edit().putString("dashboard_url", url).apply()
                    try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                    catch (_: Exception) { say("No browser available.") }
                } else say("Enter the HTTPS dashboard URL from the MARK LVI pairing screen.")
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun say(text: String) {
        val inputLanguage = prefs.getString("last_input_language", "en") ?: "en"
        val responseLanguage = LanguageMode.ttsLanguageFor(text, inputLanguage)
        val spoken = SinhalaLanguage.localizeResponse(text, responseLanguage)
        status.text = spoken
        if (::orb.isInitialized) orb.setMode(JarvisOrbView.Mode.SPEAKING)
        if (::tts.isInitialized) {
            val locale = if (responseLanguage == "si") Locale("si", "LK") else Locale.getDefault()
            val result = tts.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.language = Locale.getDefault()
            }
            tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "mark-lvi-response")
        } else if (::orb.isInitialized) orb.setMode(JarvisOrbView.Mode.IDLE)
    }

    override fun onDestroy() {
        if (wakeReceiverRegistered) { try { unregisterReceiver(wakeReceiver) } catch (_: Exception) {} }
        network.shutdownNow()
        speechRecognizer?.destroy(); speechRecognizer = null
        if (::tts.isInitialized) tts.shutdown()
        super.onDestroy()
    }
}
