package com.breezybuilds.cheatstation.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.breezybuilds.cheatstation.R

/** Applies the saved Material colour scheme before view inflation or dialog creation. */
abstract class RetroThemedActivity : AppCompatActivity() {
    private var appliedTheme = "navy"
    private var appliedPack = "duotone"
    private var appliedSystemMode = "matched"

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedTheme = Ui.Appearance.theme(this)
        appliedPack = Ui.Appearance.iconPack(this)
        appliedSystemMode = Ui.Appearance.systemMode(this)
        setTheme(when (appliedTheme) {
            "oled" -> R.style.Theme_BreezyCheatStation_Oled
            "light" -> R.style.Theme_BreezyCheatStation_Light
            else -> R.style.Theme_BreezyCheatStation_Navy
        })
        super.onCreate(savedInstanceState)
        val light = appliedTheme == "light"
        @Suppress("DEPRECATION")
        window.statusBarColor = Ui.Appearance.background(this)
        @Suppress("DEPRECATION")
        window.navigationBarColor = Ui.Appearance.background(this)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    override fun onResume() {
        super.onResume()
        // Handles returning from Appearance without retaining stale colours in earlier screens.
        if (appliedTheme != Ui.Appearance.theme(this) ||
            appliedPack != Ui.Appearance.iconPack(this) ||
            appliedSystemMode != Ui.Appearance.systemMode(this)) recreate()
    }
}
