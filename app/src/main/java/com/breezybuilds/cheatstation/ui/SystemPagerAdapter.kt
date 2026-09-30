package com.breezybuilds.cheatstation.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class SystemPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ThreeDsPageFragment()
            1 -> Ps2PageFragment()
            2 -> PlaceholderSystemFragment.newInstance("Nintendo Wii")
            else -> PlaceholderSystemFragment.newInstance("Nintendo GameCube")
        }
    }
}
