package com.papa.xausent

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class XauRefreshWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val data = ForexFactoryClient.fetch()
            XauStore.save(applicationContext, data)
            XauWidgetProvider.updateAll(applicationContext)
            Result.success()
        } catch (_: Throwable) {
            XauWidgetProvider.updateAll(applicationContext, error = true)
            Result.retry()
        }
    }
}
