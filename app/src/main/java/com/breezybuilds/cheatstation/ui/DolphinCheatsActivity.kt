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
import android.widget.ScrollView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.breezybuilds.cheatstation.App
import com.breezybuilds.cheatstation.cheats.dolphin.DolphinCheatInstaller
import com.breezybuilds.cheatstation.model.Cheat
import com.breezybuilds.cheatstation.provider.DolphinCheatProvider
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DolphinCheatsActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var search: EditText
    private lateinit var adapter: DolphinCheatAdapter
    private lateinit var buttons: List<View>

    private val selected = LinkedHashSet<String>()

    private lateinit var gameTitle: String
    private var titleId = ""
    private var loadedCheats: List<Cheat> = emptyList()
    private var installedNames: Set<String> = emptySet()
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gameTitle = intent.getStringExtra("title") ?: "Game"
        titleId = intent.getStringExtra("titleId") ?: ""
        val platform = intent.getStringExtra("platform") ?: ""

        val root = Ui.vbox(this)
        Ui.edgeToEdge(root)

        val toolbar = MaterialToolbar(this).apply {
            title = "Cheats"

            menu.add(Menu.NONE, 1, 1, "Refresh cheats")
            menu.add(Menu.NONE, 2, 2, "Select all")
            menu.add(Menu.NONE, 3, 3, "Select none")
            menu.add(Menu.NONE, 5, 5, "Install all cheats")

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

                    5 -> installAll()

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
            gameTitle,
            18f
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
            installedNames,
            ::showCheatDetails
        )

        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@DolphinCheatsActivity)
            adapter = this@DolphinCheatsActivity.adapter
            clipToPadding = false
            setPadding(0, 0, 0, Ui.dp(this@DolphinCheatsActivity, 16))
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

        val removeSelected =
            Ui.button(this, "Remove Selected", filled = false) {
                removeSelected()
            }

        buttons = listOf(
            installSelected,
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
                        "No cheats available • ${installedNames.size} installed"

                    else ->
                        "${result.cheats.size} ${if (result.cheats.size == 1) "cheat" else "cheats"} available • " +
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
        val cheats = loadedCheats.filter { it.key in selected && it.name in installedNames }
        if (cheats.isEmpty()) {
            status.text = "Select at least one installed cheat first."
            return
        }
        confirmRemoval(cheats)
    }

    private fun confirmRemoval(cheats: List<Cheat>) {
        if (busy || cheats.isEmpty()) return
        MaterialAlertDialogBuilder(this)
            .setTitle("Remove ${cheats.size} cheat(s)?")
            .setMessage("Remove the selected cheats from Dolphin's game settings? Review the names before continuing.\n\n" + cheats.joinToString("\n") { "• ${it.name}" })
            .setPositiveButton("Remove") { _, _ -> removeCheats(cheats) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun removeCheats(cheats: List<Cheat>) {
        if (busy) return
        setBusy(true)
        status.text = "Removing ${cheats.size} cheat(s)…"
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                DolphinCheatInstaller(this@DolphinCheatsActivity, (application as App).dolphinStorage)
                    .remove(titleId, cheats)
            }
            result.fold(
                onSuccess = {
                    selected.clear()
                    setBusy(false)
                    loadCheats()
                },
                onFailure = { error ->
                    status.text = error.message ?: "Could not remove Dolphin cheats."
                    setBusy(false)
                }
            )
        }
    }

    private fun showCheatDetails(cheat: Cheat) {
        if (busy) return
        val ctx = this
        val installed = cheat.name in installedNames
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(ctx, 22), Ui.dp(ctx, 8), Ui.dp(ctx, 22), Ui.dp(ctx, 8))
        }
        content.addView(Ui.tv(ctx, if (installed) "Installed · Database cheat" else "Not installed · Database cheat", 14f), Ui.lp().apply {
            bottomMargin = Ui.dp(ctx, 16)
        })
        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(cheat.name)
            .setView(ScrollView(ctx).apply { addView(content) })
            .setNegativeButton("Close", null)
            .create()
        fun action(label: String, run: () -> Unit) {
            content.addView(Ui.button(ctx, label, filled = false) {
                dialog.dismiss()
                run()
            }, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = Ui.dp(ctx, 12)
            })
        }
        action(if (installed) "Reinstall cheat" else "Install cheat") {
            applyCheats(listOf(cheat), "Installing ${cheat.name}…")
        }
        if (installed) action("Remove from emulator") { confirmRemoval(listOf(cheat)) }
        val codeText = cheat.codeLines.joinToString("\n")
        if (codeText.isNotBlank()) {
            val code = Ui.tv(ctx, codeText, 12f).apply {
                setTextIsSelectable(true)
                setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 10), Ui.dp(ctx, 10), Ui.dp(ctx, 10))
                setBackgroundColor(0xFF182337.toInt())
                visibility = View.GONE
            }
            val copy = Ui.button(ctx, "Copy code", filled = false) {
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText(cheat.name, codeText))
                Toast.makeText(ctx, "Cheat code copied", Toast.LENGTH_SHORT).show()
            }.apply { visibility = View.GONE }
            val toggle = Ui.button(ctx, "View cheat code", filled = false) { }
            toggle.setOnClickListener {
                val expanded = code.visibility != View.VISIBLE
                code.visibility = if (expanded) View.VISIBLE else View.GONE
                copy.visibility = if (expanded) View.VISIBLE else View.GONE
                toggle.text = if (expanded) "Hide cheat code" else "View cheat code"
            }
            content.addView(toggle, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(ctx, 12) })
            content.addView(code, Ui.lp())
            content.addView(copy, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        dialog.show()
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
    private var installed: Set<String>,
    private val onDetails: (Cheat) -> Unit
) : RecyclerView.Adapter<DolphinCheatAdapter.VH>() {

    fun updateInstalled(names: Set<String>) {
        installed = names
    }

    class VH(
        val root: LinearLayout,
        val check: CheckBox,
        val name: TextView,
        val badge: TextView,
        val details: TextView
    ) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val root = Ui.vbox(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 8))
        }
        val line = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val check = CheckBox(ctx).apply { minWidth = Ui.dp(ctx, 44) }
        val name = Ui.tv(ctx, "", 14f).apply {
            setPadding(Ui.dp(ctx, 3), 0, Ui.dp(ctx, 5), 0)
        }
        val badge = Ui.tv(ctx, "", 11f, bold = true).apply {
            setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 5), Ui.dp(ctx, 6), Ui.dp(ctx, 5))
        }
        val details = Ui.tv(ctx, "›", 25f).apply {
            setPadding(Ui.dp(ctx, 10), 0, Ui.dp(ctx, 6), 0)
            contentDescription = "Open cheat details"
        }
        line.addView(check, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        line.addView(name, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        line.addView(badge, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        line.addView(details, Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(line, Ui.lp())
        root.addView(View(ctx).apply {
            setBackgroundColor(0xFF263345.toInt())
        }, Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 1)))
        return VH(root, check, name, badge, details)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val cheat = source()[position]
        val isInstalled = cheat.name in installed
        holder.check.setOnCheckedChangeListener(null)
        holder.check.isChecked = cheat.key in selected
        holder.check.setOnCheckedChangeListener { _, checked ->
            onSelected(cheat.key, checked)
        }
        holder.name.text = cheat.name
        holder.badge.text = if (isInstalled) "Installed" else "Not installed"
        holder.badge.setTextColor(if (isInstalled) 0xFF66DFA6.toInt() else 0xFFB3BED0.toInt())
        holder.details.setTextColor(0xFF94A3B8.toInt())
        holder.name.setOnClickListener { onDetails(cheat) }
        holder.badge.setOnClickListener { onDetails(cheat) }
        holder.details.setOnClickListener { onDetails(cheat) }
        holder.root.setOnClickListener { onDetails(cheat) }
    }

    override fun getItemCount(): Int = source().size
}
