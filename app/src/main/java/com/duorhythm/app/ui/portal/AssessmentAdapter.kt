package com.duorhythm.app.ui.portal

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.R
import com.duorhythm.app.data.model.SubjectWorkItem
import com.duorhythm.app.databinding.ItemAssessmentCardBinding
import com.duorhythm.app.util.FileUtils
import com.google.android.material.snackbar.Snackbar
import database.entities.AssessmentEntity

class AssessmentAdapter(
    private var items: List<AssessmentEntity> = emptyList(),
    private val onToggleStatus: ((AssessmentEntity, String) -> Unit)? = null,
    private val onDeleteClick: ((AssessmentEntity) -> Unit)? = null,
    private val onItemClick: ((AssessmentEntity) -> Unit)? = null
) : RecyclerView.Adapter<AssessmentAdapter.ViewHolder>() {

    var onFilterApplied: ((List<AssessmentEntity>) -> Unit)? = null

    val masterItemCount: Int get() = masterList.size

    private var masterList: List<AssessmentEntity> = emptyList()
    private var currentCategory: String = "All"
    private var currentSearchQuery: String = ""

    fun setMasterData(list: List<AssessmentEntity>) {
        masterList = list
        applyFilters()
    }

    fun updateCategoryFilter(category: String) {
        currentCategory = category
        applyFilters()
    }

    fun updateSearchQuery(query: String) {
        currentSearchQuery = query.lowercase().trim()
        applyFilters()
    }

    private fun applyFilters() {
        val filtered = masterList.filter { item ->
            val matchesCategory = when (currentCategory) {
                "All" -> true
                "Finals" -> item.category.contains("Final", ignoreCase = true)
                "Midterms" -> item.category.contains("Midterm", ignoreCase = true)
                "Quizzes / CT" -> item.category.contains("CT", ignoreCase = true) || item.category.contains("Quiz", ignoreCase = true) || item.category.contains("QT", ignoreCase = true)
                "Lab Exams" -> item.category.contains("Lab", ignoreCase = true)
                "Viva" -> item.category.contains("Viva", ignoreCase = true)
                else -> item.category.equals(currentCategory, ignoreCase = true)
            }

            val matchesQuery = if (currentSearchQuery.isEmpty()) {
                true
            } else {
                item.title.lowercase().contains(currentSearchQuery) ||
                item.courseCode.lowercase().contains(currentSearchQuery) ||
                (item.syllabus?.lowercase()?.contains(currentSearchQuery) == true) ||
                (item.room?.lowercase()?.contains(currentSearchQuery) == true) ||
                (item.instructor?.lowercase()?.contains(currentSearchQuery) == true)
            }

            matchesCategory && matchesQuery
        }
        submitList(ArrayList(filtered))
        onFilterApplied?.invoke(filtered)
    }

    fun updateList(newItems: List<AssessmentEntity>) {
        setMasterData(newItems)
    }

    @JvmName("updateSubjectWorkItemList")
    fun updateList(newItems: List<SubjectWorkItem>) {
        setMasterData(newItems.map { AssessmentEntity.fromSubjectWorkItem(it) })
    }

    fun submitList(list: List<AssessmentEntity>?, commitCallback: (() -> Unit)? = null) {
        items = if (list != null) ArrayList(list) else emptyList()
        notifyDataSetChanged()
        commitCallback?.invoke()
    }

    @JvmName("submitSubjectWorkItemList")
    fun submitList(list: List<SubjectWorkItem>?) {
        setMasterData(list?.map { AssessmentEntity.fromSubjectWorkItem(it) } ?: emptyList())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAssessmentCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(val binding: ItemAssessmentCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AssessmentEntity) {
            val workItem = item.toSubjectWorkItem()
            val context = binding.root.context

            val typePrefix = when {
                item.category.contains("Final", ignoreCase = true) || workItem.examType.contains("Final", ignoreCase = true) -> "SEMESTER FINAL"
                item.category.contains("Midterm", ignoreCase = true) || workItem.examType.contains("Midterm", ignoreCase = true) -> "MIDTERM EXAM"
                item.category.contains("CT", ignoreCase = true) || item.category.contains("Class Test", ignoreCase = true) || workItem.examType.contains("CT", ignoreCase = true) || workItem.examType.contains("Class Test", ignoreCase = true) -> "CLASS TEST (CT)"
                item.category.contains("Quiz", ignoreCase = true) || workItem.examType.contains("Quiz", ignoreCase = true) -> "QUIZ"
                item.category.contains("Lab", ignoreCase = true) || workItem.examType.contains("Lab", ignoreCase = true) -> "LAB EXAM"
                item.category.contains("Viva", ignoreCase = true) || item.category.contains("Presentation", ignoreCase = true) || workItem.examType.contains("Viva", ignoreCase = true) || workItem.examType.contains("Presentation", ignoreCase = true) -> "PRESENTATION / VIVA"
                item.category.isNotBlank() -> item.category.uppercase()
                workItem.examType.isNotBlank() -> workItem.examType.uppercase()
                else -> "ASSESSMENT"
            }
            binding.tvWorkItemType.text = typePrefix
            binding.tvWorkItemTitle.text = item.title
            val desc = if (!item.syllabus.isNullOrBlank()) item.syllabus else workItem.description
            binding.tvWorkItemDescription.text = desc
            binding.tvWorkItemDescription.visibility = if (desc.isNullOrBlank()) View.GONE else View.VISIBLE

            val daysRemaining = workItem.getDaysRemaining()
            val countdownBadge = when {
                daysRemaining < 0 -> " (Overdue)"
                daysRemaining == 0L -> " (Today!)"
                daysRemaining == 1L -> " (Tomorrow)"
                else -> " (in $daysRemaining days)"
            }
            val datePart = if (item.date.isNotBlank()) item.date else (workItem.endDate.ifBlank { workItem.startDate })
            val timePart = if (item.time.isNotBlank()) item.time else workItem.scoreOrMarks
            binding.tvWorkItemDates.text = buildString {
                append("Date: ")
                append(datePart.ifBlank { "TBD" })
                if (timePart.isNotBlank()) {
                    append(" • ")
                    append(timePart)
                }
                append(countdownBadge)
            }

            val status = if (item.status.isNotBlank()) item.status else workItem.status
            binding.tvWorkItemStatus.text = status
            if (status == "DONE") {
                binding.tvWorkItemStatus.setTextColor(ContextCompat.getColor(context, R.color.income_green))
                binding.tvWorkItemStatus.setBackgroundColor(0x2210B981)
                binding.btnToggleComplete.text = "Mark as Pending"
            } else {
                binding.tvWorkItemStatus.setTextColor(ContextCompat.getColor(context, R.color.accent_amber))
                binding.tvWorkItemStatus.setBackgroundColor(0x22F59E0B)
                binding.btnToggleComplete.text = "Mark as Done"
            }

            // 1. Bind Room
            val roomVal = if (!item.room.isNullOrBlank()) item.room else workItem.room
            if (!roomVal.isNullOrBlank()) {
                binding.tvAssessmentRoom.visibility = View.VISIBLE
                binding.tvAssessmentRoom.text = "Room: $roomVal"
            } else {
                binding.tvAssessmentRoom.visibility = View.GONE
            }

            // 2. Bind Attachment
            val attachName = if (!item.attachmentName.isNullOrBlank()) item.attachmentName else workItem.attachmentName
            val attachUri = if (!item.attachmentUri.isNullOrBlank()) item.attachmentUri else workItem.attachmentUri
            if (!attachName.isNullOrBlank()) {
                binding.layoutAttachmentPreview.visibility = View.VISIBLE
                binding.tvAttachmentFileName.text = attachName
                binding.layoutAttachmentPreview.setOnClickListener {
                    if (!attachUri.isNullOrBlank()) {
                        try {
                            val uri = Uri.parse(attachUri)
                            val fileExists = FileUtils.checkFileExists(context, uri)
                            if (!fileExists) {
                                Snackbar.make(binding.root, "Original file not found on this device.", Snackbar.LENGTH_LONG).show()
                                return@setOnClickListener
                            }
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Snackbar.make(binding.root, "Unable to open attachment.", Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                binding.layoutAttachmentPreview.visibility = View.GONE
            }

            binding.btnDeleteWorkItem.setOnClickListener {
                onDeleteClick?.invoke(item)
            }

            binding.btnToggleComplete.setOnClickListener {
                val nextStatus = if (status == "DONE") "PENDING" else "DONE"
                onToggleStatus?.invoke(item, nextStatus)
            }

            binding.root.setOnClickListener {
                onItemClick?.invoke(item)
            }
        }
    }
}
