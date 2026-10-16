package com.pocketdev.duorhythm.ui.tasks

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.pocketdev.duorhythm.R
import com.pocketdev.duorhythm.data.local.db.DatabaseHelper
import com.pocketdev.duorhythm.data.model.TaskItem
import com.pocketdev.duorhythm.databinding.DialogAddTaskBinding
import com.pocketdev.duorhythm.databinding.FragmentTasksBinding
import com.pocketdev.duorhythm.util.AnimationUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TasksFragment : Fragment() {

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var tasksAdapter: TasksAdapter
    private var currentFilter = "ALL"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTasksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            dbHelper = DatabaseHelper.getInstance(requireContext())
            setupRecyclerView()
            setupListeners()
            populateCategoryChips()
            loadTasks()

            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
                try {
                    val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    val density = resources.displayMetrics.density
                    val baseBottomMargin = (96 * density).toInt()
                    val sideMargin = (20 * density).toInt()
                    val basePaddingBottom = (120 * density).toInt()

                    (binding.fabAddTask.layoutParams as? CoordinatorLayout.LayoutParams)?.let { lp ->
                        lp.bottomMargin = baseBottomMargin + systemBars.bottom
                        lp.rightMargin = sideMargin
                        binding.fabAddTask.layoutParams = lp
                    }
                    binding.rvTasks.setPadding(
                        binding.rvTasks.paddingLeft,
                        binding.rvTasks.paddingTop,
                        binding.rvTasks.paddingRight,
                        basePaddingBottom + systemBars.bottom
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                insets
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            try {
                loadTasks()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && _binding != null) {
            try {
                loadTasks()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    private fun setupRecyclerView() {
        tasksAdapter = TasksAdapter(
            emptyList(),
            onToggleCompleted = { task, isCompleted ->
                dbHelper.updateTaskCompletion(task.id, isCompleted)
                loadTasks()
            },
            onTaskClick = { task ->
                if (task.isGoal) {
                    showGoalProgressDialog(task)
                } else {
                    showEditTaskDialog(task)
                }
            },
            onDeleteClick = { task ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete Task")
                    .setMessage("Remove '${task.title}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        dbHelper.deleteTask(task.id)
                        loadTasks()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        binding.rvTasks.layoutManager = LinearLayoutManager(requireContext())
        binding.rvTasks.adapter = tasksAdapter
        AnimationUtils.applySlideUpLayoutAnimation(binding.rvTasks)
    }

    private fun showGoalProgressDialog(task: TaskItem) {
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val label = android.widget.TextView(requireContext()).apply {
            text = "Current Milestone Progress: ${task.progress}%"
            textSize = 16f
            setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_primary))
        }
        val seekBar = android.widget.SeekBar(requireContext()).apply {
            max = 100
            progress = task.progress
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    label.text = "Current Milestone Progress: $progress%"
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
            })
        }
        container.addView(label)
        container.addView(seekBar)

        AlertDialog.Builder(requireContext())
            .setTitle("Update Goal: ${task.title}")
            .setMessage("Drag slider to update achievement percentage:")
            .setView(container)
            .setPositiveButton("Update Progress") { _, _ ->
                val newProgress = seekBar.progress
                dbHelper.updateTaskProgress(task.id, newProgress)
                loadTasks()
                val msg = if (newProgress == 100) "Goal achieved (100%)" else "Goal progress updated to $newProgress%"
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditTaskDialog(task: TaskItem) {
        val dialogBinding = DialogAddTaskBinding.inflate(layoutInflater)
        dialogBinding.tvTaskDialogHeader.text = "Edit Task"
        dialogBinding.etTaskTitle.setText(task.title)
        dialogBinding.etTaskDescription.setText(task.description)
        dialogBinding.etTaskDueDate.setText(task.dueDate)
        dialogBinding.cbIsGoal.isChecked = task.isGoal
        dialogBinding.btnSaveTask.text = "Save Changes"

        val categories = dbHelper.getTaskCategories().ifEmpty { listOf(task.category.ifEmpty { "General" }) }
        val catAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categories)
        dialogBinding.spTaskCategory.adapter = catAdapter
        val catIdx = categories.indexOf(task.category).coerceAtLeast(0)
        dialogBinding.spTaskCategory.setSelection(catIdx)

        val priorities = listOf("HIGH", "MEDIUM", "LOW", "NONE")
        val prioAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, priorities)
        dialogBinding.spTaskPriority.adapter = prioAdapter
        val prioIdx = priorities.indexOf(task.priority).coerceAtLeast(0)
        dialogBinding.spTaskPriority.setSelection(prioIdx)

        dialogBinding.etTaskDueDate.setOnClickListener {
            val calendar = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    val cal = java.util.Calendar.getInstance()
                    cal.set(year, month, dayOfMonth)
                    val formatted = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                    dialogBinding.etTaskDueDate.setText(formatted)
                },
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        dialogBinding.btnSaveTask.setOnClickListener {
            val title = dialogBinding.etTaskTitle.text?.toString()?.trim().orEmpty()
            val desc = dialogBinding.etTaskDescription.text?.toString()?.trim().orEmpty()
            val dueDate = dialogBinding.etTaskDueDate.text?.toString()?.trim().orEmpty()
            val category = categories.getOrNull(dialogBinding.spTaskCategory.selectedItemPosition) ?: task.category.ifEmpty { "General" }
            val priority = priorities[dialogBinding.spTaskPriority.selectedItemPosition]
            val isGoal = dialogBinding.cbIsGoal.isChecked

            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "Task title is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updated = task.copy(
                title = title,
                description = desc,
                category = category,
                dueDate = dueDate,
                priority = priority,
                isGoal = isGoal
            )
            dbHelper.updateTask(updated)
            dialog.dismiss()
            loadTasks()
            Toast.makeText(requireContext(), "Task updated!", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun setupListeners() {
        binding.chipGroupTaskFilters.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId != View.NO_ID) {
                binding.chipGroupCustomCategories.clearCheck()
                currentFilter = when (checkedId) {
                    R.id.chipFilterToday -> "TODAY"
                    R.id.chipFilterCompleted -> "COMPLETED"
                    R.id.chipFilterAll -> "ALL"
                    else -> "ALL"
                }
                loadTasks()
            } else if (binding.chipGroupCustomCategories.checkedChipId == View.NO_ID) {
                // Keep "All" checked by default if nothing selected
                binding.chipFilterAll.isChecked = true
            }
        }

        binding.chipGroupCustomCategories.setOnCheckedChangeListener { group, checkedId ->
            if (checkedId != View.NO_ID) {
                binding.chipGroupTaskFilters.clearCheck()
                val chip = group.findViewById<com.google.android.material.chip.Chip>(checkedId)
                currentFilter = chip?.text?.toString() ?: "ALL"
                loadTasks()
            }
        }

        binding.btnAddCustomCategory.setOnClickListener {
            val input = android.widget.EditText(requireContext()).apply {
                hint = "Category Name (e.g. Exams, Research)"
                setSingleLine(true)
                setPadding(48, 32, 48, 32)
            }
            AlertDialog.Builder(requireContext())
                .setTitle("Create Custom Category")
                .setMessage("Add a new task classification tag:")
                .setView(input)
                .setPositiveButton("Add") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isNotEmpty()) {
                        val added = dbHelper.addTaskCategory(name)
                        if (added) {
                            populateCategoryChips()
                            Toast.makeText(requireContext(), "Category '$name' created", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(requireContext(), "Category already exists", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.fabAddTask.setOnClickListener { showCreateTaskDialog(isGoal = false) }
        binding.btnAddGoal.setOnClickListener { showCreateTaskDialog(isGoal = true) }
        binding.btnEmptyAddTask.setOnClickListener { showCreateTaskDialog(isGoal = false) }

        AnimationUtils.attachTactileFeedback(
            binding.fabAddTask,
            binding.btnAddGoal,
            binding.btnEmptyAddTask,
            binding.btnAddCustomCategory
        )
    }

    private fun populateCategoryChips() {
        binding.chipGroupCustomCategories.removeAllViews()
        val categories = dbHelper.getTaskCategories()
        for (category in categories) {
            val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                text = category
                isCheckable = true
                isCloseIconVisible = true
                setCloseIconResource(R.drawable.ic_close)
                setOnCloseIconClickListener {
                    showDeleteCategoryDialog(category)
                }
                setOnLongClickListener {
                    showDeleteCategoryDialog(category)
                    true
                }
            }
            binding.chipGroupCustomCategories.addView(chip)
        }
    }

    private fun showDeleteCategoryDialog(category: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Category")
            .setMessage("Remove category '$category'?")
            .setPositiveButton("Delete") { _, _ ->
                dbHelper.deleteTaskCategory(category)
                if (currentFilter == category) {
                    currentFilter = "ALL"
                    binding.chipFilterAll.isChecked = true
                }
                populateCategoryChips()
                loadTasks()
                Toast.makeText(requireContext(), "Category '$category' removed", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun loadTasks() {
        if (_binding == null || !isAdded || !::dbHelper.isInitialized || !::tasksAdapter.isInitialized) return
        try {
            val tasks = dbHelper.getAllTasks(currentFilter)
            tasksAdapter.updateList(tasks)

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val allTasks = if (currentFilter == "ALL") tasks else dbHelper.getAllTasks("ALL")
            val pendingToday = allTasks.count { !it.isCompleted && (it.dueDate.isEmpty() || it.dueDate <= today) }
            binding.tvTasksSubtitle.text = "$pendingToday pending today"

            if (tasks.isEmpty()) {
                binding.rvTasks.visibility = View.GONE
                binding.layoutEmptyTasks.visibility = View.VISIBLE
            } else {
                binding.rvTasks.visibility = View.VISIBLE
                binding.layoutEmptyTasks.visibility = View.GONE
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun showCreateTaskDialog(isGoal: Boolean) {
        val dialogBinding = DialogAddTaskBinding.inflate(layoutInflater)
        if (isGoal) {
            dialogBinding.tvTaskDialogHeader.text = "Set a New Milestone Goal"
            dialogBinding.tilTaskTitle.hint = "Goal Title (e.g. Master Kotlin, Save 100k BDT)"
            dialogBinding.btnSaveTask.text = "Create Goal"
            dialogBinding.btnSaveTask.setBackgroundColor(0xFFD97706.toInt()) // Amber
        } else {
            dialogBinding.tvTaskDialogHeader.text = "Create New Task"
            dialogBinding.tilTaskTitle.hint = "Task Title"
            dialogBinding.btnSaveTask.text = "Save Task"
        }
        dialogBinding.cbIsGoal.isChecked = isGoal

        val categories = dbHelper.getTaskCategories().ifEmpty { listOf("General") }
        val catAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categories)
        dialogBinding.spTaskCategory.adapter = catAdapter
        val goalIdx = categories.indexOf("Goals")
        if (isGoal && goalIdx >= 0) {
            dialogBinding.spTaskCategory.setSelection(goalIdx)
        }

        val priorities = listOf("HIGH", "MEDIUM", "LOW", "NONE")
        val prioAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, priorities)
        dialogBinding.spTaskPriority.adapter = prioAdapter

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        dialogBinding.etTaskDueDate.setText(today)
        dialogBinding.etTaskDueDate.setOnClickListener {
            val calendar = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    val cal = java.util.Calendar.getInstance()
                    cal.set(year, month, dayOfMonth)
                    val formatted = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                    dialogBinding.etTaskDueDate.setText(formatted)
                },
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        dialogBinding.btnSaveTask.setOnClickListener {
            val title = dialogBinding.etTaskTitle.text?.toString()?.trim().orEmpty()
            val desc = dialogBinding.etTaskDescription.text?.toString()?.trim().orEmpty()
            val dueDate = dialogBinding.etTaskDueDate.text?.toString()?.trim().orEmpty()
            val category = categories.getOrNull(dialogBinding.spTaskCategory.selectedItemPosition) ?: "General"
            val priority = priorities[dialogBinding.spTaskPriority.selectedItemPosition]
            val markedGoal = dialogBinding.cbIsGoal.isChecked

            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "Task title is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val task = TaskItem(
                title = title,
                description = desc,
                category = category,
                dueDate = dueDate,
                priority = priority,
                isCompleted = false,
                isGoal = markedGoal,
                progress = if (markedGoal) 25 else 0
            )
            dbHelper.insertTask(task)
            dialog.dismiss()
            loadTasks()
            Toast.makeText(requireContext(), if (markedGoal) "Goal created!" else "Task created!", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
