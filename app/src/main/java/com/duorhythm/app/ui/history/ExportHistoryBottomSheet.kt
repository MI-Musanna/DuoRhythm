package com.duorhythm.app.ui.history

import android.content.Intent
import android.net.Uri
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
            onDeleteClick = { file -> deleteFile(file) }
        )

        binding.rvExportHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvExportHistory.adapter = adapter

        loadHistoryFiles()
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

    private fun deleteFile(file: File) {
        if (file.delete()) {
            Toast.makeText(requireContext(), "Deleted ${file.name}", Toast.LENGTH_SHORT).show()
            loadHistoryFiles()
        } else {
            Toast.makeText(requireContext(), "Failed to delete file", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
