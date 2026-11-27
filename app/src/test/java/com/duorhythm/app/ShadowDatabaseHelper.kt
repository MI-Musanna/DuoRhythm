package com.duorhythm.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.ClassScheduleItem
import com.duorhythm.app.data.model.ClientOrder
import com.duorhythm.app.data.model.ContactGroupItem
import com.duorhythm.app.data.model.DueDebtItem
import com.duorhythm.app.data.model.StudentSubject
import com.duorhythm.app.data.model.TaskItem
import com.duorhythm.app.data.model.TransactionItem
import com.duorhythm.app.data.model.WalletBucketItem
import com.duorhythm.app.data.model.WorkOrder
import database.entities.AssessmentEntity
import database.entities.ProfileEntity
import database.entities.SubscriptionEntity
import org.json.JSONArray
import org.json.JSONObject
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Implements(DatabaseHelper::class)
class ShadowDatabaseHelper {

    @RealObject
    private lateinit var realDbHelper: DatabaseHelper

    companion object {
        private val buckets = mutableMapOf<Long, WalletBucketItem>()
        private val profiles = mutableListOf<ProfileEntity>()
        private val tasks = mutableListOf<TaskItem>()
        private val assessments = mutableListOf<AssessmentEntity>()
        private val taskCategories = mutableListOf<String>()
        private val holidays = mutableMapOf<String, String>()
        private val studentSubjects = mutableListOf<StudentSubject>()
        private val classes = mutableListOf<ClassScheduleItem>()
        private val contactsGroups = mutableListOf<ContactGroupItem>()
        private val workOrders = mutableListOf<WorkOrder>()
        private val transactionsList = mutableListOf<TransactionItem>()
        private val subscriptionsList = mutableListOf<SubscriptionEntity>()
        private val duesDebtsList = mutableListOf<DueDebtItem>()
        private val genericTables = mutableMapOf<String, MutableList<Map<String, Any?>>>()
        private var nextBucketId = 1L
        private var nextTxId = 1L
        private var nextProfileId = 1L
        private var nextTaskId = 1L
        private var nextAssessmentId = 1L
        private var nextSubjectId = 1L
        private var nextClassId = 1L
        private var nextContactGroupId = 1L
        private var nextWorkOrderId = 1L
        private var nextSubId = 1L
        private var nextDueId = 1L

        fun reset() {
            buckets.clear()
            profiles.clear()
            tasks.clear()
            assessments.clear()
            taskCategories.clear()
            holidays.clear()
            studentSubjects.clear()
            classes.clear()
            contactsGroups.clear()
            workOrders.clear()
            transactionsList.clear()
            subscriptionsList.clear()
            duesDebtsList.clear()
            genericTables.clear()
            nextBucketId = 1L
            nextTxId = 1L
            nextProfileId = 1L
            nextTaskId = 1L
            nextAssessmentId = 1L
            nextSubjectId = 1L
            nextClassId = 1L
            nextContactGroupId = 1L
            nextWorkOrderId = 1L
            nextSubId = 1L
            nextDueId = 1L
        }
    }

    @Implementation
    fun insertWalletBucket(bucket: WalletBucketItem): Long {
        val id = nextBucketId++
        buckets[id] = bucket.copy(id = id)
        if (::realDbHelper.isInitialized) {
            realDbHelper.walletUpdateFlow.tryEmit(Unit)
            realDbHelper.transactionUpdateFlow.tryEmit(Unit)
        }
        return id
    }

    @Implementation
    fun getAllWalletBuckets(profileId: Long = 1L): List<WalletBucketItem> {
        return buckets.values.filter { it.profileId == profileId }.sortedBy { it.id }
    }

    @Implementation
    fun getWalletBucketById(id: Long): WalletBucketItem? {
        return buckets[id]
    }

    @Implementation
    fun insertTransaction(tx: TransactionItem): Long {
        val id = nextTxId++
        transactionsList.add(tx.copy(id = id))
        val delta = if (tx.type == "INCOME") tx.amount else -tx.amount
        val targetId = tx.bucketId
        val bucket = buckets[targetId]
        if (bucket != null) {
            buckets[targetId] = bucket.copy(balance = bucket.balance + delta)
        }
        if (::realDbHelper.isInitialized) {
            realDbHelper.transactionUpdateFlow.tryEmit(Unit)
            realDbHelper.walletUpdateFlow.tryEmit(Unit)
        }
        return id
    }

