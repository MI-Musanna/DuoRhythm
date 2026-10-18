package com.duorhythm.app.ui.common

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.duorhythm.app.ui.home.HomeFragment
import com.duorhythm.app.ui.portal.PortalFragment
import com.duorhythm.app.ui.schedule.ScheduleFragment
import com.duorhythm.app.ui.tasks.TasksFragment
import com.duorhythm.app.ui.wallet.WalletFragment

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
