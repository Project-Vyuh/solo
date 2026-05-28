package dev.projectvyuh.solo.data.voice.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "SoloVoicePlayback"

/**
 * Low-latency 24 kHz mono PCM 16-bit playback for TTS chunks.
 *
 * Kokoro-82M emits audio at 24 kHz (its training sample rate). We do NOT
 * resample to 16 kHz — AudioTrack natively supports 24 kHz output.
 *
 * Uses [AudioTrack.PERFORMANCE_MODE_LOW_LATENCY] on API 26+ to keep the
 * speaker-to-ear delay minimal — important because TTS first-audio latency
 * is part of our Phase 1B latency budget.
 *
 * One [AudioPlayback] instance owns one underlying AudioTrack. Construct,
 * call [start], stream chunks via [write], and call [stop] when done. Reuse
 * across turns is OK; releasing requires [release].
 *
 * Threading: [write] serializes on a mutex. AudioTrack itself can be written
 * to from any thread; the mutex guards against concurrent [stop]/[release]
 * vs [write].
 */
class AudioPlayback(
    val sampleRateHz: Int = 24_000,
) {

    private val mutex = Mutex()
    private var track: AudioTrack? = null

    /** Allocate and start the underlying AudioTrack. Safe to call again after [stop]. */
    suspend fun start() = mutex.withLock {
        if (track != null) return@withLock
        val channelOut = AudioFormat.CHANNEL_OUT_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioTrack.getMinBufferSize(sampleRateHz, channelOut, encoding)
        if (minBuf <= 0) error("AudioTrack.getMinBufferSize returned $minBuf")

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRateHz)
            .setChannelMask(channelOut)
            .setEncoding(encoding)
            .build()
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val builder = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }

        val newTrack = builder.build()
        newTrack.play()
        track = newTrack
        Log.i(TAG, "AudioTrack started at $sampleRateHz Hz, low-latency mode")
    }

    /**
     * Write a PCM16 chunk (host short array) to the speaker. Blocks until the
     * samples are queued — typically nearly instant unless the output buffer
     * is full, in which case it backs off until space is available.
     */
    suspend fun write(samples: ShortArray) = mutex.withLock {
        val t = track ?: error("AudioPlayback not started")
        var written = 0
        while (written < samples.size) {
            val n = t.write(samples, written, samples.size - written)
            if (n < 0) {
                Log.e(TAG, "AudioTrack.write returned error $n; aborting")
                break
            }
            written += n
        }
    }

    /** Drain the pending audio and stop. Track can be restarted with [start]. */
    suspend fun stop() = mutex.withLock {
        val t = track ?: return@withLock
        runCatching { t.stop() }
        runCatching { t.flush() }
    }

    /** Fully release native resources. Track cannot be reused afterward. */
    suspend fun release() = mutex.withLock {
        val t = track ?: return@withLock
        runCatching { t.stop() }
        runCatching { t.release() }
        track = null
    }
}
