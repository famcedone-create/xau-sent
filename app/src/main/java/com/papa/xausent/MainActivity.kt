package com.papa.xausent

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
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
        val token = findViewById<EditText>(R.id.oandaToken)
        val account = findViewById<EditText>(R.id.oandaAccount)
        val saveConfig = findViewById<Button>(R.id.buttonSaveConfig)
        SecureConfig.read(this)?.let {
            account.setText(it.account)
            token.setText(it.token)
        }
        saveConfig.setOnClickListener {
            SecureConfig.save(this, token.text.toString(), account.text.toString())
            status.text = "Configurazione OANDA salvata. Premi AGGIORNA per leggere il feed 5m."
        }
        button.setOnClickListener {
            status.text = "Sto aggiornando… poi puoi tornare alla schermata Home."
            val req = OneTimeWorkRequestBuilder<XauRefreshWorker>().build()
            WorkManager.getInstance(this).enqueue(req)
        }
    }
}
