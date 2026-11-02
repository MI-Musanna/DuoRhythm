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

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.background = null
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetExportExamsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val profileId = ProfileManager.getActiveProfileId()
        val db = DatabaseHelper.getInstance(requireContext())
        val selectedCategories = mutableSetOf<String>()

        fun refreshThumbnail() {
            var items = db.getAssessments(profileId)
            if (binding.switchOnlyPendingExams.isChecked) {
                items = items.filter { it.status.uppercase() != "COMPLETED" }
            }

            val filtered = items.filter { item ->
                val cat = item.category.uppercase()
                if (selectedCategories.isEmpty()) false
                else selectedCategories.any { sel -> cat.contains(sel) || sel.contains(cat) }
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

        fun setupDynamicChips() {
            binding.chipGroupExamCategories.removeAllViews()
            val categories = db.getCustomAssessmentCategories(profileId)
            for (cat in categories) {
                val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                    text = cat
                    isCheckable = true
                    isChecked = true
                    selectedCategories.add(cat.uppercase())
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) selectedCategories.add(cat.uppercase())
                        else selectedCategories.remove(cat.uppercase())
                        refreshThumbnail()
                    }
                    setOnLongClickListener {
                        androidx.appcompat.app.AlertDialog.Builder(requireContext())
                            .setTitle("Delete Category")
                            .setMessage("Delete '$cat' from categories?")
                            .setPositiveButton("Delete") { _, _ ->
                                db.deleteCustomAssessmentCategory(cat)
                                selectedCategories.remove(cat.uppercase())
                                setupDynamicChips()
                                refreshThumbnail()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                        true
                    }
                }
                binding.chipGroupExamCategories.addView(chip)
            }

            // Persistent "+ New" chip
            val newChip = com.google.android.material.chip.Chip(requireContext()).apply {
                text = "+ New"
                isCheckable = false
                setOnClickListener {
                    val input = android.widget.EditText(requireContext()).apply {
                        hint = "Category Name (e.g. Lab Exam)"
                    }
                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("New Assessment Category")
                        .setView(input)
                        .setPositiveButton("Add") { _, _ ->
                            val text = input.text.toString().trim()
                            if (text.isNotEmpty()) {
                                db.addCustomAssessmentCategory(text)
                                selectedCategories.add(text.uppercase())
                                setupDynamicChips()
                                refreshThumbnail()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            binding.chipGroupExamCategories.addView(newChip)
        }

        binding.switchOnlyPendingExams.setOnCheckedChangeListener { _, _ -> refreshThumbnail() }
        setupDynamicChips()
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
