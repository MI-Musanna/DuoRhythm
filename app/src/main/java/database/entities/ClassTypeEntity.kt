package database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "class_types")
data class ClassTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long = 1,
    val name: String,
    val isDefault: Boolean = false
)
