package com.mamstricks.readsms

object DebugRepository {
    interface DebugListener {
        fun onLog(payload: String, responseCode: Int, responseBody: String)
    }

    private var listener: DebugListener? = null

    fun setListener(l: DebugListener?) {
        this.listener = l
    }

    fun log(payload: String, responseCode: Int, responseBody: String) {
        try {
            listener?.onLog(payload, responseCode, responseBody)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
