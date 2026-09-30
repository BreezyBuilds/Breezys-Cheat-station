package com.breezybuilds.cheatstation.ui

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.breezybuilds.cheatstation.App
import com.breezybuilds.cheatstation.cheats.dolphin.DolphinCheatInstaller
import com.breezybuilds.cheatstation.model.Cheat
import com.breezybuilds.cheatstation.provider.DolphinCheatProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DolphinCheatsActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var search: EditText
    private lateinit var adapter: DolphinCheatAdapter
    private lateinit var buttons: List<View>

    private val selected = LinkedHashSet<String>()

    private var titleId = ""
    private var loadedCheats: List<Cheat> = emptyList()
    private var installedNames: Set<String> = emptySet()
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gameTitle = intent.getStringExtra("title") ?: "Game"
        titleId = intent.getStringExtra("titleId") ?: ""
        val platform = intent.getStringExtra("platform") ?: ""

        val root = Ui.vbox(this)
        Ui.edgeToEdge(root)

        val toolbar = MaterialToolbar(this).apply {
            title = "Cheats"

            menu.add(Menu.NONE, 1, 1, "Refresh cheats")
            menu.add(Menu.NONE, 2, 2, "Select all")
            menu.add(Menu.NONE, 3, 3, "Select none")

            menu.add(
                Menu.NONE,
                4,
                0,
                "Search cheats"
            ).apply {
                setIcon(android.R.drawable.ic_menu_search)
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }

            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> loadCheats()

                    2 -> {
                        selected.addAll(
                            visibleCheats().map { cheat -> cheat.key }
                        )
                        adapter.notifyDataSetChanged()
                    }

                    3 -> {
                        selected.clear()
                        adapter.notifyDataSetChanged()
                    }

                    4 -> {
                        val showing =
                            search.visibility == View.VISIBLE

                        search.visibility =
                            if (showing) View.GONE else View.VISIBLE

                        if (showing) {
                            search.text.clear()
                            search.clearFocus()
                        } else {
                            search.requestFocus()
                        }
                    }
                }

                true
            }
        }

        root.addView(toolbar, Ui.lp())

        val header = Ui.tv(
            this,
            "$gameTitle\n$titleId • ${platform.uppercase()}",
            14f
        ).apply {
            setPadding(
                Ui.dp(this@DolphinCheatsActivity, 16),
                Ui.dp(this@DolphinCheatsActivity, 8),
                Ui.dp(this@DolphinCheatsActivity, 16),
                Ui.dp(this@DolphinCheatsActivity, 6)
            )
        }

        root.addView(header, Ui.lp())

        search = EditText(this).apply {
            hint = "Search cheats"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()

            addTextChangedListener(
                object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        adapter.notifyDataSetChanged()
                    }

                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) {}

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) {}
                }
            )
        }

        search.visibility = View.GONE

        root.addView(
            search,
            Ui.lp().apply {
                setMargins(
                    Ui.dp(this@DolphinCheatsActivity, 12),
                    0,
                    Ui.dp(this@DolphinCheatsActivity, 12),
                    Ui.dp(this@DolphinCheatsActivity, 6)
                )
            }
        )

        status = Ui.tv(
            this,
            "Loading Dolphin cheats…",
            13f,
            secondary = true
        ).apply {
            setPadding(
                Ui.dp(this@DolphinCheatsActivity, 16),
                Ui.dp(this@DolphinCheatsActivity, 2),
                Ui.dp(this@DolphinCheatsActivity, 16),
                Ui.dp(this@DolphinCheatsActivity, 6)
            )
        }

        root.addView(status, Ui.lp())

        adapter = DolphinCheatAdapter(
            { visibleCheats() },
            selected,
            { key, checked ->
                if (checked) {
                    selected.add(key)
                } else {
                    selected.remove(key)
                }
            },
            installedNames
        )

        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@DolphinCheatsActivity)
            adapter = this@DolphinCheatsActivity.adapter
            clipToPadding = false
        }

        root.addView(
            list,
            Ui.lp(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val actionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                Ui.dp(this@DolphinCheatsActivity, 8),
                Ui.dp(this@DolphinCheatsActivity, 6),
                Ui.dp(this@DolphinCheatsActivity, 8),
                Ui.dp(this@DolphinCheatsActivity, 6)
            )
        }

        val installSelected =
            Ui.button(this, "Install Selected") {
                installSelected()
            }

        val installAll =
            Ui.button(this, "Install All", filled = false) {
                installAll()
            }

        val removeSelected =
            Ui.button(this, "Remove Selected", filled = false) {
                removeSelected()
            }

        buttons = listOf(
            installSelected,
            installAll,
            removeSelected
        )

        buttons.forEach { button ->
            actionBar.addView(
                button,
                Ui.lp(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(
                        Ui.dp(this@DolphinCheatsActivity, 3),
                        0,
                        Ui.dp(this@DolphinCheatsActivity, 3),
                        0
                    )
                }
            )
        }

        root.addView(actionBar, Ui.lp())

        setContentView(root)

        loadCheats()
    }

    private fun loadCheats() {
        if (busy) return

        setBusy(true)
        status.text = "Loading Dolphin cheats…"

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    DolphinCheatProvider({ (application as App).dolphinSettings.sources() }).fetch(titleId)
                }

                loadedCheats = result.cheats

                installedNames = withContext(Dispatchers.IO) {
                    DolphinCheatInstaller(
                        this@DolphinCheatsActivity,
                        (application as App).dolphinStorage
                    ).installedNames(titleId)
                }

                selected.clear()

                adapter.updateInstalled(installedNames)
                adapter.notifyDataSetChanged()

                status.text = when {
                    result.cheats.isEmpty() ->
                        "No cheats found for $titleId."

                    installedNames.isEmpty() ->
                        "${result.cheats.size} cheat(s) found • None installed"

                    else ->
                        "${result.cheats.size} cheat(s) found • " +
                            "${installedNames.size} installed"
                }

                if (result.warnings.isNotEmpty()) {
                    status.append(
                        "\n${result.warnings.size} warning(s)"
                    )
                }

                setBusy(false)
            } catch (e: Exception) {
                loadedCheats = emptyList()
                selected.clear()
                adapter.updateInstalled(emptySet())
                adapter.notifyDataSetChanged()

                status.text =
                    e.message ?: "Could not load Dolphin cheats."

                setBusy(false)
            }
        }
    }

    private fun installSelected() {
        val cheats = loadedCheats.filter {
            it.key in selected
        }

        if (cheats.isEmpty()) {
            status.text = "Select at least one cheat first."
            return
        }

        applyCheats(
            cheats,
            "Installing ${cheats.size} selected cheat(s)…"
        )
    }

    private fun installAll() {
        if (loadedCheats.isEmpty()) {
            status.text = "No cheats available to install."
            return
        }

        applyCheats(
            loadedCheats,
            "Installing all ${loadedCheats.size} cheat(s)…"
        )
    }

    private fun applyCheats(
        cheats: List<Cheat>,
        message: String
    ) {
        if (busy) return

        setBusy(true)
        status.text = message

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                DolphinCheatInstaller(
                    this@DolphinCheatsActivity,
                    (application as App).dolphinStorage
                ).apply(
                    titleId,
                    cheats.map {
                        it.copy(enabled = true)
                    }
                )
            }

            result.fold(
                onSuccess = {
                    selected.clear()
                    setBusy(false)
                    loadCheats()
                },
                onFailure = { error ->
                    status.text =
                        error.message ?: "Could not install Dolphin cheats."
                    setBusy(false)
                }
            )
        }
    }

    private fun removeSelected() {
        val cheats = loadedCheats.filter {
            it.key in selected
        }

        if (cheats.isEmpty()) {
            status.text = "Select at least one cheat first."
            return
        }

        if (busy) return

        setBusy(true)
        status.text =
            "Removing ${cheats.size} selected cheat(s)…"

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                DolphinCheatInstaller(
                    this@DolphinCheatsActivity,
                    (application as App).dolphinStorage
                ).remove(titleId, cheats)
            }

            result.fold(
                onSuccess = {
                    selected.clear()
                    setBusy(false)
                    loadCheats()
                },
                onFailure = { error ->
                    status.text =
                        error.message ?: "Could not remove Dolphin cheats."
                    setBusy(false)
                }
            )
        }
    }

    private fun visibleCheats(): List<Cheat> {
        val query = search.text
            .toString()
            .trim()

        if (query.isBlank()) {
            return loadedCheats
        }

        return loadedCheats.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.key.contains(query, ignoreCase = true)
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        buttons.forEach {
            it.isEnabled = !value
        }
    }
}

