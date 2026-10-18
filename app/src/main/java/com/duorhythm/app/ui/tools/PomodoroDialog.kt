package com.duorhythm.app.ui.tools

import android.app.AlertDialog
import android.content.Context
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.widget.Toast
import com.duorhythm.app.R
import com.duorhythm.app.databinding.DialogPomodoroBinding
import java.util.Locale

class PomodoroDialog(private val context: Context) {

    private var countDownTimer: CountDownTimer? = null
    private var isTimerRunning = false
    private var timeLeftInMillis: Long = 25 * 60 * 1000L
    private var initialTimeInMillis: Long = 25 * 60 * 1000L
    private var completedSessions = 0

    fun show() {
        val binding = DialogPomodoroBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .create()

        com.duorhythm.app.util.AnimationUtils.applyDialogAnimation(dialog)
        com.duorhythm.app.util.AnimationUtils.attachTactileFeedback(
            binding.btnPomoStart,
            binding.btnPomoReset
        )

        fun updateTimerText() {
            val minutes = (timeLeftInMillis / 1000) / 60
            val seconds = (timeLeftInMillis / 1000) % 60
            binding.tvPomoTimer.text = String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }

        fun startTimer() {
            countDownTimer = object : CountDownTimer(timeLeftInMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    timeLeftInMillis = millisUntilFinished
                    updateTimerText()
                }

                override fun onFinish() {
                    isTimerRunning = false
                    completedSessions++
                    binding.tvPomoSessions.text = "Completed sessions: $completedSessions"
                    binding.tvPomoStatus.text = "Session complete! Great work."
                    binding.btnPomoStart.text = "Start"

                    // Alert user with vibration
                    try {
                        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                        vibrator?.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                    } catch (_: Exception) {}

                    Toast.makeText(context, "Pomodoro cycle complete!", Toast.LENGTH_LONG).show()
                }
            }.start()

            isTimerRunning = true
            binding.btnPomoStart.text = "Pause"
            binding.tvPomoStatus.text = "Focus in progress..."
        }

        fun pauseTimer() {
            countDownTimer?.cancel()
            isTimerRunning = false
            binding.btnPomoStart.text = "Resume"
            binding.tvPomoStatus.text = "Paused"
        }

        fun resetTimer() {
            countDownTimer?.cancel()
            isTimerRunning = false
            timeLeftInMillis = initialTimeInMillis
            updateTimerText()
            binding.btnPomoStart.text = "Start"
            binding.tvPomoStatus.text = "Ready to focus"
        }

        binding.togglePomoMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                countDownTimer?.cancel()
                isTimerRunning = false
                when (checkedId) {
                    R.id.btnPomoFocus -> {
                        initialTimeInMillis = 25 * 60 * 1000L
                        binding.tvPomoStatus.text = "Focus Time (25m)"
                    }
                    R.id.btnPomoShortBreak -> {
                        initialTimeInMillis = 5 * 60 * 1000L
                        binding.tvPomoStatus.text = "Short Rest (5m)"
                    }
                    R.id.btnPomoLongBreak -> {
                        initialTimeInMillis = 15 * 60 * 1000L
                        binding.tvPomoStatus.text = "Long Rest (15m)"
                    }
                }
                timeLeftInMillis = initialTimeInMillis
                updateTimerText()
                binding.btnPomoStart.text = "Start"
            }
        }

        binding.btnPomoStart.setOnClickListener {
            if (isTimerRunning) {
                pauseTimer()
            } else {
                startTimer()
            }
        }

        binding.btnPomoReset.setOnClickListener {
            resetTimer()
        }

        dialog.setOnDismissListener {
            countDownTimer?.cancel()
        }

        updateTimerText()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
    }
}
