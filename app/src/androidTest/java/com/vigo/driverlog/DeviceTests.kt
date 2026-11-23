package com.vigo.driverlog

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.vigo.driverlog.data.CapturedLocation
import com.vigo.driverlog.data.RecordRejected
import com.vigo.driverlog.data.TapMoment
import com.vigo.driverlog.data.TripProfile
import com.vigo.driverlog.data.TripRepository
import com.vigo.driverlog.data.backup.BackupManager
import com.vigo.driverlog.data.db.AppDatabase
import com.vigo.driverlog.data.location.Fix
import com.vigo.driverlog.data.location.FixResult
import com.vigo.driverlog.data.location.LocationService
import com.vigo.driverlog.data.settings.AppSettings
import com.vigo.driverlog.domain.EventType
import com.vigo.driverlog.domain.LocationSource
import com.vigo.driverlog.domain.TripPhase
import com.vigo.driverlog.domain.TripStatus
import com.vigo.driverlog.report.MapRenderer
import com.vigo.driverlog.report.ReportRenderer
import com.vigo.driverlog.report.ReportService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Rule
import java.time.LocalDate
import java.time.ZoneId

/**
 * Kiểm thử chạy trên thiết bị/emulator Android thật: Room, logic hành trình, chống trùng,
 * dữ liệu còn sau khi đóng/mở lại DB, sao lưu–khôi phục, dựng và lưu ảnh báo cáo.
 * Dữ liệu trong file này CHỈ dùng cho kiểm thử, không có trong ứng dụng.
 */
@RunWith(AndroidJUnit4::class)
class DeviceTests {
    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var repo: TripRepository
    private val profile = TripProfile("Nguyễn Văn Kiểm Thử", "30A-999.99", "Công ty Kiểm thử")
    private var clock = 0L
    private var elapsed = 1_000_000L

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        repo = TripRepository(db)
        clock = LocalDate.now().atTime(7, 30).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    @After fun tearDown() { db.close() }

    private fun gps(lat: Double, lng: Double, acc: Float = 6f) =
        CapturedLocation.Gps(Fix(lat, lng, acc, clock, elapsed, false), LocationSource.GPS_FRESH, 0)

    /** Tiến đồng hồ giả lập [minutes] phút rồi ghi sự kiện. */
    private suspend fun rec(type: EventType, minutes: Int, lat: Double = 21.0285, lng: Double = 105.8542, note: String? = null) =
        run {
            clock += minutes * 60_000L; elapsed += minutes * 60_000L
            repo.record(type, TapMoment(clock, elapsed), gps(lat, lng), profile, note)
        }

    @Test fun fullTripFlowAndRules() = runBlocking<Unit> {
        assertEquals(TripPhase.NOT_STARTED, repo.currentPhase())
        try { rec(EventType.ARRIVE, 1); fail("ARRIVE trước START phải bị chặn") } catch (e: RecordRejected) {}

        rec(EventType.START_TRIP, 1)
        assertEquals(TripPhase.MOVING, repo.currentPhase())
        try { rec(EventType.START_TRIP, 1); fail("START khi đang có chuyến phải bị chặn") } catch (e: RecordRejected) {}
        try { rec(EventType.PARK_END, 1); fail("PARK_END khi đang chạy phải bị chặn") } catch (e: RecordRejected) {}

        rec(EventType.ARRIVE, 40, 21.0368, 105.8342)
        assertEquals(TripPhase.ARRIVED, repo.currentPhase())
        rec(EventType.PARK_START, 3)
        assertEquals(TripPhase.PARKED, repo.currentPhase())
        try { rec(EventType.END_TRIP, 1); fail("Kết thúc khi đang đỗ phải bị chặn") } catch (e: RecordRejected) {}
        rec(EventType.QUICK_LOG, 2, note = "Giao tài liệu") // ghi nhận nhanh không đổi trạng thái
        assertEquals(TripPhase.PARKED, repo.currentPhase())
        rec(EventType.PARK_END, 45)
        assertEquals(TripPhase.LEFT_PARKING, repo.currentPhase())
        rec(EventType.RESUME, 2)
        rec(EventType.ARRIVE, 30, 21.0122, 105.8250)
        rec(EventType.PARK_START, 1)
        rec(EventType.PARK_END, 20)
        rec(EventType.END_TRIP, 15, 21.0285, 105.8542)
        assertEquals(TripPhase.NOT_STARTED, repo.currentPhase())

        val trips = db.tripDao().all()
        assertEquals(1, trips.size)
        assertEquals(TripStatus.COMPLETED.name, trips[0].status)
        assertNotNull(trips[0].endTime)
        assertEquals(10, db.eventDao().all().size)
    }

