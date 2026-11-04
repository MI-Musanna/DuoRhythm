package com.duorhythm.app.ui.tools

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.widget.Toast
import com.duorhythm.app.R
import com.duorhythm.app.databinding.DialogPomodoroTimerBinding
import com.duorhythm.app.util.AnimationUtils
import java.util.Locale

class PomodoroDialog(private val context: Context) {

    private enum class Mode(val durationMs: Long, val stateText: String) {
        FOCUS(25 * 60 * 1000L, "Deep Flow State"),
        SHORT_BREAK(5 * 60 * 1000L, "Short Rest & Refresh"),
        LONG_BREAK(15 * 60 * 1000L, "Extended Recharge")
    }

    private var currentMode = Mode.FOCUS
    private var countDownTimer: CountDownTimer? = null
    private var isTimerRunning = false
    private var timeLeftInMillis: Long = currentMode.durationMs
    private var initialTimeInMillis: Long = currentMode.durationMs
    private var completedSessions = 0

    fun createDialog(): AlertDialog {
        val binding = DialogPomodoroTimerBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .create()

        AnimationUtils.applyDialogAnimation(dialog)
        AnimationUtils.attachTactileFeedback(
            binding.btnStartTimer,
            binding.btnResetTimer,
            binding.tabFocusMode,
            binding.tabShortBreak,
            binding.tabLongBreak
        )

        fun updateTimerText() {
            val totalSeconds = (timeLeftInMillis + 999) / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            binding.tvPomodoroTime.text = String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }

        fun updateModePillsUI() {
            val activeBg = R.drawable.bg_pill_blue
            val activeColor = Color.WHITE
            val inactiveColor = Color.parseColor("#64748B")

            binding.tabFocusMode.background = if (currentMode == Mode.FOCUS) context.getDrawable(activeBg) else null
            binding.tabFocusMode.setTextColor(if (currentMode == Mode.FOCUS) activeColor else inactiveColor)

            binding.tabShortBreak.background = if (currentMode == Mode.SHORT_BREAK) context.getDrawable(activeBg) else null
            binding.tabShortBreak.setTextColor(if (currentMode == Mode.SHORT_BREAK) activeColor else inactiveColor)

            binding.tabLongBreak.background = if (currentMode == Mode.LONG_BREAK) context.getDrawable(activeBg) else null
            binding.tabLongBreak.setTextColor(if (currentMode == Mode.LONG_BREAK) activeColor else inactiveColor)
        }

        fun selectMode(mode: Mode) {
            countDownTimer?.cancel()
            isTimerRunning = false
            currentMode = mode
            initialTimeInMillis = mode.durationMs
            timeLeftInMillis = initialTimeInMillis

            val maxSeconds = (initialTimeInMillis / 1000).toInt()
            binding.pbPomodoroRing.max = maxSeconds
            binding.pbPomodoroRing.progress = maxSeconds

            binding.tvPomodoroState.text = mode.stateText
            binding.btnStartTimer.text = "START"
            updateModePillsUI()
            updateTimerText()
        }

        fun startTimer() {
            countDownTimer?.cancel()
            val totalSeconds = (initialTimeInMillis / 1000).toInt()
            binding.pbPomodoroRing.max = totalSeconds

            countDownTimer = object : CountDownTimer(timeLeftInMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    timeLeftInMillis = millisUntilFinished
                    binding.pbPomodoroRing.progress = ((millisUntilFinished + 999) / 1000).toInt()
                    updateTimerText()
                }

                override fun onFinish() {
                    isTimerRunning = false
                    binding.pbPomodoroRing.progress = 0
                    if (currentMode == Mode.FOCUS) {
                        completedSessions++
                        val sessionDisplay = (completedSessions % 4) + 1
                        binding.tvSessionCounter.text = "Session $sessionDisplay of 4 • Stay locked in"
                    }
                    binding.tvPomodoroState.text = "Session Complete!"
                    binding.btnStartTimer.text = "START"
                    timeLeftInMillis = initialTimeInMillis
                    updateTimerText()

                    try {
                        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                        vibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                    } catch (_: Exception) {}

                    Toast.makeText(context, "Pomodoro cycle complete!", Toast.LENGTH_LONG).show()
                }
            }.start()

            isTimerRunning = true
            binding.btnStartTimer.text = "PAUSE"
            binding.tvPomodoroState.text = currentMode.stateText
        }

        fun pauseTimer() {
            countDownTimer?.cancel()
            isTimerRunning = false
            binding.btnStartTimer.text = "RESUME"
            binding.tvPomodoroState.text = "Paused"
        }

        fun resetTimer() {
            countDownTimer?.cancel()
            isTimerRunning = false
            timeLeftInMillis = initialTimeInMillis
            val maxSeconds = (initialTimeInMillis / 1000).toInt()
            binding.pbPomodoroRing.max = maxSeconds
            binding.pbPomodoroRing.progress = maxSeconds
            updateTimerText()
            binding.btnStartTimer.text = "START"
            binding.tvPomodoroState.text = currentMode.stateText
        }

        binding.tabFocusMode.setOnClickListener { selectMode(Mode.FOCUS) }
        binding.tabShortBreak.setOnClickListener { selectMode(Mode.SHORT_BREAK) }
        binding.tabLongBreak.setOnClickListener { selectMode(Mode.LONG_BREAK) }

        binding.btnStartTimer.setOnClickListener {
            if (isTimerRunning) {
                pauseTimer()
            } else {
                startTimer()
            }
        }

        binding.btnResetTimer.setOnClickListener {
            resetTimer()
        }

        dialog.setOnDismissListener {
            countDownTimer?.cancel()
        }

        // Initialize state
        selectMode(Mode.FOCUS)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    fun show() {
        createDialog().show()
    }
}
