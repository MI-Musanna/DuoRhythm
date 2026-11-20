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
import com.duorhythm.app.widget.ScheduleWidgetProvider

class MainActivity : AppCompatActivity() {

    companion object {
        private var instanceRef: java.lang.ref.WeakReference<MainActivity>? = null

        fun notifyBadgeDataChanged(tabIndex: Int = -1) {
            instanceRef?.get()?.notifyBadgeDataChanged(tabIndex)
        }
    }

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
    private val clearedTabBadgesThisSession = mutableSetOf<Int>()
    private var lastBadgeDayStamp = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
            )
        } catch (t: Throwable) {
            android.util.Log.e("DUO_MAIN", "Error setting night mode", t)
        }
        super.onCreate(savedInstanceState)
        instanceRef = java.lang.ref.WeakReference(this)
        com.duorhythm.app.util.ViewAnimationExtensions.installGlobalMotionEngine(application)
        com.duorhythm.app.util.ViewAnimationExtensions.applySpringRecursively(window.decorView)
        com.duorhythm.app.scheduler.BriefingAlarmScheduler.scheduleDailyBriefings(this)

        // Cancel all spam workers firing independent notifications at 8:00 AM, 9:00 AM
        try {
            val wm = androidx.work.WorkManager.getInstance(applicationContext)
            wm.cancelUniqueWork("SubscriptionRenewalWorker")
            wm.cancelUniqueWork("AutoRenewalWorker")
            wm.cancelUniqueWork("duorhythm_subscription_renewal")
            wm.cancelUniqueWork("ExamReminderWorker")
            wm.cancelUniqueWork("ClassReminderWorker")
            wm.cancelAllWorkByTag("subscription_renewal")
            wm.cancelAllWorkByTag("exam_reminder_tag")
        } catch (_: Exception) {}

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
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.statusBarColor = Color.TRANSPARENT
                window.navigationBarColor = Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Keep status bar icons light (white) on dark navy/obsidian
                    window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
                }
            }
            window.setBackgroundDrawableResource(R.drawable.bg_ambient_canvas)
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            com.duorhythm.app.util.ViewAnimationExtensions.applySpringToAllButtons(binding.root)
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
            val backupPrefs = getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE)
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

            purgeLegacyBackgroundWorkers()
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        try {
            ScheduleWidgetProvider.updateAllWidgets(this)
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

    private fun purgeLegacyBackgroundWorkers() {
        try {
            val workManager = androidx.work.WorkManager.getInstance(applicationContext)
            // Cancel all legacy unique worker tags causing ghost notifications
            workManager.cancelUniqueWork("ClassMorningWorker")
            workManager.cancelUniqueWork("DailyBriefingWorker")
            workManager.cancelUniqueWork("ClassReminderWorker")
            workManager.cancelUniqueWork("MorningAgendaWorker")
            workManager.cancelUniqueWork("SubscriptionRenewalWorker")
            workManager.cancelUniqueWork("AutoRenewalWorker")
            workManager.cancelUniqueWork("duorhythm_subscription_renewal")
            workManager.cancelUniqueWork("ExamReminderWorker")
            workManager.cancelAllWorkByTag("morning_classes_tag")
            workManager.cancelAllWorkByTag("subscription_renewal")
            workManager.cancelAllWorkByTag("exam_reminder_tag")
        } catch (_: Exception) {}

        try {
            val am = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (am != null) {
                val intent = Intent(this, com.duorhythm.app.receiver.DailyAutomationReceiver::class.java).apply {
                    action = com.duorhythm.app.receiver.DailyAutomationReceiver.ACTION_SUBSCRIPTION_CHECK
                }
                val pi = PendingIntent.getBroadcast(
                    this,
                    com.duorhythm.app.automation.SmartAutomationManager.REQUEST_CODE_SUBSCRIPTION,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                am.cancel(pi)
            }
        } catch (_: Exception) {}
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
        com.duorhythm.app.scheduler.BriefingAlarmScheduler.scheduleDailyBriefings(this)
        if (::binding.isInitialized) {
            ensureOfficialAppLogo()
        }
        try {
            ScheduleWidgetProvider.updateAllWidgets(this)
        } catch (t: Throwable) {
            t.printStackTrace()
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

    private fun syncAllSpeedDialFabs(isWorkMode: Boolean) {
        getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("is_work_mode", isWorkMode)
            .apply()
        fun walk(v: View?) {
            if (v is com.duorhythm.app.ui.views.SpeedDialFabView) {
                v.syncWorkspaceAccent(isWorkMode)
            } else if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
        }
        if (::binding.isInitialized) {
            walk(binding.root)
        }
    }

    fun setAppWorkspaceMode(isWorkMode: Boolean) {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_work_mode", isWorkMode).apply()
        getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("is_work_mode", isWorkMode).apply()
        isStudent = !isWorkMode
        syncAllSpeedDialFabs(isWorkMode)
        if (::binding.isInitialized) {
            com.duorhythm.app.util.DuoMotionHelper.triggerCircularReveal(binding.mainViewPager)
        }

        val currentPos = binding.mainViewPager.currentItem
        if (currentPos == MainPagerAdapter.INDEX_HOME) {
            (pagerAdapter.getFragment(MainPagerAdapter.INDEX_HOME) as? HomeFragment)?.loadDashboardData()
        } else if (currentPos == MainPagerAdapter.INDEX_PORTAL) {
            (pagerAdapter.getFragment(MainPagerAdapter.INDEX_PORTAL) as? PortalFragment)?.setProfile(!isWorkMode)
        }
        updateSubtitleForPosition(currentPos)

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
        syncAllSpeedDialFabs(isWorkMode)
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
        if (!::binding.isInitialized) return
        val (newTitle, newSub, accentHex) = when {
            isToolsActive -> Triple("Focus Suite", "Concentration & Study Tools", "#38BDF8")
            position == MainPagerAdapter.INDEX_WALLET -> Triple("Wallet & Accounts", "Net Worth & Cash Flow", "#34D399")
            position == MainPagerAdapter.INDEX_TASKS -> Triple("Tasks & Goals", "Daily Action & Milestones", "#38BDF8")
            position == MainPagerAdapter.INDEX_HOME -> Triple(
                "DuoRhythm",
                if (isStudent) "Personal Dashboard" else "Executive Work Hub",
                if (isStudent) "#38BDF8" else "#34D399"
            )
            position == MainPagerAdapter.INDEX_SCHEDULE -> Triple("Timetable", "Weekly Class Routine", "#38BDF8")
            position == MainPagerAdapter.INDEX_PORTAL -> Triple(
                if (isStudent) "Academic Portal" else "Work Portal",
                if (isStudent) "Courses, Exams & Directory" else "Client Orders & Deliverables",
                if (isStudent) "#38BDF8" else "#34D399"
            )
            else -> Triple("DuoRhythm", "Personal Dashboard", "#38BDF8")
        }

        animateHeaderIdentity(newTitle, newSub, accentHex)
        attachScrollReactiveHeaderToCurrentPage(position)
    }

    private fun animateHeaderIdentity(title: String, subtitle: String, accentHex: String) {
        if (!::binding.isInitialized) return
        val prevTitle = binding.tvAppTitle.text?.toString().orEmpty()
        val prevSub = binding.tvAppSubtitle.text?.toString().orEmpty()
        binding.tvAppSubtitle.setTextColor(Color.parseColor(accentHex))

        if (prevTitle == title && prevSub == subtitle) return

        val density = resources.displayMetrics.density
        val slideOffset = 6f * density

        binding.tvAppTitle.animate().cancel()
        binding.tvAppSubtitle.animate().cancel()

        binding.tvAppTitle.alpha = 0.25f
        binding.tvAppTitle.translationY = slideOffset
        binding.tvAppTitle.text = title
        binding.tvAppTitle.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(200L)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()

        binding.tvAppSubtitle.alpha = 0.25f
        binding.tvAppSubtitle.translationY = slideOffset * 0.7f
        binding.tvAppSubtitle.text = subtitle
        binding.tvAppSubtitle.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220L)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
    }

    fun updateHeaderScrollProgress(scrollY: Int) {
        if (!::binding.isInitialized) return
        val density = resources.displayMetrics.density
        val threshold = 36f * density
        val progress = (scrollY.coerceAtLeast(0).toFloat() / threshold).coerceIn(0f, 1f)

        binding.viewHeaderScrollFrost.alpha = progress
        val padTop = ((10f - (3f * progress)) * density).toInt()
        val padBottom = ((8f - (2f * progress)) * density).toInt()
        val padH = (16f * density).toInt()
        binding.headerInnerContainer.setPadding(padH, padTop, padH, padBottom)
    }

    private fun attachScrollReactiveHeaderToCurrentPage(position: Int) {
        if (!::binding.isInitialized) return
        // Reset header smoothly to merged state on tab switch
        updateHeaderScrollProgress(0)
        binding.root.post {
            try {
                val targetRoot: View? = if (isToolsActive) {
                    supportFragmentManager.findFragmentByTag("tools")?.view
                } else {
                    (pagerAdapter.getFragment(position)?.view)
                        ?: supportFragmentManager.findFragmentByTag("f$position")?.view
                }
                val scrollView = findFirstNestedScrollView(targetRoot)
                if (scrollView != null) {
                    updateHeaderScrollProgress(scrollView.scrollY)
                    scrollView.setOnScrollChangeListener(
                        androidx.core.widget.NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ ->
                            updateHeaderScrollProgress(scrollY)
                        }
                    )
                }
            } catch (_: Exception) {}
        }
    }

    private fun findFirstNestedScrollView(root: View?): androidx.core.widget.NestedScrollView? {
        if (root == null) return null
        if (root is androidx.core.widget.NestedScrollView) return root
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findFirstNestedScrollView(root.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun setupTopQuickActions() {
        try {
            com.duorhythm.app.util.ViewAnimationExtensions.addSpringPressEffect(binding.btnQuickTools)
            com.duorhythm.app.util.ViewAnimationExtensions.addSpringPressEffect(binding.btnQuickSettings)
            com.duorhythm.app.util.ViewAnimationExtensions.addSpringPressEffect(binding.btnQuickProfile)
            com.duorhythm.app.util.ViewAnimationExtensions.addSpringPressEffect(binding.btnQuickPomodoro)
        } catch (_: Exception) {}

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
            if (isToolsActive) {
                closeTools()
            } else if (binding.mainViewPager.currentItem != MainPagerAdapter.INDEX_HOME) {
                selectNavTab(R.id.nav_home)
            } else {
                com.duorhythm.app.ui.tools.AboutBottomSheetDialog(this).show()
            }
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
        updateSubtitleForPosition(binding.mainViewPager.currentItem)
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
            binding.btnQuickTools.imageTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
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


    /**
     * Rules 3, 4, 5 & 7: Decision-only badge engine.
     * - Tasks: Actionable due-today / overdue uncompleted tasks (Count Badge)
     * - Portal: Upcoming exams today/tomorrow (Student) or urgent orders (Work) (Count Badge)
     * - Wallet: Unpaid overdue / due-today Dues & Debts (Count Badge)
     * - Schedule: Live ongoing class session indicator (Status Dot only, no digits)
     * - Home: Never badged
     */
    fun refreshNavigationBadges() {
        if (!::binding.isInitialized) return
        try {
            val todayCal = java.util.Calendar.getInstance()
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val todayStr = sdf.format(todayCal.time)
            todayCal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            val tomorrowStr = sdf.format(todayCal.time)

            if (todayStr != lastBadgeDayStamp) {
                clearedTabBadgesThisSession.clear()
                lastBadgeDayStamp = todayStr
            }

            val currentTabPos = binding.mainViewPager.currentItem
            clearedTabBadgesThisSession.add(currentTabPos)

            val db = com.duorhythm.app.data.local.db.DatabaseHelper.getInstance(this)
            val profileId = com.duorhythm.app.data.local.pref.ProfileManager.getActiveProfileId(this)

            // 1. Tasks Badge (Actionable due today or overdue)
            if (clearedTabBadgesThisSession.contains(MainPagerAdapter.INDEX_TASKS)) {
                binding.badgeNavTasks.clear()
            } else {
                val urgentTasks = try {
                    db.getAllTasks("ALL").count { task ->
                        !task.isCompleted && !task.dueDate.isNullOrBlank() && task.dueDate!! <= todayStr
                    }
                } catch (_: Exception) { 0 }
                binding.badgeNavTasks.setCount(urgentTasks)
            }

            // 2. Portal Badge (Exams today/tomorrow in Student mode; active orders in Work mode)
            if (clearedTabBadgesThisSession.contains(MainPagerAdapter.INDEX_PORTAL)) {
                binding.badgeNavPortal.clear()
            } else {
                val urgentPortalCount = try {
                    if (isStudent) {
                        val seenExamKeys = mutableSetOf<String>()
                        var examCount = 0

                        val widgetExams = db.getUpcomingExamsForWidget(profileId).filter { ex ->
                            ex.dueDate == todayStr || ex.dueDate == tomorrowStr
                        }
                        for (wEx in widgetExams) {
                            val key = "${wEx.title.lowercase().trim()}_${wEx.dueDate}"
                            if (seenExamKeys.add(key)) examCount++
                        }

                        val workItemExams = db.getAllExams(profileId = profileId).filter { (item, _) ->
                            item.status != "DONE" && (
                                item.endDate == todayStr || item.endDate == tomorrowStr ||
                                item.startDate == todayStr || item.startDate == tomorrowStr
                            )
                        }
                        for ((item, _) in workItemExams) {
                            val d = item.endDate.ifBlank { item.startDate }
                            val key = "${item.title.lowercase().trim()}_$d"
                            if (seenExamKeys.add(key)) examCount++
                        }

                        examCount
                    } else {
                        db.getAllWorkOrders().count { order ->
                            !order.status.equals("COMPLETED", ignoreCase = true) &&
                                order.endDate.isNotBlank() && order.endDate <= tomorrowStr
                        }
                    }
                } catch (_: Exception) { 0 }
                binding.badgeNavPortal.setCount(urgentPortalCount)
            }

            // 3. Wallet Badge (Unpaid overdue or due-today Dues & Debts)
            if (clearedTabBadgesThisSession.contains(MainPagerAdapter.INDEX_WALLET)) {
                binding.badgeNavWallet.clear()
            } else {
                val urgentDues = try {
                    db.getAllDuesDebts().count { due ->
                        due.status.equals("UNPAID", ignoreCase = true) &&
                            due.dueDate.isNotBlank() && due.dueDate <= todayStr
                    }
                } catch (_: Exception) { 0 }
                binding.badgeNavWallet.setCount(urgentDues)
            }

            // 4. Schedule Status Dot (Passive live class indicator — never a count badge)
            if (clearedTabBadgesThisSession.contains(MainPagerAdapter.INDEX_SCHEDULE)) {
                binding.badgeNavSchedule.clear()
            } else {
                val isLiveClass = getSharedPreferences(
                    com.duorhythm.app.scheduler.ClassAlarmScheduler.PREFS_NAME,
                    Context.MODE_PRIVATE
                ).getBoolean(com.duorhythm.app.scheduler.ClassAlarmScheduler.KEY_IN_CLASS_DND, false)
                binding.badgeNavSchedule.setStatusDot(isLiveClass)
            }
        } catch (_: Exception) {}
    }

    fun notifyBadgeDataChanged(tabIndex: Int = -1) {
        if (tabIndex in 0..4) {
            clearedTabBadgesThisSession.remove(tabIndex)
        }
        runOnUiThread {
            refreshNavigationBadges()
        }
    }

    override fun onDestroy() {
        if (instanceRef?.get() === this) {
            instanceRef = null
        }
        super.onDestroy()
    }
}