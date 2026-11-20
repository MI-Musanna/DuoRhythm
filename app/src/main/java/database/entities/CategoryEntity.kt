package database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.duorhythm.app.data.model.TransactionCategoryItem

@Entity(tableName = "transaction_categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: String = "BOTH", // "EXPENSE", "INCOME", or "BOTH"
    @ColumnInfo(name = "is_default")
    val is_default: Int = 0,
    @ColumnInfo(name = "color_hex")
    val color_hex: String = "#0284C7",
    val icon: String = "category"
) {
    val isDefault: Boolean get() = is_default == 1
    val colorHex: String get() = color_hex

    fun toItem(): TransactionCategoryItem = TransactionCategoryItem(
        id = id,
        name = name,
        type = type,
        isDefault = is_default == 1,
        colorHex = color_hex,
        icon = icon
    )

    companion object {
        fun fromItem(item: TransactionCategoryItem): CategoryEntity = CategoryEntity(
            id = item.id,
            name = item.name,
            type = item.type,
            is_default = if (item.isDefault) 1 else 0,
            color_hex = item.colorHex,
            icon = item.icon
        )
    }
}
