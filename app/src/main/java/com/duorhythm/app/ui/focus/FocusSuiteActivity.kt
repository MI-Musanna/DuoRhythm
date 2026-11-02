package com.duorhythm.app.ui.focus

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.duorhythm.app.databinding.ActivityFocusSuiteBinding
import com.duorhythm.app.ui.tools.AmbientSoundDialog
import com.duorhythm.app.ui.tools.PomodoroDialog
import com.duorhythm.app.util.AnimationUtils
import kotlin.concurrent.thread
import kotlin.math.sin

class FocusSuiteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFocusSuiteBinding
    private var binauralTrack: AudioTrack? = null
    @Volatile
    private var isPlayingBinaural = false

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

        // Binaural Beat Presets
        binding.cardAlphaWaves.setOnClickListener { playBinauralBeats(baseFreq = 210.0, beatFreq = 10.0, "Alpha (10Hz) • Deep Flow & Focus") }
        binding.cardBetaWaves.setOnClickListener { playBinauralBeats(baseFreq = 250.0, beatFreq = 20.0, "Beta (20Hz) • Peak Concentration & Code") }
        binding.cardThetaWaves.setOnClickListener { playBinauralBeats(baseFreq = 180.0, beatFreq = 6.0, "Theta (6Hz) • Memory & Insight") }
        binding.cardGammaWaves.setOnClickListener { playBinauralBeats(baseFreq = 300.0, beatFreq = 40.0, "Gamma (40Hz) • Cognitive Processing") }
        binding.btnStopAudio.setOnClickListener { stopBinauralBeats() }

        AnimationUtils.attachTactileFeedback(
            binding.btnBackFocus,
            binding.cardPomodoroFocus,
            binding.cardAmbientSounds,
            binding.cardZenDnd,
            binding.cardAlphaWaves,
            binding.cardBetaWaves,
            binding.cardThetaWaves,
            binding.cardGammaWaves,
            binding.btnStopAudio
        )
    }

    private fun playBinauralBeats(baseFreq: Double, beatFreq: Double, title: String) {
        stopBinauralBeats()
        isPlayingBinaural = true
        binding.tvNowPlayingTitle.text = title
        binding.layoutAudioPlayerBar.visibility = View.VISIBLE

        thread(priority = Thread.MAX_PRIORITY) {
            val sampleRate = 44100
            val buffSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(buffSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            binauralTrack = track
            track.play()
            val samples = ShortArray(buffSize)
            var phaseLeft = 0.0
            var phaseRight = 0.0
            val freqLeft = baseFreq
            val freqRight = baseFreq + beatFreq

            while (isPlayingBinaural) {
                for (i in 0 until buffSize / 2 step 2) {
                    val sLeft = (sin(phaseLeft) * 16384).toInt().toShort()
                    val sRight = (sin(phaseRight) * 16384).toInt().toShort()
                    samples[i] = sLeft
                    samples[i + 1] = sRight
                    phaseLeft += 2.0 * Math.PI * freqLeft / sampleRate
                    phaseRight += 2.0 * Math.PI * freqRight / sampleRate
                }
                track.write(samples, 0, samples.size)
            }
        }
    }

    private fun stopBinauralBeats() {
        isPlayingBinaural = false
        try {
            binauralTrack?.stop()
            binauralTrack?.release()
            binauralTrack = null
        } catch (_: Exception) {}
        binding.layoutAudioPlayerBar.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBinauralBeats()
    }
}
