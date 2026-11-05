package com.duorhythm.app.ui.work

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.duorhythm.app.R
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.local.pref.ProfileManager
import com.duorhythm.app.data.model.ClientOrder
import com.duorhythm.app.data.model.ClientOrderEntity
import com.duorhythm.app.databinding.DialogCreateWorkOrderBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CreateWorkOrderDialogFragment : DialogFragment() {

    private var _binding: DialogCreateWorkOrderBinding? = null
    val binding get() = _binding!!

    private var editingOrderId: Long = -1
    private var editingOrder: ClientOrderEntity? = null
    private var onOrderSaved: (() -> Unit)? = null

    fun setOnOrderSavedListener(listener: () -> Unit) {
        onOrderSaved = listener
    }

    fun setupEditMode(order: ClientOrderEntity) {
        editingOrderId = order.id
        editingOrder = order
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCreateWorkOrderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (resources.displayMetrics.widthPixels * 0.92).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        binding.etOrderStartDate.setText(today)
        binding.etOrderDueDate.setText(today)

        val pickDateListener = View.OnClickListener { v ->
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, year, month, day ->
                val chosen = Calendar.getInstance().apply { set(year, month, day) }
                val formatted = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(chosen.time)
                (v as? EditText)?.setText(formatted)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        binding.etOrderStartDate.setOnClickListener(pickDateListener)
        binding.etOrderDueDate.setOnClickListener(pickDateListener)

        setupLineItemAdder()

        editingOrder?.let { order ->
            binding.tvWorkOrderDialogTitle.text = "Edit Work / Print Order"
            binding.btnSubmitWorkOrder.text = "UPDATE WORK ORDER"
            binding.etOrderTitle.setText(order.title)
            binding.etClientName.setText(order.clientName)
            binding.etOrderTotalAmount.setText(order.amount)
            if (order.startDate.isNotBlank()) binding.etOrderStartDate.setText(order.startDate)
            if (order.dueDate.isNotBlank()) binding.etOrderDueDate.setText(order.dueDate)
            binding.etOrderNotes.setText(order.notes)
        }

        binding.btnSubmitWorkOrder.setOnClickListener {
            saveWorkOrder()
        }
    }

    private fun setupLineItemAdder() {
        binding.btnAddLineItem.setOnClickListener {
            val row = layoutInflater.inflate(R.layout.item_order_line_entry, binding.layoutLineItemsContainer, false)
            val etQty = row.findViewById<EditText>(R.id.etLineItemQty)
            val etPrice = row.findViewById<EditText>(R.id.etLineItemPrice)
            val btnRemove = row.findViewById<View>(R.id.btnRemoveLineItem)

            val textWatcher = object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) { recalculateTotalFromItems() }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            }
            etQty.addTextChangedListener(textWatcher)
            etPrice.addTextChangedListener(textWatcher)

            btnRemove.setOnClickListener {
                binding.layoutLineItemsContainer.removeView(row)
                recalculateTotalFromItems()
            }

            binding.layoutLineItemsContainer.addView(row)
        }
    }

    private fun recalculateTotalFromItems() {
        val count = binding.layoutLineItemsContainer.childCount
        if (count == 0) return

        var totalSum = 0.0
        for (i in 0 until count) {
            val child = binding.layoutLineItemsContainer.getChildAt(i)
            val qty = child.findViewById<EditText>(R.id.etLineItemQty).text.toString().toDoubleOrNull() ?: 0.0
            val price = child.findViewById<EditText>(R.id.etLineItemPrice).text.toString().toDoubleOrNull() ?: 0.0
            totalSum += (qty * price)
        }
        binding.etOrderTotalAmount.setText(String.format(Locale.US, "%.2f", totalSum))
    }

    private fun saveWorkOrder() {
        val title = binding.etOrderTitle.text.toString().trim()
        if (title.isBlank()) {
            binding.etOrderTitle.error = "Title is required"
            return
        }

        val client = binding.etClientName.text.toString().trim().ifBlank { "Client Direct" }
        val totalAmount = binding.etOrderTotalAmount.text.toString().trim().toDoubleOrNull() ?: 0.0
        val startDate = binding.etOrderStartDate.text.toString().trim()
        val dueDate = binding.etOrderDueDate.text.toString().trim()
        val notes = binding.etOrderNotes.text.toString().trim()

        val db = DatabaseHelper.getInstance(requireContext())
        val profileId = ProfileManager.getActiveProfileId()

        val order = ClientOrder(
            id = if (editingOrderId > 0) editingOrderId else 0L,
            profileId = profileId,
            title = title,
            clientName = client,
            amount = String.format(Locale.US, "%.2f", totalAmount),
            status = editingOrder?.status ?: "IN PROGRESS",
            startDate = startDate,
            dueDate = dueDate,
            notes = notes
        )

        db.insertClientOrder(order)
        Toast.makeText(
            requireContext(),
            if (editingOrderId > 0) "Order updated successfully!" else "Work order created!",
            Toast.LENGTH_SHORT
        ).show()

        onOrderSaved?.invoke()
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
