package com.duorhythm.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.View
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.viewpager2.widget.ViewPager2
import com.duorhythm.app.automation.SmartAutomationManager
import com.duorhythm.app.databinding.ActivityMainBinding
import com.duorhythm.app.service.ReminderManager
import com.duorhythm.app.ui.common.MainPagerAdapter
import com.duorhythm.app.ui.common.PermissionRationaleDialogs
import com.duorhythm.app.ui.home.HomeFragment
import com.duorhythm.app.ui.portal.PortalFragment
import com.duorhythm.app.ui.settings.SettingsActivity
import com.duorhythm.app.ui.tools.PercentageCalcDialog
import com.duorhythm.app.ui.tools.PomodoroDialog
import com.duorhythm.app.ui.tools.ToolsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var pagerAdapter: MainPagerAdapter
    private var isStudent = true
    private var isToolsActive = false

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                SmartAutomationManager.init(this)
            }
        }

    private val toolsFragment by lazy { ToolsFragment() }

    private var selectedTabId = R.id.nav_home
    private var selectedPosition = MainPagerAdapter.INDEX_HOME
    private var hasInitializedTabs = false

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            )
        } catch (t: Throwable) {
            android.util.Log.e("DUO_MAIN", "Error setting night mode", t)
        }
        super.onCreate(savedInstanceState)

        try {
            if (!com.duorhythm.app.data.local.pref.ProfileManager.isOnboardingCompleted(this)) {
                val intent = Intent(this, com.duorhythm.app.ui.onboarding.OnboardingActivity::class.java)
                startActivity(intent)
                finish()
                return
            }
            com.duorhythm.app.data.local.db.DatabaseHelper.getInstance(this).ensureDefaultProfileIfEmpty()
        } catch (t: Throwable) {
            android.util.Log.e("DUO_MAIN", "Error checking onboarding or default profile", t)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.statusBarColor = Color.parseColor("#0A1322")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Keep status bar icons light (white) on dark navy/obsidian
                    window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
            }
            window.setBackgroundDrawableResource(R.drawable.bg_ambient_canvas)
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
        } catch (t: Throwable) {
            t.printStackTrace()
            return
        }

        try {
            setupWindowInsets()
            setupBottomNav()
            setupViewPager()
            setupTopQuickActions()
            setupBackNavigation()
            ensureOfficialAppLogo()
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        // Initialize background managers and alarms safely without blocking activity launch
        try {
            purgeAllLegacyAlarms()
            ReminderManager.createNotificationChannel(this)
            ReminderManager.rescheduleAll(this)
            com.duorhythm.app.scheduler.ClassAlarmScheduler.rescheduleAll(this)
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        try {
            SmartAutomationManager.init(this)
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        try {
            val backupPrefs = getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
            if (backupPrefs.getBoolean("pref_auto_backup_enabled", backupPrefs.getBoolean(com.duorhythm.app.worker.LocalBackupWorker.KEY_AUTO_BACKUP_ENABLED, false))) {
                val intervalHours = backupPrefs.getInt("pref_auto_backup_interval_hours", 24)
                com.duorhythm.app.worker.AutoBackupWorker.schedulePeriodicBackup(this, intervalHours)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        try {
            checkNotificationPermission()
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        try {
            // 1. Instantly delete any leftover APK installers from previous updates
            com.duorhythm.app.util.UpdateManager.cleanupDownloadedApks(this)

            // 2. Show What's New if newly updated (and not suppressed by user)
            com.duorhythm.app.util.UpdateManager.showWhatsNewDialog(this, forceManual = false)

            // 3. Silent check for new Stable releases (Beta will remain silent)
            com.duorhythm.app.util.UpdateManager.checkForUpdates(this, isManualCheck = false)
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        try {
            binding.root.post {
                handleShortcutIntent(intent)
                handleIncomingIntent(intent)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
        binding.root.post {
            handleShortcutIntent(intent)
            handleDeepLinkIntent(intent)
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val destination = intent?.getStringExtra("EXTRA_DEEP_LINK_DESTINATION") ?: return
        when (destination) {
            "DESTINATION_SCHEDULE" -> {
                binding.mainViewPager.setCurrentItem(3, false)
            }
            "DESTINATION_PORTAL_EXAMS" -> {
                binding.mainViewPager.setCurrentItem(4, false)
                supportFragmentManager.setFragmentResult("REQUEST_PORTAL_TAB", androidx.core.os.bundleOf("TARGET_TAB" to 1))
            }
        }
    }

    private fun handleShortcutIntent(intent: Intent?) {
        when (intent?.getStringExtra("EXTRA_ACTION_SHORTCUT")) {
            "RECORD_EXPENSE" -> {
                binding.mainViewPager.setCurrentItem(0, false) // Navigate to Wallet
                openAddTransactionDialog()
            }
            "NEW_TASK" -> {
                binding.mainViewPager.setCurrentItem(1, false) // Navigate to Tasks
                openAddTaskDialog()
            }
            "SHOW_SCHEDULE" -> {
                binding.mainViewPager.setCurrentItem(3, false) // Navigate to Schedule
            }
        }
    }

    private fun openAddTransactionDialog() {
        binding.root.post {
            try {
                val walletFrag = (pagerAdapter.getFragment(MainPagerAdapter.INDEX_WALLET) as? com.duorhythm.app.ui.wallet.WalletFragment)
                    ?: (supportFragmentManager.findFragmentByTag("f0") as? com.duorhythm.app.ui.wallet.WalletFragment)
                walletFrag?.view?.findViewById<View>(R.id.btnAddTransaction)?.performClick()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun openAddTaskDialog() {
        binding.root.post {
            try {
                val tasksFrag = (pagerAdapter.getFragment(MainPagerAdapter.INDEX_TASKS) as? com.duorhythm.app.ui.tasks.TasksFragment)
                    ?: (supportFragmentManager.findFragmentByTag("f1") as? com.duorhythm.app.ui.tasks.TasksFragment)
                tasksFrag?.view?.findViewById<View>(R.id.fabAddTask)?.performClick()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) {
            ensureOfficialAppLogo()
        }
    }

    private fun ensureOfficialAppLogo() {
        try {
            val density = resources.displayMetrics.density
            val pad2 = (2 * density).toInt()
            binding.ivAppLogo.setPadding(pad2, pad2, pad2, pad2)
            binding.ivAppLogo.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            binding.ivAppLogo.setImageResource(R.drawable.ic_app_logo)
            binding.ivAppLogo.imageTintList = null
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun purgeAllLegacyAlarms() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val legacyCodes = listOf(1002, 1003, 2001, 2002, 3001, 3002, 8000, 9000, 9001)
        for (code in legacyCodes) {
            val intent = Intent().setPackage(packageName)
            val pi = PendingIntent.getBroadcast(
                this,
                code,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                alarmManager.cancel(pi)
                pi.cancel()
            }
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(1002)
        notificationManager.cancel(2001)
        notificationManager.cancel(2002)
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                val prefs = getSharedPreferences(SmartAutomationManager.PREFS_NAME, Context.MODE_PRIVATE)
                val prompted = prefs.getBoolean("has_prompted_post_notifications", false)
                if (!prompted) {
                    prefs.edit().putBoolean("has_prompted_post_notifications", true).apply()
                    window.decorView.post {
                        if (!isFinishing && !isDestroyed) {
                            PermissionRationaleDialogs.showNotificationRationale(this) {
                                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    }
                }
            }
        }
    }

    fun isStudentProfile(): Boolean = isStudent

    fun setAppWorkspaceMode(isWorkMode: Boolean) {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_work_mode", isWorkMode).apply()
        isStudent = !isWorkMode

        val currentPos = binding.mainViewPager.currentItem
        if (currentPos == MainPagerAdapter.INDEX_HOME) {
            binding.tvAppSubtitle.text = if (!isWorkMode) "Personal Dashboard" else "Client & Project Hub"
            (pagerAdapter.getFragment(MainPagerAdapter.INDEX_HOME) as? HomeFragment)?.loadDashboardData()
        } else if (currentPos == MainPagerAdapter.INDEX_PORTAL) {
            binding.tvAppSubtitle.text = if (!isWorkMode) "Academic Overview" else "Client & Project Hub"
            (pagerAdapter.getFragment(MainPagerAdapter.INDEX_PORTAL) as? PortalFragment)?.setProfile(!isWorkMode)
        }

        val targetTab = when (binding.mainViewPager.currentItem) {
            0 -> R.id.nav_wallet
            1 -> R.id.nav_tasks
            2 -> R.id.nav_home
            3 -> R.id.nav_schedule
            4 -> R.id.nav_portal
            else -> R.id.nav_home
        }
        updateTabVisuals(selectedTabId, targetTab, animated = false)
        selectedTabId = targetTab

        // Notify fragments to refresh accent palettes
        supportFragmentManager.fragments.forEach { fragment ->
            (fragment as? com.duorhythm.app.ui.home.HomeFragment)?.applyWorkspaceColorTheme(isWorkMode)
        }
    }

    fun applyWorkspaceColorTheme(isWorkMode: Boolean) {
        setAppWorkspaceMode(isWorkMode)
    }

    fun setStudentProfile(student: Boolean) {
        setAppWorkspaceMode(!student)
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            try {
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val density = resources.displayMetrics.density
                val baseNavHeight = (60 * density).toInt()
                val bottomMargin = (16 * density).toInt()
                val sideMargin = (20 * density).toInt()

                // Add status bar padding to top bar
                binding.appBarLayout.setPadding(0, systemBars.top, 0, 0)

                // Maintain floating island dock geometry: fixed height with bottom margin above gesture bar
                val navParams = binding.bottomNav.layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
                if (navParams != null) {
                    navParams.height = baseNavHeight
                    navParams.bottomMargin = bottomMargin + systemBars.bottom
                    navParams.leftMargin = sideMargin
                    navParams.rightMargin = sideMargin
                    binding.bottomNav.layoutParams = navParams
                }

                // Adjust tools container bottom margin to prevent overlap with floating dock
                val toolsParams = binding.toolsContainer.layoutParams as? androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
                if (toolsParams != null) {
                    toolsParams.bottomMargin = baseNavHeight + bottomMargin + systemBars.bottom + (8 * density).toInt()
                    binding.toolsContainer.layoutParams = toolsParams
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            insets
        }
    }

    private fun setupViewPager() {
        pagerAdapter = MainPagerAdapter(this)
        binding.mainViewPager.adapter = pagerAdapter
        binding.mainViewPager.offscreenPageLimit = 4
        binding.mainViewPager.isUserInputEnabled = false
        binding.mainViewPager.setPageTransformer(null)
        binding.mainViewPager.setCurrentItem(MainPagerAdapter.INDEX_HOME, false)

        val recyclerView = binding.mainViewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView
        recyclerView?.overScrollMode = View.OVER_SCROLL_NEVER

        binding.mainViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // If navigation enters a dedicated full-screen overlay or modal, hide bottom bar
                binding.bottomNav.visibility = View.VISIBLE
                onTabPageSelected(position)
            }
        })
    }

    private fun getTabIdForPosition(position: Int): Int {
        return when (position) {
            MainPagerAdapter.INDEX_WALLET -> R.id.nav_wallet
            MainPagerAdapter.INDEX_TASKS -> R.id.nav_tasks
            MainPagerAdapter.INDEX_HOME -> R.id.nav_home
            MainPagerAdapter.INDEX_SCHEDULE -> R.id.nav_schedule
            MainPagerAdapter.INDEX_PORTAL -> R.id.nav_portal
            else -> R.id.nav_home
        }
    }

    private fun getPositionForTabId(tabId: Int): Int? {
        return when (tabId) {
            R.id.menu_wallet, R.id.nav_wallet -> MainPagerAdapter.INDEX_WALLET
            R.id.menu_tasks, R.id.nav_tasks -> MainPagerAdapter.INDEX_TASKS
            R.id.menu_home, R.id.nav_home -> MainPagerAdapter.INDEX_HOME
            R.id.menu_schedule, R.id.nav_schedule -> MainPagerAdapter.INDEX_SCHEDULE
            R.id.menu_portal, R.id.nav_portal -> MainPagerAdapter.INDEX_PORTAL
            else -> null
        }
    }

    private fun onTabPageSelected(position: Int) {
        if (isToolsActive) {
            closeTools()
        }

        if (position == MainPagerAdapter.INDEX_HOME) {
            (pagerAdapter.getFragment(MainPagerAdapter.INDEX_HOME) as? HomeFragment)?.loadDashboardData()
        }

        val targetTabId = getTabIdForPosition(position)
        val prevTabId = selectedTabId

        if (selectedPosition == position && hasInitializedTabs) {
            return
        }

        val animated = hasInitializedTabs
        selectedPosition = position
        selectedTabId = targetTabId

        updateTabVisuals(prevTabId, targetTabId, animated = animated)
        hasInitializedTabs = true

        updateSubtitleForPosition(position)
    }

    private fun updateSubtitleForPosition(position: Int) {
        binding.tvAppSubtitle.text = when (position) {
            MainPagerAdapter.INDEX_WALLET -> "Wallet & Accounts"
            MainPagerAdapter.INDEX_TASKS -> "To-Do List & Goals"
            MainPagerAdapter.INDEX_HOME -> if (isStudent) "Personal Dashboard" else "Client & Project Hub"
            MainPagerAdapter.INDEX_SCHEDULE -> "Weekly Class Timetable"
            MainPagerAdapter.INDEX_PORTAL -> if (isStudent) "Academic Overview" else "Client & Project Hub"
            else -> if (isStudent) "Personal Dashboard" else "Client & Project Hub"
        }
    }

    private fun setupTopQuickActions() {
        binding.btnQuickTools.setOnClickListener {
            if (isToolsActive) {
                closeTools()
            } else {
                openTools()
            }
        }

        binding.btnQuickSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnQuickProfile.setOnClickListener {
            val homeFrag = pagerAdapter.getFragment(MainPagerAdapter.INDEX_HOME) as? HomeFragment
            if (homeFrag != null && homeFrag.isAdded) {
                homeFrag.openProfileSwitcher()
            } else {
                com.duorhythm.app.ui.profile.ProfileSwitcherDialog(this) {
                    val hf = pagerAdapter.getFragment(MainPagerAdapter.INDEX_HOME) as? HomeFragment
                    hf?.loadUserProfile()
                    hf?.loadDashboardData()
                }.show()
            }
        }

        binding.btnQuickPomodoro.setOnClickListener {
            PomodoroDialog(this).show()
        }

        binding.btnQuickCalc.setOnClickListener {
            PercentageCalcDialog(this).show()
        }

        binding.btnQuickBackup.setOnClickListener {
            openTools()
        }

        binding.btnQuickAbout.setOnClickListener {
            com.duorhythm.app.ui.tools.AboutBottomSheetDialog(this).show()
        }

        binding.ivAppLogo.setOnClickListener {
            com.duorhythm.app.ui.tools.AboutBottomSheetDialog(this).show()
        }

        com.duorhythm.app.util.AnimationUtils.attachTactileFeedback(
            binding.btnQuickTools,
            binding.btnQuickProfile,
            binding.btnQuickSettings,
            binding.btnQuickPomodoro,
            binding.btnQuickCalc,
            binding.btnQuickBackup,
            binding.btnQuickAbout,
            binding.ivAppLogo
        )
    }

    private data class NavTabConfig(
        val id: Int,
        val container: LinearLayout,
        val icon: android.widget.ImageView,
        val label: android.widget.TextView
    )

    private fun setupBottomNav() {
        binding.navWallet.setOnClickListener { selectNavTab(R.id.nav_wallet) }
        binding.navTasks.setOnClickListener { selectNavTab(R.id.nav_tasks) }
        binding.navHome.setOnClickListener { selectNavTab(R.id.nav_home) }
        binding.navSchedule.setOnClickListener { selectNavTab(R.id.nav_schedule) }
        binding.navPortal.setOnClickListener { selectNavTab(R.id.nav_portal) }

        com.duorhythm.app.util.AnimationUtils.attachTactileFeedback(
            binding.navWallet,
            binding.navTasks,
            binding.navHome,
            binding.navSchedule,
            binding.navPortal
        )

        updateTabVisuals(selectedTabId, R.id.nav_home, animated = false)
        selectedTabId = R.id.nav_home
        selectedPosition = MainPagerAdapter.INDEX_HOME
        hasInitializedTabs = true
        updateSubtitleForPosition(MainPagerAdapter.INDEX_HOME)
    }

    fun selectNavTab(tabOrMenuItemId: Int) {
        val targetPosition = getPositionForTabId(tabOrMenuItemId) ?: return

        if (isToolsActive) {
            closeTools()
        }

        if (selectedPosition == targetPosition && !isToolsActive) {
            return
        }

        if (binding.mainViewPager.currentItem != targetPosition) {
            binding.mainViewPager.setCurrentItem(targetPosition, false)
        } else if (selectedPosition != targetPosition) {
            onTabPageSelected(targetPosition)
        }
    }

    private fun updateTabVisuals(prevTabId: Int, newTabId: Int, animated: Boolean) {
        val density = resources.displayMetrics.density
        val pad16 = (16 * density).toInt()
        val pad10 = (10 * density).toInt()
        val size20 = (20 * density).toInt()
        val size24 = (24 * density).toInt()

        if (animated) {
            val transition = TransitionSet().apply {
                ordering = TransitionSet.ORDERING_TOGETHER
                addTransition(ChangeBounds().apply {
                    duration = 220L
                    interpolator = FastOutSlowInInterpolator()
                })
                addTransition(Fade(Fade.IN).apply {
                    duration = 180L
                    startDelay = 40L
                })
                addTransition(Fade(Fade.OUT).apply {
                    duration = 120L
                })
            }
            TransitionManager.beginDelayedTransition(binding.bottomNav, transition)
        }

        val tabs = listOf(
            NavTabConfig(R.id.nav_wallet, binding.navWallet, binding.ivNavWallet, binding.tvNavWallet),
            NavTabConfig(R.id.nav_tasks, binding.navTasks, binding.ivNavTasks, binding.tvNavTasks),
            NavTabConfig(R.id.nav_home, binding.navHome, binding.ivNavHome, binding.tvNavHome),
            NavTabConfig(R.id.nav_schedule, binding.navSchedule, binding.ivNavSchedule, binding.tvNavSchedule),
            NavTabConfig(R.id.nav_portal, binding.navPortal, binding.ivNavPortal, binding.tvNavPortal)
        )

        for (tab in tabs) {
            val isActive = tab.id == newTabId
            val lp = tab.container.layoutParams as LinearLayout.LayoutParams

            if (isActive) {
                lp.width = LinearLayout.LayoutParams.WRAP_CONTENT
                lp.weight = 0f
                tab.container.layoutParams = lp
                tab.container.setPadding(pad16, pad10, pad16, pad10)
                tab.container.setBackgroundResource(if (isStudent) R.drawable.bg_nav_active_pill else R.drawable.bg_nav_active_pill_work)

                val iconLp = tab.icon.layoutParams
                iconLp.width = size20
                iconLp.height = size20
                tab.icon.layoutParams = iconLp
                tab.icon.imageTintList = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))

                tab.label.visibility = View.VISIBLE
                tab.label.setTextColor(Color.parseColor("#FFFFFF"))
            } else {
                lp.width = 0
                lp.weight = 1f
                tab.container.layoutParams = lp
                tab.container.setPadding(0, pad10, 0, pad10)
                tab.container.setBackgroundResource(R.drawable.bg_nav_tab_ripple)

                val iconLp = tab.icon.layoutParams
                iconLp.width = size24
                iconLp.height = size24
                tab.icon.layoutParams = iconLp
                tab.icon.imageTintList = ColorStateList.valueOf(Color.parseColor("#64748B"))

                tab.label.visibility = View.GONE
            }
        }
    }

    fun openTools() {
        isToolsActive = true
        binding.bottomNav.visibility = View.GONE
        binding.toolsContainer.visibility = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.toolsContainer, toolsFragment, "tools")
            .commit()
        binding.tvAppSubtitle.text = "Focus & Productivity Suite"
        updateHeaderSuiteVisuals(true)
    }

    fun openProductivitySuite() {
        startActivity(Intent(this, com.duorhythm.app.ui.focus.FocusSuiteActivity::class.java))
    }

    fun closeTools() {
        if (!isToolsActive) return
        isToolsActive = false
        binding.bottomNav.visibility = View.VISIBLE
        supportFragmentManager.findFragmentByTag("tools")?.let {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .remove(it)
                .commit()
        }
        binding.toolsContainer.visibility = View.GONE
        updateSubtitleForPosition(binding.mainViewPager.currentItem)
        updateHeaderSuiteVisuals(false)
    }

    private fun updateHeaderSuiteVisuals(isToolsActive: Boolean) {
        try {
            binding.btnQuickTools.setBackgroundResource(R.drawable.bg_header_action_button)
            binding.btnQuickTools.imageTintList = ColorStateList.valueOf(Color.parseColor("#00E5FF"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isToolsActive) {
                    closeTools()
                } else if (binding.mainViewPager.currentItem != MainPagerAdapter.INDEX_HOME) {
                    selectNavTab(R.id.nav_home)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
}
