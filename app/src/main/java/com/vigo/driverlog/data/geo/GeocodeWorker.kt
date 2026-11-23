package com.vigo.driverlog.data.geo

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vigo.driverlog.VigoApp
import com.vigo.driverlog.domain.AddressStatus
import java.util.concurrent.TimeUnit

/**
 * Chuyển đổi địa chỉ cho các bản ghi đang chờ (ghi nhận lúc mất mạng).
 * Chỉ ĐIỀN địa chỉ còn trống – không bao giờ đụng tới thời gian hay tọa độ.
 */
class GeocodeWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as VigoApp).container
        val dao = container.db.eventDao()
        val resolver = container.addressResolver
        val pending = dao.pendingAddress()
        var needRetry = false
        for (e in pending) {
            val lat = e.latitude ?: continue
            val lng = e.longitude ?: continue
            when (val r = resolver.resolve(lat, lng)) {
                is GeocodeResult.Found -> dao.fillAddress(e.id, r.address, AddressStatus.RESOLVED.name, System.currentTimeMillis())
                GeocodeResult.NotFound -> dao.fillAddress(e.id, null, AddressStatus.NOT_FOUND.name, System.currentTimeMillis())
                GeocodeResult.Unavailable -> { needRetry = true; break }
            }
        }
        return if (needRetry && runAttemptCount < 20) Result.retry() else Result.success()
    }

    companion object {
        private const val UNIQUE = "geocode-pending"

        fun enqueue(context: Context) {
            val req = OneTimeWorkRequestBuilder<GeocodeWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }
    }
}
