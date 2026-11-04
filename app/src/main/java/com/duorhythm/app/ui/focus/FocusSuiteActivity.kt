package com.duorhythm.app.ui.focus

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.duorhythm.app.databinding.ActivityFocusSuiteBinding
import com.duorhythm.app.ui.tools.AttendanceCalendarBottomSheet
import com.duorhythm.app.ui.tools.CurrencyConverterDialog
import com.duorhythm.app.ui.tools.PercentageCalcDialog
import com.duorhythm.app.ui.tools.PomodoroDialog
import com.duorhythm.app.util.AnimationUtils

class FocusSuiteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFocusSuiteBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFocusSuiteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Seamless Status Bar Integration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.parseColor("#0A1322")
            window.navigationBarColor = Color.parseColor("#06080F")
        }

        binding.btnBackFocus.setOnClickListener { finish() }

        // 1. Pomodoro Focus Timer
        binding.cardPomodoroLaunch.setOnClickListener {
            PomodoroDialog(this).show()
        }

        // 2. Percentage & CGPA Calculator
        binding.cardCalculatorLaunch.setOnClickListener {
            try {
                val intent = Intent(this, Class.forName("com.duorhythm.app.ui.tools.CgpaCalculatorActivity"))
                startActivity(intent)
            } catch (_: Exception) {
                PercentageCalcDialog(this).show()
            }
        }

        // 3. Live Currency Converter
        binding.cardCurrencyLaunch.setOnClickListener {
            try {
                val intent = Intent(this, Class.forName("com.duorhythm.app.ui.tools.CurrencyConverterActivity"))
                startActivity(intent)
            } catch (_: Exception) {
                CurrencyConverterDialog(this).show()
            }
        }

        // 4. DND Focus Mode
        binding.cardDndLaunch.setOnClickListener {
            try {
                val intent = Intent(this, Class.forName("com.duorhythm.app.ui.focus.DndRulesActivity"))
                startActivity(intent)
            } catch (_: Exception) {
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
        }

        // 5. Class Attendance Log
        binding.cardAttendanceLaunch.setOnClickListener {
            try {
                val intent = Intent(this, Class.forName("com.duorhythm.app.ui.attendance.AttendanceLogActivity"))
                startActivity(intent)
            } catch (_: Exception) {
                AttendanceCalendarBottomSheet(this).show()
            }
        }

        AnimationUtils.attachTactileFeedback(
            binding.btnBackFocus,
            binding.cardPomodoroLaunch,
            binding.cardCalculatorLaunch,
            binding.cardCurrencyLaunch,
            binding.cardDndLaunch,
            binding.cardAttendanceLaunch
        )
    }
}
