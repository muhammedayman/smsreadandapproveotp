package com.example.readsms

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class DiagnosisActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val textView = TextView(this)
        textView.text = "Diagnostic Mode: App Launched Successfully!"
        textView.textSize = 24f
        setContentView(textView)
    }
}