    @Implementation
    fun getAllTransactions(
        typeFilter: String? = null,
        cardIdFilter: Long? = null,
        bucketIdFilter: Long? = null,
        profileId: Long = 1L
    ): List<TransactionItem> {
        return transactionsList.filter { tx ->
            tx.profileId == profileId &&
                (typeFilter.isNullOrEmpty() || typeFilter == "ALL" || tx.type == typeFilter) &&
                (cardIdFilter == null || cardIdFilter <= 0L || tx.cardId == cardIdFilter) &&
                (bucketIdFilter == null || bucketIdFilter <= 0L || tx.bucketId == bucketIdFilter)
        }
    }

    @Implementation
    fun clearAllTransactions(profileId: Long = 1L): Int {
        val before = transactionsList.size
        transactionsList.removeAll { it.profileId == profileId }
        val removed = before - transactionsList.size
        if (::realDbHelper.isInitialized) {
            realDbHelper.transactionUpdateFlow.tryEmit(Unit)
            realDbHelper.walletUpdateFlow.tryEmit(Unit)
        }
        return removed
    }

    @Implementation
    fun insertSubscription(sub: SubscriptionEntity): Long {
        val id = if (sub.id > 0L) sub.id else nextSubId++
        val pId = if (sub.profileId > 0L) sub.profileId else 1L
        subscriptionsList.removeAll { it.id == id }
        subscriptionsList.add(sub.copy(id = id, profileId = pId))
        if (::realDbHelper.isInitialized) realDbHelper.subscriptionUpdateFlow.tryEmit(Unit)
        return id
    }

    @Implementation
    fun getSubscriptions(profileId: Long): List<SubscriptionEntity> {
        return subscriptionsList.filter { it.profileId == profileId }.sortedByDescending { it.id }
    }

    @Implementation
    fun insertDueDebt(item: DueDebtItem): Long {
        val id = if (item.id > 0L) item.id else nextDueId++
        val pId = if (item.profileId > 0L) item.profileId else 1L
        duesDebtsList.removeAll { it.id == id }
        duesDebtsList.add(item.copy(id = id, profileId = pId))
        if (::realDbHelper.isInitialized) realDbHelper.duesUpdateFlow.tryEmit(Unit)
        return id
    }

    @Implementation
    fun getAllDuesDebts(profileId: Long = 1L): List<DueDebtItem> {
        return duesDebtsList.filter { it.profileId == profileId }
    }

    @Implementation
    fun getDuesDebtsSummary(profileId: Long = 1L): Pair<Double, Double> {
        val profileDues = duesDebtsList.filter { it.profileId == profileId && it.status == "UNPAID" }
        val lent = profileDues.filter { it.type == "LENT" }.sumOf { it.amount }
        val borrowed = profileDues.filter { it.type == "BORROWED" }.sumOf { it.amount }
        return Pair(lent, borrowed)
    }

    @Implementation
    fun executeTransfer(
        fromBucketId: Long,
        toBucketId: Long,
        amount: Double,
        date: String,
        currency: String,
        note: String,
        title: String
    ): Boolean {
        if (fromBucketId <= 0L || toBucketId <= 0L || fromBucketId == toBucketId || amount <= 0.0) {
            return false
        }
        val fromBucket = buckets[fromBucketId] ?: return false
        val toBucket = buckets[toBucketId] ?: return false
        if (fromBucket.profileId != toBucket.profileId) return false

        buckets[fromBucketId] = fromBucket.copy(balance = fromBucket.balance - amount)
        buckets[toBucketId] = toBucket.copy(balance = toBucket.balance + amount)
        if (::realDbHelper.isInitialized) {
            realDbHelper.walletUpdateFlow.tryEmit(Unit)
            realDbHelper.transactionUpdateFlow.tryEmit(Unit)
        }
        return true
    }

    @Implementation
    fun insertProfile(profile: ProfileEntity): Long {
        val id = if (profile.id > 0) profile.id else nextProfileId++
        profiles.removeAll { it.slotNumber == profile.slotNumber || (profile.id > 0 && it.id == profile.id) }
        profiles.add(profile.copy(id = id))
        return id
    }

    @Implementation
    fun getAllProfiles(): List<ProfileEntity> {
        return profiles.sortedBy { it.slotNumber }
    }

