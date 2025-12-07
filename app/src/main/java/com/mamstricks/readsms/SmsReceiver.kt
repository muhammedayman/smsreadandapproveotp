package com.mamstricks.readsms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.regex.Pattern
import com.mamstricks.readsms.db.DatabaseHelper
import android.widget.Toast
import android.os.Handler
import android.os.Looper

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val bundle = intent.extras
            if (bundle != null) {
                // Legacy PDU parsing for API < 19
                val pdus = bundle.get("pdus") as? Array<*>
                if (pdus != null) {
                    for (pdu in pdus) {
                        // Cast to ByteArray safely
                        val pduBytes = pdu as ByteArray
                        val message = SmsMessage.createFromPdu(pduBytes)
                        
                        val body = message.messageBody
                        val sender = message.originatingAddress ?: "Unknown"
                        
                        // DEBUG: Show toast for ANY SMS to verify receiver works
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "Debug: SMS from $sender: $body", Toast.LENGTH_LONG).show()
                        }
                        
                        Log.d("SmsReceiver", "Received SMS from $sender: $body")
                        
                        // Pattern: Case insensitive, optional space, starts with DONIKKAH
                        val pattern = Pattern.compile("(?i)donikkah\\s*\\d+")
                        val matcher = pattern.matcher(body)

                        if (matcher.find()) {
                            val code = matcher.group()
                            Log.d("SmsReceiver", "Found code: $code")
                            
                            val dbHelper = DatabaseHelper(context)
                            val id = dbHelper.insertSms(code, sender)
                            
                            triggerWorker(context, code, sender, id)
                            
                            // Notify UI to refresh
                            context.sendBroadcast(Intent("com.mamstricks.readsms.UPDATE_LIST"))
                        }
                    }
                }
            }
        }
    }

    private fun triggerWorker(context: Context, code: String, phone: String, recordId: Long) {
        val inputData = Data.Builder()
            .putString("code", code)
            .putString("phone", phone)
            .putLong("record_id", recordId)
            .build()
        
        val workRequest = OneTimeWorkRequestBuilder<VerifyUserWorker>()
            .setInputData(inputData)
            .build()
        
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
