package com.duorhythm.app.ui.portal

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.databinding.BottomSheetExportExamsBinding
import com.duorhythm.app.ui.history.ExportHistoryBottomSheet
import com.duorhythm.app.util.ProfileManager
import com.duorhythm.app.util.ScheduleExportHelper
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ExportExamsBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetExportExamsBinding? = null
    private val binding get() = _binding!!
    private var currentGeneratedBitmap: Bitmap? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetExportExamsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val profileId = ProfileManager.getActiveProfileId()
        val db = DatabaseHelper.getInstance(requireContext())

        fun refreshThumbnail() {
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

        binding.btnViewExamHistory.setOnClickListener {
            ExportHistoryBottomSheet().show(parentFragmentManager, "EXPORT_HISTORY")
        }

        binding.btnCancelExamExport.setOnClickListener { dismiss() }

        binding.btnSaveExams.setOnClickListener {
            val bmp = currentGeneratedBitmap
            if (bmp == null) {
                Toast.makeText(requireContext(), "No matching assessments to export", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val format = if (binding.switchExamFormatJpeg.isChecked) {
                ScheduleExportHelper.ExportImageFormat.JPEG
            } else {
                ScheduleExportHelper.ExportImageFormat.PNG
            }
            ScheduleExportHelper.saveBitmapToGallery(requireContext(), bmp, "DuoRhythm_Exams", format)
            dismiss()
        }

        binding.btnShareExams.setOnClickListener {
            val bmp = currentGeneratedBitmap
            if (bmp == null) {
                Toast.makeText(requireContext(), "No matching assessments to export", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val format = if (binding.switchExamFormatJpeg.isChecked) {
                ScheduleExportHelper.ExportImageFormat.JPEG
            } else {
                ScheduleExportHelper.ExportImageFormat.PNG
            }
            ScheduleExportHelper.shareBitmapViaIntent(requireContext(), bmp, "DuoRhythm Upcoming Exams", format)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
