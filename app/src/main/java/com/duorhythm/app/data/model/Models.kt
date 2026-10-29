package com.duorhythm.app.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CardItem(
    val id: Long = 0,
    val profileId: Long = 1,
    val cardName: String,
    val bankName: String,
    val cardNumber: String, // e.g. "**** 4821"
    val holderName: String,
    val cardType: String, // "VISA", "MASTERCARD", "VIRTUAL", "AMEX"
    val colorHex: String = "#1E293B",
    val balance: Double = 0.0
)

data class WalletBucketItem(
    val id: Long = 0,
    val profileId: Long = 1,
    val name: String,
    val icon: String = "wallet",
    val colorHex: String = "#0284C7",
    val connectedAccounts: String = "",
    val balance: Double = 0.0,
    val note: String = ""
)

data class SubscriptionItem(
    val id: Long = 0,
    val profileId: Long = 1,
    val serviceName: String,
    val cost: Double,
    val currency: String = "BDT",
    val billingCycle: String = "MONTHLY",
    val renewalDate: String,
    val bucketId: Long = 0,
    val bucketName: String = "",
    val category: String = "Entertainment",
    val icon: String = "subscription",
    val isActive: Boolean = true,
    val autoLog: Boolean = true
) {
    fun getMonthlyEquivalentCost(): Double {
        return if (billingCycle.equals("YEARLY", ignoreCase = true)) cost / 12.0 else cost
    }

    fun getDaysUntilRenewal(): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val parsed = sdf.parse(renewalDate) ?: return 0L
            val today = sdf.parse(sdf.format(Date())) ?: return 0L
            val diff = parsed.time - today.time
            diff / (1000 * 60 * 60 * 24)
        } catch (_: Exception) {
            0L
        }
    }
}

data class TransactionItem(
    val id: Long = 0,
    val profileId: Long = 1,
    val title: String,
    val amount: Double,
    val type: String, // "INCOME" or "EXPENSE"
    val category: String, // "Salary", "Food", "Shopping", "Bills", "Print Service", etc.
    val date: String, // "YYYY-MM-DD"
    val cardId: Long = 0,
    val currency: String = "USD",
    val notes: String = "",
    val bucketId: Long = 0,
    val balanceAfter: Double = 0.0
)

val TransactionItem.previousBalance: Double
    get() = if (type == "INCOME") balanceAfter - amount else balanceAfter + amount

typealias Record = TransactionItem
typealias TransactionRecord = TransactionItem

data class DueDebtItem(
    val id: Long = 0,
    val profileId: Long = 1,
    val personName: String,
    val phoneOrContact: String = "",
    val amount: Double,
    val dueDate: String = "", // "YYYY-MM-DD"
    val type: String, // "LENT" (I Lent / They Owe Me) or "BORROWED" (I Borrowed / I Owe)
    val status: String = "UNPAID", // "UNPAID" or "SETTLED"
    val notes: String = "",
    val createdAt: String = ""
)

enum class PersonaMode {
    STUDENT,
    WORK
}

data class TaskItem(
    val id: Long = 0,
    val profileId: Long = 1,
    val title: String,
    val description: String = "",
    val category: String = "General", // "Personal", "Work", "Study", "Shopping", "Goals"
    val dueDate: String? = null,
    val priority: String = "MEDIUM", // "HIGH", "MEDIUM", "LOW", "NONE"
    val isCompleted: Boolean = false,
    val isGoal: Boolean = false,
    val progress: Int = 0, // 0 to 100 for goals
    val startDate: String? = null,
    val persona: String = if (category.equals("Work", ignoreCase = true)) "WORK" else "STUDENT"
)

typealias TasksEntity = TaskItem

data class ClassScheduleItem(
    val id: Long = 0,
    val profileId: Long = 1,
    val subjectName: String,
    val room: String = "",
    val teacher: String = "",
    val dayOfWeek: Int = 1, // 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat, 7=Sun
    val startTime: String, // "09:00 AM"
    val endTime: String, // "10:30 AM"
    val colorHex: String = "#2563EB",
    val classType: String = "LECTURE", // "LECTURE", "LAB", "TUTORIAL", "SEMINAR"
    val buildingOrLink: String = ""
)

data class SubjectAttendanceItem(
    val id: Long = 0,
    val subjectName: String,
    val attendedClasses: Int = 0,
    val totalClasses: Int = 0
) {
    val percentage: Float
        get() = if (totalClasses > 0) (attendedClasses.toFloat() / totalClasses.toFloat()) * 100f else 0f
}

