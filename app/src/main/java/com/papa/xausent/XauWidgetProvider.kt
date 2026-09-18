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
                rv.setImageViewBitmap(R.id.chart, XauChartRenderer.render(d?.market))

                if (d != null) {
                    rv.setTextViewText(R.id.sentiment, "FLOW BUY ${d.buyPct}% | SELL ${d.sellPct}%")
                    rv.setTextViewText(R.id.entries, "Entrate BUY ${d.buyEntries} | SELL ${d.sellEntries}")
                    rv.setTextViewText(R.id.flow, "FLOW TRADER 10m: ${d.flow} • ${d.sample}")
                    val x = d.flowX
                    val xStatus = when (x.status) {
                        "attivo" -> "FLOW X 10m: BUY ${x.buyPct}% | SELL ${x.sellPct}% • Post ${x.totalPosts}"
                        "nessun post" -> "FLOW X 10m: nessun post"
                        else -> "FLOW X 10m: non disponibile"
                    }
                    rv.setTextViewText(R.id.flow_x, xStatus)
                    rv.setTextViewText(R.id.flow_x_entries, "X Entrate: BUY ${x.buyEntries} | SELL ${x.sellEntries}")
                    rv.setTextViewText(R.id.flow_x_sample, "Campione X: ${x.sample}")
                    rv.setTextViewText(R.id.flow_x_diagnostic, "X feed: account OK ${x.accountsOk}/6 • timeline OK ${x.timelinesOk}/6")
                    rv.setTextViewText(R.id.flow_x_last, "ultimo post XAU trovato: ${ageMinutes(x.lastXauPostEpochMs)}")
                    val market = d.market
                    rv.setTextViewText(R.id.trend, "TREND 5m: ${market?.trend ?: "--"}")
                    rv.setTextViewText(R.id.status, "STATO: ${market?.state ?: d.feedStatus}")
                    rv.setTextViewText(R.id.levels, "Resistenza 1: ${fmt(market?.resistance1)}  |  Supporto 1: ${fmt(market?.support1)}")
                    rv.setTextViewText(R.id.fvg, "FVG SELL: ${fmtGap(market?.bearishFvgs?.lastOrNull())}\nFVG BUY: ${fmtGap(market?.bullishFvgs?.lastOrNull())}")
                    val sdf = SimpleDateFormat("HH:mm", Locale.ITALY)
                    rv.setTextViewText(R.id.updated, "Agg.: ${sdf.format(Date(d.updatedEpochMs))}  • FLOW TRADER 10m${if (loading) "  • aggiornamento..." else ""}")
                } else {
                    rv.setTextViewText(R.id.updated, if (error) "Errore dati • tocca ↻" else "Tocca ↻ per aggiornare")
                }
                manager.updateAppWidget(id, rv)
            }
        }

        private fun fmt(value: Double?): String = value?.let { "%.2f".format(Locale.ITALY, it) } ?: "--"
        private fun fmtGap(gap: PriceGap?): String = gap?.let { "${fmt(it.low)}–${fmt(it.high)}" } ?: "--"
        private fun ageMinutes(epochMs: Long?): String = epochMs?.let {
            "${((System.currentTimeMillis() - it).coerceAtLeast(0L) / 60_000L)}m fa"
        } ?: "--"
    }
}
