package com.papa.xausent

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Data
import androidx.work.WorkManager

class MainActivity : Activity() {
    private lateinit var watchlistContainer: LinearLayout
    private lateinit var watchlistTitle: TextView
    private val handleFields = mutableListOf<EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        XauWidgetProvider.schedule(this)

        val status = findViewById<TextView>(R.id.status)
        val button = findViewById<Button>(R.id.buttonRefresh)
        watchlistContainer = findViewById(R.id.xWatchlistContainer)
        watchlistTitle = findViewById(R.id.xWatchlistTitle)
        renderWatchlist()
        findViewById<Button>(R.id.buttonAddXAccount).setOnClickListener {
            if (handleFields.size >= XWatchlistStore.MAX_ACCOUNTS) {
                status.text = "Massimo ${XWatchlistStore.MAX_ACCOUNTS} account X."
            } else {
                addHandleField("")
                saveWatchlist()
            }
        }
        button.setOnClickListener {
            saveWatchlist()
            status.text = "Sto aggiornando… poi puoi tornare alla schermata Home."
            val req = OneTimeWorkRequestBuilder<XauRefreshWorker>()
                .setInputData(Data.Builder().putBoolean("manual_refresh", true).build())
                .build()
            WorkManager.getInstance(this).enqueue(req)
        }
    }

    override fun onPause() {
        saveWatchlist()
        super.onPause()
    }

    private fun renderWatchlist() {
        watchlistContainer.removeAllViews()
        handleFields.clear()
        val handles = XWatchlistStore.load(this)
        watchlistTitle.text = "ACCOUNT X MONITORATI (${handles.size}/${XWatchlistStore.MAX_ACCOUNTS})"
        handles.forEach(::addHandleField)
    }

    private fun addHandleField(handle: String) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val field = EditText(this).apply {
            setText(handle)
            hint = "@handle X"
            singleLine = true
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val remove = Button(this).apply {
            text = "RIMUOVI"
            setOnClickListener {
                watchlistContainer.removeView(row)
                handleFields.remove(field)
                saveWatchlist()
            }
        }
        row.addView(field)
        row.addView(remove)
        watchlistContainer.addView(row)
        handleFields += field
    }

    private fun saveWatchlist() {
        XWatchlistStore.save(this, handleFields.map { it.text.toString() })
        watchlistTitle.text = "ACCOUNT X MONITORATI (${XWatchlistStore.load(this).size}/${XWatchlistStore.MAX_ACCOUNTS})"
    }
}
