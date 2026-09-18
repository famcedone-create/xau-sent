package com.papa.xausent

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class XauWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context)
        refreshNow(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        schedule(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            refreshNow(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.papa.xausent.REFRESH"

        fun refreshNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<XauRefreshWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueue(req)
            updateAll(context, loading = true)
        }

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<XauRefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "xau-auto-refresh",
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
        }

        fun updateAll(context: Context, loading: Boolean = false, error: Boolean = false) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, XauWidgetProvider::class.java))
            val d = XauStore.load(context)
            for (id in ids) {
                val rv = RemoteViews(context.packageName, R.layout.widget_xau)
                val intent = Intent(context, XauWidgetProvider::class.java).apply { action = ACTION_REFRESH }
                val pi = PendingIntent.getBroadcast(
                    context, 1001, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                rv.setOnClickPendingIntent(R.id.refresh, pi)

                if (d != null) {
                    rv.setTextViewText(R.id.sentiment, "BUY ${d.buyPct}% | SELL ${d.sellPct}%")
                    rv.setTextViewText(R.id.entries, "Entrate: BUY ${d.buyEntries} | SELL ${d.sellEntries}")
                    rv.setTextViewText(R.id.price, "Prezzo indicativo: ${ForexFactoryClient.fmt(d.price)}")
                    rv.setTextViewText(R.id.targets, "T1: ${ForexFactoryClient.fmt(d.t1)} | T2: ${ForexFactoryClient.fmt(d.t2)}")
                    rv.setTextViewText(R.id.flow, "Flow: ${d.flow}  • campione ${d.sample.lowercase()}")
                    val lp = d.longPct?.toString() ?: "--"
                    val sp = d.shortPct?.toString() ?: "--"
                    rv.setTextViewText(R.id.positioning, "Posizionamento: LONG $lp% | SHORT $sp%")
                    val sdf = SimpleDateFormat("HH:mm", Locale.ITALY)
                    rv.setTextViewText(R.id.updated, "Agg.: ${sdf.format(Date(d.updatedEpochMs))}${if (loading) "  • aggiornamento…" else ""}")
                } else {
                    rv.setTextViewText(R.id.updated, if (error) "Errore dati • tocca ↻" else "Tocca ↻ per aggiornare")
                }
                manager.updateAppWidget(id, rv)
            }
        }
    }
}
