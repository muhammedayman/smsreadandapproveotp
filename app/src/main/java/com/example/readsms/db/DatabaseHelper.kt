package com.example.readsms.db

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
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_CODE, code)
        values.put(COLUMN_PHONE, phone)
        values.put(COLUMN_STATUS, STATUS_PENDING)
        values.put(COLUMN_TIMESTAMP, System.currentTimeMillis())
        val id = db.insert(TABLE_SMS, null, values)
        // db.close() - Keep open for concurrency
        return id
    }

    fun updateStatus(id: Long, status: String) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_STATUS, status)
        db.update(TABLE_SMS, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
        // db.close() - Keep open for concurrency
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
