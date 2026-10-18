package com.duorhythm.app.ui.tools

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.SeekBar
import android.widget.Toast
import com.duorhythm.app.R
import com.duorhythm.app.audio.AmbientSoundPlayer
import com.duorhythm.app.databinding.DialogAmbientSoundBinding

class AmbientSoundDialog(private val context: Context) {

    fun show() {
        val binding = DialogAmbientSoundBinding.inflate(LayoutInflater.from(context))

        when (AmbientSoundPlayer.currentMode) {
            AmbientSoundPlayer.SoundMode.OCEAN_WAVES -> binding.rbWavesSound.isChecked = true
            AmbientSoundPlayer.SoundMode.ZEN_BOWL -> binding.rbZenBowl.isChecked = true
            AmbientSoundPlayer.SoundMode.ALPHA_FOCUS_PAD -> binding.rbAlphaPad.isChecked = true
            AmbientSoundPlayer.SoundMode.DEEP_BROWN_NOISE -> binding.rbBrownNoise.isChecked = true
            else -> binding.rbRainSound.isChecked = true
        }

        binding.sbVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                AmbientSoundPlayer.setVolume(progress / 100f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        fun getSelectedMode(): AmbientSoundPlayer.SoundMode {
            return when (binding.rgSoundModes.checkedRadioButtonId) {
                R.id.rbWavesSound -> AmbientSoundPlayer.SoundMode.OCEAN_WAVES
                R.id.rbZenBowl -> AmbientSoundPlayer.SoundMode.ZEN_BOWL
                R.id.rbAlphaPad -> AmbientSoundPlayer.SoundMode.ALPHA_FOCUS_PAD
                R.id.rbBrownNoise -> AmbientSoundPlayer.SoundMode.DEEP_BROWN_NOISE
                else -> AmbientSoundPlayer.SoundMode.RAIN
            }
        }

        binding.btnToggleSoundPlay.setOnClickListener {
            val mode = getSelectedMode()
            AmbientSoundPlayer.play(mode)
            val name = when (mode) {
                AmbientSoundPlayer.SoundMode.RAIN -> "Gentle Rain & Stream"
                AmbientSoundPlayer.SoundMode.OCEAN_WAVES -> "Ocean Night Waves"
                AmbientSoundPlayer.SoundMode.ZEN_BOWL -> "Tibetan Singing Bowl Chimes"
                AmbientSoundPlayer.SoundMode.ALPHA_FOCUS_PAD -> "Deep Alpha Focus Pad (10Hz)"
                AmbientSoundPlayer.SoundMode.DEEP_BROWN_NOISE -> "Cozy Brown Noise (Waterfall)"
                AmbientSoundPlayer.SoundMode.OFF -> "Off"
            }
            Toast.makeText(context, "Playing $name (Studio Procedural DSP)", Toast.LENGTH_SHORT).show()
        }

        binding.btnStopSound.setOnClickListener {
            AmbientSoundPlayer.stop()
            Toast.makeText(context, "Ambient sound stopped", Toast.LENGTH_SHORT).show()
        }

        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        com.duorhythm.app.util.AnimationUtils.applyDialogAnimation(dialog)
        com.duorhythm.app.util.AnimationUtils.attachTactileFeedback(binding.btnToggleSoundPlay, binding.btnStopSound)
        dialog.show()
    }
}
