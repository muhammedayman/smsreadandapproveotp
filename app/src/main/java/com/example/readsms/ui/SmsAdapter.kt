package com.example.readsms.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.TextView
import com.example.readsms.R
import com.example.readsms.db.DatabaseHelper
import com.example.readsms.db.SmsRecord

class SmsAdapter(
    private var records: List<SmsRecord>,
    private val onResendClick: (SmsRecord) -> Unit
) : BaseAdapter() {

    fun updateData(newRecords: List<SmsRecord>) {
        records = newRecords
        notifyDataSetChanged()
    }

    override fun getCount(): Int = records.size

    override fun getItem(position: Int): Any = records[position]

    override fun getItemId(position: Int): Long = records[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View
        val holder: ViewHolder

        if (convertView == null) {
            view = LayoutInflater.from(parent?.context).inflate(R.layout.list_item_sms, parent, false)
            holder = ViewHolder(view)
            view.tag = holder
        } else {
            view = convertView
            holder = view.tag as ViewHolder
        }

        val record = records[position]
        holder.code.text = "Code: " + record.code
        holder.phone.text = "From: " + record.phone
        holder.status.text = "Status: " + record.status

        holder.resendBtn.setOnClickListener {
            onResendClick(record)
        }
        
        // Hide resend button if success
        if (record.status == DatabaseHelper.STATUS_SUCCESS) {
             holder.resendBtn.visibility = View.GONE
        } else {
             holder.resendBtn.visibility = View.VISIBLE
        }

        return view
    }

    private class ViewHolder(view: View) {
        val code: TextView = view.findViewById(R.id.textCode)
        val phone: TextView = view.findViewById(R.id.textPhone)
        val status: TextView = view.findViewById(R.id.textStatus)
        val resendBtn: Button = view.findViewById(R.id.btnResend)
    }
}
