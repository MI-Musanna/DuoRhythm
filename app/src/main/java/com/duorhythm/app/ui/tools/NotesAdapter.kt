package com.duorhythm.app.ui.tools

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.data.model.NoteItem
import com.duorhythm.app.databinding.ItemNoteBinding

class NotesAdapter(
    private var notes: List<NoteItem>,
    private val onNoteClick: (NoteItem) -> Unit,
    private val onDeleteClick: (NoteItem) -> Unit
) : RecyclerView.Adapter<NotesAdapter.NoteViewHolder>() {

    fun updateList(newNotes: List<NoteItem>) {
        notes = newNotes
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(notes[position])
    }

    override fun getItemCount(): Int = notes.size

    inner class NoteViewHolder(private val binding: ItemNoteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(note: NoteItem) {
            binding.tvNoteTitle.text = note.title
            binding.tvNoteContent.text = note.content
            binding.tvNoteCategory.text = note.category
            binding.tvNoteDate.text = "Updated: ${note.updatedAt}"

            binding.root.setOnClickListener {
                onNoteClick(note)
            }

            binding.btnDeleteNote.setOnClickListener {
                onDeleteClick(note)
            }
        }
    }
}
