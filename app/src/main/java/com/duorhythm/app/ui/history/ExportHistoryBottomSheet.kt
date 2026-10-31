package com.duorhythm.app.ui.history

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.duorhythm.app.databinding.BottomSheetExportHistoryBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

class ExportHistoryBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetExportHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ExportHistoryAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetExportHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ExportHistoryAdapter(
            onItemClick = { file -> openFile(file) },
            onShareClick = { file -> shareFile(file) },
            onDeleteClick = { file -> deleteSingleFile(file) }
        )

        binding.rvExportHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvExportHistory.adapter = adapter

        binding.btnToggleSelectMode.setOnClickListener { enterSelectionMode() }
        binding.btnCloseBatch.setOnClickListener { exitSelectionMode() }
        binding.btnSelectAllBatch.setOnClickListener { adapter.selectAll() }

        adapter.onSelectionChanged = {
            val count = adapter.selectedFiles.size
            binding.tvSelectedCount.text = "$count Selected"
            binding.btnDeleteBatch.isEnabled = count > 0
            binding.btnDeleteBatch.alpha = if (count > 0) 1.0f else 0.5f
        }

        binding.btnDeleteBatch.setOnClickListener {
            val filesToDelete = adapter.selectedFiles.toList()
            if (filesToDelete.isEmpty()) return@setOnClickListener

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete ${filesToDelete.size} Exports?")
                .setMessage("Selected snapshot images will be permanently removed from your device.")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("DELETE") { _, _ ->
                    var deletedCount = 0
                    filesToDelete.forEach { f ->
                        if (f.delete()) deletedCount++
                    }
                    Toast.makeText(requireContext(), "Deleted $deletedCount files", Toast.LENGTH_SHORT).show()
                    exitSelectionMode()
                    loadHistoryFiles()
                }
                .show()
        }

        loadHistoryFiles()
    }

    private fun enterSelectionMode() {
        adapter.isSelectionMode = true
        binding.layoutDefaultHeader.visibility = View.GONE
        binding.layoutBatchToolbar.visibility = View.VISIBLE
        adapter.onSelectionChanged?.invoke()
    }

    private fun exitSelectionMode() {
        adapter.isSelectionMode = false
        binding.layoutDefaultHeader.visibility = View.VISIBLE
        binding.layoutBatchToolbar.visibility = View.GONE
    }

    private fun loadHistoryFiles() {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "DuoRhythm")
        val files = if (dir.exists()) {
            dir.listFiles { f -> f.extension.equals("png", true) || f.extension.equals("jpg", true) }
                ?.sortedByDescending { it.lastModified() }
                ?.toList() ?: emptyList()
        } else {
            emptyList()
        }

        binding.tvEmptyHistory.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        binding.rvExportHistory.visibility = if (files.isEmpty()) View.GONE else View.VISIBLE
        adapter.setFiles(files)
    }

    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, if (file.extension.equals("jpg", true)) "image/jpeg" else "image/png")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "No app found to open image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (file.extension.equals("jpg", true)) "image/jpeg" else "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(Intent.createChooser(intent, "Share via..."))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteSingleFile(file: File) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Export?")
            .setMessage("Do you want to permanently delete '${file.name}'?")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("DELETE") { _, _ ->
                if (file.delete()) {
                    Toast.makeText(requireContext(), "Deleted ${file.name}", Toast.LENGTH_SHORT).show()
                    loadHistoryFiles()
                } else {
                    Toast.makeText(requireContext(), "Failed to delete file", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
