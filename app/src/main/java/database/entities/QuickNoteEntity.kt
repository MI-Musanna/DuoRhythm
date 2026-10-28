package database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.duorhythm.app.data.model.NoteItem

@Entity(tableName = "notes")
data class QuickNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long = 1,
    val title: String,
    val content: String,
    val category: String = "General",
    val colorHex: String = "#1E293B",
    val updatedAt: String = ""
) {
    fun toNoteItem(): NoteItem = NoteItem(
        id = id,
        profileId = profileId,
        title = title,
        content = content,
        category = category,
        colorHex = colorHex,
        updatedAt = updatedAt
    )

    companion object {
        fun fromNoteItem(item: NoteItem): QuickNoteEntity = QuickNoteEntity(
            id = item.id,
            profileId = item.profileId,
            title = item.title,
            content = item.content,
            category = item.category,
            colorHex = item.colorHex,
            updatedAt = item.updatedAt
        )
    }
}
