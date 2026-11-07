package com.israrxy.raazi.data.ai

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

/**
 * Records a short mono clip from the microphone and wraps it in a WAV header.
 * 16 kHz / 16-bit mono keeps a 15 second clip around 480 KB, well inside
 * Gemini's inline request limit. Users can extend up to 60 seconds. Nothing is
 * written to disk.
 */
object AudioPayload {

    const val MAX_DURATION_MS = 15_000L
    const val EXTEND_STEP_MS = 15_000L
    const val MAX_EXTENDED_DURATION_MS = 60_000L

    private const val SAMPLE_RATE = 16_000
    private const val PROGRESS_INTERVAL_MS = 200L
    private const val MIN_DURATION_MS = 500L

    @SuppressLint("MissingPermission")
    suspend fun record(
        shouldStop: () -> Boolean,
        maxDuration: () -> Long = { MAX_DURATION_MS },
        onProgress: (elapsedMs: Long, maxMs: Long) -> Unit,
        onLevel: (Float) -> Unit
    ): ByteArray = withContext(Dispatchers.IO) {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        require(minBuffer > 0) {
            "This device cannot record audio right now. Try again."
        }

        val bufferSize = maxOf(minBuffer, SAMPLE_RATE / 5 * 2)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        try {
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException(
                    "The microphone is not available right now. Try again."
                )
            }

            recorder.startRecording()
            val pcm = ByteArrayOutputStream(bufferSize * 4)
            val buffer = ByteArray(bufferSize)
            val startedAt = SystemClock.elapsedRealtime()
            var lastProgress = 0L

            while (true) {
                coroutineContext.ensureActive()
                val limit = maxDuration()
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                if (elapsed >= limit || shouldStop()) break

                val read = recorder.read(buffer, 0, buffer.size)
                if (read < 0) {
                    throw IllegalStateException(
                        "The microphone stopped unexpectedly. Try again."
                    )
                }
                if (read > 0) {
                    pcm.write(buffer, 0, read)
                    onLevel(levelOf(buffer, read))
                }

                if (elapsed - lastProgress >= PROGRESS_INTERVAL_MS) {
                    lastProgress = elapsed
                    onProgress(elapsed, limit)
                }
            }

            val elapsed = SystemClock.elapsedRealtime() - startedAt
            onProgress(minOf(elapsed, maxDuration()), maxDuration())

            if (elapsed < MIN_DURATION_MS) {
                throw IllegalArgumentException(
                    "That clip was too short to identify. Try again."
                )
            }

            val pcmBytes = pcm.toByteArray()
            if (pcmBytes.isEmpty()) {
                throw IllegalArgumentException(
                    "No audio was captured. Try again."
                )
            }
            wrapWav(pcmBytes)
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }

    private fun levelOf(buffer: ByteArray, read: Int): Float {
        val sampleCount = read / 2
        if (sampleCount <= 0) return 0f
        var sumSquares = 0.0
        var index = 0
        while (index + 1 < read) {
            val raw = (buffer[index].toInt() and 0xff) or (buffer[index + 1].toInt() shl 8)
            val sample = raw.toShort().toInt()
            sumSquares += sample.toDouble() * sample
            index += 2
        }
        val rms = sqrt(sumSquares / sampleCount)
        return ((rms / 32768.0) * 4.0).coerceIn(0.0, 1.0).toFloat()
    }

    private fun wrapWav(pcm: ByteArray): ByteArray {
        val header = ByteArray(WAV_HEADER_BYTES)
        val byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8
        val blockAlign = CHANNELS * BITS_PER_SAMPLE / 8

        fun writeAscii(offset: Int, value: String) {
            value.forEachIndexed { index, char ->
                header[offset + index] = char.code.toByte()
            }
        }

        fun writeInt(offset: Int, value: Int) {
            header[offset] = (value and 0xff).toByte()
            header[offset + 1] = (value shr 8 and 0xff).toByte()
            header[offset + 2] = (value shr 16 and 0xff).toByte()
            header[offset + 3] = (value shr 24 and 0xff).toByte()
        }

        fun writeShort(offset: Int, value: Int) {
            header[offset] = (value and 0xff).toByte()
            header[offset + 1] = (value shr 8 and 0xff).toByte()
        }

        writeAscii(0, "RIFF")
        writeInt(4, WAV_HEADER_BYTES - 8 + pcm.size)
        writeAscii(8, "WAVE")
        writeAscii(12, "fmt ")
        writeInt(16, 16)
        writeShort(20, 1) // PCM
        writeShort(22, CHANNELS)
        writeInt(24, SAMPLE_RATE)
        writeInt(28, byteRate)
        writeShort(32, blockAlign)
        writeShort(34, BITS_PER_SAMPLE)
        writeAscii(36, "data")
        writeInt(40, pcm.size)

        val output = ByteArrayOutputStream(WAV_HEADER_BYTES + pcm.size)
        output.write(header)
        output.write(pcm)
        return output.toByteArray()
    }

    private const val CHANNELS = 1
    private const val BITS_PER_SAMPLE = 16
    private const val WAV_HEADER_BYTES = 44
}
