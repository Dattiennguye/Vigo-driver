package com.vigo.driverlog.data.geo

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

sealed interface GeocodeResult {
    data class Found(val address: String) : GeocodeResult
    /** Dịch vụ trả lời nhưng không có địa chỉ cho tọa độ này. */
    data object NotFound : GeocodeResult
    /** Lỗi mạng / dịch vụ – nên thử lại sau. */
    data object Unavailable : GeocodeResult
}

/**
 * Chuyển tọa độ thành địa chỉ bằng Geocoder có sẵn của Android (miễn phí, không cần khóa API).
 * Cần Internet; khi mất mạng trả về [GeocodeResult.Unavailable] và bản ghi giữ trạng thái chờ.
 */
class AddressResolver(private val context: Context) {
    private val locale = Locale("vi", "VN")

    suspend fun resolve(lat: Double, lng: Double): GeocodeResult {
        if (!Geocoder.isPresent()) return GeocodeResult.Unavailable
        val geocoder = Geocoder(context, locale)
        return try {
            val list: List<Address>? = withTimeoutOrNull(15_000) {
                if (Build.VERSION.SDK_INT >= 33) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (cont.isActive) cont.resume(addresses)
                            }
                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        })
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(lat, lng, 1)
                    }
                }
            }
            when {
                list == null -> GeocodeResult.Unavailable
                list.isEmpty() -> GeocodeResult.NotFound
                else -> format(list[0])?.let { GeocodeResult.Found(it) } ?: GeocodeResult.NotFound
            }
        } catch (e: IOException) {
            GeocodeResult.Unavailable
        } catch (e: IllegalArgumentException) {
            GeocodeResult.NotFound
        } catch (e: Exception) {
            GeocodeResult.Unavailable
        }
    }

    private fun format(a: Address): String? {
        val line = (0..a.maxAddressLineIndex).mapNotNull { a.getAddressLine(it) }.joinToString(", ")
        if (line.isNotBlank()) return line
        val parts = listOfNotNull(
            listOfNotNull(a.subThoroughfare, a.thoroughfare).joinToString(" ").ifBlank { null },
            a.subLocality, a.locality, a.subAdminArea, a.adminArea,
        ).distinct()
        return parts.joinToString(", ").ifBlank { null }
    }
}
