package com.duorhythm.app.ui.common

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.duorhythm.app.R
import com.duorhythm.app.databinding.DialogCustomColorPickerBinding
import java.util.Locale

object ColorPickerHelper {

    data class ColorPreset(val hex: String, val name: String)

    val PRESET_PALETTE: List<ColorPreset> = listOf(
        ColorPreset("#EF4444", "Coral Red"),
        ColorPreset("#E11D48", "Crimson"),
        ColorPreset("#EC4899", "Pink"),
        ColorPreset("#A855F7", "Purple"),
        ColorPreset("#6366F1", "Deep Indigo"),
        ColorPreset("#2563EB", "Royal Blue"),
        ColorPreset("#0EA5E9", "Sky Blue"),
        ColorPreset("#06B6D4", "Cyan"),
        ColorPreset("#14B8A6", "Teal"),
        ColorPreset("#10B981", "Emerald"),
        ColorPreset("#34D399", "Mint"),
        ColorPreset("#F59E0B", "Amber"),
        ColorPreset("#F97316", "Sunset Orange"),
        ColorPreset("#EA580C", "Deep Orange"),
        ColorPreset("#64748B", "Slate Gray"),
        ColorPreset("#EAB308", "Gold")
    )

    fun showCustomColorPicker(
        context: Context,
        initialColorHex: String = "#EF4444",
        onColorSelected: (String) -> Unit
    ) {
        val binding = DialogCustomColorPickerBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .create()

        var currentHex = if (initialColorHex.startsWith("#")) initialColorHex else "#$initialColorHex"
        var parsed = try {
            Color.parseColor(currentHex)
        } catch (_: Exception) {
            Color.parseColor("#EF4444")
        }

        var r = Color.red(parsed)
        var g = Color.green(parsed)
        var b = Color.blue(parsed)

        var isUpdatingFromSlider = false
        var isUpdatingFromText = false

        fun updateUi(updateText: Boolean = true, updateSliders: Boolean = true) {
            val color = Color.rgb(r, g, b)
            val hexString = String.format(Locale.US, "#%02X%02X%02X", r, g, b)
            currentHex = hexString

            binding.viewColorPreview.setBackgroundColor(color)
            binding.btnApplyColor.backgroundTintList = ColorStateList.valueOf(color)

            binding.tvRedValue.text = r.toString()
            binding.tvGreenValue.text = g.toString()
            binding.tvBlueValue.text = b.toString()

            if (updateSliders) {
                binding.seekRed.progress = r
                binding.seekGreen.progress = g
                binding.seekBlue.progress = b
            }

            if (updateText && !isUpdatingFromText) {
                isUpdatingFromSlider = true
                binding.etHexCode.setText(hexString)
                binding.etHexCode.setSelection(hexString.length)
                isUpdatingFromSlider = false
            }
        }

        updateUi(updateText = true, updateSliders = true)

        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    when (seekBar?.id) {
                        R.id.seekRed -> r = progress
                        R.id.seekGreen -> g = progress
                        R.id.seekBlue -> b = progress
                    }
                    updateUi(updateText = true, updateSliders = false)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        binding.seekRed.setOnSeekBarChangeListener(seekListener)
        binding.seekGreen.setOnSeekBarChangeListener(seekListener)
        binding.seekBlue.setOnSeekBarChangeListener(seekListener)

        binding.etHexCode.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isUpdatingFromSlider) return
                val text = s?.toString()?.trim().orEmpty()
                val candidate = if (text.startsWith("#")) text else "#$text"
                if (candidate.length == 7 && candidate.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
                    try {
                        val parsedColor = Color.parseColor(candidate)
                        r = Color.red(parsedColor)
                        g = Color.green(parsedColor)
                        b = Color.blue(parsedColor)
                        isUpdatingFromText = true
                        updateUi(updateText = false, updateSliders = true)
                        isUpdatingFromText = false
                    } catch (_: Exception) {}
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnCancelColor.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnApplyColor.setOnClickListener {
            onColorSelected(currentHex)
            dialog.dismiss()
        }

        dialog.show()
    }

