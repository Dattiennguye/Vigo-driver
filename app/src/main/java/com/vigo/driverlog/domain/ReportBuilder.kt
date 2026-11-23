package com.vigo.driverlog.domain

import com.vigo.driverlog.data.db.EditLogEntity
import com.vigo.driverlog.data.db.EventEntity
import com.vigo.driverlog.data.db.TripEntity
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class ReportMetric(val label: String, val value: String)

data class ReportItem(
    val event: EventEntity,
    val type: EventType,
    /** Số thứ tự điểm trên bản đồ (null nếu không có tọa độ). */
    val mapIndex: Int?,
    val parkingDurationMs: Long?,
    val edits: List<EditLogEntity>,
)

data class ReportTripSection(
    val trip: TripEntity?,
    val title: String,
    val subtitle: String?,
    val items: List<ReportItem>,
)

data class MapPoint(val index: Int, val lat: Double, val lng: Double, val type: EventType)

data class ReportModel(
    val day: LocalDate,
    val driverName: String,
    val vehiclePlate: String,
    val companyName: String?,
    val metrics: List<ReportMetric>,
    val sections: List<ReportTripSection>,
    val mapPoints: List<MapPoint>,
    val notices: List<String>,
    val use24h: Boolean,
    val eventCount: Int,
) {
    val isEmpty get() = eventCount == 0
}

/**
 * Dựng nội dung báo cáo HOÀN TOÀN từ dữ liệu đã ghi nhận. Không có dữ liệu thì không có chỉ số –
 * không bao giờ thêm dữ liệu mẫu.
 */
object ReportBuilder {

