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
        refreshNow(context, manual = false)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        schedule(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            refreshNow(context)
        } else if (intent.action in setOf(ACTION_1M, ACTION_5M, ACTION_15M)) {
            setSelectedTimeframe(context, intent.action.removePrefix(ACTION_PREFIX))
            updateAll(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.papa.xausent.REFRESH"
        private const val ACTION_PREFIX = "com.papa.xausent.CHART_"
        const val ACTION_1M = ACTION_PREFIX + "1m"
        const val ACTION_5M = ACTION_PREFIX + "5m"
        const val ACTION_15M = ACTION_PREFIX + "15m"
        private const val PREFS = "xau_sent_chart"
        private const val TIMEFRAME = "timeframe"

        fun refreshNow(context: Context, manual: Boolean = true) {
            val req = OneTimeWorkRequestBuilder<XauRefreshWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(androidx.work.Data.Builder().putBoolean("manual_refresh", manual).build())
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
                val timeframe = selectedTimeframe(context)
                rv.setOnClickPendingIntent(R.id.chart_1m, chartIntent(context, ACTION_1M))
                rv.setOnClickPendingIntent(R.id.chart_5m, chartIntent(context, ACTION_5M))
                rv.setOnClickPendingIntent(R.id.chart_15m, chartIntent(context, ACTION_15M))
                rv.setTextViewText(R.id.chart_label, "GRAFICO $timeframe")
                rv.setImageViewBitmap(R.id.chart, XauChartRenderer.render(d?.markets?.get(timeframe) ?: d?.market))

                if (d != null) {
                    val ffNow = d.forexFactory5m
                    val ffTen = d.forexFactory10m
                    rv.setTextViewText(R.id.sentiment, "FF NOW 5m: ${flowPercent(ffNow)}")
                    rv.setTextViewText(R.id.entries, "FF 10m: ${flowPercent(ffTen)}")
                    rv.setTextViewText(R.id.flow, "FLOW TRADER 10m: ${flowEntries(ffTen)} • ${ffTen.sample}")
                    val x = d.flowX
                    val xNow = d.flowX5m
                    val xStatus = when (xNow.status) {
                        "attivo" -> "X NOW 5m: BUY ${xNow.buyPct}% | SELL ${xNow.sellPct}%"
                        "nessun dato" -> "X NOW 5m: nessun dato"
                        else -> "FLOW X 10m: non disponibile"
                    }
                    rv.setTextViewText(R.id.flow_x, xStatus)
                    rv.setTextViewText(R.id.flow_x_entries, "X Entrate 5m: BUY ${xNow.buyEntries} | SELL ${xNow.sellEntries}")
                    rv.setTextViewText(R.id.flow_x_sample, "Campione X 5m: ${xNow.sample}")
                    rv.setTextViewText(R.id.flow_x_10, "X 10m: ${flowPercent(d.flowX10m)} • ${d.flowX10m.sample}")
                    rv.setTextViewText(R.id.flow_x_diagnostic, "X feed: account OK ${x.accountsOk}/${x.accountsConfigured} • timeline OK ${x.timelinesOk}/${x.accountsConfigured}")
                    rv.setTextViewText(R.id.flow_x_last, "ultimo post XAU trovato: ${ageMinutes(x.lastXauPostEpochMs)}")
                    val market = d.market
                    rv.setTextViewText(R.id.trend, "TREND 5m: ${market?.trend ?: "--"}")
                    rv.setTextViewText(R.id.status, "STATO: ${market?.state ?: d.feedStatus}")
                    rv.setTextViewText(R.id.levels, "Resistenza 1: ${fmt(market?.resistance1)}  |  Supporto 1: ${fmt(market?.support1)}")
                    rv.setTextViewText(R.id.fvg, "FVG SELL: ${fmtGap(market?.bearishFvgs?.lastOrNull())}\nFVG BUY: ${fmtGap(market?.bullishFvgs?.lastOrNull())}")
                    rv.setTextViewText(R.id.ai_insight, formatAi(d.aiInsight))
                    val sdf = SimpleDateFormat("HH:mm", Locale.ITALY)
                    rv.setTextViewText(R.id.updated, "Agg.: ${sdf.format(Date(d.updatedEpochMs))}  • FLOW TRADER 10m${if (loading) "  • aggiornamento..." else ""}")
                } else {
                    rv.setTextViewText(R.id.updated, if (error) "Errore dati • tocca ↻" else "Tocca ↻ per aggiornare")
                }
                manager.updateAppWidget(id, rv)
            }
        }

        private fun fmt(value: Double?): String = value?.let { "%.2f".format(Locale.ITALY, it) } ?: "--"
        private fun flowPercent(flow: FlowSummary): String = if (flow.status == "nessun dato") "nessun dato" else "BUY ${flow.buyPct}% | SELL ${flow.sellPct}%"
        private fun flowEntries(flow: FlowSummary): String = if (flow.status == "nessun dato") "nessun dato" else "BUY ${flow.buyEntries} | SELL ${flow.sellEntries}"
        private fun selectedTimeframe(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TIMEFRAME, "5m") ?: "5m"
        private fun setSelectedTimeframe(context: Context, value: String) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(TIMEFRAME, value).apply() }
        private fun chartIntent(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(context, action.hashCode(), Intent(context, XauWidgetProvider::class.java).apply { this.action = action }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        private fun fmtGap(gap: PriceGap?): String = gap?.let { "${fmt(it.low)}–${fmt(it.high)}" } ?: "--"
        private fun ageMinutes(epochMs: Long?): String = epochMs?.let {
            "${((System.currentTimeMillis() - it).coerceAtLeast(0L) / 60_000L)}m fa"
        } ?: "--"

        private fun formatAi(insight: AiInsight): String = if (!insight.available) {
            "LETTURA AI: non disponibile"
        } else {
            "LETTURA AI\nSTRUTTURA: ${insight.structure}\nNOW: ${insight.now}\nFLOW: ${insight.flow}\nLETTURA: ${insight.reading}\nCONF: ${insight.confidence}%"
        }
    }
}
