package com.vigo.driverlog.data

import androidx.room.withTransaction
import com.vigo.driverlog.data.db.AppDatabase
import com.vigo.driverlog.data.db.EditLogEntity
import com.vigo.driverlog.data.db.EventEntity
import com.vigo.driverlog.data.db.TripEntity
import com.vigo.driverlog.data.location.Fix
import com.vigo.driverlog.data.location.LocationService
import com.vigo.driverlog.domain.AddressStatus
import com.vigo.driverlog.domain.EventType
import com.vigo.driverlog.domain.Formatters
import com.vigo.driverlog.domain.LocationSource
import com.vigo.driverlog.domain.TripPhase
import com.vigo.driverlog.domain.TripStateMachine
import com.vigo.driverlog.domain.TripStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import java.util.TimeZone
import java.util.UUID
import kotlin.math.abs

/** Thời điểm người dùng nhấn nút – chụp lại NGAY khi nhấn, trước khi chờ GPS. */
data class TapMoment(val wallClockMs: Long, val elapsedRealtimeMs: Long)

/** Vị trí người dùng đã chấp nhận để lưu cho sự kiện. */
sealed interface CapturedLocation {
    data class Gps(val fix: Fix, val source: LocationSource, val ageAtTapMs: Long) : CapturedLocation
    data class Manual(val placeText: String) : CapturedLocation
}

class RecordRejected(message: String) : Exception(message)

data class TripProfile(val driverName: String, val vehiclePlate: String, val companyName: String?)

class TripRepository(private val db: AppDatabase) {
    private val trips = db.tripDao()
    private val events = db.eventDao()
    private val logs = db.editLogDao()

    companion object {
        /** Chống bấm trùng: cùng loại sự kiện trong khoảng này bị bỏ qua. */
        const val DUPLICATE_WINDOW_MS = 3_000L
        /** Lệch đồng hồ lớn hơn ngưỡng này sẽ được ghi cảnh báo. */
        const val CLOCK_DRIFT_WARN_MS = 120_000L
    }

    val activeTrip: Flow<TripEntity?> = trips.observeActive()
    val lastFinishedTrip: Flow<TripEntity?> = trips.observeLastFinished()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeTripEvents: Flow<List<EventEntity>> = trips.observeActive().flatMapLatest { t ->
        if (t == null) flowOf(emptyList()) else events.observeForTrip(t.tripId)
    }

    fun eventsBetween(start: Long, end: Long) = events.observeBetween(start, end)
    fun tripsOverlapping(start: Long, end: Long) = trips.observeOverlapping(start, end)
    fun observeEvent(id: String) = events.observeById(id)
    fun observeEditLogs(eventId: String) = logs.observeForEvent(eventId)
    val pendingAddressCount = events.observePendingAddressCount()
    val eventCount = events.observeCount()
    val tripCount = trips.observeCount()

    suspend fun currentPhase(): TripPhase {
        val t = trips.getActive() ?: return TripPhase.NOT_STARTED
        val last = events.lastTripEvent(t.tripId)?.let { EventType.from(it.eventType) }
        return TripStateMachine.phaseAfter(last, true)
    }

    /**
     * Ghi một sự kiện. Kiểm tra thứ tự hợp lệ và chống ghi trùng bên trong transaction,
     * nên kể cả khi giao diện bị bấm liên tục cũng không tạo chuỗi sự kiện vô lý.
     */
    suspend fun record(
        type: EventType,
        tap: TapMoment,
        location: CapturedLocation,
        profile: TripProfile,
        note: String? = null,
    ): EventEntity = db.withTransaction {
        val now = System.currentTimeMillis()
        val active = trips.getActive()
        val lastTrip = active?.let { events.lastTripEvent(it.tripId) }
        val phase = TripStateMachine.phaseAfter(lastTrip?.let { EventType.from(it.eventType) }, active != null)

        if (!TripStateMachine.isAllowed(phase, type)) throw RecordRejected(TripStateMachine.rejectionMessage(phase, type))

        // Chống bấm trùng (đo bằng elapsedRealtime nên không bị ảnh hưởng bởi đồng hồ).
        val previous = events.lastEvent()
        if (previous != null && previous.eventType == type.name &&
            abs(tap.elapsedRealtimeMs - previous.elapsedRealtimeMs) < DUPLICATE_WINDOW_MS
        ) throw RecordRejected("Sự kiện \"${type.label}\" vừa được ghi nhận. Đã bỏ qua lần bấm trùng.")

        val tripId: String? = when (type) {
            EventType.START_TRIP -> {
                val id = UUID.randomUUID().toString()
                trips.insert(
                    TripEntity(
                        tripId = id, driverName = profile.driverName, vehiclePlate = profile.vehiclePlate,
                        companyName = profile.companyName, startTime = tap.wallClockMs, endTime = null,
                        status = TripStatus.ACTIVE.name, createdAt = now, updatedAt = now,
                    )
                )
                id
            }
            else -> active?.tripId
        }

        // Cảnh báo đồng hồ: lùi giờ so với sự kiện trước, hoặc lệch so với giờ của vị trí.
        val warnings = mutableListOf<String>()
        if (previous != null && tap.wallClockMs < previous.eventTimestamp &&
            tap.elapsedRealtimeMs > previous.elapsedRealtimeMs
        ) warnings += "Giờ thiết bị sớm hơn sự kiện trước – đồng hồ điện thoại có thể đã bị chỉnh."

        val event = when (location) {
            is CapturedLocation.Gps -> {
                val f = location.fix
                // Giờ tham chiếu lúc nhấn = giờ của bản tin vị trí + thời gian trôi từ bản tin tới lúc nhấn.
                val reference = f.time + (tap.elapsedRealtimeMs - f.elapsedRealtimeMs)
                val drift = tap.wallClockMs - reference
                if (location.source == LocationSource.GPS_FRESH && abs(drift) > CLOCK_DRIFT_WARN_MS) {
                    warnings += "Đồng hồ thiết bị lệch khoảng ${Formatters.minutesText(drift)} so với giờ vị trí."
                }
                EventEntity(
                    id = UUID.randomUUID().toString(), tripId = tripId, eventType = type.name,
                    eventTimestamp = tap.wallClockMs, elapsedRealtimeMs = tap.elapsedRealtimeMs,
                    timezoneId = TimeZone.getDefault().id,
                    latitude = f.latitude, longitude = f.longitude, accuracy = f.accuracy,
                    locationTime = f.time, locationAgeMs = location.ageAtTapMs, referenceTime = reference,
                    locationSource = location.source.name, isMockLocation = f.isMock,
                    lowAccuracy = f.accuracy > LocationService.GOOD_ACCURACY_M,
                    address = null, addressStatus = AddressStatus.PENDING.name,
                    note = note?.trim()?.ifBlank { null },
                    clockWarning = warnings.joinToString(" ").ifBlank { null },
                    createdAt = now, updatedAt = now,
                )
            }
            is CapturedLocation.Manual -> EventEntity(
                id = UUID.randomUUID().toString(), tripId = tripId, eventType = type.name,
                eventTimestamp = tap.wallClockMs, elapsedRealtimeMs = tap.elapsedRealtimeMs,
                timezoneId = TimeZone.getDefault().id,
                latitude = null, longitude = null, accuracy = null,
                locationTime = null, locationAgeMs = null, referenceTime = null,
                locationSource = LocationSource.MANUAL.name, isMockLocation = false, lowAccuracy = false,
                address = location.placeText.trim(), addressStatus = AddressStatus.MANUAL.name,
                note = note?.trim()?.ifBlank { null },
                clockWarning = warnings.joinToString(" ").ifBlank { null },
                createdAt = now, updatedAt = now,
            )
        }
        events.insert(event)

        if (type == EventType.END_TRIP && active != null) {
            trips.update(active.copy(endTime = tap.wallClockMs, status = TripStatus.COMPLETED.name, updatedAt = now))
        }
        event
    }

