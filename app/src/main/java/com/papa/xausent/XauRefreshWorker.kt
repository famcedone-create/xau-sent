package com.papa.xausent

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class XauRefreshWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val flow = ForexFactoryClient.fetch()
            val market = OandaClient.fetch(applicationContext)
            XauStore.save(applicationContext, flow.copy(market = market, feedStatus = if (market == null) "feed 5m non configurato" else "attivo"))
            XauWidgetProvider.updateAll(applicationContext)
            Result.success()
        } catch (_: Throwable) {
            XauWidgetProvider.updateAll(applicationContext, error = true)
            Result.retry()
        }
    }
}
