package com.vigo.driverlog.data.backup

import androidx.room.withTransaction
import com.vigo.driverlog.data.db.AppDatabase
import com.vigo.driverlog.data.db.EditLogEntity
import com.vigo.driverlog.data.db.EventEntity
import com.vigo.driverlog.data.db.TripEntity
import com.vigo.driverlog.domain.EventType
import com.vigo.driverlog.domain.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

data class ImportResult(val trips: Int, val events: Int, val logs: Int, val skipped: Int)

class BackupException(message: String) : Exception(message)

/**
 * Sao lưu / khôi phục toàn bộ dữ liệu dạng JSON. Khôi phục theo kiểu GỘP:
 * bản ghi đã tồn tại (cùng id) được giữ nguyên, không ghi đè và không tạo trùng.
 */
class BackupManager(private val db: AppDatabase) {
    companion object { const val FORMAT = "vigo-driver-log-backup"; const val VERSION = 1 }

    suspend fun exportJson(out: OutputStream) = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("format", FORMAT); root.put("version", VERSION); root.put("exportedAt", System.currentTimeMillis())
        root.put("trips", JSONArray().apply { db.tripDao().all().forEach { put(BackupCodec.trip(it)) } })
        root.put("events", JSONArray().apply { db.eventDao().all().forEach { put(BackupCodec.event(it)) } })
        root.put("editLogs", JSONArray().apply { db.editLogDao().all().forEach { put(BackupCodec.log(it)) } })
        out.writer(Charsets.UTF_8).use { it.write(root.toString(2)) }
    }

    suspend fun importJson(input: InputStream): ImportResult = withContext(Dispatchers.IO) {
        val text = input.reader(Charsets.UTF_8).use { it.readText() }
        val root = try { JSONObject(text) } catch (e: Exception) { throw BackupException("File không đúng định dạng sao lưu của VIGO Driver Log.") }
        if (root.optString("format") != FORMAT) throw BackupException("File không đúng định dạng sao lưu của VIGO Driver Log.")
        if (root.optInt("version") > VERSION) throw BackupException("File sao lưu được tạo từ phiên bản mới hơn. Hãy cập nhật ứng dụng.")
        val trips = root.optJSONArray("trips").objects().map(BackupCodec::parseTrip)
        val events = root.optJSONArray("events").objects().map(BackupCodec::parseEvent)
        val logs = root.optJSONArray("editLogs").objects().map(BackupCodec::parseLog)
        db.withTransaction {
            // Chỉ được có tối đa một chuyến đang diễn ra.
            val activeNow = db.tripDao().getActive()
            val keepActiveId = activeNow?.tripId ?: trips.filter { it.status == "ACTIVE" }.maxByOrNull { it.startTime }?.tripId
            val safeTrips = trips.map { if (it.status == "ACTIVE" && it.tripId != keepActiveId) it.copy(status = "INCOMPLETE") else it }
            val t = db.tripDao().insertIgnore(safeTrips).count { it != -1L }
            val e = db.eventDao().insertIgnore(events).count { it != -1L }
            // Nhật ký: bỏ id cũ để tránh xung đột, chỉ thêm dòng chưa có.
            val existing = db.editLogDao().all().map { Triple(it.eventId, it.fieldName, it.editedAt) }.toHashSet()
            val newLogs = logs.filter { Triple(it.eventId, it.fieldName, it.editedAt) !in existing }.map { it.copy(id = 0) }
            val l = db.editLogDao().insertIgnore(newLogs).count { it != -1L }
            ImportResult(t, e, l, (trips.size - t) + (events.size - e))
        }
    }

    /** Xuất bảng sự kiện dạng CSV (mở được bằng Excel/Google Sheets). */
    suspend fun exportCsv(out: OutputStream) = withContext(Dispatchers.IO) {
        val trips = db.tripDao().all().associateBy { it.tripId }
        out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) // BOM để Excel đọc đúng tiếng Việt
        out.writer(Charsets.UTF_8).buffered().use { w ->
            w.write("Ngày,Giờ,Sự kiện,Lái xe,Biển số,Địa chỉ,Vĩ độ,Kinh độ,Độ chính xác (m),Nguồn vị trí,Ghi chú,Cảnh báo,Mã sự kiện,Mã chuyến\n")
            db.eventDao().all().forEach { e ->
                val t = e.tripId?.let { trips[it] }
                val cells = listOf(
                    Formatters.date(e.eventTimestamp), Formatters.time(e.eventTimestamp, true, seconds = true),
                    EventType.from(e.eventType).label, t?.driverName ?: "", t?.vehiclePlate ?: "",
                    e.address ?: "", e.latitude?.let(Formatters::coord) ?: "", e.longitude?.let(Formatters::coord) ?: "",
                    e.accuracy?.toInt()?.toString() ?: "", e.locationSource, e.note ?: "", e.clockWarning ?: "", e.id, e.tripId ?: "",
                )
                w.write(cells.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" }); w.write("\n")
            }
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
}

/** Mã hóa/giải mã JSON – tách riêng để kiểm thử được trên JVM. */
object BackupCodec {
    private fun JSONObject.optLongOrNull(k: String): Long? = if (has(k) && !isNull(k)) getLong(k) else null
    private fun JSONObject.optDoubleOrNull(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null
    private fun JSONObject.optStringOrNull(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null
    private fun JSONObject.putN(k: String, v: Any?) = put(k, v ?: JSONObject.NULL)

    fun trip(t: TripEntity) = JSONObject().apply {
        put("tripId", t.tripId); put("driverName", t.driverName); put("vehiclePlate", t.vehiclePlate); putN("companyName", t.companyName)
        put("startTime", t.startTime); putN("endTime", t.endTime); put("status", t.status); put("createdAt", t.createdAt); put("updatedAt", t.updatedAt)
    }

    fun parseTrip(o: JSONObject) = TripEntity(
        tripId = o.getString("tripId"), driverName = o.optString("driverName"), vehiclePlate = o.optString("vehiclePlate"),
        companyName = o.optStringOrNull("companyName"), startTime = o.getLong("startTime"), endTime = o.optLongOrNull("endTime"),
        status = o.optString("status", "COMPLETED"), createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt"),
    )

    fun event(e: EventEntity) = JSONObject().apply {
        put("id", e.id); putN("tripId", e.tripId); put("eventType", e.eventType); put("eventTimestamp", e.eventTimestamp)
        put("elapsedRealtimeMs", e.elapsedRealtimeMs); put("timezoneId", e.timezoneId)
        putN("latitude", e.latitude); putN("longitude", e.longitude); putN("accuracy", e.accuracy?.toDouble())
        putN("locationTime", e.locationTime); putN("locationAgeMs", e.locationAgeMs); putN("referenceTime", e.referenceTime)
        put("locationSource", e.locationSource); put("isMockLocation", e.isMockLocation); put("lowAccuracy", e.lowAccuracy)
        putN("address", e.address); put("addressStatus", e.addressStatus); putN("note", e.note); putN("clockWarning", e.clockWarning)
        put("createdAt", e.createdAt); put("updatedAt", e.updatedAt)
    }

    fun parseEvent(o: JSONObject) = EventEntity(
        id = o.getString("id"), tripId = o.optStringOrNull("tripId"), eventType = o.getString("eventType"),
        eventTimestamp = o.getLong("eventTimestamp"), elapsedRealtimeMs = o.optLong("elapsedRealtimeMs"),
        timezoneId = o.optString("timezoneId", "Asia/Ho_Chi_Minh"),
        latitude = o.optDoubleOrNull("latitude"), longitude = o.optDoubleOrNull("longitude"),
        accuracy = o.optDoubleOrNull("accuracy")?.toFloat(), locationTime = o.optLongOrNull("locationTime"),
        locationAgeMs = o.optLongOrNull("locationAgeMs"), referenceTime = o.optLongOrNull("referenceTime"),
        locationSource = o.optString("locationSource", "NONE"), isMockLocation = o.optBoolean("isMockLocation"),
        lowAccuracy = o.optBoolean("lowAccuracy"), address = o.optStringOrNull("address"),
        addressStatus = o.optString("addressStatus", "NONE"), note = o.optStringOrNull("note"),
        clockWarning = o.optStringOrNull("clockWarning"), createdAt = o.optLong("createdAt"), updatedAt = o.optLong("updatedAt"),
    )

    fun log(l: EditLogEntity) = JSONObject().apply {
        put("id", l.id); put("eventId", l.eventId); put("fieldName", l.fieldName); putN("oldValue", l.oldValue)
        putN("newValue", l.newValue); put("editedAt", l.editedAt); putN("reason", l.reason)
    }

    fun parseLog(o: JSONObject) = EditLogEntity(
        id = o.optLong("id"), eventId = o.getString("eventId"), fieldName = o.getString("fieldName"),
        oldValue = o.optStringOrNull("oldValue"), newValue = o.optStringOrNull("newValue"),
        editedAt = o.getLong("editedAt"), reason = o.optStringOrNull("reason"),
    )
}
