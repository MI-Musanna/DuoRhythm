package com.duorhythm.app.ui.schedule

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.databinding.DialogManageClassTypesBinding
import com.duorhythm.app.databinding.ItemClassTypeBinding
import database.entities.ClassTypeEntity

class ManageClassTypesDialog(
    private val context: Context,
    private val profileId: Long,
    private val onTypesChanged: () -> Unit
) {

    fun show() {
        val dbHelper = DatabaseHelper.getInstance(context)
        val binding = DialogManageClassTypesBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        fun reloadList() {
            val types = dbHelper.classTypeDao.getTypesByProfile(profileId)
            binding.rvClassTypes.layoutManager = LinearLayoutManager(context)
            binding.rvClassTypes.adapter = ClassTypesAdapter(types) { typeToDelete ->
                dbHelper.classTypeDao.deleteType(typeToDelete.id)
                reloadList()
                onTypesChanged()
            }
        }

        binding.btnAddClassType.setOnClickListener {
            val name = binding.etNewClassType.text?.toString()?.trim().orEmpty()
            if (name.isBlank()) {
                Toast.makeText(context, "Please enter a class type name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dbHelper.classTypeDao.insertType(
                ClassTypeEntity(
                    profileId = profileId,
                    name = name,
                    isDefault = false
                )
            )
            binding.etNewClassType.setText("")
            reloadList()
            onTypesChanged()
        }

        binding.btnCloseManageTypes.setOnClickListener {
            dialog.dismiss()
        }

        reloadList()
        dialog.show()
    }

    private class ClassTypesAdapter(
        private val items: List<ClassTypeEntity>,
        private val onDelete: (ClassTypeEntity) -> Unit
    ) : RecyclerView.Adapter<ClassTypesAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemClassTypeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(private val binding: ItemClassTypeBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: ClassTypeEntity) {
                binding.tvClassTypeName.text = item.name
                if (item.isDefault) {
                    binding.tvDefaultBadge.visibility = View.VISIBLE
                    binding.btnDeleteClassType.visibility = View.GONE
                } else {
                    binding.tvDefaultBadge.visibility = View.GONE
                    binding.btnDeleteClassType.visibility = View.VISIBLE
                    binding.btnDeleteClassType.setOnClickListener {
                        onDelete(item)
                    }
                }
            }
        }
    }
}
