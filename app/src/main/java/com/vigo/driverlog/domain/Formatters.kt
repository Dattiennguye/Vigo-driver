package com.vigo.driverlog.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object Formatters {
    private val vi = Locale("vi", "VN")
    private val date = DateTimeFormatter.ofPattern("dd/MM/yyyy", vi)
    private val time24 = DateTimeFormatter.ofPattern("HH:mm", vi)
    private val time24s = DateTimeFormatter.ofPattern("HH:mm:ss", vi)
    private val time12 = DateTimeFormatter.ofPattern("hh:mm a", vi)
    private val time12s = DateTimeFormatter.ofPattern("hh:mm:ss a", vi)
    private val dateTime = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", vi)
    private val fileDate = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
    private val weekday = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", vi)

    fun date(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String = date.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun date(d: LocalDate): String = date.format(d)
    fun weekdayDate(d: LocalDate): String = weekday.format(d).replaceFirstChar { it.uppercase(vi) }
    fun fileDate(d: LocalDate): String = fileDate.format(d)

    fun time(ms: Long, use24h: Boolean = true, seconds: Boolean = false, zone: ZoneId = ZoneId.systemDefault()): String {
        val f = when {
            use24h && seconds -> time24s
            use24h -> time24
            seconds -> time12s
            else -> time12
        }
        return f.format(Instant.ofEpochMilli(ms).atZone(zone))
    }

    fun dateTime(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String = dateTime.format(Instant.ofEpochMilli(ms).atZone(zone))

    /** 15 phút · 1 giờ 20 phút · 2 giờ 05 phút. Dưới 1 phút hiển thị "Dưới 1 phút". */
    fun duration(ms: Long): String {
        if (ms < 0) return "Không xác định"
        val totalMin = ms / 60_000
        if (totalMin < 1) return "Dưới 1 phút"
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h == 0L) "$m phút" else "$h giờ ${m.toString().padStart(2, '0')} phút"
    }

    fun coord(v: Double): String = String.format(Locale.US, "%.6f", v)
    fun coords(lat: Double, lng: Double): String = "${coord(lat)}, ${coord(lng)}"
    fun accuracy(m: Float): String = "±${m.toInt()} m"

    fun distance(meters: Double, unit: DistanceUnit): String = when (unit) {
        DistanceUnit.KM -> if (meters < 1000) "${meters.toInt()} m" else String.format(vi, "%.1f km", meters / 1000)
        DistanceUnit.MILE -> String.format(vi, "%.1f dặm", meters / 1609.344)
    }

    fun minutesText(ms: Long): String {
        val min = abs(ms) / 60_000
        return if (min < 60) "$min phút" else duration(abs(ms))
    }
}

enum class DistanceUnit(val label: String) { KM("Kilômét (km)"), MILE("Dặm (mile)");
    companion object { fun from(v: String?) = entries.firstOrNull { it.name == v } ?: KM }
}
