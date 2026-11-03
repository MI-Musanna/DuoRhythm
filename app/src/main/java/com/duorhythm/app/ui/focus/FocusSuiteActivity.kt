package com.duorhythm.app.ui.focus

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.duorhythm.app.databinding.ActivityFocusSuiteBinding
import com.duorhythm.app.ui.tools.AmbientSoundDialog
import com.duorhythm.app.ui.tools.PomodoroDialog
import com.duorhythm.app.util.AnimationUtils
import java.util.Random
import kotlin.concurrent.thread
import kotlin.math.sin

class FocusSuiteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFocusSuiteBinding
    private var audioTrack: AudioTrack? = null
    private var isPlayingAudio = false
    private var masterVolume = 0.6f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFocusSuiteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBackFocus.setOnClickListener { finish() }

        // Productivity Tools Launchers
        binding.cardPomodoroFocus.setOnClickListener {
            PomodoroDialog(this).show()
        }

        binding.cardAmbientSounds.setOnClickListener {
            AmbientSoundDialog(this).show()
        }

        binding.cardZenDnd.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)
                startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_SOUND_SETTINGS)
                    startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(this, "Please adjust DND settings in system Settings > Sound", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Scientific Binaural Frequencies layered on Soft Ambient Noise
        binding.cardAlphaWaves.setOnClickListener {
            playLayeredBinaural(baseFreq = 144.0, beatFreq = 10.0, "Alpha Flow (10 Hz) • Soft Rain & Calm Focus")
        }
        binding.cardBetaWaves.setOnClickListener {
            playLayeredBinaural(baseFreq = 180.0, beatFreq = 20.0, "Beta Concentration (20 Hz) • Deep Coding & Logic")
        }
        binding.cardThetaWaves.setOnClickListener {
            playLayeredBinaural(baseFreq = 120.0, beatFreq = 6.0, "Theta Meditation (6 Hz) • Creative Insight & Rest")
        }
        binding.cardGammaWaves.setOnClickListener {
            playLayeredBinaural(baseFreq = 216.0, beatFreq = 40.0, "Gamma Processing (40 Hz) • Peak Memory Recall")
        }
        binding.cardCozyBrown.setOnClickListener {
            playLayeredBinaural(baseFreq = 0.0, beatFreq = 0.0, "Pure Brown Noise • Deep Waterfall (Zero Whistle)")
        }

        binding.btnStopAudio.setOnClickListener { stopAudio() }

        binding.sbVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                masterVolume = progress / 100f
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        AnimationUtils.attachTactileFeedback(
            binding.btnBackFocus,
            binding.cardPomodoroFocus,
            binding.cardAmbientSounds,
            binding.cardZenDnd,
            binding.cardAlphaWaves,
            binding.cardBetaWaves,
            binding.cardThetaWaves,
            binding.cardGammaWaves,
            binding.cardCozyBrown,
            binding.btnStopAudio
        )
    }

    private fun playLayeredBinaural(baseFreq: Double, beatFreq: Double, title: String) {
        stopAudio()
        isPlayingAudio = true
        binding.tvNowPlayingTitle.text = title
        binding.layoutAudioPlayerBar.visibility = View.VISIBLE

        thread(priority = Thread.MAX_PRIORITY) {
            val sampleRate = 44100
            val buffSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(buffSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            val samples = ShortArray(buffSize)
            val random = java.util.Random()

            var brownL = 0.0
            var brownR = 0.0
            var phase1 = 0.0
            var phase2 = 0.0

            // Warm ambient root chord (Gentle, low-octave meditative pad)
            val chordRoot = if (baseFreq > 0) baseFreq else 108.0

            while (isPlayingAudio) {
                for (i in 0 until buffSize / 2 step 2) {
                    // 1. Organic Rain/Brown Ambient Blanket (Zero sharp hiss)
                    val whiteL = random.nextDouble() * 2.0 - 1.0
                    val whiteR = random.nextDouble() * 2.0 - 1.0
                    brownL = (brownL + (0.015 * whiteL)) / 1.015
                    brownR = (brownR + (0.015 * whiteR)) / 1.015

                    // 2. Soft, filtered warm musical pad (Sine harmonics blended so it sounds like music, NOT a test beep)
                    val padL = (sin(phase1) + 0.4 * sin(phase1 * 1.5) + 0.2 * sin(phase1 * 2.0)) * 0.04
                    val padR = (sin(phase2) + 0.4 * sin(phase2 * 1.5) + 0.2 * sin(phase2 * 2.0)) * 0.04

                    val finalL = ((brownL * 0.5 + padL) * masterVolume).coerceIn(-1.0, 1.0)
                    val finalR = ((brownR * 0.5 + padR) * masterVolume).coerceIn(-1.0, 1.0)

                    samples[i] = (finalL * 32767).toInt().toShort()
                    samples[i + 1] = (finalR * 32767).toInt().toShort()

                    phase1 += 2.0 * Math.PI * chordRoot / sampleRate
                    phase2 += 2.0 * Math.PI * (chordRoot + beatFreq) / sampleRate
                }
                audioTrack?.write(samples, 0, samples.size)
            }
        }
    }

    private fun stopAudio() {
        isPlayingAudio = false
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (_: Exception) {}
        binding.layoutAudioPlayerBar.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudio()
    }

    companion object {
        val soundOptions = listOf(
            Pair("Alpha Waves (10 Hz)", "Deep flow, relaxed focus & memory retention"),
            Pair("Beta Waves (20 Hz)", "High concentration, problem solving & coding"),
            Pair("Theta Waves (6 Hz)", "Creative insight, intuition & consolidation"),
            Pair("Gamma Waves (40 Hz)", "Peak cognitive processing & fast information recall"),
            Pair("Cozy Brown Noise", "Deep waterfall, zero high-pitch hiss")
        )

        fun setupBinauralRadioGroup(radioGroup: android.widget.RadioGroup) {
            radioGroup.removeAllViews()
            soundOptions.forEachIndexed { index, (title, desc) ->
                val radioButton = android.widget.RadioButton(radioGroup.context).apply {
                    id = index + 100
                    text = "$title\n$desc"
                    textSize = 13f
                    setTextColor(android.graphics.Color.WHITE)
                    buttonTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#00E5FF"))
                    setPadding(16, 16, 16, 16)
                }
                radioGroup.addView(radioButton)
            }
            radioGroup.check(100) // Default to Alpha Waves
        }
    }
}
