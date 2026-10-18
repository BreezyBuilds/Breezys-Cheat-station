package com.breezybuilds.cheatstation.ui

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** V2 foundation. Intentionally independent of legacy settings and emulator adapters. */
class RetroHubV2Activity : AppCompatActivity() {
    private data class Palette(val name: String, val bg: Int, val surface: Int, val heading: Int,
                               val secondary: Int, val primary: Int, val primaryText: Int, val border: Int)
    private fun c(hex: String) = Color.parseColor(hex)
    private val themes by lazy { listOf(
        Palette("OLED Black",c("#000000"),c("#101010"),Color.WHITE,c("#B8B8B8"),Color.WHITE,Color.BLACK,c("#343434")),
        Palette("Frost White",c("#F4F7FB"),Color.WHITE,c("#152033"),c("#63738A"),c("#1E4FFF"),Color.WHITE,c("#D6DFEB")),
        Palette("Navy Core",c("#0E1A2B"),c("#16243A"),Color.WHITE,c("#B8C7E0"),Color.WHITE,c("#0E1A2B"),c("#354A66")),
        Palette("Crimson Ember",c("#1A0E10"),c("#2A1518"),Color.WHITE,c("#F1C0B8"),c("#FF6A3D"),Color.WHITE,c("#593036")),
        Palette("Forest Grid",c("#0E1A14"),c("#16271E"),c("#F5FFF8"),c("#B6D7C2"),c("#7BE495"),c("#0E1A14"),c("#365242")),
        Palette("Neon Orchid",c("#170F1F"),c("#241731"),Color.WHITE,c("#D7B8E8"),c("#F66DDA"),c("#1B1020"),c("#52345F")),
        Palette("Royal Violet",c("#151228"),c("#201B36"),Color.WHITE,c("#C7BEEB"),c("#8B7BFF"),c("#111020"),c("#453B65")),
        Palette("Burnt Orange",c("#1B130E"),c("#2A1C14"),c("#FFF8F2"),c("#E9C8AC"),c("#FF9F43"),c("#24150D"),c("#5B3B27")),
        Palette("Signal Yellow",c("#17160F"),c("#242218"),c("#FFFDF4"),c("#E3D9A6"),c("#FFD84D"),c("#1D1A0A"),c("#58502C")),
        Palette("Steel Grey",c("#1A1D21"),c("#252A30"),c("#F5F7FA"),c("#B6BEC8"),c("#9FB1C7"),c("#18202A"),c("#414A55"))
    ) }
    private val prefs by lazy { getSharedPreferences("retrohub_v2_appearance", MODE_PRIVATE) }
    private fun current(): Palette {
        val key = prefs.getString("theme", "system") ?: "system"
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return themes.firstOrNull { it.name == key } ?: themes[if (dark) 0 else 1]
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun bg(color: Int, border: Int, radius: Int = 4) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); setStroke(dp(1), border)
    }
    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text; setTextColor(color); setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun addGap(parent: LinearLayout, size: Int) { parent.addView(View(this), LinearLayout.LayoutParams(1, dp(size))) }
    private fun page(title: String, subtitle: String, p: Palette): LinearLayout {
        val root = column().apply { setPadding(dp(20),dp(18),dp(20),dp(24)) }
        root.addView(label(title, 26f, p.heading, true)); addGap(root,4)
        root.addView(label(subtitle,14f,p.secondary)); addGap(root,18)
        return root
    }
    private fun show(root: LinearLayout, p: Palette) {
        val scroll = ScrollView(this).apply { setBackgroundColor(p.bg); isFillViewport = true }
        scroll.addView(root); setContentView(scroll)
        @Suppress("DEPRECATION") window.statusBarColor = p.bg
        @Suppress("DEPRECATION") window.navigationBarColor = p.bg
        val light = p.name == "Frost White"
        androidx.core.view.WindowInsetsControllerCompat(window,window.decorView).apply {
            isAppearanceLightStatusBars=light; isAppearanceLightNavigationBars=light
        }
    }
    private fun action(text: String, p: Palette, primary: Boolean, click: () -> Unit): Button = Button(this).apply {
        this.text=text; isAllCaps=false; textSize=13f
        setTextColor(if(primary)p.primaryText else p.heading)
        background=bg(if(primary)p.primary else p.surface,p.border)
        minHeight=dp(48); setPadding(dp(12),dp(8),dp(12),dp(8)); setOnClickListener { click() }
    }
    private fun station(title:String, desc:String, icon:String, p:Palette, click:()->Unit):LinearLayout = column().apply {
        background=bg(p.surface,p.border)
        setPadding(dp(18),dp(18),dp(18),dp(18))
        minimumHeight=dp(220)
        addView(label(icon,25f,p.heading,true));addGap(this,14)
        addView(label(title,20f,p.heading,true));addGap(this,6)
        addView(label(desc,13f,p.secondary))
        val spacer=View(this@RetroHubV2Activity)
        addView(spacer,LinearLayout.LayoutParams(1,0,1f))
        addGap(this,16)
        addView(action("Open station  →",p,true,click),LinearLayout.LayoutParams(-1,dp(48)))
    }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); home() }
    private fun home() {
        val p=current()
        val root=column().apply { setPadding(dp(20),dp(18),dp(20),dp(24)) }
        val header=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        val heading=column().apply {
            addView(label("Breezy's Retro Hub",26f,p.heading,true))
            addGap(this,4)
            addView(label("One hub. Every system.",14f,p.secondary))
        }
        header.addView(heading,LinearLayout.LayoutParams(0,-2,1f))
        val settings=action("⚙",p,false){ appearance() }.apply {
            contentDescription="Appearance and themes"
            textSize=23f
            minWidth=dp(48)
        }
        header.addView(settings,LinearLayout.LayoutParams(dp(52),dp(48)))
        root.addView(header)
        addGap(root,dpGap(28))
        val entries=listOf(
            Triple("Emulators","Install and configure your systems","▣"),
            Triple("Cheats","Find and manage compatible cheats","✦"),
            Triple("Tuning","Find recommended settings for your device","⚙")
        )
        val wide=resources.configuration.screenWidthDp>=600
        if(wide){
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            entries.forEachIndexed { i,e ->
                val card=station(e.first,e.second,e.third,p){stationPage(i)}
                val lp=LinearLayout.LayoutParams(0,dp(240),1f).apply { if(i>0) leftMargin=dp(12) }
                row.addView(card,lp)
            }
            root.addView(row)
        } else {
            entries.forEachIndexed { i,e ->
                root.addView(station(e.first,e.second,e.third,p){stationPage(i)},LinearLayout.LayoutParams(-1,dp(220)))
                addGap(root,12)
            }
        }
        addGap(root,20)
        root.addView(label("V2 DEV BUILD  •  FOUNDATION",11f,p.secondary))
        show(root,p)
    }
    private fun dpGap(value:Int)=value
    private fun stationPage(index:Int){
        val p=current();val names=listOf("Emulators","Cheats","Tuning")
        val root=page(names[index],"Breezy's Retro Hub V2",p)
        root.addView(action("← Main hub",p,false){home()});addGap(root,20)
        if(index==0){
            startActivity(Intent(this, EmulatorStationActivity::class.java))
            return
        }
        if(index==1){
            root.addView(label("Existing cheat integrations",18f,p.heading,true));addGap(root,10)
            root.addView(label("This opens the legacy 3DS / PS2 / Wii / GameCube interface. Its capabilities are not yet migrated or reverified for V2.",14f,p.secondary));addGap(root,14)
            root.addView(action("Open legacy cheat station",p,true){
                startActivity(Intent(this,WelcomeActivity::class.java).putExtra("skip_retro_setup",true))
            })
        }else{
            root.addView(label("Not implemented yet",18f,p.heading,true));addGap(root,8)
            root.addView(label(if(index==0)"Emulator discovery, official setup and core management will be added after the foundation is tested." else "Hardware detection and game-specific settings guidance will be added after the foundation is tested.",14f,p.secondary))
        }
        show(root,p)
    }
    private fun appearance(){
        val p=current();val root=page("Appearance","Follow your device by default, or choose a colour theme.",p)
        root.addView(action("← Main hub",p,false){home()});addGap(root,16)
        val selected=prefs.getString("theme","system")
        val items=listOf("Follow device" to "system")+themes.map { it.name to it.name }
        items.forEach { (title,key)->
            val mark=if(selected==key) "✓  " else "    "
            root.addView(action(mark+title,p,false){prefs.edit().putString("theme",key).apply();appearance()},LinearLayout.LayoutParams(-1,dp(51)))
            addGap(root,7)
        }
        show(root,p)
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { home() }
}
