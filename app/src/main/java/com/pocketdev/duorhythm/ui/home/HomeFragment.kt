package com.pocketdev.duorhythm.ui.home

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.pocketdev.duorhythm.MainActivity
import com.pocketdev.duorhythm.R
import com.pocketdev.duorhythm.data.local.db.DatabaseHelper
import com.pocketdev.duorhythm.databinding.FragmentHomeBinding
import android.app.AlertDialog
import android.widget.ArrayAdapter
import android.widget.Toast
import com.pocketdev.duorhythm.data.model.NoteItem
import com.pocketdev.duorhythm.databinding.DialogAddNoteBinding
import com.pocketdev.duorhythm.ui.tools.AmbientSoundDialog
import com.pocketdev.duorhythm.ui.tools.PercentageCalcDialog
import com.pocketdev.duorhythm.ui.tools.PomodoroDialog
import com.pocketdev.duorhythm.ui.wallet.WalletDataSyncManager
import com.pocketdev.duorhythm.util.BlurUtil
import com.pocketdev.duorhythm.util.AnimationUtils
import android.content.Context
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var quickNotesAdapter: QuickNotesAdapter

    private val walletSyncListener: () -> Unit = {
        activity?.runOnUiThread {
            if (isAdded && _binding != null) {
                loadDashboardData()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            WalletDataSyncManager.register(walletSyncListener)
            dbHelper = DatabaseHelper.getInstance(requireContext())
            quickNotesAdapter = QuickNotesAdapter(
                emptyList(),
                onNoteClick = { showEditNoteDialog(it) },
                onNoteDelete = { confirmDeleteNote(it) }
            )
            binding.rvHomeQuickNotes.adapter = quickNotesAdapter
            setupBlurEffects()
            loadUserProfile()
            setupGreetingAndDate()
            setupListeners()
            loadDashboardData()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            try {
                loadUserProfile()
                loadDashboardData()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && _binding != null) {
            try {
                loadUserProfile()
                loadDashboardData()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    private fun setupBlurEffects() {
        try {
            val rootView = activity?.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)
                ?: (binding.root as? ViewGroup)
            if (rootView != null) {
                BlurUtil.setupBlur(binding.blurHeader, rootView)
                BlurUtil.setupBlur(binding.cardHomeSchedule, rootView)
                BlurUtil.setupBlur(binding.cardHomeWallet, rootView)
                BlurUtil.setupBlur(binding.cardHomeTasks, rootView)
                BlurUtil.setupBlur(binding.cardHomeQuickNotes, rootView)
                BlurUtil.setupBlur(binding.cardHomePortal, rootView)
                BlurUtil.setupBlur(binding.btnHomeOpenTools, rootView)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun loadUserProfile() {
        if (_binding == null) return
        try {
            val context = context ?: return
            val profilePrefs = context.getSharedPreferences("duorhythm_user_profile_prefs", Context.MODE_PRIVATE)
            val savedName = profilePrefs.getString(com.pocketdev.duorhythm.data.local.pref.UserProfileManager.KEY_USER_NAME, null)?.trim()
                ?: profilePrefs.getString("user_display_name", null)?.trim()
            val profileName = if (!savedName.isNullOrEmpty()) savedName else "Scholar"
            binding.tvHomeGreeting.text = "Welcome back,"
            binding.tvUserName.text = profileName
            binding.tvUserName.visibility = View.VISIBLE

            val avatarBitmap = com.pocketdev.duorhythm.data.local.pref.UserProfileManager.loadCircularAvatarBitmap(context, 80)
            if (avatarBitmap != null) {
                binding.ivHomeAvatar.setPadding(0, 0, 0, 0)
                binding.ivHomeAvatar.imageTintList = null
                binding.ivHomeAvatar.setImageBitmap(avatarBitmap)
            } else {
                val density = resources.displayMetrics.density
                val pad8 = (8 * density).toInt()
                binding.ivHomeAvatar.setPadding(pad8, pad8, pad8, pad8)
                binding.ivHomeAvatar.setImageResource(R.drawable.ic_person)
                binding.ivHomeAvatar.imageTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
            }

            val openSettings = View.OnClickListener {
                startActivity(android.content.Intent(requireContext(), com.pocketdev.duorhythm.ui.settings.SettingsActivity::class.java))
            }
            binding.containerHomeAvatar.setOnClickListener(openSettings)
            binding.tvUserName.setOnClickListener(openSettings)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupGreetingAndDate() {
        try {
            val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.US)
            binding.tvHomeLiveDate.text = dateFormat.format(Date())

            val workPortalEnabled = dbHelper.isWorkPortalEnabled()
            val mainActivity = activity as? MainActivity
            val isStudent = !workPortalEnabled || (mainActivity?.isStudentProfile() ?: true)

            if (!workPortalEnabled) {
                binding.toggleHomeProfile.visibility = View.GONE
            } else {
                binding.toggleHomeProfile.visibility = View.VISIBLE
            }

            val targetId = if (isStudent) R.id.btnHomeProfileStudent else R.id.btnHomeProfileWork
            if (binding.toggleHomeProfile.checkedButtonId != targetId) {
                binding.toggleHomeProfile.check(targetId)
            }
            updateToggleStyle(isStudent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateToggleStyle(isStudent: Boolean) {
        try {
            val cyanColor = ColorStateList.valueOf(Color.parseColor("#0284C7"))
            val emeraldColor = ColorStateList.valueOf(Color.parseColor("#10B981"))
            val transparentColor = ColorStateList.valueOf(Color.TRANSPARENT)

            if (isStudent) {
                binding.btnHomeProfileStudent.backgroundTintList = cyanColor
                binding.btnHomeProfileStudent.strokeWidth = 0
                binding.btnHomeProfileStudent.setTextColor(Color.WHITE)
                binding.btnHomeProfileStudent.elevation = 0f

                binding.btnHomeProfileWork.backgroundTintList = transparentColor
                binding.btnHomeProfileWork.strokeWidth = 0
                binding.btnHomeProfileWork.setTextColor(Color.parseColor("#475569"))
                binding.btnHomeProfileWork.elevation = 0f

                // Sync Portal Shortcut Card to Student Mode
                binding.badgeHomePortal.setBackgroundResource(R.drawable.bg_squircle_portal_student)
                binding.ivHomePortalIcon.setColorFilter(Color.parseColor("#38BDF8"))
                binding.btnHomePortalAction.setBackgroundResource(R.drawable.bg_portal_pill_student)
                binding.btnHomePortalAction.setTextColor(Color.parseColor("#38BDF8"))
            } else {
                binding.btnHomeProfileStudent.backgroundTintList = transparentColor
                binding.btnHomeProfileStudent.strokeWidth = 0
                binding.btnHomeProfileStudent.setTextColor(Color.parseColor("#475569"))
                binding.btnHomeProfileStudent.elevation = 0f

                binding.btnHomeProfileWork.backgroundTintList = emeraldColor
                binding.btnHomeProfileWork.strokeWidth = 0
                binding.btnHomeProfileWork.setTextColor(Color.WHITE)
                binding.btnHomeProfileWork.elevation = 0f

                // Sync Portal Shortcut Card to Work Mode
                binding.badgeHomePortal.setBackgroundResource(R.drawable.bg_squircle_portal_work)
                binding.ivHomePortalIcon.setColorFilter(Color.parseColor("#10B981"))
                binding.btnHomePortalAction.setBackgroundResource(R.drawable.bg_portal_pill_work)
                binding.btnHomePortalAction.setTextColor(Color.parseColor("#10B981"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupListeners() {
        try {
            val mainActivity = activity as? MainActivity

            binding.toggleHomeProfile.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    val isStudent = checkedId == R.id.btnHomeProfileStudent
                    mainActivity?.setStudentProfile(isStudent)
                    updateToggleStyle(isStudent)
                    loadDashboardData()
                }
            }

            binding.cardHomeWallet.setOnClickListener {
                mainActivity?.selectNavTab(R.id.menu_wallet)
            }

            binding.cardHomeTasks.setOnClickListener {
                mainActivity?.selectNavTab(R.id.menu_tasks)
            }

            binding.cardHomeSchedule.setOnClickListener {
                mainActivity?.selectNavTab(R.id.menu_schedule)
            }

            binding.cardHomePortal.setOnClickListener {
                mainActivity?.selectNavTab(R.id.menu_portal)
            }

            binding.toolHomePomodoro.setOnClickListener {
                PomodoroDialog(requireContext()).show()
            }

            binding.toolHomeMusic.setOnClickListener {
                AmbientSoundDialog(requireContext()).show()
            }

            binding.toolHomeCalc.setOnClickListener {
                PercentageCalcDialog(requireContext()).show()
            }

            binding.toolHomeBackup.setOnClickListener {
                mainActivity?.openTools()
            }

            binding.btnHomeOpenTools.setOnClickListener {
                mainActivity?.openTools()
            }

            binding.btnHomeAddNote.setOnClickListener {
                showAddNoteDialog()
            }

            AnimationUtils.attachTactileFeedback(
                binding.cardHomeWallet,
                binding.cardHomeTasks,
                binding.cardHomeSchedule,
                binding.cardHomePortal,
                binding.btnHomeOpenTools,
                binding.btnHomeAddNote,
                binding.toolHomePomodoro,
                binding.toolHomeMusic,
                binding.toolHomeCalc,
                binding.toolHomeBackup,
                binding.containerHomeAvatar
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isBangladeshLocale(): Boolean {
        val loc = Locale.getDefault()
        val tz = java.util.TimeZone.getDefault().id
        return loc.country.equals("BD", ignoreCase = true) ||
                loc.language.equals("bn", ignoreCase = true) ||
                tz.contains("Dhaka", ignoreCase = true) ||
                tz.contains("Bangladesh", ignoreCase = true)
    }

    fun loadDashboardData() {
        if (_binding == null || !isAdded || !::dbHelper.isInitialized) return
        try {
            val ctx = context ?: return
            val prefs = ctx.getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
            val defaultCurrency = if (isBangladeshLocale()) "BDT" else "USD"
            val selectedCurrency = prefs.getString("selected_currency", defaultCurrency) ?: defaultCurrency
            val ratesList = dbHelper.getAllCurrencyRates()
            val rateObj = ratesList.find { it.code == selectedCurrency } ?: ratesList.firstOrNull()
            val currentSymbol = if (selectedCurrency == "BDT") "৳" else (rateObj?.symbol ?: "৳")

            // 1. Finance Overview - 100% RAW Local Currency (Pure 1:1 Parity with Wallet Ledger)
            val totalRawBalance = dbHelper.getTotalBalance() // e.g., 22460.0
            val monthlyIncome = dbHelper.getMonthlyIncome()   // e.g., 2000.0
            val monthlyExpense = dbHelper.getMonthlyExpense() // e.g., 1700.0

            val numberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }

            // Total Net Balance: e.g. ৳22,460.00
            binding.tvHomeBalance.text = if (totalRawBalance < 0) {
                "-$currentSymbol" + numberFormat.format(kotlin.math.abs(totalRawBalance))
            } else {
                currentSymbol + numberFormat.format(totalRawBalance)
            }

            // Monthly Income and Expense: e.g. +৳2,000.00 / -৳1,700.00
            val formattedIncome = numberFormat.format(monthlyIncome)
            val formattedExpense = numberFormat.format(monthlyExpense)
            binding.tvHomeIncomeExpenseBrief.text = "+$currentSymbol${formattedIncome} / -$currentSymbol${formattedExpense}"

            val trendData = if (totalRawBalance >= 0) {
                floatArrayOf(0.2f, 0.35f, 0.45f, 0.38f, 0.65f, 0.58f, 0.82f, 1.0f)
            } else {
                floatArrayOf(0.9f, 0.78f, 0.85f, 0.62f, 0.55f, 0.4f, 0.32f, 0.18f)
            }
            binding.sparklineHomeBalance.setPoints(trendData)

            // 2. Tasks / Missions Overview
            val allTasks = dbHelper.getAllTasks("ALL")
            val pendingTasks = allTasks.filter { !it.isCompleted }
            val completedTasks = allTasks.filter { it.isCompleted }
            val highPriorityCount = pendingTasks.count { it.priority.equals("HIGH", ignoreCase = true) }
            val pendingCount = pendingTasks.size
            binding.tvHomeTasksCount.text = "$pendingCount Pending"
            val totalTasks = allTasks.size
            val fraction = if (totalTasks > 0) completedTasks.size.toFloat() / totalTasks.toFloat() else 0f
            binding.progressRingHomeTasks.setProgress(
                fraction,
                if (totalTasks == 0) "0/0" else "${completedTasks.size}/$totalTasks",
                "DONE"
            )
            if (highPriorityCount > 0) {
                binding.tvHomeTasksPriorityBrief.text = "$highPriorityCount high priority"
            } else if (pendingCount == 0) {
                binding.tvHomeTasksPriorityBrief.text = "All tasks clear"
            } else {
                binding.tvHomeTasksPriorityBrief.text = "Standard priority queue"
            }

            // 3. Next Up Class / Schedule
            val allClasses = dbHelper.getAllClasses()
            val calendar = Calendar.getInstance()
            var dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1 // 1=Mon ... 7=Sun
            if (dayOfWeek <= 0) dayOfWeek = 7
            val todayClasses = allClasses.filter { it.dayOfWeek == dayOfWeek }
            if (todayClasses.isNotEmpty()) {
                val nextClass = todayClasses.first()
                binding.tvHomeScheduleSummary.text = nextClass.subjectName
                val roomText = if (nextClass.room.isNotBlank()) "Room ${nextClass.room} • " else ""
                binding.tvHomeScheduleSubtext.text = "$roomText${nextClass.startTime} (${todayClasses.size} classes today)"
            } else {
                binding.tvHomeScheduleSummary.text = "No Classes Today"
                binding.tvHomeScheduleSubtext.text = "All clear • Timetable on track"
            }

            // 4. Portal Overview
            val workPortalEnabled = dbHelper.isWorkPortalEnabled()
            val isStudent = !workPortalEnabled || ((activity as? MainActivity)?.isStudentProfile() ?: true)

            if (!workPortalEnabled) {
                binding.toggleHomeProfile.visibility = View.GONE
            } else {
                binding.toggleHomeProfile.visibility = View.VISIBLE
            }

            val targetId = if (isStudent) R.id.btnHomeProfileStudent else R.id.btnHomeProfileWork
            if (binding.toggleHomeProfile.checkedButtonId != targetId) {
                binding.toggleHomeProfile.check(targetId)
            }
            updateToggleStyle(isStudent)

            if (isStudent) {
                binding.tvHomePortalTitle.text = "Study Portal"
                val subjects = dbHelper.getAllStudentSubjects()
                binding.tvHomePortalSummary.text = "${subjects.size} Enrolled Subjects"
                binding.tvHomePortalSubtext.text = "Assignments, exams, and notes"
            } else {
                binding.tvHomePortalTitle.text = "Work Portal"
                val workOrders = dbHelper.getAllWorkOrders()
                val pendingOrders = workOrders.count { !it.status.equals("COMPLETED", ignoreCase = true) }
                binding.tvHomePortalSummary.text = "$pendingOrders Active Orders"
                binding.tvHomePortalSubtext.text = "Client projects and deliverables"
            }

            // 4. Quick Notes & Scratchpad
            loadDashboardNotes()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadDashboardNotes() {
        if (_binding == null) return
        try {
            val notes = dbHelper.getAllNotes()
            quickNotesAdapter.updateList(notes)
            if (notes.isEmpty()) {
                binding.rvHomeQuickNotes.visibility = View.GONE
                binding.tvEmptyHomeQuickNotes.visibility = View.VISIBLE
            } else {
                binding.rvHomeQuickNotes.visibility = View.VISIBLE
                binding.tvEmptyHomeQuickNotes.visibility = View.GONE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showAddNoteDialog() {
        val dialogBinding = DialogAddNoteBinding.inflate(layoutInflater)
        val categories = listOf("General", "Study", "Work", "Lecture", "Ideas", "Formula")
        dialogBinding.spNoteCategory.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categories)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnSaveNote.setOnClickListener {
            val title = dialogBinding.etNoteTitle.text?.toString()?.trim().orEmpty()
            val content = dialogBinding.etNoteContent.text?.toString()?.trim().orEmpty()
            val category = categories[dialogBinding.spNoteCategory.selectedItemPosition]

            if (title.isEmpty() && content.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a title or note content", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val note = NoteItem(
                title = if (title.isNotEmpty()) title else "Quick Note",
                content = content,
                category = category,
                updatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            )
            dbHelper.insertNote(note)
            dialog.dismiss()
            loadDashboardNotes()
            Toast.makeText(requireContext(), "Note saved to Scratchpad!", Toast.LENGTH_SHORT).show()
        }

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
    }

    private fun showEditNoteDialog(note: NoteItem) {
        val dialogBinding = DialogAddNoteBinding.inflate(layoutInflater)
        dialogBinding.etNoteTitle.setText(note.title)
        dialogBinding.etNoteContent.setText(note.content)
        dialogBinding.btnSaveNote.text = "Save Changes"

        val categories = listOf("General", "Study", "Work", "Lecture", "Ideas", "Formula")
        dialogBinding.spNoteCategory.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categories)
        val catIdx = categories.indexOf(note.category).coerceAtLeast(0)
        dialogBinding.spNoteCategory.setSelection(catIdx)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnSaveNote.setOnClickListener {
            val title = dialogBinding.etNoteTitle.text?.toString()?.trim().orEmpty()
            val content = dialogBinding.etNoteContent.text?.toString()?.trim().orEmpty()
            val category = categories[dialogBinding.spNoteCategory.selectedItemPosition]

            if (title.isEmpty() && content.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter a title or note content", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updated = note.copy(
                title = if (title.isNotEmpty()) title else "Quick Note",
                content = content,
                category = category,
                updatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            )
            dbHelper.updateNote(updated)
            dialog.dismiss()
            loadDashboardNotes()
            Toast.makeText(requireContext(), "Note updated!", Toast.LENGTH_SHORT).show()
        }

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
    }

    private fun confirmDeleteNote(note: NoteItem) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Quick Note")
            .setMessage("Delete '${note.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                dbHelper.deleteNote(note.id)
                loadDashboardNotes()
                Toast.makeText(requireContext(), "Note deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        WalletDataSyncManager.unregister(walletSyncListener)
        super.onDestroyView()
        _binding = null
    }
}
