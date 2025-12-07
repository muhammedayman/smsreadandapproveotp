package com.mamstricks.readsms.db

data class SmsRecord(
    val id: Long,
    val code: String,
    val phone: String,
    val status: String,
    val timestamp: Long
)
