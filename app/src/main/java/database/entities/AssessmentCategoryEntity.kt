package database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assessment_categories")
data class AssessmentCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long = 1,
    val name: String,
    val isDefault: Boolean = false,
    val colorHex: String = "#8B5CF6"
)
