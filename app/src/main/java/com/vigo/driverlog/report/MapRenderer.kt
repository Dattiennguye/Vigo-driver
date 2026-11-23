package com.vigo.driverlog.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.vigo.driverlog.domain.EventType
import com.vigo.driverlog.domain.MapPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan

data class MapImage(val bitmap: Bitmap, val hasTiles: Boolean)

/**
 * Vẽ sơ đồ các điểm GPS đã ghi nhận. Nền bản đồ dùng ô bản đồ OpenStreetMap (miễn phí, không cần khóa),
 * có lưu cache trên máy. Không tải được → vẽ sơ đồ theo tọa độ, báo cáo không bao giờ bị trống/hỏng.
 */
class MapRenderer(private val context: Context) {
    private val tileDir = File(context.filesDir, "tiles")
    private val tileSize = 256

    suspend fun render(points: List<MapPoint>, width: Int, height: Int, allowNetwork: Boolean): MapImage = withContext(Dispatchers.Default) {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val margin = 70.0

        val zoom = chooseZoom(points, width - 2 * margin, height - 2 * margin)
        val px = points.map { lonToX(it.lng, zoom) to latToY(it.lat, zoom) }
        val minX = px.minOf { it.first }; val maxX = px.maxOf { it.first }
        val minY = px.minOf { it.second }; val maxY = px.maxOf { it.second }
        val left = (minX + maxX) / 2 - width / 2.0
        val top = (minY + maxY) / 2 - height / 2.0

        val tilesOk = drawTiles(c, zoom, left, top, width, height, allowNetwork)
        if (!tilesOk) drawSchematicBackground(c, width, height)

        // Đường nối theo thứ tự thời gian (nét đứt – không phải tuyến đường thực tế)
        val screen = px.map { (x, y) -> (x - left).toFloat() to (y - top).toFloat() }
        if (screen.size >= 2) {
            val path = Path().apply {
                moveTo(screen[0].first, screen[0].second)
                screen.drop(1).forEach { lineTo(it.first, it.second) }
            }
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 9f; color = Color.WHITE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            })
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 5f; color = ReportColors.NAVY
                pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f); strokeCap = Paint.Cap.ROUND
            })
        }

        // Điểm đánh số: điểm trùng vị trí được dời nhẹ để không chồng chữ số.
        val placed = mutableListOf<Pair<Float, Float>>()
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.WHITE }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD; textSize = 24f
        }
        points.forEachIndexed { i, p ->
            var (x, y) = screen[i]
            var tries = 0
            while (placed.any { (ox, oy) -> (ox - x) * (ox - x) + (oy - y) * (oy - y) < 30f * 30f } && tries < 12) {
                val ang = tries * (PI / 3)
                x = screen[i].first + (34f * kotlin.math.cos(ang)).toFloat() * (1 + tries / 6)
                y = screen[i].second + (34f * kotlin.math.sin(ang)).toFloat() * (1 + tries / 6)
                tries++
            }
            placed += x to y
            val r = 20f
            fill.color = ReportColors.forType(p.type)
            c.drawCircle(x, y, r, fill)
            c.drawCircle(x, y, r, stroke)
            text.textSize = if (p.index >= 100) 17f else if (p.index >= 10) 20f else 24f
            c.drawText(p.index.toString(), x, y - (text.descent() + text.ascent()) / 2, text)
        }

        // Ghi chú nguồn bản đồ
        val attr = if (tilesOk) "© OpenStreetMap contributors" else "Sơ đồ theo tọa độ (không có nền bản đồ)"
        val ap = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; color = ReportColors.TEXT }
        val tw = ap.measureText(attr)
        c.drawRect(RectF(width - tw - 24f, height - 34f, width.toFloat(), height.toFloat()), Paint().apply { color = Color.argb(210, 255, 255, 255) })
        c.drawText(attr, width - tw - 12f, height - 10f, ap)

        MapImage(bmp, tilesOk)
    }

    private fun chooseZoom(points: List<MapPoint>, w: Double, h: Double): Int {
        for (z in 17 downTo 2) {
            val xs = points.map { lonToX(it.lng, z) }
            val ys = points.map { latToY(it.lat, z) }
            if (xs.max() - xs.min() <= w && ys.max() - ys.min() <= h) return z
        }
        return 2
    }

    private suspend fun drawTiles(c: Canvas, z: Int, left: Double, top: Double, w: Int, h: Int, allowNetwork: Boolean): Boolean {
        val n = 2.0.pow(z).toInt()
        val tx0 = floor(left / tileSize).toInt(); val tx1 = floor((left + w) / tileSize).toInt()
        val ty0 = floor(top / tileSize).toInt(); val ty1 = floor((top + h) / tileSize).toInt()
        val coords = (tx0..tx1).flatMap { tx -> (ty0..ty1).map { ty -> tx to ty } }
        if (coords.size > 40) return false
        val sem = Semaphore(4)
        val tiles = withTimeoutOrNull(25_000) {
            coroutineScope {
                coords.map { (tx, ty) ->
                    async(Dispatchers.IO) {
                        if (ty < 0 || ty >= n) return@async Triple(tx, ty, null as Bitmap?)
                        val wx = ((tx % n) + n) % n
                        sem.withPermit { Triple(tx, ty, loadTile(z, wx, ty, allowNetwork)) }
                    }
                }.awaitAll()
            }
        } ?: return false
        if (tiles.any { it.third == null && it.second in 0 until n }) return false
        val dst = Rect()
        for ((tx, ty, b) in tiles) {
            if (b == null) continue
            val x = (tx * tileSize - left).toInt(); val y = (ty * tileSize - top).toInt()
            dst.set(x, y, x + tileSize, y + tileSize)
            c.drawBitmap(b, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        // Phủ nhẹ để các điểm nổi bật trên nền bản đồ
        c.drawColor(Color.argb(40, 255, 255, 255))
        return true
    }

    private fun loadTile(z: Int, x: Int, y: Int, allowNetwork: Boolean): Bitmap? {
        val f = File(tileDir, "$z/$x/$y.png")
        if (f.exists() && f.length() > 0) {
            BitmapFactory.decodeFile(f.absolutePath)?.let { return it }
        }
        if (!allowNetwork) return null
        return try {
            val conn = (URL("https://tile.openstreetmap.org/$z/$x/$y.png").openConnection() as HttpURLConnection).apply {
                connectTimeout = 7_000; readTimeout = 8_000
                setRequestProperty("User-Agent", "VIGODriverLog/1.0 (Android; com.vigo.driverlog)")
            }
            try {
                if (conn.responseCode != 200) return null
                val bytes = conn.inputStream.use { it.readBytes() }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
                runCatching { f.parentFile?.mkdirs(); f.writeBytes(bytes) }
                bmp
            } finally { conn.disconnect() }
        } catch (e: Exception) { null }
    }

    private fun drawSchematicBackground(c: Canvas, w: Int, h: Int) {
        c.drawColor(Color.rgb(0xEE, 0xF2, 0xF7))
        val grid = Paint().apply { color = Color.rgb(0xDC, 0xE3, 0xEC); strokeWidth = 2f }
        var x = 0f; while (x < w) { c.drawLine(x, 0f, x, h.toFloat(), grid); x += 60f }
        var y = 0f; while (y < h) { c.drawLine(0f, y, w.toFloat(), y, grid); y += 60f }
    }

    fun clearCache() { tileDir.deleteRecursively() }

    companion object {
        fun lonToX(lon: Double, z: Int): Double = (lon + 180.0) / 360.0 * 256.0 * 2.0.pow(z)
        fun latToY(lat: Double, z: Int): Double {
            val clamped = lat.coerceIn(-85.05112878, 85.05112878)
            val rad = Math.toRadians(clamped)
            return (1.0 - ln(tan(rad) + 1.0 / kotlin.math.cos(rad)) / PI) / 2.0 * 256.0 * 2.0.pow(z)
        }
        @Suppress("unused") fun yToLat(y: Double, z: Int): Double =
            Math.toDegrees(atan(sinh(PI * (1 - 2 * y / (256.0 * 2.0.pow(z))))))
    }
}

object ReportColors {
    val NAVY = Color.rgb(0x14, 0x2B, 0x45)
    val BLUE = Color.rgb(0x28, 0x78, 0xD0)
    val GREEN = Color.rgb(0x1F, 0x9D, 0x68)
    val ORANGE = Color.rgb(0xE9, 0x9A, 0x35)
    val BG = Color.rgb(0xF4, 0xF6, 0xF9)
    val TEXT = Color.rgb(0x18, 0x23, 0x34)
    val MUTED = Color.rgb(0x5B, 0x67, 0x7A)
    val LINE = Color.rgb(0xDD, 0xE3, 0xEA)
    val RED = Color.rgb(0xC6, 0x3D, 0x3D)
    val PURPLE = Color.rgb(0x7A, 0x5A, 0xC8)
    val TEAL = Color.rgb(0x2A, 0x9A, 0xA8)

    fun forType(t: EventType): Int = when (t) {
        EventType.START_TRIP -> GREEN
        EventType.ARRIVE -> BLUE
        EventType.PARK_START -> ORANGE
        EventType.PARK_END -> ORANGE
        EventType.RESUME -> TEAL
        EventType.END_TRIP -> RED
        EventType.QUICK_LOG -> PURPLE
    }
}
