package com.duorhythm.app.data.local.entity

data class AssessmentEntity(
    val id: Long = 0,
    val profileId: Long = 1,
    val courseId: Long = 0,
    val title: String = "",
    val type: String = "Semester Final Exam",
    val dueDate: String = "",
    val dueTime: String = "",
    val room: String = "",
    val instructor: String = "",
    val syllabus: String = "",
    val status: String = "PENDING",
    val score: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
