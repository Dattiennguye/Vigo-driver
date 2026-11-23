package com.vigo.driverlog

import com.vigo.driverlog.data.backup.BackupCodec
import com.vigo.driverlog.data.db.EventEntity
import com.vigo.driverlog.data.db.TripEntity
import com.vigo.driverlog.domain.DistanceUnit
import com.vigo.driverlog.domain.EventType
import com.vigo.driverlog.domain.Formatters
import com.vigo.driverlog.domain.ReportBuilder
import com.vigo.driverlog.domain.TripPhase
import com.vigo.driverlog.domain.TripStateMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StateMachineTest {
    @Test fun notStartedOnlyAllowsStart() {
        assertEquals(listOf(EventType.START_TRIP), TripStateMachine.allowedActions(TripPhase.NOT_STARTED))
        assertFalse(TripStateMachine.isAllowed(TripPhase.NOT_STARTED, EventType.ARRIVE))
        assertTrue(TripStateMachine.isAllowed(TripPhase.NOT_STARTED, EventType.QUICK_LOG))
    }

    @Test fun fullSequence() {
        val seq = listOf(EventType.START_TRIP, EventType.ARRIVE, EventType.PARK_START, EventType.PARK_END, EventType.RESUME,
            EventType.ARRIVE, EventType.PARK_START, EventType.PARK_END, EventType.END_TRIP)
        var phase = TripPhase.NOT_STARTED
        var last: EventType? = null
        var active = false
        for (t in seq) {
            assertTrue("$t phải được phép ở $phase", TripStateMachine.isAllowed(phase, t))
            if (t == EventType.START_TRIP) active = true
            if (t == EventType.END_TRIP) active = false
            last = t
            phase = TripStateMachine.phaseAfter(last, active)
        }
        assertEquals(TripPhase.NOT_STARTED, phase)
    }

    @Test fun parkedOnlyAllowsLeave() {
        assertEquals(listOf(EventType.PARK_END), TripStateMachine.allowedActions(TripPhase.PARKED))
        assertFalse(TripStateMachine.isAllowed(TripPhase.PARKED, EventType.END_TRIP))
        assertFalse(TripStateMachine.isAllowed(TripPhase.MOVING, EventType.PARK_END))
        assertFalse(TripStateMachine.isAllowed(TripPhase.MOVING, EventType.START_TRIP))
    }
}

class FormatterTest {
    @Test fun durations() {
        assertEquals("15 phút", Formatters.duration(15 * 60_000L))
        assertEquals("1 giờ 20 phút", Formatters.duration(80 * 60_000L))
        assertEquals("2 giờ 05 phút", Formatters.duration(125 * 60_000L))
        assertEquals("45 phút", Formatters.duration(45 * 60_000L + 59_000))
        assertEquals("Dưới 1 phút", Formatters.duration(30_000))
    }

    @Test fun coords() {
        assertEquals("21.028511, 105.804817", Formatters.coords(21.0285112, 105.8048171))
    }
}

class ReportBuilderTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val day = LocalDate.of(2026, 11, 23)
    private fun at(h: Int, m: Int) = day.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    private fun ev(id: String, type: EventType, t: Long, lat: Double? = 21.0 + id.hashCode() % 100 / 10000.0, trip: String? = "T1") = EventEntity(
        id = id, tripId = trip, eventType = type.name, eventTimestamp = t, elapsedRealtimeMs = t, timezoneId = zone.id,
        latitude = lat, longitude = lat?.let { 105.8 }, accuracy = 8f, locationTime = t, locationAgeMs = 0, referenceTime = t,
        locationSource = if (lat == null) "MANUAL" else "GPS_FRESH", isMockLocation = false, lowAccuracy = false,
        address = "Địa chỉ $id", addressStatus = if (lat == null) "MANUAL" else "RESOLVED", note = null, clockWarning = null,
        createdAt = t, updatedAt = t,
    )

    @Test fun metricsFromRealDataOnly() {
        val trip = TripEntity("T1", "Nguyễn Văn A", "30A-123.45", null, at(8, 0), at(17, 30), "COMPLETED", at(8, 0), at(17, 30))
        val events = listOf(
            ev("1", EventType.START_TRIP, at(8, 0)), ev("2", EventType.ARRIVE, at(9, 15)),
            ev("3", EventType.PARK_START, at(9, 20)), ev("4", EventType.PARK_END, at(10, 5)),
            ev("5", EventType.RESUME, at(10, 10)), ev("6", EventType.ARRIVE, at(11, 30)),
            ev("7", EventType.QUICK_LOG, at(12, 0), lat = null), ev("8", EventType.END_TRIP, at(17, 30)),
        )
        val m = ReportBuilder.build(day, zone, listOf(trip), events, mapOf("T1" to events), emptyList(),
            "Nguyễn Văn A", "30A-123.45", null, true, DistanceUnit.KM)
        val byLabel = m.metrics.associate { it.label to it.value }
        assertEquals("08:00", byLabel["Giờ bắt đầu"])
        assertEquals("17:30", byLabel["Giờ kết thúc"])
        assertEquals("2", byLabel["Tổng số địa điểm đến"])
        assertEquals("1", byLabel["Tổng số lần đỗ xe"])
        assertEquals("45 phút", byLabel["Tổng thời gian đỗ"])
        assertEquals("9 giờ 30 phút", byLabel["Tổng thời gian làm việc trong chuyến"])
        assertEquals(7, m.mapPoints.size) // ghi nhận thủ công không có tọa độ
        assertEquals(8, m.sections.single().items.size)
        assertEquals(45 * 60_000L, m.sections.single().items.first { it.type == EventType.PARK_END }.parkingDurationMs)
    }

    @Test fun emptyDayHasNoMetrics() {
        val m = ReportBuilder.build(day, zone, emptyList(), emptyList(), emptyMap(), emptyList(), "A", "B", null, true, DistanceUnit.KM)
        assertTrue(m.isEmpty); assertTrue(m.metrics.isEmpty())
    }

    @Test fun activeTripHasNoEndMetric() {
        val trip = TripEntity("T1", "A", "B", null, at(8, 0), null, "ACTIVE", at(8, 0), at(8, 0))
        val events = listOf(ev("1", EventType.START_TRIP, at(8, 0)), ev("2", EventType.PARK_START, at(9, 0)))
        val m = ReportBuilder.build(day, zone, listOf(trip), events, mapOf("T1" to events), emptyList(), "A", "B", null, true, DistanceUnit.KM)
        val labels = m.metrics.map { it.label }
        assertFalse(labels.contains("Giờ kết thúc"))
        assertFalse(labels.contains("Tổng thời gian đỗ"))
        assertTrue(m.notices.any { it.contains("chưa kết thúc") })
    }
}

class BackupCodecTest {
    @Test fun roundTripEvent() {
        val e = EventEntity("id1", null, "QUICK_LOG", 1L, 2L, "Asia/Ho_Chi_Minh", null, null, null, null, null, null,
            "MANUAL", false, false, "Kho hàng", "MANUAL", "ghi chú \"có ngoặc\"", null, 3L, 4L)
        val back = BackupCodec.parseEvent(BackupCodec.event(e))
        assertEquals(e, back)
        assertNull(back.latitude)
    }
}
