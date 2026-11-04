package com.duorhythm.app.ui.focus

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.duorhythm.app.databinding.ActivityDndRulesBinding

class DndRulesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDndRulesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDndRulesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBackDnd.setOnClickListener { finish() }

        binding.switchAutoClassDnd.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!checkAndRequestDndPermission()) {
                    binding.switchAutoClassDnd.isChecked = false
                } else {
                    enableClassDndMode(this)
                }
            } else {
                disableClassDndMode(this)
            }
        }
    }

    private fun checkAndRequestDndPermission(): Boolean {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                AlertDialog.Builder(this)
                    .setTitle("Permission Required for System DND")
                    .setMessage("Android requires 'Do Not Disturb Access' to automatically mute your device and show the DND status bar icon during classes.")
                    .setPositiveButton("Grant Access") { _, _ ->
                        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        startActivity(intent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return false
            }
        }
        return true
    }

    companion object {
        fun enableClassDndMode(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notificationManager.isNotificationPolicyAccessGranted) {
                    // Triggers the real system ZenMode icon in status bar
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                } else {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                }
                Toast.makeText(context, "Class DND Mode Engaged", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to toggle DND: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        fun disableClassDndMode(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notificationManager.isNotificationPolicyAccessGranted) {
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                }
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                Toast.makeText(context, "Class DND Mode Disengaged", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {}
        }
    }
}
