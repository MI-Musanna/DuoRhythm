package com.duorhythm.app.ui.history

import android.graphics.BitmapFactory
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.databinding.ItemExportHistoryBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportHistoryAdapter(
    private val onItemClick: (File) -> Unit,
    private val onShareClick: (File) -> Unit,
    private val onDeleteClick: (File) -> Unit
) : RecyclerView.Adapter<ExportHistoryAdapter.HistoryViewHolder>() {

    private val filesList = mutableListOf<File>()

    fun setFiles(files: List<File>) {
        filesList.clear()
        filesList.addAll(files)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemExportHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(filesList[position])
    }

    override fun getItemCount(): Int = filesList.size

    inner class HistoryViewHolder(private val binding: ItemExportHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(file: File) {
            binding.tvFileName.text = file.name

            val size = Formatter.formatFileSize(binding.root.context, file.length())
            val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH).format(Date(file.lastModified()))
            binding.tvFileMeta.text = "$size • $dateStr"

            try {
                val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                val thumb = BitmapFactory.decodeFile(file.absolutePath, options)
                binding.ivFileThumb.setImageBitmap(thumb)
            } catch (e: Exception) {
                binding.ivFileThumb.setImageDrawable(null)
            }

            binding.root.setOnClickListener { onItemClick(file) }
            binding.btnShareItem.setOnClickListener { onShareClick(file) }
            binding.btnDeleteItem.setOnClickListener { onDeleteClick(file) }
        }
    }
}
