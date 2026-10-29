package com.marklvi.assistant

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/** Lightweight on-device acoustic voice profile prototype. Not suitable for security authentication. */
object VoiceIdentity {
    private const val RATE = 16000
    private const val FRAME = 256
    private const val BANDS = 20

    fun recordAndExtract(seconds: Int): FloatArray {
        val minBuffer = AudioRecord.getMinBufferSize(
            RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        require(minBuffer > 0) { "Audio recording is unavailable on this device" }
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, RATE * 2)
        )
        require(recorder.state == AudioRecord.STATE_INITIALIZED) { "Could not initialize microphone" }
        val pcm = ShortArray(RATE * seconds)
        try {
            recorder.startRecording()
            var offset = 0
            while (offset < pcm.size) {
                val n = recorder.read(pcm, offset, minOf(2048, pcm.size - offset))
                if (n < 0) throw IllegalStateException("Microphone recording failed")
                if (n > 0) offset += n
            }
        } finally {
            try { recorder.stop() } catch (_: Exception) {}
            recorder.release()
        }
        return extractFeatures(pcm)
    }

    private fun extractFeatures(pcm: ShortArray): FloatArray {
        val accum = DoubleArray(BANDS)
        var frames = 0
        var energy = 0.0
        var i = 0
        while (i + FRAME <= pcm.size) {
            val x = DoubleArray(FRAME) { j ->
                val v = pcm[i + j].toDouble() / 32768.0
                energy += v * v
                v * (0.54 - 0.46 * cos(2.0 * PI * j / (FRAME - 1)))
            }
            // Small DFT over positive-frequency bins, grouped into bands.
            for (k in 1..(FRAME / 2)) {
                var re = 0.0; var im = 0.0
                for (n in 0 until FRAME) {
                    val a = 2.0 * PI * k * n / FRAME
                    re += x[n] * cos(a); im -= x[n] * kotlin.math.sin(a)
                }
                val band = minOf(BANDS - 1, ((k - 1) * BANDS) / (FRAME / 2 - 1))
                accum[band] += sqrt(re * re + im * im)
            }
            frames++
            i += FRAME
        }
        if (frames == 0 || energy / pcm.size < 0.00002) throw IllegalArgumentException("Too quiet. Speak clearly and try again.")
        val values = FloatArray(BANDS) { (accum[it] / frames).toFloat() }
        val norm = sqrt(values.sumOf { it.toDouble() * it.toDouble() }).toFloat().coerceAtLeast(1e-8f)
        return FloatArray(BANDS) { values[it] / norm }
    }

    fun similarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return -1f
        var dot = 0f; var na = 0f; var nb = 0f
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        return dot / (sqrt(na) * sqrt(nb)).coerceAtLeast(1e-8f)
    }

    fun encode(profile: FloatArray): String = profile.joinToString(",") { it.toString() }
    fun decode(profile: String?): FloatArray? {
        if (profile.isNullOrBlank()) return null
        return try { profile.split(",").map { it.toFloat() }.toFloatArray().takeIf { it.size == BANDS } } catch (_: Exception) { null }
    }
}
