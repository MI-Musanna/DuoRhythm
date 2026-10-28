package database.dao

import database.entities.AssessmentEntity

interface AssessmentDao {
    fun getAssessmentsByProfile(profileId: Long): List<AssessmentEntity>
    fun getAssessmentsByCourse(profileId: Long, courseId: Long): List<AssessmentEntity>
    fun insertAssessment(assessment: AssessmentEntity): Long
    fun updateAssessment(assessment: AssessmentEntity)
    fun deleteAssessment(id: Long)
    fun deleteAssessmentsByCourse(profileId: Long, courseId: Long)
}