    @Implementation
    fun getProfileById(id: Long): ProfileEntity? {
        return profiles.find { it.id == id }
    }

    @Implementation
    fun getProfileBySlot(slotNumber: Int): ProfileEntity? {
        return profiles.find { it.slotNumber == slotNumber }
    }

    @Implementation
    fun updateProfile(profile: ProfileEntity) {
        val idx = profiles.indexOfFirst { it.id == profile.id || it.slotNumber == profile.slotNumber }
        if (idx >= 0) {
            profiles[idx] = profile
        } else {
            profiles.add(profile)
        }
    }

    @Implementation
    fun deleteProfile(profile: ProfileEntity) {
        profiles.removeAll { it.id == profile.id }
    }

    @Implementation
    fun insertTask(task: TaskItem): Long {
        val id = if (task.id > 0) task.id else nextTaskId++
        tasks.add(task.copy(id = id))
        if (::realDbHelper.isInitialized) realDbHelper.taskUpdateFlow.tryEmit(Unit)
        return id
    }

    @Implementation
    fun updateTask(task: TaskItem): Int {
        val idx = tasks.indexOfFirst { it.id == task.id }
        if (idx >= 0) {
            tasks[idx] = task
            if (::realDbHelper.isInitialized) realDbHelper.taskUpdateFlow.tryEmit(Unit)
            return 1
        }
        return 0
    }

    @Implementation
    fun updateTaskCompletion(taskId: Long, isCompleted: Boolean) {
        val idx = tasks.indexOfFirst { it.id == taskId }
        if (idx >= 0) {
            val cur = tasks[idx]
            tasks[idx] = cur.copy(isCompleted = isCompleted, progress = if (isCompleted) 100 else 0)
            if (::realDbHelper.isInitialized) realDbHelper.taskUpdateFlow.tryEmit(Unit)
        }
    }

    @Implementation
    fun updateTaskProgress(taskId: Long, progress: Int) {
        val idx = tasks.indexOfFirst { it.id == taskId }
        if (idx >= 0) {
            val cur = tasks[idx]
            val clamped = progress.coerceIn(0, 100)
            tasks[idx] = cur.copy(progress = clamped, isCompleted = clamped >= 100)
            if (::realDbHelper.isInitialized) realDbHelper.taskUpdateFlow.tryEmit(Unit)
        }
    }

    @Implementation
    fun deleteTask(taskId: Long): Int {
        val before = tasks.size
        tasks.removeAll { it.id == taskId }
        val deleted = if (tasks.size < before) 1 else 0
        if (deleted > 0 && ::realDbHelper.isInitialized) realDbHelper.taskUpdateFlow.tryEmit(Unit)
        return deleted
    }

    @Implementation
    fun getAllTasks(filter: String = "ALL", isGoal: Boolean? = null, profileId: Long = 1L): List<TaskItem> {
        return tasks.filter { task ->
            val matchesGoal = (isGoal == null || task.isGoal == isGoal)
            val matchesProfile = (profileId <= 0L || task.profileId == profileId || task.profileId == 0L)
            matchesGoal && matchesProfile
        }
    }

    @Implementation
    fun insertAssessment(entity: AssessmentEntity): Long {
        val id = if (entity.id > 0) entity.id else nextAssessmentId++
        val pId = if (entity.profileId > 0) entity.profileId else 1L
        assessments.add(entity.copy(id = id, profileId = pId))
        if (::realDbHelper.isInitialized) realDbHelper.assessmentUpdateFlow.tryEmit(Unit)
        return id
    }

    @Implementation
    fun getAllAssessments(): List<AssessmentEntity> {
        return assessments.toList()
    }

    @Implementation
    fun getAssessments(profileId: Long): List<AssessmentEntity> {
        return assessments.filter { it.profileId == profileId }
    }

    @Implementation
    fun addTaskCategory(category: String): Boolean {
        if (!taskCategories.contains(category)) {
            taskCategories.add(category)
        }
        return true
    }

    @Implementation
    fun getTaskCategories(): List<String> {
        return taskCategories.toList()
    }

    @Implementation
    fun setHoliday(date: String, title: String = "Holiday", profileId: Long = 1L) {
        holidays[date] = title
    }

    @Implementation
    fun isHoliday(date: String, profileId: Long = 1L): Boolean {
        return holidays.containsKey(date)
    }