private class DolphinCheatAdapter(
    private val source: () -> List<Cheat>,
    private val selected: Set<String>,
    private val onSelected: (String, Boolean) -> Unit,
    private var installed: Set<String>
) : RecyclerView.Adapter<DolphinCheatAdapter.VH>() {

    fun updateInstalled(names: Set<String>) {
        installed = names
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): VH {
        val ctx = parent.context

        val card = MaterialCardView(ctx).apply {
            radius = Ui.dp(ctx, 14).toFloat()
            setCardElevation(Ui.dp(ctx, 1).toFloat())

            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(
                    Ui.dp(ctx, 8),
                    Ui.dp(ctx, 4),
                    Ui.dp(ctx, 8),
                    Ui.dp(ctx, 4)
                )
            }
        }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                Ui.dp(ctx, 8),
                Ui.dp(ctx, 10),
                Ui.dp(ctx, 12),
                Ui.dp(ctx, 10)
            )
        }

        val check = CheckBox(ctx)

        val textBox = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }

        val name = Ui.tv(
            ctx,
            "",
            15f,
            bold = true
        )

        val state = Ui.tv(
            ctx,
            "",
            12f,
            secondary = true
        )

        textBox.addView(name, Ui.lp())
        textBox.addView(state, Ui.lp())

        row.addView(
            check,
            Ui.lp(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        row.addView(
            textBox,
            Ui.lp(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        card.addView(row)

        return VH(card, check, name, state)
    }

    override fun onBindViewHolder(
        holder: VH,
        position: Int
    ) {
        holder.bind(source()[position])
    }

    override fun getItemCount(): Int =
        source().size

    inner class VH(
        view: View,
        private val check: CheckBox,
        private val name: TextView,
        private val state: TextView
    ) : RecyclerView.ViewHolder(view) {

        fun bind(cheat: Cheat) {
            check.setOnCheckedChangeListener(null)
            check.isChecked = cheat.key in selected

            name.text = cheat.name

            state.text =
                if (cheat.name in installed) {
                    "Installed"
                } else {
                    "${cheat.codeLines.size} code line(s)"
                }

            check.setOnCheckedChangeListener { _, checked ->
                onSelected(cheat.key, checked)
            }
        }
    }
}
