package database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slotNumber: Int, // 1, 2, or 3
    val name: String,
    val email: String,
    val passwordHash: String,
    val institution: String,
    val department: String,
    val semester: String,
    val avatarUri: String? = null,
    val rememberEmail: Boolean = false
)
