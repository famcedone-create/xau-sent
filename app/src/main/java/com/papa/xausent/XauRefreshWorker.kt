package com.papa.xausent

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class XauRefreshWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val manual = inputData.getBoolean("manual_refresh", false)
            RemoteConfigClient.refresh(applicationContext)
            val previous = XauStore.load(applicationContext)
            val flow = try {
                ForexFactoryClient.fetch()
            } catch (_: Throwable) {
                null
            }
            val markets = listOf("1m", "5m", "15m", "1h").mapNotNull { interval ->
                try { interval to BiQuoteClient.fetch(interval) } catch (_: Throwable) { null }
            }.toMap()
            val market = markets["5m"]
            val xWindows = XGuestClient.fetchWindows(applicationContext)
            val base = flow ?: XauData(
                buyPct = 0, sellPct = 0, buyEntries = 0, sellEntries = 0,
                price = null, flow = "non disponibile", longPct = null, shortPct = null,
                sample = "--", market = null,
                feedStatus = "feed 5m non disponibile", flowX = xWindows.ten,
                markets = markets, flowX5m = xWindows.five, flowX10m = xWindows.tenSummary,
                updatedEpochMs = System.currentTimeMillis()
            )
            val current = base.copy(
                market = market,
                feedStatus = if (market == null) "feed 5m non disponibile" else "attivo",
                flowX = xWindows.ten,
                markets = markets,
                forexFactory5m = base.forexFactory5m,
                forexFactory10m = base.forexFactory10m,
                flowX5m = xWindows.five,
                flowX10m = xWindows.tenSummary
            )
            val withAi = if (manual) {
                val newAi = AiInsightClient.fetch(applicationContext, current)
                if (newAi.available) {
                    current.copy(aiInsight = newAi)
                } else {
                    current.copy(aiInsight = previous?.aiInsight ?: AiInsight())
                }
            } else {
                current.copy(aiInsight = previous?.aiInsight ?: AiInsight())
            }
            XauStore.save(applicationContext, withAi)
            XauWidgetProvider.updateAll(applicationContext)
            Result.success()
        } catch (_: Throwable) {
            XauWidgetProvider.updateAll(applicationContext, error = true)
            Result.retry()
        }
    }
}
