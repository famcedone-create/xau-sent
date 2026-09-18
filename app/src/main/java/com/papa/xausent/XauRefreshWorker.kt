package com.papa.xausent

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class XauRefreshWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val flow = try {
                ForexFactoryClient.fetch()
            } catch (_: Throwable) {
                null
            }
            val market = try {
                BiQuoteClient.fetch()
            } catch (_: Throwable) {
                null
            }
            val flowX = XGuestClient.fetch()
            val base = flow ?: XauData(
                buyPct = 0, sellPct = 0, buyEntries = 0, sellEntries = 0,
                price = null, flow = "non disponibile", longPct = null, shortPct = null,
                sample = "--", market = null,
                feedStatus = "feed 5m non disponibile", flowX = flowX,
                updatedEpochMs = System.currentTimeMillis()
            )
            XauStore.save(applicationContext, base.copy(
                market = market,
                feedStatus = if (market == null) "feed 5m non disponibile" else "attivo",
                flowX = flowX
            ))
            XauWidgetProvider.updateAll(applicationContext)
            Result.success()
        } catch (_: Throwable) {
            XauWidgetProvider.updateAll(applicationContext, error = true)
            Result.retry()
        }
    }
}
