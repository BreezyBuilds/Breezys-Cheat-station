package com.breezybuilds.cheatstation.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.ScrollView
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.breezybuilds.cheatstation.app
import com.breezybuilds.cheatstation.model.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Ps2Activity : AppCompatActivity(){
    private lateinit var setup:LinearLayout; private lateinit var setupText:TextView; private lateinit var banner:TextView; private lateinit var progress:LinearProgressIndicator; private lateinit var search:EditText; private lateinit var adapter:GameAdapter
    private var games:List<Game> = emptyList(); private var busy=false
    private val pickRoot=registerForActivityResult(ActivityResultContracts.OpenDocumentTree()){u->if(u!=null)onRoot(u)}
    private val pickGames=registerForActivityResult(ActivityResultContracts.OpenDocumentTree()){u->if(u!=null)onGames(u)}
    private val pickTransfer=registerForActivityResult(ActivityResultContracts.OpenDocumentTree()){u->if(u!=null)onTransfer(u)}
    private lateinit var detectedText: TextView
    override fun onCreate(b:Bundle?){super.onCreate(b); val ctx=this; val root=Ui.vbox(ctx);Ui.edgeToEdge(root)
        root.addView(MaterialToolbar(ctx).apply{title="Breezy's Cheat Station";subtitle="PlayStation 2 • Cheat Manager";menu.add(Menu.NONE,1,1,"Rescan games");menu.add(Menu.NONE,2,2,"Cheat source");menu.add(Menu.NONE,4,4,"Settings");setOnMenuItemClickListener{when(it.itemId){1->scan();2->sourceDialog();4->startActivity(Intent(ctx,SettingsActivity::class.java))};true}},Ui.lp())
        root.addView(SystemTabs.create(ctx, 1), Ui.lp())
        progress=LinearProgressIndicator(ctx).apply{isIndeterminate=true;visibility=View.GONE};root.addView(progress,Ui.lp());banner=Ui.banner(ctx);root.addView(banner,Ui.lp())
        setup=Ui.vbox(ctx,4);setupText=Ui.tv(ctx,"",11f);detectedText=Ui.tv(ctx,"",11f);setup.addView(setupText,Ui.lp());setup.addView(detectedText,Ui.lp());root.addView(setup,Ui.lp())
        search=EditText(ctx).apply{hint="🔎 Search games";setSingleLine();addTextChangedListener(object:android.text.TextWatcher{override fun afterTextChanged(s:android.text.Editable?){adapter.notifyDataSetChanged()};override fun beforeTextChanged(s:CharSequence?,a:Int,c:Int,d:Int){};override fun onTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){}})};root.addView(search,Ui.lp().apply{setMargins(Ui.dp(ctx,12),Ui.dp(ctx,6),Ui.dp(ctx,12),0)})
        adapter=GameAdapter({g->listOf(g.title,"${g.productCode ?: "Serial unknown"}  •  CRC ${g.version ?: "unknown"}","ID: ${g.titleId}","Tap for cheats")},{g->startActivity(Intent(ctx,CheatsActivity::class.java).putExtra("platform","ps2").putExtra("titleId",g.titleId).putExtra("title",g.title).putExtra("version",g.version).putExtra("region",g.region))},{})
        val list=RecyclerView(ctx).apply{layoutManager=LinearLayoutManager(ctx);adapter=this@Ps2Activity.adapter};root.addView(list,Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));setContentView(root);autoDetect();scanIfReady()
    }
    private fun filtered():List<Game>{val q=search.text?.toString()?.trim().orEmpty();return if(q.isEmpty())games else games.filter{it.title.contains(q,true)||it.titleId.contains(q,true)}}
    private fun updateAdapter(){adapter.submit(filtered())}
    private fun autoDetect(){
        val d=app.ps2Storage.detectEmulator()
        if(d==null){
            detectedText.text="No supported NetherSX2/AetherSX2 package was found. You can enter the path manually."
            updateSetup()
            return
        }
        detectedText.text="Detected ${d.name}\n${d.path}\n" + if(d.accessible) "✓ Direct filesystem access is available." else "⚠ Android is blocking direct access to Android/data on this device."
        if(d.accessible){ updateSetup(); scan() } else { updateSetup() }
    }
    private fun manualPathDialog(initial:String?=app.ps2Storage.manualPath){
        val input=EditText(this).apply{setSingleLine();hint="/storage/emulated/0/Android/data/.../files";setText(initial);setSelection(text.length)}
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(Ui.dp(this@Ps2Activity,24),0,Ui.dp(this@Ps2Activity,24),0);addView(input,Ui.lp())}
        MaterialAlertDialogBuilder(this).setTitle("NetherSX2 data path").setMessage("Enter the full path to the emulator's data folder. This only works if Android actually permits Breezy's process to read/write that path.").setView(box).setPositiveButton("Test & save"){_,_->
            val err=app.ps2Storage.saveManualPath(input.text.toString())
            if(err!=null) Ui.show(banner,Ui.Kind.ERROR,err) else { val store=app.ps2Storage.directCheatsStore(); if(store!=null){Ui.show(banner,Ui.Kind.OK,"Direct access works. NetherSX2 cheats can be installed automatically.");updateSetup();scan()} else Ui.show(banner,Ui.Kind.WARN,"Path saved, but Android is still denying direct access. Use a shared transfer folder to export PNACH files."); updateSetup()}
        }.setNegativeButton("Cancel",null).show()
    }

    private fun updateSetup(){
        val ok=app.ps2Storage.rootDoc()!=null || app.ps2Storage.directCheatsStore()!=null
        val transfer=app.ps2Storage.transferDoc()!=null
        setup.visibility=View.VISIBLE
        setupText.text=(if(ok)"✓ NetherSX2 data configured" else if(transfer)"✓ Shared transfer folder configured" else "PS2 storage not configured — open Settings to configure it.") + "  " + (if(app.ps2Storage.gamesDoc()!=null)"✓ Games folder configured" else "Games folder not configured")
    }
    private fun onRoot(u:Uri){val e=app.ps2Storage.acceptRoot(u);if(e==null){updateSetup();scan()}else MaterialAlertDialogBuilder(this).setTitle("NetherSX2 folder cannot be used").setMessage(e+"\n\nAndroid 11 and newer prevent normal apps from taking persistent access to another app's Android/data folder. Use the shared transfer folder option instead, then import the generated cheats from NetherSX2's Transfer Data menu.").setPositiveButton("Choose again"){_,_->pickRoot.launch(null)}.setNeutralButton("Use transfer folder"){_,_->pickTransfer.launch(null)}.setNegativeButton("Cancel",null).show()}
    private fun onGames(u:Uri){val e=app.ps2Storage.acceptGames(u);if(e==null){updateSetup();scan()}else MaterialAlertDialogBuilder(this).setTitle("Games folder problem").setMessage(e).setPositiveButton("Choose again"){_,_->pickGames.launch(null)}.show()}
    private fun onTransfer(u:Uri){val e=app.ps2Storage.acceptTransfer(u);if(e==null){updateSetup();Ui.show(banner,Ui.Kind.OK,"Shared transfer folder ready. PS2 cheats can now be exported there for NetherSX2 import.")}else MaterialAlertDialogBuilder(this).setTitle("Transfer folder problem").setMessage(e).setPositiveButton("Choose again"){_,_->pickTransfer.launch(null)}.show()}
    private fun scanIfReady(){if(app.ps2Storage.gamesDoc()!=null)scan()}
    private fun scan(){if(busy||app.ps2Storage.gamesDoc()==null){updateSetup();return};busy=true;progress.visibility=View.VISIBLE;Ui.show(banner,Ui.Kind.INFO,"Scanning PS2 games…");lifecycleScope.launch{val r=withContext(Dispatchers.IO){app.ps2Scanner.scan{m->runOnUiThread{Ui.show(banner,Ui.Kind.INFO,m)}}};busy=false;progress.visibility=View.GONE;games=r.games;updateAdapter();updateSetup();Ui.show(banner,if(r.warnings.isEmpty())Ui.Kind.OK else Ui.Kind.WARN,"Found ${games.size} PS2 game(s)."+(if(r.warnings.isNotEmpty())" ${r.warnings.first()}" else ""))}}
    private fun sourceDialog(){val a=com.breezybuilds.cheatstation.provider.Ps2CheatSources.all;var sel=a.indexOfFirst{it.id==app.ps2Settings.source().id}.coerceAtLeast(0);val labels=a.map{it.name+"\n"+it.description}.toTypedArray();MaterialAlertDialogBuilder(this).setTitle("PS2 cheat sources").setSingleChoiceItems(labels,sel){_,w->sel=w}.setPositiveButton("Use selected"){_,_->app.ps2Settings.saveSource(a[sel]);Ui.show(banner,Ui.Kind.OK,"Using ${a[sel].name}.")}.setNegativeButton("Cancel",null).show()}
}