    @Test fun duplicateTapIsRejected() = runBlocking<Unit> {
        rec(EventType.START_TRIP, 1)
        rec(EventType.QUICK_LOG, 1)
        // bấm lại sau 1 giây
        elapsed += 1_000; clock += 1_000
        try {
            repo.record(EventType.QUICK_LOG, TapMoment(clock, elapsed), gps(21.0, 105.0), profile)
            fail("Bấm trùng phải bị chặn")
        } catch (e: RecordRejected) {}
        assertEquals(2, db.eventDao().all().size)
    }

    @Test fun manualLocationIsFlagged() = runBlocking<Unit> {
        clock += 60_000; elapsed += 60_000
        val e = repo.record(EventType.START_TRIP, TapMoment(clock, elapsed), CapturedLocation.Manual("Kho Long Biên"), profile)
        assertEquals(LocationSource.MANUAL.name, e.locationSource)
        assertEquals(null, e.latitude)
        assertEquals("Kho Long Biên", e.address)
    }

    @Test fun clockDriftIsWarned() = runBlocking<Unit> {
        clock += 60_000; elapsed += 60_000
        // Giờ vị trí chậm hơn giờ máy 10 phút → phải có cảnh báo
        val fix = Fix(21.0, 105.8, 5f, clock - 10 * 60_000, elapsed, false)
        val e = repo.record(EventType.START_TRIP, TapMoment(clock, elapsed), CapturedLocation.Gps(fix, LocationSource.GPS_FRESH, 0), profile)
        assertNotNull(e.clockWarning)
    }

    @Test fun editsAreLoggedAndOriginalsKept() = runBlocking<Unit> {
        val e = rec(EventType.START_TRIP, 1)
        repo.correctAddress(e.id, "Số 1 Tràng Tiền, Hoàn Kiếm, Hà Nội", "GPS lệch")
        val after = db.eventDao().getById(e.id)!!
        assertEquals(e.latitude, after.latitude)
        assertEquals(e.eventTimestamp, after.eventTimestamp)
        assertEquals(1, db.editLogDao().all().size)
        assertEquals("address", db.editLogDao().all()[0].fieldName)
    }

    @Test fun dataSurvivesReopen() = runBlocking<Unit> {
        val name = "persist-test.db"
        ctx.deleteDatabase(name)
        val d1 = Room.databaseBuilder(ctx, AppDatabase::class.java, name).build()
        val r1 = TripRepository(d1)
        clock += 60_000; elapsed += 60_000
        r1.record(EventType.START_TRIP, TapMoment(clock, elapsed), gps(21.0, 105.0), profile)
        d1.close()
        val d2 = Room.databaseBuilder(ctx, AppDatabase::class.java, name).build()
        assertEquals(1, d2.eventDao().all().size)
        assertEquals(TripStatus.ACTIVE.name, d2.tripDao().getActive()?.status)
        d2.close(); ctx.deleteDatabase(name)
    }

    @Test fun staleTripMarkedIncompleteWithoutFakeEnd() = runBlocking<Unit> {
        rec(EventType.START_TRIP, 1)
        val t = db.tripDao().getActive()!!
        repo.markIncomplete(t.tripId)
        val after = db.tripDao().getById(t.tripId)!!
        assertEquals(TripStatus.INCOMPLETE.name, after.status)
        assertEquals(null, after.endTime)
        assertEquals(TripPhase.NOT_STARTED, repo.currentPhase())
    }

    @Test fun backupRoundTripIsIdempotent() = runBlocking<Unit> {
        rec(EventType.START_TRIP, 1); rec(EventType.ARRIVE, 10); rec(EventType.END_TRIP, 10)
        val out = ByteArrayOutputStream()
        BackupManager(db).exportJson(out)
        val other = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        val r1 = BackupManager(other).importJson(ByteArrayInputStream(out.toByteArray()))
        assertEquals(1, r1.trips); assertEquals(3, r1.events)
        val r2 = BackupManager(other).importJson(ByteArrayInputStream(out.toByteArray()))
        assertEquals(0, r2.trips); assertEquals(0, r2.events)
        assertEquals(3, other.eventDao().all().size)
        other.close()
    }

