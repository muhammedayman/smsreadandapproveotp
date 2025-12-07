package com.mamstricks.readsms

import android.app.Activity
import android.os.Bundle

class AboutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_about)
        } catch (e: Exception) {
             val tv = android.widget.TextView(this)
             tv.text = "Error: " + e.message
             setContentView(tv)
        }
    }
}
