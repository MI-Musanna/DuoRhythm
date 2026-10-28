package com.duorhythm.app.ui.tasks

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.TaskItem
import com.duorhythm.app.scheduler.TaskAlarmScheduler
import com.duorhythm.app.util.crossfade
import com.duorhythm.app.databinding.DialogAddTaskBinding
import com.duorhythm.app.databinding.DialogEditGoalBinding
import com.duorhythm.app.databinding.DialogEditTaskBinding
import com.duorhythm.app.databinding.FragmentTasksBinding
import com.duorhythm.app.util.AnimationUtils
import com.duorhythm.app.util.TaskDateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TasksFragment : Fragment() {

    private enum class TaskTab { TASKS, GOALS }

    private var _binding: FragmentTasksBinding? = null
    private val binding get() = _binding!!

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var tasksAdapter: TasksAdapter
    private var currentFilter = "ALL"
    private var currentTab = TaskTab.TASKS

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
                if (isCompleted) {
                    TaskAlarmScheduler.cancelTaskReminder(requireContext(), task.id)
                } else {
                    TaskAlarmScheduler.scheduleTaskReminder(requireContext(), task.copy(isCompleted = false))
                }
                loadTasks()
            },
            onTaskClick = { task ->
                if (task.isGoal) {
                    showEditGoalDialog(task)
                } else {
                    showEditTaskDialog(task)
                }
            },
            onDeleteClick = { task ->
                val title = if (task.isGoal) "Delete Goal" else "Delete Task"
                AlertDialog.Builder(requireContext())
                    .setTitle(title)
                    .setMessage("Remove '${task.title}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        TaskAlarmScheduler.cancelTaskReminder(requireContext(), task.id)
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

    private fun setupDatePicker(
        editText: com.google.android.material.textfield.TextInputEditText,
        textInputLayout: com.google.android.material.textfield.TextInputLayout,
        btnClear: View,
        initialDate: String?,
        title: String,
        onDateChanged: (String?) -> Unit = {}
    ) {
        fun updateDisplay(dateStr: String?) {
            if (!dateStr.isNullOrBlank()) {
                editText.setText(TaskDateUtils.formatDisplayDate(dateStr))
                editText.tag = dateStr
                btnClear.visibility = View.VISIBLE
            } else {
                editText.setText("")
                editText.tag = null
                btnClear.visibility = View.GONE
            }
        }

        updateDisplay(initialDate)

        fun launchPicker() {
            val builder = com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
            builder.setTitleText(title)
            val currentTag = editText.tag as? String
            val utcMillis = TaskDateUtils.toUtcMillis(currentTag)
            if (utcMillis != null) {
                builder.setSelection(utcMillis)
            }
            val picker = builder.build()
            picker.addOnPositiveButtonClickListener { selectionMillis ->
                val dateStr = TaskDateUtils.fromUtcMillisToStorage(selectionMillis)
                updateDisplay(dateStr)
                onDateChanged(dateStr)
            }
            picker.show(childFragmentManager, "TASK_DATE_PICKER_${System.currentTimeMillis()}")
        }

        editText.setOnClickListener { launchPicker() }
        textInputLayout.setEndIconOnClickListener { launchPicker() }

        btnClear.setOnClickListener {
            updateDisplay(null)
            onDateChanged(null)
        }
    }

    private fun showEditGoalDialog(goal: TaskItem) {
        val dialogBinding = DialogEditGoalBinding.inflate(layoutInflater)
        dialogBinding.etGoalTitle.setText(goal.title)
        dialogBinding.etGoalDescription.setText(goal.description)
        dialogBinding.sbGoalProgress.progress = goal.progress
        dialogBinding.tvGoalProgressValue.text = "${goal.progress}%"

        setupDatePicker(
            dialogBinding.etGoalStartDate,
            dialogBinding.tilGoalStartDate,
            dialogBinding.btnClearGoalStartDate,
            goal.startDate,
            "Select Start Date"
        )

        setupDatePicker(
            dialogBinding.etGoalTargetDate,
            dialogBinding.tilGoalTargetDate,
            dialogBinding.btnClearGoalTargetDate,
            goal.dueDate,
            "Select Target Date"
        )

        dialogBinding.sbGoalProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                dialogBinding.tvGoalProgressValue.text = "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogBinding.btnSaveGoal.setOnClickListener {
            val title = dialogBinding.etGoalTitle.text?.toString()?.trim().orEmpty()
            val startDate = dialogBinding.etGoalStartDate.tag as? String
            val targetDate = dialogBinding.etGoalTargetDate.tag as? String
            val desc = dialogBinding.etGoalDescription.text?.toString()?.trim().orEmpty()
            val progress = dialogBinding.sbGoalProgress.progress

            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "Goal title is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updated = goal.copy(
                title = title,
                startDate = startDate,
                dueDate = targetDate,
                description = desc,
                progress = progress,
                isCompleted = progress >= 100
            )
            dbHelper.updateTask(updated)
            if (updated.isCompleted) {
                TaskAlarmScheduler.cancelTaskReminder(requireContext(), updated.id)
            } else {
                TaskAlarmScheduler.scheduleTaskReminder(requireContext(), updated)
            }
            dialog.dismiss()
            loadTasks()
            val msg = if (progress == 100) "Goal achieved (100%)!" else "Goal updated ($progress%)"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }

    private fun showEditTaskDialog(task: TaskItem) {
        val dialogBinding = DialogEditTaskBinding.inflate(layoutInflater)
        dialogBinding.tvTaskDialogHeader.text = if (task.isGoal) "Edit Goal" else "Edit Task"
        dialogBinding.etTaskTitle.setText(task.title)
        dialogBinding.etTaskDescription.setText(task.description)
        dialogBinding.cbIsGoal.isChecked = task.isGoal
        dialogBinding.btnSaveTask.text = "Save Changes"

        dialogBinding.layoutGoalStartDate.visibility = if (task.isGoal) View.VISIBLE else View.GONE
        dialogBinding.tilTaskDueDate.hint = if (task.isGoal) "Target Date (Optional)" else "Due Date (Optional)"

        dialogBinding.cbIsGoal.setOnCheckedChangeListener { _, isChecked ->
            dialogBinding.layoutGoalStartDate.visibility = if (isChecked) View.VISIBLE else View.GONE
            dialogBinding.tilTaskDueDate.hint = if (isChecked) "Target Date (Optional)" else "Due Date (Optional)"
            dialogBinding.tvTaskDialogHeader.text = if (isChecked) "Edit Goal" else "Edit Task"
        }

        setupDatePicker(
            dialogBinding.etTaskStartDate,
            dialogBinding.tilTaskStartDate,
            dialogBinding.btnClearStartDate,
            task.startDate,
            "Select Start Date"
        )

        setupDatePicker(
            dialogBinding.etTaskDueDate,
            dialogBinding.tilTaskDueDate,
            dialogBinding.btnClearDueDate,
            task.dueDate,
            "Select Due Date"
        )

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

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogBinding.btnSaveTask.setOnClickListener {
            val title = dialogBinding.etTaskTitle.text?.toString()?.trim().orEmpty()
            val desc = dialogBinding.etTaskDescription.text?.toString()?.trim().orEmpty()
            val isGoal = dialogBinding.cbIsGoal.isChecked
            val startDate = if (isGoal) dialogBinding.etTaskStartDate.tag as? String else null
            val dueDate = dialogBinding.etTaskDueDate.tag as? String
            val category = categories.getOrNull(dialogBinding.spTaskCategory.selectedItemPosition) ?: task.category.ifEmpty { "General" }
            val priority = priorities[dialogBinding.spTaskPriority.selectedItemPosition]

            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "Task title is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updated = task.copy(
                title = title,
                description = desc,
                category = category,
                startDate = startDate,
                dueDate = dueDate,
                priority = priority,
                isGoal = isGoal
            )
            dbHelper.updateTask(updated)
            if (updated.isCompleted) {
                TaskAlarmScheduler.cancelTaskReminder(requireContext(), updated.id)
            } else {
                TaskAlarmScheduler.scheduleTaskReminder(requireContext(), updated)
            }
            dialog.dismiss()
            loadTasks()
            Toast.makeText(requireContext(), if (isGoal) "Goal updated!" else "Task updated!", Toast.LENGTH_SHORT).show()
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
                binding.rvTasks.crossfade(true, 150)
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
                binding.rvTasks.crossfade(true, 150)
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

        binding.toggleTasksGoals.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            if (checkedId == R.id.btnTabTasks) {
                currentTab = TaskTab.TASKS
                binding.btnTabTasks.setBackgroundColor(Color.parseColor("#0284C7"))
                binding.btnTabTasks.setTextColor(Color.WHITE)
                binding.btnTabGoals.setBackgroundColor(Color.TRANSPARENT)
                binding.btnTabGoals.setTextColor(Color.parseColor("#94A3B8"))
                binding.categoryFilterScrollView.visibility = View.VISIBLE
                binding.tvTasksTitle.text = "My Tasks"
                binding.fabAddTask.text = "New Task"
                binding.fabAddTask.setIconResource(R.drawable.ic_add)
                binding.fabAddTask.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#0284C7"))
            } else if (checkedId == R.id.btnTabGoals) {
                currentTab = TaskTab.GOALS
                binding.btnTabGoals.setBackgroundColor(Color.parseColor("#D97706"))
                binding.btnTabGoals.setTextColor(Color.WHITE)
                binding.btnTabTasks.setBackgroundColor(Color.TRANSPARENT)
                binding.btnTabTasks.setTextColor(Color.parseColor("#94A3B8"))
                binding.categoryFilterScrollView.visibility = View.GONE
                binding.tvTasksTitle.text = "Milestone Goals"
                binding.fabAddTask.text = "New Goal"
                binding.fabAddTask.setIconResource(R.drawable.ic_target)
                binding.fabAddTask.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#D97706"))
            }
            loadTasks()
        }

        binding.fabAddTask.setOnClickListener { showCreateTaskDialog(isGoal = (currentTab == TaskTab.GOALS)) }
        binding.btnAddGoal.setOnClickListener {
            binding.toggleTasksGoals.check(R.id.btnTabGoals)
            showCreateTaskDialog(isGoal = true)
        }
        binding.btnAddFirstTask.setOnClickListener { showCreateTaskDialog(isGoal = false) }
        binding.btnAddFirstGoal.setOnClickListener { showCreateTaskDialog(isGoal = true) }

        AnimationUtils.attachTactileFeedback(
            binding.fabAddTask,
            binding.btnAddGoal,
            binding.btnAddFirstTask,
            binding.btnAddFirstGoal,
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
            if (currentTab == TaskTab.TASKS) {
                val tasks = dbHelper.getAllTasks(currentFilter, isGoal = false)
                tasksAdapter.updateList(tasks)

                val today = TaskDateUtils.getTodayStorageDate()
                val allTasks = if (currentFilter == "ALL") tasks else dbHelper.getAllTasks("ALL", isGoal = false)
                val pendingToday = allTasks.count { !it.isCompleted && !it.dueDate.isNullOrBlank() && it.dueDate <= today }
                binding.tvTasksSubtitle.text = "$pendingToday pending today"

                // Update Circular Goal/Task Progress Header
                val totalTasks = allTasks.size
                val completedTasks = allTasks.count { it.isCompleted }
                val taskPercent = if (totalTasks > 0) ((completedTasks * 100) / totalTasks) else 0

                binding.tvGoalProgressHeader.text = "Task Progress"
                binding.tvGoalProgressSubtitle.text = "$completedTasks of $totalTasks Completed"
                binding.progressGoalCircular.setIndicatorColor(Color.parseColor("#0284C7"))
                binding.progressGoalHorizontal.setIndicatorColor(Color.parseColor("#0284C7"))
                binding.progressGoalCircular.setProgressCompat(taskPercent, true)
                binding.progressGoalHorizontal.setProgressCompat(taskPercent, true)
                binding.tvGoalPercentText.text = "$taskPercent%"

                if (tasks.isEmpty()) {
                    binding.rvTasks.visibility = View.GONE
                    binding.layoutEmptyTasks.visibility = View.VISIBLE
                    binding.layoutEmptyGoals.visibility = View.GONE
                } else {
                    binding.rvTasks.visibility = View.VISIBLE
                    binding.layoutEmptyTasks.visibility = View.GONE
                    binding.layoutEmptyGoals.visibility = View.GONE
                }
            } else {
                val goals = dbHelper.getAllTasks("ALL", isGoal = true)
                tasksAdapter.updateList(goals)

                val activeGoals = goals.count { it.progress < 100 }
                binding.tvTasksSubtitle.text = "$activeGoals active goals"

                // Update Circular Goal/Task Progress Header
                val totalGoals = goals.size
                val completedGoals = goals.count { it.isCompleted || it.progress >= 100 }
                val goalPercent = if (totalGoals > 0) {
                    (goals.sumOf { it.progress.coerceIn(0, 100) } / totalGoals)
                } else 0

                binding.tvGoalProgressHeader.text = "Goal Progress"
                binding.tvGoalProgressSubtitle.text = "$completedGoals of $totalGoals Completed"
                binding.progressGoalCircular.setIndicatorColor(Color.parseColor("#D97706"))
                binding.progressGoalHorizontal.setIndicatorColor(Color.parseColor("#D97706"))
                binding.progressGoalCircular.setProgressCompat(goalPercent, true)
                binding.progressGoalHorizontal.setProgressCompat(goalPercent, true)
                binding.tvGoalPercentText.text = "$goalPercent%"

                if (goals.isEmpty()) {
                    binding.rvTasks.visibility = View.GONE
                    binding.layoutEmptyTasks.visibility = View.GONE
                    binding.layoutEmptyGoals.visibility = View.VISIBLE
                } else {
                    binding.rvTasks.visibility = View.VISIBLE
                    binding.layoutEmptyTasks.visibility = View.GONE
                    binding.layoutEmptyGoals.visibility = View.GONE
                }
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
            dialogBinding.tilTaskDueDate.hint = "Target Date (Optional)"
        } else {
            dialogBinding.tvTaskDialogHeader.text = "Create New Task"
            dialogBinding.tilTaskTitle.hint = "Task Title"
            dialogBinding.btnSaveTask.text = "Save Task"
            dialogBinding.tilTaskDueDate.hint = "Due Date (Optional)"
        }
        dialogBinding.cbIsGoal.isChecked = isGoal
        dialogBinding.layoutGoalStartDate.visibility = if (isGoal) View.VISIBLE else View.GONE

        dialogBinding.cbIsGoal.setOnCheckedChangeListener { _, checked ->
            dialogBinding.layoutGoalStartDate.visibility = if (checked) View.VISIBLE else View.GONE
            dialogBinding.tilTaskDueDate.hint = if (checked) "Target Date (Optional)" else "Due Date (Optional)"
            if (checked) {
                dialogBinding.tvTaskDialogHeader.text = "Set a New Milestone Goal"
                dialogBinding.tilTaskTitle.hint = "Goal Title (e.g. Master Kotlin, Save 100k BDT)"
                dialogBinding.btnSaveTask.text = "Create Goal"
                dialogBinding.btnSaveTask.setBackgroundColor(0xFFD97706.toInt())
            } else {
                dialogBinding.tvTaskDialogHeader.text = "Create New Task"
                dialogBinding.tilTaskTitle.hint = "Task Title"
                dialogBinding.btnSaveTask.text = "Save Task"
                dialogBinding.btnSaveTask.setBackgroundColor(0xFF0284C7.toInt())
            }
        }

        setupDatePicker(
            dialogBinding.etTaskStartDate,
            dialogBinding.tilTaskStartDate,
            dialogBinding.btnClearStartDate,
            initialDate = null,
            title = "Select Start Date"
        )

        setupDatePicker(
            dialogBinding.etTaskDueDate,
            dialogBinding.tilTaskDueDate,
            dialogBinding.btnClearDueDate,
            initialDate = null,
            title = "Select Due Date"
        )

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

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogBinding.btnSaveTask.setOnClickListener {
            val title = dialogBinding.etTaskTitle.text?.toString()?.trim().orEmpty()
            val desc = dialogBinding.etTaskDescription.text?.toString()?.trim().orEmpty()
            val markedGoal = dialogBinding.cbIsGoal.isChecked
            val startDate = if (markedGoal) dialogBinding.etTaskStartDate.tag as? String else null
            val dueDate = dialogBinding.etTaskDueDate.tag as? String
            val category = categories.getOrNull(dialogBinding.spTaskCategory.selectedItemPosition) ?: "General"
            val priority = priorities[dialogBinding.spTaskPriority.selectedItemPosition]

            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "Task title is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val task = TaskItem(
                title = title,
                description = desc,
                category = category,
                startDate = startDate,
                dueDate = dueDate,
                priority = priority,
                isCompleted = false,
                isGoal = markedGoal,
                progress = 0
            )
            val newId = dbHelper.insertTask(task)
            TaskAlarmScheduler.scheduleTaskReminder(requireContext(), task.copy(id = newId))
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