    @Test fun reportRendersSavesAndPaginates() = runBlocking<Unit> {
        // Ngày nhiều sự kiện: 1 chuyến, 25 điểm đến, mỗi điểm đỗ xe
        rec(EventType.START_TRIP, 1, 21.0285, 105.8542)
        for (i in 1..25) {
            val lat = 21.0285 + i * 0.004; val lng = 105.8542 + (if (i % 2 == 0) 0.006 else -0.004) * (i % 5)
            rec(EventType.ARRIVE, 10, lat, lng)
            rec(EventType.PARK_START, 2, lat, lng)
            rec(EventType.PARK_END, 7, lat, lng)
            rec(EventType.RESUME, 1, lat, lng)
        }
        rec(EventType.END_TRIP, 5)
        db.eventDao().all().forEachIndexed { i, e ->
            db.eventDao().fillAddress(e.id, "Số ${i + 1} phố Tràng Tiền, phường Tràng Tiền, quận Hoàn Kiếm, thành phố Hà Nội, Việt Nam", "RESOLVED", clock)
        }
        val service = ReportService(ctx, repo, MapRenderer(ctx))
        val settings = AppSettings(profile.driverName, profile.vehiclePlate, profile.companyName!!, true)
        val model = service.buildModel(LocalDate.now(), settings)
        assertEquals(102, model.eventCount)
        val pages = service.render(model, allowNetwork = true)
        assertTrue(pages.isNotEmpty())
        pages.forEach {
            assertEquals(ReportRenderer.WIDTH, it.width)
            assertTrue(it.height in ReportRenderer.MIN_HEIGHT..ReportRenderer.MAX_PAGE_HEIGHT + 400)
        }
        dumpForCi("report_many", pages)

        val saved = service.save(LocalDate.now(), pages)
        assertEquals(pages.size, saved.uris.size)
        saved.uris.forEach { uri -> ctx.contentResolver.openInputStream(uri).use { assertNotNull(it); assertTrue(it!!.read() >= 0) } }
        // Xuất lại không được tạo file trùng tên
        val again = service.save(LocalDate.now(), pages)
        assertEquals(saved.fileNames, again.fileNames)
    }

    @Test fun reportSmallDayOfflineSchematic() = runBlocking<Unit> {
        rec(EventType.START_TRIP, 30, 21.0285, 105.8542)
        rec(EventType.ARRIVE, 75, 21.0453, 105.7880, note = "Giao hàng cho kho XYZ")
        rec(EventType.PARK_START, 5, 21.0455, 105.7882)
        rec(EventType.PARK_END, 45, 21.0455, 105.7882)
        rec(EventType.RESUME, 5, 21.0455, 105.7882)
        clock += 30 * 60_000; elapsed += 30 * 60_000
        repo.record(EventType.QUICK_LOG, TapMoment(clock, elapsed), CapturedLocation.Manual("Ngân hàng Vietcombank chi nhánh Cầu Giấy"), profile, "Ghé ngân hàng")
        rec(EventType.ARRIVE, 50, 20.9990, 105.8010)
        rec(EventType.END_TRIP, 20, 20.9990, 105.8010)
        db.eventDao().all().filter { it.latitude != null }.forEachIndexed { i, e ->
            db.eventDao().fillAddress(e.id, if (i == 1) null else "Địa chỉ kiểm thử số ${i + 1}, Hà Nội", if (i == 1) "NOT_FOUND" else "RESOLVED", clock)
        }
        val service = ReportService(ctx, repo, MapRenderer(ctx).also { it.clearCache() })
        val model = service.buildModel(LocalDate.now(), AppSettings(profile.driverName, profile.vehiclePlate, "", true))
        val offline = service.render(model, allowNetwork = false)
        assertEquals(1, offline.size)
        dumpForCi("report_small_offline", offline)
        val online = service.render(model, allowNetwork = true)
        dumpForCi("report_small_online", online)
    }

    @Test fun locationServiceNeverCrashes() = runBlocking<Unit> {
        val r = LocationService(ctx).getFreshFix(timeoutMs = 15_000)
        Log.i("VigoTest", "Kết quả GPS trên emulator: $r")
        when (r) {
            is FixResult.Success -> assertTrue(r.fix.ageMs(SystemClock.elapsedRealtime()) <= LocationService.FRESH_MAX_AGE_MS)
            is FixResult.Failure -> assertNotNull(r.reason)
        }
    }

    /** Lưu ảnh vào Pictures/VIGO-CI (vẫn còn sau khi gỡ app test) để CI lấy ra kiểm tra bằng mắt. */
    private fun dumpForCi(name: String, pages: List<Bitmap>) {
        val resolver = ctx.contentResolver
        pages.forEachIndexed { i, b ->
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "${name}_$i.png")
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VIGO-CI/")
            }
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@forEachIndexed
            resolver.openOutputStream(uri)?.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
