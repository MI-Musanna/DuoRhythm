package com.duorhythm.app.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.transition.TransitionManager
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.duorhythm.app.R
import com.duorhythm.app.automation.SmartAutomationManager
import com.duorhythm.app.data.repository.BackupService
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.AppSettings
import com.duorhythm.app.data.local.pref.ThemeManager
import com.duorhythm.app.databinding.ActivitySettingsBinding
import com.duorhythm.app.ui.wallet.CategoryManagementDialog
import com.duorhythm.app.security.LifeSyncSecurityManager
import com.duorhythm.app.worker.LocalBackupWorker
import com.duorhythm.app.service.ReminderManager
import com.duorhythm.app.ui.common.PermissionRationaleDialogs
import com.duorhythm.app.ui.tools.SystemInfoBottomSheetDialog
import com.duorhythm.app.util.AnimationUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var dbHelper: DatabaseHelper
    private var isHydratingUI = false
    private var devClickCount = 0
    private var devFirstClickTime = 0L

    private val exportBackupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
            uri?.let {
                val success = BackupService.exportDatabaseToUri(this, it)
                if (success) {
                    updateBackupUI()
                    Toast.makeText(this, "Backup successfully saved to storage / cloud folder!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Failed to write backup file.", Toast.LENGTH_SHORT).show()
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
            setupThemeSelector()
            setupAutoBackup()
            setupListeners()
            syncSwitchStates()
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
        refreshCategoriesCountUI()
        refreshAutoTrackAttendanceUI()
        refreshShowRunningBalanceUI()
        updateThemeSelectorUI(ThemeManager.getSavedThemeMode(this))
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

    private fun setupAccordions() {
        binding.headerAccordionAppearance.setOnClickListener {
            toggleAccordion(binding.bodyAccordionAppearance, binding.dividerAccordionAppearance, binding.ivChevronAppearance)
        }
        binding.headerAccordionPreferences.setOnClickListener {
            toggleAccordion(binding.bodyAccordionPreferences, binding.dividerAccordionPreferences, binding.ivChevronPreferences)
        }
        binding.headerAccordionSecurity.setOnClickListener {
            toggleAccordion(binding.bodyAccordionSecurity, binding.dividerAccordionSecurity, binding.ivChevronSecurity)
        }
        binding.headerAccordionBackup.setOnClickListener {
            toggleAccordion(binding.bodyAccordionBackup, binding.dividerAccordionBackup, binding.ivChevronBackup)
        }
        binding.headerAccordionDataSlate.setOnClickListener {
            toggleAccordion(binding.bodyAccordionDataSlate, binding.dividerAccordionDataSlate, binding.ivChevronDataSlate)
        }
        binding.headerAccordionAbout.setOnClickListener {
            toggleAccordion(binding.bodyAccordionAbout, binding.dividerAccordionAbout, binding.ivChevronAbout)
        }
    }

    private fun setupPreferencesSection() {
        refreshStartOfWeekUI()
        refreshCategoriesCountUI()
        refreshAutoTrackAttendanceUI()
        refreshShowRunningBalanceUI()

        binding.itemStartOfWeek.setOnClickListener {
            showStartOfWeekDialog()
        }
        binding.rowAutoTrackAttendance.setOnClickListener {
            binding.switchAutoTrackAttendance.toggle()
        }
        binding.switchAutoTrackAttendance.setOnCheckedChangeListener { _, isChecked ->
            if (!isHydratingUI) {
                AppSettings.setAutoTrackAttendanceEnabled(this, isChecked)
            }
        }
        binding.rowShowRunningBalance.setOnClickListener {
            binding.switchShowRunningBalance.toggle()
        }
        binding.switchShowRunningBalance.setOnCheckedChangeListener { _, isChecked ->
            if (!isHydratingUI) {
                AppSettings.setShowRunningBalanceEnabled(this, isChecked)
            }
        }
        binding.rowManageTransactionCategories.setOnClickListener {
            CategoryManagementDialog.show(this, dbHelper) {
                refreshCategoriesCountUI()
            }
        }
    }

    private fun refreshShowRunningBalanceUI() {
        binding.switchShowRunningBalance.setOnCheckedChangeListener(null)
        binding.switchShowRunningBalance.isChecked = AppSettings.isShowRunningBalanceEnabled(this)
        binding.switchShowRunningBalance.setOnCheckedChangeListener { _, isChecked ->
            if (!isHydratingUI) {
                AppSettings.setShowRunningBalanceEnabled(this, isChecked)
            }
        }
    }

    private fun refreshAutoTrackAttendanceUI() {
        binding.switchAutoTrackAttendance.setOnCheckedChangeListener(null)
        binding.switchAutoTrackAttendance.isChecked = AppSettings.isAutoTrackAttendanceEnabled(this)
        binding.switchAutoTrackAttendance.setOnCheckedChangeListener { _, isChecked ->
            if (!isHydratingUI) {
                AppSettings.setAutoTrackAttendanceEnabled(this, isChecked)
            }
        }
    }

    private fun refreshCategoriesCountUI() {
        val count = dbHelper.getAllTransactionCategories().size
        binding.tvManageCategoriesSubtext.text = if (count == 0) {
            "0 categories • Tap to create custom categories"
        } else {
            "$count custom categories • Tap to edit or add"
        }
    }

    private fun refreshStartOfWeekUI() {
        val currentStart = AppSettings.getStartOfWeek(this)
        binding.tvStartOfWeekValue.text = AppSettings.getStartOfWeekDisplayLabel(currentStart)
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

    private fun toggleAccordion(bodyView: View, dividerView: View, chevronView: ImageView) {
        val isExpanding = bodyView.visibility != View.VISIBLE
        AnimationUtils.beginAccordionTransition(binding.layoutSettingsRoot)
        bodyView.visibility = if (isExpanding) View.VISIBLE else View.GONE
        dividerView.visibility = if (isExpanding) View.VISIBLE else View.GONE
        AnimationUtils.rotateChevron(chevronView, isExpanding)
    }

    private fun setupAutoBackup() {
        binding.switchAutoBackup.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isHydratingUI || !buttonView.isPressed) return@setOnCheckedChangeListener
            val prefs = getSharedPreferences(LocalBackupWorker.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(LocalBackupWorker.KEY_AUTO_BACKUP_ENABLED, isChecked).apply()

            TransitionManager.beginDelayedTransition(binding.layoutSettingsRoot)
            binding.layoutAutoBackupInterval.visibility = if (isChecked) View.VISIBLE else View.GONE

            val selectedInterval = when (binding.toggleAutoBackupInterval.checkedButtonId) {
                R.id.btnBackupWeekly -> "WEEKLY"
                R.id.btnBackupMonthly -> "MONTHLY"
                else -> "DAILY"
            }

            if (isChecked) {
                LocalBackupWorker.schedule(this, selectedInterval)
                Toast.makeText(this, "Automatic local backup scheduled ($selectedInterval)", Toast.LENGTH_SHORT).show()
            } else {
                LocalBackupWorker.cancel(this)
                Toast.makeText(this, "Automatic local backup disabled", Toast.LENGTH_SHORT).show()
            }
        }

        binding.toggleAutoBackupInterval.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isHydratingUI || !isChecked) return@addOnButtonCheckedListener
            val newInterval = when (checkedId) {
                R.id.btnBackupWeekly -> "WEEKLY"
                R.id.btnBackupMonthly -> "MONTHLY"
                else -> "DAILY"
            }
            val prefs = getSharedPreferences(LocalBackupWorker.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(LocalBackupWorker.KEY_AUTO_BACKUP_INTERVAL, newInterval).apply()
            if (binding.switchAutoBackup.isChecked) {
                LocalBackupWorker.schedule(this, newInterval)
                Toast.makeText(this, "Auto-backup interval set to $newInterval", Toast.LENGTH_SHORT).show()
            }
        }

        syncAutoBackupState()
    }

    private fun syncAutoBackupState() {
        val prefs = getSharedPreferences(LocalBackupWorker.PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(LocalBackupWorker.KEY_AUTO_BACKUP_ENABLED, false)
        val interval = prefs.getString(LocalBackupWorker.KEY_AUTO_BACKUP_INTERVAL, "DAILY") ?: "DAILY"

        binding.switchAutoBackup.isChecked = isEnabled
        binding.layoutAutoBackupInterval.visibility = if (isEnabled) View.VISIBLE else View.GONE

        val targetBtnId = when (interval.uppercase(Locale.US)) {
            "WEEKLY" -> R.id.btnBackupWeekly
            "MONTHLY" -> R.id.btnBackupMonthly
            else -> R.id.btnBackupDaily
        }
        if (binding.toggleAutoBackupInterval.checkedButtonId != targetBtnId) {
            binding.toggleAutoBackupInterval.check(targetBtnId)
        }
    }

    private fun setupListeners() {
        // --- 1. Streamlined Data Backup & Restore Hub ---
        binding.btnExportBackup.setOnClickListener {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val defaultFileName = "DuoRhythm_Backup_$timeStamp.json"
            exportBackupLauncher.launch(defaultFileName)
        }

        binding.btnImportBackup.setOnClickListener {
            importBackupLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
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
            openExternalUrl("https://github.com/LittleEagle2007")
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
            binding.cardAboutBlog
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
        // Biometric Lock
        binding.switchBiometricLock.setOnCheckedChangeListener(null)
        binding.switchBiometricLock.isChecked = LifeSyncSecurityManager.isLockEnabled(this)
        binding.switchBiometricLock.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isHydratingUI || !buttonView.isPressed) return@setOnCheckedChangeListener
            if (isChecked) {
                if (!LifeSyncSecurityManager.isLockEnabled(this)) {
                    LifeSyncSecurityManager.authenticate(
                        this,
                        title = "Verify Identity",
                        subtitle = "Confirm credentials to activate lock",
                        onSuccess = {
                            LifeSyncSecurityManager.setLockEnabled(this, true)
                            Toast.makeText(this, "Biometric Lock Activated", Toast.LENGTH_SHORT).show()
                        },
                        onError = { err ->
                            binding.switchBiometricLock.isChecked = false
                            Toast.makeText(this, "Lock not enabled: $err", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            } else {
                if (LifeSyncSecurityManager.isLockEnabled(this)) {
                    LifeSyncSecurityManager.setLockEnabled(this, false)
                    Toast.makeText(this, "Biometric Lock Disabled", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Class Alarms
        binding.switchClassReminders.setOnCheckedChangeListener(null)
        binding.switchClassReminders.isChecked = ReminderManager.isRemindersEnabled(this)
        binding.switchClassReminders.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isHydratingUI || !buttonView.isPressed) return@setOnCheckedChangeListener
            ReminderManager.setRemindersEnabled(this, isChecked)
            val msg = if (isChecked) "15-minute class alarms enabled" else "Class alarms disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Auto-Silent During Classes
        binding.switchAutoSilentClasses.setOnCheckedChangeListener(null)
        binding.switchAutoSilentClasses.isChecked = SmartAutomationManager.isAutoSilentClassesEnabled(this)
        binding.switchAutoSilentClasses.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isHydratingUI || !buttonView.isPressed) return@setOnCheckedChangeListener
            if (isChecked && !SmartAutomationManager.isNotificationPolicyAccessGranted(this)) {
                PermissionRationaleDialogs.showDndRationale(this)
            }
            SmartAutomationManager.setAutoSilentClassesEnabled(this, isChecked)
            val msg = if (isChecked) "Auto-silent during classes activated" else "Auto-silent disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Smart Expense Detection
        binding.switchSmartExpenseDetection.setOnCheckedChangeListener(null)
        binding.switchSmartExpenseDetection.isChecked = SmartAutomationManager.isExpenseDetectionEnabled(this)
        binding.switchSmartExpenseDetection.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isHydratingUI || !buttonView.isPressed) return@setOnCheckedChangeListener
            if (isChecked && !SmartAutomationManager.isNotificationListenerEnabled(this)) {
                PermissionRationaleDialogs.showNotificationListenerRationale(this)
            }
            SmartAutomationManager.setExpenseDetectionEnabled(this, isChecked)
            val msg = if (isChecked) "Smart expense detection activated" else "Smart expense detection disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Morning & Evening Briefings
        binding.switchSmartBriefings.setOnCheckedChangeListener(null)
        binding.switchSmartBriefings.isChecked = SmartAutomationManager.isSmartBriefingsEnabled(this)
        binding.switchSmartBriefings.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isHydratingUI || !buttonView.isPressed) return@setOnCheckedChangeListener
            SmartAutomationManager.setSmartBriefingsEnabled(this, isChecked)
            val msg = if (isChecked) "Daily 8 AM & 10 PM briefings enabled" else "Daily briefings disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Subscription Alerts
        binding.switchSubscriptionAlerts.setOnCheckedChangeListener(null)
        binding.switchSubscriptionAlerts.isChecked = SmartAutomationManager.isSubscriptionAlertsEnabled(this)
        binding.switchSubscriptionAlerts.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isHydratingUI || !buttonView.isPressed) return@setOnCheckedChangeListener
            SmartAutomationManager.setSubscriptionAlertsEnabled(this, isChecked)
            val msg = if (isChecked) "Subscription alerts & auto-log enabled" else "Subscription alerts disabled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Work Portal Module
        binding.switchWorkPortal.setOnCheckedChangeListener(null)
        binding.switchWorkPortal.isChecked = dbHelper.isWorkPortalEnabled()
        binding.switchWorkPortal.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isHydratingUI || !buttonView.isPressed) return@setOnCheckedChangeListener
            dbHelper.setWorkPortalEnabled(isChecked)
            val msg = if (isChecked) "Work Portal module enabled" else "Work Portal disabled (Student Mode only)"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupThemeSelector() {
        val currentMode = ThemeManager.getSavedThemeMode(this)
        updateThemeSelectorUI(currentMode)

        binding.toggleThemeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isHydratingUI || !isChecked) return@addOnButtonCheckedListener

            val selectedMode = when (checkedId) {
                R.id.btnThemeLight -> ThemeManager.THEME_LIGHT
                R.id.btnThemeDark -> ThemeManager.THEME_DARK
                R.id.btnThemeSystem -> ThemeManager.THEME_SYSTEM
                else -> return@addOnButtonCheckedListener
            }

            if (selectedMode != ThemeManager.getSavedThemeMode(this)) {
                ThemeManager.setThemeMode(this, selectedMode)
            }
        }

        binding.btnThemeDialog.setOnClickListener {
            showThemeSelectionDialog()
        }

        binding.cardThemeSettings.setOnClickListener {
            showThemeSelectionDialog()
        }
    }

    private fun updateThemeSelectorUI(themeMode: String) {
        val checkedButtonId = when (themeMode) {
            ThemeManager.THEME_LIGHT -> R.id.btnThemeLight
            ThemeManager.THEME_DARK -> R.id.btnThemeDark
            else -> R.id.btnThemeSystem
        }

        if (binding.toggleThemeGroup.checkedButtonId != checkedButtonId) {
            binding.toggleThemeGroup.check(checkedButtonId)
        }

        when (themeMode) {
            ThemeManager.THEME_LIGHT -> {
                binding.tvCurrentThemeDetails.text =
                    "Light Mode: Clean Slate White (#F8FAFC) & Surface (#FFFFFF) with high-contrast text (#0F172A)"
            }
            ThemeManager.THEME_DARK -> {
                binding.tvCurrentThemeDetails.text =
                    "Dark Mode: Obsidian Navy (#0A0E17 / #131B2E) with deep ambient glass"
            }
            else -> {
                binding.tvCurrentThemeDetails.text =
                    "System Default: Automatically matches OS Battery Saver and Day/Night settings"
            }
        }
    }

    private fun showThemeSelectionDialog() {
        val options = arrayOf(
            "System Default (Follow OS Battery/Day-Night setting)",
            "Light Mode (Clean Slate White #F8FAFC / Surface #FFFFFF)",
            "Dark Mode (Obsidian Navy #0A0E17 / #131B2E)"
        )
        val currentMode = ThemeManager.getSavedThemeMode(this)
        val checkedIndex = when (currentMode) {
            ThemeManager.THEME_LIGHT -> 1
            ThemeManager.THEME_DARK -> 2
            else -> 0
        }

        AlertDialog.Builder(this)
            .setTitle("Appearance & Theme")
            .setSingleChoiceItems(options, checkedIndex) { dialog, which ->
                val selectedMode = when (which) {
                    1 -> ThemeManager.THEME_LIGHT
                    2 -> ThemeManager.THEME_DARK
                    else -> ThemeManager.THEME_SYSTEM
                }
                dialog.dismiss()
                if (selectedMode != ThemeManager.getSavedThemeMode(this)) {
                    ThemeManager.setThemeMode(this, selectedMode)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
