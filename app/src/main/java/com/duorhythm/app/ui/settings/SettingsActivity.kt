package com.duorhythm.app.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.duorhythm.app.R
import com.duorhythm.app.automation.SmartAutomationManager
import com.duorhythm.app.data.repository.BackupService
import com.duorhythm.app.data.repository.DataBackupHelper
import com.duorhythm.app.ui.history.ExportHistoryBottomSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.AppPreferences
import com.duorhythm.app.data.local.pref.AppSettings
import com.duorhythm.app.databinding.ActivitySettingsBinding
import com.duorhythm.app.scheduler.AlarmSyncManager
import com.duorhythm.app.scheduler.BriefingAlarmScheduler
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.button.MaterialButton
import android.widget.CheckBox
import android.widget.TextView
import java.util.Calendar
import com.duorhythm.app.security.LifeSyncSecurityManager
import com.duorhythm.app.worker.LocalBackupWorker
import com.duorhythm.app.worker.AutoBackupWorker
import com.duorhythm.app.service.ReminderManager
import com.duorhythm.app.ui.common.PermissionRationaleDialogs
import com.duorhythm.app.ui.tools.SystemInfoBottomSheetDialog
import com.duorhythm.app.util.AnimationUtils
import com.duorhythm.app.util.KeyboardUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SettingSearchItem(
    val title: String,
    val subtitle: String,
    val targetView: View,
    val parentCard: View,
    val parentContentLayout: View,
    val parentChevron: ImageView
)

val ActivitySettingsBinding.cardPreferencesGeneralHeader: View get() = headerPreferencesGeneral
val ActivitySettingsBinding.layoutPreferencesGeneralContent: View get() = bodyPreferencesGeneral
val ActivitySettingsBinding.ivChevronGeneral: ImageView get() = ivChevronPreferencesGeneral

val ActivitySettingsBinding.cardAcademicAutomationsHeader: View get() = headerAcademicAutomations
val ActivitySettingsBinding.layoutAcademicAutomationsContent: View get() = bodyAcademicAutomations
val ActivitySettingsBinding.ivChevronAcademic: ImageView get() = ivChevronAcademicAutomations

val ActivitySettingsBinding.cardFinanceAutomationsHeader: View get() = headerFinanceAutomations
val ActivitySettingsBinding.layoutFinanceAutomationsContent: View get() = bodyFinanceAutomations
val ActivitySettingsBinding.ivChevronFinance: ImageView get() = ivChevronFinanceAutomations

val ActivitySettingsBinding.cardWorkPortalHeader: View get() = headerWorkPortal
val ActivitySettingsBinding.layoutWorkPortalContent: View get() = bodyWorkPortal
val ActivitySettingsBinding.ivChevronWork: ImageView get() = ivChevronWorkPortal

val ActivitySettingsBinding.cardSecurityBiometricsHeader: View get() = headerSecurityBiometrics
val ActivitySettingsBinding.layoutSecurityBiometricsContent: View get() = bodySecurityBiometrics
val ActivitySettingsBinding.ivChevronSecurity: ImageView get() = ivChevronSecurityBiometrics

val ActivitySettingsBinding.cardBackupRestoreHeader: View get() = headerAccordionBackup
val ActivitySettingsBinding.layoutBackupRestoreContent: View get() = bodyAccordionBackup

val ActivitySettingsBinding.cardStorageResetHeader: View get() = headerAccordionDataSlate
val ActivitySettingsBinding.layoutStorageResetContent: View get() = bodyAccordionDataSlate
val ActivitySettingsBinding.ivChevronStorage: ImageView get() = ivChevronDataSlate

val ActivitySettingsBinding.cardAboutDeveloperHeader: View get() = headerAccordionAbout
val ActivitySettingsBinding.layoutAboutDeveloperContent: View get() = bodyAccordionAbout

val ActivitySettingsBinding.rowFirstDayOfWeek: View get() = itemStartOfWeek
val ActivitySettingsBinding.rowRunningBalance: View get() = rowShowRunningBalance
val ActivitySettingsBinding.rowAutoSms: View get() = rowAutoSmsDetection

