package com.pocketdev.duorhythm.ui.common

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.pocketdev.duorhythm.ui.home.HomeFragment
import com.pocketdev.duorhythm.ui.portal.PortalFragment
import com.pocketdev.duorhythm.ui.schedule.ScheduleFragment
import com.pocketdev.duorhythm.ui.tasks.TasksFragment
import com.pocketdev.duorhythm.ui.wallet.WalletFragment

class MainPagerAdapter(private val activity: FragmentActivity) : FragmentStateAdapter(activity) {

    companion object {
        const val TAB_COUNT = 5
        const val INDEX_WALLET = 0
        const val INDEX_TASKS = 1
        const val INDEX_HOME = 2
        const val INDEX_SCHEDULE = 3
        const val INDEX_PORTAL = 4
    }

    private val fragments = mutableMapOf<Int, Fragment>()

    override fun getItemCount(): Int = TAB_COUNT

    override fun createFragment(position: Int): Fragment {
        val fragment = when (position) {
            INDEX_WALLET -> WalletFragment()
            INDEX_TASKS -> TasksFragment()
            INDEX_HOME -> HomeFragment()
            INDEX_SCHEDULE -> ScheduleFragment()
            INDEX_PORTAL -> PortalFragment()
            else -> HomeFragment()
        }
        fragments[position] = fragment
        return fragment
    }

    fun getFragment(position: Int): Fragment? {
        return fragments[position] ?: activity.supportFragmentManager.findFragmentByTag("f$position")
    }
}
