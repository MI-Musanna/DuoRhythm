package com.duorhythm.app.data.model

data class TransactionCategoryItem(
    val id: Long = 0,
    val name: String,
    val type: String = "BOTH", // "EXPENSE", "INCOME", or "BOTH"
    val isDefault: Boolean = false,
    val colorHex: String = "#0284C7",
    val icon: String = "category"
)

typealias CategoryEntity = database.entities.CategoryEntity
