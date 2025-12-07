package com.example.readsms

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.readsms.db.DatabaseHelper
import com.example.readsms.db.SmsRecord
import com.example.readsms.ui.SmsAdapter

class MainActivity : Activity() {

    private val SMS_PERMISSION_CODE = 100
    private lateinit var statusText: TextView
    private lateinit var permissionButton: Button
    private lateinit var listView: ListView
    private lateinit var adapter: SmsAdapter
    private lateinit var dbHelper: DatabaseHelper
    private val smsReceiver = SmsReceiver()
    
    // Tab State
    private var isVerifiedTab = false // False = Pending, True = Verified

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadRecords()
        }
    }

    private val apiDebugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // DEBUG TOAST
            android.widget.Toast.makeText(context, "Debug Broadcast Received!", android.widget.Toast.LENGTH_SHORT).show()
            
            if (intent?.action == "com.example.readsms.API_DEBUG") {
                val payload = intent.getStringExtra("payload")
                val code = intent.getIntExtra("response_code", 0)
                val body = intent.getStringExtra("response_body")
                
                if (code == 100) {
                     android.widget.Toast.makeText(context, "Worker Started...", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.app.AlertDialog.Builder(this@MainActivity)
                        .setTitle("API Debug ($code)")
                        .setMessage("Payload:\n$payload\n\nResponse:\n$body")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            // 1. Check Config Safety Check
            try {
                val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                if (!prefs.getBoolean("is_configured", false)) {
                    val intent = Intent(this, ConfigActivity::class.java)
                    startActivity(intent)
                    finish()
                    return
                }
            } catch (e: Exception) {
                 android.util.Log.e("CRASH_REPORT", "Config Check Failed", e)
            }

            setContentView(R.layout.activity_main)

            // Start the Background Monitor Service
            try {
                val intent = Intent(this, SmsMonitorService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("CRASH_REPORT", "Service Start Failed", e)
            }

            statusText = findViewById(R.id.statusText)
            permissionButton = findViewById(R.id.permissionButton)
            listView = findViewById(R.id.recyclerView)
            
            try {
                dbHelper = DatabaseHelper(this)
                // One-time deduplication on startup
                dbHelper.deduplicate()
                statusText.text = "DB Cleaned"
            } catch (e: Exception) {
                statusText.text = "DB Error: ${e.message}"
                android.util.Log.e("CRASH_REPORT", "DB Error", e)
                return 
            }
            
            // TABS
            val btnTabPending = findViewById<Button>(R.id.btnTabPending)
            val btnTabVerified = findViewById<Button>(R.id.btnTabVerified)
            
            btnTabPending.setOnClickListener {
                isVerifiedTab = false
                btnTabPending.alpha = 1.0f
                btnTabVerified.alpha = 0.5f
                loadRecords()
            }
            
            btnTabVerified.setOnClickListener {
                isVerifiedTab = true
                btnTabVerified.alpha = 1.0f
                btnTabPending.alpha = 0.5f
                loadRecords()
            }
            // Init visual
            btnTabVerified.alpha = 0.5f

            try {
                adapter = SmsAdapter(emptyList()) { record ->
                    resendApi(record)
                }
                listView.adapter = adapter
            } catch (e: Exception) {
                statusText.text = "Adapter Error: ${e.message}"
                return
            }

            val scanButton = findViewById<Button>(R.id.scanButton)
            scanButton.setOnClickListener {
                 scanInbox()
            }
            scanButton.setOnLongClickListener {
                val debugIntent = Intent("com.example.readsms.API_DEBUG")
                debugIntent.setPackage(applicationContext.packageName)
                debugIntent.putExtra("payload", "TEST PAYLOAD: Manual Trigger")
                debugIntent.putExtra("response_code", 200)
                debugIntent.putExtra("response_body", "{ 'status': 'success', 'message': 'Test Response' }")
                sendBroadcast(debugIntent)
                DebugRepository.log("TEST PAYLOAD: Manual Trigger", 200, "{ 'status': 'success', 'message': 'Test Response (Static)' }")
                android.widget.Toast.makeText(this, "Sending Test...", android.widget.Toast.LENGTH_SHORT).show()
                true
            }
            
            try {
                findViewById<Button>(R.id.btnSettings).setOnClickListener {
                    startActivity(Intent(this, ConfigActivity::class.java))
                }
                
                findViewById<Button>(R.id.btnAbout)?.setOnClickListener {
                    try {
                         // startActivity(Intent(this, AboutActivity::class.java)) // AboutActivity not created yet?
                         android.widget.Toast.makeText(this, "About Page", android.widget.Toast.LENGTH_SHORT).show()
                    } catch(e: Exception) {}
                }
            } catch (e: Exception) {
                statusText.text = "Button Error: ${e.message}"
            }
            
            try {
                checkPermission()
                loadRecords()
                val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                val keyword = prefs.getString("keyword", "DONIKKAH") ?: "DONIKKAH"
                statusText.text = if (isVerifiedTab) "Verified Users" else "Ready. Scanning for '$keyword'..."
            } catch (e: Exception) {
                statusText.text = "Load Error: ${e.message}"
                android.util.Log.e("CRASH_REPORT", "Load Error", e)
            }

            permissionButton.setOnClickListener {
                requestSmsPermission()
            }
            
            try {
                registerReceiver(refreshReceiver, IntentFilter("com.example.readsms.UPDATE_LIST"))
                ContextCompat.registerReceiver(this, apiDebugReceiver, IntentFilter("com.example.readsms.API_DEBUG"), ContextCompat.RECEIVER_NOT_EXPORTED)
            } catch (e: Exception) {
                 android.util.Log.e("CRASH_REPORT", "Receiver Error", e)
            }
        } catch (e: Exception) {
            android.util.Log.e("CRASH_REPORT", "Error in MainActivity: " + e.message, e)
        }
    }
    
    override fun onResume() {
        super.onResume()
        try {
            val filter = IntentFilter("android.provider.Telephony.SMS_RECEIVED")
            filter.priority = 2147483647
            registerReceiver(smsReceiver, filter)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        DebugRepository.setListener(object : DebugRepository.DebugListener {
            override fun onLog(payload: String, responseCode: Int, responseBody: String) {
                runOnUiThread {
                    if (responseCode == 100) {
                         android.widget.Toast.makeText(this@MainActivity, "Worker Started...", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.app.AlertDialog.Builder(this@MainActivity)
                            .setTitle("API Debug ($responseCode)")
                            .setMessage("Payload:\n$payload\n\nResponse:\n$responseBody")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        })
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(smsReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        DebugRepository.setListener(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(refreshReceiver)
            unregisterReceiver(apiDebugReceiver)
        } catch (e: Exception) {
        }
        DebugRepository.setListener(null)
    }

    private fun loadRecords() {
        try {
             var records = dbHelper.getAllRecords()
            // FILTER BY TAB
            records = if (isVerifiedTab) {
                records.filter { it.status == DatabaseHelper.STATUS_SUCCESS }
            } else {
                records.filter { it.status != DatabaseHelper.STATUS_SUCCESS }
            }
            
            records = records.sortedByDescending { it.timestamp }
            
            runOnUiThread {
                adapter.updateData(records)
                statusText.text = if (isVerifiedTab) "Verified Users (${records.size})" else "Pending/Failed (${records.size})"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resendApi(record: SmsRecord) {
        val inputData = Data.Builder()
            .putString("code", record.code)
            .putString("phone", record.phone)
            .putLong("record_id", record.id)
            .build()
        
        val workRequest = OneTimeWorkRequestBuilder<VerifyUserWorker>()
            .setInputData(inputData)
            .build()
        
        WorkManager.getInstance(this).enqueue(workRequest)
        dbHelper.updateStatus(record.id, DatabaseHelper.STATUS_PENDING)
        loadRecords()
    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            statusText.text = "Listening & Ready..."
            permissionButton.isEnabled = false
        } else {
            statusText.text = "Permissions needed"
            permissionButton.isEnabled = true
        }
    }

    private fun requestSmsPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS),
            SMS_PERMISSION_CODE
        )
    }

    private fun scanInbox() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            statusText.text = "Grant Permissions First!"
            return
        }

        var newSmsCount = 0
        var sb = StringBuilder()
        val processedNumbers = HashSet<String>()

        try {
            val uriSms = android.net.Uri.parse("content://sms/")
            val cursorSms = contentResolver.query(uriSms, null, null, null, "date DESC")
            
            if (cursorSms != null && cursorSms.moveToFirst()) {
                var loopCount = 0
                do {
                    val body = cursorSms.getString(cursorSms.getColumnIndexOrThrow("body"))
                    val address = cursorSms.getString(cursorSms.getColumnIndexOrThrow("address")) ?: "Unknown"
                    
                    if (processedNumbers.contains(address)) {
                        loopCount++
                        continue
                    }
                    
                    // MARK AS PROCESSED: We only look at the LATEST message per number.
                    processedNumbers.add(address)
                    
                    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                    val keyword = prefs.getString("keyword", "DONIKKAH") ?: "DONIKKAH"
                    
                     if (body.contains(keyword, ignoreCase = true)) {
                          val pattern = java.util.regex.Pattern.compile("(?i)$keyword\\s*([a-zA-Z0-9]+)")
                          val matcher = pattern.matcher(body)
                          val code = if (matcher.find()) matcher.group(1) else body
                         
                         if (saveRecordIfNew(code, address)) {
                             newSmsCount++
                         }
                    }
                    loopCount++
                } while (cursorSms.moveToNext() && loopCount < 300)
                cursorSms.close()
            }
            // Skipping MMS for brevity as syntax was broken before, focusing on restoring stability first
             statusText.text = "Scan Complete. Found: $newSmsCount new."
            
        } catch (e: Exception) {
            statusText.text = "Scan Error: ${e.message}"
        }
    }

    private fun saveRecordIfNew(code: String, phone: String): Boolean {
        return try {
            // IGNORE IF ALREADY VERIFIED
            if (dbHelper.isNumberVerified(phone)) {
                return false
            }

            val id = dbHelper.upsertSms(code, phone)
            if (id != -1L) {
                 val record = SmsRecord(id, code, phone, DatabaseHelper.STATUS_PENDING, System.currentTimeMillis())
                 resendApi(record)
                 return true
            }
            false
        } catch (e: Exception) {
            false
        }
    }
    
    // ... MMS helpers if needed later ...
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkPermission()
            } else {
                statusText.text = "Permission Denied"
            }
        }
    }
}
