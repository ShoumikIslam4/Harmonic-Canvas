package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.model.AtmosphereMode
import com.example.model.SoundTexture
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

class HarmonicAudioEngine {

    private val sampleRate = 44100
    private val bufferSize = 1024
    private val isRunning = AtomicBoolean(false)
    private var audioTrack: AudioTrack? = null
    private var synthThread: Thread? = null

    private val maxVoices = 16
    private val voices = Array(maxVoices) { Voice() }

    @Volatile
    var isMuted: Boolean = false

    @Volatile
    var masterVolume: Float = 0.85f

    @Volatile
    var atmosphereMode: AtmosphereMode = AtmosphereMode.OFF

    // Atmosphere state
    private var rainB0 = 0.0
    private var rainB1 = 0.0
    private var rainB2 = 0.0
    private var dronePhase1 = 0.0
    private var dronePhase2 = 0.0
    private var dronePhase3 = 0.0

    private class Voice {
        @Volatile var active: Boolean = false
        var freq: Double = 440.0
        var phase: Double = 0.0
        var phaseStep: Double = 0.0
        var sampleIndex: Long = 0
        var totalSamples: Long = 0
        var texture: SoundTexture = SoundTexture.CRYSTAL
        var velocity: Float = 0.8f
        var decayRate: Double = 2.0
    }

