package com.duorhythm.app.ui.focus

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.duorhythm.app.databinding.ActivityFocusSuiteBinding

class FocusSuiteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFocusSuiteBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFocusSuiteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.parseColor("#0A1322")
            window.navigationBarColor = Color.parseColor("#06080F")
        }

        binding.btnBackFocus.setOnClickListener { finish() }

        binding.cardPomodoroLaunch.setOnClickListener {
            PomodoroDialog().show(supportFragmentManager, "PomodoroDialog")
        }

        binding.cardCalculatorLaunch.setOnClickListener {
            try {
                val intent = Intent(this, Class.forName("com.duorhythm.app.ui.tools.CgpaCalculatorActivity"))
                startActivity(intent)
            } catch (_: Exception) {}
        }

        binding.cardCurrencyLaunch.setOnClickListener {
            try {
                val intent = Intent(this, Class.forName("com.duorhythm.app.ui.tools.CurrencyConverterActivity"))
                startActivity(intent)
            } catch (_: Exception) {}
        }

        binding.cardDndLaunch.setOnClickListener {
            try {
                val intent = Intent(this, Class.forName("com.duorhythm.app.ui.focus.DndRulesActivity"))
                startActivity(intent)
            } catch (_: Exception) {}
        }

        binding.cardAttendanceLaunch.setOnClickListener {
            try {
                val intent = Intent(this, Class.forName("com.duorhythm.app.ui.attendance.AttendanceLogActivity"))
                startActivity(intent)
            } catch (_: Exception) {}
        }
    }
}
