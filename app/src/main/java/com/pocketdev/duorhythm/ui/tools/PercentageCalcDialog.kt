package com.pocketdev.duorhythm.ui.tools

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import com.pocketdev.duorhythm.databinding.DialogPercentageCalcBinding
import java.util.Locale

class PercentageCalcDialog(private val context: Context) {

    fun show() {
        val binding = DialogPercentageCalcBinding.inflate(LayoutInflater.from(context))

        val modes = listOf(
            "1. What is X% of Y?",
            "2. X is what % of Y?",
            "3. Discount: Price X with Y% off",
            "4. Exam Marks: Scored X out of Y",
            "5. % Change: from X to Y"
        )

        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, modes)
        binding.spCalcMode.adapter = adapter

        fun updateHints(position: Int) {
            when (position) {
                0 -> {
                    binding.tilCalcInput1.hint = "Percentage (X %)"
                    binding.tilCalcInput2.hint = "Total Value (Y)"
                }
                1 -> {
                    binding.tilCalcInput1.hint = "Part Value (X)"
                    binding.tilCalcInput2.hint = "Total Value (Y)"
                }
                2 -> {
                    binding.tilCalcInput1.hint = "Original Price ($)"
                    binding.tilCalcInput2.hint = "Discount Percentage (%)"
                }
                3 -> {
                    binding.tilCalcInput1.hint = "Marks Scored (X)"
                    binding.tilCalcInput2.hint = "Total Maximum Marks (Y)"
                }
                4 -> {
                    binding.tilCalcInput1.hint = "Initial Value (X)"
                    binding.tilCalcInput2.hint = "Final Value (Y)"
                }
            }
        }

        binding.spCalcMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateHints(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.btnDoCalculate.setOnClickListener {
            val val1Str = binding.etCalcInput1.text?.toString()?.trim().orEmpty()
            val val2Str = binding.etCalcInput2.text?.toString()?.trim().orEmpty()

            val x = val1Str.toDoubleOrNull()
            val y = val2Str.toDoubleOrNull()

            if (x == null || y == null) {
                Toast.makeText(context, "Please enter valid numbers", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val mode = binding.spCalcMode.selectedItemPosition
            when (mode) {
                0 -> {
                    // What is X% of Y?
                    val result = (x / 100.0) * y
                    binding.tvCalcResult.text = String.format(Locale.US, "%.2f", result)
                    binding.tvCalcResultDetail.text = "$x% of $y is ${String.format(Locale.US, "%.2f", result)}"
                }
                1 -> {
                    // X is what % of Y?
                    if (y == 0.0) {
                        Toast.makeText(context, "Division by zero", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val result = (x / y) * 100.0
                    binding.tvCalcResult.text = String.format(Locale.US, "%.2f%%", result)
                    binding.tvCalcResultDetail.text = "$x is ${String.format(Locale.US, "%.2f%%", result)} of $y"
                }
                2 -> {
                    // Discount: Original price X, Y% off
                    val saved = (y / 100.0) * x
                    val finalPrice = x - saved
                    binding.tvCalcResult.text = String.format(Locale.US, "$%.2f", finalPrice)
                    binding.tvCalcResultDetail.text = "You save $${String.format(Locale.US, "%.2f", saved)} (Discount: $y%)"
                }
                3 -> {
                    // Exam marks: X out of Y
                    if (y == 0.0) {
                        Toast.makeText(context, "Max marks cannot be 0", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val pct = (x / y) * 100.0
                    val grade = when {
                        pct >= 90 -> "Grade: A+ (Outstanding)"
                        pct >= 80 -> "Grade: A (Excellent)"
                        pct >= 70 -> "Grade: B (Good)"
                        pct >= 60 -> "Grade: C (Satisfactory)"
                        pct >= 50 -> "Grade: D (Pass)"
                        else -> "Grade: F (Needs Improvement)"
                    }
                    binding.tvCalcResult.text = String.format(Locale.US, "%.1f%%", pct)
                    binding.tvCalcResultDetail.text = "$grade • Score: $x / $y"
                }
                4 -> {
                    // % Change
                    if (x == 0.0) {
                        Toast.makeText(context, "Initial value cannot be 0", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    val diff = y - x
                    val pctChange = (diff / x) * 100.0
                    val changeType = if (pctChange >= 0) "Increase" else "Decrease"
                    binding.tvCalcResult.text = String.format(Locale.US, "%+.2f%%", pctChange)
                    binding.tvCalcResultDetail.text = "$changeType from $x to $y (Difference: ${String.format(Locale.US, "%+.2f", diff)})"
                }
            }
        }

        updateHints(0)
        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        com.pocketdev.duorhythm.util.AnimationUtils.applyDialogAnimation(dialog)
        com.pocketdev.duorhythm.util.AnimationUtils.attachTactileFeedback(binding.btnDoCalculate)
        dialog.show()
    }
}
