package com.duorhythm.app.ui.profile

import android.app.Activity
import androidx.fragment.app.FragmentActivity

class ProfileSwitcherDialog(
    private val activity: Activity,
    private val onProfileSwitched: (() -> Unit)? = null
) {
    fun show() {
        val fragmentActivity = activity as? FragmentActivity
        if (fragmentActivity != null) {
            WorkspaceProfilesBottomSheet.newInstance(onProfileSwitched)
                .show(fragmentActivity.supportFragmentManager, WorkspaceProfilesBottomSheet.TAG)
        }
    }
}
