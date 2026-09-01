package com.example.autologin

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val textView = TextView(this).apply {
            text = "GCE Wi-Fi AutoLogin\n\nAdd the Quick Settings tile from your notification panel to trigger login."
            textSize = 18f
            setPadding(50, 100, 50, 50)
        }
        
        setContentView(textView)
    }
}
