package com.duorhythm.app.ui.portal

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.R
import com.duorhythm.app.data.model.SubjectWorkItem
import com.duorhythm.app.databinding.ItemSubjectWorkBinding
import java.io.File

class SubjectItemsAdapter(
    private var items: List<SubjectWorkItem>,
    private val onToggleStatus: (SubjectWorkItem, String) -> Unit,
    private val onDeleteClick: (SubjectWorkItem) -> Unit,
    private val onItemClick: ((SubjectWorkItem) -> Unit)? = null
) : RecyclerView.Adapter<SubjectItemsAdapter.ItemViewHolder>() {

    fun updateList(newItems: List<SubjectWorkItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding = ItemSubjectWorkBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ItemViewHolder(private val binding: ItemSubjectWorkBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SubjectWorkItem) {
            val context = binding.root.context
            val typePrefix = when (item.itemType) {
                "HOMEWORK" -> "HOMEWORK"
                "PRACTICAL" -> "PRACTICAL"
                "EXAM" -> when {
                    item.examType.contains("Final", ignoreCase = true) -> "SEMESTER FINAL"
                    item.examType.contains("Midterm", ignoreCase = true) -> "MIDTERM EXAM"
                    item.examType.contains("CT", ignoreCase = true) || item.examType.contains("Class Test", ignoreCase = true) -> "CLASS TEST (CT)"
                    item.examType.contains("Quiz", ignoreCase = true) -> "QUIZ"
                    item.examType.contains("Lab", ignoreCase = true) -> "LAB EXAM"
                    item.examType.contains("Viva", ignoreCase = true) || item.examType.contains("Presentation", ignoreCase = true) -> "PRESENTATION / VIVA"
                    item.examType.isNotBlank() -> item.examType.uppercase()
                    else -> "EXAM"
                }
                else -> "ASSIGNMENT"
            }
            binding.tvWorkItemType.text = typePrefix
            binding.tvWorkItemTitle.text = item.title
            binding.tvWorkItemDescription.text = item.description
            binding.tvWorkItemDescription.visibility = if (item.description.isEmpty()) View.GONE else View.VISIBLE

            val daysRemaining = item.getDaysRemaining()
            val countdownBadge = when {
                daysRemaining < 0 -> " (Overdue)"
                daysRemaining == 0L -> " (Today!)"
                daysRemaining == 1L -> " (Tomorrow)"
                else -> " (in $daysRemaining days)"
            }
            binding.tvWorkItemDates.text = "Start: ${item.startDate}  •  Due: ${item.endDate}$countdownBadge"

            if (item.scoreOrMarks.isNotEmpty()) {
                binding.tvWorkItemScore.visibility = View.VISIBLE
                binding.tvWorkItemScore.text = "Marks: ${item.scoreOrMarks}"
            } else {
                binding.tvWorkItemScore.visibility = View.GONE
            }

            binding.tvWorkItemStatus.text = item.status
            if (item.status == "DONE") {
                binding.tvWorkItemStatus.setTextColor(ContextCompat.getColor(context, R.color.income_green))
                binding.tvWorkItemStatus.setBackgroundColor(0x2210B981)
                binding.btnToggleComplete.text = "Mark as Pending"
            } else {
                binding.tvWorkItemStatus.setTextColor(ContextCompat.getColor(context, R.color.accent_amber))
                binding.tvWorkItemStatus.setBackgroundColor(0x22F59E0B)
                binding.btnToggleComplete.text = "Mark as Done"
            }

            if (item.attachmentPath.isNotEmpty()) {
                binding.layoutAttachment.visibility = View.VISIBLE
                val file = File(item.attachmentPath)
                binding.tvAttachmentName.text = "Attachment: ${file.name}"
                binding.layoutAttachment.setOnClickListener {
                    try {
                        val authority = "${context.packageName}.fileprovider"
                        val fileUri = FileProvider.getUriForFile(context, authority, file)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(fileUri, "*/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Open File"))
                    } catch (_: Exception) {
                        Toast.makeText(context, "Attachment saved at: ${file.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                binding.layoutAttachment.visibility = View.GONE
            }

            binding.btnToggleComplete.setOnClickListener {
                val nextStatus = if (item.status == "DONE") "PENDING" else "DONE"
                onToggleStatus(item, nextStatus)
            }

            binding.root.setOnClickListener {
                onItemClick?.invoke(item)
            }

            binding.btnDeleteWorkItem.visibility = View.VISIBLE
            binding.btnDeleteWorkItem.setOnClickListener {
                onDeleteClick(item)
            }
        }
    }
}
