package com.vigo.driverlog.report

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.vigo.driverlog.data.TripRepository
import com.vigo.driverlog.data.settings.AppSettings
import com.vigo.driverlog.domain.Formatters
import com.vigo.driverlog.domain.ReportBuilder
import com.vigo.driverlog.domain.ReportModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

class ReportException(message: String) : Exception(message)

data class ExportedReport(val uris: List<Uri>, val fileNames: List<String>, val folder: String)

class ReportService(
    private val context: Context,
    private val repo: TripRepository,
    private val mapRenderer: MapRenderer,
) {
    companion object {
        const val FOLDER = "VIGO Driver Log"
        private const val MIN_FREE_BYTES = 40L * 1024 * 1024
    }

    suspend fun buildModel(day: LocalDate, settings: AppSettings, zone: ZoneId = ZoneId.systemDefault()): ReportModel = withContext(Dispatchers.IO) {
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = repo.eventsSnapshot(start, end)
        val tripIds = events.mapNotNull { it.tripId }.distinct()
        val trips = repo.tripsSnapshot(start, end).associateBy { it.tripId }.toMutableMap()
        val tripEvents = tripIds.associateWith { repo.eventsForTrip(it) }
        val logs = repo.editLogsFor(events.map { it.id })
        ReportBuilder.build(
            day = day, zone = zone, trips = trips.values.toList(), dayEvents = events, tripEventsById = tripEvents,
            editLogs = logs, fallbackDriver = settings.driverName, fallbackPlate = settings.vehiclePlate,
            fallbackCompany = settings.companyName.ifBlank { null }, use24h = settings.use24h, distanceUnit = settings.distanceUnit,
        )
    }

    suspend fun render(model: ReportModel, allowNetwork: Boolean): List<Bitmap> {
        if (model.isEmpty) throw ReportException("Ngày ${Formatters.date(model.day)} không có dữ liệu ghi nhận nào để tạo báo cáo.")
        val map = if (model.mapPoints.isNotEmpty()) {
            runCatching {
                mapRenderer.render(model.mapPoints, ReportRenderer.WIDTH - 2 * ReportRenderer.PAD, ReportRenderer.MAP_HEIGHT, allowNetwork)
            }.getOrNull()
        } else null
        return withContext(Dispatchers.Default) {
            try {
                ReportRenderer().render(model, map, System.currentTimeMillis())
            } catch (e: OutOfMemoryError) {
                throw ReportException("Điện thoại không đủ bộ nhớ để tạo ảnh báo cáo. Hãy đóng bớt ứng dụng khác rồi thử lại.")
            } finally {
                map?.bitmap?.recycle()
            }
        }
    }

    fun fileNames(day: LocalDate, pages: Int): List<String> {
        val base = "VIGO_DriverLog_${Formatters.fileDate(day)}"
        return if (pages == 1) listOf("$base.png") else (1..pages).map { "${base}_trang$it.png" }
    }

    fun freeBytes(): Long = runCatching {
        val dir = if (Build.VERSION.SDK_INT >= 29) context.filesDir else Environment.getExternalStorageDirectory()
        StatFs(dir.absolutePath).availableBytes
    }.getOrDefault(Long.MAX_VALUE)

    /** Lưu ảnh vào Pictures/VIGO Driver Log. Xuất lại cùng ngày sẽ thay thế file cũ. */
    suspend fun save(day: LocalDate, pages: List<Bitmap>): ExportedReport = withContext(Dispatchers.IO) {
        if (freeBytes() < MIN_FREE_BYTES) throw ReportException("Bộ nhớ điện thoại sắp đầy (còn dưới 40 MB). Hãy giải phóng dung lượng rồi xuất lại.")
        val names = fileNames(day, pages.size)
        val uris = try {
            if (Build.VERSION.SDK_INT >= 29) saveMediaStore(names, pages) else saveLegacy(names, pages)
        } catch (e: ReportException) { throw e
        } catch (e: SecurityException) {
            throw ReportException("Chưa có quyền lưu ảnh vào bộ nhớ. Hãy cấp quyền lưu trữ rồi thử lại.")
        } catch (e: IOException) {
            throw ReportException("Không ghi được file ảnh: ${e.message ?: "lỗi bộ nhớ"}.")
        }
        ExportedReport(uris, names, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
    }

    @RequiresApi(29)
    private fun saveMediaStore(names: List<String>, pages: List<Bitmap>): List<Uri> {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relPath = "${Environment.DIRECTORY_PICTURES}/$FOLDER/"
        // Xóa bản xuất cũ (do chính ứng dụng tạo) của cùng ngày để tên file giữ nguyên.
        val base = names.first().substringBefore(".png").substringBefore("_trang")
        runCatching {
            resolver.query(collection, arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.RELATIVE_PATH}=? AND ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
                arrayOf(relPath, "$base%"), null)?.use { cur ->
                while (cur.moveToNext()) {
                    val id = cur.getLong(0)
                    runCatching { resolver.delete(android.content.ContentUris.withAppendedId(collection, id), null, null) }
                }
            }
        }
        return names.zip(pages).map { (name, bmp) ->
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, relPath)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: throw IOException("Không tạo được file trong thư viện ảnh")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("Nén PNG thất bại")
                } ?: throw IOException("Không mở được file để ghi")
                values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (e: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                throw e
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(names: List<String>, pages: List<Bitmap>): List<Uri> {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), FOLDER)
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Không tạo được thư mục $FOLDER")
        val base = names.first().substringBefore(".png").substringBefore("_trang")
        dir.listFiles()?.filter { it.name.startsWith(base) }?.forEach { it.delete() }
        val files = names.zip(pages).map { (name, bmp) ->
            val f = File(dir, name)
            f.outputStream().use { if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("Nén PNG thất bại") }
            f
        }
        MediaScannerConnection.scanFile(context, files.map { it.absolutePath }.toTypedArray(), arrayOf("image/png"), null)
        return files.map { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it) }
    }

    fun viewIntent(uri: Uri): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "image/png")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun shareIntent(uris: List<Uri>, day: LocalDate): Intent {
        val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
        else Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)) }
        send.type = "image/png"
        send.putExtra(Intent.EXTRA_SUBJECT, "Báo cáo nhật ký hành trình ngày ${Formatters.date(day)}")
        send.putExtra(Intent.EXTRA_TEXT, "Báo cáo nhật ký hành trình ngày ${Formatters.date(day)} – VIGO Driver Log")
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Chia sẻ báo cáo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
