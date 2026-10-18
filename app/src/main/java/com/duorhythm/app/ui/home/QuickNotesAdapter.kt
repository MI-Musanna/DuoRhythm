package com.duorhythm.app.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.data.model.NoteItem
import com.duorhythm.app.databinding.ItemQuickNoteHomeBinding

class QuickNotesAdapter(
    private var notes: List<NoteItem>,
    private val onNoteClick: (NoteItem) -> Unit,
    private val onNoteDelete: (NoteItem) -> Unit
) : RecyclerView.Adapter<QuickNotesAdapter.QuickNoteViewHolder>() {

    fun updateList(newNotes: List<NoteItem>) {
        notes = newNotes
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuickNoteViewHolder {
        val binding = ItemQuickNoteHomeBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return QuickNoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QuickNoteViewHolder, position: Int) {
        holder.bind(notes[position])
    }

    override fun getItemCount(): Int = notes.size

    inner class QuickNoteViewHolder(private val binding: ItemQuickNoteHomeBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(note: NoteItem) {
            binding.tvQuickNoteTitle.text = note.title
            binding.tvQuickNoteContent.text = note.content
            binding.tvQuickNoteCategory.text = note.category.ifBlank { "General" }
            binding.tvQuickNoteDate.text = if (note.updatedAt.isNotBlank()) note.updatedAt.take(10) else "Just now"

            binding.root.setOnClickListener {
                onNoteClick(note)
            }

            binding.btnQuickNoteDelete.setOnClickListener {
                onNoteDelete(note)
            }
        }
    }
}
