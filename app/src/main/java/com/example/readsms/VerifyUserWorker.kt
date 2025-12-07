package com.example.readsms

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import com.example.readsms.db.DatabaseHelper
import com.google.gson.Gson
import android.content.Intent
import android.preference.PreferenceManager

class VerifyUserWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    private val client = OkHttpClient()
    
    // We don't use strict data class for payload anymore since it's dynamic
    // But we use this for the fallback/default
    
    override fun doWork(): Result {
        val code = inputData.getString("code")
        val phone = inputData.getString("phone") ?: ""
        val recordId = inputData.getLong("record_id", -1)
        
        if (code.isNullOrEmpty()) {
            Log.e("VerifyUserWorker", "No code provided")
            // If no code, we can't do anything, but it's not a 'retry' situation
            return Result.failure()
        }

        // Fetch user config
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val apiUrl = prefs.getString("api_url", "") ?: ""
        val payloadTemplate = prefs.getString("payload_template", "{ 'code': '%code%', 'phone': '%phone%' }") ?: "{}"
        
        if (apiUrl.isEmpty()) {
            Log.e("VerifyUserWorker", "API URL is empty in settings")
             if (recordId != -1L) updateDbStatus(recordId, DatabaseHelper.STATUS_FAILURE)
            return Result.failure()
        }

        // Build Payload
        // Simple string replacement for placeholders
        var json = payloadTemplate.replace("%code%", code)
        json = json.replace("%phone%", phone)

        Log.d("VerifyUserWorker", "Sending: $json to $apiUrl")

        val mediaType = MediaType.parse("application/json; charset=utf-8")
        val body = RequestBody.create(mediaType, json)

        val requestBuilder = Request.Builder()
            .url(apiUrl)
            .post(body)
            
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

        return try {
            val response = client.newCall(request).execute()
            val respBody = response.body()?.string() ?: ""
            
            // Broadcast for Debugging
            val debugIntent = Intent("com.example.readsms.API_DEBUG")
            debugIntent.setPackage(applicationContext.packageName)
            debugIntent.putExtra("payload", json)
            debugIntent.putExtra("response_code", response.code())
            debugIntent.putExtra("response_body", respBody)
            applicationContext.sendBroadcast(debugIntent)
            
            if (response.isSuccessful) {
                Log.d("VerifyUserWorker", "API Call Success: ${response.code()}")
                if (recordId != -1L) updateDbStatus(recordId, DatabaseHelper.STATUS_SUCCESS)
                Result.success()
            } else {
                 Log.e("VerifyUserWorker", "API Call Failed: ${response.code()} ${response.message()}")
                 if (recordId != -1L) updateDbStatus(recordId, DatabaseHelper.STATUS_FAILURE)
                 // If it's a server error 5xx, we might want to retry
                 if (response.code() >= 500) {
                     Result.retry()
                 } else {
                     Result.failure()
                 }
            }
        } catch (e: Exception) {
            Log.e("VerifyUserWorker", "Exception during API call", e)
             // Broadcast for Debugging (Error)
            val debugIntent = Intent("com.example.readsms.API_DEBUG")
            debugIntent.setPackage(applicationContext.packageName)
            debugIntent.putExtra("payload", json)
            debugIntent.putExtra("response_code", -1)
            debugIntent.putExtra("response_body", "Exception: ${e.message}")
            applicationContext.sendBroadcast(debugIntent)
            
            if (recordId != -1L) updateDbStatus(recordId, DatabaseHelper.STATUS_FAILURE)
            Result.retry()
        }
    }

    private fun updateDbStatus(id: Long, status: String) {
        val dbHelper = DatabaseHelper(applicationContext)
        dbHelper.updateStatus(id, status)
        applicationContext.sendBroadcast(Intent("com.example.readsms.UPDATE_LIST"))
    }
}
