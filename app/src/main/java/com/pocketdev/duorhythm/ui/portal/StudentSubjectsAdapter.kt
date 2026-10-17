package com.pocketdev.duorhythm.ui.portal

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pocketdev.duorhythm.data.model.StudentSubject
import com.pocketdev.duorhythm.data.model.SubjectAttendanceItem
import com.pocketdev.duorhythm.databinding.ItemStudentSubjectBinding

class StudentSubjectsAdapter(
    private var subjects: List<StudentSubject>,
    private var attendanceMap: Map<String, SubjectAttendanceItem> = emptyMap(),
    private var progressMap: Map<Long, Int> = emptyMap(),
    private val onSubjectClick: (StudentSubject) -> Unit,
    private val onDeleteClick: (StudentSubject) -> Unit
) : RecyclerView.Adapter<StudentSubjectsAdapter.SubjectViewHolder>() {

    fun updateList(
        newSubjects: List<StudentSubject>,
        newAttendance: Map<String, SubjectAttendanceItem> = attendanceMap,
        newProgress: Map<Long, Int> = progressMap
    ) {
        subjects = newSubjects
        attendanceMap = newAttendance
        progressMap = newProgress
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubjectViewHolder {
        val binding = ItemStudentSubjectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SubjectViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SubjectViewHolder, position: Int) {
        holder.bind(subjects[position])
    }

    override fun getItemCount(): Int = subjects.size

    inner class SubjectViewHolder(private val binding: ItemStudentSubjectBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(subject: StudentSubject) {
            binding.tvSubjectName.text = subject.name
            binding.tvSubjectCredits.text = "${subject.credits} Credits"
            binding.tvSubjectCode.text = if (subject.code.isNotEmpty()) subject.code else "SUBJECT"

            // Instructor tag
            val instructor = if (subject.instructorName.isNotBlank()) subject.instructorName else "Course Faculty"
            binding.tvInstructorTag.text = instructor

            // Attendance badge & dynamic status
            val att = attendanceMap[subject.name]
            val totalSessions = att?.totalClasses ?: 0
            if (totalSessions > 0) {
                val percentage = (att!!.attendedClasses * 100) / totalSessions
                binding.badgeAttendance.visibility = View.VISIBLE
                binding.tvAttendancePercentage.text = "$percentage% Attendance"
                binding.tvAttendanceRatio.text = "$percentage% Attendance (${att.attendedClasses}/$totalSessions)"

                val bgColor = if (percentage >= 75) Color.parseColor("#064E3B") else Color.parseColor("#4C1D24")
                val textColor = if (percentage >= 75) Color.parseColor("#A7F3D0") else Color.parseColor("#FECDD3")
                val iconColor = if (percentage >= 75) Color.parseColor("#34D399") else Color.parseColor("#F87171")
                val strokeColor = if (percentage >= 75) Color.parseColor("#047857") else Color.parseColor("#991B1B")

                binding.badgeAttendance.setCardBackgroundColor(bgColor)
                binding.badgeAttendance.strokeColor = strokeColor
                binding.tvAttendancePercentage.setTextColor(textColor)
                binding.ivAttendanceIcon.imageTintList = ColorStateList.valueOf(iconColor)
            } else {
                // When no sessions recorded yet:
                binding.badgeAttendance.visibility = View.VISIBLE
                binding.tvAttendancePercentage.text = "--% Attendance"
                binding.tvAttendanceRatio.text = "--% Attendance"
                val ctx = binding.root.context
                val neutralBg = androidx.core.content.ContextCompat.getColor(ctx, com.pocketdev.duorhythm.R.color.badge_neutral_bg)
                val neutralStroke = androidx.core.content.ContextCompat.getColor(ctx, com.pocketdev.duorhythm.R.color.badge_neutral_stroke)
                val neutralText = androidx.core.content.ContextCompat.getColor(ctx, com.pocketdev.duorhythm.R.color.badge_neutral_text)
                binding.badgeAttendance.setCardBackgroundColor(neutralBg)
                binding.badgeAttendance.strokeColor = neutralStroke
                binding.tvAttendancePercentage.setTextColor(neutralText)
                binding.ivAttendanceIcon.imageTintList = ColorStateList.valueOf(neutralText)
            }

            // Dynamic Subject Progress based on completed tasks/assessments
            val progressPercentage = progressMap[subject.id] ?: 0
            binding.pbSubjectProgress.progress = progressPercentage
            binding.tvSubjectProgress.text = "$progressPercentage% Progress"

            binding.tvSubjectNotes.text = if (subject.notes.isNotBlank()) subject.notes else "Syllabus active • Tap to view homework and practicals."

            val parsedColor = try {
                Color.parseColor(subject.colorHex)
            } catch (_: Exception) {
                0xFF2563EB.toInt()
            }
            val density = binding.root.resources.displayMetrics.density
            val pillDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 2f * density
                setColor(parsedColor)
            }
            binding.viewSubjectColor.background = pillDrawable

            binding.root.setOnClickListener {
                onSubjectClick(subject)
            }

            binding.btnDeleteSubject.setOnClickListener {
                onDeleteClick(subject)
            }
        }
    }
}
