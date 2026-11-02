package com.duorhythm.app.ui.tools

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Toast
import com.duorhythm.app.audio.AmbientSoundPlayer
import com.duorhythm.app.databinding.DialogAmbientSoundBinding
import com.duorhythm.app.util.AnimationUtils

class AmbientSoundDialog(private val context: Context) {

    private val soundOptions = listOf(
        Pair("Alpha Waves (10 Hz)", "Deep flow, relaxed focus & memory retention"),
        Pair("Beta Waves (20 Hz)", "High concentration, problem solving & coding"),
        Pair("Theta Waves (6 Hz)", "Creative insight, intuition & consolidation"),
        Pair("Gamma Waves (40 Hz)", "Peak cognitive processing & fast information recall"),
        Pair("Cozy Brown Noise", "Deep waterfall, zero high-pitch hiss")
    )

    fun setupBinauralRadioGroup(radioGroup: RadioGroup) {
        radioGroup.removeAllViews()
        soundOptions.forEachIndexed { index, (title, desc) ->
            val radioButton = RadioButton(radioGroup.context).apply {
                id = index + 100
                text = "$title\n$desc"
                textSize = 13f
                setTextColor(Color.WHITE)
                buttonTintList = ColorStateList.valueOf(Color.parseColor("#00E5FF"))
                setPadding(16, 16, 16, 16)
            }
            radioGroup.addView(radioButton)
        }
        radioGroup.check(100) // Default to Alpha Waves
    }

    fun show() {
        val binding = DialogAmbientSoundBinding.inflate(LayoutInflater.from(context))

        setupBinauralRadioGroup(binding.rgSoundModes)

        // Select currently active mode if playing
        when (AmbientSoundPlayer.currentMode) {
            AmbientSoundPlayer.SoundMode.BETA_WAVES -> binding.rgSoundModes.check(101)
            AmbientSoundPlayer.SoundMode.THETA_WAVES -> binding.rgSoundModes.check(102)
            AmbientSoundPlayer.SoundMode.GAMMA_WAVES -> binding.rgSoundModes.check(103)
            AmbientSoundPlayer.SoundMode.DEEP_BROWN_NOISE -> binding.rgSoundModes.check(104)
            else -> binding.rgSoundModes.check(100)
        }

        binding.sbVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                AmbientSoundPlayer.setVolume(progress / 100f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        fun getSelectedMode(): Pair<AmbientSoundPlayer.SoundMode, String> {
            return when (binding.rgSoundModes.checkedRadioButtonId) {
                101 -> Pair(AmbientSoundPlayer.SoundMode.BETA_WAVES, "Beta Waves (20 Hz)")
                102 -> Pair(AmbientSoundPlayer.SoundMode.THETA_WAVES, "Theta Waves (6 Hz)")
                103 -> Pair(AmbientSoundPlayer.SoundMode.GAMMA_WAVES, "Gamma Waves (40 Hz)")
                104 -> Pair(AmbientSoundPlayer.SoundMode.DEEP_BROWN_NOISE, "Cozy Brown Noise")
                else -> Pair(AmbientSoundPlayer.SoundMode.ALPHA_WAVES, "Alpha Waves (10 Hz)")
            }
        }

        binding.btnToggleSoundPlay.setOnClickListener {
            val (mode, name) = getSelectedMode()
            AmbientSoundPlayer.play(mode)
            Toast.makeText(context, "Playing $name (Studio Procedural DSP)", Toast.LENGTH_SHORT).show()
        }

        binding.btnStopSound.setOnClickListener {
            AmbientSoundPlayer.stop()
            Toast.makeText(context, "Ambient sound stopped", Toast.LENGTH_SHORT).show()
        }

        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        AnimationUtils.applyDialogAnimation(dialog)
        AnimationUtils.attachTactileFeedback(binding.btnToggleSoundPlay, binding.btnStopSound)
        dialog.show()
    }
}
