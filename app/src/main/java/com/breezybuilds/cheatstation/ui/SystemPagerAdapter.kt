package com.breezybuilds.cheatstation.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.breezybuilds.cheatstation.scan.DolphinPlatform

class SystemPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {

    private val fragments = mutableMapOf<Int, Fragment>()

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        val fragment = when (position) {
            0 -> ThreeDsPageFragment()
            1 -> Ps2PageFragment()
            2 -> DolphinPageFragment(DolphinPlatform.WII)
            else -> DolphinPageFragment(DolphinPlatform.GAMECUBE)
        }

        fragments[position] = fragment
        return fragment
    }

    fun fragmentAt(position: Int): Fragment? = fragments[position]
}
