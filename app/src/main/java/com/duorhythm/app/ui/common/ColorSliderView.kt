package com.duorhythm.app.ui.common

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.duorhythm.app.R
import com.duorhythm.app.databinding.DialogColorPickerBinding
import java.util.Locale

class ColorSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val previewCircle: View
    private val etHexCode: EditText
    private val sbHue: SeekBar
    private val sbSaturation: SeekBar
    private val sbValue: SeekBar

    private var currentHue = 200f
    private var currentSaturation = 0.8f
    private var currentValue = 0.9f
    private var isUpdatingInternally = false

    private var onColorChanged: ((Int, String) -> Unit)? = null

    init {
        orientation = VERTICAL
        val dp = { value: Int -> (value * context.resources.displayMetrics.density).toInt() }

        // 1. Preview Circle & Hex input row
        val previewRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }

        previewCircle = View(context).apply {
            layoutParams = LayoutParams(dp(44), dp(44)).apply {
                marginEnd = dp(14)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#0284C7"))
                setStroke(dp(2), Color.WHITE)
            }
        }
        previewRow.addView(previewCircle)

        etHexCode = EditText(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(44))
            setText("#0284C7")
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(dp(12), dp(8), dp(12), dp(8))
            filters = arrayOf(InputFilter.LengthFilter(7))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), Color.parseColor("#334155"))
            }
        }
        previewRow.addView(etHexCode)
        addView(previewRow)

        // 2. Hue Slider (0-360)
        val tvHue = TextView(context).apply {
            text = "Hue (Spectrum)"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            setPadding(0, dp(4), 0, dp(2))
        }
        addView(tvHue)

        sbHue = SeekBar(context).apply {
            max = 360
            progress = 200
            val rainbow = GradientDrawable(
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
                cornerRadius = dp(6).toFloat()
            }
            progressDrawable = rainbow
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        addView(sbHue)

        // 3. Saturation Slider (0-100)
        val tvSat = TextView(context).apply {
            text = "Saturation (Vibrancy)"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            setPadding(0, dp(8), 0, dp(2))
        }
        addView(tvSat)

        sbSaturation = SeekBar(context).apply {
            max = 100
            progress = 80
            val satBg = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#334155"))
            }
            progressDrawable = satBg
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        addView(sbSaturation)

        // 4. Lightness / Value Slider (0-100)
        val tvVal = TextView(context).apply {
            text = "Brightness / Value"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
            setPadding(0, dp(8), 0, dp(2))
        }
        addView(tvVal)

        sbValue = SeekBar(context).apply {
            max = 100
            progress = 90
            val valBg = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(Color.parseColor("#334155"))
            }
            progressDrawable = valBg
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        addView(sbValue)

        // Listeners for SeekBars
        val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || isUpdatingInternally) return
                currentHue = sbHue.progress.toFloat()
                currentSaturation = sbSaturation.progress / 100f
                currentValue = sbValue.progress / 100f
                updateFromHsv(updateText = true)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        sbHue.setOnSeekBarChangeListener(seekBarListener)
        sbSaturation.setOnSeekBarChangeListener(seekBarListener)
        sbValue.setOnSeekBarChangeListener(seekBarListener)

        // Listener for manual Hex EditText input
        etHexCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isUpdatingInternally) return
                val text = s?.toString()?.trim().orEmpty()
                if (text.length == 7 && text.startsWith("#")) {
                    try {
                        val parsed = Color.parseColor(text)
                        val hsv = FloatArray(3)
                        Color.colorToHSV(parsed, hsv)
                        currentHue = hsv[0]
                        currentSaturation = hsv[1]
                        currentValue = hsv[2]
                        updateFromHsv(updateText = false)
                    } catch (_: Exception) {}
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        updateFromHsv(updateText = true)
    }

    private fun updateFromHsv(updateText: Boolean) {
        val colorInt = Color.HSVToColor(floatArrayOf(currentHue, currentSaturation, currentValue))
        val hex = String.format(Locale.US, "#%06X", 0xFFFFFF and colorInt)

        (previewCircle.background as? GradientDrawable)?.setColor(colorInt)

        if (updateText && !etHexCode.hasFocus()) {
            isUpdatingInternally = true
            etHexCode.setText(hex)
            sbHue.progress = currentHue.toInt().coerceIn(0, 360)
            sbSaturation.progress = (currentSaturation * 100).toInt().coerceIn(0, 100)
            sbValue.progress = (currentValue * 100).toInt().coerceIn(0, 100)
            isUpdatingInternally = false
        }

        onColorChanged?.invoke(colorInt, hex)
    }

    fun setOnColorChangedListener(listener: (Int, String) -> Unit) {
        this.onColorChanged = listener
    }

    fun setColorHex(hex: String) {
        try {
            val cleanHex = if (hex.startsWith("#")) hex else "#$hex"
            val parsed = Color.parseColor(cleanHex)
            val hsv = FloatArray(3)
            Color.colorToHSV(parsed, hsv)
            currentHue = hsv[0]
            currentSaturation = hsv[1]
            currentValue = hsv[2]

            isUpdatingInternally = true
            sbHue.progress = currentHue.toInt().coerceIn(0, 360)
            sbSaturation.progress = (currentSaturation * 100).toInt().coerceIn(0, 100)
            sbValue.progress = (currentValue * 100).toInt().coerceIn(0, 100)
            etHexCode.setText(String.format(Locale.US, "#%06X", 0xFFFFFF and parsed))
            (previewCircle.background as? GradientDrawable)?.setColor(parsed)
            isUpdatingInternally = false

            onColorChanged?.invoke(parsed, getColorHex())
        } catch (_: Exception) {}
    }

    fun getColorHex(): String {
        val colorInt = Color.HSVToColor(floatArrayOf(currentHue, currentSaturation, currentValue))
        return String.format(Locale.US, "#%06X", 0xFFFFFF and colorInt)
    }

    companion object {
        fun showDialog(context: Context, initialHex: String = "#0284C7", onSelected: (String) -> Unit) {
            val binding = DialogColorPickerBinding.inflate(LayoutInflater.from(context))
            var chosenHex = if (initialHex.startsWith("#")) initialHex else "#$initialHex"

            binding.colorSliderView.setColorHex(chosenHex)
            binding.colorSliderView.setOnColorChangedListener { _, hex ->
                chosenHex = hex
            }

            val dialog = AlertDialog.Builder(context)
                .setView(binding.root)
                .create()

            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            binding.btnCancelColor.setOnClickListener {
                dialog.dismiss()
            }

            binding.btnSelectColor.setOnClickListener {
                onSelected(chosenHex)
                dialog.dismiss()
            }

            dialog.show()
        }
    }
}
