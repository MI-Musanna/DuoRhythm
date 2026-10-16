package com.pocketdev.duorhythm.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.Random
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

object AmbientSoundPlayer {

    enum class SoundMode {
        OFF,
        RAIN,
        OCEAN_WAVES,
        ZEN_BOWL,
        ALPHA_FOCUS_PAD,
        DEEP_BROWN_NOISE
    }

    private var audioTrack: AudioTrack? = null
    @Volatile
    private var isPlaying = false
    private var playbackThread: Thread? = null

    var currentMode: SoundMode = SoundMode.OFF
        private set

    private var volume = 0.6f

    fun setVolume(vol: Float) {
        volume = vol.coerceIn(0f, 1f)
        try {
            audioTrack?.setVolume(volume)
        } catch (_: Exception) {}
    }

    fun play(mode: SoundMode) {
        stop()
        if (mode == SoundMode.OFF) return

        currentMode = mode
        isPlaying = true

        val sampleRate = 44100
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = (minBufferSize * 2).coerceAtLeast(4096)

        try {
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
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.setVolume(volume)
            audioTrack?.play()
        } catch (e: Exception) {
            isPlaying = false
            currentMode = SoundMode.OFF
            return
        }

        playbackThread = Thread {
            val random = Random()
            val shortBuffer = ShortArray(1024)
            var sampleIndex: Long = 0

            // Filters & State variables across buffers
            var rainLp1 = 0.0
            var rainLp2 = 0.0
            var rainDropTimer = 0
            var dropAmp = 0.0
            var dropFreq = 1200.0
            var dropPhase = 0.0

            var oceanLp = 0.0
            var oceanBrown = 0.0

            var brownVal = 0.0
            var brownLp1 = 0.0
            var brownLp2 = 0.0

            val twoPi = 2.0 * Math.PI

            while (isPlaying) {
                when (currentMode) {
                    SoundMode.RAIN -> {
                        // Gentle Rain & Stream: Low-pass filtered pink noise with soft drops and gust swell
                        for (i in shortBuffer.indices) {
                            val white = random.nextDouble() * 2.0 - 1.0
                            rainLp1 += 0.07 * (white - rainLp1)
                            rainLp2 += 0.07 * (rainLp1 - rainLp2)
                            val baseRumble = rainLp2 * 14000.0

                            // Slow gentle breeze modulation (~0.12 Hz)
                            val gust = 0.72 + 0.28 * sin(twoPi * 0.12 * (sampleIndex.toDouble() / sampleRate))

                            // Organic randomized raindrops
                            if (rainDropTimer <= 0) {
                                if (random.nextDouble() < 0.0006) {
                                    dropAmp = 0.25 + random.nextDouble() * 0.35
                                    dropFreq = 700.0 + random.nextDouble() * 900.0
                                    dropPhase = 0.0
                                    rainDropTimer = 1200 + random.nextInt(3500)
                                }
                            } else {
                                rainDropTimer--
                            }

                            var dropSample = 0.0
                            if (dropAmp > 0.002) {
                                dropSample = sin(dropPhase) * dropAmp * 12000.0
                                dropPhase += twoPi * dropFreq / sampleRate
                                dropAmp *= 0.9991 // Smooth exponential decay
                            }

                            val out = (baseRumble * gust + dropSample).toInt().coerceIn(-32767, 32767)
                            shortBuffer[i] = out.toShort()
                            sampleIndex++
                        }
                    }

                    SoundMode.OCEAN_WAVES -> {
                        // Ocean Waves: Rhythmic tidal surge (~7.2s swell period) with dynamic cutoff sweep
                        val swellPeriodSamples = (sampleRate * 7.2)
                        for (i in shortBuffer.indices) {
                            val swellPhase = (sampleIndex % swellPeriodSamples.toLong()) / swellPeriodSamples
                            // Raised cosine swell from 0.0 to 1.0
                            val swell = 0.5 - 0.5 * cos(twoPi * swellPhase)

                            val white = random.nextDouble() * 2.0 - 1.0
                            // Cutoff opens slightly when cresting, closes to deep rumble in trough
                            val cutoff = 0.012 + 0.038 * (swell * swell)
                            oceanLp += cutoff * (white - oceanLp)
                            oceanBrown += 0.008 * (white - oceanBrown)

                            val surf = (oceanLp * 0.75 + oceanBrown * 0.25) * (0.25 + 0.75 * swell) * 19000.0
                            shortBuffer[i] = surf.toInt().coerceIn(-32767, 32767).toShort()
                            sampleIndex++
                        }
                    }

                    SoundMode.ZEN_BOWL -> {
                        // Tibetan Singing Bowl: 5.5s repeating chime cycle with resonant harmonics & soft background drone
                        val cycleSamples = (sampleRate * 5.5).toLong()
                        for (i in shortBuffer.indices) {
                            val cyclePos = (sampleIndex % cycleSamples)
                            val t = cyclePos.toDouble() / sampleRate

                            // Attack ramp over first 20ms to prevent click
                            val attack = (t / 0.02).coerceIn(0.0, 1.0)
                            // Resonant frequencies: fundamental ~216Hz, overtone ~596Hz, high shimmer ~1166Hz
                            val f1 = 216.0
                            val f1Detune = 217.2 // Beating shimmer
                            val f2 = 596.0
                            val f3 = 1166.0

                            val env1 = exp(-0.6 * t) * attack
                            val env2 = exp(-1.4 * t) * attack
                            val env3 = exp(-2.8 * t) * attack

                            val tone1 = sin(twoPi * f1 * t) * 0.55 + sin(twoPi * f1Detune * t) * 0.45
                            val tone2 = sin(twoPi * f2 * t) * 0.35
                            val tone3 = sin(twoPi * f3 * t) * 0.15

                            // Continuous meditative background hum
                            val bgDrone = sin(twoPi * f1 * (sampleIndex.toDouble() / sampleRate)) * 0.12

                            val chime = (tone1 * env1 + tone2 * env2 + tone3 * env3 + bgDrone) * 16000.0
                            shortBuffer[i] = chime.toInt().coerceIn(-32767, 32767).toShort()
                            sampleIndex++
                        }
                    }

                    SoundMode.ALPHA_FOCUS_PAD -> {
                        // Deep Alpha Focus Pad: Warm 108Hz drone pad with soothing 10Hz alpha pulsation
                        for (i in shortBuffer.indices) {
                            val t = sampleIndex.toDouble() / sampleRate
                            val root = sin(twoPi * 108.0 * t) // A2 root
                            val fifth = sin(twoPi * 162.0 * t) * 0.45 // E3 fifth
                            val octave = sin(twoPi * 216.0 * t) * 0.25 // A3 octave

                            // 10 Hz Alpha wave rhythmic entrainment
                            val alphaMod = 0.78 + 0.22 * sin(twoPi * 10.0 * t)

                            // Very slow chorus movement (0.07 Hz)
                            val chorus = 0.85 + 0.15 * sin(twoPi * 0.07 * t)

                            val pad = (root + fifth + octave) * alphaMod * chorus * 11000.0
                            shortBuffer[i] = pad.toInt().coerceIn(-32767, 32767).toShort()
                            sampleIndex++
                        }
                    }

                    SoundMode.DEEP_BROWN_NOISE -> {
                        // Cozy Brown Noise: Integrated white noise with two-pole 250Hz low-pass filter
                        for (i in shortBuffer.indices) {
                            val white = (random.nextDouble() * 2.0 - 1.0) * 0.2
                            brownVal = (brownVal * 0.995) + white
                            brownLp1 += 0.045 * (brownVal - brownLp1)
                            brownLp2 += 0.045 * (brownLp1 - brownLp2)

                            val sample = (brownLp2 * 32000.0).toInt().coerceIn(-32767, 32767)
                            shortBuffer[i] = sample.toShort()
                        }
                    }

                    SoundMode.OFF -> break
                }

                if (!isPlaying) break
                audioTrack?.write(shortBuffer, 0, shortBuffer.size)
            }
        }.apply {
            isDaemon = true
            name = "LifeSync-AmbientSoundThread"
            start()
        }
    }

    fun stop() {
        isPlaying = false
        playbackThread?.interrupt()
        playbackThread = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioTrack = null
        currentMode = SoundMode.OFF
    }
}
