package com.papa.xausent

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        XauWidgetProvider.schedule(this)

        val status = findViewById<TextView>(R.id.status)
        val button = findViewById<Button>(R.id.buttonRefresh)
        button.setOnClickListener {
            status.text = "Sto aggiornando… poi puoi tornare alla schermata Home."
            val req = OneTimeWorkRequestBuilder<XauRefreshWorker>().build()
            WorkManager.getInstance(this).enqueue(req)
        }
    }
}
