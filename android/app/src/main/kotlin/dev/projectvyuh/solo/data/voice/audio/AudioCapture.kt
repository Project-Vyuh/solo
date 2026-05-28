package dev.projectvyuh.solo.data.voice.audio

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "SoloVoiceCapture"

/**
 * 16 kHz mono PCM 16-bit microphone capture.
 *
 * The 16 kHz / mono / PCM16 format is what every speech model in Solo's voice
 * stack expects natively (Moonshine, Silero VAD, openWakeWord). No resampling
 * needed downstream.
 *
 * Emits fixed-size [ShortArray] frames sized for [frameSizeMs]. At 16 kHz,
 * 20 ms = 320 samples — a common chunk size for streaming STT and VAD.
 *
 * Requires [Manifest.permission.RECORD_AUDIO]; caller must check this before
 * starting capture. We do not request permission here — that's a UI-layer
 * concern.
 *
 * @param frameSizeMs frame duration in ms. 20 ms is the default; Silero VAD
 *   wants 30 ms, openWakeWord wants 80 ms — caller picks per-consumer.
 */
class AudioCapture(
    private val frameSizeMs: Int = 20,
) {

    companion object {
        const val SAMPLE_RATE_HZ = 16_000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    /** Samples per emitted frame at [SAMPLE_RATE_HZ]. */
    val frameSize: Int = SAMPLE_RATE_HZ * frameSizeMs / 1000

    /**
     * Open the mic and emit audio frames as a cold Flow. Cancelling the
     * collecting coroutine cleanly releases the AudioRecord.
     *
     * Throws [SecurityException] if RECORD_AUDIO is not granted at runtime.
     */
    @SuppressLint("MissingPermission")
    fun frames(scope: CoroutineScope): Flow<ShortArray> = callbackFlow {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_HZ, CHANNEL_CONFIG, ENCODING)
        if (minBufferSize <= 0) {
            close(IllegalStateException("AudioRecord.getMinBufferSize returned $minBufferSize"))
            return@callbackFlow
        }
        // Use 4× the OS minimum to absorb scheduler jitter without dropping samples.
        val bufferSize = (minBufferSize * 4).coerceAtLeast(frameSize * 2 * 2)

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE_HZ,
            CHANNEL_CONFIG,
            ENCODING,
            bufferSize,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            close(IllegalStateException("AudioRecord failed to initialize"))
            return@callbackFlow
        }

        Log.i(TAG, "starting capture: sr=$SAMPLE_RATE_HZ frameSize=$frameSize bufferSize=$bufferSize")
        record.startRecording()

        val readJob: Job = scope.launch(Dispatchers.IO) {
            val buf = ShortArray(frameSize)
            while (isActive) {
                val n = record.read(buf, 0, buf.size)
                if (n < 0) {
                    Log.w(TAG, "AudioRecord.read returned error $n; stopping")
                    break
                }
                if (n != buf.size) {
                    // Partial reads can happen during startup/shutdown — emit only complete frames.
                    continue
                }
                val frame = ShortArray(buf.size)
                System.arraycopy(buf, 0, frame, 0, buf.size)
                val result = trySend(frame)
                if (!result.isSuccess) {
                    // Consumer can't keep up — back-pressure rather than queue forever.
                    Log.w(TAG, "frame dropped: consumer slow")
                }
            }
        }

        awaitClose {
            Log.i(TAG, "closing capture")
            readJob.cancel()
            runCatching { record.stop() }
            record.release()
        }
    }.flowOn(Dispatchers.IO)
}
