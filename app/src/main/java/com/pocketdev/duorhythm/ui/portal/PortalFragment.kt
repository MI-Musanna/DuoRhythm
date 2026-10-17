package com.pocketdev.duorhythm.ui.portal

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
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.pocketdev.duorhythm.MainActivity
import com.pocketdev.duorhythm.R
import com.pocketdev.duorhythm.data.local.db.DatabaseHelper
import com.pocketdev.duorhythm.data.model.StudentSubject
import com.pocketdev.duorhythm.data.model.WorkOrder
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import com.pocketdev.duorhythm.data.model.SubjectWorkItem
import com.pocketdev.duorhythm.databinding.DialogEditContactBinding
import com.pocketdev.duorhythm.databinding.DialogAddExamBinding
import com.pocketdev.duorhythm.databinding.DialogAddQuizBinding
import com.pocketdev.duorhythm.databinding.DialogAddWorkOrderBinding
import com.pocketdev.duorhythm.databinding.FragmentPortalBinding
import com.pocketdev.duorhythm.util.CountryUtils
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PortalFragment : Fragment() {

    private var _binding: FragmentPortalBinding? = null
    private val binding get() = _binding!!

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var studentSubjectsAdapter: StudentSubjectsAdapter
    private lateinit var workOrdersAdapter: WorkOrdersAdapter
    private lateinit var semesterFinalsAdapter: SubjectItemsAdapter
    private lateinit var classTestsAdapter: SubjectItemsAdapter
    private lateinit var studentContactsAdapter: com.pocketdev.duorhythm.ui.tools.ContactsAdapter
    private lateinit var workContactsAdapter: com.pocketdev.duorhythm.ui.tools.ContactsAdapter

    private var isStudentProfile = true
    private var isStudentContactsTab = true
    private var isWorkContactsTab = true
    private var pendingOrderAttachmentPath = ""
    private var activeOrderDialogBinding: DialogAddWorkOrderBinding? = null
    private var studentContactRoleFilter = "ALL"
    private var workContactRoleFilter = "ALL"

    private val pickOrderAttachmentLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { saveOrderAttachmentFromUri(it) }
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
            setupAdapters()
            setupListeners()
            val rootView = activity?.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)
                ?: (binding.root as? ViewGroup)
            if (rootView != null) {
                com.pocketdev.duorhythm.util.BlurUtil.setupBlur(binding.blurPortalToggle, rootView)
            }
            updateProfileView()
            loadAcademicIdentity()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
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
                    .setMessage("Remove '${subject.name}' and all its homework/exams?")
                    .setPositiveButton("Delete") { _, _ ->
                        dbHelper.deleteStudentSubject(subject.id)
                        loadStudentData()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        binding.rvStudentSubjects.layoutManager = LinearLayoutManager(requireContext())
        binding.rvStudentSubjects.adapter = studentSubjectsAdapter

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
                loadWorkData()
            },
            onDeleteClick = { order ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Work Order")
                    .setMessage("Remove '${order.title}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        dbHelper.deleteWorkOrder(order.id)
                        loadWorkData()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        // Semester Finals
        semesterFinalsAdapter = SubjectItemsAdapter(
            emptyList(),
            onToggleStatus = { item, nextStatus ->
                dbHelper.updateSubjectWorkItemStatus(item.id, nextStatus)
                loadStudentData()
            },
            onDeleteClick = { item ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Exam")
                    .setMessage("Remove '${item.title}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        dbHelper.deleteSubjectWorkItem(item.id)
                        loadStudentData()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onItemClick = { item ->
                showEditExamDialog(item)
            }
        )
        binding.rvSemesterFinals.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSemesterFinals.adapter = semesterFinalsAdapter

        // Class Tests & Quizzes
        classTestsAdapter = SubjectItemsAdapter(
            emptyList(),
            onToggleStatus = { item, nextStatus ->
                dbHelper.updateSubjectWorkItemStatus(item.id, nextStatus)
                loadStudentData()
            },
            onDeleteClick = { item ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Class Test")
                    .setMessage("Remove '${item.title}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        dbHelper.deleteSubjectWorkItem(item.id)
                        loadStudentData()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onItemClick = { item ->
                showEditExamDialog(item)
            }
        )
        binding.rvClassTests.layoutManager = LinearLayoutManager(requireContext())
        binding.rvClassTests.adapter = classTestsAdapter

        // Student Contacts
        studentContactsAdapter = com.pocketdev.duorhythm.ui.tools.ContactsAdapter(
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
        workContactsAdapter = com.pocketdev.duorhythm.ui.tools.ContactsAdapter(
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
        binding.rvWorkContacts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvWorkContacts.adapter = workContactsAdapter
    }

    private fun setupListeners() {
        binding.togglePortalProfile.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isStudentProfile = (checkedId == R.id.btnProfileStudent)
                (activity as? MainActivity)?.setStudentProfile(isStudentProfile)
                updateProfileView()
            }
        }
        binding.toggleStudentSegment.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                updateStudentSegmentView(checkedId)
            }
        }
        binding.btnAddSubject.setOnClickListener { showAddSubjectDialog() }
        binding.btnEnrollFirstSubject.setOnClickListener { showAddSubjectDialog() }
        binding.btnEmptyAddSubject.setOnClickListener { showAddSubjectDialog() }
        binding.btnAddWorkOrder.setOnClickListener { showAddWorkOrderDialog() }
        binding.btnEmptyAddWorkOrder.setOnClickListener { showAddWorkOrderDialog() }
        binding.btnStudentScheduleShortcut.setOnClickListener {
            (activity as? com.pocketdev.duorhythm.MainActivity)?.selectNavTab(R.id.nav_schedule)
        }

        binding.cardAcademicInstitution.setOnClickListener {
            showEditInstitutionDialog()
        }

        binding.btnStudentAddFinalExam.setOnClickListener { showAddExamDialog("SEMESTER_FINAL") }
        binding.btnStudentAddClassTest.setOnClickListener { showAddQuizDialog() }

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

        binding.toggleWorkDirectoryTab.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isWorkContactsTab = checkedId == R.id.tabWorkContacts
                val emeraldColor = ColorStateList.valueOf(Color.parseColor("#10B981"))
                val transparentColor = ColorStateList.valueOf(Color.TRANSPARENT)
                if (isWorkContactsTab) {
                    binding.tabWorkContacts.backgroundTintList = emeraldColor
                    binding.tabWorkContacts.setTextColor(Color.WHITE)
                    binding.tabWorkGroupLinks.backgroundTintList = transparentColor
                    binding.tabWorkGroupLinks.setTextColor(Color.parseColor("#64748B"))
                    binding.btnWorkAddContact.text = "+ Contact"
                } else {
                    binding.tabWorkGroupLinks.backgroundTintList = emeraldColor
                    binding.tabWorkGroupLinks.setTextColor(Color.WHITE)
                    binding.tabWorkContacts.backgroundTintList = transparentColor
                    binding.tabWorkContacts.setTextColor(Color.parseColor("#64748B"))
                    binding.btnWorkAddContact.text = "+ Add Link"
                }
                loadWorkContacts()
            }
        }

        binding.btnStudentAddContact.setOnClickListener {
            if (isStudentContactsTab) showAddContactDialog(true) else showAddGroupLinkDialog(true)
        }
        binding.btnAddContact.setOnClickListener {
            showAddContactDialog(isStudentProfile)
        }
        binding.btnAddLink.setOnClickListener {
            showAddGroupLinkDialog(isStudentProfile)
        }
        binding.btnWorkAddContact.setOnClickListener {
            if (isWorkContactsTab) showAddContactDialog(false) else showAddGroupLinkDialog(false)
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
        if (_binding == null || !isAdded || !::dbHelper.isInitialized) return
        try {
            val workEnabled = dbHelper.isWorkPortalEnabled()
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
                binding.layoutWorkPortal.visibility = View.GONE
                val currentSegment = if (binding.toggleStudentSegment.checkedButtonId != View.NO_ID) {
                    binding.toggleStudentSegment.checkedButtonId
                } else {
                    R.id.btnSegmentCourses
                }
                updateStudentSegmentView(currentSegment)
                loadStudentData()
            } else {
                binding.layoutStudentPortal.visibility = View.GONE
                binding.layoutWorkPortal.visibility = View.VISIBLE
                loadWorkData()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
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
        studentSubjectsAdapter.updateList(subjects, attendanceMap, progressMap)
        if (subjects.isEmpty()) {
            binding.rvStudentSubjects.visibility = View.GONE
            binding.layoutEmptyStudentSubjects.visibility = View.VISIBLE
        } else {
            binding.rvStudentSubjects.visibility = View.VISIBLE
            binding.layoutEmptyStudentSubjects.visibility = View.GONE
        }

        val totalAssessments = subjects.sumOf { dbHelper.getItemsForSubject(it.id).size }
        binding.tvStudentTotalSubjects.text = "${subjects.size} Courses"
        binding.tvStudentTotalAssessments.text = "$totalAssessments Items"

        val totalAttended = allAttendance.sumOf { it.attendedClasses }
        val totalClasses = allAttendance.sumOf { it.totalClasses }
        if (totalClasses > 0) {
            val avgPct = (totalAttended * 100) / totalClasses
            binding.tvStudentAttendanceTarget.text = "$avgPct% Target"
        } else {
            binding.tvStudentAttendanceTarget.text = "0% Target"
        }

        // Load Semester Finals (SEMESTER_FINAL or MIDTERM)
        val allExams = dbHelper.getAllExams().distinctBy { it.first.id }
        val finalsItems = allExams.filter { it.first.examType == "SEMESTER_FINAL" || it.first.examType == "MIDTERM" }.map { it.first }
        semesterFinalsAdapter.updateList(finalsItems)
        binding.tvFinalExamsCountBadge.text = "${finalsItems.size} Scheduled"
        if (finalsItems.isEmpty()) {
            binding.rvSemesterFinals.visibility = View.GONE
            binding.tvEmptySemesterFinals.visibility = View.VISIBLE
        } else {
            binding.rvSemesterFinals.visibility = View.VISIBLE
            binding.tvEmptySemesterFinals.visibility = View.GONE
        }

        // Load Class Tests & Quizzes (CLASS_TEST_QUIZ or untagged exams)
        val testItems = allExams.filter { it.first.examType == "CLASS_TEST_QUIZ" || (it.first.examType != "SEMESTER_FINAL" && it.first.examType != "MIDTERM") }.map { it.first }
        classTestsAdapter.updateList(testItems)
        binding.tvClassTestsCountBadge.text = "${testItems.size} Active"
        if (testItems.isEmpty()) {
            binding.rvClassTests.visibility = View.GONE
            binding.tvEmptyClassTests.visibility = View.VISIBLE
        } else {
            binding.rvClassTests.visibility = View.VISIBLE
            binding.tvEmptyClassTests.visibility = View.GONE
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

    private fun loadWorkData() {
        val prefs = requireContext().getSharedPreferences("lifesync_prefs", android.content.Context.MODE_PRIVATE)
        val defaultCurr = if (java.util.Locale.getDefault().country.equals("BD", ignoreCase = true)) "BDT" else "USD"
        val currCode = prefs.getString("selected_currency", defaultCurr) ?: defaultCurr
        val rateObj = dbHelper.getAllCurrencyRates().find { it.code == currCode }
        val rate = rateObj?.rate ?: 1.0
        val symbol = rateObj?.symbol ?: "$"

        val orders = dbHelper.getAllWorkOrders()
        workOrdersAdapter.updateList(orders, symbol, rate)
        if (orders.isEmpty()) {
            binding.rvWorkOrders.visibility = View.GONE
            binding.layoutEmptyWorkOrders.visibility = View.VISIBLE
        } else {
            binding.rvWorkOrders.visibility = View.VISIBLE
            binding.layoutEmptyWorkOrders.visibility = View.GONE
        }

        val totalOrders = orders.size
        val pendingOrders = orders.count { it.status != "COMPLETED" }
        val totalRevenue = orders.sumOf { it.totalPrice } * rate

        binding.tvWorkTotalOrders.text = "$totalOrders Orders"
        binding.tvWorkPendingOrders.text = "$pendingOrders Active"
        binding.tvWorkTotalRevenue.text = String.format(Locale.US, "%s%.2f", symbol, totalRevenue)

        loadWorkContacts()
    }

    private fun loadWorkContacts() {
        val all = dbHelper.getContactsForPortal(isStudent = false, roleFilter = workContactRoleFilter)
        val contacts = if (isWorkContactsTab) {
            all.filter { !it.type.equals("GROUP", ignoreCase = true) }
        } else {
            all.filter { it.type.equals("GROUP", ignoreCase = true) }
        }
        workContactsAdapter.updateList(contacts)
        if (contacts.isEmpty()) {
            binding.rvWorkContacts.visibility = View.GONE
            binding.tvEmptyWorkContacts.visibility = View.VISIBLE
            binding.tvEmptyWorkContacts.text = if (isWorkContactsTab) {
                "No client or partner contacts recorded yet. Tap + Contact to save new work connections!"
            } else {
                "No community or client channel links saved yet. Tap + Add Link to save Discord, Telegram, or WhatsApp channels."
            }
        } else {
            binding.rvWorkContacts.visibility = View.VISIBLE
            binding.tvEmptyWorkContacts.visibility = View.GONE
        }
    }

    private fun showAddExamDialog(preselectedExamType: String) {
        if (preselectedExamType == "CLASS_TEST_QUIZ") {
            showAddQuizDialog()
            return
        }
        val subjects = dbHelper.getAllStudentSubjects()
        if (subjects.isEmpty()) {
            Toast.makeText(requireContext(), "Please add at least one subject/course first!", Toast.LENGTH_LONG).show()
            showAddSubjectDialog()
            return
        }

        val dialogBinding = DialogAddExamBinding.inflate(layoutInflater)
        val isFinal = preselectedExamType == "SEMESTER_FINAL"

        if (isFinal) {
            dialogBinding.tvExamDialogTitle.text = "Schedule Semester Final"
            dialogBinding.tvExamDialogSubtitle.text = "Assign course, date, room, and syllabus for this final exam."
            dialogBinding.tvExamTypeBadge.text = "SEMESTER FINAL"
            dialogBinding.tvExamTypeBadge.setTextColor(Color.parseColor("#38BDF8"))
            dialogBinding.tvExamTypeBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#220284C7"))
            dialogBinding.tilExamTitle.hint = "Exam Title (e.g. Final Examination)"
            dialogBinding.etExamTitle.hint = "Exam Title (e.g. Final Examination)"
            dialogBinding.etExamTitle.setText("Final Examination")
        } else {
            dialogBinding.tvExamDialogTitle.text = "Schedule Class Test / Quiz"
            dialogBinding.tvExamDialogSubtitle.text = "Assign course, date, room, and syllabus for this class test/quiz."
            dialogBinding.tvExamTypeBadge.text = "CLASS TEST / QUIZ"
            dialogBinding.tvExamTypeBadge.setTextColor(Color.parseColor("#FBBF24"))
            dialogBinding.tvExamTypeBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#22F59E0B"))
            dialogBinding.tilExamTitle.hint = "e.g. Class Test 1, Quiz 2"
            dialogBinding.etExamTitle.hint = "e.g. Class Test 1, Quiz 2"
            dialogBinding.etExamTitle.setText("")
        }

        val subjectNames = subjects.map { if (it.code.isNotEmpty()) "${it.code} - ${it.name}" else it.name }
        dialogBinding.spExamSubject.adapter =
            android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, subjectNames)

        val cal = java.util.Calendar.getInstance()
        val defaultDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        dialogBinding.etExamDate.setText(defaultDate)

        val openDatePicker = View.OnClickListener {
            val now = java.util.Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    val selected = java.util.Calendar.getInstance()
                    selected.set(year, month, dayOfMonth)
                    dialogBinding.etExamDate.setText(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selected.time))
                },
                now.get(java.util.Calendar.YEAR),
                now.get(java.util.Calendar.MONTH),
                now.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }
        dialogBinding.etExamDate.setOnClickListener(openDatePicker)

        val openTimePicker = View.OnClickListener {
            val now = java.util.Calendar.getInstance()
            TimePickerDialog(
                requireContext(),
                { _, hourOfDay, minute ->
                    val isPm = hourOfDay >= 12
                    val displayHour = when {
                        hourOfDay == 0 -> 12
                        hourOfDay > 12 -> hourOfDay - 12
                        else -> hourOfDay
                    }
                    val amPm = if (isPm) "PM" else "AM"
                    dialogBinding.etExamTime.setText(String.format(Locale.US, "%02d:%02d %s", displayHour, minute, amPm))
                },
                now.get(java.util.Calendar.HOUR_OF_DAY),
                now.get(java.util.Calendar.MINUTE),
                false
            ).show()
        }
        dialogBinding.etExamTime.setOnClickListener(openTimePicker)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnSaveExam.setOnClickListener {
            val title = dialogBinding.etExamTitle.text?.toString()?.trim().orEmpty()
            val date = dialogBinding.etExamDate.text?.toString()?.trim().orEmpty()
            val time = dialogBinding.etExamTime.text?.toString()?.trim().orEmpty()
            val room = dialogBinding.etExamRoom.text?.toString()?.trim().orEmpty()
            val notes = dialogBinding.etExamNotes.text?.toString()?.trim().orEmpty()

            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "Exam title is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (date.isEmpty()) {
                Toast.makeText(requireContext(), "Exam date is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialogBinding.btnSaveExam.isEnabled = false

            val selectedSubject = subjects[dialogBinding.spExamSubject.selectedItemPosition]
            val descParts = mutableListOf<String>()
            if (time.isNotEmpty()) descParts.add("Time: $time")
            if (room.isNotEmpty()) descParts.add("Room: $room")
            if (notes.isNotEmpty()) descParts.add("Syllabus: $notes")
            val description = descParts.joinToString(" • ")

            val examItem = SubjectWorkItem(
                subjectId = selectedSubject.id,
                itemType = "EXAM",
                examType = preselectedExamType,
                title = title,
                description = description,
                startDate = date,
                endDate = date,
                status = "PENDING"
            )

            dbHelper.insertSubjectWorkItem(examItem)
            dialog.dismiss()
            loadStudentData()
            Toast.makeText(requireContext(), "Exam scheduled successfully!", Toast.LENGTH_SHORT).show()
        }

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
    }

    private fun showAddQuizDialog() {
        val subjects = dbHelper.getAllStudentSubjects()
        if (subjects.isEmpty()) {
            Toast.makeText(requireContext(), "Please add at least one subject/course first!", Toast.LENGTH_LONG).show()
            showAddSubjectDialog()
            return
        }

        val dialogBinding = DialogAddQuizBinding.inflate(layoutInflater)
        val subjectNames = subjects.map { if (it.code.isNotEmpty()) "${it.code} - ${it.name}" else it.name }
        dialogBinding.spQuizSubject.adapter =
            android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, subjectNames)

        val cal = java.util.Calendar.getInstance()
        val defaultDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        dialogBinding.etQuizDate.setText(defaultDate)

        val openDatePicker = View.OnClickListener {
            val now = java.util.Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    val selected = java.util.Calendar.getInstance()
                    selected.set(year, month, dayOfMonth)
                    dialogBinding.etQuizDate.setText(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selected.time))
                },
                now.get(java.util.Calendar.YEAR),
                now.get(java.util.Calendar.MONTH),
                now.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }
        dialogBinding.etQuizDate.setOnClickListener(openDatePicker)

        val openTimePicker = View.OnClickListener {
            val now = java.util.Calendar.getInstance()
            TimePickerDialog(
                requireContext(),
                { _, hourOfDay, minute ->
                    val isPm = hourOfDay >= 12
                    val displayHour = when {
                        hourOfDay == 0 -> 12
                        hourOfDay > 12 -> hourOfDay - 12
                        else -> hourOfDay
                    }
                    val amPm = if (isPm) "PM" else "AM"
                    dialogBinding.etQuizTime.setText(String.format(Locale.US, "%02d:%02d %s", displayHour, minute, amPm))
                },
                now.get(java.util.Calendar.HOUR_OF_DAY),
                now.get(java.util.Calendar.MINUTE),
                false
            ).show()
        }
        dialogBinding.etQuizTime.setOnClickListener(openTimePicker)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnSaveQuiz.setOnClickListener {
            val title = dialogBinding.etQuizTitle.text?.toString()?.trim().orEmpty()
            val date = dialogBinding.etQuizDate.text?.toString()?.trim().orEmpty()
            val time = dialogBinding.etQuizTime.text?.toString()?.trim().orEmpty()
            val room = dialogBinding.etQuizRoom.text?.toString()?.trim().orEmpty()
            val notes = dialogBinding.etQuizNotes.text?.toString()?.trim().orEmpty()

            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "Quiz title is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (date.isEmpty()) {
                Toast.makeText(requireContext(), "Quiz date is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Immediately disable save button to prevent double-tap insertion
            dialogBinding.btnSaveQuiz.isEnabled = false

            val selectedSubject = subjects[dialogBinding.spQuizSubject.selectedItemPosition]
            val descParts = mutableListOf<String>()
            if (time.isNotEmpty()) descParts.add("Time: $time")
            if (room.isNotEmpty()) descParts.add("Room: $room")
            if (notes.isNotEmpty()) descParts.add("Syllabus: $notes")
            val description = descParts.joinToString(" • ")

            val quizItem = SubjectWorkItem(
                subjectId = selectedSubject.id,
                itemType = "EXAM",
                examType = "CLASS_TEST_QUIZ",
                title = title,
                description = description,
                startDate = date,
                endDate = date,
                status = "PENDING"
            )

            dbHelper.insertSubjectWorkItem(quizItem)
            dialog.dismiss()
            loadStudentData()
            Toast.makeText(requireContext(), "Class Test / Quiz scheduled successfully!", Toast.LENGTH_SHORT).show()
        }

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
    }

    private fun showEditExamDialog(item: SubjectWorkItem) {
        val subjects = dbHelper.getAllStudentSubjects()
        val dialogBinding = DialogAddExamBinding.inflate(layoutInflater)
        val isFinal = item.examType == "SEMESTER_FINAL" || item.examType == "MIDTERM"

        if (isFinal) {
            dialogBinding.tvExamDialogTitle.text = "Edit Final Exam"
            dialogBinding.tvExamDialogSubtitle.text = "Modify title, dates, and details for this exam."
            dialogBinding.tvExamTypeBadge.text = if (item.examType == "MIDTERM") "MIDTERM EXAM" else "SEMESTER FINAL"
            dialogBinding.tvExamTypeBadge.setTextColor(Color.parseColor("#38BDF8"))
            dialogBinding.tvExamTypeBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#220284C7"))
            dialogBinding.tilExamTitle.hint = "Exam Title"
            dialogBinding.btnSaveExam.text = "Save Changes"
        } else {
            dialogBinding.tvExamDialogTitle.text = "Edit CT / Quiz"
            dialogBinding.tvExamDialogSubtitle.text = "Update date, title, and syllabus notes."
            dialogBinding.tvExamTypeBadge.text = "CLASS TEST / QUIZ"
            dialogBinding.tvExamTypeBadge.setTextColor(Color.parseColor("#FBBF24"))
            dialogBinding.tvExamTypeBadge.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#22F59E0B"))
            dialogBinding.tilExamTitle.hint = "e.g. Class Test 1, Quiz 2"
            dialogBinding.btnSaveExam.text = "Save Changes"
        }

        dialogBinding.etExamTitle.setText(item.title)
        dialogBinding.etExamDate.setText(item.startDate)

        var timeVal = ""
        var roomVal = ""
        var notesVal = ""
        if (item.description.contains(" • ")) {
            val parts = item.description.split(" • ")
            for (p in parts) {
                when {
                    p.startsWith("Time: ") -> timeVal = p.removePrefix("Time: ").trim()
                    p.startsWith("Room: ") -> roomVal = p.removePrefix("Room: ").trim()
                    p.startsWith("Syllabus: ") -> notesVal = p.removePrefix("Syllabus: ").trim()
                    else -> if (notesVal.isEmpty()) notesVal = p.trim() else notesVal += " $p"
                }
            }
        } else if (item.description.isNotEmpty()) {
            notesVal = item.description
        }
        dialogBinding.etExamTime.setText(timeVal)
        dialogBinding.etExamRoom.setText(roomVal)
        dialogBinding.etExamNotes.setText(notesVal)

        val subjectNames = subjects.map { if (it.code.isNotEmpty()) "${it.code} - ${it.name}" else it.name }
        dialogBinding.spExamSubject.adapter =
            android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, subjectNames)
        val subIdx = subjects.indexOfFirst { it.id == item.subjectId }.coerceAtLeast(0)
        dialogBinding.spExamSubject.setSelection(subIdx)

        val openDatePicker = View.OnClickListener {
            val now = java.util.Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    val selected = java.util.Calendar.getInstance()
                    selected.set(year, month, dayOfMonth)
                    dialogBinding.etExamDate.setText(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selected.time))
                },
                now.get(java.util.Calendar.YEAR),
                now.get(java.util.Calendar.MONTH),
                now.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }
        dialogBinding.etExamDate.setOnClickListener(openDatePicker)

        val openTimePicker = View.OnClickListener {
            val now = java.util.Calendar.getInstance()
            TimePickerDialog(
                requireContext(),
                { _, hourOfDay, minute ->
                    val isPm = hourOfDay >= 12
                    val displayHour = when {
                        hourOfDay == 0 -> 12
                        hourOfDay > 12 -> hourOfDay - 12
                        else -> hourOfDay
                    }
                    val amPm = if (isPm) "PM" else "AM"
                    dialogBinding.etExamTime.setText(String.format(Locale.US, "%02d:%02d %s", displayHour, minute, amPm))
                },
                now.get(java.util.Calendar.HOUR_OF_DAY),
                now.get(java.util.Calendar.MINUTE),
                false
            ).show()
        }
        dialogBinding.etExamTime.setOnClickListener(openTimePicker)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnSaveExam.setOnClickListener {
            val title = dialogBinding.etExamTitle.text?.toString()?.trim().orEmpty()
            val date = dialogBinding.etExamDate.text?.toString()?.trim().orEmpty()
            val time = dialogBinding.etExamTime.text?.toString()?.trim().orEmpty()
            val room = dialogBinding.etExamRoom.text?.toString()?.trim().orEmpty()
            val notes = dialogBinding.etExamNotes.text?.toString()?.trim().orEmpty()

            if (title.isEmpty()) {
                Toast.makeText(requireContext(), "Exam title is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (date.isEmpty()) {
                Toast.makeText(requireContext(), "Exam date is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialogBinding.btnSaveExam.isEnabled = false

            val selectedSubject = if (subjects.isNotEmpty() && dialogBinding.spExamSubject.selectedItemPosition in subjects.indices) {
                subjects[dialogBinding.spExamSubject.selectedItemPosition]
            } else null

            val descParts = mutableListOf<String>()
            if (time.isNotEmpty()) descParts.add("Time: $time")
            if (room.isNotEmpty()) descParts.add("Room: $room")
            if (notes.isNotEmpty()) descParts.add("Syllabus: $notes")
            val description = descParts.joinToString(" • ")

            val updated = item.copy(
                subjectId = selectedSubject?.id ?: item.subjectId,
                title = title,
                description = description,
                startDate = date,
                endDate = date
            )
            dbHelper.updateSubjectWorkItem(updated)
            dialog.dismiss()
            loadStudentData()
            val label = if (isFinal) "Final exam updated!" else "CT/Quiz updated!"
            Toast.makeText(requireContext(), label, Toast.LENGTH_SHORT).show()
        }

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
    }

    private fun showAddContactDialog(isStudentContext: Boolean) {
        val activity = requireActivity()
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_add_contact, null)

        val etName = dialogView.findViewById<android.widget.EditText>(R.id.etContactName)
        val etPhone = dialogView.findViewById<android.widget.EditText>(R.id.etContactPhone) ?: dialogView.findViewById(R.id.etPhoneNumber)
        val etRole = dialogView.findViewById<android.widget.EditText>(R.id.etContactRole)
        val etNotes = dialogView.findViewById<android.widget.EditText>(R.id.etContactNotes)
        val etCountry = dialogView.findViewById<android.widget.EditText>(R.id.etContactCountryCode)
        val spScope = dialogView.findViewById<android.widget.Spinner>(R.id.spContactScope)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveContact) ?: dialogView.findViewById(R.id.btnSave)

        val scopes = listOf("STUDENT_ONLY", "WORK_ONLY", "SHARED")
        val scopeDisplay = listOf("Student Portal", "Work Portal", "Both / Shared")
        spScope?.adapter =
            android.widget.ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, scopeDisplay)
        val defaultScopeIdx = if (isStudentContext) 0 else 1
        spScope?.setSelection(defaultScopeIdx)

        val defaultCountryCode = CountryUtils.getDeviceCountryCode(activity)
        etCountry?.setText(defaultCountryCode)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(dialogView)
            .create()

        btnSave?.setOnClickListener {
            val name = etName?.text?.toString()?.trim().orEmpty()
            val countryCode = etCountry?.text?.toString()?.trim().orEmpty().ifEmpty { defaultCountryCode }
            val phoneRaw = etPhone?.text?.toString()?.trim().orEmpty()
            val cleanPhone = CountryUtils.getNormalizedInternationalNumber(phoneRaw, countryCode)
            val customRole = etRole?.text?.toString()?.trim().orEmpty().ifEmpty { "Contact" }
            val notes = etNotes?.text?.toString()?.trim().orEmpty()
            val scopePos = spScope?.selectedItemPosition ?: defaultScopeIdx
            val scope = scopes.getOrElse(scopePos) { if (isStudentContext) "STUDENT_ONLY" else "WORK_ONLY" }

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

            val item = com.pocketdev.duorhythm.data.model.ContactGroupItem(
                name = name,
                type = "CONTACT",
                platform = "PHONE",
                targetLink = cleanPhone,
                notes = notes,
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

    private fun showEditContactDialog(contact: com.pocketdev.duorhythm.data.model.ContactGroupItem) {
        try {
            val ctx = context ?: return
            val dialogBinding = DialogEditContactBinding.inflate(LayoutInflater.from(ctx))
            val dialogView = dialogBinding.root

            val etName = dialogBinding.etContactName ?: dialogView.findViewById(R.id.etContactName)
            val etPhone = dialogBinding.etContactPhone ?: dialogView.findViewById(R.id.etPhoneNumber) ?: dialogView.findViewById(R.id.etContactPhone)
            val etRole = dialogBinding.etContactRole ?: dialogView.findViewById(R.id.etContactRole)
            val etNotes = dialogBinding.etContactNotes ?: dialogView.findViewById(R.id.etContactNotes)
            val etCountry = dialogBinding.etContactCountryCode ?: dialogView.findViewById(R.id.etContactCountryCode)
            val spScope = dialogBinding.spContactScope ?: dialogView.findViewById(R.id.spContactScope)
            val tilPhone = dialogBinding.tilContactPhone ?: dialogView.findViewById(R.id.tilContactPhone)
            val btnSave = dialogBinding.btnSaveContact ?: dialogView.findViewById(R.id.btnSave) ?: dialogView.findViewById(R.id.btnSaveContact)

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

    private fun showAddGroupLinkDialog(isStudentContext: Boolean) {
        val activity = requireActivity()
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_add_group_link, null)

        val etName = dialogView.findViewById<android.widget.EditText>(R.id.etGroupName) ?: dialogView.findViewById(R.id.etGroupTitle)
        val etLink = dialogView.findViewById<android.widget.EditText>(R.id.etGroupLink) ?: dialogView.findViewById(R.id.etGroupUrl)
        val etNotes = dialogView.findViewById<android.widget.EditText>(R.id.etGroupNotes)
        val spPlatform = dialogView.findViewById<android.widget.Spinner>(R.id.spGroupPlatform)
        val spScope = dialogView.findViewById<android.widget.Spinner>(R.id.spGroupScope)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveGroupLink) ?: dialogView.findViewById(R.id.btnSave)

        val platforms = listOf("Discord", "Telegram", "WhatsApp Group", "Messenger", "Other / Web")
        spPlatform?.adapter =
            android.widget.ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, platforms)

        val scopes = listOf("STUDENT_ONLY", "WORK_ONLY", "SHARED")
        val scopeDisplay = listOf("Student Portal", "Work Portal", "Both / Shared")
        spScope?.adapter =
            android.widget.ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, scopeDisplay)
        val defaultScopeIdx = if (isStudentContext) 0 else 1
        spScope?.setSelection(defaultScopeIdx)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(dialogView)
            .create()

        btnSave?.setOnClickListener {
            val name = etName?.text?.toString()?.trim().orEmpty()
            val link = etLink?.text?.toString()?.trim().orEmpty()
            val notes = etNotes?.text?.toString()?.trim().orEmpty()
            val platformSelected = platforms.getOrElse(spPlatform?.selectedItemPosition ?: 0) { "Other / Web" }
            val scope = scopes.getOrElse(spScope?.selectedItemPosition ?: defaultScopeIdx) { if (isStudentContext) "STUDENT_ONLY" else "WORK_ONLY" }

            if (name.isEmpty()) {
                etName?.error = "Please enter a name"
                return@setOnClickListener
            }

            if (link.isEmpty()) {
                etLink?.error = "Please enter a valid URL (e.g. https://chat.whatsapp.com/...)"
                return@setOnClickListener
            }

            var formattedLink = link
            if (!formattedLink.startsWith("http://", ignoreCase = true) && !formattedLink.startsWith("https://", ignoreCase = true)) {
                formattedLink = "https://$formattedLink"
            }

            if (!android.util.Patterns.WEB_URL.matcher(formattedLink).matches()) {
                etLink?.error = "Please enter a valid URL (e.g. https://chat.whatsapp.com/...)"
                return@setOnClickListener
            }

            val platformCode = when {
                formattedLink.contains("discord", ignoreCase = true) -> "DISCORD"
                formattedLink.contains("t.me", ignoreCase = true) || formattedLink.contains("telegram", ignoreCase = true) -> "TELEGRAM"
                formattedLink.contains("whatsapp", ignoreCase = true) || formattedLink.contains("wa.me", ignoreCase = true) -> "WHATSAPP"
                formattedLink.contains("m.me", ignoreCase = true) || formattedLink.contains("messenger", ignoreCase = true) -> "MESSENGER"
                spPlatform != null -> {
                    when (platformSelected) {
                        "Discord" -> "DISCORD"
                        "Telegram" -> "TELEGRAM"
                        "WhatsApp Group" -> "WHATSAPP"
                        "Messenger" -> "MESSENGER"
                        else -> "OTHER"
                    }
                }
                else -> "OTHER"
            }

            btnSave?.isEnabled = false

            val item = com.pocketdev.duorhythm.data.model.ContactGroupItem(
                name = name,
                type = "GROUP",
                platform = platformCode,
                targetLink = formattedLink,
                notes = notes,
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

    private fun showEditGroupLinkDialog(groupLink: com.pocketdev.duorhythm.data.model.ContactGroupItem) {
        val activity = activity ?: return
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_add_group_link, null)

        val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tvGroupLinkTitle)
        val etName = dialogView.findViewById<android.widget.EditText>(R.id.etGroupName) ?: dialogView.findViewById(R.id.etGroupTitle)
        val etLink = dialogView.findViewById<android.widget.EditText>(R.id.etGroupLink) ?: dialogView.findViewById(R.id.etGroupUrl)
        val etNotes = dialogView.findViewById<android.widget.EditText>(R.id.etGroupNotes)
        val spPlatform = dialogView.findViewById<android.widget.Spinner>(R.id.spGroupPlatform)
        val spScope = dialogView.findViewById<android.widget.Spinner>(R.id.spGroupScope)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveGroupLink) ?: dialogView.findViewById(R.id.btnSave)

        tvTitle?.text = "Edit Community / Group Link"
        btnSave?.text = "Update Group Link"

        val platforms = listOf("Discord", "Telegram", "WhatsApp Group", "Messenger", "Other / Web")
        spPlatform?.adapter =
            android.widget.ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, platforms)

        val platformIdx = when (groupLink.platform.uppercase()) {
            "DISCORD" -> 0
            "TELEGRAM" -> 1
            "WHATSAPP" -> 2
            "MESSENGER" -> 3
            else -> 4
        }
        spPlatform?.setSelection(platformIdx)

        val scopes = listOf("STUDENT_ONLY", "WORK_ONLY", "SHARED")
        val scopeDisplay = listOf("Student Portal", "Work Portal", "Both / Shared")
        spScope?.adapter =
            android.widget.ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, scopeDisplay)
        val currentScopeIdx = when (groupLink.scope) {
            "STUDENT_ONLY" -> 0
            "WORK_ONLY" -> 1
            else -> 2
        }
        spScope?.setSelection(currentScopeIdx)

        etName?.setText(groupLink.name)
        etLink?.setText(groupLink.targetLink)
        etNotes?.setText(groupLink.notes)

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(dialogView)
            .create()

        btnSave?.setOnClickListener {
            val name = etName?.text?.toString()?.trim().orEmpty()
            val link = etLink?.text?.toString()?.trim().orEmpty()
            val notes = etNotes?.text?.toString()?.trim().orEmpty()
            val platformSelected = platforms.getOrElse(spPlatform?.selectedItemPosition ?: 0) { "Other / Web" }
            val scopeSelected = scopes.getOrElse(spScope?.selectedItemPosition ?: currentScopeIdx) { "SHARED" }

            if (name.isEmpty()) {
                etName?.error = "Please enter a name"
                return@setOnClickListener
            } else {
                etName?.error = null
            }

            if (link.isEmpty()) {
                etLink?.error = "Please enter a valid URL (e.g. https://chat.whatsapp.com/...)"
                return@setOnClickListener
            }

            var formattedLink = link
            if (!formattedLink.startsWith("http://", ignoreCase = true) && !formattedLink.startsWith("https://", ignoreCase = true)) {
                formattedLink = "https://$formattedLink"
            }

            if (!android.util.Patterns.WEB_URL.matcher(formattedLink).matches()) {
                etLink?.error = "Please enter a valid URL (e.g. https://chat.whatsapp.com/...)"
                return@setOnClickListener
            } else {
                etLink?.error = null
            }

            val platformCode = when (platformSelected) {
                "Discord" -> "DISCORD"
                "Telegram" -> "TELEGRAM"
                "WhatsApp Group" -> "WHATSAPP"
                "Messenger" -> "MESSENGER"
                else -> "OTHER"
            }

            val updated = groupLink.copy(
                name = name,
                platform = platformCode,
                targetLink = formattedLink,
                notes = notes,
                scope = scopeSelected
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
        val etCredits = dialogView.findViewById<android.widget.EditText>(R.id.etCredits) ?: dialogView.findViewById<android.widget.EditText>(R.id.etSubjectCredits)
        val etInstructor = dialogView.findViewById<android.widget.EditText>(R.id.etInstructor) ?: dialogView.findViewById<android.widget.EditText>(R.id.etSubjectInstructor)
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
            val autoTrack = com.pocketdev.duorhythm.data.local.pref.AppSettings.isAutoTrackAttendanceEnabled(activity)
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
            pickOrderAttachmentLauncher.launch("*/*")
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
            dbHelper.insertWorkOrder(order)
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
            val inst = com.pocketdev.duorhythm.data.local.pref.UserProfileManager.getInstitutionName(context)
            val faculty = com.pocketdev.duorhythm.data.local.pref.UserProfileManager.getAcademicFaculty(context)

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
        val currentInst = com.pocketdev.duorhythm.data.local.pref.UserProfileManager.getInstitutionName(context)
        val currentFaculty = com.pocketdev.duorhythm.data.local.pref.UserProfileManager.getAcademicFaculty(context)

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
                com.pocketdev.duorhythm.data.local.pref.UserProfileManager.setInstitutionName(context, newInst)
                com.pocketdev.duorhythm.data.local.pref.UserProfileManager.setAcademicFaculty(context, newFaculty)
                loadAcademicIdentity()
                Toast.makeText(context, "Academic info updated", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Open Settings") { _, _ ->
                startActivity(Intent(context, com.pocketdev.duorhythm.ui.settings.SettingsActivity::class.java))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
