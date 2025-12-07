package com.mamstricks.readsms

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val tvVersion = findViewById<TextView>(R.id.tvVersion)
        
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val version = pInfo.versionName
            tvVersion.text = "Version $version"
        } catch (e: Exception) {
            e.printStackTrace()
            tvVersion.text = "Version Unknown"
        }

        findViewById<android.widget.Button>(R.id.btnDonate).setOnClickListener {
            val uri = android.net.Uri.parse("upi://pay?pa=Q801608072@ybl&pn=PhonePeMerchant&mc=0000&mode=02&purpose=00")
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(this, "No UPI app found", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
