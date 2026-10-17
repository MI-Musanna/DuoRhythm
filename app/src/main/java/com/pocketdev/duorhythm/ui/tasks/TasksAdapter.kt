package com.pocketdev.duorhythm.ui.tasks

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.pocketdev.duorhythm.R
import com.pocketdev.duorhythm.data.model.TaskItem
import com.pocketdev.duorhythm.databinding.ItemTaskBinding

import com.pocketdev.duorhythm.util.AnimationUtils

class TasksAdapter(
    private var tasks: List<TaskItem>,
    private val onToggleCompleted: (TaskItem, Boolean) -> Unit,
    private val onTaskClick: (TaskItem) -> Unit,
    private val onDeleteClick: (TaskItem) -> Unit
) : RecyclerView.Adapter<TasksAdapter.TaskViewHolder>() {

    fun updateList(newTasks: List<TaskItem>) {
        tasks = newTasks
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(tasks[position])
        AnimationUtils.animateItemEntry(holder.itemView, position)
    }

    override fun getItemCount(): Int = tasks.size

    inner class TaskViewHolder(private val binding: ItemTaskBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(task: TaskItem) {
            val context = binding.root.context
            binding.root.setOnClickListener {
                onTaskClick(task)
            }
            binding.tvTaskTitle.text = task.title
            binding.tvTaskDescription.text = task.description
            binding.tvTaskDescription.visibility = if (task.description.isEmpty()) View.GONE else View.VISIBLE

            binding.cbTaskCompleted.setOnCheckedChangeListener(null)
            binding.cbTaskCompleted.isChecked = task.isCompleted
            if (task.isCompleted) {
                binding.tvTaskTitle.paintFlags = binding.tvTaskTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                binding.tvTaskTitle.setTextColor(ContextCompat.getColor(context, R.color.text_muted))
            } else {
                binding.tvTaskTitle.paintFlags = binding.tvTaskTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                binding.tvTaskTitle.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            }

            binding.tvTaskCategory.text = task.category

            if (task.isGoal) {
                val hasDue = !task.dueDate.isNullOrBlank()
                val hasStart = !task.startDate.isNullOrBlank()
                if (hasDue && hasStart) {
                    binding.tvTaskDueDate.text = "${com.pocketdev.duorhythm.util.TaskDateUtils.formatDisplayDate(task.startDate)} → ${com.pocketdev.duorhythm.util.TaskDateUtils.formatDisplayDate(task.dueDate)}"
                    binding.tvTaskDueDate.setTextColor(ContextCompat.getColor(context, R.color.accent_amber))
                    binding.tvTaskDueDate.visibility = View.VISIBLE
                } else if (hasDue) {
                    binding.tvTaskDueDate.text = "Target: ${com.pocketdev.duorhythm.util.TaskDateUtils.formatDisplayDate(task.dueDate)}"
                    binding.tvTaskDueDate.setTextColor(ContextCompat.getColor(context, R.color.accent_amber))
                    binding.tvTaskDueDate.visibility = View.VISIBLE
                } else if (hasStart) {
                    binding.tvTaskDueDate.text = "Started: ${com.pocketdev.duorhythm.util.TaskDateUtils.formatDisplayDate(task.startDate)}"
                    binding.tvTaskDueDate.setTextColor(ContextCompat.getColor(context, R.color.accent_amber))
                    binding.tvTaskDueDate.visibility = View.VISIBLE
                } else {
                    binding.tvTaskDueDate.visibility = View.GONE
                }
            } else {
                if (task.dueDate.isNullOrBlank()) {
                    binding.tvTaskDueDate.visibility = View.GONE
                } else {
                    val today = com.pocketdev.duorhythm.util.TaskDateUtils.getTodayStorageDate()
                    val formattedDue = com.pocketdev.duorhythm.util.TaskDateUtils.formatDisplayDate(task.dueDate)
                    if (!task.isCompleted && task.dueDate < today) {
                        binding.tvTaskDueDate.text = "$formattedDue (Overdue)"
                        binding.tvTaskDueDate.setTextColor(ContextCompat.getColor(context, R.color.priority_high))
                    } else if (task.dueDate == today) {
                        binding.tvTaskDueDate.text = "Today"
                        binding.tvTaskDueDate.setTextColor(ContextCompat.getColor(context, R.color.accent_sky))
                    } else {
                        binding.tvTaskDueDate.text = formattedDue
                        binding.tvTaskDueDate.setTextColor(ContextCompat.getColor(context, R.color.text_muted))
                    }
                    binding.tvTaskDueDate.visibility = View.VISIBLE
                }
            }

            // Priority badge
            binding.tvTaskPriority.text = task.priority
            when (task.priority) {
                "HIGH" -> {
                    binding.tvTaskPriority.setTextColor(ContextCompat.getColor(context, R.color.priority_high))
                    binding.tvTaskPriority.setBackgroundColor(0x22EF4444)
                }
                "MEDIUM" -> {
                    binding.tvTaskPriority.setTextColor(ContextCompat.getColor(context, R.color.priority_medium))
                    binding.tvTaskPriority.setBackgroundColor(0x22F59E0B)
                }
                "LOW" -> {
                    binding.tvTaskPriority.setTextColor(ContextCompat.getColor(context, R.color.priority_low))
                    binding.tvTaskPriority.setBackgroundColor(0x223B82F6)
                }
                else -> {
                    binding.tvTaskPriority.setTextColor(ContextCompat.getColor(context, R.color.priority_none))
                    binding.tvTaskPriority.setBackgroundColor(0x2294A3B8)
                }
            }

            // Goal Progress vs Task Completion
            if (task.isGoal) {
                binding.cbTaskCompleted.visibility = View.GONE
                binding.layoutGoalProgress.visibility = View.VISIBLE
                binding.pbGoalProgress.progress = task.progress
                binding.tvGoalProgressPercent.text = "${task.progress}%"
            } else {
                binding.cbTaskCompleted.visibility = View.VISIBLE
                binding.layoutGoalProgress.visibility = View.GONE
            }

            binding.cbTaskCompleted.setOnCheckedChangeListener { _, isChecked ->
                onToggleCompleted(task, isChecked)
            }

            binding.btnDeleteTask.setOnClickListener {
                onDeleteClick(task)
            }
            AnimationUtils.attachTactileFeedback(binding.btnDeleteTask)
        }
    }
}
