package com.papa.xausent

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Data
import androidx.work.WorkManager

class MainActivity : Activity() {
    private lateinit var watchlistContainer: LinearLayout
    private lateinit var watchlistTitle: TextView
    private lateinit var newHandleField: EditText
    private val accountRows = mutableListOf<AccountRow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        XauWidgetProvider.schedule(this)

        val status = findViewById<TextView>(R.id.status)
        val button = findViewById<Button>(R.id.buttonRefresh)
        watchlistContainer = findViewById(R.id.xWatchlistContainer)
        watchlistTitle = findViewById(R.id.xWatchlistTitle)
        newHandleField = findViewById(R.id.xNewHandle)
        renderWatchlist()
        findViewById<Button>(R.id.buttonAddXAccount).setOnClickListener {
            val normalized = XWatchlistStore.normalize(listOf(newHandleField.text.toString())).firstOrNull()
            if (normalized == null) status.text = "Handle non valido. Usa solo lettere, numeri e underscore."
            else if (accountRows.any { it.handle.equals(normalized, ignoreCase = true) }) status.text = "Account già disponibile."
            else {
                val available = accountRows.map { it.handle } + normalized
                XWatchlistStore.saveAvailable(this, available)
                if (XWatchlistStore.active(this).size < XWatchlistStore.MAX_ACCOUNTS) {
                    XWatchlistStore.saveActive(this, XWatchlistStore.active(this) + normalized)
                }
                newHandleField.text.clear()
                renderWatchlist()
            }
        }
        button.setOnClickListener {
            saveAccounts()
            status.text = "Sto aggiornando… poi puoi tornare alla schermata Home."
            val req = OneTimeWorkRequestBuilder<XauRefreshWorker>()
                .setInputData(Data.Builder().putBoolean("manual_refresh", true).build())
                .build()
            WorkManager.getInstance(this).enqueue(req)
        }
    }

    override fun onPause() {
        saveAccounts()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::watchlistContainer.isInitialized) renderWatchlist()
    }

    private fun renderWatchlist() {
        watchlistContainer.removeAllViews()
        accountRows.clear()
        val handles = XWatchlistStore.available(this)
        val active = XWatchlistStore.active(this).map { it.lowercase() }.toSet()
        val statuses = XauStore.load(this)?.flowX?.accountStatuses.orEmpty()
        watchlistTitle.text = "ACCOUNT X MONITORATI (${active.size}/${XWatchlistStore.MAX_ACCOUNTS})"
        handles.forEach { addAccountRow(it, it.lowercase() in active, if (it.lowercase() in active) statuses[it] else null) }
    }

    private fun addAccountRow(handle: String, isActive: Boolean, status: String?) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val check = CheckBox(this).apply {
            text = handle
            isChecked = isActive
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val state = TextView(this).apply { text = status ?: "--"; setPadding(8, 0, 8, 0) }
        val remove = Button(this).apply {
            text = "RIMUOVI"
            setOnClickListener {
                watchlistContainer.removeView(row)
                accountRows.removeAll { it.handle.equals(handle, ignoreCase = true) }
                saveAccounts()
            }
        }
        check.setOnCheckedChangeListener { _, checked ->
            if (checked && accountRows.count { it.check.isChecked } > XWatchlistStore.MAX_ACCOUNTS) {
                check.isChecked = false
            } else {
                saveAccounts()
            }
        }
        row.addView(check)
        row.addView(state)
        row.addView(remove)
        watchlistContainer.addView(row)
        accountRows += AccountRow(handle, check)
    }

    private fun saveAccounts() {
        if (!::watchlistContainer.isInitialized) return
        val available = accountRows.map { it.handle }
        val selected = accountRows.filter { it.check.isChecked }.map { it.handle }
        XWatchlistStore.saveAvailable(this, available)
        XWatchlistStore.saveActive(this, selected)
        watchlistTitle.text = "ACCOUNT X MONITORATI (${selected.size}/${XWatchlistStore.MAX_ACCOUNTS})"
    }

    private data class AccountRow(val handle: String, val check: CheckBox)
}
