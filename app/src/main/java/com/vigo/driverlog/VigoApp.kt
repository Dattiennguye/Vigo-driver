package com.vigo.driverlog

import android.app.Application
import android.content.Context
import com.vigo.driverlog.data.TripRepository
import com.vigo.driverlog.data.backup.BackupManager
import com.vigo.driverlog.data.db.AppDatabase
import com.vigo.driverlog.data.geo.AddressResolver
import com.vigo.driverlog.data.geo.GeocodeWorker
import com.vigo.driverlog.data.location.LocationService
import com.vigo.driverlog.data.settings.SettingsRepository
import com.vigo.driverlog.report.MapRenderer
import com.vigo.driverlog.report.ReportService

/** Khởi tạo thủ công các phụ thuộc (đủ gọn cho V1, dễ thay bằng Hilt sau này). */
class AppContainer(context: Context) {
    val db: AppDatabase = AppDatabase.build(context)
    val settings = SettingsRepository(context)
    val location = LocationService(context)
    val addressResolver = AddressResolver(context)
    val trips = TripRepository(db)
    val mapRenderer = MapRenderer(context)
    val reports = ReportService(context, trips, mapRenderer)
    val backup = BackupManager(db)
}

class VigoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Bản ghi ghi nhận lúc mất mạng sẽ được bổ sung địa chỉ khi có mạng trở lại.
        GeocodeWorker.enqueue(this)
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as VigoApp).container
