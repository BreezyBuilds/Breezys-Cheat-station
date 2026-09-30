package com.breezybuilds.cheatstation.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startActivity(
            Intent(this, SystemHubActivity::class.java)
                .putExtra("system", 0)
        )

        finish()
    }
}
