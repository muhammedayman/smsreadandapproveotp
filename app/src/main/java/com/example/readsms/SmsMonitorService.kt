package com.example.readsms

import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.readsms.db.DatabaseHelper
import com.example.readsms.db.SmsRecord

class SmsMonitorService : Service() {

    private lateinit var dbHelper: DatabaseHelper
    private val handler = Handler()
    private val CHANNEL_ID = "SmsMonitorChannel"

    private val smsObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            Log.d("SmsMonitor", "SMS/MMS DB Changed. Scanning...")
            // Delay scan slightly to ensure write completes
            handler.postDelayed({
                scanAndProcess()
            }, 2000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        dbHelper = DatabaseHelper(this)
        Log.d("SmsMonitor", "Service Created")
        
        createNotificationChannel()
        val notification = createNotification()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }
        
        // Register Observers for both SMS and MMS
        contentResolver.registerContentObserver(Uri.parse("content://sms/"), true, smsObserver)
        contentResolver.registerContentObserver(Uri.parse("content://mms/"), true, smsObserver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("SmsMonitor", "Service Started")
        
        val notification = createNotification()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }
        
        // Sticky means if the OS kills it for memory, it recreates it automatically
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val serviceChannel = android.app.NotificationChannel(
                CHANNEL_ID,
                "SMS Monitor Service",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): android.app.Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Monitor Running")
            .setContentText("Listening for OTP messages...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(smsObserver)
        Log.d("SmsMonitor", "Service Destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun scanAndProcess() {
        try {
            var foundSomething = false
            
            // 1. Scan SMS
            val cursorSms = contentResolver.query(Uri.parse("content://sms/"), null, null, null, "date DESC")
            if (cursorSms != null && cursorSms.moveToFirst()) {
                var loopCount = 0
                do {
                    val body = cursorSms.getString(cursorSms.getColumnIndexOrThrow("body"))
                    val address = cursorSms.getString(cursorSms.getColumnIndexOrThrow("address")) ?: "Unknown"
                    if (checkForDonikkah(body, address)) foundSomething = true
                    loopCount++
                } while (cursorSms.moveToNext() && loopCount < 50)
                cursorSms.close()
            }

            // 2. Scan MMS
            val cursorMms = contentResolver.query(Uri.parse("content://mms/"), null, null, null, "date DESC")
            if (cursorMms != null && cursorMms.moveToFirst()) {
                var loopCount = 0
                do {
                    val mmsId = cursorMms.getString(cursorMms.getColumnIndexOrThrow("_id"))
                    val body = getMmsText(mmsId)
                    val address = getMmsAddress(mmsId)
                    if (checkForDonikkah(body, address)) foundSomething = true
                    loopCount++
                } while (cursorMms.moveToNext() && loopCount < 50)
                cursorMms.close()
            }
            
            if (foundSomething) {
                // Notify UI to update if visible
                sendBroadcast(Intent("com.example.readsms.UPDATE_LIST"))
            }
        } catch (e: Exception) {
            Log.e("SmsMonitor", "Bg Scan Error", e)
        }
    }

    private fun checkForDonikkah(body: String, address: String): Boolean {
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(this)
        val keyword = prefs.getString("keyword", "DONIKKAH") ?: "DONIKKAH"

        if (body.contains(keyword, ignoreCase = true)) {
             // Use capturing group () around the code part
             val pattern = java.util.regex.Pattern.compile("(?i)$keyword\\s*([a-zA-Z0-9]+)")
             val matcher = pattern.matcher(body)
             // Extract group 1 (the code) instead of the whole match
             val code = if (matcher.find()) matcher.group(1) else body
             
             // Check DB Duplicate - DB Helper handles Upsert now
             // We allow re-processing if it helps with updates, but upsert ensures unique Phone
             val id = dbHelper.insertSms(code, address)
             val record = SmsRecord(id, code, address, DatabaseHelper.STATUS_PENDING, System.currentTimeMillis())
             
             // Trigger Worker
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
                 return true
             }
        return false
    }

    private fun getMmsText(mmsId: String): String {
        var body = ""
        val partUri = Uri.parse("content://mms/part")
        val selection = "mid=$mmsId"
        val cursor = contentResolver.query(partUri, null, selection, null, null)
        if (cursor != null && cursor.moveToFirst()) {
            do {
                val type = cursor.getString(cursor.getColumnIndexOrThrow("ct"))
                if ("text/plain" == type) {
                    val text = cursor.getString(cursor.getColumnIndexOrThrow("text"))
                    if (!text.isNullOrEmpty()) body += text
                }
            } while (cursor.moveToNext())
            cursor.close()
        }
        return body
    }

    private fun getMmsAddress(mmsId: String): String {
        val uriAddr = Uri.parse("content://mms/$mmsId/addr")
        val cursor = contentResolver.query(uriAddr, null, "type=137", null, null)
        var address = "Unknown"
        if (cursor != null && cursor.moveToFirst()) {
            address = cursor.getString(cursor.getColumnIndexOrThrow("address"))
            cursor.close()
        }
        return address
    }
}
