package com.mamstricks.readsms.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "sms_history.db"
        const val DATABASE_VERSION = 1
        const val TABLE_SMS = "sms"
        const val COLUMN_ID = "_id"
        const val COLUMN_CODE = "code"
        const val COLUMN_PHONE = "phone"
        const val COLUMN_STATUS = "status"
        const val COLUMN_TIMESTAMP = "timestamp"

        const val STATUS_PENDING = "PENDING"
        const val STATUS_SUCCESS = "SUCCESS"
        const val STATUS_FAILURE = "FAILURE"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE " + TABLE_SMS + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_CODE + " TEXT,"
                + COLUMN_PHONE + " TEXT,"
                + COLUMN_STATUS + " TEXT,"
                + COLUMN_TIMESTAMP + " INTEGER" + ")")
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SMS)
        onCreate(db)
    }

    fun insertSms(code: String, phone: String): Long {
        // Legacy support - redirection to upsert to ensure uniqueness
        return upsertSms(code, phone)
    }

    fun isNumberVerified(phone: String): Boolean {
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_SMS, 
            arrayOf(COLUMN_ID), 
            "$COLUMN_PHONE = ? AND $COLUMN_STATUS = ?", 
            arrayOf(phone, STATUS_SUCCESS), 
            null, null, null
        )
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }

    fun upsertSms(code: String, phone: String): Long {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_CODE, code)
        values.put(COLUMN_PHONE, phone)
        values.put(COLUMN_STATUS, STATUS_PENDING) // Reset to Pending for new code
        values.put(COLUMN_TIMESTAMP, System.currentTimeMillis())

        // Check if phone exists
        val cursor = db.query(TABLE_SMS, arrayOf(COLUMN_ID), "$COLUMN_PHONE = ?", arrayOf(phone), null, null, null)
        
        if (cursor != null && cursor.moveToFirst()) {
             // UPDATE existing
             val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
             cursor.close()
             db.update(TABLE_SMS, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
             return id
        } else {
             // INSERT new
             cursor?.close()
             return db.insert(TABLE_SMS, null, values)
        }
    }
    
    fun deduplicate() {
        val db = this.writableDatabase
        try {
            // Keep only the LATEST record for each phone number (using MAX(_id))
            db.execSQL("DELETE FROM $TABLE_SMS WHERE $COLUMN_ID NOT IN (SELECT MAX($COLUMN_ID) FROM $TABLE_SMS GROUP BY $COLUMN_PHONE)")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateStatus(id: Long, status: String) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_STATUS, status)
        db.update(TABLE_SMS, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
        // db.close() - Keep open for concurrency
    }

    fun deleteSms(id: Long) {
        val db = this.writableDatabase
        db.delete(TABLE_SMS, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun getAllRecords(): List<SmsRecord> {
        val list = ArrayList<SmsRecord>()
        val selectQuery = "SELECT * FROM $TABLE_SMS ORDER BY $COLUMN_TIMESTAMP DESC"
        val db = this.readableDatabase
        val cursor = db.rawQuery(selectQuery, null)

        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val code = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CODE))
                val phone = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PHONE))
                val status = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_STATUS))
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                list.add(SmsRecord(id, code, phone, status, timestamp))
            } while (cursor.moveToNext())
        }
        cursor.close()
        // db.close() - Keep open for concurrency
        return list
    }
}
