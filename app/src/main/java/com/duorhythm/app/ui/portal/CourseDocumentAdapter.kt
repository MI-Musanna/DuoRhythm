package com.duorhythm.app.ui.portal

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.R
import com.duorhythm.app.data.model.SubjectMaterial
import com.duorhythm.app.databinding.ItemCourseDocumentBinding
import java.io.File

class CourseDocumentAdapter(
    private var materials: List<SubjectMaterial>,
    private val onDeleteClick: (SubjectMaterial) -> Unit
) : RecyclerView.Adapter<CourseDocumentAdapter.ViewHolder>() {

    fun updateList(newMaterials: List<SubjectMaterial>) {
        materials = newMaterials
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCourseDocumentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(materials[position])
    }

    override fun getItemCount(): Int = materials.size

    inner class ViewHolder(private val binding: ItemCourseDocumentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SubjectMaterial) {
            val context = binding.root.context
            binding.tvDocumentTitle.text = item.title

            val ext = File(item.filePath).extension.lowercase()
                .ifEmpty { item.title.substringAfterLast('.', "").lowercase() }

            val (badgeText, badgeColor, iconRes) = when (ext) {
                "pdf" -> Triple("PDF", Color.parseColor("#0284C7"), R.drawable.ic_file_pdf)
                "jpg", "jpeg", "png", "webp", "gif" -> Triple("IMG", Color.parseColor("#7C3AED"), R.drawable.ic_file_image)
                "txt", "doc", "docx", "rtf" -> Triple("DOC", Color.parseColor("#D97706"), R.drawable.ic_file_text)
                else -> {
                    when {
                        item.type == "PDF_DOCUMENT" -> Triple("PDF", Color.parseColor("#0284C7"), R.drawable.ic_file_pdf)
                        item.type == "IMAGE_NOTE" -> Triple("IMG", Color.parseColor("#7C3AED"), R.drawable.ic_file_image)
                        else -> {
                            val upper = ext.uppercase().takeIf { it.isNotBlank() && it.length <= 5 } ?: "FILE"
                            Triple(upper, Color.parseColor("#334155"), R.drawable.ic_attachment)
                        }
                    }
                }
            }

            binding.badgeFileType.text = badgeText
            binding.badgeFileType.setTextColor(badgeColor)
            val badgeBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(context, 6f)
                setColor(ColorUtils.setAlphaComponent(badgeColor, 35))
            }
            binding.badgeFileType.background = badgeBg

            binding.ivFileIcon.setImageResource(iconRes)
            binding.ivFileIcon.imageTintList = ColorStateList.valueOf(badgeColor)
            val iconBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(context, 12f)
                setColor(ColorUtils.setAlphaComponent(badgeColor, 35))
            }
            binding.layoutIconBg.background = iconBg

            val file = File(item.filePath)
            val sizeKb = if (file.exists()) file.length() / 1024 else 0
            val dateStr = if (item.createdAt.isNotBlank()) "Added: ${item.createdAt} • " else ""
            binding.tvDocumentSubtitle.text = "$dateStr${sizeKb} KB • Tap to open"

            binding.root.setOnClickListener {
                if (!file.exists()) {
                    Toast.makeText(context, "File does not exist: ${file.name}", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                try {
                    val authority = "${context.packageName}.fileprovider"
                    val fileUri = FileProvider.getUriForFile(context, authority, file)
                    val mimeType = when (ext) {
                        "pdf" -> "application/pdf"
                        "jpg", "jpeg" -> "image/jpeg"
                        "png" -> "image/png"
                        "webp" -> "image/webp"
                        "gif" -> "image/gif"
                        "txt" -> "text/plain"
                        "doc", "docx" -> "application/msword"
                        else -> "*/*"
                    }
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(fileUri, mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Open Document"))
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not open file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            binding.btnDeleteDocument.setOnClickListener {
                onDeleteClick(item)
            }
        }

        private fun dpToPx(context: Context, dp: Float): Float {
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.resources.displayMetrics
            )
        }
    }
}
