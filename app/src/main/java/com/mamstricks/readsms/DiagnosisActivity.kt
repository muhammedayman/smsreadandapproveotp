package com.mamstricks.readsms

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView

class DiagnosisActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = ScrollView(this)
        val layout = android.widget.LinearLayout(this)
        layout.orientation = android.widget.LinearLayout.VERTICAL
        layout.setPadding(32, 32, 32, 32)
        scrollView.addView(layout)

        val title = TextView(this)
        title.text = "APP CRASHED"
        title.textSize = 24f
        title.setTextColor(android.graphics.Color.RED)
        title.setTypeface(null, android.graphics.Typeface.BOLD)
        layout.addView(title)

        val subtitle = TextView(this)
        subtitle.text = "Please take a screenshot of this error and send it to the developer."
        subtitle.setPadding(0, 16, 0, 16)
        layout.addView(subtitle)

        val errorText = TextView(this)
        errorText.text = intent.getStringExtra("error_trace") ?: "No trace available."
        errorText.textSize = 12f
        errorText.setTextColor(android.graphics.Color.parseColor("#333333"))
        errorText.setTextIsSelectable(true)
        layout.addView(errorText)

        val btnRestart = Button(this)
        btnRestart.text = "Restart App"
        btnRestart.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            finish()
        }
        layout.addView(btnRestart)

        setContentView(scrollView)
    }
}
