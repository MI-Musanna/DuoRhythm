package com.duorhythm.app.ui.portal

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Window
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.databinding.DialogExportExamsBinding
import com.duorhythm.app.util.ProfileManager
import com.duorhythm.app.util.ScheduleExportHelper

class ExportExamsDialog : DialogFragment() {
    private var _binding: DialogExportExamsBinding? = null
    private val binding get() = _binding!!
    private var currentGeneratedBitmap: Bitmap? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        _binding = DialogExportExamsBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)

        fun refreshThumbnail() {
            val profileId = ProfileManager.getActiveProfileId()
            val db = DatabaseHelper.getInstance(requireContext())
            var items = db.getAssessments(profileId)

            if (binding.switchOnlyPendingExams.isChecked) {
                items = items.filter { it.status.uppercase() != "COMPLETED" }
            }

            val incCT = binding.chipCatCT.isChecked
            val incMid = binding.chipCatMidterm.isChecked
            val incFinal = binding.chipCatFinals.isChecked
            val incAssign = binding.chipCatAssignment.isChecked

            val filtered = items.filter { item ->
                val cat = item.category.uppercase()
                (incCT && (cat.contains("CT") || cat.contains("QUIZ") || cat.contains("QT"))) ||
                (incMid && cat.contains("MID")) ||
                (incFinal && cat.contains("FINAL")) ||
                (incAssign && cat.contains("ASSIGNMENT"))
            }

            if (filtered.isEmpty()) {
                binding.ivExamThumbnail.setImageDrawable(null)
                currentGeneratedBitmap = null
                return
            }

            val bmp = ScheduleExportHelper.generateExamsScheduleBitmap(requireContext(), filtered)
            currentGeneratedBitmap = bmp
            binding.ivExamThumbnail.setImageBitmap(bmp)
        }

        binding.switchOnlyPendingExams.setOnCheckedChangeListener { _, _ -> refreshThumbnail() }
        binding.chipCatCT.setOnCheckedChangeListener { _, _ -> refreshThumbnail() }
        binding.chipCatMidterm.setOnCheckedChangeListener { _, _ -> refreshThumbnail() }
        binding.chipCatFinals.setOnCheckedChangeListener { _, _ -> refreshThumbnail() }
        binding.chipCatAssignment.setOnCheckedChangeListener { _, _ -> refreshThumbnail() }

        refreshThumbnail()

        binding.btnCancelExamExport.setOnClickListener { dismiss() }

        binding.btnSaveExams.setOnClickListener {
            val bmp = currentGeneratedBitmap
            if (bmp == null) {
                Toast.makeText(requireContext(), "No matching assessments to export", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ScheduleExportHelper.saveBitmapToGallery(requireContext(), bmp, "DuoRhythm_Exams.png")
            dismiss()
        }

        binding.btnShareExams.setOnClickListener {
            val bmp = currentGeneratedBitmap
            if (bmp == null) {
                Toast.makeText(requireContext(), "No matching assessments to export", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ScheduleExportHelper.shareBitmapViaIntent(requireContext(), bmp, "DuoRhythm Upcoming Exams")
            dismiss()
        }

        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
