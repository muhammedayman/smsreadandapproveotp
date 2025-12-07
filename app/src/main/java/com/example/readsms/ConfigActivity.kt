package com.example.readsms

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

class ConfigActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_config)

            val etApiUrl = findViewById<EditText>(R.id.etApiUrl)
            val etPayload = findViewById<EditText>(R.id.etPayload)
            val etHeaderKey1 = findViewById<EditText>(R.id.etHeaderKey1)
            val etHeaderVal1 = findViewById<EditText>(R.id.etHeaderVal1)
            val etHeaderKey2 = findViewById<EditText>(R.id.etHeaderKey2)
            val etHeaderVal2 = findViewById<EditText>(R.id.etHeaderVal2)
            val etKeyword = findViewById<EditText>(R.id.etKeyword)
            val btnSave = findViewById<Button>(R.id.btnSave)

            // Load existing prefs
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            etApiUrl.setText(prefs.getString("api_url", ""))
            etPayload.setText(prefs.getString("payload_template", "{'code':'%code%', 'phone':'%phone%'}"))
            etHeaderKey1.setText(prefs.getString("header_key_1", ""))
            etHeaderVal1.setText(prefs.getString("header_val_1", ""))
            etHeaderKey2.setText(prefs.getString("header_key_2", ""))
            etHeaderVal2.setText(prefs.getString("header_val_2", ""))
            etKeyword.setText(prefs.getString("keyword", "DONIKKAH"))

            btnSave.setOnClickListener {
                try {
                    val url = etApiUrl.text.toString().trim()
                    val keyword = etKeyword.text.toString().trim()
                    
                    if (url.isEmpty()) {
                        etApiUrl.error = "URL required"
                        return@setOnClickListener
                    }
                    if (keyword.isEmpty()) {
                        etKeyword.error = "Keyword required"
                        return@setOnClickListener
                    }

                    prefs.edit()
                        .putString("api_url", url)
                        .putString("payload_template", etPayload.text.toString())
                        .putString("header_key_1", etHeaderKey1.text.toString())
                        .putString("header_val_1", etHeaderVal1.text.toString())
                        .putString("header_key_2", etHeaderKey2.text.toString())
                        .putString("header_val_2", etHeaderVal2.text.toString())
                        .putString("keyword", keyword)
                        .putBoolean("is_configured", true)
                        .apply() // Async save

                    Toast.makeText(this, "Configuration Saved!", Toast.LENGTH_SHORT).show()
                    
                    // Go to Main
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(this, "Save Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            // Fallback UI
            val tv = android.widget.TextView(this)
            tv.text = "CONFIG ERROR:\n${e.message}\n${android.util.Log.getStackTraceString(e)}"
            tv.textSize = 20f
            tv.setTextColor(android.graphics.Color.RED)
            setContentView(tv)
        }
    }
}
