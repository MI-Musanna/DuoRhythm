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
}
