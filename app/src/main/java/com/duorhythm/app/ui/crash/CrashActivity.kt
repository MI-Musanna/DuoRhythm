package com.duorhythm.app.ui.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.duorhythm.app.MainActivity
import java.io.File

class CrashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val stackTrace = intent.getStringExtra(EXTRA_CRASH_INFO)
            ?: intent.getStringExtra("stacktrace")
            ?: run {
                try {
                    val crashLogFile = File(filesDir, "last_fatal_crash.txt")
                    if (crashLogFile.exists()) crashLogFile.readText() else "No stacktrace recorded."
                } catch (_: Throwable) {
                    "No stacktrace recorded."
                }
            }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F172A"))
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val tvTitle = TextView(this).apply {
            text = "DuoRhythm Crash Report"
            textSize = 20f
            setTextColor(Color.parseColor("#EF4444"))
            setTypeface(null, Typeface.BOLD)
            val padBottom = (8 * resources.displayMetrics.density).toInt()
            setPadding(0, 0, 0, padBottom)
        }
        rootLayout.addView(tvTitle)

        val tvSubtitle = TextView(this).apply {
            text = "The application encountered an unexpected error. Process: :crash_process"
            textSize = 14f
            setTextColor(Color.parseColor("#94A3B8"))
            val padBottom = (16 * resources.displayMetrics.density).toInt()
            setPadding(0, 0, 0, padBottom)
        }
        rootLayout.addView(tvSubtitle)

        val buttonBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val padBottom = (12 * resources.displayMetrics.density).toInt()
            setPadding(0, 0, 0, padBottom)
        }

        val btnRestart = Button(this).apply {
            text = "Restart App"
            setOnClickListener {
                val restartIntent = Intent(this@CrashActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(restartIntent)
                finish()
            }
        }
        buttonBar.addView(btnRestart)

        val btnCopy = Button(this).apply {
            text = "Copy Stacktrace"
            val leftMargin = (8 * resources.displayMetrics.density).toInt()
            (layoutParams as? ViewGroup.MarginLayoutParams)?.leftMargin = leftMargin
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Crash StackTrace", stackTrace)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@CrashActivity, "Stacktrace copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
        buttonBar.addView(btnCopy)

        rootLayout.addView(buttonBar)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(Color.parseColor("#1E293B"))
            val innerPad = (12 * resources.displayMetrics.density).toInt()
            setPadding(innerPad, innerPad, innerPad, innerPad)
        }

        val tvStackTrace = TextView(this).apply {
            text = stackTrace
            textSize = 12f
            setTextColor(Color.parseColor("#F1F5F9"))
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        scrollView.addView(tvStackTrace)
        rootLayout.addView(scrollView)

        setContentView(rootLayout)
    }

    companion object {
        const val EXTRA_CRASH_INFO = "extra_crash_info"
    }
}
