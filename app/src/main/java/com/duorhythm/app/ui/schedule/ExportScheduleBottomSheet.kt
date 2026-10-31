package com.duorhythm.app.ui.schedule

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.ClassEntity
import com.duorhythm.app.databinding.BottomSheetExportScheduleBinding
import com.duorhythm.app.ui.history.ExportHistoryBottomSheet
import com.duorhythm.app.util.ProfileManager
import com.duorhythm.app.util.ScheduleExportHelper
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ExportScheduleBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetExportScheduleBinding? = null
    private val binding get() = _binding!!
    private var currentGeneratedBitmap: Bitmap? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetExportScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val profileId = ProfileManager.getActiveProfileId()
        val db = DatabaseHelper.getInstance(requireContext())

        // Ordered starting with Saturday as First Day of the Week
        val dayChips = listOf(
            Pair(binding.chipSat, "Saturday"),
            Pair(binding.chipSun, "Sunday"),
            Pair(binding.chipMon, "Monday"),
            Pair(binding.chipTue, "Tuesday"),
            Pair(binding.chipWed, "Wednesday"),
            Pair(binding.chipThu, "Thursday")
        )

        fun refreshThumbnail() {
            val selectedDays = dayChips.filter { it.first.isChecked }.map { it.second }
            if (selectedDays.isEmpty()) {
                binding.ivScheduleThumbnail.setImageDrawable(null)
                currentGeneratedBitmap = null
                return
            }
            val exportMap = linkedMapOf<String, List<ClassEntity>>()
            for (dayName in selectedDays) {
                exportMap[dayName] = db.getClassesByDayName(profileId, dayName)
            }
            val bmp = ScheduleExportHelper.generateWeeklyScheduleBitmap(requireContext(), exportMap)
            currentGeneratedBitmap = bmp
            binding.ivScheduleThumbnail.setImageBitmap(bmp)
        }

        fun applyAutoSelection() {
            dayChips.forEach { (chip, dayName) ->
                val classCount = db.getClassesByDayName(profileId, dayName).size
                chip.isChecked = classCount > 0
            }
            refreshThumbnail()
        }

        applyAutoSelection()

        binding.switchOnlyClassDays.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                applyAutoSelection()
            } else {
                dayChips.forEach { it.first.isChecked = true }
                refreshThumbnail()
            }
        }

        dayChips.forEach { (chip, _) ->
            chip.setOnCheckedChangeListener { _, _ -> refreshThumbnail() }
        }

        binding.btnViewHistory.setOnClickListener {
            ExportHistoryBottomSheet().show(parentFragmentManager, "EXPORT_HISTORY")
        }

        binding.btnCancelScheduleExport.setOnClickListener { dismiss() }

        binding.btnSaveSchedule.setOnClickListener {
            val bmp = currentGeneratedBitmap
            if (bmp == null) {
                Toast.makeText(requireContext(), "Select at least one day", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val format = if (binding.switchScheduleFormatJpeg.isChecked) {
                ScheduleExportHelper.ExportImageFormat.JPEG
            } else {
                ScheduleExportHelper.ExportImageFormat.PNG
            }
            ScheduleExportHelper.saveBitmapToGallery(requireContext(), bmp, "DuoRhythm_Timetable", format)
            dismiss()
        }

        binding.btnShareSchedule.setOnClickListener {
            val bmp = currentGeneratedBitmap
            if (bmp == null) {
                Toast.makeText(requireContext(), "Select at least one day", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val format = if (binding.switchScheduleFormatJpeg.isChecked) {
                ScheduleExportHelper.ExportImageFormat.JPEG
            } else {
                ScheduleExportHelper.ExportImageFormat.PNG
            }
            ScheduleExportHelper.shareBitmapViaIntent(requireContext(), bmp, "DuoRhythm Academic Timetable", format)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
