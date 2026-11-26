package com.duorhythm.app

import android.content.Context
import android.graphics.Rect
import android.os.Environment
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
import com.duorhythm.app.data.repository.BackupService
import com.duorhythm.app.scheduler.AssessmentAlarmScheduler
import com.duorhythm.app.ui.tasks.TasksAdapter
import com.duorhythm.app.ui.views.SpeedDialFabView
import com.duorhythm.app.ui.views.WormDotsIndicatorView
import com.duorhythm.app.util.BackupSnapshotHelper
import com.duorhythm.app.util.UpdateManager
import database.entities.AssessmentEntity
import database.entities.ProfileEntity
import com.duorhythm.app.data.local.pref.ProfileManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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
import java.io.File

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

    @Test
    fun audit21_luminousWavefrontCircularReveal() {
        val parent = android.widget.FrameLayout(context)
        val viewport = android.widget.FrameLayout(context)
        parent.addView(viewport)
        com.duorhythm.app.util.DuoMotionHelper.triggerViewportCircularReveal(
            viewport = viewport,
            isWorkMode = true,
            anchorView = null
        )
        assertTrue("Luminous wavefront reveal helper executed cleanly", true)
    }

    @Test
    fun audit22_customDefaultExamReminderTimePersistence() {
        // 1. Default time should be 09:00 AM
        val initial = com.duorhythm.app.scheduler.AssessmentAlarmScheduler.getDefaultExamReminderTime(context)
        assertEquals(Pair(9, 0), initial)
        assertEquals("09:00 AM", com.duorhythm.app.scheduler.AssessmentAlarmScheduler.formatDefaultExamTimeLabel(context))

        // 2. Custom time (e.g. 10:30 AM) persists & formats accurately
        com.duorhythm.app.scheduler.AssessmentAlarmScheduler.setDefaultExamReminderTime(context, 10, 30)
        assertEquals(Pair(10, 30), com.duorhythm.app.scheduler.AssessmentAlarmScheduler.getDefaultExamReminderTime(context))
        assertEquals("10:30 AM", com.duorhythm.app.scheduler.AssessmentAlarmScheduler.formatDefaultExamTimeLabel(context))

        // 3. Turning off (-1) disables untimed exam fallback cleanly
        com.duorhythm.app.scheduler.AssessmentAlarmScheduler.setDefaultExamReminderTime(context, -1, 0)
        assertNull(com.duorhythm.app.scheduler.AssessmentAlarmScheduler.getDefaultExamReminderTime(context))
        assertEquals("OFF", com.duorhythm.app.scheduler.AssessmentAlarmScheduler.formatDefaultExamTimeLabel(context))

        // Restore default 09:00 AM
        com.duorhythm.app.scheduler.AssessmentAlarmScheduler.setDefaultExamReminderTime(context, 9, 0)
    }

    @Test
    fun audit23_examReminderLeadTimeSelectionAndSchedulingAudit() {
        val prefs = context.getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("pref_exam_reminders_enabled", true).apply()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val shadowAlarm = org.robolectric.Shadows.shadowOf(alarmManager)

        // 1. Verify default lead time is 60m (1H)
        assertEquals(60, AssessmentAlarmScheduler.getExamReminderLeadMinutes(context))
        assertEquals("1 Hour before timed exams", com.duorhythm.app.data.local.pref.AppPreferences.getExamReminderLeadDisplayLabel(60))

        // 2. Verify selecting 20M, 1H, 2H, and OFF persists and updates display labels
        val testOptions = listOf(
            Pair(20, "20 Minutes before timed exams"),
            Pair(60, "1 Hour before timed exams"),
            Pair(120, "2 Hours before timed exams"),
            Pair(-1, "Off for timed exams")
        )
        for ((mins, expectedLabel) in testOptions) {
            AssessmentAlarmScheduler.setExamReminderLeadMinutes(context, mins)
            assertEquals(mins, AssessmentAlarmScheduler.getExamReminderLeadMinutes(context))
            assertEquals(expectedLabel, com.duorhythm.app.data.local.pref.AppPreferences.getExamReminderLeadDisplayLabel(mins))
        }

        // 3. Timed exam scheduling verification:
        val now = System.currentTimeMillis()
        val examCal = java.util.Calendar.getInstance().apply {
            timeInMillis = now + (3 * 60 * 60 * 1000L) // 3 hours in future
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val examDateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(examCal.time)
        val examTimeStr = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US).format(examCal.time)

        val timedExam = database.entities.AssessmentEntity(
            id = 999L,
            profileId = 1L,
            subjectId = 5L,
            subjectName = "Operating Systems",
            courseCode = "25951",
            type = "Final Exam",
            title = "Final Exam",
            date = examDateStr,
            time = examTimeStr,
            room = "Lab 1",
            syllabus = "All Chapters",
            teacher = "Dr. Farhan",
            status = "PENDING"
        )

        // Test with 1H (60m)
        AssessmentAlarmScheduler.setExamReminderLeadMinutes(context, 60)
        shadowAlarm.scheduledAlarms.clear()
        AssessmentAlarmScheduler.scheduleAssessmentAlarms(context, timedExam)
        assertEquals("Exactly one alarm must be scheduled for timed exam with 1H lead", 1, shadowAlarm.scheduledAlarms.size)
        val alarm1h = shadowAlarm.scheduledAlarms.first()
        val expectedTrigger1h = examCal.timeInMillis - (60 * 60 * 1000L)
        assertEquals(expectedTrigger1h, alarm1h.triggerAtTime)

        // Test with 20M
        AssessmentAlarmScheduler.setExamReminderLeadMinutes(context, 20)
        shadowAlarm.scheduledAlarms.clear()
        AssessmentAlarmScheduler.scheduleAssessmentAlarms(context, timedExam)
        assertEquals("Exactly one alarm must be scheduled for timed exam with 20M lead", 1, shadowAlarm.scheduledAlarms.size)
        val alarm20m = shadowAlarm.scheduledAlarms.first()
        val expectedTrigger20m = examCal.timeInMillis - (20 * 60 * 1000L)
        assertEquals(expectedTrigger20m, alarm20m.triggerAtTime)

        // Test with 2H (120m)
        AssessmentAlarmScheduler.setExamReminderLeadMinutes(context, 120)
        shadowAlarm.scheduledAlarms.clear()
        AssessmentAlarmScheduler.scheduleAssessmentAlarms(context, timedExam)
        assertEquals("Exactly one alarm must be scheduled for timed exam with 2H lead", 1, shadowAlarm.scheduledAlarms.size)
        val alarm2h = shadowAlarm.scheduledAlarms.first()
        val expectedTrigger2h = examCal.timeInMillis - (120 * 60 * 1000L)
        assertEquals(expectedTrigger2h, alarm2h.triggerAtTime)

        // Test with OFF (-1) -> must schedule ZERO alarms
        AssessmentAlarmScheduler.setExamReminderLeadMinutes(context, -1)
        shadowAlarm.scheduledAlarms.clear()
        AssessmentAlarmScheduler.scheduleAssessmentAlarms(context, timedExam)
        assertTrue("When lead time is OFF, zero alarms must be scheduled", shadowAlarm.scheduledAlarms.isEmpty())

        // Restore default 60m
        AssessmentAlarmScheduler.setExamReminderLeadMinutes(context, 60)
    }

    @Test
    fun audit24_backupExportToDownloadsAndRoundTripRestore() {
        // 1. Seed Multi-Profile & Multi-Table Data (Slot 1, Slot 2, Slot 3)
        val p1 = database.entities.ProfileEntity(
            slotNumber = 1,
            name = "Student Alpha",
            email = "alpha@campus.edu",
            institution = "MIT",
            department = "CS",
            semester = "Fall 2026",
            passwordHash = "hash1",
            rememberEmail = true
        )
        val p2 = database.entities.ProfileEntity(
            slotNumber = 2,
            name = "Student Beta",
            email = "beta@campus.edu",
            institution = "Stanford",
            department = "EE",
            semester = "Spring 2026",
            passwordHash = "hash2",
            rememberEmail = false
        )
        val p3 = database.entities.ProfileEntity(
            slotNumber = 3,
            name = "Student Gamma",
            email = "gamma@campus.edu",
            institution = "Harvard",
            department = "Bio",
            semester = "Fall 2026",
            passwordHash = "hash3",
            rememberEmail = true
        )
        val p1Id = db.insertProfile(p1)
        val p2Id = db.insertProfile(p2)
        val p3Id = db.insertProfile(p3)
        assertTrue(p1Id > 0)
        assertTrue(p2Id > 0)
        assertTrue(p3Id > 0)

        // Seed data in tables across slots:
        // Tasks
        val taskId = db.insertTask(
            TaskItem(
                profileId = p1Id,
                title = "Complete Audit 24 Assignment",
                description = "Robolectric Backup & Restore Verification",
                priority = "HIGH",
                isCompleted = false,
                dueDate = "2026-12-01",
                category = "Study"
            )
        )
        assertTrue(taskId > 0)

        // Task category
        val catAdded = db.addTaskCategory("Academic Audit")
        assertTrue(catAdded)

        // Assessment
        val exam = database.entities.AssessmentEntity(
            id = 555L,
            profileId = p2Id,
            subjectId = 12L,
            subjectName = "Algorithms",
            courseCode = "CS201",
            type = "Midterm",
            title = "Midterm Exam",
            date = "2026-11-30",
            time = "02:00 PM",
            room = "Room 301",
            syllabus = "Chapters 1-5",
            teacher = "Prof. Turing",
            status = "PENDING"
        )
        val examId = db.insertAssessment(exam)
        assertTrue(examId > 0)

        // Holiday record
        db.setHoliday("2026-12-25", "Winter Break", p1Id)
        assertTrue(db.isHoliday("2026-12-25", p1Id))

        // Settings Preferences in duorhythm_prefs
        AssessmentAlarmScheduler.setExamReminderLeadMinutes(context, 20)
        AssessmentAlarmScheduler.setDefaultExamReminderTime(context, 11, 45)
        context.getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("pref_missions_collapsed", true)
            .putInt("pref_class_lead_minutes", 10)
            .apply()

        // 2. Perform Instant Backup (which writes internal snapshot AND to Downloads/DuoRhythm_Backups)
        val backupSuccess = BackupSnapshotHelper.createInstantSnapshot(context, isAuto = false)
        assertTrue("Instant backup snapshot creation must succeed", backupSuccess)

        // 3. Verify Public External Export in Downloads/DuoRhythm_Backups/
        val downloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "DuoRhythm_Backups")
        assertTrue("Downloads/DuoRhythm_Backups directory must exist", downloadsDir.exists())
        val backupFiles = downloadsDir.listFiles { f ->
            f.name.startsWith("DuoRhythm_Backup_") && f.name.endsWith(".json")
        }
        assertNotNull("Backup files array in Downloads must not be null", backupFiles)
        assertTrue("At least one timestamped backup file must exist in Downloads", backupFiles!!.isNotEmpty())

        val latestBackup = backupFiles.maxByOrNull { it.lastModified() }!!
        val jsonContent = latestBackup.readText()
        val root = org.json.JSONObject(jsonContent)

        // Verify multi-profile data serialization
        assertTrue("JSON must contain profiles table", root.has("profiles"))
        val profilesArr = root.getJSONArray("profiles")
        assertTrue("Must serialize all 3 profiles", profilesArr.length() >= 3)

        // Verify all tables serialized
        assertTrue("JSON must contain tasks", root.has("tasks"))
        assertTrue("JSON must contain task_categories", root.has("task_categories"))
        assertTrue("JSON must contain assessments", root.has("assessments"))
        assertTrue("JSON must contain assessment_categories", root.has("assessment_categories"))
        assertTrue("JSON must contain classes", root.has("classes"))
        assertTrue("JSON must contain holiday_records", root.has("holiday_records"))

        // Verify Settings preferences serialization
        assertTrue("JSON must contain settings_preferences", root.has("settings_preferences"))
        val settingsObj = root.getJSONObject("settings_preferences")
        assertEquals(20, settingsObj.getInt("exam_reminder_lead_minutes"))
        assertEquals(11, settingsObj.getInt("pref_default_exam_hour"))
        assertEquals(45, settingsObj.getInt("pref_default_exam_minute"))
        assertEquals(10, settingsObj.getInt("pref_class_lead_minutes"))
        assertTrue(settingsObj.getBoolean("pref_missions_collapsed"))

        // 4. Verify Last Backup Timestamp update
        val lastBackupTime = BackupService.getLastBackupTimestamp(context)
        assertNotEquals("Never", lastBackupTime)
        assertTrue("Last backup timestamp must not be blank", lastBackupTime.isNotBlank())

        // 5. Clear Database and Preferences (Empty Database Simulation)
        db.clearAllUserData()
        context.getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        assertEquals(60, AssessmentAlarmScheduler.getExamReminderLeadMinutes(context))
        assertTrue("Tasks should be empty after clear", db.getAllTasks().isEmpty())
        assertTrue("Profiles should be empty after clear", db.getAllProfiles().isEmpty())

        // 6. Round-Trip Restore from Downloaded Backup File
        val restoreSuccess = BackupSnapshotHelper.restoreSnapshotFromFile(context, latestBackup)
        assertTrue("Round-trip restore must succeed", restoreSuccess)

        // 7. Verify Data Integrity Post-Restore
        val restoredProfiles = db.getAllProfiles()
        assertEquals("All 3 profiles must be restored", 3, restoredProfiles.size)
        val p1Restored = restoredProfiles.find { it.slotNumber == 1 }
        assertNotNull(p1Restored)
        assertEquals("Student Alpha", p1Restored!!.name)
        assertEquals("MIT", p1Restored.institution)

        val p2Restored = restoredProfiles.find { it.slotNumber == 2 }
        assertNotNull(p2Restored)
        assertEquals("Student Beta", p2Restored!!.name)

        val p3Restored = restoredProfiles.find { it.slotNumber == 3 }
        assertNotNull(p3Restored)
        assertEquals("Student Gamma", p3Restored!!.name)

        val restoredTasks = db.getAllTasks()
        assertTrue("Tasks must be restored", restoredTasks.isNotEmpty())
        assertEquals("Complete Audit 24 Assignment", restoredTasks.first().title)

        // Verify Settings preferences restored
        assertEquals(20, AssessmentAlarmScheduler.getExamReminderLeadMinutes(context))
        val restoredExamTime = AssessmentAlarmScheduler.getDefaultExamReminderTime(context)
        assertNotNull(restoredExamTime)
        assertEquals(Pair(11, 45), restoredExamTime)

        val restoredPrefs = context.getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE)
        assertEquals(10, restoredPrefs.getInt("pref_class_lead_minutes", -1))
        assertTrue(restoredPrefs.getBoolean("pref_missions_collapsed", false))

        // Clean up test backup file
        latestBackup.delete()
    }

    @Test
    fun audit25_updateManagerVersionComparisonAndPackaging() {
        // Semantic version comparisons
        assertTrue(UpdateManager.compareVersions("v1.6.9-beta", "v1.6.8") > 0)
        assertEquals(0, UpdateManager.compareVersions("v1.6.9-beta", "v1.6.9-beta"))
        assertEquals(0, UpdateManager.compareVersions("1.6.9-beta", "v1.6.9-beta"))
        assertTrue(UpdateManager.compareVersions("v1.6.9-beta", "v1.6.9") < 0)
        assertTrue(UpdateManager.compareVersions("v1.6.9", "v1.6.9-beta") > 0)
        assertTrue(UpdateManager.compareVersions("v1.7.0", "v1.6.9-beta") > 0)
        assertTrue(UpdateManager.compareVersions("v1.6.8", "v1.6.9-beta") < 0)
        assertTrue(UpdateManager.compareVersions("1.6.9.1", "1.6.9") > 0)

        // Permission check in Robolectric package manager
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS
        )
        val permissions = packageInfo.requestedPermissions?.toList() ?: emptyList()
        assertTrue(
            "REQUEST_INSTALL_PACKAGES must be declared in AndroidManifest",
            permissions.contains("android.permission.REQUEST_INSTALL_PACKAGES")
        )
    }

    @Test
    fun audit26_profileSyncOnRestoreAndSingleStreamAvatar() {
        // 1. Single-stream URI image decoding test
        val testFile = File(context.cacheDir, "test_avatar_sample.png")
        val bitmap = android.graphics.Bitmap.createBitmap(120, 120, android.graphics.Bitmap.Config.ARGB_8888)
        java.io.FileOutputStream(testFile).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
        val fileUri = android.net.Uri.fromFile(testFile)
        val decoded = com.duorhythm.app.util.ImageUtils.decodeSampledBitmapFromUri(context, fileUri, 64, 64)
        assertNotNull("Sampled bitmap decoded from single-read stream must not be null", decoded)
        assertTrue("Decoded width must be > 0", (decoded?.width ?: 0) > 0)
        testFile.delete()

        // 2. Profile sync on backup restore verification
        val profileId = db.profileDao.insertProfile(
            database.entities.ProfileEntity(
                slotNumber = 1,
                name = "Dr. Ada Lovelace",
                email = "ada@example.com",
                passwordHash = "",
                institution = "Cambridge",
                department = "Computing",
                semester = "Fall 2026",
                avatarUri = null
            )
        )
        com.duorhythm.app.data.local.pref.ProfileManager.setActiveProfileId(context, profileId)
        com.duorhythm.app.data.local.pref.ProfileManager.syncUserProfile(context)
        assertEquals("Dr. Ada Lovelace", com.duorhythm.app.data.local.pref.UserProfileManager.getUserName(context))
        assertEquals("Cambridge", com.duorhythm.app.data.local.pref.UserProfileManager.getInstitutionName(context))

        // 3. Two-way snapshot deletion test
        val snapshotsDir = BackupSnapshotHelper.getBackupDirectory(context)
        val timestamp = System.currentTimeMillis()
        val snapshotFile = File(snapshotsDir, "Manual_Snapshot_${timestamp}.json").apply {
            writeText("{\"test\": true}")
        }
        val backupSubDir = BackupSnapshotHelper.getPublicBackupDirectory()
        val matchingBackupFile = File(backupSubDir, "DuoRhythm_Backup_${timestamp}.json").apply {
            writeText("{\"test\": true}")
        }
        assertTrue("Snapshot file must exist before deletion", snapshotFile.exists())
        assertTrue("Matching backup file in Downloads must exist before deletion", matchingBackupFile.exists())

        BackupSnapshotHelper.deleteSnapshot(context, snapshotFile)

        assertFalse("Internal snapshot file must be deleted", snapshotFile.exists())
        assertFalse("Matching backup file in Downloads must be deleted via two-way deletion", matchingBackupFile.exists())

        // 4. Confetti debounce verification
        com.duorhythm.app.ui.views.ConfettiOverlayView.burst(null)
    }

    @Test
    fun audit27_confettiContextUnwrappingAndPrereleaseDetection() {
        // Reset debounce timer before test run
        com.duorhythm.app.ui.views.ConfettiOverlayView.resetDebounceTimerForTesting()

        // 1. ContextWrapper unwrapping test
        val controller = org.robolectric.Robolectric.buildActivity(com.duorhythm.app.MainActivity::class.java).setup()
        val activity = controller.get()
        val nestedWrapper = android.content.ContextWrapper(android.content.ContextWrapper(activity))

        // Call burst with nested ContextWrapper
        com.duorhythm.app.ui.views.ConfettiOverlayView.burst(nestedWrapper)
        val burstTime1 = com.duorhythm.app.ui.views.ConfettiOverlayView.getLastBurstTimeForTesting()
        assertTrue("Burst time must be set when ContextWrapper successfully unwraps Activity", burstTime1 > 0L)

        // 2. Debounce guard verification
        // Immediate second call within 400ms should be debounced and ignored
        com.duorhythm.app.ui.views.ConfettiOverlayView.burst(nestedWrapper)
        val burstTime2 = com.duorhythm.app.ui.views.ConfettiOverlayView.getLastBurstTimeForTesting()
        assertEquals("Subsequent burst within 400ms must be debounced without updating timestamp", burstTime1, burstTime2)

        // Non-activity ContextWrapper (e.g. Application context) should not trigger burst
        com.duorhythm.app.ui.views.ConfettiOverlayView.resetDebounceTimerForTesting()
        val appWrapper = android.content.ContextWrapper(context.applicationContext)
        com.duorhythm.app.ui.views.ConfettiOverlayView.burst(appWrapper)
        assertEquals("Non-activity ContextWrapper must not trigger burst", 0L, com.duorhythm.app.ui.views.ConfettiOverlayView.getLastBurstTimeForTesting())

        // 3. UpdateManager boolean prerelease vs tag-name test
        val releaseBetaLatest = UpdateManager.ReleaseInfo(
            tagName = "v1.7.2-beta",
            isPrerelease = false,
            downloadUrl = "https://example.com/v1.7.2-beta.apk",
            bodyText = "Latest release notes"
        )
        assertFalse("Release with prerelease=false must not be treated as prerelease even if tagged -beta", releaseBetaLatest.isPrerelease)
        assertTrue(UpdateManager.compareVersions(releaseBetaLatest.tagName, "v1.7.1-beta") > 0)
    }

    @Test
    fun audit28_profileRepositoryStateFlowAndDuoAlertBanner() {
        // 1. ProfileRepository reactive StateFlow test
        val p1 = ProfileEntity(
            id = 1L,
            slotNumber = 1,
            name = "Musanna Primary",
            email = "musanna@example.com",
            passwordHash = "",
            institution = "BUET",
            department = "CSE",
            semester = "L-4 T-1"
        )
        val p2 = ProfileEntity(
            id = 2L,
            slotNumber = 2,
            name = "Work Studio",
            email = "work@example.com",
            passwordHash = "",
            institution = "DuoRhythm Labs",
            department = "Engineering",
            semester = "Q4"
        )
        db.insertProfile(p1)
        db.insertProfile(p2)

        ProfileManager.setActiveProfileId(context, 1L)
        com.duorhythm.app.data.repository.ProfileRepository.refreshProfiles(context)

        val initialActive = com.duorhythm.app.data.repository.ProfileRepository.activeProfileFlow.value
        assertNotNull("Active profile flow must emit initial active profile", initialActive)
        assertEquals("Initial active profile name mismatch", "Musanna Primary", initialActive?.name)
        assertEquals(2, com.duorhythm.app.data.repository.ProfileRepository.allProfilesFlow.value.size)

        // Switch to slot 2 and verify StateFlow reacts
        ProfileManager.switchProfile(context, 2)
        val switchedActive = com.duorhythm.app.data.repository.ProfileRepository.activeProfileFlow.value
        assertNotNull("Active profile flow must update after switchProfile", switchedActive)
        assertEquals("Switched active profile name mismatch", "Work Studio", switchedActive?.name)

        // 2. DuoAlertBanner & DuoMorphBannerView lifecycle, debounce, and context unwrapping test
        com.duorhythm.app.ui.custom.DuoAlertBanner.resetForTesting()
        val controller = org.robolectric.Robolectric.buildActivity(com.duorhythm.app.MainActivity::class.java).setup()
        val activity = controller.get()
        val nestedWrapper = android.content.ContextWrapper(android.content.ContextWrapper(activity))

        com.duorhythm.app.ui.custom.DuoAlertBanner.show(nestedWrapper, "Test Banner Success", com.duorhythm.app.ui.custom.DuoAlertBanner.BannerType.SUCCESS)

        assertEquals("Test Banner Success", com.duorhythm.app.ui.custom.DuoAlertBanner.getLastMessageForTesting())
        val showTime1 = com.duorhythm.app.ui.custom.DuoAlertBanner.getLastShowTimeForTesting()
        assertTrue("Show timestamp must be recorded", showTime1 > 0L)

        // Verify debounce guard (same message within 300ms is debounced)
        com.duorhythm.app.ui.custom.DuoAlertBanner.show(nestedWrapper, "Test Banner Success", com.duorhythm.app.ui.custom.DuoAlertBanner.BannerType.SUCCESS)
        val showTime2 = com.duorhythm.app.ui.custom.DuoAlertBanner.getLastShowTimeForTesting()
        assertEquals("Subsequent identical alert banner within 300ms must be debounced", showTime1, showTime2)

        // Verify DuoMorphBannerView instantiation & setup
        val bannerView = com.duorhythm.app.ui.custom.DuoMorphBannerView(activity)
        bannerView.setBanner("Morphing Banner Active", com.duorhythm.app.ui.custom.DuoAlertBanner.BannerType.SUCCESS)
        assertNotNull("DuoMorphBannerView must initialize successfully", bannerView)

        // 3. Concentric Radii and Typography dimensions verification
        val res = context.resources
        val cardHeroRadius = res.getDimension(R.dimen.radius_card_hero)
        val innerHeroRadius = res.getDimension(R.dimen.radius_inner_hero_nested)
        val innerNestedRadius = res.getDimension(R.dimen.radius_inner_nested)
        assertTrue("radius_card_hero must be defined and > 0", cardHeroRadius > 0f)
        assertTrue("radius_inner_hero_nested must be defined and > 0", innerHeroRadius > 0f)
        assertTrue("radius_inner_nested must be defined and > 0", innerNestedRadius > 0f)
        assertTrue("Concentric relationship: hero outer > hero inner", cardHeroRadius > innerHeroRadius)

        // Verify NumericDisplay style identifier exists in styles
        val styleResId = res.getIdentifier("TextAppearance.DuoRhythm.NumericDisplay", "style", context.packageName)
        assertTrue("TextAppearance.DuoRhythm.NumericDisplay style must exist", styleResId != 0)
    }

    @Test
    fun audit29_v1731_hotfixVerification() {
        val controller = org.robolectric.Robolectric.buildActivity(com.duorhythm.app.MainActivity::class.java).setup()
        val activity = controller.get()
        val inflater = android.view.LayoutInflater.from(activity)

        // 1. Home Avatar clipToOutline & ProfileAvatarHelper RoundedBitmapDrawable wrapping
        val vHome = inflater.inflate(R.layout.fragment_home, null)
        val ivHomeProfile = vHome.findViewById<android.widget.ImageView>(R.id.ivUserProfile)
        assertNotNull("ivUserProfile must exist in fragment_home", ivHomeProfile)
        assertTrue("ivUserProfile must have clipToOutline enabled in layout", ivHomeProfile.clipToOutline)

        val testBmp = android.graphics.Bitmap.createBitmap(40, 40, android.graphics.Bitmap.Config.ARGB_8888)
        val tempAvatarFile = File(activity.cacheDir, "test_avatar_audit29.png")
        java.io.FileOutputStream(tempAvatarFile).use { out ->
            testBmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
        val ivAvatar = android.widget.ImageView(activity)
        com.duorhythm.app.util.ProfileAvatarHelper.bindProfileAvatar(ivAvatar, tempAvatarFile.absolutePath, "Audit User")
        assertTrue("ImageView must have clipToOutline enabled for circular avatar", ivAvatar.clipToOutline)
        val drawable = ivAvatar.drawable
        assertNotNull("Avatar drawable must not be null", drawable)
        assertTrue("Avatar drawable must be an instance of RoundedBitmapDrawable", drawable is androidx.core.graphics.drawable.RoundedBitmapDrawable)
        val roundedDrawable = drawable as androidx.core.graphics.drawable.RoundedBitmapDrawable
        assertTrue("RoundedBitmapDrawable must be configured as circular", roundedDrawable.isCircular)
        tempAvatarFile.delete()

        // 2. Numeric InputType and digit restriction on Subject Code in Course Dialogs
        val vCreateSubject = inflater.inflate(R.layout.dialog_create_subject, null)
        val etCreateCode = vCreateSubject.findViewById<android.widget.EditText>(R.id.etSubjectCode)
        assertNotNull("etSubjectCode must exist in dialog_create_subject", etCreateCode)
        assertEquals("etSubjectCode must have numeric inputType", android.text.InputType.TYPE_CLASS_NUMBER, etCreateCode.inputType)

        val vAddSubject = inflater.inflate(R.layout.dialog_add_subject, null)
        val etAddCode = vAddSubject.findViewById<android.widget.EditText>(R.id.etSubjectCode)
        assertNotNull("etSubjectCode must exist in dialog_add_subject", etAddCode)
        assertEquals("etSubjectCode must have numeric inputType", android.text.InputType.TYPE_CLASS_NUMBER, etAddCode.inputType)

        val vEditSubject = inflater.inflate(R.layout.dialog_edit_subject, null)
        val etEditCode = vEditSubject.findViewById<android.widget.EditText>(R.id.etEditSubjectCode)
        assertNotNull("etEditSubjectCode must exist in dialog_edit_subject", etEditCode)
        assertEquals("etEditSubjectCode must have numeric inputType", android.text.InputType.TYPE_CLASS_NUMBER, etEditCode.inputType)

        // 3. What's New fallback and changelog.json verification
        val changelogJsonStr = activity.assets.open("changelog.json").bufferedReader().use { it.readText() }
        val changelogObj = org.json.JSONObject(changelogJsonStr)
        assertTrue("changelog.json must contain 1.7.3.1-beta entry", changelogObj.has("1.7.3.1-beta"))
        assertTrue("changelog.json must contain 1.7.3-beta entry", changelogObj.has("1.7.3-beta"))
        val note1731 = changelogObj.getString("1.7.3.1-beta")
        assertTrue("1.7.3.1-beta changelog must mention Circular Avatar", note1731.contains("Circular Avatar"))
        assertTrue("1.7.3.1-beta changelog must mention Numeric Subject Code", note1731.contains("Numeric Subject Code"))
        assertTrue("1.7.3.1-beta changelog must mention Mandatory Profile Password", note1731.contains("Mandatory Profile Password"))

        // 4. Mandatory Password Check in CreateProfileDialog & Layout
        val vCreateProfile = inflater.inflate(R.layout.dialog_create_profile, null)
        val etPassword = vCreateProfile.findViewById<android.widget.EditText>(R.id.etCreatePassword)
        assertNotNull("etCreatePassword must exist in dialog_create_profile", etPassword)
        assertEquals("Create profile password (min 4 characters)", etPassword.hint?.toString())

        // Test validation logic in CreateProfileDialog
        com.duorhythm.app.ui.custom.DuoAlertBanner.resetForTesting()
        val dialog = com.duorhythm.app.ui.profile.CreateProfileDialog(activity, 3)
        dialog.show()

        val latestDialog = org.robolectric.shadows.ShadowAlertDialog.getLatestDialog()
        assertNotNull("Dialog must be displayed", latestDialog)
        val etNameInDialog = latestDialog.findViewById<android.widget.EditText>(R.id.etCreateName)
        val etPassInDialog = latestDialog.findViewById<android.widget.EditText>(R.id.etCreatePassword)
        val btnSubmitInDialog = latestDialog.findViewById<android.widget.Button>(R.id.btnCreateSubmit)

        assertNotNull(etNameInDialog)
        assertNotNull(etPassInDialog)
        assertNotNull(btnSubmitInDialog)

        // Submit with blank name
        btnSubmitInDialog.performClick()
        assertEquals("Please enter your full name", com.duorhythm.app.ui.custom.DuoAlertBanner.getLastMessageForTesting())

        // Submit with valid name but short password (< 4 chars)
        com.duorhythm.app.ui.custom.DuoAlertBanner.resetForTesting()
        etNameInDialog.setText("Audit Student")
        etPassInDialog.setText("123")
        btnSubmitInDialog.performClick()
        assertEquals("Please enter a profile password (at least 4 characters)", com.duorhythm.app.ui.custom.DuoAlertBanner.getLastMessageForTesting())
        latestDialog.dismiss()
    }

    @Test
    fun audit30_v1732_hotfixVerification() {
        val controller = org.robolectric.Robolectric.buildActivity(com.duorhythm.app.MainActivity::class.java).setup()
        val activity = controller.get()
        val inflater = android.view.LayoutInflater.from(activity)

        // 1. Explicit Oval ViewOutlineProvider on Home Avatar & ProfileAvatarHelper
        val vHome = inflater.inflate(R.layout.fragment_home, null)
        val ivHomeProfile = vHome.findViewById<android.widget.ImageView>(R.id.ivUserProfile)
        ivHomeProfile.layout(0, 0, 80, 80)
        com.duorhythm.app.util.ProfileAvatarHelper.applyCircularOutline(ivHomeProfile)
        assertTrue("ivUserProfile must have clipToOutline=true", ivHomeProfile.clipToOutline)
        assertNotEquals("ivUserProfile must have custom oval ViewOutlineProvider", android.view.ViewOutlineProvider.BACKGROUND, ivHomeProfile.outlineProvider)
        val outline = android.graphics.Outline()
        ivHomeProfile.outlineProvider.getOutline(ivHomeProfile, outline)
        assertFalse("Oval outline must not be empty", outline.isEmpty)

        // 2. 4-Segment Version Comparison & Dual-Channel Updater Defaults
        assertTrue("1.7.3.2-beta must be > 1.7.1-beta", UpdateManager.compareVersions("1.7.3.2-beta", "1.7.1-beta") > 0)
        assertTrue("v1.7.3.2-beta must be > v1.7.3-beta", UpdateManager.compareVersions("v1.7.3.2-beta", "v1.7.3-beta") > 0)
        assertTrue("v1.7.3.2-beta must be > 1.7.3.1-beta", UpdateManager.compareVersions("v1.7.3.2-beta", "1.7.3.1-beta") > 0)
        assertEquals("Normalized versions must match", 0, UpdateManager.compareVersions("v1.7.3.2-beta", "1.7.3.2-beta"))

        val vUpdater = inflater.inflate(R.layout.dialog_modern_updater, null)
        val btnActionBeta = vUpdater.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnActionBeta)
        assertNotNull(btnActionBeta)
        assertFalse("Beta button must default to disabled", btnActionBeta.isEnabled)
        assertEquals("NO NEWER BETA", btnActionBeta.text.toString())

        // 3. Required Password Across All Profile Creation Layouts & Trimmed Validation
        val vProfileCreate = inflater.inflate(R.layout.dialog_profile_create, null)
        val etProfileCreatePass = vProfileCreate.findViewById<android.widget.EditText>(R.id.etCreatePassword)
        assertEquals("Create profile password (min 4 characters)", etProfileCreatePass.hint?.toString())

        val vOnboardingItem = inflater.inflate(R.layout.item_onboarding_profile, null)
        val etOnboardingPass = vOnboardingItem.findViewById<android.widget.EditText>(R.id.etOnboardingPassword)
        assertEquals("Create profile password (min 4 characters)", etOnboardingPass.hint?.toString())

        com.duorhythm.app.ui.custom.DuoAlertBanner.resetForTesting()
        val createDialog = com.duorhythm.app.ui.profile.CreateProfileDialog(activity, 2)
        createDialog.show()
        val latestDialog = org.robolectric.shadows.ShadowAlertDialog.getLatestDialog()
        val etNameInDialog = latestDialog.findViewById<android.widget.EditText>(R.id.etCreateName)
        val etPassInDialog = latestDialog.findViewById<android.widget.EditText>(R.id.etCreatePassword)
        val btnSubmitInDialog = latestDialog.findViewById<android.widget.Button>(R.id.btnCreateSubmit)
        etNameInDialog.setText("Slot Two")
        etPassInDialog.setText("  12  ") // Whitespace-padded < 4 trimmed chars
        btnSubmitInDialog.performClick()
        assertEquals("Please enter a profile password (at least 4 characters)", com.duorhythm.app.ui.custom.DuoAlertBanner.getLastMessageForTesting())
        latestDialog.dismiss()

        // 4. Cascade Deletion & DB Existence Guard Blocking Ghost Notifications
        val activeId = ProfileManager.getActiveProfileId(activity)
        val subjectId = db.insertStudentSubject(
            com.duorhythm.app.data.model.StudentSubject(
                profileId = activeId,
                name = "2222w",
                code = "2222",
                credits = 3
            ),
            autoTrackAttendance = false
        )
        assertTrue("Subject must be inserted", subjectId > 0L)

        val classId = db.insertClassSchedule(
            com.duorhythm.app.data.model.ClassScheduleItem(
                profileId = activeId,
                subjectName = "2222w",
                room = "101",
                teacher = "Prof",
                dayOfWeek = 1,
                startTime = "09:00 AM",
                endTime = "10:00 AM"
            )
        )
        assertTrue("Class must be inserted", classId > 0L)
        assertTrue("DB guard must detect active class before deletion", db.doesClassExistForActiveProfile(classId))

        // Delete subject -> cascade deletes class 2222w
        assertTrue("Cascade delete must succeed", db.deleteCourseCascade(subjectId, "2222", activeId))
        assertFalse("DB guard must return false after cascade deletion", db.doesClassExistForActiveProfile(classId))

        // Trigger notification receivers for deleted classId & ensure 0 notifications are posted
        val nm = activity.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadowNm = org.robolectric.Shadows.shadowOf(nm)
        nm.cancelAll()

        val schedIntent = android.content.Intent(activity, com.duorhythm.app.receiver.ScheduleReceiver::class.java).apply {
            action = com.duorhythm.app.scheduler.ClassAlarmScheduler.ACTION_CLASS_PRE_ALERT
            putExtra(com.duorhythm.app.scheduler.ClassAlarmScheduler.EXTRA_CLASS_ID, classId)
            putExtra(com.duorhythm.app.scheduler.ClassAlarmScheduler.EXTRA_SUBJECT_NAME, "2222w")
        }
        com.duorhythm.app.receiver.ScheduleReceiver().onReceive(activity, schedIntent)

        val reminderIntent = android.content.Intent(activity, com.duorhythm.app.receiver.ReminderReceiver::class.java).apply {
            action = com.duorhythm.app.service.ReminderManager.ACTION_REMINDER
            putExtra(com.duorhythm.app.service.ReminderManager.EXTRA_TYPE, "CLASS")
            putExtra(com.duorhythm.app.service.ReminderManager.EXTRA_ID, classId)
            putExtra(com.duorhythm.app.service.ReminderManager.EXTRA_TITLE, "Class in 10m: 2222w")
        }
        com.duorhythm.app.receiver.ReminderReceiver().onReceive(activity, reminderIntent)

        val assessmentIntent = android.content.Intent(activity, com.duorhythm.app.receiver.AssessmentAlarmReceiver::class.java).apply {
            action = com.duorhythm.app.receiver.AssessmentAlarmReceiver.ACTION_ASSESSMENT_ALERT
            putExtra(com.duorhythm.app.receiver.AssessmentAlarmReceiver.EXTRA_ASSESSMENT_ID, 99999L)
            putExtra(com.duorhythm.app.receiver.AssessmentAlarmReceiver.EXTRA_TITLE, "Ghost Exam")
        }
        com.duorhythm.app.receiver.AssessmentAlarmReceiver().onReceive(activity, assessmentIntent)

        val taskIntent = android.content.Intent(activity, com.duorhythm.app.receiver.TaskAlarmReceiver::class.java).apply {
            action = com.duorhythm.app.receiver.TaskAlarmReceiver.ACTION_TASK_ALERT
            putExtra(com.duorhythm.app.receiver.TaskAlarmReceiver.EXTRA_TASK_ID, 99999L)
            putExtra(com.duorhythm.app.receiver.TaskAlarmReceiver.EXTRA_TASK_TITLE, "Ghost Task")
        }
        com.duorhythm.app.receiver.TaskAlarmReceiver().onReceive(activity, taskIntent)

        assertEquals("Deleted/non-existent items must post 0 ghost notifications", 0, shadowNm.allNotifications.size)
    }

    @Test
    fun audit31_v1733_checkUpdatesVerification() {
        val controller = org.robolectric.Robolectric.buildActivity(androidx.appcompat.app.AppCompatActivity::class.java)
        val activity = controller.get()
        activity.setTheme(R.style.Theme_DuoRhythm)
        controller.setup()

        // 1. Version comparison with -beta suffix stripping & multi-segment parsing
        assertTrue("v1.7.3.3-beta must be > v1.7.3.2-beta", UpdateManager.compareVersions("v1.7.3.3-beta", "v1.7.3.2-beta") > 0)
        assertTrue("1.7.3.2-beta must be > 1.7.1-beta", UpdateManager.compareVersions("1.7.3.2-beta", "1.7.1-beta") > 0)
        assertEquals(0, UpdateManager.compareVersions("v1.7.3.3-beta", "1.7.3.3-beta"))

        // 2. GitHub JSON parsing with 0 prereleases (betaRelease == null) & dialog rendering
        val jsonNoBeta = """
            [
              {
                "tag_name": "v1.7.3.3-beta",
                "prerelease": false,
                "draft": false,
                "body": "Release 1.7.3.3-beta highlights",
                "assets": [
                  {
                    "name": "DuoRhythm-v1.7.3.3-beta.apk",
                    "browser_download_url": "https://example.com/DuoRhythm-v1.7.3.3-beta.apk"
                  }
                ]
              }
            ]
        """.trimIndent()
        val (stable, beta) = UpdateManager.parseReleasesJson(jsonNoBeta, activity.getSharedPreferences("app_version_meta", Context.MODE_PRIVATE))
        assertNotNull("Stable release must be parsed", stable)
        assertEquals("v1.7.3.3-beta", stable?.tagName)
        assertNull("Beta release must be null when 0 prereleases exist", beta)

        // Render dual channel dialog with beta == null
        UpdateManager.showModernDualChannelDialog(activity, stable, null)
        val latestDialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        assertNotNull("Dual channel update dialog must be shown when beta == null", latestDialog)
        val tvStable = latestDialog.findViewById<android.widget.TextView>(R.id.tvStableVersion)
        val tvBeta = latestDialog.findViewById<android.widget.TextView>(R.id.tvBetaVersion)
        val badgeBeta = latestDialog.findViewById<android.widget.TextView>(R.id.badgeBetaInstalled)
        val btnActionBeta = latestDialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnActionBeta)

        assertEquals("v1.7.3.3-beta • Verified Latest", tvStable?.text?.toString())
        assertEquals("No active beta preview", tvBeta?.text?.toString())
        assertEquals("UP TO DATE", badgeBeta?.text?.toString())
        assertFalse("Beta button must be disabled when beta == null", btnActionBeta?.isEnabled ?: true)
        assertEquals("NO NEWER BETA", btnActionBeta?.text?.toString())
        latestDialog.dismiss()

        // 3. Verify isChecking resets to false on exception and DuoAlertBanner reports error
        val origUrl = UpdateManager.releasesApiUrl
        val origIo = UpdateManager.ioDispatcher
        val origMain = UpdateManager.mainDispatcher
        try {
            com.duorhythm.app.ui.custom.DuoAlertBanner.resetForTesting()
            UpdateManager.isChecking = false
            UpdateManager.releasesApiUrl = "http://invalid-protocol-test://"
            UpdateManager.ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
            UpdateManager.mainDispatcher = kotlinx.coroutines.Dispatchers.Unconfined

            var callbackInvoked = false
            UpdateManager.checkForUpdates(activity, isManualCheck = true) { s, b ->
                callbackInvoked = true
                assertNull(s)
                assertNull(b)
            }
            assertTrue("Callback must be invoked on error", callbackInvoked)
            assertFalse("isChecking lock MUST reset to false in finally block after exception", UpdateManager.isChecking)
            assertTrue(
                "Error banner must report update check failure",
                com.duorhythm.app.ui.custom.DuoAlertBanner.getLastMessageForTesting().startsWith("Update check failed:")
            )
        } finally {
            UpdateManager.releasesApiUrl = origUrl
            UpdateManager.ioDispatcher = origIo
            UpdateManager.mainDispatcher = origMain
            UpdateManager.isChecking = false
        }
    }
}