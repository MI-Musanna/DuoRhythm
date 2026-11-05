package com.duorhythm.app.ui.portal

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.res.ColorStateList
import android.graphics.Color
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.duorhythm.app.MainActivity
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.StudentSubject
import com.duorhythm.app.data.model.WorkOrder
import com.duorhythm.app.data.model.ClientOrder
import com.duorhythm.app.ui.work.ClientOrdersAdapter
import com.duorhythm.app.data.local.pref.ProfileManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import com.duorhythm.app.data.model.SubjectWorkItem
import com.duorhythm.app.databinding.DialogEditContactBinding
import com.duorhythm.app.databinding.DialogAddExamBinding
import com.duorhythm.app.databinding.DialogAddQuizBinding
import com.duorhythm.app.databinding.DialogAddWorkOrderBinding
import com.duorhythm.app.databinding.FragmentPortalBinding
import com.duorhythm.app.util.CountryUtils
import com.duorhythm.app.util.KeyboardUtils
import database.entities.AssessmentEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val FragmentPortalBinding.rvExams: androidx.recyclerview.widget.RecyclerView get() = rvUnifiedAssessments
val FragmentPortalBinding.rvDirectory: androidx.recyclerview.widget.RecyclerView get() = rvStudentContacts

class PortalFragment : Fragment() {

    private var _binding: FragmentPortalBinding? = null
    private val binding get() = _binding!!

