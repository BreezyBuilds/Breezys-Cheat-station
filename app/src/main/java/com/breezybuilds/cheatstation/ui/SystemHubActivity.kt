package com.breezybuilds.cheatstation.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class SystemHubActivity : AppCompatActivity() {

    private lateinit var pager: ViewPager2
    private lateinit var tabs: TabLayout
    private lateinit var toolbar: MaterialToolbar
    private var searchItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = Ui.vbox(this)
        Ui.edgeToEdge(root)

        toolbar = MaterialToolbar(this).apply {
            title = "Breezy's Cheat Station"

            val search = menu.add(
                Menu.NONE,
                100,
                0,
                "Search games"
            ).apply {
                setIcon(android.R.drawable.ic_menu_search)
                setShowAsAction(
                    MenuItem.SHOW_AS_ACTION_ALWAYS
                )
            }

            val searchView = SearchView(this@SystemHubActivity).apply {
                queryHint = "Search games"
                isIconified = true

                setOnQueryTextListener(
                    object : SearchView.OnQueryTextListener {
                        override fun onQueryTextSubmit(
                            query: String
                        ): Boolean {
                            setSearchQuery(query)
                            clearFocus()
                            return true
                        }

                        override fun onQueryTextChange(
                            newText: String
                        ): Boolean {
                            setSearchQuery(newText)
                            return true
                        }
                    }
                )
            }

            search.actionView = searchView
            searchItem = search

            setOnMenuItemClickListener { item ->
                handleMenuAction(item.itemId)
            }
        }

        tabs = TabLayout(this)

        pager = ViewPager2(this).apply {
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
        }

        root.addView(toolbar, Ui.lp())
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

        TabLayoutMediator(tabs, pager) { tab, position ->
            tab.text = when (position) {
                0 -> "3DS"
                1 -> "PS2"
                2 -> "Wii"
                else -> "GameCube"
            }
        }.attach()

        pager.setCurrentItem(
            intent.getIntExtra("system", 0),
            false
        )

        pager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    updateToolbar(position)
                }
            }
        )

        updateToolbar(pager.currentItem)
    }

    private fun updateToolbar(position: Int) {
        val search = searchItem?.actionView as? SearchView

        search?.setQuery("", false)
        search?.clearFocus()
        search?.isIconified = true

        toolbar.menu.removeItem(101)
        toolbar.menu.removeItem(102)
        toolbar.menu.removeItem(103)
        toolbar.menu.removeItem(104)
        toolbar.menu.removeItem(105)

        when (position) {
            0 -> {
                toolbar.menu.add(
                    Menu.NONE,
                    101,
                    1,
                    "Rescan games"
                )

                toolbar.menu.add(
                    Menu.NONE,
                    102,
                    2,
                    "Add game by Title ID"
                )

                toolbar.menu.add(
                    Menu.NONE,
                    103,
                    3,
                    "Refresh cheat database"
                )

                toolbar.menu.add(
                    Menu.NONE,
                    104,
                    4,
                    "Settings"
                )
            }

            1 -> {
                toolbar.menu.add(
                    Menu.NONE,
                    101,
                    1,
                    "Rescan games"
                )

                toolbar.menu.add(
                    Menu.NONE,
                    105,
                    2,
                    "Cheat source"
                )

                toolbar.menu.add(
                    Menu.NONE,
                    104,
                    3,
                    "Settings"
                )
            }

            2, 3 -> {
                toolbar.menu.add(
                    Menu.NONE,
                    101,
                    1,
                    "Rescan games"
                )
            }
        }
    }

    private fun handleMenuAction(itemId: Int): Boolean {
        val fragment = currentFragment()

        return when (itemId) {
            101 -> {
                when (fragment) {
                    is ThreeDsPageFragment -> fragment.rescanGames()
                    is Ps2PageFragment -> fragment.rescanGames()
                    is DolphinPageFragment -> fragment.rescanGames()
                }
                true
            }

            102 -> {
                (fragment as? ThreeDsPageFragment)
                    ?.addGameByTitleId()
                true
            }

            103 -> {
                (fragment as? ThreeDsPageFragment)
                    ?.refreshCheatDatabase()
                true
            }

            104 -> {
                startActivity(
                    android.content.Intent(
                        this,
                        SettingsActivity::class.java
                    )
                )
                true
            }

            105 -> {
                (fragment as? Ps2PageFragment)
                    ?.openCheatSource()
                true
            }

            else -> false
        }
    }

    private fun setSearchQuery(query: String) {
        when (val fragment = currentFragment()) {
            is ThreeDsPageFragment ->
                fragment.setSearchQuery(query)

            is Ps2PageFragment ->
                fragment.setSearchQuery(query)

            is DolphinPageFragment ->
                fragment.filterGames(query)
        }
    }

    private fun currentFragment(): Fragment? {
        return (pager.adapter as? SystemPagerAdapter)
            ?.fragmentAt(pager.currentItem)
    }
}
