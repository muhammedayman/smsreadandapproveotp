package com.mamstricks.readsms

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import com.mamstricks.readsms.db.DatabaseHelper
import com.google.gson.Gson
import android.content.Intent
import android.preference.PreferenceManager

class VerifyUserWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    private val client = OkHttpClient()
    
    // We don't use strict data class for payload anymore since it's dynamic
    // But we use this for the fallback/default
    
    override fun doWork(): Result {
        var recordId = -1L
        var json = "{}"
        
        try {
            val code = inputData.getString("code")
            val phone = inputData.getString("phone") ?: ""
            recordId = inputData.getLong("record_id", -1)
            
            // Broadcast START
            val startIntent = Intent("com.mamstricks.readsms.API_DEBUG")
            startIntent.setPackage(applicationContext.packageName)
            startIntent.putExtra("payload", "Starting Worker for ID: $recordId")
            startIntent.putExtra("response_code", 100) // 100 = Started
            startIntent.putExtra("response_body", "Preparing API Call...")
            applicationContext.sendBroadcast(startIntent)
            
            // Static Log
            DebugRepository.log("Starting Worker for ID: $recordId", 100, "Preparing API Call...")
            
            if (code.isNullOrEmpty()) {
                Log.e("VerifyUserWorker", "No code provided")
                return Result.failure()
            }
    
            // Fetch user config
            val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
            val apiUrl = prefs.getString("api_url", "") ?: ""
            val keyword = prefs.getString("keyword", "DONIKKAH") ?: "DONIKKAH"
            // Use escaped double quotes for valid JSON default
            val payloadTemplate = prefs.getString("payload_template", "{ \"code\": \"%code%\", \"phone\": \"%phone%\" }") ?: "{}"
            
            if (apiUrl.isEmpty()) {
                Log.e("VerifyUserWorker", "API URL is empty in settings")
                 if (recordId != -1L) updateDbStatus(recordId, DatabaseHelper.STATUS_FAILURE)
                return Result.failure()
            }
    
            // Build Payload
            // CLEAN THE CODE (Safety Net)
            // Even if regex failed or it's an old record, strip the keyword if present
            val cleanCode = code.replace(keyword, "", ignoreCase = true).trim()
            
            // Simple string replacement for placeholders
            json = payloadTemplate.replace("%code%", cleanCode)
            json = json.replace("%phone%", phone)
            
            // Fix JSON format: Replace single quotes with double quotes (Common user error)
            json = json.replace("'", "\"")
    
            Log.d("VerifyUserWorker", "Sending: $json to $apiUrl")
    
            val mediaType = MediaType.parse("application/json; charset=utf-8")
            val body = RequestBody.create(mediaType, json)
    
            val requestBuilder = Request.Builder()
                .url(apiUrl)
                .post(body)
                .addHeader("Content-Type", "application/json")
                
            // Add Headers
            val hk1 = prefs.getString("header_key_1", "") ?: ""
            val hv1 = prefs.getString("header_val_1", "") ?: ""
            if (hk1.isNotEmpty()) {
                requestBuilder.addHeader(hk1, hv1)
            }
            
            val hk2 = prefs.getString("header_key_2", "") ?: ""
            val hv2 = prefs.getString("header_val_2", "") ?: ""
            if (hk2.isNotEmpty()) {
                requestBuilder.addHeader(hk2, hv2)
            }
            
            val request = requestBuilder.build()
    
            val response = client.newCall(request).execute()
            val respBody = response.body()?.string() ?: ""
            
            // Broadcast for Debugging (Result)
            val debugIntent = Intent("com.mamstricks.readsms.API_DEBUG")
            debugIntent.setPackage(applicationContext.packageName)
            debugIntent.putExtra("payload", json)
            debugIntent.putExtra("response_code", response.code())
            debugIntent.putExtra("response_body", respBody)
            applicationContext.sendBroadcast(debugIntent)
            
            // Static Log
            DebugRepository.log(json, response.code(), respBody)
            
            if (response.isSuccessful) {
                Log.d("VerifyUserWorker", "API Call Success: ${response.code()}")
                if (recordId != -1L) updateDbStatus(recordId, DatabaseHelper.STATUS_SUCCESS)
                return Result.success()
            } else {
                 Log.e("VerifyUserWorker", "API Call Failed: ${response.code()} ${response.message()}")
                 if (recordId != -1L) updateDbStatus(recordId, DatabaseHelper.STATUS_FAILURE)
                 // If it's a server error 5xx, we might want to retry
                 if (response.code() >= 500) {
                     return Result.retry()
                 } else {
                     return Result.failure()
                 }
            }
        } catch (e: Throwable) {
             Log.e("VerifyUserWorker", "Exception during API call", e)
             // Broadcast for Debugging (Error)
            val debugIntent = Intent("com.mamstricks.readsms.API_DEBUG")
            debugIntent.setPackage(applicationContext.packageName)
            debugIntent.putExtra("payload", json)
            debugIntent.putExtra("response_code", -1)
            debugIntent.putExtra("response_body", "Exception: ${e.message}\n${Log.getStackTraceString(e)}")
            applicationContext.sendBroadcast(debugIntent)
            
            // Static Log
            DebugRepository.log(json, -1, "Exception: ${e.message}\n${Log.getStackTraceString(e)}")
            
            if (recordId != -1L) updateDbStatus(recordId, DatabaseHelper.STATUS_FAILURE)
            return Result.failure()
        }
    }

    private fun updateDbStatus(id: Long, status: String) {
        val dbHelper = DatabaseHelper(applicationContext)
        dbHelper.updateStatus(id, status)
        applicationContext.sendBroadcast(Intent("com.mamstricks.readsms.UPDATE_LIST"))
    }
}
