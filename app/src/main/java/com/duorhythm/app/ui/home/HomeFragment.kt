package com.duorhythm.app.ui.home

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import android.widget.LinearLayout
import com.duorhythm.app.MainActivity
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.data.model.ClassEntity
import com.duorhythm.app.data.model.ClientOrder
import com.duorhythm.app.data.model.ClassStatus
import com.duorhythm.app.data.model.CarouselClassItem
import com.duorhythm.app.databinding.FragmentHomeBinding
import android.app.AlertDialog
import android.widget.ArrayAdapter
import android.widget.Toast
import com.duorhythm.app.data.model.NoteItem
import com.duorhythm.app.databinding.DialogAddNoteBinding
import com.duorhythm.app.ui.common.ColorSliderView
import com.duorhythm.app.ui.tools.PercentageCalcDialog
import com.duorhythm.app.ui.tools.PomodoroDialog
import com.duorhythm.app.ui.focus.FocusSuiteActivity
import com.duorhythm.app.data.model.PersonaMode
import com.duorhythm.app.util.crossfade
import com.duorhythm.app.ui.wallet.WalletDataSyncManager
import com.duorhythm.app.util.BlurUtil
import com.duorhythm.app.util.AnimationUtils
import android.content.Context
import android.content.Intent
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.widget.ImageView
import com.duorhythm.app.data.local.pref.AppPreferences
import com.duorhythm.app.ui.profile.ProfileActivity
import com.duorhythm.app.ui.profile.ProfileSwitcherDialog
import com.google.android.material.tabs.TabLayoutMediator
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
    private lateinit var homeViewModel: HomeViewModel
    private var currentWorkOrderIndex = 0
    private var activeWorkOrders = listOf<com.duorhythm.app.data.model.WorkOrder>()
    private var tabLayoutMediator: TabLayoutMediator? = null

    private var isNotesCollapsed = false

    fun setSectionCollapsed(viewToHide: View, chevron: ImageView, isCollapsed: Boolean, animate: Boolean) {
        if (animate && _binding != null) {
            TransitionManager.beginDelayedTransition(binding.rootLayout, AutoTransition().apply { duration = 180 })
        }
        viewToHide.visibility = if (isCollapsed) View.GONE else View.VISIBLE
        chevron.animate().rotation(if (isCollapsed) -90f else 0f).setDuration(180).start()
    }

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
        com.duorhythm.app.util.ViewAnimationExtensions.applySpringToAllButtons(view)
        try {
            WalletDataSyncManager.register(walletSyncListener)
            dbHelper = DatabaseHelper.getInstance(requireContext())
            try {
                homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
            } catch (e: Throwable) {
                homeViewModel = HomeViewModel(requireActivity().application)
            }

            setupNextUpCarousel()
            loadNextUpSchedule()

            quickNotesAdapter = QuickNotesAdapter(
                emptyList(),
                onNoteClick = { showEditNoteDialog(it) },
                onNoteDelete = { confirmDeleteNote(it) }
            )
            binding.rvHomeQuickNotes.adapter = quickNotesAdapter
            setupBlurEffects()
            loadUserProfile()
            setupGreetingAndDate()

            var isScratchpadCollapsed = false
            binding.layoutScratchpadHeader.setOnClickListener {
                isScratchpadCollapsed = !isScratchpadCollapsed
                binding.layoutNotesCollapsibleBody.visibility = if (isScratchpadCollapsed) View.GONE else View.VISIBLE
                binding.ivNotesChevron.animate().rotation(if (isScratchpadCollapsed) 180f else 0f).setDuration(200).start()
            }

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
                (binding.cardHomeQuickNotes as? eightbitlab.com.blurview.BlurView)?.let { BlurUtil.setupBlur(it, rootView) }
                (binding.cardHomePortal as? eightbitlab.com.blurview.BlurView)?.let { BlurUtil.setupBlur(it, rootView) }
                (binding.cardHomePortalWork as? eightbitlab.com.blurview.BlurView)?.let { BlurUtil.setupBlur(it, rootView) }
                (binding.btnHomeOpenTools as? eightbitlab.com.blurview.BlurView)?.let { BlurUtil.setupBlur(it, rootView) }
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
            val savedName = profilePrefs.getString(com.duorhythm.app.data.local.pref.UserProfileManager.KEY_USER_NAME, null)?.trim()
                ?: profilePrefs.getString("user_display_name", null)?.trim()
            val profileName = if (!savedName.isNullOrEmpty()) savedName else "Scholar"
            binding.tvWelcomeUser.text = "Welcome back, $profileName"

            val activeId = ProfileManager.getActiveProfileId(context)
            val activeProfile = dbHelper.profileDao.getProfileById(activeId)

            binding.ivUserProfile.setImageDrawable(null)
            binding.ivUserProfile.setImageBitmap(null)

            val avatarUriString = activeProfile?.avatarUri
            if (!avatarUriString.isNullOrBlank()) {
                try {
                    binding.ivUserProfile.setPadding(0, 0, 0, 0)
                    binding.ivUserProfile.imageTintList = null
                    binding.ivUserProfile.setImageURI(android.net.Uri.parse(avatarUriString))
                } catch (e: Exception) {
                    val density = resources.displayMetrics.density
                    val pad8 = (8 * density).toInt()
                    binding.ivUserProfile.setPadding(pad8, pad8, pad8, pad8)
                    binding.ivUserProfile.setImageResource(R.drawable.ic_profile_placeholder)
                    binding.ivUserProfile.imageTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
                }
            } else {
                val density = resources.displayMetrics.density
                val pad8 = (8 * density).toInt()
                binding.ivUserProfile.setPadding(pad8, pad8, pad8, pad8)
                binding.ivUserProfile.setImageResource(R.drawable.ic_profile_placeholder)
                binding.ivUserProfile.imageTintList = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
            }

            val openProfile = View.OnClickListener {
                val intent = Intent(requireContext(), ProfileActivity::class.java)
                startActivity(intent)
            }
            binding.ivUserProfile.setOnClickListener(openProfile)
            binding.tvWelcomeUser.setOnClickListener(openProfile)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupGreetingAndDate() {
        try {
            val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.US)
            binding.tvCurrentDateHeader.text = dateFormat.format(Date())

            val workPortalEnabled = dbHelper.isWorkPortalEnabled()
            val mainActivity = activity as? MainActivity
            val isStudent = !workPortalEnabled || (mainActivity?.isStudentProfile() ?: true)

            if (!workPortalEnabled) {
                binding.toggleStudentWork.visibility = View.GONE
            } else {
                binding.toggleStudentWork.visibility = View.VISIBLE
            }

            updateToggleStyle(isStudent)
            updateWorkspaceThemeAndFeed(!isStudent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateToggleStyle(isStudent: Boolean) {
        try {
            android.transition.TransitionManager.beginDelayedTransition(
                binding.rootLayout,
                android.transition.AutoTransition().apply { duration = 180 }
            )
            binding.layoutNextUpSection.crossfade(isStudent)
            binding.layoutWorkMilestonesContainer.crossfade(!isStudent)
            binding.layoutStudentOverview.crossfade(isStudent)
            binding.layoutWorkOverview.crossfade(!isStudent)
            binding.cardStudyPortal.crossfade(isStudent)
            binding.cardWorkPortal.crossfade(!isStudent)

            if (isStudent) {
                binding.btnStudentMode.setBackgroundResource(R.drawable.bg_pill_blue)
                binding.btnStudentMode.setTextColor(Color.WHITE)
                binding.btnWorkMode.background = null
                binding.btnWorkMode.setTextColor(Color.parseColor("#64748B"))

                // Sync Portal Shortcut Card to Student Mode
                binding.badgeHomePortal.setBackgroundResource(R.drawable.bg_squircle_portal_student)
                binding.ivHomePortalIcon.setColorFilter(Color.parseColor("#38BDF8"))
                binding.btnHomePortalAction.setBackgroundResource(R.drawable.bg_portal_pill_student)
                binding.btnHomePortalAction.setTextColor(Color.parseColor("#38BDF8"))
            } else {
                binding.btnStudentMode.background = null
                binding.btnStudentMode.setTextColor(Color.parseColor("#64748B"))
                binding.btnWorkMode.setBackgroundResource(R.drawable.bg_pill_work_active)
                binding.btnWorkMode.setTextColor(Color.WHITE)

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

    private fun updateWorkspaceThemeAndFeed(isWorkMode: Boolean) {
        if (_binding == null || !isAdded) return
        val context = context ?: return

        // UNIFIED CONTAINER TRANSITION: Eliminates vertical jumping between Study Portal and Work Hub
        try {
            val rootFeed = (binding.root as? android.view.ViewGroup)?.getChildAt(0) as? android.view.ViewGroup
                ?: (binding.root as? android.view.ViewGroup)
            if (rootFeed != null) {
                val autoTransition = androidx.transition.AutoTransition().apply {
                    duration = 250L
                    ordering = androidx.transition.TransitionSet.ORDERING_TOGETHER
                    interpolator = androidx.interpolator.view.animation.FastOutSlowInInterpolator()
                }
                androidx.transition.TransitionManager.beginDelayedTransition(rootFeed, autoTransition)
            }
        } catch (_: Exception) {}

        // SLIDING PILL SMOOTH MOTION
        try {
            val toggleGroup = binding.btnStudentMode.parent as? android.view.ViewGroup
            if (toggleGroup != null) {
                val transition = androidx.transition.ChangeBounds().apply {
                    duration = 240L
                    interpolator = android.view.animation.DecelerateInterpolator()
                }
                androidx.transition.TransitionManager.beginDelayedTransition(toggleGroup, transition)
            }
        } catch (_: Exception) {}

        val db = DatabaseHelper.getInstance(context)
        val profileId = ProfileManager.getActiveProfileId()

        // 1. Detach existing dots indicator mediator to avoid stale dot counts
        tabLayoutMediator?.detach()
        tabLayoutMediator = null

        if (isWorkMode) {
            // --- WORK MODE ---
            binding.btnWorkMode.setBackgroundResource(R.drawable.bg_pill_emerald)
            binding.btnWorkMode.setTextColor(Color.WHITE)
            binding.btnStudentMode.background = null
            binding.btnStudentMode.setTextColor(Color.parseColor("#64748B"))

            // Update Header & Portal Banner
            binding.tvWelcomeUser.text = "Client & Project Hub"
            binding.tvPortalCardTitle.text = "Work & Client Hub"
            val orders = db.getClientOrders(profileId)
            val activeOrders = orders.filter { it.status.uppercase() != "COMPLETED" }
            binding.tvEnrolledSubjectsCount.text = "${activeOrders.size} Active Client Deliverables • Invoicing & CRM"
            binding.btnStudyPortal.text = "Work Hub →"
            binding.btnStudyPortal.setTextColor(Color.parseColor("#10B981"))
            binding.btnStudyPortal.strokeColor = ColorStateList.valueOf(Color.parseColor("#10B981"))
            binding.ivPortalCardIcon.setImageResource(R.drawable.ic_work)
            binding.ivPortalCardIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#10B981"))

            // Bind Work Orders into Next-Up Carousel
            if (activeOrders.isNotEmpty()) {
                binding.viewPagerNextUp.visibility = View.VISIBLE
                binding.dotsIndicatorNextUp.visibility = View.VISIBLE
                binding.tvNoClassesScheduled.visibility = View.GONE

                val adapter = WorkOrdersCarouselAdapter(activeOrders) { order ->
                    openOrderDetails(order)
                }
                binding.viewPagerNextUp.adapter = adapter
                binding.viewPagerNextUp.setCurrentItem(0, false)

                tabLayoutMediator = com.google.android.material.tabs.TabLayoutMediator(
                    binding.dotsIndicatorNextUp,
                    binding.viewPagerNextUp
                ) { _, _ -> }
                tabLayoutMediator?.attach()
            } else {
                binding.viewPagerNextUp.visibility = View.GONE
                binding.dotsIndicatorNextUp.visibility = View.GONE
                binding.tvNoClassesScheduled.visibility = View.VISIBLE
                binding.tvNoClassesScheduled.text = "No pending client deliverables • All clear"
            }
        } else {
            // --- STUDENT MODE ---
            binding.btnStudentMode.setBackgroundResource(R.drawable.bg_pill_blue)
            binding.btnStudentMode.setTextColor(Color.WHITE)
            binding.btnWorkMode.background = null
            binding.btnWorkMode.setTextColor(Color.parseColor("#64748B"))

            // Restore Student Header & Portal Banner
            val profilePrefs = context.getSharedPreferences("duorhythm_user_profile_prefs", Context.MODE_PRIVATE)
            val savedName = profilePrefs.getString(com.duorhythm.app.data.local.pref.UserProfileManager.KEY_USER_NAME, null)?.trim()
                ?: profilePrefs.getString("user_display_name", null)?.trim()
            val displayName = if (!savedName.isNullOrBlank()) savedName else "Mahdi Islam Musanna"
            binding.tvWelcomeUser.text = "Welcome back, $displayName"
            binding.tvPortalCardTitle.text = "Study Portal"
            val courses = db.getCourses(profileId)
            binding.tvEnrolledSubjectsCount.text = "${courses.size} Enrolled Subjects • Coursework & Exams"
            binding.btnStudyPortal.text = "Portal →"
            binding.btnStudyPortal.setTextColor(Color.parseColor("#00E5FF"))
            binding.btnStudyPortal.strokeColor = ColorStateList.valueOf(Color.parseColor("#18243C"))
            binding.ivPortalCardIcon.setImageResource(R.drawable.ic_portal)
            binding.ivPortalCardIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#00E5FF"))

            // Reload Academic Classes into Next-Up
            loadStudentNextUpLectures()
        }
    }

    private fun loadStudentNextUpLectures() {
        if (_binding == null || !isAdded) return
        val context = context ?: return
        val db = DatabaseHelper.getInstance(context)
        val profileId = ProfileManager.getActiveProfileId()

        val cal = java.util.Calendar.getInstance()
        val currentIsoDay = when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.MONDAY -> 1
            java.util.Calendar.TUESDAY -> 2
            java.util.Calendar.WEDNESDAY -> 3
            java.util.Calendar.THURSDAY -> 4
            java.util.Calendar.FRIDAY -> 5
            java.util.Calendar.SATURDAY -> 6
            java.util.Calendar.SUNDAY -> 7
            else -> 1
        }

        val nowMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val todayClasses = db.getClassesForIsoDay(profileId, currentIsoDay)

        // Check if all classes today have already ended
        val displayClasses = mutableListOf<ClassEntity>()
        var isShowingTomorrow = false

        if (todayClasses.isNotEmpty()) {
            val lastClass = todayClasses.maxByOrNull { parseTimeToMinutes(it.endTime) }
            val lastClassEndMinutes = lastClass?.let { parseTimeToMinutes(it.endTime) } ?: 0

            if (nowMinutes > lastClassEndMinutes) {
                // All of today's classes ended -> Rollover to Tomorrow!
                val nextIsoDay = if (currentIsoDay == 7) 1 else currentIsoDay + 1
                val tomorrowClasses = db.getClassesForIsoDay(profileId, nextIsoDay)
                if (tomorrowClasses.isNotEmpty()) {
                    displayClasses.addAll(tomorrowClasses)
                    isShowingTomorrow = true
                }
            } else {
                // Show today's upcoming/current classes
                val upcomingToday = todayClasses.filter { parseTimeToMinutes(it.endTime) >= nowMinutes }
                displayClasses.addAll(if (upcomingToday.isNotEmpty()) upcomingToday else todayClasses)
            }
        } else {
            // No classes today -> check tomorrow
            val nextIsoDay = if (currentIsoDay == 7) 1 else currentIsoDay + 1
            val tomorrowClasses = db.getClassesForIsoDay(profileId, nextIsoDay)
            if (tomorrowClasses.isNotEmpty()) {
                displayClasses.addAll(tomorrowClasses)
                isShowingTomorrow = true
            }
        }

        if (displayClasses.isNotEmpty()) {
            binding.viewPagerNextUp.visibility = View.VISIBLE
            binding.dotsIndicatorNextUp.visibility = View.VISIBLE
            binding.tvNoClassesScheduled.visibility = View.GONE

            tabLayoutMediator?.detach()
            tabLayoutMediator = null

            val adapter = NextUpClassAdapter(displayClasses, isShowingTomorrow) {
                (activity as? MainActivity)?.selectNavTab(R.id.menu_schedule)
            }
            binding.viewPagerNextUp.adapter = adapter
            binding.viewPagerNextUp.setCurrentItem(0, false)

            tabLayoutMediator = com.google.android.material.tabs.TabLayoutMediator(
                binding.dotsIndicatorNextUp,
                binding.viewPagerNextUp
            ) { _, _ -> }
            tabLayoutMediator?.attach()
        } else {
            binding.viewPagerNextUp.visibility = View.GONE
            binding.dotsIndicatorNextUp.visibility = View.GONE
            binding.tvNoClassesScheduled.visibility = View.VISIBLE
            binding.tvNoClassesScheduled.text = "No upcoming lectures scheduled"
        }
    }

    private fun parseTimeToMinutes(timeStr: String?): Int {
        if (timeStr.isNullOrBlank()) return 0
        return try {
            val clean = timeStr.trim().uppercase()
            val isPm = clean.contains("PM")
            val parts = clean.replace("AM", "").replace("PM", "").trim().split(":")
            var hour = parts[0].trim().toInt()
            val minute = if (parts.size > 1) parts[1].trim().toInt() else 0
            if (isPm && hour < 12) hour += 12
            if (!isPm && hour == 12) hour = 0
            hour * 60 + minute
        } catch (_: Exception) {
            0
        }
    }

    private fun openOrderDetails(order: ClientOrder) {
        (activity as? MainActivity)?.selectNavTab(R.id.menu_portal)
    }

    fun applyWorkspaceColorTheme(isWorkMode: Boolean) {
        if (_binding != null) {
            updateToggleStyle(!isWorkMode)
            updateWorkspaceThemeAndFeed(isWorkMode)
        }
    }

    private fun setupListeners() {
        try {
            val mainActivity = activity as? MainActivity

            try {
                com.duorhythm.app.util.ViewAnimationExtensions.addSpringPressEffect(binding.btnStudentMode)
                com.duorhythm.app.util.ViewAnimationExtensions.addSpringPressEffect(binding.btnWorkMode)
                com.duorhythm.app.util.ViewAnimationExtensions.addSpringPressEffect(binding.btnStudyPortal)
            } catch (_: Exception) {}

            binding.btnStudentMode.setOnClickListener {
                mainActivity?.applyWorkspaceColorTheme(false)
                mainActivity?.setStudentProfile(true)
                updateToggleStyle(true)
                updateWorkspaceThemeAndFeed(false)
                loadDashboardData()
            }

            binding.btnWorkMode.setOnClickListener {
                mainActivity?.applyWorkspaceColorTheme(true)
                mainActivity?.setStudentProfile(false)
                updateToggleStyle(false)
                updateWorkspaceThemeAndFeed(true)
                loadDashboardData()
            }

            binding.btnStudyPortal.setOnClickListener {
                mainActivity?.selectNavTab(R.id.menu_portal)
            }
            binding.cardStudyPortal.setOnClickListener {
                mainActivity?.selectNavTab(R.id.menu_portal)
            }

            binding.cardFinanceGlance.setOnClickListener {
                mainActivity?.selectNavTab(R.id.menu_wallet)
            }

            binding.cardMissionsGlance.setOnClickListener {
                mainActivity?.selectNavTab(R.id.menu_tasks)
            }

            val prefs = requireContext().getSharedPreferences(AppPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            binding.btnToggleNotes.setOnClickListener {
                isNotesCollapsed = !isNotesCollapsed
                prefs.edit().putBoolean("pref_notes_collapsed", isNotesCollapsed).apply()
                setSectionCollapsed(binding.layoutNotesContent, binding.ivChevronNotes, isNotesCollapsed, animate = true)
            }

            binding.cardHomePortal.setOnClickListener {
                mainActivity?.selectNavTab(R.id.menu_portal)
            }

            binding.cardHomePortalWork.setOnClickListener {
                mainActivity?.selectNavTab(R.id.menu_portal)
            }

            binding.btnHomePortalWorkAction.setOnClickListener {
                mainActivity?.selectNavTab(R.id.menu_portal)
            }

            binding.cardWorkMilestones.setOnClickListener {
                mainActivity?.selectNavTab(R.id.menu_portal)
            }

            binding.btnPrevWorkOrder.setOnClickListener {
                if (activeWorkOrders.isNotEmpty()) {
                    currentWorkOrderIndex = (currentWorkOrderIndex - 1 + activeWorkOrders.size) % activeWorkOrders.size
                    bindWorkOrderCard()
                }
            }

            binding.btnNextWorkOrder.setOnClickListener {
                if (activeWorkOrders.isNotEmpty()) {
                    currentWorkOrderIndex = (currentWorkOrderIndex + 1) % activeWorkOrders.size
                    bindWorkOrderCard()
                }
            }

            binding.btnQuickFocusTimer.setOnClickListener {
                PomodoroDialog(requireContext()).show()
            }

            binding.btnQuickCalculator.setOnClickListener {
                PercentageCalcDialog(requireContext()).show()
            }

            binding.btnNewQuickNote.setOnClickListener {
                showAddNoteDialog()
            }

            binding.toolHomePomodoro.setOnClickListener {
                PomodoroDialog(requireContext()).show()
            }

            binding.toolHomeCalc.setOnClickListener {
                PercentageCalcDialog(requireContext()).show()
            }

            binding.btnHomeOpenTools.setOnClickListener {
                mainActivity?.openTools()
            }

            binding.btnHomeAddNote.setOnClickListener {
                showAddNoteDialog()
            }

            // Fast Workspace Profile Switcher Header Button
            activity?.findViewById<View>(R.id.btnQuickProfile)?.setOnClickListener {
                openProfileSwitcher()
            }

            AnimationUtils.attachTactileFeedback(
                binding.cardFinanceGlance,
                binding.cardMissionsGlance,
                binding.cardHomePortal,
                binding.cardWorkMilestones,
                binding.btnPrevWorkOrder,
                binding.btnNextWorkOrder,
                binding.btnHomeOpenTools,
                binding.btnHomeAddNote,
                binding.btnNewQuickNote,
                binding.btnQuickFocusTimer,
                binding.btnQuickCalculator,
                binding.toolHomePomodoro,
                binding.toolHomeCalc,
                binding.ivUserProfile,
                binding.btnStudentMode,
                binding.btnWorkMode
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

    fun openProfileSwitcher() {
        val act = activity as? androidx.fragment.app.FragmentActivity ?: return
        com.duorhythm.app.ui.profile.WorkspaceProfilesBottomSheet.newInstance {
            loadUserProfile()
            loadDashboardData()
        }.show(act.supportFragmentManager, com.duorhythm.app.ui.profile.WorkspaceProfilesBottomSheet.TAG)
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

            val formattedBalance = if (totalRawBalance < 0) {
                "-$currentSymbol" + numberFormat.format(kotlin.math.abs(totalRawBalance))
            } else {
                currentSymbol + numberFormat.format(totalRawBalance)
            }
            binding.tvFinanceBalanceMain.text = formattedBalance

            // Monthly Income and Expense: e.g. +৳2,000.00 / -৳1,700.00
            val formattedIncome = numberFormat.format(monthlyIncome)
            val formattedExpense = numberFormat.format(monthlyExpense)
            binding.tvFinanceDeltaSub.text = "+$currentSymbol${formattedIncome} / -$currentSymbol${formattedExpense}"

            // 2. Tasks / Missions Overview
            val allTasks = dbHelper.getAllTasks("ALL")
            val pendingTasks = allTasks.filter { !it.isCompleted }
            val completedTasks = allTasks.filter { it.isCompleted }
            val pendingCount = pendingTasks.size
            val totalTasks = allTasks.size
            val highPriorityCount = pendingTasks.count { it.priority.equals("HIGH", ignoreCase = true) }

            binding.badgeMissionRatio.text = "${completedTasks.size}/$totalTasks"
            binding.tvMissionsTitleCount.text = "$pendingCount Active"
            binding.tvMissionsSubtitle.text = if (highPriorityCount > 0) "$highPriorityCount high priority" else if (pendingCount == 0) "All tasks clear" else "Standard queue"

            // 3. Dynamic Daily Class Carousel with Smart Landing Selection
            try {
                loadNextUpSchedule()
            } catch (e: Throwable) {
                e.printStackTrace()
            }

            // 4. Portal Overview
            val workPortalEnabled = dbHelper.isWorkPortalEnabled()
            val isStudent = !workPortalEnabled || ((activity as? MainActivity)?.isStudentProfile() ?: true)

            if (!workPortalEnabled) {
                binding.toggleStudentWork.visibility = View.GONE
            } else {
                binding.toggleStudentWork.visibility = View.VISIBLE
            }

            updateToggleStyle(isStudent)
            updateWorkspaceThemeAndFeed(!isStudent)

            if (isStudent) {
                binding.tvHomePortalTitle.text = "Study Portal"
                val subjects = dbHelper.getAllStudentSubjects()
                binding.tvHomePortalSummary.text = "${subjects.size} Enrolled Subjects"
                binding.tvHomePortalSubtext.text = "Assignments, exams, and notes"
            } else {
                binding.tvHomePortalTitle.text = "Work Portal"
                val workOrders = dbHelper.getAllWorkOrders()
                val pendingOrders = workOrders.filter { !it.status.equals("COMPLETED", ignoreCase = true) }
                binding.tvHomePortalSummary.text = "${pendingOrders.size} Active Orders"
                binding.tvHomePortalSubtext.text = "Client projects and deliverables"
                activeWorkOrders = if (pendingOrders.isNotEmpty()) pendingOrders else workOrders
                bindWorkOrderCard()
            }

            // 4. Quick Notes & Scratchpad
            loadDashboardNotes()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupNextUpCarousel() {
        if (_binding == null) return
        binding.dotsIndicatorNextUp.setOnTouchListener { _, _ -> true }

        binding.btnNextUpPrev.setOnClickListener {
            val current = binding.viewPagerNextUp.currentItem
            if (current > 0) binding.viewPagerNextUp.setCurrentItem(current - 1, true)
        }

        binding.btnNextUpNext.setOnClickListener {
            val adapter = binding.viewPagerNextUp.adapter ?: return@setOnClickListener
            val current = binding.viewPagerNextUp.currentItem
            if (current < adapter.itemCount - 1) binding.viewPagerNextUp.setCurrentItem(current + 1, true)
        }
    }

    private fun loadNextUpSchedule() {
        if (_binding == null || !isAdded) return
        val workPortalEnabled = dbHelper.isWorkPortalEnabled()
        val isStudent = !workPortalEnabled || ((activity as? MainActivity)?.isStudentProfile() ?: true)
        if (isStudent) {
            loadStudentNextUpLectures()
        }
    }

    private fun loadDashboardNotes() {
        if (_binding == null) return
        try {
            val notes = dbHelper.getAllNotes()
            quickNotesAdapter.updateList(notes)
            if (notes.isEmpty()) {
                binding.rvHomeQuickNotes.visibility = View.GONE
                binding.tvEmptyHomeQuickNotes.visibility = View.GONE
                binding.tvQuickNotePreview.visibility = View.VISIBLE
                binding.scrollScratchpadPreview.visibility = View.GONE
            } else {
                binding.rvHomeQuickNotes.visibility = View.VISIBLE
                binding.tvEmptyHomeQuickNotes.visibility = View.GONE
                binding.tvQuickNotePreview.visibility = View.GONE
                binding.scrollScratchpadPreview.visibility = View.GONE
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

        var selectedColor = "#1E293B"
        try {
            dialogBinding.btnNotePalette.backgroundTintList = ColorStateList.valueOf(Color.parseColor(selectedColor))
        } catch (_: Exception) {}

        dialogBinding.btnNotePalette.setOnClickListener {
            ColorSliderView.showDialog(requireContext(), selectedColor) { chosenHex ->
                selectedColor = chosenHex
                try {
                    dialogBinding.btnNotePalette.backgroundTintList = ColorStateList.valueOf(Color.parseColor(chosenHex))
                } catch (_: Exception) {}
            }
        }

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
                colorHex = selectedColor,
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
        dialogBinding.tvNoteDialogTitle.text = "Edit Note"
        dialogBinding.etNoteTitle.setText(note.title)
        dialogBinding.etNoteContent.setText(note.content)
        dialogBinding.btnSaveNote.text = "Save Changes"

        var selectedColor = if (note.colorHex.isNotBlank()) note.colorHex else "#1E293B"
        try {
            dialogBinding.btnNotePalette.backgroundTintList = ColorStateList.valueOf(Color.parseColor(selectedColor))
        } catch (_: Exception) {}

        dialogBinding.btnNotePalette.setOnClickListener {
            ColorSliderView.showDialog(requireContext(), selectedColor) { chosenHex ->
                selectedColor = chosenHex
                try {
                    dialogBinding.btnNotePalette.backgroundTintList = ColorStateList.valueOf(Color.parseColor(chosenHex))
                } catch (_: Exception) {}
            }
        }

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
                colorHex = selectedColor,
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

    private fun bindWorkOrderCard() {
        if (_binding == null) return
        val orders = activeWorkOrders
        if (orders.isEmpty()) {
            binding.layoutActiveWorkOrder.visibility = View.GONE
            binding.layoutEmptyWorkOrders.visibility = View.VISIBLE
            binding.btnPrevWorkOrder.visibility = View.GONE
            binding.btnNextWorkOrder.visibility = View.GONE
            binding.tvDeliverablesBadge.text = "WORK MODE"
            return
        }

        binding.layoutActiveWorkOrder.visibility = View.VISIBLE
        binding.layoutEmptyWorkOrders.visibility = View.GONE

        if (orders.size > 1) {
            binding.btnPrevWorkOrder.visibility = View.VISIBLE
            binding.btnNextWorkOrder.visibility = View.VISIBLE
        } else {
            binding.btnPrevWorkOrder.visibility = View.GONE
            binding.btnNextWorkOrder.visibility = View.GONE
        }

        if (currentWorkOrderIndex >= orders.size) {
            currentWorkOrderIndex = 0
        }

        val order = orders[currentWorkOrderIndex]
        binding.tvWorkMilestoneTitle.text = order.title
        val client = if (order.clientName.isNotBlank()) order.clientName else "Direct Client"
        val priceStr = if (order.totalPrice > 0.0) {
            val formatted = java.text.DecimalFormat("#,##0").format(order.totalPrice)
            "$client • ৳$formatted"
        } else {
            client
        }
        binding.tvWorkMilestoneSubtext.text = priceStr

        // Status
        val statusText = order.status.uppercase()
        binding.tvWorkMilestoneStatus.text = statusText
        binding.tvWorkMilestoneStatus.setTextColor(
            when (statusText) {
                "COMPLETED" -> Color.parseColor("#10B981")
                "IN_PROGRESS", "IN PROGRESS" -> Color.parseColor("#38BDF8")
                else -> Color.parseColor("#F59E0B")
            }
        )

        // Countdown calculation
        val countdownInfo = calculateCountdown(order.endDate)
        binding.tvWorkMilestoneCountdown.text = countdownInfo.first
        binding.tvWorkMilestoneCountdown.setTextColor(countdownInfo.second)

        binding.tvWorkOrderCounter.text = "${currentWorkOrderIndex + 1} of ${orders.size}"
        binding.tvDeliverablesBadge.text = "${orders.size} ACTIVE"
    }

    private fun calculateCountdown(endDateStr: String): Pair<String, Int> {
        if (endDateStr.isBlank()) return Pair("No Deadline", Color.parseColor("#052E16"))
        val formats = listOf("yyyy-MM-dd HH:mm", "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy")
        var date: Date? = null
        for (fmt in formats) {
            try {
                date = SimpleDateFormat(fmt, Locale.US).parse(endDateStr)
                if (date != null) break
            } catch (_: Exception) {}
        }
        if (date == null) return Pair("Due: $endDateStr", Color.parseColor("#052E16"))

        val diffMillis = date.time - System.currentTimeMillis()
        return if (diffMillis < 0) {
            val daysAgo = kotlin.math.abs(diffMillis) / (1000 * 60 * 60 * 24)
            if (daysAgo == 0L) Pair("Overdue Today", Color.parseColor("#DC2626"))
            else Pair("Overdue $daysAgo d", Color.parseColor("#DC2626"))
        } else {
            val hours = diffMillis / (1000 * 60 * 60)
            val days = diffMillis / (1000 * 60 * 60 * 24)
            when {
                days > 1 -> Pair("Due in $days d", Color.parseColor("#052E16"))
                days == 1L -> Pair("Due Tomorrow", Color.parseColor("#052E16"))
                hours > 0 -> Pair("Due in ${hours}h", Color.parseColor("#052E16"))
                else -> Pair("Due Soon", Color.parseColor("#052E16"))
            }
        }
    }

    override fun onDestroyView() {
        WalletDataSyncManager.unregister(walletSyncListener)
        super.onDestroyView()
        _binding = null
    }
}
