package com.example.readsms

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
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
    
    // Receiver to refresh list when SMS received or Worker finishes
    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadRecords()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Check Config
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this)
        if (!prefs.getBoolean("is_configured", false)) {
            startActivity(Intent(this, ConfigActivity::class.java))
            finish()
            return
        }

        try {
            setContentView(R.layout.activity_main)
            
            // Start the Background Monitor Service
            val intent = Intent(this, SmsMonitorService::class.java)
            startService(intent)

            statusText = findViewById(R.id.statusText)
            permissionButton = findViewById(R.id.permissionButton)
            listView = findViewById(R.id.recyclerView)
            
            try {
                dbHelper = DatabaseHelper(this)
                statusText.text = "DB Init Success"
            } catch (e: Exception) {
                statusText.text = "DB Error: ${e.message}"
                android.util.Log.e("CRASH_REPORT", "DB Error", e)
                return 
            }
            
            try {
                adapter = SmsAdapter(emptyList()) { record ->
                    resendApi(record)
                }
                listView.adapter = adapter
                statusText.text = "Adapter Init Success"
            } catch (e: Exception) {
                statusText.text = "Adapter Error: ${e.message}"
                android.util.Log.e("CRASH_REPORT", "Adapter Error", e)
                return
            }

            val scanButton = findViewById<Button>(R.id.scanButton)
            scanButton.setOnClickListener {
                 scanInbox()
            }
            
            findViewById<Button>(R.id.btnSettings).setOnClickListener {
                startActivity(Intent(this, ConfigActivity::class.java))
            }
            
            findViewById<Button>(R.id.btnAbout).setOnClickListener {
                startActivity(Intent(this, AboutActivity::class.java))
            }
            
            // Add Config/About Buttons (Hack: Finding a container or adding below scan)
            // Just overriding existing button actions for now or we need to edit XML
            // Better: Add these buttons to the layout later. For now, let's assume valid XML exists
            // Let's add them to XML first...
            
            try {
                checkPermission()
                loadRecords()
                val keyword = prefs.getString("keyword", "DONIKKAH")
                statusText.text = "Ready. Scanning for '$keyword'..." 
            } catch (e: Exception) {
                statusText.text = "Load Error: ${e.message}"
                android.util.Log.e("CRASH_REPORT", "Load Error", e)
            }

            permissionButton.setOnClickListener {
                requestSmsPermission()
            }
            
            try {
                registerReceiver(refreshReceiver, IntentFilter("com.example.readsms.UPDATE_LIST"))
            } catch (e: Exception) {
                 android.util.Log.e("CRASH_REPORT", "Receiver Error", e)
            }
        } catch (e: Exception) {
            android.util.Log.e("CRASH_REPORT", "Error in MainActivity: " + e.message, e)
            throw e 
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
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(smsReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(refreshReceiver)
    }

    private fun loadRecords() {
        val records = dbHelper.getAllRecords()
        runOnUiThread {
            adapter.updateData(records)
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
        
        // Optimistically update status to PENDING
        dbHelper.updateStatus(record.id, DatabaseHelper.STATUS_PENDING)
        loadRecords()
    }

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
            statusText.text = "Listening & Ready to Scan..."
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
        var newMmsCount = 0
        val sb = StringBuilder()

        try {
            // 1. Scan SMS (All folders: Inbox, Sent, etc.)
            val uriSms = android.net.Uri.parse("content://sms/")
            val cursorSms = contentResolver.query(uriSms, null, null, null, "date DESC")
            
            if (cursorSms != null && cursorSms.moveToFirst()) {
                var loopCount = 0
                do {
                    val body = cursorSms.getString(cursorSms.getColumnIndexOrThrow("body"))
                    val address = cursorSms.getString(cursorSms.getColumnIndexOrThrow("address")) ?: "Unknown"
                    
                    // Load keyword from prefs
                    val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this)
                    val keyword = prefs.getString("keyword", "DONIKKAH") ?: "DONIKKAH"
                    
                    if (body.contains(keyword, ignoreCase = true)) {
                         val pattern = java.util.regex.Pattern.compile("(?i)$keyword\\s*\\d+")
                         val matcher = pattern.matcher(body)
                         val code = if (matcher.find()) matcher.group() else body
                         
                         if (saveRecordIfNew(code, address)) {
                             newSmsCount++
                             sb.append("[SMS] $code\n")
                         }
                    }
                    loopCount++
                } while (cursorSms.moveToNext() && loopCount < 300)
                cursorSms.close()
            }

            // 2. Scan MMS (All folders)
            // Note: RCS often appears here or in SMS provider on many devices
            val uriMms = android.net.Uri.parse("content://mms/")
            val cursorMms = contentResolver.query(uriMms, null, null, null, "date DESC")
            
            if (cursorMms != null && cursorMms.moveToFirst()) {
                var loopCount = 0
                do {
                    val mmsId = cursorMms.getString(cursorMms.getColumnIndexOrThrow("_id"))
                    val body = getMmsText(mmsId)
                    val address = getMmsAddress(mmsId)
                    
                    if (body.contains("donikkah", ignoreCase = true)) {
                         val pattern = java.util.regex.Pattern.compile("(?i)donikkah\\s*\\d+")
                         val matcher = pattern.matcher(body)
                         val code = if (matcher.find()) matcher.group() else body
                         
                         if (saveRecordIfNew(code, address)) {
                             newMmsCount++
                             sb.append("[MMS] $code\n")
                         }
                    }
                    loopCount++
                } while (cursorMms.moveToNext() && loopCount < 100)
                cursorMms.close()
            }

            val total = newSmsCount + newMmsCount
            if (total > 0) {
                 statusText.text = "Scan Complete.\nFound: $newSmsCount SMS, $newMmsCount MMS/RCS.\n$sb"
            } else {
                 statusText.text = "Scan Complete. No new 'DONIKKAH' messages found."
            }
            
        } catch (e: Exception) {
            statusText.text = "Scan Error: ${e.message}"
            android.util.Log.e("CRASH_REPORT", "Scan Error", e)
        }
    }

    // Helper to avoid duplicate logic
    private fun saveRecordIfNew(code: String, address: String): Boolean {
        // Strict duplicate check on content info to prevent spamming list:
        val alreadyHas = dbHelper.getAllRecords().any { it.code == code }
        if (!alreadyHas) {
            // We use insertSms from DatabaseHelper (which we verified is named insertSms)
            val id = dbHelper.insertSms(code, address)
            val record = SmsRecord(id, code, address, DatabaseHelper.STATUS_PENDING, System.currentTimeMillis())
            resendApi(record)
            return true
        }
        return false
    }

    private fun getMmsText(mmsId: String): String {
        var body = ""
        val partUri = android.net.Uri.parse("content://mms/part")
        val selection = "mid=$mmsId"
        val cursor = contentResolver.query(partUri, null, selection, null, null)
        if (cursor != null && cursor.moveToFirst()) {
            do {
                val type = cursor.getString(cursor.getColumnIndexOrThrow("ct"))
                if ("text/plain" == type) {
                    val text = cursor.getString(cursor.getColumnIndexOrThrow("text"))
                    if (!text.isNullOrEmpty()) {
                        body += text
                    }
                }
            } while (cursor.moveToNext())
            cursor.close()
        }
        return body
    }

    private fun getMmsAddress(mmsId: String): String {
        val uriAddr = android.net.Uri.parse("content://mms/$mmsId/addr")
        // type=137 is 'From' address in PDU
        val cursor = contentResolver.query(uriAddr, null, "type=137", null, null)
        var address = "Unknown"
        if (cursor != null && cursor.moveToFirst()) {
            address = cursor.getString(cursor.getColumnIndexOrThrow("address"))
            cursor.close()
        }
        return address
    }

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