val ActivitySettingsBinding.tvStartOfWeekValue: TextView get() = tvFirstDayOfWeekSubtitle
val ActivitySettingsBinding.switchAutoTrackAttendance: SwitchMaterial get() = switchAutoLinkAttendance
val ActivitySettingsBinding.switchClassReminders: SwitchMaterial get() = switchClassAlarms
val ActivitySettingsBinding.tvClassLeadTimeValue: TextView get() = tvClassLeadSubtitle
val ActivitySettingsBinding.switchAutoSilentClasses: SwitchMaterial get() = switchClassDnd
val ActivitySettingsBinding.switchShowRunningBalance: SwitchMaterial get() = switchRunningBalance
val ActivitySettingsBinding.switchSmartExpenseDetection: SwitchMaterial get() = switchAutoSmsDetection
val ActivitySettingsBinding.tvOrderLeadTimeValue: TextView get() = tvClientLeadSubtitle
val ActivitySettingsBinding.rowOrderLeadTime: View get() = rowClientLeadTime
val ActivitySettingsBinding.layoutSettingsRoot: ViewGroup get() = rootLayout
val ActivitySettingsBinding.cardAccordionPreferences: com.google.android.material.card.MaterialCardView get() = cardPreferencesGeneral
val ActivitySettingsBinding.cardAccordionSecurity: com.google.android.material.card.MaterialCardView get() = cardSecurityBiometrics
val ActivitySettingsBinding.cardAccordionBackup: com.google.android.material.card.MaterialCardView get() = cardBackupRestore
val ActivitySettingsBinding.cardAccordionDataSlate: com.google.android.material.card.MaterialCardView get() = cardStorageReset
val ActivitySettingsBinding.cardAccordionAbout: com.google.android.material.card.MaterialCardView get() = cardAboutDeveloper

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var dbHelper: DatabaseHelper
    private var isHydratingUI = false
    private var devClickCount = 0
    private var devFirstClickTime = 0L

    private val searchableItems = mutableListOf<SettingSearchItem>()

    private var selectedSlots: List<Int> = listOf(1, 2, 3)

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { destinationUri ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val prefs = getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
                    val includeAttachments = prefs.getBoolean("pref_backup_include_attachments", false)
                    val backupJson = DataBackupHelper.generateSelectedProfilesBackupJson(
                        selectedSlots,
                        this@SettingsActivity,
                        includeAttachments
                    )
                    contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                        outputStream.write(backupJson.toByteArray())
                    }
                    val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                    BackupService.setLastBackupTimestamp(this@SettingsActivity, now)
                    withContext(Dispatchers.Main) {
                        updateBackupUI()
                        Toast.makeText(this@SettingsActivity, "Backup saved to chosen folder!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@SettingsActivity, "Failed to write backup file.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private val importBackupLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                val (success, message) = BackupService.restoreBackupWithVerification(this, it)
                if (success) {
                    updateBackupUI()
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            isHydratingUI = true
            window.setBackgroundDrawableResource(R.drawable.bg_ambient_canvas)
            binding = ActivitySettingsBinding.inflate(layoutInflater)
            setContentView(binding.root)

            dbHelper = DatabaseHelper.getInstance(this)

            setupWindowInsets()
            setupToolbar()
            setupAccordions()
            setupPreferencesSection()
            setupAutoBackup()
            setupListeners()
            setupSearch()
            syncSwitchStates()
            refreshLeadTimesUI()
            updateBackupUI()
            isHydratingUI = false
        } catch (e: Exception) {
            e.printStackTrace()
            isHydratingUI = false
        }
    }

    override fun onResume() {
        super.onResume()
        isHydratingUI = true
        refreshStartOfWeekUI()
        refreshAutoTrackAttendanceUI()
        refreshShowRunningBalanceUI()
        refreshLeadTimesUI()
        syncSwitchStates()
        syncAutoBackupState()
        updateBackupUI()
        isHydratingUI = false
    }



    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.settingsRoot) { _, insets ->
            try {
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                binding.appBarSettings.setPadding(0, systemBars.top, 0, 0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            insets
        }
    }

    private fun setupToolbar() {
        binding.btnSettingsBack.setOnClickListener {
            finish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    fun setupAccordion(cardHeader: View, contentLayout: View, chevron: ImageView, defaultExpanded: Boolean = false) {
        contentLayout.visibility = if (defaultExpanded) View.VISIBLE else View.GONE
        chevron.rotation = if (defaultExpanded) 180f else 0f
        cardHeader.setOnClickListener {
            val isExpanding = contentLayout.visibility != View.VISIBLE
            TransitionManager.beginDelayedTransition(binding.rootLayout, AutoTransition().apply { duration = 180 })
            contentLayout.visibility = if (isExpanding) View.VISIBLE else View.GONE
            chevron.animate().rotation(if (isExpanding) 180f else 0f).setDuration(180).start()
        }
    }

    private fun setupAccordions() {
        setupAccordion(binding.cardPreferencesGeneralHeader, binding.layoutPreferencesGeneralContent, binding.ivChevronGeneral, defaultExpanded = false)
        setupAccordion(binding.cardAcademicAutomationsHeader, binding.layoutAcademicAutomationsContent, binding.ivChevronAcademic, defaultExpanded = false)
        setupAccordion(binding.cardFinanceAutomationsHeader, binding.layoutFinanceAutomationsContent, binding.ivChevronFinance, defaultExpanded = false)
        setupAccordion(binding.cardWorkPortalHeader, binding.layoutWorkPortalContent, binding.ivChevronWork, defaultExpanded = false)
        setupAccordion(binding.cardSecurityBiometricsHeader, binding.layoutSecurityBiometricsContent, binding.ivChevronSecurity, defaultExpanded = false)
        setupAccordion(binding.cardBackupRestoreHeader, binding.layoutBackupRestoreContent, binding.ivChevronBackup, defaultExpanded = false)
        setupAccordion(binding.cardStorageResetHeader, binding.layoutStorageResetContent, binding.ivChevronStorage, defaultExpanded = false)
        setupAccordion(binding.cardAboutDeveloperHeader, binding.layoutAboutDeveloperContent, binding.ivChevronAbout, defaultExpanded = false)
    }

    fun indexSettings() {
        searchableItems.clear()
        // Preferences / General
        searchableItems.add(SettingSearchItem("First Day of the Week", "Saturday start calendar configuration", binding.rowFirstDayOfWeek, binding.cardPreferencesGeneral, binding.layoutPreferencesGeneralContent, binding.ivChevronGeneral))
        searchableItems.add(SettingSearchItem("Smart Daily Briefings", "Configurable morning agenda & evening recap alarms", binding.rowDailyBriefings, binding.cardPreferencesGeneral, binding.layoutPreferencesGeneralContent, binding.ivChevronGeneral))

        // Academic & Schedule
        searchableItems.add(SettingSearchItem("Auto-link Attendance to Courses", "Automatically add newly created subjects to timetable", binding.rowAutoLinkAttendance, binding.cardAcademicAutomations, binding.layoutAcademicAutomationsContent, binding.ivChevronAcademic))
        searchableItems.add(SettingSearchItem("Class & Exam Alarms", "System alarm reminders before scheduled lectures", binding.rowClassAlarms, binding.cardAcademicAutomations, binding.layoutAcademicAutomationsContent, binding.ivChevronAcademic))
        searchableItems.add(SettingSearchItem("Class Reminder Lead Time", "Custom minutes before lecture", binding.rowClassLeadTime, binding.cardAcademicAutomations, binding.layoutAcademicAutomationsContent, binding.ivChevronAcademic))
        searchableItems.add(SettingSearchItem("Auto-Silent During Classes (DND)", "Silences ringer during scheduled lectures", binding.rowClassDnd, binding.cardAcademicAutomations, binding.layoutAcademicAutomationsContent, binding.ivChevronAcademic))

        // Financial Ledger
        searchableItems.add(SettingSearchItem("Show Running Balance", "Display remaining balance underneath transactions", binding.rowRunningBalance, binding.cardFinanceAutomations, binding.layoutFinanceAutomationsContent, binding.ivChevronFinance))
        searchableItems.add(SettingSearchItem("Subscription Renewal Alerts", "Notify 2 days before recurring payments", binding.rowSubscriptionAlerts, binding.cardFinanceAutomations, binding.layoutFinanceAutomationsContent, binding.ivChevronFinance))
        searchableItems.add(SettingSearchItem("Auto SMS & Notification Expense Detection", "1-Tap logging prompts for bKash, Nagad, bank", binding.rowAutoSms, binding.cardFinanceAutomations, binding.layoutFinanceAutomationsContent, binding.ivChevronFinance))

        // Work Portal
        searchableItems.add(SettingSearchItem("Work Portal Module", "Enable freelance and business client tracking", binding.rowWorkPortal, binding.cardWorkPortal, binding.layoutWorkPortalContent, binding.ivChevronWork))
        searchableItems.add(SettingSearchItem("Client Deadline Alerts", "Custom hours lead alert", binding.rowClientLeadTime, binding.cardWorkPortal, binding.layoutWorkPortalContent, binding.ivChevronWork))

        // Security
        searchableItems.add(SettingSearchItem("Money Tracker & Wallet Lock", "Require biometric or device PIN to access financial data", binding.rowWalletBiometric, binding.cardSecurityBiometrics, binding.layoutSecurityBiometricsContent, binding.ivChevronSecurity))
        searchableItems.add(SettingSearchItem("Workspace Profile Switching Auth", "Biometric verification on profile switch", binding.rowProfileAuth, binding.cardSecurityBiometrics, binding.layoutSecurityBiometricsContent, binding.ivChevronSecurity))
    }

    private fun setupSearch() {
        indexSettings()

        binding.etSettingsSearch.doAfterTextChanged { text ->
            val query = text?.toString()?.trim()?.lowercase().orEmpty()
            binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE

            if (query.isEmpty()) {
                // Reset state: show all cards, keep all content layouts collapsed
                val allCards = listOf(
                    Pair(binding.cardPreferencesGeneral, binding.layoutPreferencesGeneralContent),
                    Pair(binding.cardAcademicAutomations, binding.layoutAcademicAutomationsContent),
                    Pair(binding.cardFinanceAutomations, binding.layoutFinanceAutomationsContent),
                    Pair(binding.cardWorkPortal, binding.layoutWorkPortalContent),
                    Pair(binding.cardSecurityBiometrics, binding.layoutSecurityBiometricsContent)
                )
                allCards.forEach { (card, content) ->
                    card.visibility = View.VISIBLE
                    content.visibility = View.GONE
                }
                listOf(
                    binding.ivChevronGeneral,
                    binding.ivChevronAcademic,
                    binding.ivChevronFinance,
                    binding.ivChevronWork,
                    binding.ivChevronSecurity
                ).forEach {
                    it.rotation = 0f
                }
                searchableItems.forEach { it.targetView.visibility = View.VISIBLE }
                binding.rowClientLeadTime.visibility = if (dbHelper.isWorkPortalEnabled()) View.VISIBLE else View.GONE
                listOf(binding.cardBackupRestore, binding.cardStorageReset, binding.cardAboutDeveloper).forEach {
                    it.visibility = View.VISIBLE
                }
                return@doAfterTextChanged
            }

            // Group matched items by parent card
            val activeMatchingCards = mutableSetOf<View>()
            searchableItems.forEach { item ->
                val matches = item.title.lowercase().contains(query) || item.subtitle.lowercase().contains(query)
                item.targetView.visibility = if (matches) View.VISIBLE else View.GONE
                if (matches) {
                    activeMatchingCards.add(item.parentCard)
                    item.parentContentLayout.visibility = View.VISIBLE
                    item.parentChevron.rotation = 180f
                }
            }

            // Hide cards that have zero matching rows; keep cards with matches visible
            val allCategories = searchableItems.map { it.parentCard }.distinct()
            allCategories.forEach { card ->
                card.visibility = if (activeMatchingCards.contains(card)) View.VISIBLE else View.GONE
            }
            listOf(binding.cardBackupRestore, binding.cardStorageReset, binding.cardAboutDeveloper).forEach {
                it.visibility = View.GONE
            }
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSettingsSearch.text?.clear()
        }
    }

    private fun setupPreferencesSection() {
        refreshStartOfWeekUI()
        refreshAutoTrackAttendanceUI()
        refreshShowRunningBalanceUI()

        binding.itemStartOfWeek.setOnClickListener {
            showStartOfWeekDialog()
        }
        binding.rowAutoLinkAttendance.setOnClickListener {
            binding.switchAutoLinkAttendance.toggle()
        }
        binding.switchAutoLinkAttendance.setOnCheckedChangeListener { _, isChecked ->
            if (!isHydratingUI) {
                getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("pref_auto_link_attendance", isChecked).apply()
                AppSettings.setAutoTrackAttendanceEnabled(this, isChecked)
            }
        }
        binding.rowShowRunningBalance.setOnClickListener {
            binding.switchRunningBalance.toggle()
        }
        binding.switchRunningBalance.setOnCheckedChangeListener { _, isChecked ->
            if (!isHydratingUI) {
                getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("pref_show_running_balance", isChecked).apply()
                AppSettings.setShowRunningBalanceEnabled(this, isChecked)
            }
        }
    }

    private fun refreshShowRunningBalanceUI() {
        binding.switchRunningBalance.setOnCheckedChangeListener(null)
        val prefs = getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("pref_show_running_balance", AppSettings.isShowRunningBalanceEnabled(this))
        binding.switchRunningBalance.isChecked = isEnabled
        binding.switchRunningBalance.setOnCheckedChangeListener { _, isChecked ->
            if (!isHydratingUI) {
                prefs.edit().putBoolean("pref_show_running_balance", isChecked).apply()
                AppSettings.setShowRunningBalanceEnabled(this, isChecked)
            }
        }
    }

    private fun refreshAutoTrackAttendanceUI() {
        binding.switchAutoLinkAttendance.setOnCheckedChangeListener(null)
        val prefs = getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("pref_auto_link_attendance", AppSettings.isAutoTrackAttendanceEnabled(this))
        binding.switchAutoLinkAttendance.isChecked = isEnabled
        binding.switchAutoLinkAttendance.setOnCheckedChangeListener { _, isChecked ->
            if (!isHydratingUI) {
                prefs.edit().putBoolean("pref_auto_link_attendance", isChecked).apply()
                AppSettings.setAutoTrackAttendanceEnabled(this, isChecked)
            }
        }
    }

    private fun refreshStartOfWeekUI() {
        val currentStart = AppSettings.getStartOfWeek(this)
        binding.tvFirstDayOfWeekSubtitle.text = AppSettings.getStartOfWeekDisplayLabel(currentStart)
    }

    private fun showStartOfWeekDialog() {
        val currentStart = AppSettings.getStartOfWeek(this)
        val selectedIndex = AppSettings.START_OF_WEEK_OPTIONS.indexOf(currentStart).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("First Day of the Week")
            .setSingleChoiceItems(AppSettings.START_OF_WEEK_LABELS, selectedIndex) { dialog, which ->
                if (which in AppSettings.START_OF_WEEK_OPTIONS.indices) {
                    val newStart = AppSettings.START_OF_WEEK_OPTIONS[which]
                    AppSettings.setStartOfWeek(this, newStart)
                    refreshStartOfWeekUI()
                    Toast.makeText(this, "Start of week set to ${AppSettings.START_OF_WEEK_LABELS[which]}", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshLeadTimesUI() {
        val classMinutes = AppPreferences.getClassLeadMinutes(this)
        binding.tvClassLeadSubtitle.text = AppPreferences.getClassLeadMinutesDisplayLabel(classMinutes)
        val orderHours = AppPreferences.getOrderLeadHours(this)
        binding.tvClientLeadSubtitle.text = AppPreferences.getOrderLeadHoursDisplayLabel(orderHours)
    }

    private fun showCustomLeadTimeDialog(isClassMode: Boolean) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_custom_lead_time, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvLeadDialogTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvLeadDialogSubtitle)
        val switchLeadActive = dialogView.findViewById<SwitchMaterial>(R.id.switchLeadActive)
        val layoutLeadInput = dialogView.findViewById<View>(R.id.layoutLeadInput)
        val etLeadInput = dialogView.findViewById<TextInputEditText>(R.id.etLeadInput)
        val tvLeadUnit = dialogView.findViewById<TextView>(R.id.tvLeadUnit)
        val tvValidationError = dialogView.findViewById<TextView>(R.id.tvValidationError)
        val btnCancel = dialogView.findViewById<View>(R.id.btnCancelLead)
        val btnSave = dialogView.findViewById<View>(R.id.btnSaveLead)

        val minVal: Int
        val maxVal: Int
        val currentVal: Int
        val unitLabel: String
        val errorMsg: String

        if (isClassMode) {
            tvTitle.text = "Class Reminder Timing"
            tvSubtitle.text = "Set custom notification lead time before scheduled lectures"
            unitLabel = "Minutes before class"
            minVal = 1
            maxVal = 240
            errorMsg = "Enter between 1 to 240 minutes."
            currentVal = AppPreferences.getClassLeadMinutes(this)
        } else {
            tvTitle.text = "Client Deadline Alerts"
            tvSubtitle.text = "Set custom alert lead time before order deadlines"
            unitLabel = "Hours before deadline"
            minVal = 1
            maxVal = 72
            errorMsg = "Enter between 1 to 72 hours."
            currentVal = AppPreferences.getOrderLeadHours(this)
        }

        tvLeadUnit.text = unitLabel
        val isInitiallyEnabled = currentVal > 0
        switchLeadActive.isChecked = isInitiallyEnabled
        layoutLeadInput.visibility = if (isInitiallyEnabled) View.VISIBLE else View.GONE
        etLeadInput.setText(if (isInitiallyEnabled) currentVal.toString() else (if (isClassMode) "15" else "2"))
        etLeadInput.setSelection(etLeadInput.text?.length ?: 0)

        switchLeadActive.setOnCheckedChangeListener { _, isChecked ->
            layoutLeadInput.visibility = if (isChecked) View.VISIBLE else View.GONE
            tvValidationError.visibility = View.GONE
        }

        etLeadInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tvValidationError.visibility = View.GONE
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val prefs = getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
            if (!switchLeadActive.isChecked) {
                if (isClassMode) {
                    AppPreferences.setClassLeadMinutes(this, 0)
                    prefs.edit().putBoolean("pref_class_reminder_enabled", false).apply()
                    AlarmSyncManager.cancelAllClassLeadAlarms(this)
                } else {
                    AppPreferences.setOrderLeadHours(this, 0)
                    prefs.edit().putBoolean("pref_order_reminder_enabled", false).apply()
                    AlarmSyncManager.cancelAllOrderLeadAlarms(this)
                }
                refreshLeadTimesUI()
                AlarmSyncManager.resyncAll(this)
                Toast.makeText(this, "Reminder alert disabled", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                return@setOnClickListener
            }

            val text = etLeadInput.text?.toString()?.trim() ?: ""
            val inputNum = text.toIntOrNull()
            if (inputNum == null || inputNum < minVal || inputNum > maxVal) {
                tvValidationError.text = errorMsg
                tvValidationError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            if (isClassMode) {
                AppPreferences.setClassLeadMinutes(this, inputNum)
                prefs.edit().putBoolean("pref_class_reminder_enabled", true).apply()
                Toast.makeText(this, "Class reminder lead time set to $inputNum minutes", Toast.LENGTH_SHORT).show()
            } else {
                AppPreferences.setOrderLeadHours(this, inputNum)
                prefs.edit().putBoolean("pref_order_reminder_enabled", true).apply()
                Toast.makeText(this, "Deadline alert set to $inputNum hours before", Toast.LENGTH_SHORT).show()
            }

            refreshLeadTimesUI()
            AlarmSyncManager.resyncAll(this)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showTimePickerDialog(isMorning: Boolean) {
        val currentTimeStr = if (isMorning) {
            AppPreferences.getMorningBriefingTime(this)
        } else {
            AppPreferences.getEveningBriefingTime(this)
        }

        val pair = BriefingAlarmScheduler.parseTime(currentTimeStr) ?: (if (isMorning) Pair(8, 0) else Pair(20, 30))

        val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
            .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_12H)
            .setHour(pair.first)
            .setMinute(pair.second)
            .setTitleText(if (isMorning) "Morning Briefing Time" else "Evening Briefing Time")
            .build()

        picker.addOnPositiveButtonClickListener {
            val hour = picker.hour
            val minute = picker.minute
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }
            val formatted = SimpleDateFormat("hh:mm a", Locale.US).format(cal.time)
            if (isMorning) {
                AppPreferences.setMorningBriefingTime(this, formatted)
                binding.tvMorningBriefingTime.text = formatted
            } else {
                AppPreferences.setEveningBriefingTime(this, formatted)
                binding.tvEveningBriefingTime.text = formatted
            }
            BriefingAlarmScheduler.resyncBriefings(this)
            Toast.makeText(this, "Briefing time updated to $formatted", Toast.LENGTH_SHORT).show()
        }

        picker.show(supportFragmentManager, "BRIEFING_TIME_PICKER")
    }

    private fun toggleAccordion(bodyView: View, dividerView: View, chevronView: ImageView) {
        val isExpanding = bodyView.visibility != View.VISIBLE
        AnimationUtils.beginAccordionTransition(binding.layoutSettingsRoot)
        bodyView.visibility = if (isExpanding) View.VISIBLE else View.GONE
        dividerView.visibility = if (isExpanding) View.VISIBLE else View.GONE
        AnimationUtils.rotateChevron(chevronView, isExpanding)
    }

    private val backupIntervalLabels = arrayOf(
        "Every 6 Hours",
        "Every 12 Hours",
        "Every 24 Hours (Default)",
        "Every 1 Week"
    )
    private val backupIntervalHours = intArrayOf(6, 12, 24, 168)

    private fun getBackupIntervalLabel(hours: Int): String {
        return when (hours) {
            6 -> "Every 6 Hours"
            12 -> "Every 12 Hours"
            168 -> "Every 1 Week"
            else -> "Every 24 Hours (Default)"
        }
    }

    private fun setupAutoBackup() {
        val prefs = getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("pref_auto_backup_enabled", prefs.getBoolean(LocalBackupWorker.KEY_AUTO_BACKUP_ENABLED, false))
        val savedHours = prefs.getInt("pref_auto_backup_interval_hours", 24)

        binding.switchAutoBackup.isChecked = isEnabled
        binding.layoutBackupFrequency.visibility = if (isEnabled) View.VISIBLE else View.GONE
        binding.tvBackupInterval.text = getBackupIntervalLabel(savedHours)

        binding.switchAutoBackup.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isHydratingUI || !buttonView.isPressed) return@setOnCheckedChangeListener
            prefs.edit()
                .putBoolean("pref_auto_backup_enabled", isChecked)
                .putBoolean(LocalBackupWorker.KEY_AUTO_BACKUP_ENABLED, isChecked)
                .apply()

            TransitionManager.beginDelayedTransition(binding.layoutSettingsRoot)
            binding.layoutBackupFrequency.visibility = if (isChecked) View.VISIBLE else View.GONE

            if (isChecked) {
                val currentHours = prefs.getInt("pref_auto_backup_interval_hours", 24)
                AutoBackupWorker.schedulePeriodicBackup(this, currentHours)
                Toast.makeText(this, "Automatic local backup scheduled (${getBackupIntervalLabel(currentHours)})", Toast.LENGTH_SHORT).show()
            } else {
                AutoBackupWorker.cancel(this)
                Toast.makeText(this, "Automatic local backup disabled", Toast.LENGTH_SHORT).show()
            }
        }

        binding.layoutBackupFrequency.setOnClickListener {
            showBackupIntervalDialog()
        }

        syncAutoBackupState()
    }

    private fun showBackupIntervalDialog() {
        val prefs = getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
        val currentHours = prefs.getInt("pref_auto_backup_interval_hours", 24)
        val selectedIndex = backupIntervalHours.indexOf(currentHours).let { if (it >= 0) it else 2 }

        MaterialAlertDialogBuilder(this)
            .setTitle("Backup Frequency Interval")
            .setSingleChoiceItems(backupIntervalLabels, selectedIndex) { dialog, which ->
                if (which in backupIntervalHours.indices) {
                    val chosenHours = backupIntervalHours[which]
                    prefs.edit()
                        .putInt("pref_auto_backup_interval_hours", chosenHours)
                        .apply()
                    binding.tvBackupInterval.text = backupIntervalLabels[which]
                    if (binding.switchAutoBackup.isChecked) {
                        AutoBackupWorker.schedulePeriodicBackup(this, chosenHours)
                    }
                    Toast.makeText(this, "Auto-backup interval set to ${backupIntervalLabels[which]}", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun syncAutoBackupState() {
        val prefs = getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("pref_auto_backup_enabled", prefs.getBoolean(LocalBackupWorker.KEY_AUTO_BACKUP_ENABLED, false))
        val intervalHours = prefs.getInt("pref_auto_backup_interval_hours", 24)

        binding.switchAutoBackup.isChecked = isEnabled
        binding.layoutBackupFrequency.visibility = if (isEnabled) View.VISIBLE else View.GONE
        binding.tvBackupInterval.text = getBackupIntervalLabel(intervalHours)
    }

    private fun showBackupProfilesDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_backup_profiles, null)
        val cbSlot1 = dialogView.findViewById<CheckBox>(R.id.cbProfileSlot1)
        val cbSlot2 = dialogView.findViewById<CheckBox>(R.id.cbProfileSlot2)
        val cbSlot3 = dialogView.findViewById<CheckBox>(R.id.cbProfileSlot3)
        val switchInclude = dialogView.findViewById<SwitchMaterial>(R.id.switchIncludeAttachments)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancelBackupDialog)
        val btnConfirm = dialogView.findViewById<MaterialButton>(R.id.btnConfirmExportBackup)

        val prefs = getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
        val includeAttachments = prefs.getBoolean("pref_backup_include_attachments", false)
        switchInclude.isChecked = includeAttachments

        val profiles = try { dbHelper.getAllProfiles() } catch (_: Exception) { emptyList() }
        val p1 = profiles.firstOrNull { it.id == 1L }
        val p2 = profiles.firstOrNull { it.id == 2L }
        val p3 = profiles.firstOrNull { it.id == 3L }
        if (p1 != null) cbSlot1.text = "${p1.name} (Slot 1)"
        if (p2 != null) cbSlot2.text = "${p2.name} (Slot 2)"
        if (p3 != null) cbSlot3.text = "${p3.name} (Slot 3)"

        cbSlot1.isChecked = selectedSlots.contains(1)
        cbSlot2.isChecked = selectedSlots.contains(2)
        cbSlot3.isChecked = selectedSlots.contains(3)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            val chosen = mutableListOf<Int>()
            if (cbSlot1.isChecked) chosen.add(1)
            if (cbSlot2.isChecked) chosen.add(2)
            if (cbSlot3.isChecked) chosen.add(3)

            if (chosen.isEmpty()) {
                Toast.makeText(this, "Please select at least one workspace profile", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val shouldInclude = switchInclude.isChecked
            prefs.edit().putBoolean("pref_backup_include_attachments", shouldInclude).apply()
            binding.switchIncludeAttachments.isChecked = shouldInclude
            selectedSlots = chosen

            dialog.dismiss()

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val defaultFileName = "DuoRhythm_Backup_$timeStamp.json"
            exportBackupLauncher.launch(defaultFileName)
        }

        dialog.show()
    }

    private fun setupListeners() {
        binding.nestedScrollViewSettings.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (Math.abs(scrollY - oldScrollY) > 5) {
                KeyboardUtils.hideKeyboard(this)
            }
        }
        binding.nestedScrollViewSettings.setOnTouchListener { _, motionEvent ->
            if (motionEvent.action == android.view.MotionEvent.ACTION_MOVE) {
                KeyboardUtils.hideKeyboard(this)
            }
            false
        }

        // --- 1. Streamlined Data Backup & Restore Hub ---
        binding.btnExportBackup.setOnClickListener {
            showBackupProfilesDialog()
        }

        binding.btnImportBackup.setOnClickListener {
            importBackupLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
        }

        binding.rowExportHistory.setOnClickListener {
            ExportHistoryBottomSheet().show(supportFragmentManager, "EXPORT_HISTORY")
        }

        // --- 2. Storage & Reset ---
        binding.btnLoadDemoData.visibility = View.GONE

        binding.btnResetAppData.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Reset All Data")
                .setMessage("Erase all tasks, schedule, and ledger records? This cannot be undone.")
                .setPositiveButton("Reset All") { _, _ ->
                    dbHelper.clearAllUserData()
                    getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
                        .edit().remove("cash_funds_usd").apply()
                    Toast.makeText(this, "All data has been reset", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
                .apply {
                    getButton(android.content.DialogInterface.BUTTON_POSITIVE)?.setTextColor(Color.parseColor("#EF4444"))
                }
        }

        binding.btnLoadDemoData.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Load Demo Data")
                .setMessage("Populate sample preview data across wallet, tasks, and timetable?")
                .setPositiveButton("Load Demo") { _, _ ->
                    dbHelper.seedDemoData()
                    Toast.makeText(this, "Sample demo data loaded", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Hidden Developer Easter Egg: 7 taps within 3 seconds
        val easterEggListener = View.OnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - devFirstClickTime > 3000L) {
                devClickCount = 1
                devFirstClickTime = currentTime
            } else {
                devClickCount++
                if (devClickCount >= 7) {
                    devClickCount = 0
                    devFirstClickTime = 0L
                    binding.btnLoadDemoData.visibility = View.VISIBLE
                    if (binding.bodyAccordionDataSlate.visibility != View.VISIBLE) {
                        binding.bodyAccordionDataSlate.visibility = View.VISIBLE
                        binding.ivChevronDataSlate.rotation = 180f
                        binding.dividerAccordionDataSlate.visibility = View.VISIBLE
                    }
                    Snackbar.make(binding.root, "Developer Options unlocked 🛠️", Snackbar.LENGTH_LONG)
                        .setBackgroundTint(androidx.core.content.ContextCompat.getColor(this, R.color.surface_variant))
                        .setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_primary))
                        .show()
                }
            }
        }
        binding.tvDeveloperBio.setOnClickListener(easterEggListener)
        binding.tvDeveloperName.setOnClickListener(easterEggListener)
        binding.tvAppVersion.setOnClickListener(easterEggListener)

        // --- 3. About Developer ---
        binding.btnOpenAboutModal.setOnClickListener {
            SystemInfoBottomSheetDialog(this).show()
        }

        binding.cardAboutHub.setOnClickListener {
            openExternalUrl("https://taplink.cc/mi2007")
        }

        binding.cardAboutGithub.setOnClickListener {
            openExternalUrl("https://github.com/MI-Musanna")
        }

        binding.cardAboutTelegram.setOnClickListener {
            openExternalUrl("https://t.me/NetHunter2007")
        }

        binding.cardAboutBlog.setOnClickListener {
            openExternalUrl("https://mitecharsenal.blogspot.com")
        }

        binding.tvAppVersion.text = "DuoRhythm v${com.duorhythm.app.util.AppUpdateChecker.getAppVersion(this)}"
        binding.btnCheckUpdates.setOnClickListener {
            com.duorhythm.app.util.AppUpdateChecker.checkForUpdates(this, manual = true)
        }

        binding.rowClassLeadTime.setOnClickListener {
            showCustomLeadTimeDialog(isClassMode = true)
        }
        binding.rowClientLeadTime.setOnClickListener {
            showCustomLeadTimeDialog(isClassMode = false)
        }
        binding.rowDailyBriefings.setOnClickListener {
            binding.switchDailyBriefings.toggle()
        }
        binding.rowClassAlarms.setOnClickListener {
            binding.switchClassAlarms.toggle()
        }
        binding.rowClassDnd.setOnClickListener {
            binding.switchClassDnd.toggle()
        }
        binding.rowSubscriptionAlerts.setOnClickListener {
            binding.switchSubscriptionAlerts.toggle()
        }
        binding.rowAutoSmsDetection.setOnClickListener {
            binding.switchAutoSmsDetection.toggle()
        }
        binding.rowWorkPortal.setOnClickListener {
            binding.switchWorkPortal.toggle()
        }
        binding.rowWalletBiometric.setOnClickListener {
            binding.switchWalletBiometric.toggle()
        }

        AnimationUtils.attachTactileFeedback(
            binding.btnExportBackup,
            binding.btnImportBackup,
            binding.btnResetAppData,
            binding.btnLoadDemoData,
            binding.btnOpenAboutModal,
            binding.btnCheckUpdates,
            binding.cardAboutHub,
            binding.cardAboutGithub,
            binding.cardAboutTelegram,
            binding.cardAboutBlog,
            binding.rowClassLeadTime,
            binding.rowClientLeadTime,
            binding.rowExportHistory
        )
    }

    private fun updateBackupUI() {
        val lastBackup = BackupService.getLastBackupTimestamp(this)
        binding.tvLastBackupStatus.text = "Last Backup: $lastBackup"
    }

    private fun openExternalUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open link: $url", Toast.LENGTH_SHORT).show()
        }
    }

    private fun syncSwitchStates() {
        val backupPrefs = getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)

        // Include Attachments in Backup
        binding.switchIncludeAttachments.setOnCheckedChangeListener(null)
        binding.switchIncludeAttachments.isChecked = backupPrefs.getBoolean("pref_backup_include_attachments", false)
        binding.switchIncludeAttachments.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isHydratingUI || !buttonView.isPressed) return@setOnCheckedChangeListener
            backupPrefs.edit().putBoolean("pref_backup_include_attachments", isChecked).apply()
        }

        // Card 1: Preferences / General - Daily Briefings
        binding.switchDailyBriefings.setOnCheckedChangeListener(null)
        val briefingsEnabled = SmartAutomationManager.isSmartBriefingsEnabled(this)
        binding.switchDailyBriefings.isChecked = briefingsEnabled
        binding.layoutBriefingTimes.visibility = if (briefingsEnabled) View.VISIBLE else View.GONE
        binding.tvMorningBriefingTime.text = AppPreferences.getMorningBriefingTime(this)
        binding.tvEveningBriefingTime.text = AppPreferences.getEveningBriefingTime(this)
        binding.switchDailyBriefings.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isHydratingUI || !buttonView.isPressed) return@setOnCheckedChangeListener
            SmartAutomationManager.setSmartBriefingsEnabled(this, isChecked)
            binding.layoutBriefingTimes.visibility = if (isChecked) View.VISIBLE else View.GONE
            BriefingAlarmScheduler.resyncBriefings(this)
            val msg = if (isChecked) "Daily briefings enabled" else "Daily briefings disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        binding.rowMorningBriefing.setOnClickListener {
            showTimePickerDialog(isMorning = true)
        }
        binding.rowEveningBriefing.setOnClickListener {
            showTimePickerDialog(isMorning = false)
        }

        // Card 2: Academic - Auto-link Attendance
        refreshAutoTrackAttendanceUI()

        // Card 2: Academic - Class & Exam Alarms
        binding.switchClassAlarms.setOnCheckedChangeListener(null)
        binding.switchClassAlarms.isChecked = ReminderManager.isRemindersEnabled(this)
        binding.switchClassAlarms.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isHydratingUI || !buttonView.isPressed) return@setOnCheckedChangeListener
            ReminderManager.setRemindersEnabled(this, isChecked)
            AlarmSyncManager.resyncAll(this)
            val msg = if (isChecked) "Class alarms enabled" else "Class alarms disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Card 2: Academic - Auto-Silent During Classes (DND)
        binding.switchClassDnd.setOnCheckedChangeListener(null)
        val isDnd = backupPrefs.getBoolean("pref_class_dnd_enabled", SmartAutomationManager.isAutoSilentClassesEnabled(this))
        binding.switchClassDnd.isChecked = isDnd
        binding.switchClassDnd.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isHydratingUI || !buttonView.isPressed) return@setOnCheckedChangeListener
            backupPrefs.edit().putBoolean("pref_class_dnd_enabled", isChecked).apply()
            if (isChecked && !SmartAutomationManager.isNotificationPolicyAccessGranted(this)) {
                PermissionRationaleDialogs.showDndRationale(this)
            }
            SmartAutomationManager.setAutoSilentClassesEnabled(this, isChecked)
            AlarmSyncManager.resyncAll(this)
            val msg = if (isChecked) "Auto-silent during classes activated" else "Auto-silent disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Card 3: Financial - Show Running Balance
        refreshShowRunningBalanceUI()

        // Card 3: Financial - Subscription Renewal Alerts
        binding.switchSubscriptionAlerts.setOnCheckedChangeListener(null)
        binding.switchSubscriptionAlerts.isChecked = SmartAutomationManager.isSubscriptionAlertsEnabled(this)
        binding.switchSubscriptionAlerts.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isHydratingUI || !buttonView.isPressed) return@setOnCheckedChangeListener
            SmartAutomationManager.setSubscriptionAlertsEnabled(this, isChecked)
            val msg = if (isChecked) "Subscription alerts & auto-log enabled" else "Subscription alerts disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Card 3: Financial - Auto SMS & Notification Expense Detection
        binding.switchAutoSmsDetection.setOnCheckedChangeListener(null)
        binding.switchAutoSmsDetection.isChecked = SmartAutomationManager.isExpenseDetectionEnabled(this)
        binding.switchAutoSmsDetection.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isHydratingUI || !buttonView.isPressed) return@setOnCheckedChangeListener
            if (isChecked && !SmartAutomationManager.isNotificationListenerEnabled(this)) {
                PermissionRationaleDialogs.showNotificationListenerRationale(this)
            }
            SmartAutomationManager.setExpenseDetectionEnabled(this, isChecked)
            val msg = if (isChecked) "Smart expense detection activated" else "Smart expense detection disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Card 4: Work - Work Portal Module & Client Deadline Alerts
        val isWorkEnabled = dbHelper.isWorkPortalEnabled()
        binding.switchWorkPortal.setOnCheckedChangeListener(null)
        binding.switchWorkPortal.isChecked = isWorkEnabled
        binding.rowClientLeadTime.visibility = if (isWorkEnabled) View.VISIBLE else View.GONE
        binding.switchWorkPortal.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isHydratingUI || !buttonView.isPressed) return@setOnCheckedChangeListener
            dbHelper.setWorkPortalEnabled(isChecked)
            binding.rowClientLeadTime.visibility = if (isChecked) View.VISIBLE else View.GONE
            val msg = if (isChecked) "Work Portal module enabled" else "Work Portal disabled (Student Mode only)"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Card 5: Security - Wallet Biometric Lock
        binding.switchWalletBiometric.setOnCheckedChangeListener(null)
        binding.switchWalletBiometric.isChecked = AppPreferences.isWalletBiometricLocked(this)
        binding.switchWalletBiometric.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isHydratingUI || !buttonView.isPressed) return@setOnCheckedChangeListener
            if (isChecked) {
                LifeSyncSecurityManager.authenticate(
                    this,
                    title = "Verify Identity",
                    subtitle = "Confirm credentials to lock wallet",
                    onSuccess = {
                        AppPreferences.setWalletBiometricLocked(this, true)
                        backupPrefs.edit().putBoolean("pref_wallet_biometric_locked", true).apply()
                        Toast.makeText(this, "Wallet Biometric Lock Activated", Toast.LENGTH_SHORT).show()
                    },
                    onError = { err ->
                        binding.switchWalletBiometric.isChecked = false
                        Toast.makeText(this, "Lock not enabled: $err", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                AppPreferences.setWalletBiometricLocked(this, false)
                backupPrefs.edit().putBoolean("pref_wallet_biometric_locked", false).apply()
                Toast.makeText(this, "Wallet Biometric Lock Disabled", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
