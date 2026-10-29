package database.entities

import com.duorhythm.app.data.model.SubjectWorkItem

data class AssessmentEntity(
    val id: Long = 0,
    val profileId: Long = 1,
    val subjectId: Long = 0,
    val subjectName: String = "",
    val courseCode: String = "",
    val type: String = "Semester Final Exam",
    val title: String = "",
    val date: String = "",
    val time: String = "",
    val room: String = "",
    val syllabus: String = "",
    val teacher: String = "",
    val status: String = "PENDING",
    val attachmentUri: String? = null,
    val attachmentName: String? = null
) {
    fun toSubjectWorkItem(): SubjectWorkItem {
        return SubjectWorkItem(
            id = id,
            profileId = profileId,
            subjectId = subjectId,
            itemType = "EXAM",
            title = title,
            description = syllabus,
            startDate = date,
            endDate = date,
            scoreOrMarks = time,
            status = status,
            attachmentPath = room,
            examType = type,
            attachmentUri = attachmentUri,
            attachmentName = attachmentName
        )
    }

    companion object {
        fun fromSubjectWorkItem(
            item: SubjectWorkItem,
            subjectName: String = "",
            courseCode: String = "",
            teacher: String = ""
        ): AssessmentEntity {
            return AssessmentEntity(
                id = item.id,
                profileId = item.profileId,
                subjectId = item.subjectId,
                subjectName = subjectName,
                courseCode = courseCode,
                type = if (item.examType.isNotBlank()) item.examType else "Semester Final Exam",
                title = item.title,
                date = item.endDate.ifBlank { item.startDate },
                time = item.scoreOrMarks,
                room = item.attachmentPath,
                syllabus = item.description,
                teacher = teacher,
                status = item.status,
                attachmentUri = item.attachmentUri,
                attachmentName = item.attachmentName
            )
        }
    }
}