data class StudentSubject(
    val id: Long = 0,
    val profileId: Long = 1,
    val name: String,
    val code: String = "",
    val colorHex: String = "#2563EB",
    val notes: String = "",
    val credits: Int = 3,
    val instructorName: String = "",
    val instructorEmail: String = "",
    val instructorPhone: String = "",
    val syllabusText: String = "",
    val syllabusPath: String = "",
    val room: String = ""
) {
    val teacher: String get() = instructorName
    val instructor: String get() = instructorName
    val teacherName: String get() = instructorName
}

typealias SubjectEntity = StudentSubject
typealias Course = StudentSubject
typealias CourseDocument = SubjectMaterial

data class SubjectMaterial(
    val id: Long = 0,
    val subjectId: Long,
    val title: String,
    val type: String = "PDF_DOCUMENT", // "PDF_DOCUMENT", "IMAGE_NOTE", "SYLLABUS", "LECTURE_SLIDE"
    val filePath: String,
    val createdAt: String = "",
    val notes: String = ""
)

data class CategoryExpense(
    val category: String,
    val amount: Double,
    val percentage: Float,
    val colorHex: String
)

data class SubjectWorkItem(
    val id: Long = 0,
    val profileId: Long = 1,
    val subjectId: Long,
    val itemType: String, // "HOMEWORK", "PRACTICAL", "EXAM", "ASSIGNMENT"
    val title: String,
    val description: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val scoreOrMarks: String = "",
    val status: String = "PENDING", // "PENDING", "DONE"
    val attachmentPath: String = "",
    val examType: String = "", // "SEMESTER_FINAL", "MIDTERM", "CLASS_TEST_QUIZ"
    val attachmentUri: String? = null,
    val attachmentName: String? = null
) {
    val room: String get() = attachmentPath
    fun getDaysRemaining(): Long {
        return try {
            val dateStr = endDate.ifBlank { startDate }
            if (dateStr.isBlank()) return 0L
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val target = sdf.parse(dateStr.trim()) ?: return 0L
            val today = sdf.parse(sdf.format(Date())) ?: return 0L
            val diff = target.time - today.time
            diff / (1000 * 60 * 60 * 24)
        } catch (_: Exception) {
            0L
        }
    }
}

data class WorkOrder(
    val id: Long = 0,
    val title: String, // e.g. "Print 10 Pages Color"
    val clientName: String = "",
    val quantity: Int = 1,
    val unitPrice: Double = 0.0,
    val totalPrice: Double = 0.0,
    val startDate: String = "",
    val endDate: String = "",
    val status: String = "PENDING", // "PENDING", "IN_PROGRESS", "COMPLETED"
    val notes: String = "",
    val attachmentPath: String = ""
)

data class NoteItem(
    val id: Long = 0,
    val profileId: Long = 1,
    val title: String,
    val content: String,
    val category: String = "General",
    val colorHex: String = "#2563EB",
    val updatedAt: String = ""
)

data class ContactGroupItem(
    val id: Long = 0,
    val name: String,
    val type: String = "CONTACT", // "CONTACT" or "GROUP"
    val platform: String = "PHONE", // "PHONE", "WHATSAPP", "TELEGRAM", "DISCORD", "MESSENGER"
    val targetLink: String = "",
    val notes: String = "",
    val phoneNumber: String = "",
    val role: String = "Classmate", // "Teacher", "CR", "Classmate", "Client", "Partner"
    val scope: String = "SHARED" // "STUDENT_ONLY", "WORK_ONLY", "SHARED"
)

data class CurrencyRate(
    val code: String, // "USD", "EUR", "GBP", "JPY", "INR", "CAD", "AUD"
    val rate: Double, // Relative to USD
    val symbol: String,
    val lastUpdated: String
)

typealias ExamEntity = SubjectWorkItem
typealias QuizEntity = SubjectWorkItem
typealias Contact = ContactGroupItem

enum class ClassStatus {
    IN_SESSION_NOW,
    UPCOMING_NEXT,
    COMPLETED
}

data class CarouselClassItem(
    val classItem: ClassScheduleItem?,
    val status: ClassStatus,
    val totalCount: Int = 1,
    val positionIndex: Int = 0,
    val isPlaceholder: Boolean = false
)