    fun start() {
        if (isRunning.getAndSet(true)) return

        val minBufSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val trackBufSize = maxOf(minBufSize, bufferSize * 4)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(trackBufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        synthThread = Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            val audioBuffer = ShortArray(bufferSize)

            while (isRunning.get()) {
                generateAudioChunk(audioBuffer)
                audioTrack?.write(audioBuffer, 0, audioBuffer.size)
            }
        }, "HarmonicAudioSynthThread").apply { start() }
    }

    fun stop() {
        isRunning.set(false)
        synthThread?.interrupt()
        synthThread = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
    }

    fun playNote(frequency: Float, texture: SoundTexture, velocity: Float = 0.8f) {
        if (isMuted) return
        val clampedVelocity = velocity.coerceIn(0.1f, 1.0f)
        val freqD = frequency.toDouble().coerceIn(60.0, 4000.0)

        synchronized(voices) {
            // Find inactive voice, or voice furthest along its lifecycle
            var targetIndex = -1
            var maxProgress = -1.0

            for (i in 0 until maxVoices) {
                val v = voices[i]
                if (!v.active) {
                    targetIndex = i
                    break
                }
                val progress = v.sampleIndex.toDouble() / v.totalSamples.coerceAtLeast(1)
                if (progress > maxProgress) {
                    maxProgress = progress
                    targetIndex = i
                }
            }

            if (targetIndex != -1) {
                val durationSec = when (texture) {
                    SoundTexture.CRYSTAL -> 2.4
                    SoundTexture.ZEN_BELL -> 3.2
                    SoundTexture.CYBER_PLUCK -> 1.2
                    SoundTexture.PURE_SINE -> 1.8
                }
                val decay = when (texture) {
                    SoundTexture.CRYSTAL -> 1.8
                    SoundTexture.ZEN_BELL -> 1.2
                    SoundTexture.CYBER_PLUCK -> 4.5
                    SoundTexture.PURE_SINE -> 1.9
                }

                voices[targetIndex].apply {
                    freq = freqD
                    phase = 0.0
                    phaseStep = (2.0 * PI * freqD) / sampleRate
                    sampleIndex = 0
                    totalSamples = (durationSec * sampleRate).toLong()
                    this.texture = texture
                    this.velocity = clampedVelocity
                    this.decayRate = decay
                    active = true
                }
            }
        }
    }

    private fun generateAudioChunk(outputBuffer: ShortArray) {
        if (isMuted) {
            outputBuffer.fill(0)
            return
        }

        val twoPi = 2.0 * PI
        val droneStep1 = (twoPi * 108.0) / sampleRate
        val droneStep2 = (twoPi * 110.0) / sampleRate
        val droneStep3 = (twoPi * 216.0) / sampleRate

        for (i in outputBuffer.indices) {
            var mixedSample = 0.0

            // 1. Synthesize active voices
            synchronized(voices) {
                for (v in voices) {
                    if (!v.active) continue

                    val t = v.sampleIndex.toDouble() / sampleRate
                    val attackTime = 0.006 // 6ms attack to eliminate clicks
                    val attackGain = if (t < attackTime) (t / attackTime) else 1.0
                    val envelope = attackGain * exp(-v.decayRate * t) * v.velocity

                    if (envelope < 0.0005 || v.sampleIndex >= v.totalSamples) {
                        v.active = false
                        continue
                    }

                    val sampleValue = when (v.texture) {
                        SoundTexture.CRYSTAL -> {
                            val f1 = sin(v.phase)
                            val f2 = 0.35 * sin(v.phase * 2.001)
                            val f3 = 0.18 * sin(v.phase * 3.004)
                            val f5 = 0.08 * sin(v.phase * 5.0)
                            (f1 + f2 + f3 + f5) * 0.65
                        }
                        SoundTexture.ZEN_BELL -> {
                            val f1 = sin(v.phase)
                            val f2 = 0.42 * sin(v.phase * 2.756)
                            val f3 = 0.22 * sin(v.phase * 5.404)
                            val f4 = 0.10 * sin(v.phase * 8.93)
                            (f1 + f2 + f3 + f4) * 0.6
                        }
                        SoundTexture.CYBER_PLUCK -> {
                            val filterEnv = exp(-t * 9.0)
                            val s1 = sin(v.phase)
                            val s2 = 0.6 * filterEnv * sin(v.phase * 2.0)
                            val s3 = 0.35 * filterEnv * sin(v.phase * 3.0)
                            val s4 = 0.2 * filterEnv * sin(v.phase * 4.0)
                            (s1 + s2 + s3 + s4) * 0.65
                        }
                        SoundTexture.PURE_SINE -> {
                            sin(v.phase) * 0.85
                        }
                    }

                    mixedSample += sampleValue * envelope
                    v.phase = (v.phase + v.phaseStep) % twoPi
                    v.sampleIndex++
                }
            }

            // 2. Synthesize atmosphere noise/drone bed
            when (atmosphereMode) {
                AtmosphereMode.OFF -> {}
                AtmosphereMode.RAIN_MURMUR -> {
                    // Filtered pink noise algorithm (Paul Kellet's method)
                    val white = Random.nextDouble(-1.0, 1.0)
                    rainB0 = 0.99886 * rainB0 + white * 0.0555179
                    rainB1 = 0.99332 * rainB1 + white * 0.0750759
                    rainB2 = 0.96900 * rainB2 + white * 0.1538520
                    val pink = rainB0 + rainB1 + rainB2 + white * 0.5362
                    mixedSample += pink * 0.022 // soft soothing rain bed
                }
                AtmosphereMode.COSMIC_CHORD -> {
                    val s1 = sin(dronePhase1) * 0.04
                    val s2 = sin(dronePhase2) * 0.035
                    val s3 = sin(dronePhase3) * 0.015
                    mixedSample += (s1 + s2 + s3)
                    dronePhase1 = (dronePhase1 + droneStep1) % twoPi
                    dronePhase2 = (dronePhase2 + droneStep2) % twoPi
                    dronePhase3 = (dronePhase3 + droneStep3) % twoPi
                }
            }

            // 3. Master volume & Soft Saturation Limiter (tanh)
            val processed = tanh(mixedSample * masterVolume * 1.1)
            val pcm = (processed * 32767.0).coerceIn(-32767.0, 32767.0).toInt().toShort()
            outputBuffer[i] = pcm
        }
    }
}