    /**
     * Dynamically populates a horizontal color palette with 16 preset colors + "+ Custom Color" button.
     */
    fun setupPaletteRow(
        context: Context,
        container: LinearLayout,
        initialColor: String = "#06B6D4",
        onColorSelected: (String) -> Unit
    ): (String) -> Unit {
        container.removeAllViews()
        val dp = { value: Int -> (value * context.resources.displayMetrics.density).toInt() }

        var activeColor = initialColor
        val colorViews = mutableListOf<Triple<String, View, ImageView>>()

        fun refreshSelection() {
            for ((hex, ring, check) in colorViews) {
                val isSelected = hex.equals(activeColor, ignoreCase = true)
                ring.visibility = if (isSelected) View.VISIBLE else View.GONE
                check.visibility = if (isSelected) View.VISIBLE else View.GONE
            }
        }

        for (preset in PRESET_PALETTE) {
            val frame = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                    marginEnd = dp(8)
                }
                isClickable = true
                isFocusable = true
            }

            val ring = View(context).apply {
                layoutParams = FrameLayout.LayoutParams(dp(40), dp(40))
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setStroke(dp(2), Color.parseColor(preset.hex))
                    setColor(Color.TRANSPARENT)
                }
                background = bg
                visibility = View.GONE
            }

            val fill = View(context).apply {
                layoutParams = FrameLayout.LayoutParams(dp(30), dp(30)).apply {
                    gravity = Gravity.CENTER
                }
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(preset.hex))
                }
                background = bg
            }

            val check = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(dp(16), dp(16)).apply {
                    gravity = Gravity.CENTER
                }
                setImageResource(R.drawable.ic_check)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
                visibility = View.GONE
            }

            frame.addView(ring)
            frame.addView(fill)
            frame.addView(check)

            frame.setOnClickListener {
                activeColor = preset.hex
                refreshSelection()
                onColorSelected(activeColor)
            }

            container.addView(frame)
            colorViews.add(Triple(preset.hex, ring, check))
        }

        // Add Stylish Rainbow / Custom Palette Button at the end
        val customFrame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply {
                marginEnd = dp(8)
            }
            isClickable = true
            isFocusable = true
        }

        val customRing = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(dp(40), dp(40))
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(2), ContextCompat.getColor(context, R.color.primary))
                setColor(Color.TRANSPARENT)
            }
            background = bg
            visibility = View.GONE
        }

        val customFill = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(dp(30), dp(30)).apply {
                gravity = Gravity.CENTER
            }
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1E293B"))
                setStroke(dp(1), ContextCompat.getColor(context, R.color.card_stroke))
            }
            background = bg
        }

        val customIcon = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(dp(18), dp(18)).apply {
                gravity = Gravity.CENTER
            }
            setImageResource(R.drawable.ic_palette)
            imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.primary))
        }

        customFill.addView(customIcon)
        customFrame.addView(customRing)
        customFrame.addView(customFill)

        customFrame.setOnClickListener {
            showCustomColorPicker(context, activeColor) { chosenHex ->
                activeColor = chosenHex
                refreshSelection()
                val isPreset = PRESET_PALETTE.any { it.hex.equals(chosenHex, ignoreCase = true) }
                customRing.visibility = if (!isPreset) View.VISIBLE else View.GONE
                onColorSelected(chosenHex)
            }
        }

        container.addView(customFrame)

        refreshSelection()
        val isPreset = PRESET_PALETTE.any { it.hex.equals(activeColor, ignoreCase = true) }
        customRing.visibility = if (!isPreset) View.VISIBLE else View.GONE

        return { newSelection ->
            activeColor = newSelection
            refreshSelection()
            val hasPreset = PRESET_PALETTE.any { it.hex.equals(activeColor, ignoreCase = true) }
            customRing.visibility = if (!hasPreset) View.VISIBLE else View.GONE
        }
    }
}
