package com.breezybuilds.cheatstation.ui

import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.breezybuilds.cheatstation.emulator.InstalledEmulators

/** Catalogue and per-console emulator preferences. No unverified installation claims. */
class EmulatorStationActivity : AppCompatActivity() {
    private data class Console(val id:String, val name:String, val group:String, val recommended:String, val alternatives:List<String> = emptyList(), val note:String = "")
    private val consoles = listOf(
        Console("gb","Game Boy","Nintendo","RetroArch · Gambatte",listOf("Pizza Boy")),
        Console("gbc","Game Boy Color","Nintendo","RetroArch · Gambatte",listOf("Pizza Boy")),
        Console("gba","Game Boy Advance","Nintendo","RetroArch · mGBA",listOf("Pizza Boy GBA")),
        Console("nds","Nintendo DS / DSi","Nintendo","melonDS",listOf("RetroArch · melonDS DS (DS mode)","DraStic"),note="DSi-mode features require separate emulator/core compatibility verification"),
        Console("3ds","Nintendo 3DS","Nintendo","Azahar",listOf("Citra (existing)","Lime3DS (existing)")),
        Console("vb","Virtual Boy","Nintendo","RetroArch · Beetle VB"),
        Console("nes","NES / Famicom","Nintendo","RetroArch · FCEUmm",listOf("RetroArch · Mesen")),
        Console("snes","SNES / Super Famicom","Nintendo","RetroArch · Snes9x",listOf("Snes9x EX+")),
        Console("n64","Nintendo 64","Nintendo","M64Plus FZ",listOf("RetroArch · Mupen64Plus-Next")),
        Console("gc","GameCube","Nintendo","Dolphin"),
        Console("wii","Wii","Nintendo","Dolphin"),
        Console("wiiu","Wii U","Nintendo","Cemu",note="Experimental Android compatibility: verify device and game support"),
        Console("switch","Nintendo Switch","Nintendo","Eden",note="Experimental: emulator compatibility varies; use legally obtained game files"),
        Console("ps1","PlayStation 1","PlayStation","DuckStation",listOf("RetroArch · SwanStation")),
        Console("ps2","PlayStation 2","PlayStation","NetherSX2",listOf("ARMSX2"),"Different variants require separate validation"),
        Console("psp","PlayStation Portable","PlayStation","PPSSPP",listOf("RetroArch · PPSSPP")),
        Console("vita","PlayStation Vita","PlayStation","Vita3K",note="Experimental: check game compatibility"),
        Console("sms","Master System","Sega","RetroArch · Genesis Plus GX"),
        Console("gg","Game Gear","Sega","RetroArch · Genesis Plus GX"),
        Console("md","Mega Drive / Genesis","Sega","RetroArch · Genesis Plus GX"),
        Console("segacd","Mega CD / Sega CD","Sega","RetroArch · Genesis Plus GX",note="Some games require user-supplied firmware"),
        Console("32x","Sega 32X","Sega","RetroArch · PicoDrive"),
        Console("saturn","Sega Saturn","Sega","Yaba Sanshiro 2",listOf("RetroArch · Beetle Saturn")),
        Console("dc","Dreamcast","Sega","Flycast"),
        Console("a2600","Atari 2600","Other","RetroArch · Stella"),
        Console("lynx","Atari Lynx","Other","RetroArch · Handy"),
        Console("pce","PC Engine / TurboGrafx-16","Other","RetroArch · Beetle PCE Fast"),
        Console("ngp","Neo Geo Pocket / Color","Other","RetroArch · Beetle NeoPop"),
        Console("neogeo","Neo Geo","Other","RetroArch · FBNeo"),
        Console("arcade","Arcade (MAME / FBNeo)","Other","RetroArch · FBNeo",listOf("RetroArch · MAME"))
    )
    private val preferences by lazy { getSharedPreferences("retrohub_emulator_choices_v1", MODE_PRIVATE) }
    private val themePrefs by lazy { getSharedPreferences("retrohub_v2_appearance", MODE_PRIVATE) }
    private var query = ""
    private var onlySelected = false
    private val collapsedGroups = mutableSetOf<String>()
    private var columns = 2
    private lateinit var content: LinearLayout
    private var bgColor = Color.BLACK
    private var surface = Color.rgb(16,16,16)
    private var inkColor = Color.WHITE
    private var muted = Color.LTGRAY
    private var outline = Color.DKGRAY
    private fun dp(n:Int) = (n*resources.displayMetrics.density+.5f).toInt()
    private fun colors() {
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val name = themePrefs.getString("theme","system") ?: "system"
        val palette = mapOf(
            "OLED Black" to listOf("#000000","#101010","#FFFFFF","#B8B8B8","#343434"),
            "Frost White" to listOf("#F4F7FB","#FFFFFF","#152033","#63738A","#D6DFEB"),
            "Navy Core" to listOf("#0E1A2B","#16243A","#FFFFFF","#B8C7E0","#354A66"),
            "Crimson Ember" to listOf("#1A0E10","#2A1518","#FFFFFF","#F1C0B8","#593036"),
            "Forest Grid" to listOf("#0E1A14","#16271E","#F5FFF8","#B6D7C2","#365242"),
            "Neon Orchid" to listOf("#170F1F","#241731","#FFFFFF","#D7B8E8","#52345F"),
            "Royal Violet" to listOf("#151228","#201B36","#FFFFFF","#C7BEEB","#453B65"),
            "Burnt Orange" to listOf("#1B130E","#2A1C14","#FFF8F2","#E9C8AC","#5B3B27"),
            "Signal Yellow" to listOf("#17160F","#242218","#FFFDF4","#E3D9A6","#58502C"),
            "Steel Grey" to listOf("#1A1D21","#252A30","#F5F7FA","#B6BEC8","#414A55")
        )[if(name=="system") if(dark) "OLED Black" else "Frost White" else name] ?: listOf("#000000","#101010","#FFFFFF","#B8B8B8","#343434")
        bgColor=Color.parseColor(palette[0]); surface=Color.parseColor(palette[1]); inkColor=Color.parseColor(palette[2]); muted=Color.parseColor(palette[3]); outline=Color.parseColor(palette[4])
    }
    private fun shape():GradientDrawable = GradientDrawable().apply { setColor(surface); cornerRadius=dp(4).toFloat(); setStroke(dp(1),outline) }
    private fun text(value:String,size:Float=14f,bold:Boolean=false):TextView = TextView(this).apply {
        text=value; textSize=size; setTextColor(if(bold)this@EmulatorStationActivity.inkColor else muted); if(bold)setTypeface(null,Typeface.BOLD)
    }
    private fun vertical():LinearLayout=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    private fun gap(parent:LinearLayout,size:Int) { parent.addView(View(this),LinearLayout.LayoutParams(1,dp(size))) }
    private fun button(label:String,action:()->Unit):TextView= text(label,14f,true).apply {
        gravity=Gravity.CENTER; background=shape(); setPadding(dp(12),dp(12),dp(12),dp(12)); setOnClickListener{action()}
    }
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); colors(); build() }
    override fun onResume() {
        super.onResume()
        // Recheck after installation/uninstallation or returning from Android settings.
        consoles.forEach { c ->
            val options = (listOf(c.recommended) + c.alternatives).distinct()
            val key = "emulator_${c.id}"
            val saved = preferences.getString(key, null)
            val choice = InstalledEmulators.automaticChoice(options, saved,
                preferences.getString("package_${c.id}", null)) { InstalledEmulators.installed(this, it) }
            if (choice != null) {
                preferences.edit().putString(key, choice.first).putString("package_${c.id}", choice.second).apply()
            }
        }
        render()
    }
    private fun state(c: Console) = InstalledEmulators.status(this, preferred(c), preferences.getString("package_${c.id}", null))
    private fun preferred(c:Console) = preferences.getString("emulator_${c.id}",c.recommended) ?: c.recommended
    private fun build() {
        colors()
        val root=vertical().apply { setPadding(dp(16),dp(14),dp(16),dp(24)) }
        val availableDp=resources.configuration.screenWidthDp
        columns=when { availableDp>=800 -> 4; availableDp>=540 -> 3; availableDp>=340 -> 2; else -> 1 }
        val bar=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        val titles=vertical().apply { addView(text("Emulator Station",24f,true));addView(text("30 systems · Emulator settings",13f)) }
        bar.addView(titles,LinearLayout.LayoutParams(0,-2,1f))
        bar.addView(button("← Settings"){finish()})
        root.addView(bar);gap(root,12)
        root.addView(text("Installed emulators are detected automatically. Tap a system to choose.",13f));gap(root,12)
        val search=EditText(this).apply {
            setSingleLine(true); hint="Search console or emulator"; setTextColor(inkColor);setHintTextColor(muted)
            background=shape();setPadding(dp(12),dp(8),dp(12),dp(8));setText(query)
            addTextChangedListener(object:TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){query=s?.toString().orEmpty();render()}
                override fun afterTextChanged(s:Editable?){}
            })
        }
        root.addView(search,LinearLayout.LayoutParams(-1,dp(44)));gap(root,10)
        val filters=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        filters.addView(button("All" ){onlySelected=false;render()},LinearLayout.LayoutParams(0,-2,1f))
        val selectedButton=button("Selected"){onlySelected=true;render()}
        filters.addView(selectedButton,LinearLayout.LayoutParams(0,-2,1f).apply {leftMargin=dp(10)})
        root.addView(filters);gap(root,12)
        content=vertical();root.addView(content)
        val scroll=ScrollView(this).apply { setBackgroundColor(bgColor);isFillViewport=true;addView(root) }
        setContentView(scroll);render()
    }
    private fun render(){
        if(!::content.isInitialized)return
        content.removeAllViews()
        var visible=0
        listOf("Nintendo","PlayStation","Sega","Other").forEach { group ->
            val entries=consoles.filter { c -> c.group==group && (!onlySelected || preferences.getBoolean("enabled_${c.id}",false)) && (query.isBlank() || (listOf(c.name,c.recommended,group)+c.alternatives).any { it.contains(query,true) }) }
            if(entries.isEmpty())return@forEach
            val isOpen=query.isNotBlank() || !collapsedGroups.contains(group)
            val groupHeader=button("${if(isOpen) "▾" else "▸"}  $group  ·  ${entries.size}") {
                if(collapsedGroups.contains(group)) collapsedGroups.remove(group) else collapsedGroups.add(group)
                render()
            }
            groupHeader.gravity=Gravity.CENTER_VERTICAL or Gravity.START
            content.addView(groupHeader,LinearLayout.LayoutParams(-1,dp(46)))
            gap(content,6)
            if(isOpen){
                entries.chunked(columns).forEach { batch ->
                    val gridRow=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.TOP }
                    batch.forEachIndexed { i,entry ->
                        val cell=consoleCard(entry)
                        gridRow.addView(cell,LinearLayout.LayoutParams(0,dp(116),1f).apply {
                            if(i>0)leftMargin=dp(8)
                        })
                    }
                    repeat(columns-batch.size){ gridRow.addView(View(this),LinearLayout.LayoutParams(0,1,1f).apply{leftMargin=dp(8)}) }
                    content.addView(gridRow);gap(content,7)
                }
                visible+=entries.size
            }
            gap(content,7)
        }
        if(visible==0 && (query.isNotBlank() || onlySelected))content.addView(text("No matching systems. Try a different search or select more consoles.",14f))
        content.addView(text("Choices are saved per system. Core, firmware and cheat storage setup remain separate.",12f))
    }
    private fun showEmulatorOptions(entry:Console) {
        val choices = (listOf(entry.recommended) + entry.alternatives).distinct().flatMap { option ->
            val packages = InstalledEmulators.packages(option)
            if (packages.isEmpty()) listOf(option to null) else packages.map { option to it }
        }
        val savedPackage = preferences.getString("package_${entry.id}", null)
        val checked = choices.indexOfFirst { it.first == preferred(entry) && (savedPackage == null || it.second == savedPackage) }
        val labels = choices.map { (option, pkg) ->
            "$option · ${InstalledEmulators.status(this, option, pkg)}" + (pkg?.let { "\n$it" } ?: "")
        }
        AlertDialog.Builder(this)
            .setTitle(entry.name)
            .setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, which ->
                val (option, pkg) = choices[which]
                preferences.edit().putString("emulator_${entry.id}", option)
                    .putString("package_${entry.id}", pkg).apply()
                dialog.dismiss()
                render()
            }
            .setNeutralButton("Setup help") { _, _ ->
                AlertDialog.Builder(this).setTitle("${entry.name} setup")
                    .setMessage(listOf(entry.note, "Install your chosen emulator, then open it to configure game folders and any required firmware. RetroArch cores must be installed inside RetroArch; their presence is not detected here. Package detection does not verify game or cheat compatibility.").filter { it.isNotBlank() }.joinToString("\n\n"))
                    .setPositiveButton("Close", null)
                    .setNeutralButton("Open emulator") { _, _ ->
                        val option = preferred(entry)
                        val pkg = preferences.getString("package_${entry.id}", null)
                            ?: InstalledEmulators.installed(this, option).firstOrNull()
                        val launch = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
                        if (launch == null || runCatching { startActivity(launch) }.isFailure)
                            Toast.makeText(this, "Selected emulator is not installed or cannot be opened.", Toast.LENGTH_LONG).show()
                    }.show()
            }
            .setNegativeButton("Close", null)
            .show()
    }
    private fun consoleCard(entry:Console):LinearLayout {
        val card=vertical().apply {
            background=shape()
            setPadding(dp(8),dp(7),dp(8),dp(7))
            gravity=Gravity.CENTER_VERTICAL
        }
        val line=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL }
        val check=CheckBox(this).apply {
            isChecked=preferences.getBoolean("enabled_${entry.id}",false)
            buttonTintList=android.content.res.ColorStateList.valueOf(inkColor)
            contentDescription="Select ${entry.name}"
            setOnCheckedChangeListener { _,checked ->
                preferences.edit().putBoolean("enabled_${entry.id}",checked).apply()
                if(onlySelected)render()
            }
        }
        line.addView(check,LinearLayout.LayoutParams(dp(38),dp(44)))
        val labels=vertical().apply {
            addView(text(entry.name,14f,true).apply { maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END })
            addView(text(preferred(entry),11f).apply { maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END })
            addView(text(state(entry),11f).apply { maxLines=2 })
        }
        line.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
        val arrow=text("›",25f,true).apply {
            gravity=Gravity.CENTER
            contentDescription="Emulator options for ${entry.name}"
        }
        line.addView(arrow,LinearLayout.LayoutParams(dp(24),dp(44)))
        card.addView(line)
        // The checkbox handles selection; the rest of the card opens emulator preferences.
        card.setOnClickListener { showEmulatorOptions(entry) }
        labels.setOnClickListener { showEmulatorOptions(entry) }
        arrow.setOnClickListener { showEmulatorOptions(entry) }
        return card
    }
}
