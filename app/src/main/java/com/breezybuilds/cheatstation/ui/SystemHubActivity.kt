package com.breezybuilds.cheatstation.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class SystemHubActivity : RetroThemedActivity() {

    private lateinit var pager: ViewPager2
    private lateinit var tabs: TabLayout
    private lateinit var gameHeader: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = Ui.vbox(this).apply { setBackgroundColor(Ui.Appearance.background(this@SystemHubActivity)) }
        Ui.edgeToEdge(root)

        gameHeader = android.widget.LinearLayout(this).apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(
                Ui.dp(this@SystemHubActivity, 12),
                Ui.dp(this@SystemHubActivity, 6),
                Ui.dp(this@SystemHubActivity, 8),
                Ui.dp(this@SystemHubActivity, 6)
            )
        }

        val searchButton = Ui.tv(
            this,
            "⌕",
            24f,
            bold = true
        ).apply {
            gravity = android.view.Gravity.CENTER
            contentDescription = "Search games"
            setPadding(
                Ui.dp(this@SystemHubActivity, 8),
                0,
                Ui.dp(this@SystemHubActivity, 8),
                0
            )

            Ui.interact(this@SystemHubActivity, this) { showSearch() }
        }

        // Push the search and overflow actions to the right side of the toolbar.
        (gameHeader as android.widget.LinearLayout).addView(
            View(this),
            android.widget.LinearLayout.LayoutParams(
                0,
                1,
                1f
            )
        )

        (gameHeader as android.widget.LinearLayout).addView(
            searchButton,
            Ui.lp(
                Ui.dp(this, 44),
                Ui.dp(this, 44)
            )
        )

        val menuButton = Ui.tv(
            this,
            "⋮",
            24f,
            bold = true
        ).apply {
            gravity = android.view.Gravity.CENTER
            contentDescription = "Game options"
            setPadding(
                Ui.dp(this@SystemHubActivity, 8),
                0,
                Ui.dp(this@SystemHubActivity, 4),
                0
            )

            Ui.interact(this@SystemHubActivity, this) { showGameMenu(this) }
        }

        (gameHeader as android.widget.LinearLayout).addView(
            menuButton,
            Ui.lp(
                Ui.dp(this, 36),
                Ui.dp(this, 44)
            )
        )

        tabs = TabLayout(this).apply {
            setBackgroundColor(Ui.Appearance.surface(this@SystemHubActivity))
            setTabTextColors(Ui.Appearance.secondary(this@SystemHubActivity), Ui.Appearance.foreground(this@SystemHubActivity))
            setSelectedTabIndicatorColor(Ui.Appearance.action(this@SystemHubActivity))
            tabRippleColor = android.content.res.ColorStateList.valueOf(Ui.Appearance.raised(this@SystemHubActivity))
        }

        pager = ViewPager2(this).apply {
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
        }

        root.addView(tabs, Ui.lp())
        root.addView(gameHeader, Ui.lp())
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
                }
            }
        )

    }

    private fun showSearch() {
        val search = SearchView(this).apply {
            queryHint = "Search games"
            setIconifiedByDefault(false)

            setOnQueryTextListener(
                object : SearchView.OnQueryTextListener {
                    override fun onQueryTextSubmit(
                        query: String
                    ): Boolean {
                        setSearchQuery(query)
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

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Search games")
            .setView(search)
            .setNegativeButton("Close", null)
            .create()

        dialog.setOnShowListener {
            search.requestFocus()
        }

        dialog.show()
    }

    private fun showGameMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)

        popup.menu.add(
            Menu.NONE,
            101,
            1,
            "Rescan games"
        )

        popup.menu.add(
            Menu.NONE,
            104,
            2,
            "Settings"
        )

        popup.setOnMenuItemClickListener { item ->
            handleMenuAction(item.itemId)
        }

        popup.show()
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
