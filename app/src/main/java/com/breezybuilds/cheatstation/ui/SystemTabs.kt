package com.breezybuilds.cheatstation.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.google.android.material.tabs.TabLayout

object SystemTabs {

    fun create(
        context: Context,
        selected: Int
    ): TabLayout {
        return TabLayout(context).apply {
            addTab(newTab().setText("3DS"), selected == 0)
            addTab(newTab().setText("PS2"), selected == 1)
            addTab(newTab().setText("Wii"), selected == 2)
            addTab(newTab().setText("GameCube"), selected == 3)

            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    when (tab.position) {
                        0 -> {
                            if (selected != 0) {
                                context.startActivity(
                                    Intent(context, MainActivity::class.java)
                                )
                            }
                        }

                        1 -> {
                            if (selected != 1) {
                                context.startActivity(
                                    Intent(context, Ps2Activity::class.java)
                                )
                            }
                        }

                        2 -> {
                            if (selected != 2) {
                                Toast.makeText(
                                    context,
                                    "Wii support is coming soon.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        3 -> {
                            if (selected != 3) {
                                Toast.makeText(
                                    context,
                                    "GameCube support is coming soon.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }

                override fun onTabUnselected(tab: TabLayout.Tab) = Unit

                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            })
        }
    }
}
