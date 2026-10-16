package com.pocketdev.duorhythm.ui.tools

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.pocketdev.duorhythm.data.model.ContactGroupItem
import com.pocketdev.duorhythm.databinding.ItemContactGroupBinding

class ContactsAdapter(
    private var items: List<ContactGroupItem>,
    private val onDeleteClick: (ContactGroupItem) -> Unit
) : RecyclerView.Adapter<ContactsAdapter.ContactViewHolder>() {

    fun updateList(newItems: List<ContactGroupItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ContactViewHolder(private val binding: ItemContactGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ContactGroupItem) {
            val context = binding.root.context
            binding.tvContactName.text = item.name
            if (item.role.isNotBlank()) {
                binding.tvContactRole.visibility = android.view.View.VISIBLE
                binding.tvContactRole.text = item.role
            } else {
                binding.tvContactRole.visibility = android.view.View.GONE
            }
            
            val infoText = when {
                item.notes.isNotEmpty() && item.phoneNumber.isNotEmpty() -> "${item.phoneNumber} • ${item.notes}"
                item.phoneNumber.isNotEmpty() -> item.phoneNumber
                item.notes.isNotEmpty() -> item.notes
                else -> item.targetLink
            }
            binding.tvContactNotes.text = infoText

            val isGroup = item.type.equals("GROUP", ignoreCase = true)
            val iconRes = when (item.role.lowercase().trim()) {
                "teacher", "instructor", "professor" -> com.pocketdev.duorhythm.R.drawable.ic_school
                "classmate", "student", "batchmate" -> com.pocketdev.duorhythm.R.drawable.ic_school
                "client", "lead" -> com.pocketdev.duorhythm.R.drawable.ic_article
                "partner", "lab partner" -> com.pocketdev.duorhythm.R.drawable.ic_contacts
                "colleague", "senior", "team" -> com.pocketdev.duorhythm.R.drawable.ic_contacts
                else -> when (item.platform) {
                    "TELEGRAM" -> com.pocketdev.duorhythm.R.drawable.ic_telegram
                    "WHATSAPP" -> com.pocketdev.duorhythm.R.drawable.ic_chat
                    else -> if (isGroup) com.pocketdev.duorhythm.R.drawable.ic_globe else com.pocketdev.duorhythm.R.drawable.ic_contacts
                }
            }
            binding.ivPlatformBadge.setImageResource(iconRes)

            val phoneVal = if (item.phoneNumber.isNotBlank()) item.phoneNumber.trim() else item.targetLink.trim()
            val cleanPhone = phoneVal.replace(Regex("[^0-9+]"), "")

            if (!isGroup) {
                // Truecaller-Style 1-Tap Action Buttons
                binding.layoutContactActions.visibility = android.view.View.VISIBLE
                binding.btnActionCall.visibility = android.view.View.VISIBLE
                binding.btnActionWhatsApp.visibility = android.view.View.VISIBLE
                binding.btnActionTelegram.visibility = android.view.View.VISIBLE
                binding.btnOpenContactLink.visibility = android.view.View.GONE

                // 1-Tap Phone Call: Intent.ACTION_DIAL with tel:$cleanNumber
                binding.btnActionCall.setOnClickListener {
                    if (cleanPhone.isNotEmpty()) {
                        try {
                            val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhone"))
                            context.startActivity(callIntent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "Cannot open dialer: $cleanPhone", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "No phone number available", Toast.LENGTH_SHORT).show()
                    }
                }

                // 1-Tap WhatsApp: Intent.ACTION_VIEW with https://api.whatsapp.com/send?phone=$cleanNumber
                binding.btnActionWhatsApp.setOnClickListener {
                    val waNumber = cleanPhone.replace("+", "")
                    if (waNumber.isNotEmpty()) {
                        try {
                            val waUri = Uri.parse("https://api.whatsapp.com/send?phone=$waNumber")
                            val waIntent = Intent(Intent.ACTION_VIEW, waUri)
                            context.startActivity(waIntent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "Cannot open WhatsApp for $cleanPhone", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "No valid phone number for WhatsApp", Toast.LENGTH_SHORT).show()
                    }
                }

                // 1-Tap Telegram: https://t.me/+cleanNumber
                binding.btnActionTelegram.setOnClickListener {
                    val tgNumber = cleanPhone.replace("+", "")
                    if (tgNumber.isNotEmpty()) {
                        try {
                            val tgUri = Uri.parse("https://t.me/+$tgNumber")
                            val tgIntent = Intent(Intent.ACTION_VIEW, tgUri)
                            context.startActivity(tgIntent)
                        } catch (_: Exception) {
                            try {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "Hello from LifeSync!")
                                    `package` = "org.telegram.messenger"
                                }
                                context.startActivity(shareIntent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Cannot open Telegram for $cleanPhone", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, "No valid phone number for Telegram", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                binding.btnActionCall.visibility = android.view.View.GONE
                binding.btnActionWhatsApp.visibility = android.view.View.GONE
                binding.btnActionTelegram.visibility = android.view.View.GONE
            }

            // Fallback / Community link opener (for Discord, Messenger, or general group link)
            val showOpenLink = isGroup || (item.platform != "PHONE" && item.platform != "WHATSAPP" && item.platform != "TELEGRAM" && item.targetLink.isNotBlank())
            if (showOpenLink && item.targetLink.isNotBlank()) {
                binding.btnOpenContactLink.visibility = android.view.View.VISIBLE
                binding.btnOpenContactLink.text = if (isGroup) "Join Community ➜" else "Open Link ➜"
                binding.btnOpenContactLink.setOnClickListener {
                    try {
                        val target = if (item.targetLink.startsWith("http")) item.targetLink else "https://${item.targetLink}"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                    } catch (_: Exception) {
                        Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                binding.btnOpenContactLink.visibility = android.view.View.GONE
            }

            binding.btnDeleteContact.setOnClickListener {
                onDeleteClick(item)
            }
        }
    }
}
