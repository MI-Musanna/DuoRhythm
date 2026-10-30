package database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "profile_id") val profileId: Long = 1,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "amount") val amount: Double,
    @ColumnInfo(name = "type") val type: String = "EXPENSE",
    @ColumnInfo(name = "category") val category: String = "General",
    @ColumnInfo(name = "date") val date: String = "",
    @ColumnInfo(name = "card_id") val cardId: Long? = null,
    @ColumnInfo(name = "currency") val currency: String = "USD",
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "bucket_id") val bucketId: Long = 0
)
