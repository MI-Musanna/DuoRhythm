package com.duorhythm.app.ui.schedule

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
import com.duorhythm.app.data.model.ClassEntity
import com.duorhythm.app.databinding.DialogExportScheduleBinding
import com.duorhythm.app.util.ProfileManager
import com.duorhythm.app.util.ScheduleExportHelper

class ExportScheduleDialog : DialogFragment() {
    private var _binding: DialogExportScheduleBinding? = null
    private val binding get() = _binding!!
    private var currentGeneratedBitmap: Bitmap? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        _binding = DialogExportScheduleBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)

        val dayChips = listOf(
            Pair(binding.chipSat, "Saturday" to 7),
            Pair(binding.chipSun, "Sunday" to 1),
            Pair(binding.chipMon, "Monday" to 2),
            Pair(binding.chipTue, "Tuesday" to 3),
            Pair(binding.chipWed, "Wednesday" to 4),
            Pair(binding.chipThu, "Thursday" to 5)
        )

        fun refreshThumbnail() {
            val selectedDays = dayChips.filter { it.first.isChecked }.map { it.second }
            if (selectedDays.isEmpty()) {
                binding.ivScheduleThumbnail.setImageDrawable(null)
                currentGeneratedBitmap = null
                return
            }
            val profileId = ProfileManager.getActiveProfileId()
            val db = DatabaseHelper.getInstance(requireContext())
            val exportMap = linkedMapOf<String, List<ClassEntity>>()
            for ((dayName, dayInt) in selectedDays) {
                exportMap[dayName] = db.getClasses(profileId, dayInt)
            }
            val bmp = ScheduleExportHelper.generateWeeklyScheduleBitmap(requireContext(), exportMap)
            currentGeneratedBitmap = bmp
            binding.ivScheduleThumbnail.setImageBitmap(bmp)
        }

        binding.switchSelectAllDays.setOnCheckedChangeListener { _, isChecked ->
            dayChips.forEach { it.first.isChecked = isChecked }
            refreshThumbnail()
        }

        dayChips.forEach { (chip, _) ->
            chip.setOnCheckedChangeListener { _, _ ->
                refreshThumbnail()
            }
        }

        refreshThumbnail()

        binding.btnCancelScheduleExport.setOnClickListener { dismiss() }

        binding.btnSaveSchedule.setOnClickListener {
            val bmp = currentGeneratedBitmap
            if (bmp == null) {
                Toast.makeText(requireContext(), "Select at least one day", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ScheduleExportHelper.saveBitmapToGallery(requireContext(), bmp, "DuoRhythm_Timetable.png")
            dismiss()
        }

        binding.btnShareSchedule.setOnClickListener {
            val bmp = currentGeneratedBitmap
            if (bmp == null) {
                Toast.makeText(requireContext(), "Select at least one day", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ScheduleExportHelper.shareBitmapViaIntent(requireContext(), bmp, "DuoRhythm Academic Timetable")
            dismiss()
        }

        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