    @Implementation
    fun clearAllUserData() {
        buckets.clear()
        profiles.clear()
        tasks.clear()
        assessments.clear()
        taskCategories.clear()
        holidays.clear()
        studentSubjects.clear()
        classes.clear()
        genericTables.clear()
        val context: Context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    @Implementation
    fun insertStudentSubject(subject: StudentSubject, autoTrackAttendance: Boolean): Long {
        val id = if (subject.id > 0) subject.id else nextSubjectId++
        val pId = if (subject.profileId > 0) subject.profileId else 1L
        studentSubjects.add(subject.copy(id = id, profileId = pId))
        if (::realDbHelper.isInitialized) realDbHelper.courseUpdateFlow.tryEmit(Unit)
        return id
    }

    @Implementation
    fun getAllStudentSubjects(profileId: Long): List<StudentSubject> {
        return studentSubjects.filter { it.profileId == profileId }
    }

    @Implementation
    fun getStudentSubjectById(subjectId: Long): StudentSubject? {
        return studentSubjects.firstOrNull { it.id == subjectId }
    }

    @Implementation
    fun insertContactGroup(item: ContactGroupItem): Long {
        val id = if (item.id > 0) item.id else nextContactGroupId++
        val pId = if (item.profileId > 0) item.profileId else 1L
        contactsGroups.add(item.copy(id = id, profileId = pId))
        if (::realDbHelper.isInitialized) realDbHelper.directoryUpdateFlow.tryEmit(Unit)
        return id
    }

    @Implementation
    fun getAllContactsGroups(profileId: Long): List<ContactGroupItem> {
        return contactsGroups.filter { it.profileId == profileId }
    }

    @Implementation
    fun getContactsForPortal(isStudent: Boolean, roleFilter: String?, profileId: Long): List<ContactGroupItem> {
        return contactsGroups.filter { item ->
            val scopeOk = if (isStudent) {
                item.scope == "STUDENT_ONLY" || item.scope == "SHARED" || item.scope.isEmpty()
            } else {
                item.scope == "WORK_ONLY" || item.scope == "SHARED" || item.scope.isEmpty()
            }
            val roleOk = roleFilter.isNullOrBlank() || roleFilter == "ALL" || item.role == roleFilter
            item.profileId == profileId && scopeOk && roleOk
        }
    }

    @Implementation
    fun insertWorkOrder(order: WorkOrder): Long {
        val id = if (order.id > 0) order.id else nextWorkOrderId++
        val pId = if (order.profileId > 0) order.profileId else 1L
        workOrders.add(order.copy(id = id, profileId = pId))
        if (::realDbHelper.isInitialized) realDbHelper.workOrderUpdateFlow.tryEmit(Unit)
        return id
    }

    @Implementation
    fun insertClientOrder(order: ClientOrder): Long {
        return insertWorkOrder(
            WorkOrder(
                id = order.id,
                profileId = if (order.profileId > 0) order.profileId else 1L,
                title = order.title,
                clientName = order.clientName,
                totalPrice = order.amount.toDoubleOrNull() ?: 0.0,
                startDate = order.startDate,
                endDate = order.dueDate,
                status = order.status,
                notes = order.notes
            )
        )
    }

    @Implementation
    fun getAllWorkOrders(profileId: Long): List<WorkOrder> {
        return workOrders.filter { it.profileId == profileId }
    }

    @Implementation
    fun getClientOrders(profileId: Long): List<ClientOrder> {
        return getAllWorkOrders(profileId).map { wo ->
            ClientOrder(
                id = wo.id,
                title = wo.title,
                clientName = wo.clientName,
                amount = String.format(Locale.US, "%.2f", wo.totalPrice),
                status = wo.status,
                dueDate = wo.endDate,
                notes = wo.notes,
                profileId = wo.profileId
            )
        }
    }

    @Implementation
    fun deleteByProfileId(profileId: Long) {
        contactsGroups.removeAll { it.profileId == profileId }
        workOrders.removeAll { it.profileId == profileId }
        studentSubjects.removeAll { it.profileId == profileId }
        assessments.removeAll { it.profileId == profileId }
        tasks.removeAll { it.profileId == profileId }
        classes.removeAll { it.profileId == profileId }
        buckets.entries.removeAll { it.value.profileId == profileId }
        transactionsList.removeAll { it.profileId == profileId }
        subscriptionsList.removeAll { it.profileId == profileId }
        duesDebtsList.removeAll { it.profileId == profileId }
        if (::realDbHelper.isInitialized) {
            realDbHelper.courseUpdateFlow.tryEmit(Unit)
            realDbHelper.assessmentUpdateFlow.tryEmit(Unit)
            realDbHelper.directoryUpdateFlow.tryEmit(Unit)
            realDbHelper.workOrderUpdateFlow.tryEmit(Unit)
            realDbHelper.taskUpdateFlow.tryEmit(Unit)
            realDbHelper.timetableUpdateFlow.tryEmit(Unit)
            realDbHelper.walletUpdateFlow.tryEmit(Unit)
            realDbHelper.transactionUpdateFlow.tryEmit(Unit)
            realDbHelper.subscriptionUpdateFlow.tryEmit(Unit)
            realDbHelper.duesUpdateFlow.tryEmit(Unit)
        }
    }

    @Implementation
    fun insertClassSchedule(item: ClassScheduleItem): Long {
        val id = if (item.id > 0) item.id else nextClassId++
        classes.add(item.copy(id = id))
        if (::realDbHelper.isInitialized) realDbHelper.timetableUpdateFlow.tryEmit(Unit)
        return id
    }

    @Implementation
    fun updateClassSchedule(item: ClassScheduleItem): Int {
        val idx = classes.indexOfFirst { it.id == item.id }
        if (idx >= 0) {
            classes[idx] = item
            if (::realDbHelper.isInitialized) realDbHelper.timetableUpdateFlow.tryEmit(Unit)
            return 1
        }
        return 0
    }

    @Implementation
    fun getScheduleForDay(dayOfWeek: Int, profileId: Long = 1L): List<ClassScheduleItem> {
        return classes.filter {
            it.dayOfWeek == dayOfWeek && (profileId <= 0L || it.profileId == profileId || it.profileId == 0L)
        }
    }

    @Implementation
    fun getAllClasses(profileId: Long): List<ClassScheduleItem> {
        return classes.filter { it.profileId == profileId || it.profileId == 0L }
    }

    @Implementation
    fun deleteClassSchedule(id: Long): Int {
        val before = classes.size
        classes.removeAll { it.id == id }
        val deleted = if (classes.size < before) 1 else 0
        if (deleted > 0 && ::realDbHelper.isInitialized) realDbHelper.timetableUpdateFlow.tryEmit(Unit)
        return deleted
    }

    @Implementation
    fun deleteCourseCascade(courseId: Long, courseCode: String, profileId: Long): Boolean {
        val course = studentSubjects.firstOrNull { it.id == courseId }
        val courseName = course?.name?.trim() ?: ""
        val code = (if (courseCode.isNotBlank()) courseCode else (course?.code ?: "")).trim()
        classes.removeAll { cls ->
            val clsSub = cls.subjectName.trim()
            (cls.profileId == profileId || cls.profileId == 0L) && (
                (courseName.isNotBlank() && (clsSub.equals(courseName, ignoreCase = true) || clsSub.startsWith("$courseName (", ignoreCase = true))) ||
                    (code.isNotBlank() && clsSub.contains(code, ignoreCase = true))
            )
        }
        assessments.removeAll { it.subjectId == courseId }
        studentSubjects.removeAll { it.id == courseId }
        if (::realDbHelper.isInitialized) realDbHelper.timetableUpdateFlow.tryEmit(Unit)
        return true
    }

    @Implementation
    fun doesClassExistForActiveProfile(classId: Long, profileId: Long): Boolean {
        return classes.any { it.id == classId && (it.profileId == profileId || it.profileId == 0L) }
    }

    @Implementation
    fun doesAssessmentExistForActiveProfile(assessmentId: Long, profileId: Long): Boolean {
        return assessments.any { it.id == assessmentId && (it.profileId == profileId || it.profileId == 0L) && it.status != "DONE" }
    }

    @Implementation
    fun doesTaskExistForActiveProfile(taskId: Long, profileId: Long): Boolean {
        return tasks.any { it.id == taskId && (it.profileId == profileId || it.profileId == 0L) && !it.isCompleted }
    }

    @Implementation
    fun doesSubscriptionExistForActiveProfile(subId: Long, profileId: Long): Boolean = false

    @Implementation
    fun doesDueDebtExistForActiveProfile(debtId: Long, profileId: Long): Boolean = false

    @Implementation
    fun doesWorkOrderExist(orderId: Long): Boolean = false

    @Implementation
    fun exportDatabaseToJson(): String {
        val root = JSONObject()
        val context: Context = ApplicationProvider.getApplicationContext()

        // Profiles
        val profilesArr = JSONArray()
        for (p in profiles) {
            val obj = JSONObject().apply {
                put("id", p.id)
                put("slotNumber", p.slotNumber)
                put("name", p.name)
                put("email", p.email)
                put("passwordHash", p.passwordHash)
                put("institution", p.institution)
                put("department", p.department)
                put("semester", p.semester)
                put("avatarUri", p.avatarUri ?: JSONObject.NULL)
                put("rememberEmail", if (p.rememberEmail) 1 else 0)
            }
            profilesArr.put(obj)
        }
        root.put("profiles", profilesArr)

        // Tasks
        val tasksArr = JSONArray()
        for (t in tasks) {
            val obj = JSONObject().apply {
                put("id", t.id)
                put("profile_id", t.profileId)
                put("title", t.title)
                put("description", t.description)
                put("category", t.category)
                put("due_date", t.dueDate ?: "")
                put("priority", t.priority)
                put("is_completed", if (t.isCompleted) 1 else 0)
                put("is_goal", if (t.isGoal) 1 else 0)
                put("progress", t.progress)
            }
            tasksArr.put(obj)
        }
        root.put("tasks", tasksArr)

        // Assessments
        val assessmentsArr = JSONArray()
        for (a in assessments) {
            val obj = JSONObject().apply {
                put("id", a.id)
                put("profile_id", a.profileId)
                put("subject_id", a.subjectId)
                put("subject_name", a.subjectName)
                put("course_code", a.courseCode)
                put("type", a.type)
                put("title", a.title)
                put("date", a.date)
                put("time", a.time)
                put("room", a.room)
                put("syllabus", a.syllabus)
                put("teacher", a.teacher)
                put("status", a.status)
            }
            assessmentsArr.put(obj)
        }
        root.put("assessments", assessmentsArr)

        // Task categories
        val taskCatArr = JSONArray()
        for (c in taskCategories) {
            taskCatArr.put(JSONObject().apply { put("name", c) })
        }
        root.put("task_categories", taskCatArr)

        // Holidays
        val holidayArr = JSONArray()
        for ((d, t) in holidays) {
            holidayArr.put(JSONObject().apply {
                put("date", d)
                put("title", t)
                put("profile_id", 1)
            })
        }
        root.put("holiday_records", holidayArr)

        // Ensure all other tables present in schema are represented
        val otherTables = listOf(
            "app_settings", "cards", "accounts", "transactions", "transaction_categories", "wallet_buckets",
            "subscriptions", "currency_rates", "classes", "attendance", "attendance_records", "student_subjects",
            "subject_materials", "subject_work_items", "work_orders", "notes", "contacts_groups",
            "dues_debts", "class_types", "assessment_categories"
        )
        for (table in otherTables) {
            val rows = genericTables[table] ?: emptyList()
            val arr = JSONArray()
            for (row in rows) {
                arr.put(JSONObject(row))
            }
            root.put(table, arr)
        }

        // Export duorhythm_prefs
        val prefs = context.getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE)
        val prefsObj = JSONObject()
        for ((key, value) in prefs.all) {
            when (value) {
                is Boolean -> prefsObj.put(key, value)
                is Int -> prefsObj.put(key, value)
                is Long -> prefsObj.put(key, value)
                is Float -> prefsObj.put(key, value.toDouble())
                is Double -> prefsObj.put(key, value)
                is String -> prefsObj.put(key, value)
            }
        }
        if (!prefsObj.has("exam_reminder_lead_minutes")) {
            prefsObj.put("exam_reminder_lead_minutes", com.duorhythm.app.scheduler.AssessmentAlarmScheduler.getExamReminderLeadMinutes(context))
        }
        val defaultExamTime = com.duorhythm.app.scheduler.AssessmentAlarmScheduler.getDefaultExamReminderTime(context)
        if (defaultExamTime != null) {
            prefsObj.put("pref_default_exam_hour", defaultExamTime.first)
            prefsObj.put("pref_default_exam_minute", defaultExamTime.second)
        } else {
            prefsObj.put("pref_default_exam_hour", -1)
        }
        root.put("settings_preferences", prefsObj)

        root.put("profile_avatars", JSONObject())
        root.put("exported_at", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
        return root.toString(2)
    }

    @Implementation
    fun exportFullDatabaseAsJson(): String = exportDatabaseToJson()

    @Implementation
    fun importFullDatabaseFromJson(jsonString: String): Boolean = importDatabaseFromJson(jsonString)

    @Implementation
    fun importDatabaseFromJson(jsonString: String): Boolean {
        return try {
            val root = JSONObject(jsonString)
            val context: Context = ApplicationProvider.getApplicationContext()

            // Profiles
            if (root.has("profiles")) {
                profiles.clear()
                val arr = root.getJSONArray("profiles")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    profiles.add(
                        ProfileEntity(
                            id = obj.optLong("id", 0L),
                            slotNumber = obj.optInt("slotNumber", 1),
                            name = obj.optString("name", ""),
                            email = obj.optString("email", ""),
                            passwordHash = obj.optString("passwordHash", ""),
                            institution = obj.optString("institution", ""),
                            department = obj.optString("department", ""),
                            semester = obj.optString("semester", ""),
                            avatarUri = if (obj.isNull("avatarUri")) null else obj.optString("avatarUri"),
                            rememberEmail = obj.optInt("rememberEmail", 1) == 1
                        )
                    )
                }
            }

            // Tasks
            if (root.has("tasks")) {
                tasks.clear()
                val arr = root.getJSONArray("tasks")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    tasks.add(
                        TaskItem(
                            id = obj.optLong("id", 0L),
                            profileId = obj.optLong("profile_id", 1L),
                            title = obj.optString("title", ""),
                            description = obj.optString("description", ""),
                            category = obj.optString("category", "General"),
                            dueDate = if (obj.isNull("due_date")) null else obj.optString("due_date"),
                            priority = obj.optString("priority", "MEDIUM"),
                            isCompleted = obj.optInt("is_completed", 0) == 1,
                            isGoal = obj.optInt("is_goal", 0) == 1,
                            progress = obj.optInt("progress", 0)
                        )
                    )
                }
            }

            // Assessments
            if (root.has("assessments")) {
                assessments.clear()
                val arr = root.getJSONArray("assessments")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    assessments.add(
                        AssessmentEntity(
                            id = obj.optLong("id", 0L),
                            profileId = obj.optLong("profile_id", 1L),
                            subjectId = obj.optLong("subject_id", 0L),
                            subjectName = obj.optString("subject_name", ""),
                            courseCode = obj.optString("course_code", ""),
                            type = obj.optString("type", ""),
                            title = obj.optString("title", ""),
                            date = obj.optString("date", ""),
                            time = obj.optString("time", ""),
                            room = obj.optString("room", ""),
                            syllabus = obj.optString("syllabus", ""),
                            teacher = obj.optString("teacher", ""),
                            status = obj.optString("status", "PENDING")
                        )
                    )
                }
            }

            // Task categories
            if (root.has("task_categories")) {
                taskCategories.clear()
                val arr = root.getJSONArray("task_categories")
                for (i in 0 until arr.length()) {
                    taskCategories.add(arr.getJSONObject(i).optString("name", ""))
                }
            }

            // Holidays
            if (root.has("holiday_records")) {
                holidays.clear()
                val arr = root.getJSONArray("holiday_records")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    holidays[obj.optString("date")] = obj.optString("title", "Holiday")
                }
            }

            // Restore settings_preferences
            if (root.has("settings_preferences")) {
                val prefsObj = root.optJSONObject("settings_preferences")
                if (prefsObj != null) {
                    val editor = context.getSharedPreferences("duorhythm_prefs", Context.MODE_PRIVATE).edit()
                    val keys = prefsObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = prefsObj.get(key)
                        when (value) {
                            is Boolean -> editor.putBoolean(key, value)
                            is Int -> editor.putInt(key, value)
                            is Long -> editor.putLong(key, value)
                            is Double -> editor.putFloat(key, value.toFloat())
                            is Float -> editor.putFloat(key, value)
                            is String -> editor.putString(key, value)
                        }
                    }
                    editor.apply()
                }
            }

            DatabaseHelper.reactivateAllAlarms(context)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
