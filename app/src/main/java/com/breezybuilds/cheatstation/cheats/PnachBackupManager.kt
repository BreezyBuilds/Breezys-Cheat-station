package com.breezybuilds.cheatstation.cheats

import com.breezybuilds.cheatstation.storage.FileStore
import com.breezybuilds.cheatstation.storage.StoreException
import com.breezybuilds.cheatstation.util.AppLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PnachBackupManager(private val store: FileStore, private val now:()->Date={Date()}, private val keepPerTitle:Int=25){
    companion object { const val DIR="backups"; private val NAME=Regex("^(.+)_pnach_backup_(\\d{8}_\\d{6})(_\\d+)?\\.pnach$") }
    fun create(key:String,content:String):String{val stamp=SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(now());var n="${key}_pnach_backup_${stamp}.pnach";var i=2;while(store.exists("$DIR/$n"))n="${key}_pnach_backup_${stamp}_${i++}.pnach";store.writeText("$DIR/$n",content);if(store.readText("$DIR/$n")!=content){store.delete("$DIR/$n");throw StoreException("Backup could not be verified")};prune(key);AppLog.i("PS2Backup","Created $n");return n}
    fun list(key:String?=null)=store.list(DIR).filter{NAME.matches(it)&& (key==null||it.startsWith("${key}_"))}.sortedDescending()
    private fun prune(key:String){val a=list(key);if(a.size>keepPerTitle)a.drop(keepPerTitle).forEach{store.delete("$DIR/$it")}}
}
