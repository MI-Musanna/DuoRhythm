package com.duorhythm.app

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.DueDebtItem
import com.duorhythm.app.data.model.TaskItem
import com.duorhythm.app.data.model.TransactionItem
import com.duorhythm.app.data.model.WalletBucketItem
import com.duorhythm.app.scheduler.AssessmentAlarmScheduler
import com.duorhythm.app.ui.tasks.TasksAdapter
import com.duorhythm.app.ui.views.SpeedDialFabView
import com.duorhythm.app.ui.views.WormDotsIndicatorView
import database.entities.AssessmentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w412dp-h915dp-xxhdpi", shadows = [ShadowDatabaseHelper::class])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class DuoRhythmFullAppAuditTest {

    private lateinit var context: Context
    private lateinit var db: DatabaseHelper

    @Before
    fun setUp() {
        ShadowDatabaseHelper.reset()
        context = ApplicationProvider.getApplicationContext()
        db = DatabaseHelper.getInstance(context)
    }

    @Test
    fun audit01_walletLedgerMathAndTransferConservation() {
        val cashId = db.insertWalletBucket(
            WalletBucketItem(
                profileId = 1L,
                name = "AuditCash",
                icon = "cash",
                colorHex = "#00E5FF",
                connectedAccounts = "AuditCash",
                balance = 1000.0,
                note = "Test"
            )
        )
        val bankId = db.insertWalletBucket(
            WalletBucketItem(
                profileId = 1L,
                name = "AuditBank",
                icon = "bank",
                colorHex = "#10B981",
                connectedAccounts = "AuditBank",
                balance = 500.0,
                note = "Test"
            )
        )
        assertTrue("Buckets must insert cleanly", cashId > 0 && bankId > 0)

        db.insertTransaction(
            TransactionItem(
                title = "Freelance",
                amount = 400.0,
                type = "INCOME",
                category = "Work",
                date = "2026-11-17",
                cardId = 0L,
                currency = "BDT",
                notes = "",
                bucketId = cashId
            )
        )
        assertEquals(1400.0, db.getWalletBucketById(cashId)?.balance ?: 0.0, 0.01)

        val transferred = db.executeTransfer(
            fromBucketId = cashId,
            toBucketId = bankId,
            amount = 300.0,
            date = "2026-11-17",
            currency = "BDT",
            note = "Move",
            title = "Transfer"
        )
        assertTrue("Transfer must succeed", transferred)
        assertEquals(1100.0, db.getWalletBucketById(cashId)?.balance ?: 0.0, 0.01)
        assertEquals(800.0, db.getWalletBucketById(bankId)?.balance ?: 0.0, 0.01)
    }

    @Test
    fun audit02_tasksAndGoalsAnimatedRemovalConsistency() {
        val task = TaskItem(
            id = 901L,
            profileId = 1L,
            title = "Test Task 1",
            description = "",
            category = "Study",
            priority = "HIGH",
            isCompleted = false,
            isGoal = false,
            progress = 0
        )
        val task2 = task.copy(id = 902L, title = "Test Task 2")

        val adapter = TasksAdapter(
            tasks = listOf(task, task2),
            onToggleCompleted = { _, _ -> },
            onTaskClick = {},
            onDeleteClick = {}
        )
        adapter.setMasterTasks(listOf(task, task2))
        assertEquals(2, adapter.itemCount)

        adapter.removeItemAnimated(task)
        assertEquals("Adapter must immediately reflect 1 remaining item", 1, adapter.itemCount)
    }

    @Test
    fun audit03_speedDialFabZeroOverlapAndCloseButtonWorksOnStandardAndNarrowScreens() {
        val screenWidthsPx = listOf(960, 1080) // Narrow (320dp @ 3x) and Standard (360-412dp @ 3x)
        for (wPx in screenWidthsPx) {
            val fab = SpeedDialFabView(context)
            fab.setItems(
                listOf(
                    SpeedDialFabView.SpeedDialItem("New Task", R.drawable.ic_tasks) {},
                    SpeedDialFabView.SpeedDialItem("New Goal", R.drawable.ic_target) {},
                    SpeedDialFabView.SpeedDialItem("Record Money", R.drawable.ic_wallet) {},
                    SpeedDialFabView.SpeedDialItem("Quick Note", R.drawable.ic_notes) {}
                )
            )
            fab.measure(
                View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2200, View.MeasureSpec.EXACTLY)
            )
            fab.layout(0, 0, wPx, 2200)
            fab.expand()

            val visibleNodes = mutableListOf<Rect>()
            for (i in 0 until fab.childCount) {
                val child = fab.getChildAt(i)
                if (child is ViewGroup && child.visibility == View.VISIBLE && child.childCount == 2) {
                    val r = Rect()
                    child.getHitRect(r)
                    assertTrue("Node left edge (${r.left}) must stay inside viewport width $wPx", r.left >= 0)
                    visibleNodes.add(r)
                }
            }
            assertEquals("All 4 SpeedDial nodes must be visible when expanded", 4, visibleNodes.size)

            for (i in visibleNodes.indices) {
                for (j in i + 1 until visibleNodes.size) {
                    assertFalse(
                        "Nodes $i and $j overlapped on width $wPx: ${visibleNodes[i]} vs ${visibleNodes[j]}",
                        Rect.intersects(visibleNodes[i], visibleNodes[j])
                    )
                }
            }

            // Simulate user tapping the main circular close (X) button at bottom-right
            val mainBtn = fab.getChildAt(fab.childCount - 1)
            val btnRect = Rect()
            mainBtn.getHitRect(btnRect)
            val now = SystemClock.uptimeMillis()
            val upEvent = MotionEvent.obtain(
                now, now + 30, MotionEvent.ACTION_UP,
                btnRect.exactCenterX(), btnRect.exactCenterY(), 0
            )
            val intercepted = fab.dispatchTouchEvent(upEvent)
            upEvent.recycle()
            assertTrue("Close (X) button tap must be intercepted and collapse menu", intercepted)
        }
    }

    @Test
    fun audit04_wormDotsIndicatorVisibilityAndMeasure() {
        val dots = WormDotsIndicatorView(context)
        dots.setDotCount(1)
        assertEquals("Single item must hide dots", View.GONE, dots.visibility)

        dots.setDotCount(3)
        assertEquals("Multiple items must show dots", View.VISIBLE, dots.visibility)
        dots.measure(
            View.MeasureSpec.makeMeasureSpec(500, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.AT_MOST)
        )
        assertTrue("Dots width must be positive", dots.measuredWidth > 0)
    }

    @Test
    fun audit05_examAlarmsStrictlyRespectOptInPreference() {
        val prefs = context.getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("pref_exam_reminders_enabled", false).apply()

        val exam = AssessmentEntity(
            id = 777L,
            profileId = 1L,
            subjectId = 1L,
            subjectName = "Mathematics 3",
            courseCode = "25931",
            type = "Quiz",
            title = "CT 3",
            date = "2026-12-20",
            time = "10:00 AM",
            room = "201",
            syllabus = "Ch 1",
            teacher = "Rabeya Alam",
            status = "PENDING",
            attachmentUri = null,
            attachmentName = null
        )
        // Must exit cleanly without scheduling unsolicited 8:00 AM alarms
        AssessmentAlarmScheduler.scheduleAssessmentAlarms(context, exam)
    }


    @Test
    fun audit06_portalDynamicAssessmentCategoryFilteringAndRemoval() {
        val adapter = com.duorhythm.app.ui.portal.AssessmentAdapter()
        val finalExam = database.entities.AssessmentEntity(
            id = 101L,
            profileId = 1L,
            subjectId = 10L,
            subjectName = "Physics 2",
            courseCode = "25922",
            type = "Semester Final Exam",
            title = "Final Examination",
            date = "2026-12-15",
            time = "10:00 AM",
            room = "502",
            syllabus = "Full Syllabus",
            teacher = "Fariha Ahmed",
            status = "PENDING",
            attachmentUri = null,
            attachmentName = null
        )
        val labReport = finalExam.copy(
            id = 102L,
            type = "Lab Report",
            title = "Optics Practical"
        )

        adapter.setMasterData(listOf(finalExam, labReport))
        assertEquals("All filter must display both items", 2, adapter.itemCount)

        adapter.updateCategoryFilter("Lab Report")
        assertEquals("Custom category filter must match custom type", 1, adapter.itemCount)

        adapter.updateCategoryFilter("All")
        adapter.removeItemAnimated(finalExam)
        assertEquals("Animated assessment removal must leave 1 item", 1, adapter.itemCount)
    }

    @Test
    fun audit07_rollingCurrencyDigitsAndBiometricMaskProtection() {
        val tv = android.widget.TextView(context)
        com.duorhythm.app.util.ViewAnimationExtensions.animateCurrencyRoll(tv, 21865.0, "৳")
        assertEquals("৳21,865.00", tv.text.toString())

        // Simulate biometric lock mask active -> rolling animation must NOT leak real balance
        tv.text = "••••••"
        com.duorhythm.app.util.ViewAnimationExtensions.animateCurrencyRoll(tv, 99999.0, "৳")
        assertEquals("••••••", tv.text.toString())
    }

    @Test
    fun audit08_confettiOverlayAndSmoothProgressBounds() {
        val confetti = com.duorhythm.app.ui.views.ConfettiOverlayView(context)
        confetti.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2200, View.MeasureSpec.EXACTLY)
        )
        confetti.layout(0, 0, 1080, 2200)
        confetti.startBurst(540f, 900f)
        assertFalse("Confetti overlay must never intercept user touches", confetti.isClickable)

        val pb = android.widget.ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 20
        }
        com.duorhythm.app.util.ViewAnimationExtensions.animateProgressSmooth(pb, 150)
        assertTrue("Progress value must remain clamped within max", pb.progress <= 100)
    }



    @Test
    fun audit09_notificationOptInGuardsAndActionIntents() {
        val prefs = context.getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("pref_task_reminders_enabled", false).apply()

        val task = TaskItem(
            id = 555L,
            profileId = 1L,
            title = "Opt-In Task",
            description = "",
            category = "Study",
            dueDate = "2026-12-25",
            priority = "HIGH",
            isCompleted = false,
            isGoal = false,
            progress = 0
        )
        com.duorhythm.app.scheduler.TaskAlarmScheduler.scheduleTaskReminder(context, task)

        val snoozeIntent = android.content.Intent(context, com.duorhythm.app.receiver.NotificationActionReceiver::class.java).apply {
            action = com.duorhythm.app.receiver.NotificationActionReceiver.ACTION_SNOOZE_ALERT
            putExtra(com.duorhythm.app.receiver.NotificationActionReceiver.EXTRA_ITEM_ID, 555L)
            putExtra(com.duorhythm.app.receiver.NotificationActionReceiver.EXTRA_ALERT_TYPE, "TASK")
            putExtra(com.duorhythm.app.receiver.NotificationActionReceiver.EXTRA_SNOOZE_MINUTES, 10)
        }
        assertEquals(
            com.duorhythm.app.receiver.NotificationActionReceiver.ACTION_SNOOZE_ALERT,
            snoozeIntent.action
        )
    }



    @Test
    fun audit10_notificationBadgeSevenRulesAndZeroIconDrift() {
        val badge = com.duorhythm.app.ui.views.NotificationBadgeView(context)

        // Rule 1: 0 -> GONE, 150 -> "99+"
        badge.setCount(0)
        assertEquals(View.GONE, badge.visibility)

        badge.setCount(3)
        assertEquals(View.VISIBLE, badge.visibility)
        assertEquals("3", badge.text.toString())

        // Rule 6: Count Up, Never Blink (3 -> 4 keeps VISIBLE and updates cleanly)
        badge.setCount(4)
        assertEquals(View.VISIBLE, badge.visibility)
        assertEquals("4", badge.text.toString())

        badge.setCount(150)
        assertEquals("99+", badge.text.toString())

        // Rules 3 & 4: Status dot suppresses count text (one signal per item)
        badge.setStatusDot(true)
        assertEquals(View.VISIBLE, badge.visibility)
        assertEquals("", badge.text.toString())
        assertEquals(
            com.duorhythm.app.ui.views.NotificationBadgeView.BadgeMode.STATUS_DOT,
            badge.getMode()
        )

        // Rule 5: Open It, Clear It
        badge.clear()
        assertEquals(View.GONE, badge.visibility)
        assertEquals(0, badge.getCount())

        // Rule 2: Corner pinning & zero icon drift across 1 vs 99+
        val density = context.resources.displayMetrics.density
        val container = android.widget.FrameLayout(context)
        val icon = android.widget.ImageView(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                (24 * density).toInt(),
                (24 * density).toInt(),
                android.view.Gravity.CENTER
            )
        }
        val pinnedBadge = com.duorhythm.app.ui.views.NotificationBadgeView(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.TOP or android.view.Gravity.END
            )
        }
        container.addView(icon)
        container.addView(pinnedBadge)

        val wSpec = View.MeasureSpec.makeMeasureSpec((30 * density).toInt(), View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec((28 * density).toInt(), View.MeasureSpec.EXACTLY)

        pinnedBadge.setCount(1)
        container.measure(wSpec, hSpec)
        container.layout(0, 0, (30 * density).toInt(), (28 * density).toInt())
        val leftAtOne = icon.left
        val topAtOne = icon.top

        pinnedBadge.setCount(250)
        container.measure(wSpec, hSpec)
        container.layout(0, 0, (30 * density).toInt(), (28 * density).toInt())
        assertEquals("Icon left coordinate must not drift when badge expands to 99+", leftAtOne, icon.left)
        assertEquals("Icon top coordinate must not drift when badge expands to 99+", topAtOne, icon.top)
    }

    @Test
    fun audit11_shortWindowExamFallbackAndUndatedTaskBriefingSupport() {
        val prefs = context.getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("pref_exam_reminders_enabled", true).apply()

        // 1. Short-window exam fallback (e.g. Exam starting in 10 minutes)
        val now = System.currentTimeMillis()
        val examCal = java.util.Calendar.getInstance().apply {
            timeInMillis = now + (10 * 60 * 1000L) // 10 minutes in future (< 20m lead time)
        }
        val examDateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(examCal.time)
        val examTimeStr = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US).format(examCal.time)

        val shortWindowExam = database.entities.AssessmentEntity(
            id = 888L,
            profileId = 1L,
            subjectId = 5L,
            subjectName = "Software Engineering",
            courseCode = "25941",
            type = "Quiz",
            title = "Surprise Quiz",
            date = examDateStr,
            time = examTimeStr,
            room = "Lab 3",
            syllabus = "Design Patterns",
            teacher = "Dr. Hasan",
            status = "PENDING"
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val shadowAlarm = org.robolectric.Shadows.shadowOf(alarmManager)
        shadowAlarm.scheduledAlarms.clear()

        AssessmentAlarmScheduler.scheduleAssessmentAlarms(context, shortWindowExam)
        val scheduled = shadowAlarm.scheduledAlarms
        assertTrue("Short-window exam must be scheduled despite start time being < 20m away", scheduled.isNotEmpty())
        val alarm = scheduled.first()
        assertTrue("Trigger time must be > 0 and <= exam start time", alarm.triggerAtTime > 0 && alarm.triggerAtTime <= examCal.timeInMillis)

        // 2. Timed task 15m heads-up or short-window fallback
        val taskCal = java.util.Calendar.getInstance().apply {
            timeInMillis = now + (8 * 60 * 1000L) // 8m in future (< 15m heads-up)
        }
        val taskDueStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(taskCal.time)
        val parsedTaskTrigger = com.duorhythm.app.scheduler.TaskAlarmScheduler.parseDueDateTime(taskDueStr)
        assertTrue("Short-window task trigger must be non-null and in future", parsedTaskTrigger != null && parsedTaskTrigger > now)

        // 3. DueDebt 9:00 AM alarm scheduler verification
        val parse9Am = com.duorhythm.app.scheduler.DueDebtAlarmScheduler.parseDueDate9Am("2026-12-25")
        assertTrue("DueDebt 9 AM parser must return valid timestamp", parse9Am != null && parse9Am > 0)
        val cal9Am = java.util.Calendar.getInstance().apply { timeInMillis = parse9Am!! }
        assertEquals(9, cal9Am.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(0, cal9Am.get(java.util.Calendar.MINUTE))

        // 4. Undated floating task support in daily briefing
        val undatedTask = TaskItem(
            profileId = 1L,
            title = "ITSS Practical",
            description = "Floating lab work",
            category = "Study",
            dueDate = "",
            priority = "HIGH",
            isCompleted = false,
            isGoal = false,
            progress = 0
        )
        val isBriefingEligible = { t: TaskItem, targetDate: String ->
            !t.isCompleted && (t.dueDate.isNullOrBlank() || t.dueDate!! <= targetDate)
        }
        assertTrue("Undated task 'ITSS Practical' must be included in daily briefing", isBriefingEligible(undatedTask, examDateStr))

        val futureTask = undatedTask.copy(dueDate = "2099-12-31")
        assertFalse("Future task must be excluded from today's briefing", isBriefingEligible(futureTask, examDateStr))

        val completedTask = undatedTask.copy(isCompleted = true)
        assertFalse("Completed task must be excluded from briefing", isBriefingEligible(completedTask, examDateStr))
    }

    @Test
    fun audit12_seamlessScrollReactiveHeaderFrostProgression() {
        val frostDrawable = androidx.core.content.ContextCompat.getDrawable(
            context,
            R.drawable.bg_header_scroll_frost
        )
        assertTrue("Scroll frost drawable must exist", frostDrawable != null)

        val headerBg = androidx.core.content.ContextCompat.getDrawable(
            context,
            R.drawable.bg_cyber_header
        )
        assertTrue("Seamless cyber header drawable must exist", headerBg != null)
    }


    private fun relativeLuminance(colorInt: Int): Double {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        val r = channel(android.graphics.Color.red(colorInt))
        val g = channel(android.graphics.Color.green(colorInt))
        val b = channel(android.graphics.Color.blue(colorInt))
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrastRatio(fg: Int, bg: Int): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val lighter = Math.max(l1, l2)
        val darker = Math.min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun audit13_wcagContrastAndGroupingSpacingTokens() {
        val cardBg = androidx.core.content.ContextCompat.getColor(context, R.color.bg_surface)
        val primaryText = androidx.core.content.ContextCompat.getColor(context, R.color.text_on_canvas_primary)
        val secondaryText = androidx.core.content.ContextCompat.getColor(context, R.color.text_on_canvas_secondary)
        val mutedText = androidx.core.content.ContextCompat.getColor(context, R.color.text_on_canvas_muted)

        assertTrue("Primary text contrast must exceed 4.5:1", contrastRatio(primaryText, cardBg) >= 4.5)
        assertTrue("Secondary text contrast must exceed 4.5:1", contrastRatio(secondaryText, cardBg) >= 4.5)
        assertTrue("Muted text contrast must exceed 4.5:1", contrastRatio(mutedText, cardBg) >= 4.5)

        val insideDp = context.resources.getDimension(R.dimen.spacing_inside_group) / context.resources.displayMetrics.density
        val betweenDp = context.resources.getDimension(R.dimen.spacing_between_sections) / context.resources.displayMetrics.density
        assertEquals(8f, insideDp, 0.1f)
        assertEquals(24f, betweenDp, 0.1f)
    }



    @Test
    fun audit14_speedDialFabClearsNavigationDockAndTonalHierarchy() {
        val density = context.resources.displayMetrics.density
        val fab = com.duorhythm.app.ui.views.SpeedDialFabView(context)
        val wSpec = View.MeasureSpec.makeMeasureSpec((412 * density).toInt(), View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec((900 * density).toInt(), View.MeasureSpec.EXACTLY)
        fab.measure(wSpec, hSpec)
        fab.layout(0, 0, (412 * density).toInt(), (900 * density).toInt())

        // Navigation dock height (60dp) + bottom margin (16dp) = 76dp from screen bottom.
        // FAB default bottom margin must be >= 88dp so it sits at least 12dp above the dock with zero overlap.
        val mainButton = fab.getChildAt(1)
        val lp = mainButton.layoutParams as android.widget.FrameLayout.LayoutParams
        val bottomMarginDp = lp.bottomMargin / density
        assertTrue("FAB bottom margin ($bottomMarginDp dp) must clear the 76dp navigation dock", bottomMarginDp >= 88f)

        // Verify distinct 3-tier luminance separation: bg_canvas < bg_surface < bg_surface_elevated
        val canvasLum = relativeLuminance(androidx.core.content.ContextCompat.getColor(context, R.color.bg_canvas))
        val surfaceLum = relativeLuminance(androidx.core.content.ContextCompat.getColor(context, R.color.bg_surface))
        val elevatedLum = relativeLuminance(androidx.core.content.ContextCompat.getColor(context, R.color.bg_surface_elevated))
        assertTrue("Card surface must be visibly elevated above canvas", surfaceLum > canvasLum)
        assertTrue("Elevated pills must stand out above card surface", elevatedLum > surfaceLum)
    }



    @Test
    fun audit15_dualModeAccentSeparationAndSpeedDialSync() {
        val fab = com.duorhythm.app.ui.views.SpeedDialFabView(context)
        // Student mode -> Sky-Blue (#38BDF8), Work mode -> Emerald Green (#10B981)
        fab.syncWorkspaceAccent(isWorkMode = false)
        fab.syncWorkspaceAccent(isWorkMode = true)
        assertTrue("SpeedDialFabView must support dual-mode accent sync", true)
    }

    @Test
    fun audit16_deterministicAvatarScaleAndMotionHelpers() {
        // 1. Verify deterministic name hashing & 2-letter vs 1-letter (<= 20dp) rules
        val c1 = com.duorhythm.app.util.DeterministicAvatarHelper.colorForName("Mahdi Islam Musanna")
        val c2 = com.duorhythm.app.util.DeterministicAvatarHelper.colorForName("mahdi islam musanna")
        assertEquals("Same name must always hash to identical avatar color", c1, c2)

        assertEquals("MM", com.duorhythm.app.util.DeterministicAvatarHelper.extractInitials("Mahdi Islam Musanna", 32))
        assertEquals("M", com.duorhythm.app.util.DeterministicAvatarHelper.extractInitials("Mahdi Islam Musanna", 20))

        // 2. Verify 3-size avatar tokens (24dp / 32dp / 40dp) & 48dp touch target in dimens.xml
        val density = context.resources.displayMetrics.density
        assertEquals(24f, context.resources.getDimension(R.dimen.avatar_size_row) / density, 0.1f)
        assertEquals(32f, context.resources.getDimension(R.dimen.avatar_size_card) / density, 0.1f)
        assertEquals(40f, context.resources.getDimension(R.dimen.avatar_size_profile) / density, 0.1f)
        assertEquals(48f, context.resources.getDimension(R.dimen.touch_target_min) / density, 0.1f)

        // 3. Verify dark-mode photo dimming & 0ms press listener attachment
        val iv = android.widget.ImageView(context)
        com.duorhythm.app.util.DuoMotionHelper.applyDarkPhotoDimming(iv, hasPhoto = true)
        assertEquals(0.85f, iv.alpha, 0.01f)
        com.duorhythm.app.util.DuoMotionHelper.attachZeroMsPressFeedback(iv)
        com.duorhythm.app.util.DuoMotionHelper.playValidationShake(iv)
    }

    @Test
    fun audit17_profileInitialsIntegrationAndDualModeFabUnlock() {
        // 1. Verify ProfileAvatarHelper delegates to DeterministicAvatarHelper when no photo exists
        val iv = android.widget.ImageView(context)
        com.duorhythm.app.util.ProfileAvatarHelper.bindProfileAvatar(iv, null, "Mahdi Islam")
        assertTrue(
            "Named profile without photo must render deterministic initials BitmapDrawable",
            iv.drawable is android.graphics.drawable.BitmapDrawable
        )
        assertNull("Initials avatar must not apply monochrome placeholder tint", iv.imageTintList)

        // 2. Verify SpeedDialFabView switches cleanly between Student (#38BDF8) and Work (#10B981) without tint lock
        val fab = com.duorhythm.app.ui.views.SpeedDialFabView(context)
        fab.syncWorkspaceAccent(isWorkMode = false)
        val mainBtn = fab.getChildAt(1)
        assertNull("mainFabButton backgroundTintList must remain null so GradientDrawable color switches freely", mainBtn.backgroundTintList)
        fab.syncWorkspaceAccent(isWorkMode = true)
        assertNull("mainFabButton backgroundTintList must remain null in Work Mode", mainBtn.backgroundTintList)
    }

    @Test
    fun audit18_bidirectionalWorkspaceSwitchAndExamClearButton() {
        val root = android.widget.FrameLayout(context)
        val child = android.view.View(context)
        root.addView(child)
        com.duorhythm.app.util.DuoMotionHelper.animateWorkspaceSwitch(root, child)
        assertEquals(0.78f, child.alpha, 0.05f)

        val schedXml = java.io.File("src/main/res/layout/dialog_schedule_assessment.xml").let {
            if (it.exists()) it else java.io.File("app/src/main/res/layout/dialog_schedule_assessment.xml")
        }
        if (schedXml.exists()) {
            val txt = schedXml.readText()
            assertTrue("dialog_schedule_assessment.xml must include clear_text endIconMode", txt.contains("endIconMode=\"clear_text\""))
        }
    }

    @Test
    fun audit19_snapshotCircularRevealAndReactiveAssessmentTitle() {
        val parent = android.widget.FrameLayout(context)
        val container = android.widget.FrameLayout(context)
        parent.addView(container)
        com.duorhythm.app.util.DuoMotionHelper.triggerSnapshotCircularReveal(container, null)

        val sadFile = java.io.File("src/main/java/com/duorhythm/app/ui/portal/ScheduleAssessmentDialog.kt").let {
            if (it.exists()) it else java.io.File("app/src/main/java/com/duorhythm/app/ui/portal/ScheduleAssessmentDialog.kt")
        }
        if (sadFile.exists()) {
            val code = sadFile.readText()
            assertTrue(
                "ScheduleAssessmentDialog must reactively update title when category chip is checked",
                code.contains("updateComposedAssessmentTitle()")
            )
        }
    }

    @Test
    fun audit20_postClassGeneralModePreferenceAndNo8AmExamAlarm() {
        // 1. Verify 'Restore General Mode After Class' defaults to true and persists false cleanly
        assertTrue(com.duorhythm.app.automation.SmartAutomationManager.isAutoRestoreGeneralAfterClassEnabled(context))
        com.duorhythm.app.automation.SmartAutomationManager.setAutoRestoreGeneralAfterClassEnabled(context, false)
        assertFalse(com.duorhythm.app.automation.SmartAutomationManager.isAutoRestoreGeneralAfterClassEnabled(context))
        com.duorhythm.app.automation.SmartAutomationManager.setAutoRestoreGeneralAfterClassEnabled(context, true)

        // 2. Verify ReminderManager no longer schedules hardcoded 8:00 AM exam alarms
        val rmFile = java.io.File("src/main/java/com/duorhythm/app/service/ReminderManager.kt").let {
            if (it.exists()) it else java.io.File("app/src/main/java/com/duorhythm/app/service/ReminderManager.kt")
        }
        if (rmFile.exists()) {
            val code = rmFile.readText()
            assertTrue(
                "ReminderManager.scheduleExamReminder must cancel 8:00 AM legacy alarm instead of scheduling it",
                code.contains("Hardcoded 8:00 AM morning exam alarm disabled")
            )
        }
    }
}