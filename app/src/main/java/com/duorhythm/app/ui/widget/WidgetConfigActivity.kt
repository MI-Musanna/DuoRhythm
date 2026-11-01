package com.duorhythm.app.ui.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.duorhythm.app.R
import com.duorhythm.app.databinding.ActivityWidgetConfigBinding
import com.duorhythm.app.widget.AcademicHubWidget

class WidgetConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWidgetConfigBinding
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWidgetConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ensure proper result code if user cancels without saving
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val prefs = getSharedPreferences("widget_academic_prefs", Context.MODE_PRIVATE)

        // Load saved state
        val savedHour = prefs.getInt("transition_hour", 17)
        when (savedHour) {
            16 -> binding.rbHour16.isChecked = true
            18 -> binding.rbHour18.isChecked = true
            else -> binding.rbHour17.isChecked = true
        }

        binding.switchShowRoomBadge.isChecked = prefs.getBoolean("show_room", true)
        binding.switchCompactMode.isChecked = prefs.getBoolean("compact_mode", false)

        binding.btnBackConfig.setOnClickListener { finish() }

        binding.btnSaveConfig.setOnClickListener {
            val selectedHour = when (binding.rgTransitionHour.checkedRadioButtonId) {
                R.id.rbHour16 -> 16
                R.id.rbHour18 -> 18
                else -> 17
            }

            prefs.edit()
                .putInt("transition_hour", selectedHour)
                .putBoolean("show_room", binding.switchShowRoomBadge.isChecked)
                .putBoolean("compact_mode", binding.switchCompactMode.isChecked)
                .apply()

            // Push refresh to homescreen widget
            AcademicHubWidget.pushUpdate(this)

            Toast.makeText(this, "Academic Hub Widget updated", Toast.LENGTH_SHORT).show()

            val resultValue = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }
}
