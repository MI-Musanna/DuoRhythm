package com.duorhythm.app.ui.portal

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.duorhythm.app.data.local.db.DatabaseHelper
import com.duorhythm.app.data.model.WorkOrder
import com.duorhythm.app.databinding.ActivityWorkOrderDetailsBinding
import java.util.Locale

class WorkOrderDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorkOrderDetailsBinding
    private lateinit var dbHelper: DatabaseHelper
    private var orderId: Long = -1L
    private var currentOrder: WorkOrder? = null

    private val launcher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                // Fallback
            }
            saveWorkAttachment(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkOrderDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dbHelper = DatabaseHelper.getInstance(this)
        orderId = intent.getLongExtra(EXTRA_ORDER_ID, -1L)

        binding.toolbarWorkOrderDetails.setNavigationOnClickListener {
            finish()
        }

        binding.btnAttachDocument.setOnClickListener {
            launcher.launch(arrayOf("*/*"))
        }

        loadOrder()
    }

    private fun loadOrder() {
        val order = dbHelper.getAllWorkOrders().firstOrNull { it.id == orderId }
        if (order == null) {
            finish()
            return
        }
        currentOrder = order

        binding.tvOrderTitle.text = order.title
        binding.tvOrderClient.text = if (order.clientName.isNotBlank()) "Client: ${order.clientName}" else "Client: Not specified"
        binding.tvOrderPrice.text = String.format(Locale.US, "৳%.2f (%d units)", order.totalPrice, order.quantity)
        binding.tvOrderDates.text = "Due: ${order.endDate} • Status: ${order.status}"
        binding.tvOrderNotes.text = if (order.notes.isNotBlank()) "Notes:\n${order.notes}" else "No notes specified."

        if (order.attachmentPath.isNotBlank()) {
            binding.tvAttachmentStatus.text = "Attached: ${order.attachmentPath}"
        } else {
            binding.tvAttachmentStatus.text = "No document attached"
        }
    }

    private fun saveWorkAttachment(uri: Uri) {
        val order = currentOrder ?: return
        val updated = order.copy(attachmentPath = uri.toString())
        dbHelper.updateWorkOrder(updated)
        currentOrder = updated
        binding.tvAttachmentStatus.text = "Attached: ${uri.lastPathSegment ?: uri.toString()}"
        Toast.makeText(this, "Attachment saved successfully", Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_ORDER_ID = "extra_order_id"

        fun start(context: Context, orderId: Long) {
            val intent = Intent(context, WorkOrderDetailsActivity::class.java).apply {
                putExtra(EXTRA_ORDER_ID, orderId)
            }
            context.startActivity(intent)
        }
    }
}
