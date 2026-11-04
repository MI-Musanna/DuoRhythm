package com.duorhythm.app.ui.work

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.data.model.ClientOrder
import com.duorhythm.app.databinding.ItemClientOrderBinding
import java.util.Locale

class ClientOrdersAdapter(
    private var orders: List<ClientOrder>,
    private val onOrderClick: (ClientOrder) -> Unit,
    private val onDeleteOrder: (ClientOrder) -> Unit
) : RecyclerView.Adapter<ClientOrdersAdapter.OrderViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemClientOrderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(orders[position])
    }

    override fun getItemCount(): Int = orders.size

    fun updateList(newOrders: List<ClientOrder>) {
        orders = newOrders
        notifyDataSetChanged()
    }

    inner class OrderViewHolder(private val binding: ItemClientOrderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(order: ClientOrder) {
            binding.tvOrderTitle.text = order.title
            val statusUpper = order.status.uppercase()
            binding.badgeOrderStatus.text = statusUpper
            binding.badgeOrderStatus.setTextColor(
                when (statusUpper) {
                    "COMPLETED" -> Color.parseColor("#38BDF8")
                    "IN PROGRESS", "IN_PROGRESS" -> Color.parseColor("#10B981")
                    else -> Color.parseColor("#F59E0B")
                }
            )

            binding.tvClientName.text = if (order.clientName.isNotBlank()) {
                "Client: ${order.clientName}"
            } else {
                "Client: Independent Partner"
            }

            val amountNum = order.amount.toDoubleOrNull()
            binding.tvOrderAmount.text = if (amountNum != null) {
                String.format(Locale.US, "৳%,.2f", amountNum)
            } else if (order.amount.startsWith("৳") || order.amount.startsWith("$")) {
                order.amount
            } else {
                "৳${order.amount}"
            }

            binding.tvOrderDueDate.text = if (order.dueDate.isNotBlank()) {
                "Due: ${order.dueDate}"
            } else {
                "Ongoing"
            }

            binding.root.setOnClickListener { onOrderClick(order) }
            binding.root.setOnLongClickListener {
                onDeleteOrder(order)
                true
            }
        }
    }
}
