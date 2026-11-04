package com.duorhythm.app.ui.focus

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.duorhythm.app.databinding.ActivityFocusSuiteBinding
import com.duorhythm.app.ui.tools.PercentageCalcDialog
import com.duorhythm.app.ui.tools.PomodoroDialog
import com.duorhythm.app.util.AnimationUtils

class FocusSuiteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFocusSuiteBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.parseColor("#0A1322")
            window.navigationBarColor = Color.parseColor("#06080F")
        }
        binding = ActivityFocusSuiteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBackFocus.setOnClickListener { finish() }

        binding.cardPomodoroLaunch.setOnClickListener {
            PomodoroDialog(this).show()
        }

        binding.cardCalculatorLaunch.setOnClickListener {
            PercentageCalcDialog(this).show()
        }

        binding.cardDndLaunch.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)
                startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_SOUND_SETTINGS)
                    startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(this, "Please adjust DND settings in system Settings > Sound", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.cardAttendanceLaunch.setOnClickListener {
            com.duorhythm.app.ui.tools.AttendanceCalendarBottomSheet(this).show()
        }

        AnimationUtils.attachTactileFeedback(
            binding.btnBackFocus,
            binding.cardPomodoroLaunch,
            binding.cardCalculatorLaunch,
            binding.cardDndLaunch,
            binding.cardAttendanceLaunch
        )
    }
}
