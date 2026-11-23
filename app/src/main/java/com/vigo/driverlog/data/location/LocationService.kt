package com.vigo.driverlog.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Một bản tin vị trí đã chuẩn hóa. */
data class Fix(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    /** Giờ do nhà cung cấp vị trí báo (epoch ms). */
    val time: Long,
    /** elapsedRealtime (ms) của bản tin – dùng để tính tuổi chính xác. */
    val elapsedRealtimeMs: Long,
    val isMock: Boolean,
) {
    fun ageMs(nowElapsed: Long = SystemClock.elapsedRealtime()): Long = (nowElapsed - elapsedRealtimeMs).coerceAtLeast(0)
}

enum class FailReason(val message: String) {
    NO_PERMISSION("Ứng dụng chưa được cấp quyền vị trí."),
    LOCATION_DISABLED("Định vị (GPS) trên điện thoại đang tắt."),
    TIMEOUT("Không xác định được vị trí trong thời gian chờ. GPS có thể đang yếu."),
    NO_PLAY_SERVICES("Điện thoại thiếu Google Play Services nên không dùng được định vị hợp nhất."),
    ERROR("Có lỗi khi lấy vị trí."),
}

sealed interface FixResult {
    data class Success(val fix: Fix) : FixResult
    /** [lastKnown]: vị trí cache cũ nhất có thể đưa cho người dùng tự quyết định (không tự dùng). */
    data class Failure(val reason: FailReason, val lastKnown: Fix?) : FixResult
}

data class GpsStatus(
    val permissionGranted: Boolean,
    val locationEnabled: Boolean,
    val lastFix: Fix?,
)

class LocationService(private val context: Context) {

    companion object {
        /** Vị trí cũ hơn ngưỡng này không được coi là "vị trí mới". */
        const val FRESH_MAX_AGE_MS = 30_000L
        /** Trên ngưỡng này hiển thị cảnh báo độ chính xác kém trước khi lưu. */
        const val GOOD_ACCURACY_M = 50f
        const val DEFAULT_TIMEOUT_MS = 25_000L
    }

    private val fused by lazy { LocationServices.getFusedLocationProviderClient(context) }
    private val lm by lazy { context.getSystemService(Context.LOCATION_SERVICE) as LocationManager }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasFinePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isLocationEnabled(): Boolean = runCatching { LocationManagerCompat.isLocationEnabled(lm) }.getOrDefault(false)

    private fun playServicesOk(): Boolean = runCatching {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }.getOrDefault(false)

