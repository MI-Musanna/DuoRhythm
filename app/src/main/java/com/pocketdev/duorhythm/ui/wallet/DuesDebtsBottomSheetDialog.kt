package com.pocketdev.duorhythm.ui.wallet

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.pocketdev.duorhythm.R
import com.pocketdev.duorhythm.data.local.db.DatabaseHelper
import com.pocketdev.duorhythm.data.model.DueDebtItem
import com.pocketdev.duorhythm.databinding.BottomSheetDuesDebtsBinding
import com.pocketdev.duorhythm.databinding.DialogAddDueDebtBinding
import com.pocketdev.duorhythm.databinding.DialogSettleDueDebtBinding
import com.pocketdev.duorhythm.util.CurrencyUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DuesDebtsBottomSheetDialog : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_CURRENCY_SYMBOL = "arg_currency_symbol"
        private const val ARG_CURRENCY_CODE = "arg_currency_code"

        fun newInstance(currencySymbol: String = "৳", currencyCode: String = "BDT"): DuesDebtsBottomSheetDialog {
            return DuesDebtsBottomSheetDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_CURRENCY_SYMBOL, currencySymbol)
                    putString(ARG_CURRENCY_CODE, currencyCode)
                }
            }
        }
    }

    private var _binding: BottomSheetDuesDebtsBinding? = null
    private val binding get() = _binding!!

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var adapter: DuesDebtsAdapter

    private var currentSymbol = "৳"
    private var currentCurrency = "BDT"
    private var currentFilter = "ALL" // "ALL", "LENT", "BORROWED", "SETTLED"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentSymbol = arguments?.getString(ARG_CURRENCY_SYMBOL, "৳") ?: "৳"
        currentCurrency = arguments?.getString(ARG_CURRENCY_CODE, "BDT") ?: "BDT"
        dbHelper = DatabaseHelper.getInstance(requireContext())
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(R.drawable.bg_sheet_obsidian)
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetDuesDebtsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        loadData()
    }

    private fun setupRecyclerView() {
        adapter = DuesDebtsAdapter(
            items = emptyList(),
            currencySymbol = currentSymbol,
            onSettleClick = { item -> showSettleDialog(item) },
            onDeleteClick = { item -> confirmDelete(item) },
            onItemClick = { item ->
                if (item.status.equals("UNPAID", ignoreCase = true)) {
                    showSettleDialog(item)
                }
            }
        )
        binding.rvDuesDebts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDuesDebts.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnAddDueDebtHeader.setOnClickListener {
            showAddDueDebtDialog()
        }

        binding.chipGroupDueFilter.setOnCheckedChangeListener { _, checkedId ->
            currentFilter = when (checkedId) {
                R.id.chipFilterLent -> "LENT"
                R.id.chipFilterBorrowed -> "BORROWED"
                R.id.chipFilterSettled -> "SETTLED"
                else -> "ALL"
            }
            loadData()
        }
    }

    private fun loadData() {
        val allItems = dbHelper.getAllDuesDebts()

        val summary = dbHelper.getDuesDebtsSummary()
        binding.tvTotalLent.text = String.format(Locale.US, "+%s%,.2f", currentSymbol, summary.first)
        binding.tvTotalBorrowed.text = String.format(Locale.US, "-%s%,.2f", currentSymbol, summary.second)

        val filtered = when (currentFilter) {
            "LENT" -> allItems.filter { it.type.equals("LENT", ignoreCase = true) && it.status.equals("UNPAID", ignoreCase = true) }
            "BORROWED" -> allItems.filter { it.type.equals("BORROWED", ignoreCase = true) && it.status.equals("UNPAID", ignoreCase = true) }
            "SETTLED" -> allItems.filter { it.status.equals("SETTLED", ignoreCase = true) }
            else -> allItems
        }

        adapter.updateData(filtered, currentSymbol)

        if (filtered.isEmpty()) {
            binding.rvDuesDebts.visibility = View.GONE
            binding.layoutEmptyDues.visibility = View.VISIBLE
        } else {
            binding.rvDuesDebts.visibility = View.VISIBLE
            binding.layoutEmptyDues.visibility = View.GONE
        }
    }

    private fun showAddDueDebtDialog() {
        val dialogBinding = DialogAddDueDebtBinding.inflate(layoutInflater)
        dialogBinding.tilDueAmount.hint = "Amount ($currentSymbol - $currentCurrency)"

        var selectedType = "LENT"
        dialogBinding.toggleDueType.check(R.id.btnTypeLent)

        fun updateToggleStyles(isLent: Boolean) {
            if (isLent) {
                dialogBinding.btnTypeLent.setBackgroundColor(Color.parseColor("#064E3B"))
                dialogBinding.btnTypeLent.setTextColor(Color.parseColor("#34D399"))
                dialogBinding.btnTypeLent.strokeColor = ColorStateList.valueOf(Color.parseColor("#059669"))

                dialogBinding.btnTypeBorrowed.setBackgroundColor(Color.TRANSPARENT)
                dialogBinding.btnTypeBorrowed.setTextColor(Color.parseColor("#94A3B8"))
                dialogBinding.btnTypeBorrowed.strokeColor = ColorStateList.valueOf(Color.parseColor("#203049"))
            } else {
                dialogBinding.btnTypeBorrowed.setBackgroundColor(Color.parseColor("#4C1D24"))
                dialogBinding.btnTypeBorrowed.setTextColor(Color.parseColor("#F87171"))
                dialogBinding.btnTypeBorrowed.strokeColor = ColorStateList.valueOf(Color.parseColor("#991B1B"))

                dialogBinding.btnTypeLent.setBackgroundColor(Color.TRANSPARENT)
                dialogBinding.btnTypeLent.setTextColor(Color.parseColor("#94A3B8"))
                dialogBinding.btnTypeLent.strokeColor = ColorStateList.valueOf(Color.parseColor("#203049"))
            }
        }
        updateToggleStyles(true)

        dialogBinding.toggleDueType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val isLent = (checkedId == R.id.btnTypeLent)
                selectedType = if (isLent) "LENT" else "BORROWED"
                updateToggleStyles(isLent)
            }
        }

        // Date Picker on Due Date
        dialogBinding.etDueDate.setOnClickListener {
            val cal = Calendar.getInstance()
            val dpd = DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    val chosen = Calendar.getInstance().apply {
                        set(year, month, dayOfMonth)
                    }
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    dialogBinding.etDueDate.setText(sdf.format(chosen.time))
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            )
            dpd.show()
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnSaveDueDebt.setOnClickListener {
            val amountStr = dialogBinding.etDueAmount.text?.toString()?.trim().orEmpty()
            if (amountStr.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter an amount", Toast.LENGTH_SHORT).show()
                dialogBinding.etDueAmount.requestFocus()
                return@setOnClickListener
            }

            val enteredAmount = amountStr.toDoubleOrNull() ?: 0.0
            if (enteredAmount <= 0.0) {
                Toast.makeText(requireContext(), "Please enter a valid amount greater than 0", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val personName = dialogBinding.etDuePersonName.text?.toString()?.trim().orEmpty()
            if (personName.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter person's name", Toast.LENGTH_SHORT).show()
                dialogBinding.etDuePersonName.requestFocus()
                return@setOnClickListener
            }

            val contact = dialogBinding.etDueContact.text?.toString()?.trim().orEmpty()
            val dueDate = dialogBinding.etDueDate.text?.toString()?.trim().orEmpty()
            val notes = dialogBinding.etDueNotes.text?.toString()?.trim().orEmpty()
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

            val item = DueDebtItem(
                personName = personName,
                phoneOrContact = contact,
                amount = enteredAmount,
                dueDate = dueDate,
                type = selectedType,
                status = "UNPAID",
                notes = notes,
                createdAt = today
            )

            dbHelper.insertDueDebt(item)
            dialog.dismiss()
            loadData()
            WalletDataSyncManager.notifyChanged()
            val typeLabel = if (selectedType == "LENT") "Lent due" else "Borrowed debt"
            Toast.makeText(requireContext(), "Recorded $typeLabel for $personName", Toast.LENGTH_SHORT).show()
        }

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        dialogBinding.etDueAmount.requestFocus()
    }

    private fun showSettleDialog(item: DueDebtItem) {
        val dialogBinding = DialogSettleDueDebtBinding.inflate(layoutInflater)
        dialogBinding.tvSettlePerson.text = item.personName
        val formattedAmt = CurrencyUtils.formatRawCurrency(item.amount, currentSymbol)
        val isLent = item.type.equals("LENT", ignoreCase = true)

        if (isLent) {
            dialogBinding.tvSettleTitle.text = "Settle Received Due"
            dialogBinding.tvSettleSubtitle.text = "Confirm you have received back the lent funds from ${item.personName}."
            dialogBinding.tvSettleAmount.text = "+$formattedAmt"
            dialogBinding.tvSettleAmount.setTextColor(Color.parseColor("#34D399"))
            dialogBinding.tilSettleAccount.hint = "Deposit Account (To Account)"
            dialogBinding.btnConfirmSettle.text = "✓ Confirm Received & Settle"
            dialogBinding.btnConfirmSettle.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#059669"))
        } else {
            dialogBinding.tvSettleTitle.text = "Settle Paid Debt"
            dialogBinding.tvSettleSubtitle.text = "Confirm you have paid back your borrowed debt to ${item.personName}."
            dialogBinding.tvSettleAmount.text = "-$formattedAmt"
            dialogBinding.tvSettleAmount.setTextColor(Color.parseColor("#F87171"))
            dialogBinding.tilSettleAccount.hint = "Payment Account (From Account)"
            dialogBinding.btnConfirmSettle.text = "✓ Confirm Paid & Settle"
            dialogBinding.btnConfirmSettle.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#991B1B"))
        }

        val buckets = dbHelper.getAllWalletBuckets()
        if (buckets.isEmpty()) {
            dialogBinding.cbLogTransaction.isChecked = false
            dialogBinding.cbLogTransaction.visibility = View.GONE
            dialogBinding.tilSettleAccount.visibility = View.GONE
        } else {
            val bucketNames = buckets.map { it.name }
            val bucketAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, bucketNames)
            dialogBinding.actvSettleAccount.setAdapter(bucketAdapter)
            val prefs = requireContext().getSharedPreferences("lifesync_prefs", Context.MODE_PRIVATE)
            val lastUsedBucketId = prefs.getLong("last_used_wallet_account_id", -1L)
            val defaultBucket = buckets.find { it.id == lastUsedBucketId } ?: buckets.first()
            dialogBinding.actvSettleAccount.setText(defaultBucket.name, false)
            dialogBinding.actvSettleAccount.setOnClickListener { dialogBinding.actvSettleAccount.showDropDown() }

            dialogBinding.cbLogTransaction.setOnCheckedChangeListener { _, isChecked ->
                dialogBinding.tilSettleAccount.visibility = if (isChecked) View.VISIBLE else View.GONE
            }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnConfirmSettle.setOnClickListener {
            val shouldLog = dialogBinding.cbLogTransaction.isChecked && buckets.isNotEmpty()
            val note = dialogBinding.etSettleNote.text?.toString()?.trim().orEmpty()

            var chosenBucketId: Long? = null
            if (shouldLog) {
                val selectedName = dialogBinding.actvSettleAccount.text?.toString()?.trim().orEmpty()
                val selectedBucket = buckets.firstOrNull { it.name.equals(selectedName, ignoreCase = true) } ?: buckets.first()
                chosenBucketId = selectedBucket.id
            }

            val settled = dbHelper.settleDueDebt(
                id = item.id,
                bucketId = chosenBucketId,
                recordTransaction = shouldLog,
                note = note,
                currency = currentCurrency
            )

            if (settled) {
                dialog.dismiss()
                loadData()
                WalletDataSyncManager.notifyChanged()
                Toast.makeText(requireContext(), "Settled with ${item.personName}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Failed to settle record", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun confirmDelete(item: DueDebtItem) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Record")
            .setMessage("Remove record for '${item.personName}' (${CurrencyUtils.formatRawCurrency(item.amount, currentSymbol)})?")
            .setPositiveButton("Delete") { _, _ ->
                dbHelper.deleteDueDebt(item.id)
                loadData()
                WalletDataSyncManager.notifyChanged()
                Toast.makeText(requireContext(), "Record deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
