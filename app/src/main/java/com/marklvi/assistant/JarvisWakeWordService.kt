package com.marklvi.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * User-started, visible foreground microphone service. Android speech recognition is not a
 * true low-power hotword engine; availability and long-running behaviour vary by device.
 */
class JarvisWakeWordService : Service(), RecognitionListener {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var waitingForCommand = false
    private var lastWakeAt = 0L
    private var cpuWakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Listening for ‘Jarvis’ — tap Stop to turn off"))
        // Keep the CPU awake while the user explicitly enables the foreground listener.
        // Android may still restrict speech recognition or terminate the service on some devices.
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            cpuWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MARKLVI:JarvisListener")
                .apply { setReferenceCounted(false); acquire() }
        } catch (_: Exception) { }
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val preferred = Locale.getDefault()
                val result = tts?.setLanguage(preferred)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) tts?.language = Locale.getDefault()
            }
        }
        if (Build.VERSION.SDK_INT >= 23 && android.provider.Settings.canDrawOverlays(this)) {
            try { startService(Intent(this, JarvisFloatingIconService::class.java)) } catch (_: Exception) { }
        }
        startRecognitionSoon(700)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Jarvis wake word", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Visible notification while the user-started microphone listener is active"
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val stopIntent = Intent(this, JarvisWakeWordService::class.java).setAction(ACTION_STOP)
        val pending = PendingIntent.getService(this, 88, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("MARK LVI • Jarvis listener")
            .setContentText(text)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop listening", pending).build())
            .build()
    }

    private fun updateNotification(text: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun startRecognitionSoon(delay: Long) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ startRecognition() }, delay)
    }

    private fun startRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("Speech recognition unavailable on this device")
            handler.postDelayed({ startRecognition() }, 5000)
            return
        }
        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(this) }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                if (Build.VERSION.SDK_INT >= 34) {
                    putExtra("android.speech.extra.ENABLE_LANGUAGE_DETECTION", true)
                    putExtra("android.speech.extra.ENABLE_LANGUAGE_SWITCH", true)
                    putStringArrayListExtra("android.speech.extra.LANGUAGE_DETECTION_ALLOWED_LANGUAGES", arrayListOf("si-LK", "en-US", "en-GB"))
                    putStringArrayListExtra("android.speech.extra.LANGUAGE_SWITCH_ALLOWED_LANGUAGES", arrayListOf("si-LK", "en-US", "en-GB"))
                }
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            updateNotification("Listener paused; retrying")
            startRecognitionSoon(2500)
        }
    }

    override fun onResults(results: android.os.Bundle?) {
        val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
        if (phrase.isNotBlank()) handlePhrase(phrase)
        startRecognitionSoon(650)
    }

    private fun handlePhrase(phrase: String) {
        val lower = phrase.lowercase(Locale.ROOT)
        val index = lower.indexOf("jarvis")
        if (waitingForCommand) {
            waitingForCommand = false
            updateNotification("Listening for ‘Jarvis’")
            dispatchCommand(phrase)
            return
        }
        if (index >= 0) {
            lastWakeAt = System.currentTimeMillis()
            val afterWake = phrase.substring(index + "jarvis".length).trim().trimStart(',', ':', ' ', '-')
            if (afterWake.isNotBlank()) {
                updateNotification("Jarvis heard a command")
                dispatchCommand(afterWake)
            } else {
                waitingForCommand = true
                updateNotification("Jarvis heard. Speak your command now.")
                tts?.speak("Yes?", TextToSpeech.QUEUE_FLUSH, null, "jarvis-wake")
                handler.postDelayed({
                    if (waitingForCommand && System.currentTimeMillis() - lastWakeAt > 12000) {
                        waitingForCommand = false
                        updateNotification("Listening for ‘Jarvis’")
                    }
                }, 12000)
            }
        }
    }

    private fun dispatchCommand(command: String) {
        val intent = Intent(ACTION_COMMAND).setPackage(packageName)
            .putExtra(EXTRA_COMMAND, command)
        sendBroadcast(intent)
    }

    override fun onError(error: Int) {
        // SpeechRecognizer often ends a session after silence. Recreate it and continue while
        // the explicit foreground listening session remains active.
        startRecognitionSoon(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1800 else 900)
    }
    override fun onReadyForSpeech(params: android.os.Bundle?) { }
    override fun onBeginningOfSpeech() { }
    override fun onRmsChanged(rmsdB: Float) { }
    override fun onBufferReceived(buffer: ByteArray?) { }
    override fun onEndOfSpeech() { }
    override fun onPartialResults(partialResults: android.os.Bundle?) { }
    override fun onEvent(eventType: Int, params: android.os.Bundle?) { }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { stopService(Intent(this, JarvisFloatingIconService::class.java)) } catch (_: Exception) { }
        try { recognizer?.cancel(); recognizer?.destroy() } catch (_: Exception) { }
        try { if (cpuWakeLock?.isHeld == true) cpuWakeLock?.release() } catch (_: Exception) { }
        cpuWakeLock = null
        recognizer = null
        tts?.shutdown(); tts = null
        getSharedPreferences("jarvis_wake", MODE_PRIVATE).edit().putBoolean("active", false).apply()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_COMMAND = "com.marklvi.assistant.JARVIS_COMMAND"
        const val EXTRA_COMMAND = "command"
        private const val ACTION_STOP = "com.marklvi.assistant.STOP_JARVIS"
        private const val CHANNEL_ID = "jarvis_wake_listener"
        private const val NOTIFICATION_ID = 6206
    }
}
