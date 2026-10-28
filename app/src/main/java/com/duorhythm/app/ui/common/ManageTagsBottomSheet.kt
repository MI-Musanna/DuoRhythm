package com.duorhythm.app.ui.common

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.databinding.DialogManageTagsBinding
import com.duorhythm.app.databinding.ItemManageTagBinding
import database.dao.AssessmentCategoryDao
import database.dao.ClassTypeDao
import database.entities.AssessmentCategoryEntity
import database.entities.ClassTypeEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TagItem(
    val id: Long,
    val name: String,
    val isDefault: Boolean = false
)

class ManageTagsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogManageTagsBinding? = null
    private val binding get() = _binding!!

    private var profileId: Long = 1
    private var isClassTypeMode: Boolean = true
    private var onTagsChangedListener: (() -> Unit)? = null

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var classTypeDao: ClassTypeDao
    private lateinit var assessmentCategoryDao: AssessmentCategoryDao
    private lateinit var adapter: TagsAdapter
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        const val TAG = "ManageTagsBottomSheet"
        private const val ARG_PROFILE_ID = "arg_profile_id"
        private const val ARG_IS_CLASS_TYPE_MODE = "arg_is_class_type_mode"

        fun newInstance(
            profileId: Long = 1,
            isClassTypeMode: Boolean = true,
            onTagsChanged: (() -> Unit)? = null
        ): ManageTagsBottomSheet {
            return ManageTagsBottomSheet().apply {
                arguments = Bundle().apply {
                    putLong(ARG_PROFILE_ID, profileId)
                    putBoolean(ARG_IS_CLASS_TYPE_MODE, isClassTypeMode)
                }
                this.onTagsChangedListener = onTagsChanged
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profileId = arguments?.getLong(ARG_PROFILE_ID, 1L) ?: 1L
        isClassTypeMode = arguments?.getBoolean(ARG_IS_CLASS_TYPE_MODE, true) ?: true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogManageTagsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dbHelper = DatabaseHelper.getInstance(requireContext())
        classTypeDao = dbHelper.classTypeDao
        assessmentCategoryDao = dbHelper.assessmentCategoryDao

        binding.tvManageTagsTitle.text = if (isClassTypeMode) "Manage Class Types" else "Manage Assessment Tags"
        binding.tvManageTagsSubtitle.text = if (isClassTypeMode) {
            "Add, edit, or delete class schedule routine types."
        } else {
            "Add, edit, or delete exam & assessment categories."
        }

        binding.btnCloseManageTags.setOnClickListener {
            dismiss()
        }

        adapter = TagsAdapter(
            onEdit = { tagItem -> showEditTagDialog(tagItem) },
            onDelete = { tagItem -> deleteTag(tagItem) }
        )
        binding.rvTags.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTags.adapter = adapter

        binding.btnAddTag.setOnClickListener {
            val name = binding.etNewTag.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            scope.launch {
                withContext(Dispatchers.IO) {
                    if (isClassTypeMode) {
                        classTypeDao.insertType(
                            ClassTypeEntity(
                                profileId = profileId,
                                name = name,
                                isDefault = false
                            )
                        )
                    } else {
                        assessmentCategoryDao.insertCategory(
                            AssessmentCategoryEntity(
                                profileId = profileId,
                                name = name,
                                isDefault = false
                            )
                        )
                    }
                }
                binding.etNewTag.setText("")
                loadTags()
                onTagsChangedListener?.invoke()
            }
        }

        loadTags()
    }

    private fun loadTags() {
        scope.launch {
            val tags = withContext(Dispatchers.IO) {
                if (isClassTypeMode) {
                    classTypeDao.getTypesByProfile(profileId).map {
                        TagItem(id = it.id, name = it.name, isDefault = it.isDefault)
                    }
                } else {
                    assessmentCategoryDao.getCategoriesByProfile(profileId).map {
                        TagItem(id = it.id, name = it.name, isDefault = it.isDefault)
                    }
                }
            }
            adapter.submitList(tags)
        }
    }

    private fun showEditTagDialog(tagItem: TagItem) {
        val context = requireContext()
        val etTagName = EditText(context).apply {
            setText(tagItem.name)
            setSelection(text.length)
            setPadding(48, 32, 48, 32)
            setTextColor(Color.WHITE)
            hint = "Tag / Category Name"
            setHintTextColor(Color.parseColor("#94A3B8"))
        }

        AlertDialog.Builder(context)
            .setTitle("Edit Tag / Category Name")
            .setView(etTagName)
            .setPositiveButton("UPDATE") { _, _ ->
                val updatedName = etTagName.text.toString().trim()
                if (updatedName.isNotEmpty()) {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            if (isClassTypeMode) {
                                classTypeDao.updateClassTypeName(tagItem.id, updatedName)
                            } else {
                                assessmentCategoryDao.updateCategoryName(tagItem.id, updatedName)
                            }
                        }
                        loadTags()
                        onTagsChangedListener?.invoke()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteTag(tagItem: TagItem) {
        scope.launch {
            withContext(Dispatchers.IO) {
                if (isClassTypeMode) {
                    classTypeDao.deleteType(tagItem.id)
                } else {
                    assessmentCategoryDao.deleteCategory(tagItem.id)
                }
            }
            loadTags()
            onTagsChangedListener?.invoke()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class TagsAdapter(
        private val onEdit: (TagItem) -> Unit,
        private val onDelete: (TagItem) -> Unit
    ) : RecyclerView.Adapter<TagsAdapter.ViewHolder>() {

        private val items = mutableListOf<TagItem>()

        fun submitList(newItems: List<TagItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemManageTagBinding.inflate(
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

        inner class ViewHolder(private val binding: ItemManageTagBinding) :
            RecyclerView.ViewHolder(binding.root) {

            fun bind(item: TagItem) {
                binding.tvTagName.text = item.name
                binding.btnEditTag.setOnClickListener {
                    onEdit(item)
                }
                binding.btnDeleteTag.setOnClickListener {
                    onDelete(item)
                }
            }
        }
    }
}
