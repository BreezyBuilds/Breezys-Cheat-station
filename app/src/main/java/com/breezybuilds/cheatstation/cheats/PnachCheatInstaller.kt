package com.breezybuilds.cheatstation.cheats

import com.breezybuilds.cheatstation.model.Cheat
import com.breezybuilds.cheatstation.model.CheatFile
import com.breezybuilds.cheatstation.storage.FileStore
import com.breezybuilds.cheatstation.storage.StoreException
import com.breezybuilds.cheatstation.util.AppLog

class PnachCheatInstaller(private val store: FileStore, private val backups: PnachBackupManager) {
    fun path(key: String) = "${key}.pnach"
    fun readExisting(key: String): CheatFile = PnachParser.parse(store.readText(path(key)) ?: "", key, true)
    fun preview(key: String, incoming: List<Cheat>): MergeResult = CheatMerger.merge(readExisting(key), incoming)
    fun install(key: String, incoming: List<Cheat>, replaceKeys: Set<String> = emptySet()): InstallResult {
        if(incoming.isEmpty()) return InstallResult.Failure("No cheats selected.")
        val existing=try{readExisting(key)}catch(e:Exception){return InstallResult.Failure("Could not read the current PNACH file: ${e.message}")}
        val merged=CheatMerger.merge(existing,incoming,replaceKeys); val summary="${merged.added.size} added" + if(merged.replaced.isNotEmpty()) ", ${merged.replaced.size} replaced" else ""
        return commit(key,PnachParser.serialize(merged.file),summary,merged.notes)
    }
    fun remove(key:String,names:Set<String>):InstallResult{ val e=readExisting(key); val u=CheatMerger.remove(e,names); val n=e.cheats.size-u.cheats.size; return if(n==0) InstallResult.Success("Nothing to remove.",null) else commit(key,PnachParser.serialize(u),"$n removed") }
    fun setEnabled(key:String,name:String,enabled:Boolean):InstallResult { val e=readExisting(key); return commit(key,PnachParser.serialize(CheatMerger.setEnabled(e,name,enabled)),if(enabled)"Enabled" else "Disabled") }
    private fun commit(key: String, text: String, summary: String, notes: List<String> = emptyList()): InstallResult {
        val p=path(key); val old=try{store.readText(p)}catch(e:Exception){return InstallResult.Failure("Could not read the current PNACH file: ${e.message}")}
        if(old!=null && old.replace("\r\n","\n").trimEnd()==text.replace("\r\n","\n").trimEnd()) return InstallResult.Success("No changes needed. $summary",null,notes)
        var backup:String?=null
        if(!old.isNullOrBlank()) backup=try{backups.create(key,old)}catch(e:Exception){return InstallResult.Failure("Could not create a backup, so nothing was changed. (${e.message})")}
        return try{store.writeText(p,text); if(store.readText(p)?.replace("\r\n","\n")?.trimEnd()!=text.replace("\r\n","\n").trimEnd()) throw StoreException("Verification failed"); AppLog.i("PS2Installer","Wrote $p: $summary"); InstallResult.Success(summary,backup,notes)}catch(e:Exception){val restored=try{if(old!=null)store.writeText(p,old)else store.delete(p);true}catch(_:Exception){false};InstallResult.Failure("Could not update the PNACH file (${e.message}). "+if(restored)"Your previous cheats were restored." else "Restoring failed; check your backup folder.",restored)}
    }
}
