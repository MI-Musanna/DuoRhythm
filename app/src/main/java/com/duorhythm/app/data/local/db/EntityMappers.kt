package com.duorhythm.app.data.local.db

import android.database.Cursor

fun Cursor.getColumnIndexSafe(columnName: String): Int {
    val idx = this.getColumnIndex(columnName)
    return if (idx >= 0) idx else -1
}

fun Cursor.safeGetLong(columnName1: String, columnName2: String? = null, default: Long = 0L): Long {
    var idx = this.getColumnIndex(columnName1)
    if (idx == -1 && columnName2 != null) {
        idx = this.getColumnIndex(columnName2)
    }
    return if (idx != -1 && !this.isNull(idx)) this.getLong(idx) else default
}

fun Cursor.safeGetString(columnName1: String, columnName2: String? = null, default: String = ""): String {
    var idx = this.getColumnIndex(columnName1)
    if (idx == -1 && columnName2 != null) {
        idx = this.getColumnIndex(columnName2)
    }
    return if (idx != -1 && !this.isNull(idx)) this.getString(idx) else default
}

fun Cursor.safeGetDouble(columnName1: String, columnName2: String? = null, default: Double = 0.0): Double {
    var idx = this.getColumnIndex(columnName1)
    if (idx == -1 && columnName2 != null) {
        idx = this.getColumnIndex(columnName2)
    }
    return if (idx != -1 && !this.isNull(idx)) this.getDouble(idx) else default
}

fun Cursor.safeGetInt(columnName1: String, columnName2: String? = null, default: Int = 0): Int {
    var idx = this.getColumnIndex(columnName1)
    if (idx == -1 && columnName2 != null) {
        idx = this.getColumnIndex(columnName2)
    }
    return if (idx != -1 && !this.isNull(idx)) this.getInt(idx) else default
}

fun Cursor.safeGetBoolean(columnName1: String, columnName2: String? = null, default: Boolean = false): Boolean {
    val intVal = safeGetInt(columnName1, columnName2, if (default) 1 else 0)
    return intVal == 1
}