    /**
     * Yêu cầu một vị trí MỚI độ chính xác cao. Không bao giờ tự thay bằng vị trí cũ:
     * nếu thất bại, vị trí cache (nếu có) chỉ được trả về để người dùng tự quyết định.
     */
    @SuppressLint("MissingPermission")
    suspend fun getFreshFix(timeoutMs: Long = DEFAULT_TIMEOUT_MS): FixResult {
        if (!hasPermission()) return FixResult.Failure(FailReason.NO_PERMISSION, null)
        if (!isLocationEnabled()) return FixResult.Failure(FailReason.LOCATION_DISABLED, lastKnownSafe())
        if (!playServicesOk()) return getFromPlatform(timeoutMs)

        val cts = CancellationTokenSource()
        return try {
            val request = CurrentLocationRequest.Builder()
                .setPriority(if (hasFinePermission()) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setGranularity(Granularity.GRANULARITY_PERMISSION_LEVEL)
                .setMaxUpdateAgeMillis(5_000)
                .setDurationMillis(timeoutMs)
                .build()
            val loc = withTimeoutOrNull(timeoutMs + 3_000) {
                fused.getCurrentLocation(request, cts.token).await()
            }
            val fix = loc?.toFix()
            if (fix != null && fix.ageMs() <= FRESH_MAX_AGE_MS) FixResult.Success(fix)
            else FixResult.Failure(FailReason.TIMEOUT, lastKnownSafe())
        } catch (e: SecurityException) {
            FixResult.Failure(FailReason.NO_PERMISSION, null)
        } catch (e: Exception) {
            FixResult.Failure(FailReason.ERROR, lastKnownSafe())
        } finally {
            cts.cancel()
        }
    }

    /** Dự phòng khi không có Google Play Services: dùng LocationManager của Android. */
    @SuppressLint("MissingPermission")
    private suspend fun getFromPlatform(timeoutMs: Long): FixResult {
        val provider = when {
            hasFinePermission() && lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return FixResult.Failure(FailReason.LOCATION_DISABLED, null)
        }
        return try {
            val loc = withTimeoutOrNull(timeoutMs) {
                kotlinx.coroutines.suspendCancellableCoroutine<Location?> { cont ->
                    val signal = android.os.CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    LocationManagerCompat.getCurrentLocation(lm, provider, signal, ContextCompat.getMainExecutor(context)) { l ->
                        if (cont.isActive) cont.resumeWith(Result.success(l))
                    }
                }
            }
            val fix = loc?.toFix()
            if (fix != null && fix.ageMs() <= FRESH_MAX_AGE_MS) FixResult.Success(fix)
            else FixResult.Failure(FailReason.TIMEOUT, null)
        } catch (e: Exception) {
            FixResult.Failure(FailReason.ERROR, null)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun lastKnownSafe(): Fix? = try {
        if (!hasPermission() || !playServicesOk()) null
        else withTimeoutOrNull(3_000) { fused.lastLocation.await() }?.toFix()
    } catch (e: Exception) {
        null
    }

    /**
     * Theo dõi trạng thái GPS khi màn hình chính đang hiển thị (chỉ chạy foreground,
     * tự dừng khi người dùng rời màn hình). Dùng để hiển thị, KHÔNG dùng để ghi sự kiện.
     */
    @SuppressLint("MissingPermission")
    fun statusUpdates(): Flow<GpsStatus> = callbackFlow {
        var last: Fix? = null
        fun emitStatus(fix: Fix?) {
            if (fix != null) last = fix
            trySend(GpsStatus(hasPermission(), isLocationEnabled(), fix ?: last?.takeIf { isLocationEnabled() }))
        }
        emitStatus(null)
        // Cập nhật lại trạng thái bật/tắt định vị định kỳ (người dùng có thể tắt GPS bất cứ lúc nào).
        val ticker = launch { while (isActive) { delay(5_000); emitStatus(null) } }
        var callback: LocationCallback? = null
        if (hasPermission() && playServicesOk()) {
            val cb = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    emitStatus(result.lastLocation?.toFix())
                }
                override fun onLocationAvailability(availability: com.google.android.gms.location.LocationAvailability) {
                    if (!availability.isLocationAvailable) trySend(GpsStatus(hasPermission(), isLocationEnabled(), null))
                }
            }
            callback = cb
            try {
                val req = LocationRequest.Builder(
                    if (hasFinePermission()) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    5_000
                ).setMinUpdateIntervalMillis(3_000).build()
                fused.requestLocationUpdates(req, cb, Looper.getMainLooper())
            } catch (e: Exception) {
                emitStatus(null)
            }
        }
        awaitClose {
            ticker.cancel()
            callback?.let { runCatching { fused.removeLocationUpdates(it) } }
        }
    }
}

fun Location.toFix(): Fix = Fix(
    latitude = latitude,
    longitude = longitude,
    accuracy = if (hasAccuracy()) accuracy else 9999f,
    time = time,
    elapsedRealtimeMs = elapsedRealtimeNanos / 1_000_000,
    isMock = mockFlag(this),
)

@Suppress("DEPRECATION")
private fun mockFlag(l: Location): Boolean = if (Build.VERSION.SDK_INT >= 31) l.isMock else l.isFromMockProvider
