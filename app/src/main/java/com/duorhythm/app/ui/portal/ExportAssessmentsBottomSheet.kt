package com.duorhythm.app.ui.portal

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.databinding.BottomSheetExportAssessmentsBinding
import com.duorhythm.app.ui.history.ExportHistoryBottomSheet
import com.duorhythm.app.util.ProfileManager
import com.duorhythm.app.util.ScheduleExportHelper
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip

class ExportAssessmentsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetExportAssessmentsBinding? = null
    private val binding get() = _binding!!
    private var currentGeneratedBitmap: Bitmap? = null
    private val selectedCategories = mutableSetOf<String>()

    override fun getTheme(): Int = com.duorhythm.app.R.style.CustomBottomSheetDialog

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as com.google.android.material.bottomsheet.BottomSheetDialog
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as com.google.android.material.bottomsheet.BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<android.widget.FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            bottomSheet?.background = null
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetExportAssessmentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        val bottomSheet = (dialog as? BottomSheetDialog)?.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.background = null
        if (bottomSheet != null) {
            BottomSheetBehavior.from(bottomSheet).state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        com.duorhythm.app.util.ViewAnimationExtensions.applySpringToAllButtons(view)
        populateDynamicInUseCategories()
        setupListeners()
    }

    private fun populateDynamicInUseCategories() {
        val db = DatabaseHelper.getInstance(requireContext())
        val profileId = ProfileManager.getActiveProfileId()

        // Pure DB query: ONLY fetch categories currently existing in assessments table
        val existingAssessments = db.getAssessments(profileId)
        val distinctUsedTypes = existingAssessments
            .map { it.type.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        binding.chipGroupFilterCategories.removeAllViews()
        selectedCategories.clear()

        if (distinctUsedTypes.isEmpty()) {
            binding.tvNoExportableAssessments.visibility = View.VISIBLE
            binding.btnSaveImage.isEnabled = false
            binding.btnShareExams.isEnabled = false
            binding.ivExamThumbnail.setImageDrawable(null)
            currentGeneratedBitmap = null
            return
        }

        binding.tvNoExportableAssessments.visibility = View.GONE
        binding.btnSaveImage.isEnabled = true
        binding.btnShareExams.isEnabled = true

        // Dynamically add only existing types (e.g. "QT", "Class Test")
        for (category in distinctUsedTypes) {
            selectedCategories.add(category)
            val chip = Chip(requireContext()).apply {
                text = category
                isCheckable = true
                isChecked = true
                setChipBackgroundColorResource(android.R.color.transparent)
                chipStrokeWidth = 2f
                chipStrokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#1F2F4D"))
                setTextColor(Color.WHITE)
                checkedIconTint = android.content.res.ColorStateList.valueOf(Color.parseColor("#00E5FF"))
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedCategories.add(category)
                    } else {
                        selectedCategories.remove(category)
                    }
                    refreshThumbnail()
                }
            }
            binding.chipGroupFilterCategories.addView(chip)
        }

        refreshThumbnail()
    }

    private fun refreshThumbnail() {
        val db = DatabaseHelper.getInstance(requireContext())
        val profileId = ProfileManager.getActiveProfileId()
        var items = db.getAssessments(profileId)

        if (binding.switchOnlyPendingExams.isChecked) {
            items = items.filter { it.status.uppercase() != "COMPLETED" }
        }

        val filtered = items.filter { item ->
            val type = item.type.trim()
            selectedCategories.contains(type)
        }

        if (filtered.isEmpty()) {
            binding.ivExamThumbnail.setImageDrawable(null)
            binding.tvNoExportableAssessments.visibility = View.VISIBLE
            currentGeneratedBitmap = null
            binding.btnSaveImage.isEnabled = false
            binding.btnShareExams.isEnabled = false
            return
        }

        binding.tvNoExportableAssessments.visibility = View.GONE
        binding.btnSaveImage.isEnabled = true
        binding.btnShareExams.isEnabled = true

        try {
            val bmp = ScheduleExportHelper.generateExamsScheduleBitmap(requireContext(), filtered)
            currentGeneratedBitmap = bmp
            binding.ivExamThumbnail.setImageBitmap(bmp)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupListeners() {
        binding.switchOnlyPendingExams.setOnCheckedChangeListener { _, _ ->
            refreshThumbnail()
        }

        binding.btnViewExamHistory.setOnClickListener {
            ExportHistoryBottomSheet().show(parentFragmentManager, "EXPORT_HISTORY")
        }

        binding.btnCancelExamExport.setOnClickListener {
            dismiss()
        }

        binding.btnSaveImage.setOnClickListener {
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
