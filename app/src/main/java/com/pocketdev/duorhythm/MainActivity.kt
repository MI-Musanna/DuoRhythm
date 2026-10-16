package com.pocketdev.duorhythm

import android.Manifest
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
import com.pocketdev.duorhythm.automation.SmartAutomationManager
import com.pocketdev.duorhythm.databinding.ActivityMainBinding
import com.pocketdev.duorhythm.service.ReminderManager
import com.pocketdev.duorhythm.ui.common.MainPagerAdapter
import com.pocketdev.duorhythm.ui.common.PermissionRationaleDialogs
import com.pocketdev.duorhythm.ui.home.HomeFragment
import com.pocketdev.duorhythm.ui.portal.PortalFragment
import com.pocketdev.duorhythm.ui.settings.SettingsActivity
import com.pocketdev.duorhythm.ui.tools.AmbientSoundDialog
import com.pocketdev.duorhythm.ui.tools.PercentageCalcDialog
import com.pocketdev.duorhythm.ui.tools.PomodoroDialog
import com.pocketdev.duorhythm.ui.tools.ToolsFragment

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
        super.onCreate(savedInstanceState)
        try {
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
            ReminderManager.createNotificationChannel(this)
            ReminderManager.rescheduleAll(this)
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        try {
            SmartAutomationManager.init(this)
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        try {
            val backupPrefs = getSharedPreferences(com.pocketdev.duorhythm.worker.LocalBackupWorker.PREFS_NAME, Context.MODE_PRIVATE)
            if (backupPrefs.getBoolean(com.pocketdev.duorhythm.worker.LocalBackupWorker.KEY_AUTO_BACKUP_ENABLED, false)) {
                val backupInterval = backupPrefs.getString(com.pocketdev.duorhythm.worker.LocalBackupWorker.KEY_AUTO_BACKUP_INTERVAL, "DAILY") ?: "DAILY"
                com.pocketdev.duorhythm.worker.LocalBackupWorker.schedule(this, backupInterval)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        try {
            checkNotificationPermission()
        } catch (t: Throwable) {
            t.printStackTrace()
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
            val pad7 = (7 * density).toInt()
            binding.ivAppLogo.setPadding(pad7, pad7, pad7, pad7)
            binding.ivAppLogo.setImageResource(R.drawable.ic_app_logo)
            binding.ivAppLogo.imageTintList = null
        } catch (e: Throwable) {
            e.printStackTrace()
        }
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

    fun setStudentProfile(student: Boolean) {
        isStudent = student
        val currentPos = binding.mainViewPager.currentItem
        if (currentPos == MainPagerAdapter.INDEX_HOME) {
            binding.tvAppSubtitle.text = if (student) "Personal Dashboard" else "Client & Project Hub"
            (pagerAdapter.getFragment(MainPagerAdapter.INDEX_HOME) as? HomeFragment)?.loadDashboardData()
        } else if (currentPos == MainPagerAdapter.INDEX_PORTAL) {
            binding.tvAppSubtitle.text = if (student) "Academic Overview" else "Client & Project Hub"
            (pagerAdapter.getFragment(MainPagerAdapter.INDEX_PORTAL) as? PortalFragment)?.setProfile(student)
        }
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
        binding.mainViewPager.offscreenPageLimit = 1
        binding.mainViewPager.setCurrentItem(MainPagerAdapter.INDEX_HOME, false)

        val recyclerView = binding.mainViewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView
        recyclerView?.overScrollMode = View.OVER_SCROLL_NEVER

        binding.mainViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
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

        binding.btnQuickPomodoro.setOnClickListener {
            PomodoroDialog(this).show()
        }

        binding.btnQuickMusic.setOnClickListener {
            AmbientSoundDialog(this).show()
        }

        binding.btnQuickCalc.setOnClickListener {
            PercentageCalcDialog(this).show()
        }

        binding.btnQuickBackup.setOnClickListener {
            openTools()
        }

        binding.btnQuickAbout.setOnClickListener {
            com.pocketdev.duorhythm.ui.tools.AboutBottomSheetDialog(this).show()
        }

        binding.ivAppLogo.setOnClickListener {
            com.pocketdev.duorhythm.ui.tools.AboutBottomSheetDialog(this).show()
        }

        com.pocketdev.duorhythm.util.AnimationUtils.attachTactileFeedback(
            binding.btnQuickTools,
            binding.btnQuickSettings,
            binding.btnQuickPomodoro,
            binding.btnQuickMusic,
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

        com.pocketdev.duorhythm.util.AnimationUtils.attachTactileFeedback(
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
            binding.mainViewPager.setCurrentItem(targetPosition, true)
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
                tab.container.setBackgroundResource(R.drawable.bg_nav_active_pill)

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
        binding.toolsContainer.visibility = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.toolsContainer, toolsFragment, "tools")
            .commit()
        binding.tvAppSubtitle.text = "Focus & Productivity Suite"
        updateHeaderSuiteVisuals(true)
    }

    fun closeTools() {
        if (!isToolsActive) return
        isToolsActive = false
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
            if (isToolsActive) {
                binding.btnQuickTools.imageTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
                binding.btnQuickTools.setBackgroundResource(R.drawable.bg_glass_squircle_action_active)
            } else {
                binding.btnQuickTools.imageTintList = ColorStateList.valueOf(Color.parseColor("#94A3B8"))
                binding.btnQuickTools.setBackgroundResource(R.drawable.bg_glass_squircle_action)
            }
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
