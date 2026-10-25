package com.duorhythm.app.ui.dialogs

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.SeekBar
import com.duorhythm.app.databinding.DialogCustomColorPickerBinding
import java.util.Locale

class CustomColorPickerDialog(
    private val context: Context,
    private val initialColorHex: String = "#06B6D4",
    private val onColorSelectedCallback: (String) -> Unit
) {

    private var dialog: AlertDialog? = null

    fun show() {
        val binding = DialogCustomColorPickerBinding.inflate(LayoutInflater.from(context))
        dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .create()

        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // Set rainbow track on hueSeekBar programmatically for smooth 7-color rainbow
        val rainbowGradient = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(
                Color.RED,
                Color.YELLOW,
                Color.GREEN,
                Color.CYAN,
                Color.BLUE,
                Color.MAGENTA,
                Color.RED
            )
        ).apply {
            cornerRadius = 7f * context.resources.displayMetrics.density
        }
        binding.hueSeekBar.progressDrawable = rainbowGradient

        val parsedInitialColor = try {
            Color.parseColor(if (initialColorHex.startsWith("#")) initialColorHex else "#$initialColorHex")
        } catch (_: Exception) {
            Color.parseColor("#06B6D4")
        }

        val initialHsv = FloatArray(3)
        Color.colorToHSV(parsedInitialColor, initialHsv)

        binding.hueSeekBar.progress = initialHsv[0].toInt().coerceIn(0, 360)
        binding.colorSpectrumView.setColor(parsedInitialColor)

        val activeColor = binding.colorSpectrumView.getCurrentColor()
        binding.viewSelectedColorPreview.backgroundTintList = ColorStateList.valueOf(activeColor)
        val initialFormattedHex = String.format(Locale.US, "#%06X", 0xFFFFFF and activeColor)
        binding.etHexCode.setText(initialFormattedHex)

        // 1. When hueSeekBar changes:
        binding.hueSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val newHue = progress.toFloat()
                binding.colorSpectrumView.hue = newHue
                val current = binding.colorSpectrumView.getCurrentColor()
                binding.viewSelectedColorPreview.backgroundTintList = ColorStateList.valueOf(current)
                if (!binding.etHexCode.hasFocus()) {
                    binding.etHexCode.setText(String.format(Locale.US, "#%06X", 0xFFFFFF and current))
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 2. When colorSpectrumView changes via touch:
        binding.colorSpectrumView.setOnColorChangedListener { current ->
            binding.viewSelectedColorPreview.backgroundTintList = ColorStateList.valueOf(current)
            if (!binding.etHexCode.hasFocus()) {
                binding.etHexCode.setText(String.format(Locale.US, "#%06X", 0xFFFFFF and current))
            }
        }

        // 3. When etHexCode is edited manually by the user:
        binding.etHexCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = s?.toString()?.trim().orEmpty()
                if (text.length == 7 && text.startsWith("#")) {
                    try {
                        val parsedColor = Color.parseColor(text)
                        val hsv = FloatArray(3)
                        Color.colorToHSV(parsedColor, hsv)
                        binding.hueSeekBar.progress = hsv[0].toInt().coerceIn(0, 360)
                        binding.colorSpectrumView.setColor(parsedColor)
                        binding.viewSelectedColorPreview.backgroundTintList = ColorStateList.valueOf(parsedColor)
                    } catch (_: Exception) {
                        // Invalid hex input ignored until complete
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 4. On btnApplyColor click:
        binding.btnApplyColor.setOnClickListener {
            val finalHex = String.format(Locale.US, "#%06X", 0xFFFFFF and binding.colorSpectrumView.getCurrentColor())
            onColorSelectedCallback(finalHex)
            dismiss()
        }

        binding.btnCancelColor.setOnClickListener {
            dismiss()
        }

        dialog?.show()
    }

    fun dismiss() {
        dialog?.dismiss()
    }

    companion object {
        fun show(
            context: Context,
            initialColorHex: String = "#06B6D4",
            onColorSelectedCallback: (String) -> Unit
        ): CustomColorPickerDialog {
            val picker = CustomColorPickerDialog(context, initialColorHex, onColorSelectedCallback)
            picker.show()
            return picker
        }
    }
}
