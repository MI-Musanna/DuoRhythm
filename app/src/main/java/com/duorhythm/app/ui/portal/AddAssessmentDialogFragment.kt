package com.duorhythm.app.ui.portal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.duorhythm.app.databinding.DialogAddAssessmentBinding

class AddAssessmentDialogFragment : DialogFragment() {

    private var _binding: DialogAddAssessmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogAddAssessmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun show(fragmentManager: androidx.fragment.app.FragmentManager, onDismiss: () -> Unit = {}) {
            val ctx = fragmentManager.fragments.firstOrNull()?.context ?: com.duorhythm.app.LifeSyncApp.instance
            ScheduleAssessmentDialog(ctx, fragmentManager = fragmentManager, onSaved = onDismiss).show()
        }
    }
}
