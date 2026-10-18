package com.duorhythm.app.ui.tools

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.duorhythm.app.MainActivity
import com.duorhythm.app.R
import com.duorhythm.app.databinding.FragmentToolsBinding

class ToolsFragment : Fragment() {

    private var _binding: FragmentToolsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentToolsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
    }

    private fun setupListeners() {
        binding.btnBackTools.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.cardToolPomodoro.setOnClickListener {
            PomodoroDialog(requireContext()).show()
        }

        binding.cardToolSound.setOnClickListener {
            AmbientSoundDialog(requireContext()).show()
        }

        binding.cardToolCalc.setOnClickListener {
            PercentageCalcDialog(requireContext()).show()
        }

        binding.cardToolCurrency.setOnClickListener {
            CurrencyConverterDialog(requireContext()).show()
        }

        binding.cardToolDND.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)
                startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_SOUND_SETTINGS)
                    startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(requireContext(), "Please adjust DND settings in system Settings > Sound", Toast.LENGTH_SHORT).show()
                }
            }
        }

        com.duorhythm.app.util.AnimationUtils.attachTactileFeedback(
            binding.btnBackTools,
            binding.cardToolPomodoro,
            binding.cardToolSound,
            binding.cardToolCalc,
            binding.cardToolCurrency,
            binding.cardToolDND
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
