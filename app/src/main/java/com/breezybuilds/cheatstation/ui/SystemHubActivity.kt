package com.breezybuilds.cheatstation.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class SystemHubActivity : AppCompatActivity() {

    private lateinit var pager: ViewPager2
    private lateinit var tabs: TabLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = Ui.vbox(this)
        Ui.edgeToEdge(root)

        tabs = TabLayout(this).apply {
            addTab(newTab().setText("3DS"))
            addTab(newTab().setText("PS2"))
            addTab(newTab().setText("Wii"))
            addTab(newTab().setText("GameCube"))
        }

        pager = ViewPager2(this).apply {
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
        }

        root.addView(tabs, Ui.lp())
        root.addView(
            pager,
            Ui.lp(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)

        pager.adapter = SystemPagerAdapter(this)

        pager.setCurrentItem(intent.getIntExtra("system", 0), false)

        TabLayoutMediator(tabs, pager) { tab, position ->
            tab.text = when (position) {
                0 -> "3DS"
                1 -> "PS2"
                2 -> "Wii"
                else -> "GameCube"
            }
        }.attach()

        pager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    tabs.getTabAt(position)?.select()
                }
            }
        )
    }
}
