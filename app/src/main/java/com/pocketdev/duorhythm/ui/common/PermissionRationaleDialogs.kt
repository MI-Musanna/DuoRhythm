package com.pocketdev.duorhythm.ui.common

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.provider.Settings
import android.view.LayoutInflater
import com.pocketdev.duorhythm.R
import com.pocketdev.duorhythm.databinding.DialogPermissionRationaleBinding
import com.pocketdev.duorhythm.util.AnimationUtils

object PermissionRationaleDialogs {

    fun showNotificationRationale(activity: Activity, onAccept: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) return

        val binding = DialogPermissionRationaleBinding.inflate(LayoutInflater.from(activity))
        binding.ivRationaleIcon.setImageResource(R.drawable.ic_schedule)
        binding.tvRationaleTitle.text = "Enable Notifications"
        binding.tvRationaleSubtitle.text = "Alarms & Briefings"
        binding.tvRationaleMessage.text =
            "DuoRhythm needs notification permission to deliver 15-minute timetable alarms, smart morning/evening daily agendas, and upcoming subscription countdown reminders."
        binding.btnRationaleAllow.text = "Allow Notifications"

        AnimationUtils.attachTactileFeedback(binding.btnRationaleAllow, binding.btnRationaleDismiss)

        val dialog = AlertDialog.Builder(activity)
            .setView(binding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        AnimationUtils.applyDialogAnimation(dialog)

        binding.btnRationaleDismiss.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnRationaleAllow.setOnClickListener {
            dialog.dismiss()
            onAccept()
        }

        dialog.show()
    }

    fun showNotificationListenerRationale(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return

        val binding = DialogPermissionRationaleBinding.inflate(LayoutInflater.from(activity))
        binding.ivRationaleIcon.setImageResource(R.drawable.ic_wallet)
        binding.tvRationaleTitle.text = "Smart Expense Detection"
        binding.tvRationaleSubtitle.text = "bKash • Nagad • Bank SMS"
        binding.tvRationaleMessage.text =
            "DuoRhythm can automatically detect transactional payment alerts from bKash, Nagad, Rocket, and bank SMS, prompting you with an effortless 1-tap [Log Expense] button so you never miss logging expenses.\n\nPurely parses amounts on your device. Zero internet transmission, zero tracking."
        binding.btnRationaleAllow.text = "Open Settings"

        AnimationUtils.attachTactileFeedback(binding.btnRationaleAllow, binding.btnRationaleDismiss)

        val dialog = AlertDialog.Builder(activity)
            .setView(binding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        AnimationUtils.applyDialogAnimation(dialog)

        binding.btnRationaleDismiss.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnRationaleAllow.setOnClickListener {
            dialog.dismiss()
            try {
                activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (_: Exception) {
                activity.startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        dialog.show()
    }

    fun showDndRationale(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return

        val binding = DialogPermissionRationaleBinding.inflate(LayoutInflater.from(activity))
        binding.ivRationaleIcon.setImageResource(R.drawable.ic_schedule)
        binding.tvRationaleTitle.text = "Auto-Silent Class Mode"
        binding.tvRationaleSubtitle.text = "Do Not Disturb Access"
        binding.tvRationaleMessage.text =
            "To automatically switch your phone to Silent or Vibrate mode when your timetable lectures start, and automatically restore normal volume when class finishes, Android requires Notification Policy access.\n\nFocus on your lectures without disruptive rings or manual hassle."
        binding.btnRationaleAllow.text = "Grant DND Access"

        AnimationUtils.attachTactileFeedback(binding.btnRationaleAllow, binding.btnRationaleDismiss)

        val dialog = AlertDialog.Builder(activity)
            .setView(binding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        AnimationUtils.applyDialogAnimation(dialog)

        binding.btnRationaleDismiss.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnRationaleAllow.setOnClickListener {
            dialog.dismiss()
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
            } catch (_: Exception) {
                activity.startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        dialog.show()
    }
}
