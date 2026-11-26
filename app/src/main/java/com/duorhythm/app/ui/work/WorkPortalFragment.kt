package com.duorhythm.app.ui.work

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.duorhythm.app.MainActivity
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.data.model.ClientOrder
import com.duorhythm.app.data.model.ClientOrderEntity
import com.duorhythm.app.databinding.FragmentWorkPortalBinding
import com.duorhythm.app.ui.tools.ContactsAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        com.duorhythm.app.util.ViewAnimationExtensions.applySpringToAllButtons(view)

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
        if (!isAdded || _binding == null) return
        val db = DatabaseHelper.getInstance(requireContext())
        val profileId = ProfileManager.getActiveProfileId()

        val orders = db.getClientOrders(profileId)
        val activeOrders = orders.filter { it.status.uppercase() != "COMPLETED" }
        val totalBilled = orders.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }

        // 1. Update 1-Line Stats
        binding.tvStatTotalOrders.text = "${orders.size} Orders"
        binding.tvStatActiveOrders.text = "${activeOrders.size} In Progress"
        binding.tvStatTotalBilled.text = "৳${String.format(Locale.US, "%.2f", totalBilled)} Billed"

        // 2. Bind Orders List to rvClientOrders
        if (orders.isEmpty()) {
            binding.rvClientOrders.visibility = View.GONE
            binding.emptyOrdersView.visibility = View.VISIBLE
            binding.emptyOrdersView.setState(
                iconRes = R.drawable.ic_work,
                title = "No client orders yet",
                subtitle = "Log freelance commissions, deliverables, due dates, and client invoices.",
                actionText = "+ New Order"
            ) {
                showAddOrderDialog()
            }
            binding.emptyOrdersView.setAccentColor(android.graphics.Color.parseColor("#10B981"))
        } else {
            binding.rvClientOrders.visibility = View.VISIBLE
            binding.emptyOrdersView.visibility = View.GONE

            binding.rvClientOrders.adapter = ClientOrdersAdapter(
                orders = orders,
                onOrderClick = { order ->
                    showOrderDetailsModal(order)
                },
                onDeleteOrder = { order ->
                    confirmDeleteOrder(order)
                }
            )
        }
    }

    private fun loadWorkContacts() {
        if (!isAdded || _binding == null) return
        val db = DatabaseHelper.getInstance(requireContext())
        val contacts = db.getContactsForPortal(isStudent = false)
        workContactsAdapter.updateList(contacts)
        if (contacts.isEmpty()) {
            binding.rvWorkContacts.visibility = View.GONE
            binding.emptyWorkContactsView.visibility = View.VISIBLE
            binding.emptyWorkContactsView.setState(
                iconRes = R.drawable.ic_contacts,
                title = "No client contacts saved",
                subtitle = "Keep key clients, collaborators, and agency contacts one tap away.",
                actionText = "+ Add Contact"
            ) {
                binding.btnAddWorkContact.performClick()
            }
            binding.emptyWorkContactsView.setAccentColor(android.graphics.Color.parseColor("#10B981"))
        } else {
            binding.rvWorkContacts.visibility = View.VISIBLE
            binding.emptyWorkContactsView.visibility = View.GONE
        }
    }

    private fun showAddOrderDialog() {
        val createDialog = CreateWorkOrderDialogFragment()
        createDialog.setOnOrderSavedListener { loadOrdersAndStats() }
        createDialog.show(parentFragmentManager, "CreateWorkOrder")
    }

    private fun showOrderDetailsModal(order: ClientOrderEntity) {
        val dialog = BottomSheetDialog(requireContext(), R.style.CustomBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.dialog_client_order_details, null)
        dialog.setContentView(view)

        // Bind order text
        view.findViewById<TextView>(R.id.tvDetailOrderTitle).text = order.title
        view.findViewById<TextView>(R.id.tvDetailClientName).text = "Client: ${if (order.clientName.isNotBlank()) order.clientName else "Direct Customer"}"
        view.findViewById<TextView>(R.id.tvDetailAmount).text = "৳${order.amount}"
        view.findViewById<TextView>(R.id.tvDetailStatus).text = order.status
        view.findViewById<TextView>(R.id.tvDetailDueDate).text = "Due Date: ${if (order.dueDate.isNotBlank()) order.dueDate else "Ongoing"}"
        val tvNotes = view.findViewById<TextView>(R.id.tvDetailNotes)
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
            val editDialog = CreateWorkOrderDialogFragment()
            editDialog.setupEditMode(order)
            editDialog.setOnOrderSavedListener { loadOrdersAndStats() }
            editDialog.show(parentFragmentManager, "EditWorkOrder")
        }

        // MARK COMPLETE ACTION (Deposit to Wallet Bucket):
        val btnComplete = view.findViewById<View>(R.id.btnMarkComplete)
        if (order.status.uppercase() == "COMPLETED") {
            (btnComplete as? com.google.android.material.button.MaterialButton)?.text = "MARK IN PROGRESS"
            btnComplete.setOnClickListener {
                dialog.dismiss()
                val db = DatabaseHelper.getInstance(requireContext())
                db.updateClientOrderStatus(order.id, "IN PROGRESS")
                loadOrdersAndStats()
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

    private fun confirmDeleteOrder(order: ClientOrderEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Order")
            .setMessage("Are you sure you want to remove '${order.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                val db = DatabaseHelper.getInstance(requireContext())
                db.deleteClientOrder(order.id)
                loadOrdersAndStats()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDepositEarningsDialog(order: ClientOrderEntity) {
        val db = DatabaseHelper.getInstance(requireContext())
        val profileId = ProfileManager.getActiveProfileId()
        val accounts = db.getAccounts(profileId)

        val depositDialog = BottomSheetDialog(requireContext(), R.style.CustomBottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.dialog_deposit_earnings, null)
        depositDialog.setContentView(view)

        view.findViewById<TextView>(R.id.tvDepositOrderTitleSub).text = "${order.title} • Client: ${order.clientName}"
        view.findViewById<TextView>(R.id.tvDepositAmountHighlight).text = "+ ৳${order.amount}"

        val container = view.findViewById<LinearLayout>(R.id.layoutBucketListContainer)
        val orderAmount = order.amount.toDoubleOrNull() ?: 0.0
        val d = resources.displayMetrics.density

        for (acc in accounts) {
            val card = MaterialCardView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, (8f * d).toInt()) }
                setCardBackgroundColor(Color.parseColor("#14171F"))
                radius = 12f * d
                strokeWidth = 2
                strokeColor = Color.parseColor("#2B303E")

                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding((16f * d).toInt(), (14f * d).toInt(), (16f * d).toInt(), (14f * d).toInt())
                    gravity = android.view.Gravity.CENTER_VERTICAL

                    val icon = ImageView(requireContext()).apply {
                        val iconDp = (24f * d).toInt()
                        layoutParams = LinearLayout.LayoutParams(iconDp, iconDp)
                        setImageResource(R.drawable.ic_wallet)
                        imageTintList = ColorStateList.valueOf(Color.parseColor("#10B981"))
                    }
                    addView(icon)

                    val textCol = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginStart = (12f * d).toInt()
                        }
                        val tvName = TextView(requireContext()).apply {
                            text = acc.name
                            setTextColor(Color.WHITE)
                            textSize = 14f
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        val tvBal = TextView(requireContext()).apply {
                            text = "Current: ৳${acc.balance}"
                            setTextColor(Color.parseColor("#94A3B8"))
                            textSize = 11f
                        }
                        addView(tvName)
                        addView(tvBal)
                    }
                    addView(textCol)

                    val arrow = TextView(requireContext()).apply {
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
                        com.duorhythm.app.ui.custom.DuoAlertBanner.show(requireContext(), "৳${order.amount} deposited into ${acc.name}! Order Completed.")
                        loadOrdersAndStats()
                    }
                }
            }
            container.addView(card)
        }

        view.findViewById<View>(R.id.btnSkipDeposit).setOnClickListener {
            depositDialog.dismiss()
            db.updateClientOrderStatus(order.id, "COMPLETED")
            com.duorhythm.app.ui.custom.DuoAlertBanner.show(requireContext(), "Order marked as completed without wallet deposit.")
            loadOrdersAndStats()
        }

        depositDialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
