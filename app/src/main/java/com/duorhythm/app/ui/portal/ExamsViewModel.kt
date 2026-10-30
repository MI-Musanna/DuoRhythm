package com.duorhythm.app.ui.portal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.SubjectWorkItem

class ExamsViewModel(application: Application) : AndroidViewModel(application) {
    private val dbHelper = DatabaseHelper.getInstance(application)

    private val _quizList = MutableLiveData<List<SubjectWorkItem>>()
    val quizList: LiveData<List<SubjectWorkItem>> = _quizList

    fun loadQuizzes() {
        val exams = dbHelper.getAllExams().filter {
            it.first.examType == "CLASS_TEST_QUIZ" || (it.first.examType != "SEMESTER_FINAL" && it.first.examType != "MIDTERM")
        }.distinctBy { it.first.id }.map { it.first }
        _quizList.postValue(exams)
    }

    fun insertQuiz(item: SubjectWorkItem): Long {
        val id = dbHelper.insertSubjectWorkItem(item)
        loadQuizzes()
        return id
    }

    fun deleteById(id: Long): Int {
        val count = dbHelper.deleteSubjectWorkItem(id)
        loadQuizzes()
        return count
    }
}

typealias DirectoryFragment = PortalFragment
typealias AcademicOverviewFragment = PortalFragment
typealias TimetableFragment = com.duorhythm.app.ui.schedule.ScheduleFragment
