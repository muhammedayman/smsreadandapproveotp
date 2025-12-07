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

    private lateinit var tvPermissionStatus: android.widget.TextView
    private lateinit var btnGrantPermission: Button
    private val SMS_PERMISSION_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_config)

            tvPermissionStatus = findViewById(R.id.tvPermissionStatus)
            btnGrantPermission = findViewById(R.id.btnGrantPermission)

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
            etPayload.setText(prefs.getString("payload_template", "{ \"code\": \"%code%\", \"phone\": \"%phone%\" }"))
            etHeaderKey1.setText(prefs.getString("header_key_1", ""))
            etHeaderVal1.setText(prefs.getString("header_val_1", ""))
            etHeaderKey2.setText(prefs.getString("header_key_2", ""))
            etHeaderVal2.setText(prefs.getString("header_val_2", ""))
            etKeyword.setText(prefs.getString("keyword", "DONIKKAH"))

            checkPermissions()

            btnGrantPermission.setOnClickListener {
                requestSmsPermissions()
            }

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

    private fun checkPermissions() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            tvPermissionStatus.text = "Granted"
            tvPermissionStatus.setTextColor(android.graphics.Color.GREEN)
            btnGrantPermission.isEnabled = false
        } else {
            tvPermissionStatus.text = "Missing Permissions"
            tvPermissionStatus.setTextColor(android.graphics.Color.RED)
            btnGrantPermission.isEnabled = true
        }
    }

    private fun requestSmsPermissions() {
        androidx.core.app.ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.RECEIVE_SMS, android.Manifest.permission.READ_SMS),
            SMS_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_CODE) {
            checkPermissions()
        }
    }
}
