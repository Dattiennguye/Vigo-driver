package com.vigo.driverlog.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trips",
    indices = [Index("status"), Index("startTime")]
)
data class TripEntity(
    @PrimaryKey val tripId: String,
    val driverName: String,
    val vehiclePlate: String,
    val companyName: String?,
    val startTime: Long,
    val endTime: Long?,
    /** ACTIVE | COMPLETED | INCOMPLETE */
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Một sự kiện ghi nhận. Thời gian [eventTimestamp] và tọa độ gốc không bao giờ bị sửa;
 * mọi chỉnh sửa (ghi chú, địa chỉ) đều được ghi vào [EditLogEntity].
 */
@Entity(
    tableName = "events",
    indices = [Index("tripId"), Index("eventTimestamp"), Index("addressStatus")]
)
data class EventEntity(
    @PrimaryKey val id: String,
    val tripId: String?,
    val eventType: String,
    /** Giờ thiết bị tại thời điểm nhấn nút (epoch ms). */
    val eventTimestamp: Long,
    /** SystemClock.elapsedRealtime() lúc nhấn nút – không bị ảnh hưởng khi chỉnh đồng hồ. */
    val elapsedRealtimeMs: Long,
    val timezoneId: String,
    val latitude: Double?,
    val longitude: Double?,
    /** Độ chính xác (mét). */
    val accuracy: Float?,
    /** Thời gian của bản tin vị trí do nhà cung cấp vị trí báo (epoch ms). */
    val locationTime: Long?,
    /** Tuổi của bản tin vị trí tại thời điểm lấy (ms). */
    val locationAgeMs: Long?,
    /** Ước tính giờ chuẩn lúc nhấn nút, suy ra từ thời gian vị trí (nếu có). */
    val referenceTime: Long?,
    /** GPS_FRESH | GPS_CACHED | MANUAL | NONE */
    val locationSource: String,
    val isMockLocation: Boolean,
    val lowAccuracy: Boolean,
    val address: String?,
    /** RESOLVED | PENDING | NOT_FOUND | MANUAL | CORRECTED | NONE */
    val addressStatus: String,
    val note: String?,
    /** Cảnh báo về đồng hồ thiết bị, ví dụ lệch so với giờ vị trí. */
    val clockWarning: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "edit_logs", indices = [Index("eventId")])
data class EditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: String,
    val fieldName: String,
    val oldValue: String?,
    val newValue: String?,
    val editedAt: Long,
    val reason: String?,
)
