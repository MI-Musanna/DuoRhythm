package com.duorhythm.app.ui.portal

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.R
import com.duorhythm.app.data.model.WorkOrder
import com.duorhythm.app.databinding.ItemWorkOrderBinding
import java.io.File
import java.util.Locale

class WorkOrdersAdapter(
    private var orders: List<WorkOrder>,
    private var currencySymbol: String = "$",
    private var currencyRate: Double = 1.0,
    private val onAdvanceStatus: (WorkOrder) -> Unit,
    private val onDeleteClick: (WorkOrder) -> Unit
) : RecyclerView.Adapter<WorkOrdersAdapter.OrderViewHolder>() {

    fun updateList(
        newOrders: List<WorkOrder>,
        newSymbol: String = currencySymbol,
        newRate: Double = currencyRate
    ) {
        orders = newOrders
        currencySymbol = newSymbol
        currencyRate = newRate
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemWorkOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(orders[position])
    }

    override fun getItemCount(): Int = orders.size

    inner class OrderViewHolder(private val binding: ItemWorkOrderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(order: WorkOrder) {
            val context = binding.root.context
            val convertedUnitPrice = order.unitPrice
            val convertedTotalPrice = order.totalPrice

            binding.tvOrderTitle.text = order.title
            binding.tvClientName.text = if (order.clientName.isNotEmpty()) "Client: ${order.clientName}" else "Client: Walk-in"
            binding.tvQuantityAndRate.text = "Qty: ${order.quantity} units  @  ${currencySymbol}${String.format(Locale.US, "%.2f", convertedUnitPrice)} / unit"
            binding.tvTotalPrice.text = String.format(Locale.US, "Total: %s%.2f", currencySymbol, convertedTotalPrice)
            binding.tvOrderDates.text = "Start: ${order.startDate}  ➔  Due: ${order.endDate}"
            binding.tvOrderNotes.text = order.notes.ifEmpty { "No special notes." }

            binding.tvOrderStatus.text = order.status
            when (order.status) {
                "COMPLETED" -> {
                    binding.tvOrderStatus.setTextColor(ContextCompat.getColor(context, R.color.income_green))
                    binding.tvOrderStatus.setBackgroundColor(0x2210B981)
                    binding.btnAdvanceStatus.text = "Completed ✓"
                }
                "IN_PROGRESS" -> {
                    binding.tvOrderStatus.setTextColor(ContextCompat.getColor(context, R.color.accent_amber))
                    binding.tvOrderStatus.setBackgroundColor(0x22F59E0B)
                    binding.btnAdvanceStatus.text = "Mark Completed ➜"
                }
                else -> {
                    binding.tvOrderStatus.setTextColor(ContextCompat.getColor(context, R.color.primary))
                    binding.tvOrderStatus.setBackgroundColor(0x222563EB)
                    binding.btnAdvanceStatus.text = "Start Progress ➜"
                }
            }

            if (order.attachmentPath.isNotEmpty()) {
                binding.layoutOrderAttachment.visibility = View.VISIBLE
                val file = File(order.attachmentPath)
                binding.tvOrderAttachmentName.text = "Attachment: ${file.name}"
                binding.layoutOrderAttachment.setOnClickListener {
                    try {
                        val authority = "${context.packageName}.fileprovider"
                        val fileUri = FileProvider.getUriForFile(context, authority, file)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(fileUri, "*/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Open File"))
                    } catch (_: Exception) {
                        Toast.makeText(context, "Attachment saved at: ${file.name}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                binding.layoutOrderAttachment.visibility = View.GONE
            }

            binding.btnAdvanceStatus.setOnClickListener {
                onAdvanceStatus(order)
            }

            binding.btnDeleteOrder.setOnClickListener {
                onDeleteClick(order)
            }
        }
    }
}
