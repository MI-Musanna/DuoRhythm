package com.pocketdev.duorhythm.ui.portal

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.pocketdev.duorhythm.R
import com.pocketdev.duorhythm.data.model.SubjectMaterial
import com.pocketdev.duorhythm.databinding.ItemSubjectMaterialBinding
import java.io.File

class SubjectMaterialsAdapter(
    private var materials: List<SubjectMaterial>,
    private val onDeleteClick: (SubjectMaterial) -> Unit
) : RecyclerView.Adapter<SubjectMaterialsAdapter.ViewHolder>() {

    fun updateList(newMaterials: List<SubjectMaterial>) {
        materials = newMaterials
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSubjectMaterialBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(materials[position])
    }

    override fun getItemCount(): Int = materials.size

    inner class ViewHolder(private val binding: ItemSubjectMaterialBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SubjectMaterial) {
            val context = binding.root.context
            binding.tvMaterialTitle.text = item.title

            val badgeText = when (item.type) {
                "PDF_DOCUMENT" -> "PDF"
                "IMAGE_NOTE" -> "NOTE"
                "SYLLABUS" -> "SYLLABUS"
                else -> "FILE"
            }
            binding.tvMaterialTypeBadge.text = badgeText

            val file = File(item.filePath)
            val sizeKb = if (file.exists()) file.length() / 1024 else 0
            val dateStr = if (item.createdAt.isNotBlank()) "Added: ${item.createdAt} • " else ""
            binding.tvMaterialSubtitle.text = "$dateStr${sizeKb} KB • Tap to open"

            binding.root.setOnClickListener {
                if (!file.exists()) {
                    Toast.makeText(context, "File does not exist: ${file.name}", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                try {
                    val authority = "${context.packageName}.fileprovider"
                    val fileUri = FileProvider.getUriForFile(context, authority, file)
                    val mimeType = when {
                        file.name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                        file.name.endsWith(".png", ignoreCase = true) || file.name.endsWith(".jpg", ignoreCase = true) || file.name.endsWith(".jpeg", ignoreCase = true) -> "image/*"
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

            binding.btnDeleteMaterial.setOnClickListener {
                onDeleteClick(item)
            }
        }
    }
}
