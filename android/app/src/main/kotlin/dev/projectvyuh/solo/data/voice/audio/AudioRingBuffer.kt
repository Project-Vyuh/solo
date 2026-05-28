package dev.projectvyuh.solo.data.voice.audio

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Fixed-capacity ring buffer for PCM16 audio samples.
 *
 * Producers (mic capture) push frames; consumers (STT, VAD, wake word) pull
 * windows of any size. Useful when:
 *   - Mic emits 20 ms frames but a model wants 30 ms windows
 *   - Multiple consumers need to tap the same audio stream at their own pace
 *   - We need a fixed-size lookback for "what did the user say just before
 *     the wake word fired?" (replay the trailing N seconds)
 *
 * Concurrency: not lock-free, but uses a single short-held mutex per op.
 * Audio frames are small (≤ a few KB) and ops complete in microseconds, so a
 * mutex is fine here; lock-free is over-engineering for this use case.
 *
 * Overflow policy: oldest samples are overwritten when capacity is exceeded.
 * This matches the "audio stream — newer is better" semantic.
 */
class AudioRingBuffer(val capacity: Int) {
    init { require(capacity > 0) { "capacity must be positive" } }

    private val buffer = ShortArray(capacity)
    private var head = 0       // next write index
    private var size = 0       // current occupancy
    private val lock = ReentrantLock()

    /** Number of samples currently in the buffer. */
    val occupied: Int get() = lock.withLock { size }

    /** Append a frame. If it overflows, the oldest samples are dropped. */
    fun write(samples: ShortArray, offset: Int = 0, length: Int = samples.size) {
        require(offset >= 0 && length >= 0 && offset + length <= samples.size)
        lock.withLock {
            var i = 0
            while (i < length) {
                buffer[head] = samples[offset + i]
                head = (head + 1) % capacity
                if (size < capacity) size++
                i++
            }
        }
    }

    /**
     * Copy the most recent [windowSize] samples to [out] (length [windowSize]).
     * @return true if there were enough samples; false if not (out is unchanged).
     */
    fun peekLatest(windowSize: Int, out: ShortArray): Boolean {
        require(out.size >= windowSize)
        lock.withLock {
            if (size < windowSize) return false
            val start = (head - windowSize + capacity) % capacity
            for (i in 0 until windowSize) {
                out[i] = buffer[(start + i) % capacity]
            }
            return true
        }
    }

    /** Remove all samples. */
    fun clear() {
        lock.withLock {
            head = 0
            size = 0
        }
    }
}