    private val portalViewModel: PortalViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(this)[PortalViewModel::class.java]
    }
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var studentSubjectsAdapter: StudentSubjectsAdapter
    private lateinit var workOrdersAdapter: WorkOrdersAdapter
    private lateinit var unifiedAssessmentsAdapter: AssessmentAdapter
    val coursesAdapter: StudentSubjectsAdapter get() = studentSubjectsAdapter
    val assessmentAdapter: AssessmentAdapter get() = unifiedAssessmentsAdapter
    private var selectedAssessmentFilter = "ALL"
    private lateinit var studentContactsAdapter: com.duorhythm.app.ui.tools.ContactsAdapter
    private lateinit var workContactsAdapter: com.duorhythm.app.ui.tools.ContactsAdapter

    private var isStudentProfile = true
    private var isSwitchingMode = false
    private var isStudentContactsTab = true
    private var isWorkContactsTab = true
    private var pendingOrderAttachmentPath = ""
    private var activeOrderDialogBinding: DialogAddWorkOrderBinding? = null
    private var studentContactRoleFilter = "ALL"
    private var workContactRoleFilter = "ALL"

    private val pickOrderAttachmentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                try {
                    requireContext().contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: SecurityException) {
                    // Fallback
                }
                saveOrderAttachmentFromUri(it)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPortalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            dbHelper = DatabaseHelper.getInstance(requireContext())
        } catch (e: Throwable) {
            android.util.Log.e("PortalFragment", "Error initializing dbHelper", e)
        }
        try {
            setupAdapters()
        } catch (e: Throwable) {
            android.util.Log.e("PortalFragment", "Error in setupAdapters", e)
        }
        try {
            setupListeners()
        } catch (e: Throwable) {
            android.util.Log.e("PortalFragment", "Error in setupListeners", e)
        }
        try {
            parentFragmentManager.setFragmentResultListener("REQUEST_PORTAL_TAB", viewLifecycleOwner) { _, bundle ->
                val targetTab = bundle.getInt("TARGET_TAB", 0)
                if (targetTab == 1) {
                    if (!isStudentProfile) {
                        isStudentProfile = true
                        isSwitchingMode = true
                        try {
                            binding.togglePortalProfile.check(R.id.btnProfileStudent)
                        } finally {
                            isSwitchingMode = false
                        }
                        (activity as? MainActivity)?.setStudentProfile(true)
                        updateProfileView()
                    }
                    binding.toggleStudentSegment.check(R.id.btnSegmentExams)
                    updateStudentSegmentView(R.id.btnSegmentExams)
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("PortalFragment", "Error setting fragment result listener", e)
        }
        try {
            KeyboardUtils.attachScrollDismiss(binding.rvExams, activity)
            KeyboardUtils.attachScrollDismiss(binding.rvDirectory, activity)
        } catch (e: Throwable) {
            android.util.Log.e("PortalFragment", "Error attaching scroll dismiss", e)
        }
        try {
            val prefs = requireContext().getSharedPreferences("lifesync_prefs", android.content.Context.MODE_PRIVATE)
            var isCoursesCollapsed = prefs.getBoolean("pref_courses_collapsed", false)
            updateCoursesCollapseState(isCoursesCollapsed, animate = false)

            binding.btnToggleEnrolledCourses.setOnClickListener {
                isCoursesCollapsed = !isCoursesCollapsed
                prefs.edit().putBoolean("pref_courses_collapsed", isCoursesCollapsed).apply()
                updateCoursesCollapseState(isCoursesCollapsed, animate = true)
            }
        } catch (e: Throwable) {
            android.util.Log.e("PortalFragment", "Error in setupEnrolledCoursesCollapse", e)
        }
        try {
            val rootView = activity?.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)
                ?: (binding.root as? ViewGroup)
            if (rootView != null) {
                com.duorhythm.app.util.BlurUtil.setupBlur(binding.blurPortalToggle, rootView)
            }
            updateProfileView()
            loadAcademicIdentity()
        } catch (e: Throwable) {
            android.util.Log.e("PortalFragment", "Error in post-setup", e)
        }
    }

    override fun onResume() {
        super.onResume()
        val profileId = com.duorhythm.app.data.local.pref.ProfileManager.getActiveProfileId()
        portalViewModel.loadCourses(profileId)
        if (_binding != null) {
            try {
                // Check profile from parent activity
                (activity as? MainActivity)?.let {
                    isStudentProfile = it.isStudentProfile()
                }
                updateProfileView()
                loadAcademicIdentity()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && _binding != null) {
            try {
                (activity as? MainActivity)?.let {
                    isStudentProfile = it.isStudentProfile()
                }
                updateProfileView()
                loadAcademicIdentity()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun setProfile(student: Boolean) {
        if (isStudentProfile == student) return
        isStudentProfile = student
        updateProfileView()
    }

    private fun setupAdapters() {
        // Student subjects
        studentSubjectsAdapter = StudentSubjectsAdapter(
            emptyList(),
            onSubjectClick = { subject ->
                val intent = Intent(requireContext(), StudentSubjectDetailActivity::class.java).apply {
                    putExtra(StudentSubjectDetailActivity.EXTRA_SUBJECT_ID, subject.id)
                }
                startActivity(intent)
            },
            onDeleteClick = { subject ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Subject")
                    .setMessage("Remove '${subject.name}' and all associated routine classes, exams, attendance records, and syllabus materials?")
                    .setPositiveButton("Delete") { _, _ ->
                        val repo = com.duorhythm.app.data.repository.CourseRepository(requireContext())
                        repo.deleteCourseCascadeSync(subject.id, subject.code, subject.profileId)
                        loadStudentData()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        binding.rvEnrolledCourses.layoutManager = LinearLayoutManager(requireContext())
        binding.rvEnrolledCourses.adapter = studentSubjectsAdapter

        // Work orders
        workOrdersAdapter = WorkOrdersAdapter(
            emptyList(),
            onAdvanceStatus = { order ->
                val nextStatus = when (order.status) {
                    "PENDING" -> "IN_PROGRESS"
                    "IN_PROGRESS" -> "COMPLETED"
                    else -> "PENDING"
                }
                dbHelper.updateWorkOrderStatus(order.id, nextStatus)
                if (nextStatus == "COMPLETED") {
                    com.duorhythm.app.scheduler.OrderAlarmScheduler.cancelOrderReminders(requireContext(), order.id)
                } else {
                    com.duorhythm.app.scheduler.OrderAlarmScheduler.scheduleOrderReminders(requireContext(), order.copy(status = nextStatus))
                }
                loadWorkData()
            },
            onDeleteClick = { order ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Work Order")
                    .setMessage("Remove '${order.title}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        com.duorhythm.app.scheduler.OrderAlarmScheduler.cancelOrderReminders(requireContext(), order.id)
                        dbHelper.deleteWorkOrder(order.id)
                        loadWorkData()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        // Unified Assessments
        unifiedAssessmentsAdapter = AssessmentAdapter(
            emptyList(),
            onToggleStatus = { entity, nextStatus ->
                dbHelper.updateAssessmentStatus(entity.id, nextStatus)
                com.duorhythm.app.widget.AcademicHubWidget.pushUpdate(requireContext())
                loadStudentData()
            },
            onDeleteClick = { entity ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Assessment")
                    .setMessage("Remove '${entity.title}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        dbHelper.deleteSubjectWorkItem(entity.id)
                        com.duorhythm.app.scheduler.AssessmentAlarmScheduler.cancelAlarmsForAssessment(requireContext(), entity.id)
                        com.duorhythm.app.widget.AcademicHubWidget.pushUpdate(requireContext())
                        loadStudentData()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onItemClick = { entity ->
                showEditExamDialog(entity)
            }
        )
        unifiedAssessmentsAdapter.onFilterApplied = { filtered ->
            if (filtered.isEmpty()) {
                binding.rvUnifiedAssessments.visibility = View.GONE
                binding.tvEmptyAssessments.visibility = View.VISIBLE
                binding.tvEmptyAssessments.text = if (unifiedAssessmentsAdapter.masterItemCount == 0) {
                    "No upcoming exams or assessments scheduled. Tap + SCHEDULE ASSESSMENT to add one!"
                } else {
                    "No assessments found matching filter."
                }
            } else {
                binding.rvUnifiedAssessments.visibility = View.VISIBLE
                binding.tvEmptyAssessments.visibility = View.GONE
            }
        }
        binding.rvUnifiedAssessments.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUnifiedAssessments.adapter = unifiedAssessmentsAdapter

        // Student Contacts
        studentContactsAdapter = com.duorhythm.app.ui.tools.ContactsAdapter(
            emptyList(),
            onDeleteClick = { contact ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Contact")
                    .setMessage("Remove '${contact.name}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        dbHelper.deleteContactGroup(contact.id)
                        loadStudentContacts()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onItemClick = { contact ->
                if (contact.type.equals("GROUP", ignoreCase = true)) {
                    showEditGroupLinkDialog(contact)
                } else {
                    showEditContactDialog(contact)
                }
            }
        )
        binding.rvStudentContacts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvStudentContacts.adapter = studentContactsAdapter

        // Work Contacts
        workContactsAdapter = com.duorhythm.app.ui.tools.ContactsAdapter(
            emptyList(),
            onDeleteClick = { contact ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Contact")
                    .setMessage("Remove '${contact.name}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        dbHelper.deleteContactGroup(contact.id)
                        loadWorkContacts()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onItemClick = { contact ->
                if (contact.type.equals("GROUP", ignoreCase = true)) {
                    showEditGroupLinkDialog(contact)
                } else {
                    showEditContactDialog(contact)
                }
            }
        )
        binding.rvWorkPortalContacts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvWorkPortalContacts.adapter = workContactsAdapter
    }

    private fun setupListeners() {
        binding.togglePortalProfile.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (!isChecked || isSwitchingMode) return@addOnButtonCheckedListener

            val prefs = requireContext().getSharedPreferences("lifesync_prefs", android.content.Context.MODE_PRIVATE)
            val workCurrentlyEnabled = prefs.getBoolean("pref_work_portal", dbHelper.isWorkPortalEnabled())

            if (checkedId == R.id.btnProfileWork) {
                if (!workCurrentlyEnabled) {
                    android.widget.Toast.makeText(requireContext(), "Work Portal is disabled in Settings", android.widget.Toast.LENGTH_SHORT).show()
                    isSwitchingMode = true
                    try {
                        group.check(R.id.btnProfileStudent)
                    } finally {
                        isSwitchingMode = false
                    }
                    isStudentProfile = true
                    (activity as? MainActivity)?.setStudentProfile(true)
                    updateProfileView()
                    return@addOnButtonCheckedListener
                }
                isStudentProfile = false
            } else if (checkedId == R.id.btnProfileStudent) {
                isStudentProfile = true
            }

            (activity as? MainActivity)?.setStudentProfile(isStudentProfile)
            updateProfileView()
        }
        binding.toggleStudentSegment.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                updateStudentSegmentView(checkedId)
            }
        }
        binding.btnAddSubject.setOnClickListener {
            try {
                showAddSubjectDialog()
            } catch (e: Throwable) {
                android.util.Log.e("FATAL_DUORHYTHM", "Crash cause: ", e)
            }
        }
        binding.btnEnrollFirstSubject.setOnClickListener {
            try {
                showAddSubjectDialog()
            } catch (e: Throwable) {
                android.util.Log.e("FATAL_DUORHYTHM", "Crash cause: ", e)
            }
        }
        binding.btnEmptyAddSubject.setOnClickListener {
            try {
                showAddSubjectDialog()
            } catch (e: Throwable) {
                android.util.Log.e("FATAL_DUORHYTHM", "Crash cause: ", e)
            }
        }
        binding.btnStudentScheduleShortcut.setOnClickListener {
            (activity as? com.duorhythm.app.MainActivity)?.selectNavTab(R.id.nav_schedule)
        }

        binding.cardAcademicInstitution.setOnClickListener {
            showEditInstitutionDialog()
        }

        binding.btnScheduleAssessment.setOnClickListener {
            val defType = when (selectedAssessmentFilter) {
                "FINALS" -> "SEMESTER_FINAL"
                "MIDTERMS" -> "MIDTERM"
                "QUIZZES" -> "CLASS_TEST_QUIZ"
                "LAB" -> "Lab Exam / Practical"
                "VIVA" -> "Presentation / Viva"
                else -> "Semester Final Exam"
            }
            showAddExamDialog(defType)
        }

        binding.btnExportAssessments.setOnClickListener {
            ExportAssessmentsBottomSheet().show(childFragmentManager, "EXPORT_ASSESSMENTS_SHEET")
        }

        binding.etExamSearch.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            binding.btnClearExamSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
            assessmentAdapter.updateSearchQuery(query)
        }

        binding.btnClearExamSearch.setOnClickListener {
            binding.etExamSearch.text?.clear()
        }

        binding.cgAssessmentFilters.setOnCheckedChangeListener { group, checkedId ->
            val selectedChip = group.findViewById<com.google.android.material.chip.Chip>(checkedId)
            val selectedChipText = selectedChip?.text?.toString() ?: "All"
            selectedAssessmentFilter = when (checkedId) {
                R.id.chipFilterFinals -> "FINALS"
                R.id.chipFilterMidterms -> "MIDTERMS"
                R.id.chipFilterQuizzes -> "QUIZZES"
                R.id.chipFilterLab -> "LAB"
                R.id.chipFilterViva -> "VIVA"
                else -> "ALL"
            }
            assessmentAdapter.updateCategoryFilter(selectedChipText)
        }

        binding.toggleStudentDirectoryTab.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isStudentContactsTab = checkedId == R.id.tabStudentContacts
                val cyanColor = ColorStateList.valueOf(Color.parseColor("#0284C7"))
                val transparentColor = ColorStateList.valueOf(Color.TRANSPARENT)
                if (isStudentContactsTab) {
                    binding.tabStudentContacts.backgroundTintList = cyanColor
                    binding.tabStudentContacts.setTextColor(Color.WHITE)
                    binding.tabStudentGroupLinks.backgroundTintList = transparentColor
                    binding.tabStudentGroupLinks.setTextColor(Color.parseColor("#64748B"))
                    binding.btnStudentAddContact.text = "+ Contact"
                } else {
                    binding.tabStudentGroupLinks.backgroundTintList = cyanColor
                    binding.tabStudentGroupLinks.setTextColor(Color.WHITE)
                    binding.tabStudentContacts.backgroundTintList = transparentColor
                    binding.tabStudentContacts.setTextColor(Color.parseColor("#64748B"))
                    binding.btnStudentAddContact.text = "+ Add Link"
                }
                loadStudentContacts()
            }
        }

        binding.btnStudentAddContact.setOnClickListener {
            try {
                if (isStudentContactsTab) showAddContactDialog(true) else showAddGroupLinkDialog(true)
            } catch (e: Throwable) {
                android.util.Log.e("FATAL_DUORHYTHM", "Crash cause: ", e)
            }
        }
        binding.btnAddContact.setOnClickListener {
            try {
                showAddContactDialog(isStudentProfile)
            } catch (e: Throwable) {
                android.util.Log.e("FATAL_DUORHYTHM", "Crash cause: ", e)
            }
        }
        binding.btnAddLink.setOnClickListener {
            try {
                showAddGroupLinkDialog(isStudentProfile)
            } catch (e: Throwable) {
                android.util.Log.e("FATAL_DUORHYTHM", "Crash cause: ", e)
            }
        }
        binding.btnWorkAddContact.setOnClickListener {
            try {
                showAddContactDialog(false)
            } catch (e: Throwable) {
                android.util.Log.e("FATAL_DUORHYTHM", "Crash cause: ", e)
            }
        }
    }

    private fun updateStudentSegmentView(checkedId: Int) {
        val cyanColor = ColorStateList.valueOf(Color.parseColor("#0284C7"))
        val transparentColor = ColorStateList.valueOf(Color.TRANSPARENT)
        val inactiveColor = Color.parseColor("#64748B")
        val inactiveTint = ColorStateList.valueOf(inactiveColor)
        val whiteTint = ColorStateList.valueOf(Color.WHITE)

        val isCourses = checkedId == R.id.btnSegmentCourses || checkedId == View.NO_ID
        val isExams = checkedId == R.id.btnSegmentExams
        val isDirectory = checkedId == R.id.btnSegmentDirectory

        binding.layoutSegmentCourses.visibility = if (isCourses) View.VISIBLE else View.GONE
        binding.layoutSegmentExams.visibility = if (isExams) View.VISIBLE else View.GONE
        binding.layoutSegmentDirectory.visibility = if (isDirectory) View.VISIBLE else View.GONE

        binding.btnSegmentCourses.apply {
            backgroundTintList = if (isCourses) cyanColor else transparentColor
            setTextColor(if (isCourses) Color.WHITE else inactiveColor)
            iconTint = if (isCourses) whiteTint else inactiveTint
        }
        binding.btnSegmentExams.apply {
            backgroundTintList = if (isExams) cyanColor else transparentColor
            setTextColor(if (isExams) Color.WHITE else inactiveColor)
            iconTint = if (isExams) whiteTint else inactiveTint
        }
        binding.btnSegmentDirectory.apply {
            backgroundTintList = if (isDirectory) cyanColor else transparentColor
            setTextColor(if (isDirectory) Color.WHITE else inactiveColor)
            iconTint = if (isDirectory) whiteTint else inactiveTint
        }
    }

    private fun updateProfileView() {
        if (_binding == null || !isAdded || !::dbHelper.isInitialized || isSwitchingMode) return
        isSwitchingMode = true
        try {
            val prefs = requireContext().getSharedPreferences("lifesync_prefs", android.content.Context.MODE_PRIVATE)
            val workEnabled = prefs.getBoolean("pref_work_portal", dbHelper.isWorkPortalEnabled())
            if (!workEnabled) {
                isStudentProfile = true
                binding.togglePortalProfile.visibility = View.GONE
                (activity as? MainActivity)?.setStudentProfile(true)
            } else {
                binding.togglePortalProfile.visibility = View.VISIBLE
            }

            val targetChecked = if (isStudentProfile) R.id.btnProfileStudent else R.id.btnProfileWork
            if (binding.togglePortalProfile.checkedButtonId != targetChecked) {
                binding.togglePortalProfile.check(targetChecked)
            }

            updateToggleStyle(isStudentProfile)

            if (isStudentProfile) {
                binding.layoutStudentPortal.visibility = View.VISIBLE
                binding.layoutWorkPortalContainer.visibility = View.GONE
                val currentSegment = if (binding.toggleStudentSegment.checkedButtonId != View.NO_ID) {
                    binding.toggleStudentSegment.checkedButtonId
                } else {
                    R.id.btnSegmentCourses
                }
                updateStudentSegmentView(currentSegment)
                loadStudentData()
            } else {
                binding.layoutStudentPortal.visibility = View.GONE
                binding.layoutWorkPortalContainer.visibility = View.VISIBLE
                setupWorkPortalView()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            isSwitchingMode = false
        }
    }

    private fun updateToggleStyle(isStudent: Boolean) {
        try {
            val density = resources.displayMetrics.density
            val cyanColor = ColorStateList.valueOf(Color.parseColor("#0284C7"))
            val cyanStroke = ColorStateList.valueOf(Color.parseColor("#38BDF8"))
            val emeraldColor = ColorStateList.valueOf(Color.parseColor("#10B981"))
            val emeraldStroke = ColorStateList.valueOf(Color.parseColor("#059669"))
            val transparentColor = ColorStateList.valueOf(Color.TRANSPARENT)
            val stroke1dp = (1 * density).toInt()

            if (isStudent) {
                binding.btnProfileStudent.backgroundTintList = cyanColor
                binding.btnProfileStudent.strokeColor = cyanStroke
                binding.btnProfileStudent.strokeWidth = stroke1dp
                binding.btnProfileStudent.setTextColor(Color.WHITE)
                binding.btnProfileStudent.elevation = 3f * density

                binding.btnProfileWork.backgroundTintList = transparentColor
                binding.btnProfileWork.strokeWidth = 0
                binding.btnProfileWork.setTextColor(Color.parseColor("#475569"))
                binding.btnProfileWork.elevation = 0f
            } else {
                binding.btnProfileStudent.backgroundTintList = transparentColor
                binding.btnProfileStudent.strokeWidth = 0
                binding.btnProfileStudent.setTextColor(Color.parseColor("#475569"))
                binding.btnProfileStudent.elevation = 0f

                binding.btnProfileWork.backgroundTintList = emeraldColor
                binding.btnProfileWork.strokeColor = emeraldStroke
                binding.btnProfileWork.strokeWidth = stroke1dp
                binding.btnProfileWork.setTextColor(Color.WHITE)
                binding.btnProfileWork.elevation = 3f * density
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadStudentData() {
        loadAcademicIdentity()
        val subjects = dbHelper.getAllStudentSubjects()
        val allAttendance = dbHelper.getAllAttendance()
        val attendanceMap = allAttendance.associateBy { it.subjectName }
        val progressMap = subjects.associate { subject ->
            val tasks = dbHelper.getItemsForSubject(subject.id)
            val totalItems = tasks.size
            val completedItems = tasks.count { it.status.equals("DONE", ignoreCase = true) }
            val progressPercentage = if (totalItems > 0) {
                ((completedItems.toFloat() / totalItems.toFloat()) * 100).toInt()
            } else {
                0 // Brand new courses with 0 tasks must strictly start at 0%
            }
            subject.id to progressPercentage
        }
        coursesAdapter.submitList(if (subjects != null) ArrayList(subjects) else null, attendanceMap, progressMap)
        studentSubjectsAdapter.updateList(subjects, attendanceMap, progressMap)
        binding.tvCourseCountBadge.text = "${subjects.size} Courses"
        if (subjects.isEmpty()) {
            binding.rvEnrolledCourses.visibility = View.GONE
            binding.layoutEmptyStudentSubjects.visibility = View.VISIBLE
        } else {
            val isCollapsed = requireContext().getSharedPreferences("lifesync_prefs", android.content.Context.MODE_PRIVATE).getBoolean("pref_courses_collapsed", false)
            binding.rvEnrolledCourses.visibility = if (isCollapsed) View.GONE else View.VISIBLE
            binding.layoutEmptyStudentSubjects.visibility = View.GONE
        }

        val totalAssessments = subjects.sumOf { dbHelper.getItemsForSubject(it.id).size }
        binding.tvStudentTotalSubjects.text = "${subjects.size} Courses"
        binding.tvStatTotalCourses.text = "${subjects.size} Courses"
        binding.tvStudentTotalAssessments.text = "$totalAssessments Items"
        binding.tvStatPendingAssessments.text = "$totalAssessments Items"

        val totalAttended = allAttendance.sumOf { it.attendedClasses }
        val totalClasses = allAttendance.sumOf { it.totalClasses }
        if (totalClasses > 0) {
            val avgPct = (totalAttended * 100) / totalClasses
            binding.tvStudentAttendanceTarget.text = "$avgPct% Target"
            binding.tvStatAttendanceRate.text = "$avgPct% Att."
        } else {
            binding.tvStudentAttendanceTarget.text = "0% Target"
            binding.tvStatAttendanceRate.text = "0% Att."
        }

        // Load Unified Exams & Assessments
        val allExamsPairs = dbHelper.getAllExams().distinctBy { it.first.id }
        val allAssessmentEntities = allExamsPairs.map { pair ->
            AssessmentEntity.fromSubjectWorkItem(
                item = pair.first,
                subjectName = pair.second.name,
                courseCode = pair.second.code,
                teacher = pair.second.teacher
            )
        }
        assessmentAdapter.setMasterData(allAssessmentEntities)
        binding.tvScheduledCountBadge.text = "${allAssessmentEntities.size} Scheduled"
        context?.let { ctx ->
            com.duorhythm.app.widget.ExamCountdownWidget.pushUpdate(ctx)
            com.duorhythm.app.widget.AcademicHubWidget.pushUpdate(ctx)
        }

        loadStudentContacts()
    }

    private fun loadStudentContacts() {
        val all = dbHelper.getContactsForPortal(isStudent = true, roleFilter = studentContactRoleFilter)
        val contacts = if (isStudentContactsTab) {
            all.filter { !it.type.equals("GROUP", ignoreCase = true) }
        } else {
            all.filter { it.type.equals("GROUP", ignoreCase = true) }
        }
        studentContactsAdapter.updateList(contacts)
        if (contacts.isEmpty()) {
            binding.rvStudentContacts.visibility = View.GONE
            binding.tvEmptyStudentContacts.visibility = View.VISIBLE
            binding.tvEmptyStudentContacts.text = if (isStudentContactsTab) {
                "No contacts recorded yet. Tap + Contact to save teachers or classmates!"
            } else {
                "No community or study group links saved yet. Tap + Add Link to save Discord, Telegram, or WhatsApp groups."
            }
        } else {
            binding.rvStudentContacts.visibility = View.VISIBLE
            binding.tvEmptyStudentContacts.visibility = View.GONE
        }
    }

    private fun setupWorkPortalView() {
        if (_binding == null || !isAdded) return
        val context = requireContext()
        val db = DatabaseHelper.getInstance(context)
        val profileId = ProfileManager.getActiveProfileId()

        val orders = db.getClientOrders(profileId)
        val activeOrders = orders.filter { it.status.uppercase() != "COMPLETED" }
        val totalBilled = orders.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }

        // 1. Update 1-line modern status pill
        binding.tvWorkStatTotalOrders.text = "${orders.size} Orders"
        binding.tvWorkStatActiveOrders.text = "${activeOrders.size} In Progress"
        binding.tvWorkStatTotalBilled.text = "৳${String.format(java.util.Locale.US, "%.2f", totalBilled)} Billed"

        // 2. Programmatically enforce LinearLayoutManager
        binding.rvWorkClientOrders.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        binding.rvWorkClientOrders.setHasFixedSize(true)

        if (orders.isEmpty()) {
            binding.rvWorkClientOrders.visibility = View.GONE
            binding.tvEmptyWorkOrders.visibility = View.VISIBLE
        } else {
            binding.rvWorkClientOrders.visibility = View.VISIBLE
            binding.tvEmptyWorkOrders.visibility = View.GONE

            binding.rvWorkClientOrders.adapter = ClientOrdersAdapter(
                orders = orders,
                onOrderClick = { order -> showOrderDetailsModal(order) },
                onDeleteOrder = { order ->
                    db.deleteClientOrder(order.id)
                    setupWorkPortalView()
                }
            )
        }

        binding.btnNewClientOrderPortal.setOnClickListener {
            showCreateOrderDialog()
        }
        loadWorkContacts()
    }

    private fun loadWorkData() {
        setupWorkPortalView()
    }

    private fun showCreateOrderDialog() {
        val createDialog = com.duorhythm.app.ui.work.CreateWorkOrderDialogFragment()
        createDialog.setOnOrderSavedListener { setupWorkPortalView() }
        createDialog.show(parentFragmentManager, "CreateWorkOrder")
    }

    private fun showOrderDetailsModal(order: ClientOrder) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext(), R.style.CustomBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.dialog_client_order_details, null)
        dialog.setContentView(view)

        view.findViewById<android.widget.TextView>(R.id.tvDetailOrderTitle).text = order.title
        view.findViewById<android.widget.TextView>(R.id.tvDetailClientName).text = "Client: ${if (order.clientName.isNotBlank()) order.clientName else "Direct Customer"}"
        view.findViewById<android.widget.TextView>(R.id.tvDetailAmount).text = "৳${order.amount}"
        view.findViewById<android.widget.TextView>(R.id.tvDetailStatus).text = order.status
        view.findViewById<android.widget.TextView>(R.id.tvDetailDueDate).text = "Due Date: ${if (order.dueDate.isNotBlank()) order.dueDate else "Ongoing"}"
        val tvNotes = view.findViewById<android.widget.TextView>(R.id.tvDetailNotes)
        if (order.notes.isNotBlank()) {
            tvNotes.visibility = View.VISIBLE
            tvNotes.text = "Notes: ${order.notes}"
        } else {
            tvNotes.visibility = View.GONE
        }

        // EDIT ACTION:
        val btnEdit = view.findViewById<View>(R.id.btnEditOrder)
        btnEdit.visibility = View.VISIBLE
        btnEdit.setOnClickListener {
            dialog.dismiss()
            val editDialog = com.duorhythm.app.ui.work.CreateWorkOrderDialogFragment()
            editDialog.setupEditMode(order)
            editDialog.setOnOrderSavedListener { setupWorkPortalView() }
            editDialog.show(parentFragmentManager, "EditWorkOrder")
        }

        // MARK COMPLETE ACTION:
        val btnComplete = view.findViewById<View>(R.id.btnMarkComplete)
        if (order.status.uppercase() == "COMPLETED") {
            (btnComplete as? com.google.android.material.button.MaterialButton)?.text = "MARK IN PROGRESS"
            btnComplete.setOnClickListener {
                dialog.dismiss()
                val db = DatabaseHelper.getInstance(requireContext())
                db.updateClientOrderStatus(order.id, "IN PROGRESS")
                setupWorkPortalView()
            }
        } else {
            btnComplete.setOnClickListener {
                dialog.dismiss()
                showDepositEarningsDialog(order)
            }
        }

        // DELETE ACTION:
        view.findViewById<View>(R.id.btnDeleteOrder).setOnClickListener {
            dialog.dismiss()
            confirmDeleteOrder(order)
        }

        dialog.show()
    }

    private fun confirmDeleteOrder(order: ClientOrder) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Order")
            .setMessage("Are you sure you want to remove '${order.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                val db = DatabaseHelper.getInstance(requireContext())
                db.deleteClientOrder(order.id)
                setupWorkPortalView()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDepositEarningsDialog(order: ClientOrder) {
        val db = DatabaseHelper.getInstance(requireContext())
        val profileId = ProfileManager.getActiveProfileId()
        val accounts = db.getAccounts(profileId)

        val depositDialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext(), R.style.CustomBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.dialog_deposit_earnings, null)
        depositDialog.setContentView(view)

        view.findViewById<android.widget.TextView>(R.id.tvDepositOrderTitleSub).text = "${order.title} • Client: ${order.clientName}"
        view.findViewById<android.widget.TextView>(R.id.tvDepositAmountHighlight).text = "+ ৳${order.amount}"

        val container = view.findViewById<android.widget.LinearLayout>(R.id.layoutBucketListContainer)
        val orderAmount = order.amount.toDoubleOrNull() ?: 0.0

        for (acc in accounts) {
            val card = com.google.android.material.card.MaterialCardView(requireContext()).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 16) }
                setCardBackgroundColor(Color.parseColor("#0D1322"))
                radius = 24f
                strokeWidth = 2
                strokeColor = Color.parseColor("#18243C")

                val row = android.widget.LinearLayout(requireContext()).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    setPadding(32, 28, 32, 28)
                    gravity = android.view.Gravity.CENTER_VERTICAL

                    val icon = android.widget.ImageView(requireContext()).apply {
                        layoutParams = android.widget.LinearLayout.LayoutParams(48, 48)
                        setImageResource(R.drawable.ic_wallet)
                        imageTintList = ColorStateList.valueOf(Color.parseColor("#10B981"))
                    }
                    addView(icon)

                    val textCol = android.widget.LinearLayout(requireContext()).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginStart = 24
                        }
                        val tvName = android.widget.TextView(requireContext()).apply {
                            text = acc.name
                            setTextColor(Color.WHITE)
                            textSize = 14f
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        val tvBal = android.widget.TextView(requireContext()).apply {
                            text = "Current: ৳${acc.balance}"
                            setTextColor(Color.parseColor("#94A3B8"))
                            textSize = 11f
                        }
                        addView(tvName)
                        addView(tvBal)
                    }
                    addView(textCol)

                    val arrow = android.widget.TextView(requireContext()).apply {
                        text = "Deposit →"
                        setTextColor(Color.parseColor("#10B981"))
                        textSize = 12f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    addView(arrow)
                }
                addView(row)

                setOnClickListener {
                    depositDialog.dismiss()
                    val success = db.completeOrderAndDeposit(
                        orderId = order.id,
                        profileId = profileId,
                        accountId = acc.id,
                        orderTitle = order.title,
                        clientName = order.clientName,
                        amount = orderAmount
                    )
                    if (success) {
                        Toast.makeText(requireContext(), "৳${order.amount} deposited into ${acc.name}! Order Completed.", Toast.LENGTH_LONG).show()
                        setupWorkPortalView()
                    }
                }
            }
            container.addView(card)
        }

        view.findViewById<View>(R.id.btnSkipDeposit).setOnClickListener {
            depositDialog.dismiss()
            db.updateClientOrderStatus(order.id, "COMPLETED")
            Toast.makeText(requireContext(), "Order marked as completed without wallet deposit.", Toast.LENGTH_SHORT).show()
            setupWorkPortalView()
        }

        depositDialog.show()
    }

    private fun loadWorkContacts() {
        if (!isAdded || _binding == null) return
        val all = dbHelper.getContactsForPortal(isStudent = false, roleFilter = workContactRoleFilter)
        workContactsAdapter.updateList(all)
        if (all.isEmpty()) {
            binding.rvWorkPortalContacts.visibility = View.GONE
            binding.tvEmptyWorkContacts.visibility = View.VISIBLE
        } else {
            binding.rvWorkPortalContacts.visibility = View.VISIBLE
            binding.tvEmptyWorkContacts.visibility = View.GONE
        }
    }

    private fun showAddExamDialog(preselectedExamType: String) {
        ScheduleAssessmentDialog(
            context = requireContext(),
            fragmentManager = parentFragmentManager,
            defaultType = preselectedExamType,
            onSaved = { loadStudentData() }
        ).show()
    }

    private fun showAddQuizDialog() {
        ScheduleAssessmentDialog(
            context = requireContext(),
            fragmentManager = parentFragmentManager,
            defaultType = "CLASS_TEST_QUIZ",
            onSaved = { loadStudentData() }
        ).show()
    }

    private fun showEditExamDialog(entity: database.entities.AssessmentEntity) {
        ScheduleAssessmentDialog(
            context = requireContext(),
            fragmentManager = parentFragmentManager,
            existingItem = entity.toSubjectWorkItem(),
            existingEntity = entity,
            onSaved = { loadStudentData() }
        ).show()
    }

    private fun showAddContactDialog(isStudentContext: Boolean = isStudentProfile) {
        val activity = requireActivity()
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_add_contact, null)

        val etName = dialogView.findViewById<android.widget.EditText>(R.id.etContactName)
        val etPhone = dialogView.findViewById<android.widget.EditText>(R.id.etPhoneNumber)
        val etRole = dialogView.findViewById<android.widget.EditText>(R.id.etContactRole)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveContact)

        val defaultCountryCode = CountryUtils.getDeviceCountryCode(activity)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(dialogView)
            .create()

        btnSave?.setOnClickListener {
            val name = etName?.text?.toString()?.trim().orEmpty()
            val phoneRaw = etPhone?.text?.toString()?.trim().orEmpty()
            val cleanPhone = CountryUtils.getNormalizedInternationalNumber(phoneRaw, defaultCountryCode)
            val customRole = etRole?.text?.toString()?.trim().orEmpty().ifEmpty { "Contact" }
            val scope = if (isStudentContext) "STUDENT_ONLY" else "WORK_ONLY"

            if (name.isEmpty()) {
                etName?.error = "Please enter a name"
                return@setOnClickListener
            }

            val phoneRegex = Regex("^\\+?[0-9]{7,15}$")
            if (cleanPhone.isEmpty() || !phoneRegex.matches(cleanPhone)) {
                etPhone?.error = "Please enter a valid phone number"
                return@setOnClickListener
            }

            btnSave.isEnabled = false

            val item = com.duorhythm.app.data.model.ContactGroupItem(
                name = name,
                type = "CONTACT",
                platform = "PHONE",
                targetLink = cleanPhone,
                notes = "",
                phoneNumber = cleanPhone,
                role = customRole,
                scope = scope
            )
            CoroutineScope(Dispatchers.IO).launch {
                dbHelper.insertContactGroup(item)
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    if (isStudentContext) loadStudentContacts() else loadWorkContacts()
                    Toast.makeText(activity, "Contact saved to Directory!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            setDimAmount(0.65f)
        }
        dialog.show()
    }

    private fun showEditContactDialog(contact: com.duorhythm.app.data.model.ContactGroupItem) {
        try {
            val ctx = context ?: return
            val dialogBinding = DialogEditContactBinding.inflate(LayoutInflater.from(ctx))
            val dialogView = dialogBinding.root

            val etName = dialogBinding.etContactName
            val etPhone = dialogBinding.etContactPhone
            val etRole = dialogBinding.etContactRole
            val etNotes = dialogBinding.etContactNotes
            val etCountry = dialogBinding.etContactCountryCode
            val spScope = dialogBinding.spContactScope
            val tilPhone = dialogBinding.tilContactPhone
            val btnSave = dialogBinding.btnSaveContact

            val scopes = listOf("STUDENT_ONLY", "WORK_ONLY", "SHARED")
            val scopeDisplay = listOf("Student Portal", "Work Portal", "Both / Shared")
            spScope?.adapter =
                android.widget.ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, scopeDisplay)
            val currentScopeIdx = when (contact.scope) {
                "STUDENT_ONLY" -> 0
                "WORK_ONLY" -> 1
                else -> 2
            }
            spScope?.setSelection(currentScopeIdx)

            etName?.setText(contact.name)
            etRole?.setText(contact.role)
            etNotes?.setText(contact.notes)

            val defaultCountryCode = CountryUtils.getDeviceCountryCode(ctx)
            etCountry?.setText(defaultCountryCode)
            etPhone?.setText(contact.phoneNumber)

            val dialog = MaterialAlertDialogBuilder(ctx)
                .setView(dialogView)
                .create()

            btnSave?.setOnClickListener {
                try {
                    val name = etName?.text?.toString()?.trim().orEmpty()
                    val countryCode = etCountry?.text?.toString()?.trim().orEmpty().ifEmpty { defaultCountryCode }
                    val phoneRaw = etPhone?.text?.toString()?.trim().orEmpty()
                    val cleanPhone = CountryUtils.getNormalizedInternationalNumber(phoneRaw, countryCode)
                    val customRole = etRole?.text?.toString()?.trim().orEmpty().ifEmpty { "Contact" }
                    val notes = etNotes?.text?.toString()?.trim().orEmpty()
                    val scopePos = spScope?.selectedItemPosition ?: currentScopeIdx
                    val scope = scopes.getOrElse(scopePos) { "SHARED" }

                    if (name.isEmpty()) {
                        etName?.error = "Please enter a name"
                        return@setOnClickListener
                    } else {
                        etName?.error = null
                    }

                    val phoneRegex = Regex("^\\+?[0-9]{7,15}$")
                    if (cleanPhone.isEmpty() || !phoneRegex.matches(cleanPhone)) {
                        tilPhone?.error = "Please enter a valid phone number"
                        return@setOnClickListener
                    } else {
                        tilPhone?.error = null
                    }

                    btnSave.isEnabled = false

                    val updated = contact.copy(
                        name = name,
                        phoneNumber = cleanPhone,
                        targetLink = cleanPhone,
                        role = customRole,
                        notes = notes,
                        scope = scope
                    )
                    dbHelper.updateContactGroup(updated)
                    dialog.dismiss()
                    loadStudentContacts()
                    loadWorkContacts()
                    Toast.makeText(ctx, "Contact updated!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("DirectoryFragment", "Error updating contact", e)
                }
            }

            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            dialog.show()
        } catch (e: Exception) {
            Log.e("DirectoryFragment", "Error showing dialog", e)
        }
    }

    private fun showAddGroupLinkDialog(isStudentContext: Boolean = isStudentProfile) {
        val activity = requireActivity()
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_add_group_link, null)

        val etTitle = dialogView.findViewById<android.widget.EditText>(R.id.etGroupTitle)
        val etUrl = dialogView.findViewById<android.widget.EditText>(R.id.etGroupUrl)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveGroupLink)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(dialogView)
            .create()

        btnSave?.setOnClickListener {
            val name = etTitle?.text?.toString()?.trim().orEmpty()
            val link = etUrl?.text?.toString()?.trim().orEmpty()

            if (name.isEmpty()) {
                etTitle?.error = "Please enter a name"
                return@setOnClickListener
            }

            if (link.isEmpty()) {
                etUrl?.error = "Please enter a valid URL (e.g. https://chat.whatsapp.com/...)"
                return@setOnClickListener
            }

            var formattedLink = link
            if (!formattedLink.startsWith("http://", ignoreCase = true) && !formattedLink.startsWith("https://", ignoreCase = true)) {
                formattedLink = "https://$formattedLink"
            }

            if (!android.util.Patterns.WEB_URL.matcher(formattedLink).matches()) {
                etUrl?.error = "Please enter a valid URL (e.g. https://chat.whatsapp.com/...)"
                return@setOnClickListener
            }

            val platformCode = when {
                formattedLink.contains("discord", ignoreCase = true) -> "DISCORD"
                formattedLink.contains("t.me", ignoreCase = true) || formattedLink.contains("telegram", ignoreCase = true) -> "TELEGRAM"
                formattedLink.contains("whatsapp", ignoreCase = true) || formattedLink.contains("wa.me", ignoreCase = true) -> "WHATSAPP"
                formattedLink.contains("m.me", ignoreCase = true) || formattedLink.contains("messenger", ignoreCase = true) -> "MESSENGER"
                else -> "OTHER"
            }

            val scope = if (isStudentContext) "STUDENT_ONLY" else "WORK_ONLY"

            btnSave.isEnabled = false

            val item = com.duorhythm.app.data.model.ContactGroupItem(
                name = name,
                type = "GROUP",
                platform = platformCode,
                targetLink = formattedLink,
                notes = "",
                role = if (isStudentContext) "Study Group" else "Client Channel",
                scope = scope
            )

            CoroutineScope(Dispatchers.IO).launch {
                dbHelper.insertContactGroup(item)
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    if (isStudentContext) loadStudentContacts() else loadWorkContacts()
                    Toast.makeText(activity, "Community group link saved!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            setDimAmount(0.65f)
        }
        dialog.show()
    }

    private fun showEditGroupLinkDialog(groupLink: com.duorhythm.app.data.model.ContactGroupItem) {
        val activity = activity ?: return
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_add_group_link, null)

        val etTitle = dialogView.findViewById<android.widget.EditText>(R.id.etGroupTitle)
        val etUrl = dialogView.findViewById<android.widget.EditText>(R.id.etGroupUrl)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveGroupLink)

        btnSave?.text = "Update Link"
        etTitle?.setText(groupLink.name)
        etUrl?.setText(groupLink.targetLink)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(dialogView)
            .create()

        btnSave?.setOnClickListener {
            val name = etTitle?.text?.toString()?.trim().orEmpty()
            val link = etUrl?.text?.toString()?.trim().orEmpty()

            if (name.isEmpty()) {
                etTitle?.error = "Please enter a name"
                return@setOnClickListener
            }

            if (link.isEmpty()) {
                etUrl?.error = "Please enter a valid URL (e.g. https://chat.whatsapp.com/...)"
                return@setOnClickListener
            }

            var formattedLink = link
            if (!formattedLink.startsWith("http://", ignoreCase = true) && !formattedLink.startsWith("https://", ignoreCase = true)) {
                formattedLink = "https://$formattedLink"
            }

            if (!android.util.Patterns.WEB_URL.matcher(formattedLink).matches()) {
                etUrl?.error = "Please enter a valid URL (e.g. https://chat.whatsapp.com/...)"
                return@setOnClickListener
            }

            val platformCode = when {
                formattedLink.contains("discord", ignoreCase = true) -> "DISCORD"
                formattedLink.contains("t.me", ignoreCase = true) || formattedLink.contains("telegram", ignoreCase = true) -> "TELEGRAM"
                formattedLink.contains("whatsapp", ignoreCase = true) || formattedLink.contains("wa.me", ignoreCase = true) -> "WHATSAPP"
                formattedLink.contains("m.me", ignoreCase = true) || formattedLink.contains("messenger", ignoreCase = true) -> "MESSENGER"
                else -> groupLink.platform
            }

            val updated = groupLink.copy(
                name = name,
                platform = platformCode,
                targetLink = formattedLink
            )

            CoroutineScope(Dispatchers.IO).launch {
                dbHelper.updateContactGroup(updated)
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    loadStudentContacts()
                    loadWorkContacts()
                    Toast.makeText(activity, "Community group link updated!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            setDimAmount(0.65f)
        }
        dialog.show()
    }

    private fun showAddSubjectDialog() {
        val activity = requireActivity()
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_add_subject, null)
        val etName = dialogView.findViewById<android.widget.EditText>(R.id.etSubjectName)
        val etCode = dialogView.findViewById<android.widget.EditText>(R.id.etSubjectCode)
        val etCredits = dialogView.findViewById<android.widget.EditText>(R.id.etCredits)
        val etInstructor = dialogView.findViewById<android.widget.EditText>(R.id.etInstructor)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveSubject)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(dialogView)
            .create()

        btnSave?.setOnClickListener {
            val name = etName?.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                etName?.error = "Subject name is required"
                return@setOnClickListener
            }
            val code = etCode?.text?.toString()?.trim().orEmpty()
            val credits = etCredits?.text?.toString()?.trim()?.toIntOrNull() ?: 3
            val instructor = etInstructor?.text?.toString()?.trim().orEmpty()

            val subject = StudentSubject(
                name = name,
                code = code,
                colorHex = "#2563EB",
                notes = "",
                credits = credits,
                instructorName = instructor
            )
            val autoTrack = com.duorhythm.app.data.local.pref.AppSettings.isAutoTrackAttendanceEnabled(activity)
            btnSave.isEnabled = false

            CoroutineScope(Dispatchers.IO).launch {
                dbHelper.insertStudentSubject(subject, autoTrackAttendance = autoTrack)
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    loadStudentData()
                    Toast.makeText(activity, "Subject created!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            setDimAmount(0.65f)
        }
        dialog.show()
    }

    private fun saveOrderAttachmentFromUri(uri: Uri) {
        try {
            val fileName = "order_doc_${System.currentTimeMillis()}"
            val attachDir = File(requireContext().filesDir, "attachments").apply { mkdirs() }
            val destFile = File(attachDir, fileName)

            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            pendingOrderAttachmentPath = destFile.absolutePath
            activeOrderDialogBinding?.tvOrderAttachmentPreview?.text = "Attached: ${destFile.name}"
            activeOrderDialogBinding?.tvOrderAttachmentPreview?.setTextColor(0xFF10B981.toInt())
        } catch (_: Exception) {
            Toast.makeText(requireContext(), "Failed to attach file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddWorkOrderDialog() {
        val dialogBinding = DialogAddWorkOrderBinding.inflate(layoutInflater)
        activeOrderDialogBinding = dialogBinding
        pendingOrderAttachmentPath = ""

        val prefs = requireContext().getSharedPreferences("lifesync_prefs", android.content.Context.MODE_PRIVATE)
        val defaultCurr = if (java.util.Locale.getDefault().country.equals("BD", ignoreCase = true)) "BDT" else "USD"
        val currCode = prefs.getString("selected_currency", defaultCurr) ?: defaultCurr
        val rateObj = dbHelper.getAllCurrencyRates().find { it.code == currCode }
        val rate = 1.0
        val symbol = rateObj?.symbol ?: (if (currCode == "BDT") "৳" else "$")

        dialogBinding.etOrderUnitPrice.hint = "Unit Price ($symbol - $currCode)"

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        dialogBinding.etOrderStartDate.setText(today)
        dialogBinding.etOrderEndDate.setText(today)

        val pickDateListener = View.OnClickListener { view ->
            val calendar = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    val cal = java.util.Calendar.getInstance()
                    cal.set(year, month, dayOfMonth)
                    val formatted = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
                    (view as? android.widget.EditText)?.setText(formatted)
                },
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }
        dialogBinding.etOrderStartDate.setOnClickListener(pickDateListener)
        dialogBinding.etOrderEndDate.setOnClickListener(pickDateListener)

        dialogBinding.btnOrderPickAttachment.setOnClickListener {
            pickOrderAttachmentLauncher.launch(arrayOf("*/*"))
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setOnDismissListener { activeOrderDialogBinding = null }
            .create()

        dialogBinding.btnSaveWorkOrder.setOnClickListener {
            val title = dialogBinding.etOrderTitle.text?.toString()?.trim().orEmpty()
            val client = dialogBinding.etOrderClient.text?.toString()?.trim().orEmpty()
            val qtyStr = dialogBinding.etOrderQuantity.text?.toString()?.trim().orEmpty()
            val unitPriceStr = dialogBinding.etOrderUnitPrice.text?.toString()?.trim().orEmpty()
            val startDate = dialogBinding.etOrderStartDate.text?.toString()?.trim().orEmpty()
            val endDate = dialogBinding.etOrderEndDate.text?.toString()?.trim().orEmpty()
            val notes = dialogBinding.etOrderNotes.text?.toString()?.trim().orEmpty()

            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "Order title is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val qty = qtyStr.toIntOrNull() ?: 1
            val enteredUnitPrice = unitPriceStr.toDoubleOrNull() ?: 0.0
            val baseUnitPrice = enteredUnitPrice
            val baseTotalPrice = qty * baseUnitPrice

            val order = WorkOrder(
                title = title,
                clientName = client,
                quantity = qty,
                unitPrice = baseUnitPrice,
                totalPrice = baseTotalPrice,
                startDate = startDate,
                endDate = endDate,
                status = "PENDING",
                notes = notes,
                attachmentPath = pendingOrderAttachmentPath
            )
            val newId = dbHelper.insertWorkOrder(order)
            val savedOrder = if (newId > 0) order.copy(id = newId) else order
            com.duorhythm.app.scheduler.OrderAlarmScheduler.scheduleOrderReminders(requireContext(), savedOrder)
            dialog.dismiss()
            loadWorkData()
            Toast.makeText(requireContext(), "Work order created!", Toast.LENGTH_SHORT).show()
        }

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
    }

    fun loadAcademicIdentity() {
        if (_binding == null) return
        try {
            val context = context ?: return
            val inst = com.duorhythm.app.data.local.pref.UserProfileManager.getInstitutionName(context)
            val faculty = com.duorhythm.app.data.local.pref.UserProfileManager.getAcademicFaculty(context)

            if (inst.isNotBlank()) {
                binding.tvInstitutionName.text = inst
                binding.tvAcademicFaculty.text = if (faculty.isNotBlank()) faculty else "Department / Major not set"
            } else {
                binding.tvInstitutionName.text = "Set Your College / University"
                binding.tvAcademicFaculty.text = if (faculty.isNotBlank()) faculty else "Tap to configure academic institution & major"
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun showEditInstitutionDialog() {
        val context = context ?: return
        val currentInst = com.duorhythm.app.data.local.pref.UserProfileManager.getInstitutionName(context)
        val currentFaculty = com.duorhythm.app.data.local.pref.UserProfileManager.getAcademicFaculty(context)

        val layout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val density = resources.displayMetrics.density
            val pad = (20 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val etInst = com.google.android.material.textfield.TextInputEditText(context).apply {
            hint = "College / University Name"
            setText(currentInst)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#94A3B8"))
            setBackgroundResource(R.drawable.bg_glass_squircle_action)
            val pad12 = (12 * resources.displayMetrics.density).toInt()
            setPadding(pad12, pad12, pad12, pad12)
        }

        val etFaculty = com.google.android.material.textfield.TextInputEditText(context).apply {
            hint = "Department / Major / Semester"
            setText(currentFaculty)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#94A3B8"))
            setBackgroundResource(R.drawable.bg_glass_squircle_action)
            val pad12 = (12 * resources.displayMetrics.density).toInt()
            setPadding(pad12, pad12, pad12, pad12)
            val topMargin = (12 * resources.displayMetrics.density).toInt()
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                this.topMargin = topMargin
            }
            layoutParams = lp
        }

        layout.addView(etInst)
        layout.addView(etFaculty)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Academic Institution")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val newInst = etInst.text?.toString()?.trim().orEmpty()
                val newFaculty = etFaculty.text?.toString()?.trim().orEmpty()
                com.duorhythm.app.data.local.pref.UserProfileManager.setInstitutionName(context, newInst)
                com.duorhythm.app.data.local.pref.UserProfileManager.setAcademicFaculty(context, newFaculty)
                loadAcademicIdentity()
                Toast.makeText(context, "Academic info updated", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Open Settings") { _, _ ->
                startActivity(Intent(context, com.duorhythm.app.ui.settings.SettingsActivity::class.java))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun updateCoursesCollapseState(isCollapsed: Boolean, animate: Boolean) {
        if (_binding == null) return
        if (animate) {
            android.transition.TransitionManager.beginDelayedTransition(
                binding.rootLayout,
                android.transition.AutoTransition().apply { duration = 200 }
            )
        }
        binding.rvEnrolledCourses.visibility = if (isCollapsed) View.GONE else View.VISIBLE
        binding.ivChevronCourses.animate().rotation(if (isCollapsed) -90f else 0f).setDuration(200).start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