    /** Chuyến đi ngày cũ chưa kết thúc: đánh dấu "chưa hoàn thành", KHÔNG tạo sự kiện kết thúc giả. */
    suspend fun markIncomplete(tripId: String): Unit = db.withTransaction {
        val t = trips.getById(tripId) ?: return@withTransaction
        if (t.status != TripStatus.ACTIVE.name) return@withTransaction
        val now = System.currentTimeMillis()
        trips.update(t.copy(status = TripStatus.INCOMPLETE.name, updatedAt = now))
        logs.insert(EditLogEntity(eventId = "trip:$tripId", fieldName = "trip.status", oldValue = t.status,
            newValue = TripStatus.INCOMPLETE.name, editedAt = now, reason = "Chuyến đi không có sự kiện kết thúc"))
        Unit
    }

    suspend fun updateNote(eventId: String, newNote: String): Unit = db.withTransaction {
        val e = events.getById(eventId) ?: return@withTransaction
        val cleaned = newNote.trim().ifBlank { null }
        if (cleaned == e.note) return@withTransaction
        val now = System.currentTimeMillis()
        events.update(e.copy(note = cleaned, updatedAt = now))
        // Bổ sung ghi chú lần đầu ngay sau khi ghi nhận không tính là chỉnh sửa; mọi thay đổi khác đều được lưu vết.
        val isInitialNote = e.note == null && now - e.createdAt in 0..15 * 60_000L
        if (!isInitialNote) logs.insert(EditLogEntity(eventId = eventId, fieldName = "note", oldValue = e.note, newValue = cleaned, editedAt = now, reason = null))
        Unit
    }

    /** Sửa địa chỉ hiển thị. Tọa độ gốc giữ nguyên; lưu giá trị cũ, mới và thời điểm sửa. */
    suspend fun correctAddress(eventId: String, newAddress: String, reason: String?): Unit = db.withTransaction {
        val e = events.getById(eventId) ?: return@withTransaction
        val cleaned = newAddress.trim()
        if (cleaned.isEmpty() || cleaned == e.address) return@withTransaction
        val now = System.currentTimeMillis()
        events.update(e.copy(address = cleaned, addressStatus = AddressStatus.CORRECTED.name, updatedAt = now))
        logs.insert(EditLogEntity(eventId = eventId, fieldName = "address", oldValue = e.address, newValue = cleaned,
            editedAt = now, reason = reason?.trim()?.ifBlank { null }))
        Unit
    }

    /** Cho phép thử chuyển đổi lại địa chỉ cho bản ghi chưa xác định được. */
    suspend fun requeueAddress(eventId: String) {
        val e = events.getById(eventId) ?: return
        if (e.latitude != null && e.addressStatus == AddressStatus.NOT_FOUND.name) {
            events.update(e.copy(addressStatus = AddressStatus.PENDING.name, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun eventsSnapshot(start: Long, end: Long) = events.getBetween(start, end)
    suspend fun tripsSnapshot(start: Long, end: Long) = trips.getOverlapping(start, end)
    suspend fun editLogsFor(ids: List<String>) = if (ids.isEmpty()) emptyList() else ids.chunked(500).flatMap { logs.forEvents(it) }
    suspend fun eventsForTrip(tripId: String) = events.getForTrip(tripId)
}