    fun build(
        day: LocalDate,
        zone: ZoneId,
        trips: List<TripEntity>,
        dayEvents: List<EventEntity>,
        tripEventsById: Map<String, List<EventEntity>>,
        editLogs: List<EditLogEntity>,
        fallbackDriver: String,
        fallbackPlate: String,
        fallbackCompany: String?,
        use24h: Boolean,
        distanceUnit: DistanceUnit,
    ): ReportModel {
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val sorted = dayEvents.sortedWith(compareBy({ it.eventTimestamp }, { it.createdAt }))
        val logsByEvent = editLogs.groupBy { it.eventId }

        // Số thứ tự điểm trên bản đồ theo thời gian
        var idx = 0
        val mapIndex = HashMap<String, Int>()
        val mapPoints = mutableListOf<MapPoint>()
        for (e in sorted) {
            val lat = e.latitude; val lng = e.longitude
            if (lat != null && lng != null) {
                idx++
                mapIndex[e.id] = idx
                mapPoints += MapPoint(idx, lat, lng, EventType.from(e.eventType))
            }
        }

        // Thời gian đỗ: ghép PARK_START với PARK_END kế tiếp trong cùng chuyến (có thể bắt đầu từ hôm trước).
        val parkDuration = HashMap<String, Long>()
        val parkingPairs = mutableListOf<Pair<EventEntity, EventEntity>>()
        for ((_, evs) in tripEventsById) {
            var open: EventEntity? = null
            for (e in evs.sortedWith(compareBy({ it.eventTimestamp }, { it.createdAt }))) {
                when (EventType.from(e.eventType)) {
                    EventType.PARK_START -> open = e
                    EventType.PARK_END -> open?.let { s ->
                        parkDuration[e.id] = e.eventTimestamp - s.eventTimestamp
                        parkingPairs += s to e
                        open = null
                    }
                    else -> {}
                }
            }
        }

        val tripById = trips.associateBy { it.tripId }
        val groups = sorted.groupBy { it.tripId }
        val orderedKeys = groups.keys.sortedBy { k -> groups[k]!!.first().eventTimestamp }
        var tripNo = 0
        val sections = orderedKeys.map { key ->
            val trip = key?.let { tripById[it] }
            val items = groups[key]!!.map { e ->
                ReportItem(e, EventType.from(e.eventType), mapIndex[e.id], parkDuration[e.id], logsByEvent[e.id].orEmpty())
            }
            if (key == null) {
                ReportTripSection(null, "Ghi nhận ngoài chuyến đi", null, items)
            } else {
                tripNo++
                val start = trip?.startTime
                val end = trip?.endTime
                val range = buildString {
                    if (start != null) {
                        append(Formatters.time(start, use24h, zone = zone))
                        if (start < dayStart) append(" (" + Formatters.date(start, zone) + ")")
                    }
                    append(" – ")
                    if (end != null) {
                        append(Formatters.time(end, use24h, zone = zone))
                        if (end >= dayEnd) append(" (" + Formatters.date(end, zone) + ")")
                    } else when (trip?.status?.let { TripStatus.from(it) }) {
                        TripStatus.INCOMPLETE -> append("chưa có sự kiện kết thúc")
                        else -> append("đang diễn ra")
                    }
                }
                val sub = trip?.let { t ->
                    if (t.vehiclePlate != fallbackPlate || t.driverName != fallbackDriver) "Lái xe: ${t.driverName} · Biển số: ${t.vehiclePlate}" else null
                }
                ReportTripSection(trip, "CHUYẾN ĐI $tripNo  ($range)", sub, items)
            }
        }

        // ---- Chỉ số tổng quan: chỉ hiển thị chỉ số có dữ liệu thật ----
        val metrics = mutableListOf<ReportMetric>()
        val starts = sorted.filter { it.eventType == EventType.START_TRIP.name }
        val ends = sorted.filter { it.eventType == EventType.END_TRIP.name }
        starts.firstOrNull()?.let { metrics += ReportMetric("Giờ bắt đầu", Formatters.time(it.eventTimestamp, use24h, zone = zone)) }
        ends.lastOrNull()?.let { metrics += ReportMetric("Giờ kết thúc", Formatters.time(it.eventTimestamp, use24h, zone = zone)) }
        val arrivals = sorted.count { it.eventType == EventType.ARRIVE.name }
        if (arrivals > 0) metrics += ReportMetric("Tổng số địa điểm đến", "$arrivals")
        val parks = sorted.count { it.eventType == EventType.PARK_START.name }
        if (parks > 0) metrics += ReportMetric("Tổng số lần đỗ xe", "$parks")
        val parkTotal = parkingPairs.filter { it.second.eventTimestamp in dayStart until dayEnd }
            .sumOf { (s, e) -> e.eventTimestamp - s.eventTimestamp }
        if (parkingPairs.any { it.second.eventTimestamp in dayStart until dayEnd }) {
            metrics += ReportMetric("Tổng thời gian đỗ", Formatters.duration(parkTotal))
        }
        val finishedTrips = trips.filter { it.endTime != null && groups.containsKey(it.tripId) }
        if (finishedTrips.isNotEmpty()) {
            val work = finishedTrips.sumOf { it.endTime!! - it.startTime }
            metrics += ReportMetric(
                if (finishedTrips.size > 1) "Tổng thời gian làm việc (${finishedTrips.size} chuyến)" else "Tổng thời gian làm việc trong chuyến",
                Formatters.duration(work)
            )
        }
        val quick = sorted.count { it.eventType == EventType.QUICK_LOG.name }
        if (quick > 0) metrics += ReportMetric("Ghi nhận nhanh", "$quick")
        if (mapPoints.size >= 2) {
            val meters = mapPoints.zipWithNext().sumOf { (a, b) -> haversine(a.lat, a.lng, b.lat, b.lng) }
            metrics += ReportMetric("Khoảng cách nối các điểm (đường thẳng)", Formatters.distance(meters, distanceUnit))
        }

        val notices = mutableListOf<String>()
        trips.filter { groups.containsKey(it.tripId) && it.status == TripStatus.INCOMPLETE.name }.forEach {
            notices += "Chuyến đi bắt đầu lúc ${Formatters.time(it.startTime, use24h, zone = zone)} ngày ${Formatters.date(it.startTime, zone)} không có sự kiện kết thúc."
        }
        trips.filter { groups.containsKey(it.tripId) && it.status == TripStatus.ACTIVE.name }.forEach {
            notices += "Chuyến đi bắt đầu lúc ${Formatters.time(it.startTime, use24h, zone = zone)} chưa kết thúc tại thời điểm xuất báo cáo."
        }
        if (sorted.any { it.locationSource == LocationSource.MANUAL.name }) notices += "Một số địa điểm do người dùng nhập thủ công (không có tọa độ GPS)."
        if (sorted.any { it.locationSource == LocationSource.GPS_CACHED.name }) notices += "Một số sự kiện dùng vị trí gần nhất (cache) do không lấy được GPS mới – đã ghi rõ tuổi vị trí."

        val firstTrip = sections.firstNotNullOfOrNull { it.trip }
        return ReportModel(
            day = day,
            driverName = firstTrip?.driverName ?: fallbackDriver,
            vehiclePlate = firstTrip?.vehiclePlate ?: fallbackPlate,
            companyName = (firstTrip?.companyName ?: fallbackCompany)?.ifBlank { null },
            metrics = metrics,
            sections = sections,
            mapPoints = mapPoints,
            notices = notices,
            use24h = use24h,
            eventCount = sorted.size,
        )
    }

    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(sqrt(a))
    }
}
