package com.duorhythm.app.ui.work

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.duorhythm.app.MainActivity
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.data.model.ClientOrder
import com.duorhythm.app.databinding.DialogAddWorkOrderBinding
import com.duorhythm.app.databinding.FragmentWorkPortalBinding
import com.duorhythm.app.ui.tools.ContactsAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WorkPortalFragment : Fragment() {

    private var _binding: FragmentWorkPortalBinding? = null
    private val binding get() = _binding!!

    private lateinit var workContactsAdapter: ContactsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkPortalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        setupListeners()
        loadOrdersAndStats()
        loadWorkContacts()
    }

    override fun onResume() {
        super.onResume()
        loadOrdersAndStats()
        loadWorkContacts()
    }

    private fun setupRecyclerViews() {
        binding.rvClientOrders.layoutManager = LinearLayoutManager(requireContext())
        binding.rvWorkContacts.layoutManager = LinearLayoutManager(requireContext())

        workContactsAdapter = ContactsAdapter(
            emptyList(),
            onDeleteClick = { contact ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Delete Contact")
                    .setMessage("Remove '${contact.name}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        DatabaseHelper.getInstance(requireContext()).deleteContactGroup(contact.id)
                        loadWorkContacts()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            },
            onItemClick = { _ -> }
        )
        binding.rvWorkContacts.adapter = workContactsAdapter
    }

    private fun setupListeners() {
        binding.btnToggleToStudent.setOnClickListener {
            val main = activity as? MainActivity
            main?.applyWorkspaceColorTheme(false)
            main?.setStudentProfile(true)
        }

        binding.btnToggleToWork.setOnClickListener {
            // Already in work mode
        }

        binding.btnNewClientOrder.setOnClickListener {
            showAddOrderDialog()
        }

        binding.btnAddWorkContact.setOnClickListener {
            // Add work contact
        }
    }

    private fun loadOrdersAndStats() {
        val db = DatabaseHelper.getInstance(requireContext())
        val profileId = ProfileManager.getActiveProfileId()

        val orders = db.getClientOrders(profileId)
        val activeOrders = orders.filter { it.status.uppercase() != "COMPLETED" }
        val totalBilled = orders.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }

        // 1. Update 1-Line Stats
        binding.tvStatTotalOrders.text = "${orders.size} Orders"
        binding.tvStatActiveOrders.text = "${activeOrders.size} Active"
        binding.tvStatTotalBilled.text = "৳${String.format(Locale.US, "%.2f", totalBilled)} Billed"

        // 2. Bind Orders List to rvClientOrders
        if (orders.isEmpty()) {
            binding.rvClientOrders.visibility = View.GONE
            binding.tvEmptyOrders.visibility = View.VISIBLE
        } else {
            binding.rvClientOrders.visibility = View.VISIBLE
            binding.tvEmptyOrders.visibility = View.GONE

            binding.rvClientOrders.adapter = ClientOrdersAdapter(
                orders = orders,
                onOrderClick = { order ->
                    showOrderDetailsModal(order)
                },
                onDeleteOrder = { order ->
                    db.deleteClientOrder(order.id)
                    loadOrdersAndStats()
                }
            )
        }
    }

    private fun loadWorkContacts() {
        if (!isAdded) return
        val db = DatabaseHelper.getInstance(requireContext())
        val contacts = db.getContactsForPortal(isStudent = false)
        workContactsAdapter.updateList(contacts)
    }

    private fun showAddOrderDialog() {
        val context = requireContext()
        val db = DatabaseHelper.getInstance(context)
        val profileId = ProfileManager.getActiveProfileId()

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_work_order, null)
        val bindingDialog = DialogAddWorkOrderBinding.bind(dialogView)

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        bindingDialog.etOrderStartDate.setText(today)
        bindingDialog.etOrderEndDate.setText(today)

        val pickDateListener = View.OnClickListener { v ->
            val cal = Calendar.getInstance()
            DatePickerDialog(context, { _, year, month, day ->
                val chosen = Calendar.getInstance().apply { set(year, month, day) }
                val formatted = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(chosen.time)
                (v as? EditText)?.setText(formatted)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        bindingDialog.etOrderStartDate.setOnClickListener(pickDateListener)
        bindingDialog.etOrderEndDate.setOnClickListener(pickDateListener)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

        bindingDialog.btnSaveWorkOrder.setOnClickListener {
            val title = bindingDialog.etOrderTitle.text.toString().trim()
            if (title.isBlank()) {
                bindingDialog.etOrderTitle.error = "Title required"
                return@setOnClickListener
            }
            val client = bindingDialog.etOrderClient.text.toString().trim()
            val unitPrice = bindingDialog.etOrderUnitPrice.text.toString().toDoubleOrNull() ?: 0.0
            val qty = bindingDialog.etOrderQuantity.text.toString().toIntOrNull() ?: 1
            val total = unitPrice * qty
            val dueDate = bindingDialog.etOrderEndDate.text.toString().trim()
            val notes = bindingDialog.etOrderNotes.text.toString().trim()

            val order = ClientOrder(
                profileId = profileId,
                title = title,
                clientName = client,
                amount = total.toString(),
                status = "IN PROGRESS",
                dueDate = dueDate,
                notes = notes
            )
            db.insertClientOrder(order)
            dialog.dismiss()
            loadOrdersAndStats()
        }

        dialog.show()
    }

    private fun showOrderDetailsModal(order: ClientOrder) {
        val db = DatabaseHelper.getInstance(requireContext())
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(order.title)
            .setMessage(
                "Client: ${if (order.clientName.isNotBlank()) order.clientName else "Independent Partner"}\n" +
                "Status: ${order.status}\n" +
                "Amount: ৳${order.amount}\n" +
                "Due Date: ${if (order.dueDate.isNotBlank()) order.dueDate else "Ongoing"}\n\n" +
                (if (order.notes.isNotBlank()) "Notes: ${order.notes}" else "")
            )
            .setPositiveButton(if (order.status.uppercase() == "COMPLETED") "Mark In-Progress" else "Mark Complete") { _, _ ->
                val newStatus = if (order.status.uppercase() == "COMPLETED") "IN PROGRESS" else "COMPLETED"
                db.insertClientOrder(order.copy(status = newStatus))
                loadOrdersAndStats()
            }
            .setNeutralButton("Delete") { _, _ ->
                db.deleteClientOrder(order.id)
                loadOrdersAndStats()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
