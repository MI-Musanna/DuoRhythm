package com.duorhythm.app.ui.tools

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.duorhythm.app.data.model.ContactGroupItem
import com.duorhythm.app.databinding.ItemContactGroupBinding

class ContactsAdapter(
    private var items: List<ContactGroupItem>,
    private val onDeleteClick: (ContactGroupItem) -> Unit,
    private val onItemClick: ((ContactGroupItem) -> Unit)? = null
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
                "teacher", "instructor", "professor" -> com.duorhythm.app.R.drawable.ic_school
                "classmate", "student", "batchmate" -> com.duorhythm.app.R.drawable.ic_school
                "client", "lead" -> com.duorhythm.app.R.drawable.ic_article
                "partner", "lab partner" -> com.duorhythm.app.R.drawable.ic_contacts
                "colleague", "senior", "team" -> com.duorhythm.app.R.drawable.ic_contacts
                else -> when (item.platform) {
                    "TELEGRAM" -> com.duorhythm.app.R.drawable.ic_telegram
                    "WHATSAPP" -> com.duorhythm.app.R.drawable.ic_chat
                    else -> if (isGroup) com.duorhythm.app.R.drawable.ic_globe else com.duorhythm.app.R.drawable.ic_contacts
                }
            }
            binding.ivPlatformBadge.setImageResource(iconRes)

            val defaultCountryCode = com.duorhythm.app.util.CountryUtils.getDeviceCountryCode(context)
            val rawPhone = if (item.phoneNumber.isNotBlank()) item.phoneNumber.trim() else item.targetLink.trim()
            val normalizedPhone = com.duorhythm.app.util.CountryUtils.getNormalizedInternationalNumber(rawPhone, defaultCountryCode)
            val digitsOnly = normalizedPhone.replace("+", "").replace(" ", "").replace("-", "")

            // Make the entire card clickable to open Edit Contact
            binding.root.setOnClickListener {
                onItemClick?.invoke(item)
            }

            if (!isGroup) {
                // Truecaller-Style 1-Tap Action Buttons
                binding.layoutContactActions.visibility = android.view.View.VISIBLE
                binding.btnActionCall.visibility = android.view.View.VISIBLE
                binding.btnActionWhatsApp.visibility = android.view.View.VISIBLE
                binding.btnActionTelegram.visibility = android.view.View.VISIBLE
                binding.btnOpenContactLink.visibility = android.view.View.GONE

                // 1-Tap Phone Call: Intent.ACTION_DIAL with tel:$cleanPhone (URI encoded)
                binding.btnActionCall.setOnClickListener {
                    if (normalizedPhone.isNotEmpty()) {
                        try {
                            val cleanPhone = Uri.encode(normalizedPhone.trim())
                            val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhone"))
                            context.startActivity(callIntent)
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, "No dialer app installed on this device", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {
                            Toast.makeText(context, "Cannot open dialer: $normalizedPhone", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "No phone number available", Toast.LENGTH_SHORT).show()
                    }
                }

                // Direct WhatsApp Chat Launch with ActivityNotFoundException safeguard
                binding.btnActionWhatsApp.setOnClickListener {
                    if (digitsOnly.isNotEmpty()) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("https://api.whatsapp.com/send?phone=$digitsOnly")
                                setPackage("com.whatsapp")
                            }
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            try {
                                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$digitsOnly"))
                                context.startActivity(fallbackIntent)
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(context, "WhatsApp is not installed on this device", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {
                                Toast.makeText(context, "WhatsApp is not installed on this device", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "WhatsApp is not installed on this device", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "No valid phone number for WhatsApp", Toast.LENGTH_SHORT).show()
                    }
                }

                // 1-Tap Telegram: https://t.me/+cleanNumber
                binding.btnActionTelegram.setOnClickListener {
                    if (digitsOnly.isNotEmpty()) {
                        try {
                            val tgUri = Uri.parse("https://t.me/+$digitsOnly")
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
                                Toast.makeText(context, "Cannot open Telegram for $normalizedPhone", Toast.LENGTH_SHORT).show()
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
