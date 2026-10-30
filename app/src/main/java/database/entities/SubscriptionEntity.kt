package database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "profile_id", defaultValue = "1") val profileId: Long = 1L,
    @ColumnInfo(name = "name") val name: String = "",
    @ColumnInfo(name = "amount") val amount: Double = 0.0,
    @ColumnInfo(name = "isRange", defaultValue = "0") val isRange: Boolean = false, // false = Fixed Day, true = Date Window Range
    @ColumnInfo(name = "startDay", defaultValue = "1") val startDay: Int = 1,        // e.g. 1st
    @ColumnInfo(name = "endDay", defaultValue = "8") val endDay: Int = 8,          // e.g. 8th
    @ColumnInfo(name = "dueDay", defaultValue = "5") val dueDay: Int = 5,          // used if isRange == false
    @ColumnInfo(name = "linkedAccountId") val linkedAccountId: Long? = null, // NULL = "None / External (Father / Third-Party - No Wallet Deduction)"
    @ColumnInfo(name = "lastPaidMonthYear") val lastPaidMonthYear: String? = null // e.g. "2026-10"
) {
    @Ignore
    var accountName: String = ""
}
