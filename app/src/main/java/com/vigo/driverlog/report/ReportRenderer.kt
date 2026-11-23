package com.vigo.driverlog.report

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.vigo.driverlog.domain.AddressStatus
import com.vigo.driverlog.domain.EventType
import com.vigo.driverlog.domain.Formatters
import com.vigo.driverlog.domain.LocationSource
import com.vigo.driverlog.domain.ReportItem
import com.vigo.driverlog.domain.ReportModel

/**
 * Vẽ ảnh báo cáo 1080 px chiều ngang bằng Canvas. Mọi khối chữ dùng StaticLayout để tự xuống dòng
 * (không tràn chữ). Chiều cao tự tăng theo dữ liệu; nếu quá dài sẽ tự chia thành nhiều trang.
 */
class ReportRenderer {

    companion object {
        const val WIDTH = 1080
        const val MIN_HEIGHT = 1920
        /** Giới hạn chiều cao mỗi trang để không thiếu bộ nhớ trên máy yếu. */
        const val MAX_PAGE_HEIGHT = 6000
        const val PAD = 60
        const val MAP_HEIGHT = 640
    }

    private val contentW = WIDTH - 2 * PAD

    private fun tp(size: Float, color: Int = ReportColors.TEXT, bold: Boolean = false, italic: Boolean = false) =
        TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = size; this.color = color
            typeface = Typeface.create(Typeface.SANS_SERIF, when {
                bold && italic -> Typeface.BOLD_ITALIC; bold -> Typeface.BOLD; italic -> Typeface.ITALIC; else -> Typeface.NORMAL
            })
        }

    private fun layout(text: CharSequence, paint: TextPaint, width: Int, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(10))
            .setAlignment(align).setLineSpacing(4f, 1.08f).setIncludePad(false).build()

    /** Một khối nội dung có chiều cao biết trước. */
    private abstract inner class Block {
        abstract val height: Int
        abstract fun draw(c: Canvas, top: Int)
        /** Khối tiêu đề không được nằm cuối trang. */
        open val keepWithNext: Boolean = false
    }

    private inner class Spacer(override val height: Int) : Block() { override fun draw(c: Canvas, top: Int) {} }

    private inner class TextBlock(text: CharSequence, paint: TextPaint, val indent: Int = 0, val topPad: Int = 0, val bottomPad: Int = 0) : Block() {
        val l = layout(text, paint, contentW - indent)
        override val height = l.height + topPad + bottomPad
        override fun draw(c: Canvas, top: Int) {
            c.save(); c.translate((PAD + indent).toFloat(), (top + topPad).toFloat()); l.draw(c); c.restore()
        }
    }

    private inner class HeaderBlock(val m: ReportModel, val continuation: Boolean) : Block() {
        val brand = layout("VIGO DRIVER LOG", tp(30f, Color.rgb(0x9C, 0xC4, 0xF0), bold = true).apply { letterSpacing = 0.18f }, contentW)
        val title = layout(if (continuation) "BÁO CÁO NHẬT KÝ HÀNH TRÌNH (tiếp theo)" else "BÁO CÁO NHẬT KÝ HÀNH TRÌNH", tp(50f, Color.WHITE, bold = true), contentW)
        val date = layout("Ngày báo cáo: ${Formatters.date(m.day)}", tp(34f, Color.WHITE), contentW)
        override val height = 56 + brand.height + 18 + title.height + 18 + date.height + 52
        override fun draw(c: Canvas, top: Int) {
            c.drawRect(0f, top.toFloat(), WIDTH.toFloat(), (top + height).toFloat(), Paint().apply { color = ReportColors.NAVY })
            c.drawRect(0f, (top + height - 10).toFloat(), WIDTH.toFloat(), (top + height).toFloat(), Paint().apply { color = ReportColors.BLUE })
            var y = top + 56
            c.save(); c.translate(PAD.toFloat(), y.toFloat()); brand.draw(c); c.restore(); y += brand.height + 18
            c.save(); c.translate(PAD.toFloat(), y.toFloat()); title.draw(c); c.restore(); y += title.height + 18
            c.save(); c.translate(PAD.toFloat(), y.toFloat()); date.draw(c); c.restore()
        }
    }

    /** Bảng 2 cột nhãn – giá trị trên nền xám nhạt. */
    private inner class InfoCard(rows: List<Pair<String, String>>) : Block() {
        val labelW = 300
        val rowsL = rows.map { (k, v) -> layout(k, tp(32f, ReportColors.MUTED), labelW - 20) to layout(v, tp(34f, ReportColors.TEXT, bold = true), contentW - labelW - 60) }
        val rowH = rowsL.map { (a, b) -> maxOf(a.height, b.height) + 28 }
        override val height = rowH.sum() + 24
        override fun draw(c: Canvas, top: Int) {
            c.drawRoundRect(RectF(PAD.toFloat(), top.toFloat(), (WIDTH - PAD).toFloat(), (top + height).toFloat()), 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ReportColors.BG })
            var y = top + 12
            val line = Paint().apply { color = ReportColors.LINE; strokeWidth = 2f }
            rowsL.forEachIndexed { i, (a, b) ->
                c.save(); c.translate((PAD + 30).toFloat(), (y + 14).toFloat()); a.draw(c); c.restore()
                c.save(); c.translate((PAD + 30 + labelW).toFloat(), (y + 14).toFloat()); b.draw(c); c.restore()
                y += rowH[i]
                if (i < rowsL.lastIndex) c.drawLine((PAD + 30).toFloat(), y.toFloat(), (WIDTH - PAD - 30).toFloat(), y.toFloat(), line)
            }
        }
    }

    private inner class SectionTitle(text: String) : Block() {
        val l = layout(text, tp(36f, ReportColors.NAVY, bold = true), contentW - 30)
        override val height = l.height + 56
        override val keepWithNext = true
        override fun draw(c: Canvas, top: Int) {
            c.drawRect(PAD.toFloat(), (top + 28).toFloat(), (PAD + 10).toFloat(), (top + 28 + l.height).toFloat(), Paint().apply { color = ReportColors.BLUE })
            c.save(); c.translate((PAD + 30).toFloat(), (top + 28).toFloat()); l.draw(c); c.restore()
        }
    }

    private inner class MetricsGrid(metrics: List<Pair<String, String>>) : Block() {
        val colW = (contentW - 24) / 2
        val cells = metrics.map { (k, v) -> layout(k, tp(28f, ReportColors.MUTED), colW - 48) to layout(v, tp(40f, ReportColors.NAVY, bold = true), colW - 48) }
        val rows = cells.chunked(2)
        val rowH = rows.map { r -> r.maxOf { (a, b) -> a.height + b.height } + 60 }
        override val height = rowH.sum() + (rows.size - 1).coerceAtLeast(0) * 24
        override fun draw(c: Canvas, top: Int) {
            var y = top
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ReportColors.BG }
            val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ReportColors.GREEN }
            rows.forEachIndexed { ri, r ->
                r.forEachIndexed { ci, (a, b) ->
                    val x = PAD + ci * (colW + 24)
                    c.drawRoundRect(RectF(x.toFloat(), y.toFloat(), (x + colW).toFloat(), (y + rowH[ri]).toFloat()), 16f, 16f, bg)
                    c.drawRoundRect(RectF(x.toFloat(), y.toFloat(), (x + 8).toFloat(), (y + rowH[ri]).toFloat()), 4f, 4f, accent)
                    c.save(); c.translate((x + 30).toFloat(), (y + 24).toFloat()); a.draw(c); c.translate(0f, (a.height + 10).toFloat()); b.draw(c); c.restore()
                }
                y += rowH[ri] + 24
            }
        }
    }

    private inner class MapBlock(val map: Bitmap) : Block() {
        override val height = map.height + 8
        override fun draw(c: Canvas, top: Int) {
            val r = RectF(PAD.toFloat(), top.toFloat(), (WIDTH - PAD).toFloat(), (top + map.height).toFloat())
            c.drawBitmap(map, null, r, Paint(Paint.FILTER_BITMAP_FLAG))
            c.drawRoundRect(r, 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = ReportColors.LINE })
        }
    }

    private inner class Legend : Block() {
        val entries = listOf(EventType.START_TRIP, EventType.ARRIVE, EventType.PARK_START, EventType.RESUME, EventType.END_TRIP, EventType.QUICK_LOG)
        val paint = tp(24f, ReportColors.MUTED)
        val labels = mapOf(EventType.START_TRIP to "Xuất phát", EventType.ARRIVE to "Điểm đến", EventType.PARK_START to "Đỗ xe",
            EventType.RESUME to "Tiếp tục", EventType.END_TRIP to "Kết thúc", EventType.QUICK_LOG to "Ghi nhận nhanh")
        // Xếp theo hàng, tự xuống dòng khi hết chỗ
        val positions: List<Triple<EventType, Int, Int>>
        override val height: Int
        init {
            var x = 0; var row = 0
            val pos = mutableListOf<Triple<EventType, Int, Int>>()
            for (e in entries) {
                val w = 34 + paint.measureText(labels[e]).toInt() + 30
                if (x + w > contentW && x > 0) { x = 0; row++ }
                pos += Triple(e, x, row); x += w
            }
            positions = pos; height = (row + 1) * 40 + 10
        }
        override fun draw(c: Canvas, top: Int) {
            val dot = Paint(Paint.ANTI_ALIAS_FLAG)
            for ((e, x, row) in positions) {
                val cy = top + row * 40 + 20f
                dot.color = ReportColors.forType(e)
                c.drawCircle(PAD + x + 12f, cy, 11f, dot)
                c.drawText(labels[e]!!, PAD + x + 32f, cy + 8f, paint)
            }
        }
    }

    private inner class TripHeader(title: String, subtitle: String?) : Block() {
        val t = layout(title, tp(30f, Color.WHITE, bold = true), contentW - 48)
        val s = subtitle?.let { layout(it, tp(26f, Color.rgb(0xD6, 0xE4, 0xF5)), contentW - 48) }
        override val height = 20 + t.height + (s?.let { it.height + 6 } ?: 0) + 20 + 20
        override val keepWithNext = true
        override fun draw(c: Canvas, top: Int) {
            c.drawRoundRect(RectF(PAD.toFloat(), top.toFloat(), (WIDTH - PAD).toFloat(), (top + height - 20).toFloat()), 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ReportColors.NAVY })
            c.save(); c.translate((PAD + 24).toFloat(), (top + 20).toFloat()); t.draw(c)
            s?.let { c.translate(0f, (t.height + 6).toFloat()); it.draw(c) }
            c.restore()
        }
    }

    /** Một mục timeline: giờ | chấm + đường dọc | nội dung. */
    private inner class TimelineItem(item: ReportItem, use24h: Boolean, val isLast: Boolean) : Block() {
        val timeW = 150
        val railX = PAD + timeW + 26
        val textX = railX + 36
        val textW = WIDTH - PAD - textX
        val dotColor = ReportColors.forType(item.type)
        val time = layout(Formatters.time(item.event.eventTimestamp, use24h), tp(34f, ReportColors.NAVY, bold = true), timeW)
        val title: StaticLayout
        val body: StaticLayout?
        override val height: Int

        init {
            val e = item.event
            val titleText = buildString {
                append(item.type.reportLabel)
                item.mapIndex?.let { append("  [$it]") }
            }
            title = layout(titleText, tp(32f, dotColor, bold = true), textW)
            val lines = mutableListOf<String>()
            val addr = when (AddressStatus.from(e.addressStatus)) {
                AddressStatus.RESOLVED, AddressStatus.CORRECTED, AddressStatus.MANUAL -> e.address ?: "Chưa xác định địa chỉ"
                else -> "Chưa xác định địa chỉ"
            }
            if (item.type != EventType.RESUME || e.address != null) lines += "Địa điểm: $addr"
            if (e.latitude != null && e.longitude != null) {
                val acc = e.accuracy?.let { " (${Formatters.accuracy(it)})" } ?: ""
                lines += "GPS: ${Formatters.coords(e.latitude, e.longitude)}$acc"
            }
            when (LocationSource.from(e.locationSource)) {
                LocationSource.MANUAL -> lines += "⚠ Vị trí nhập thủ công – không có tọa độ GPS."
                LocationSource.GPS_CACHED -> lines += "⚠ Vị trí gần nhất (cache), lấy cách thời điểm ghi nhận ${Formatters.minutesText(e.locationAgeMs ?: 0)}."
                else -> {}
            }
            if (e.lowAccuracy) lines += "⚠ Độ chính xác GPS thấp."
            if (e.isMockLocation) lines += "⚠ Hệ thống báo vị trí đến từ ứng dụng giả lập vị trí."
            item.parkingDurationMs?.let { lines += "Thời gian đỗ: ${Formatters.duration(it)}" }
            e.note?.let { lines += "Ghi chú: $it" }
            e.clockWarning?.let { lines += "⚠ $it" }
            if (item.edits.isNotEmpty()) {
                val last = item.edits.maxOf { it.editedAt }
                lines += "Đã chỉnh sửa ${item.edits.size} lần, lần cuối ${Formatters.dateTime(last)} (dữ liệu gốc được lưu trong nhật ký chỉnh sửa)."
            }
            body = if (lines.isEmpty()) null else layout(lines.joinToString("\n"), tp(28f, ReportColors.TEXT), textW)
            height = maxOf(time.height, title.height + (body?.let { it.height + 8 } ?: 0)) + 34
        }

        override fun draw(c: Canvas, top: Int) {
            c.save(); c.translate(PAD.toFloat(), top.toFloat()); time.draw(c); c.restore()
            val rail = Paint().apply { this.color = ReportColors.LINE; strokeWidth = 4f }
            if (!isLast) c.drawLine(railX.toFloat(), (top + 22).toFloat(), railX.toFloat(), (top + height).toFloat(), rail)
            c.drawCircle(railX.toFloat(), (top + 22).toFloat(), 15f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotColor })
            c.drawCircle(railX.toFloat(), (top + 22).toFloat(), 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE })
            c.save(); c.translate(textX.toFloat(), top.toFloat()); title.draw(c)
            body?.let { c.translate(0f, (title.height + 8).toFloat()); it.draw(c) }
            c.restore()
        }
    }

    private inner class Footer(exportedAt: Long, page: Int, pages: Int) : Block() {
        val l = layout(
            "Ngày xuất báo cáo: ${Formatters.date(exportedAt)}\nThời điểm xuất: ${Formatters.time(exportedAt, true, seconds = true)}",
            tp(28f, ReportColors.MUTED), contentW
        )
        val confirm = layout("Báo cáo được tạo từ dữ liệu ghi nhận trên VIGO Driver Log.", tp(28f, ReportColors.NAVY, italic = true), contentW)
        val pageL = if (pages > 1) layout("Trang $page/$pages", tp(26f, ReportColors.MUTED), contentW, Layout.Alignment.ALIGN_OPPOSITE) else null
        override val height = 40 + l.height + 16 + confirm.height + (pageL?.let { it.height + 12 } ?: 0) + 50
        override fun draw(c: Canvas, top: Int) {
            c.drawLine(PAD.toFloat(), (top + 20).toFloat(), (WIDTH - PAD).toFloat(), (top + 20).toFloat(), Paint().apply { color = ReportColors.LINE; strokeWidth = 3f })
            var y = top + 40
            c.save(); c.translate(PAD.toFloat(), y.toFloat()); l.draw(c); c.restore(); y += l.height + 16
            c.save(); c.translate(PAD.toFloat(), y.toFloat()); confirm.draw(c); c.restore(); y += confirm.height + 12
            pageL?.let { c.save(); c.translate(PAD.toFloat(), y.toFloat()); it.draw(c); c.restore() }
        }
    }

    /** Tạo danh sách trang ảnh. [map] có thể null nếu ngày không có tọa độ GPS nào. */
    fun render(m: ReportModel, map: MapImage?, exportedAt: Long): List<Bitmap> {
        val body = mutableListOf<Block>()
        val info = mutableListOf("Tên lái xe" to m.driverName.ifBlank { "Chưa cài đặt" }, "Biển số xe" to m.vehiclePlate.ifBlank { "Chưa cài đặt" })
        m.companyName?.let { info += "Công ty" to it }
        body += Spacer(36); body += InfoCard(info)

        if (m.metrics.isNotEmpty()) {
            body += SectionTitle("TỔNG QUAN")
            body += MetricsGrid(m.metrics.map { it.label to it.value })
        }
        if (m.notices.isNotEmpty()) {
            body += Spacer(20)
            m.notices.forEach { body += TextBlock("• $it", tp(26f, ReportColors.ORANGE.darken(), italic = true), bottomPad = 6) }
        }
        if (map != null) {
            body += SectionTitle("SƠ ĐỒ CÁC ĐIỂM ĐÃ GHI NHẬN")
            body += MapBlock(map.bitmap)
            body += Spacer(10)
            body += Legend()
            body += TextBlock(
                "Đường nét đứt nối các điểm GPS do người dùng ghi nhận theo thứ tự thời gian, KHÔNG phải tuyến đường thực tế đã đi (ứng dụng không theo dõi liên tục). Số trong ngoặc [n] ở phần chi tiết tương ứng với điểm trên sơ đồ.",
                tp(24f, ReportColors.MUTED, italic = true), topPad = 6
            )
        }
        body += SectionTitle("CHI TIẾT HÀNH TRÌNH")
        m.sections.forEach { s ->
            body += TripHeader(s.title, s.subtitle)
            s.items.forEachIndexed { i, it -> body += TimelineItem(it, m.use24h, i == s.items.lastIndex) }
            body += Spacer(10)
        }

        // Chia trang theo khối – không cắt ngang một mục timeline.
        val headerFirst = HeaderBlock(m, false)
        val headerNext = HeaderBlock(m, true)
        val footerProbe = Footer(exportedAt, 1, 2)
        val pages = mutableListOf<MutableList<Block>>()
        var current = mutableListOf<Block>()
        var used = headerFirst.height
        val budget = MAX_PAGE_HEIGHT - footerProbe.height
        var i = 0
        while (i < body.size) {
            val b = body[i]
            val needed = b.height + if (b.keepWithNext && i + 1 < body.size) body[i + 1].height else 0
            if (used + needed > budget && current.isNotEmpty()) {
                pages += current; current = mutableListOf(); used = headerNext.height
            }
            current += b; used += b.height; i++
        }
        pages += current

        return pages.mapIndexed { pi, blocks ->
            val header = if (pi == 0) headerFirst else headerNext
            val footer = Footer(exportedAt, pi + 1, pages.size)
            val contentH = header.height + blocks.sumOf { it.height } + footer.height
            val h = contentH.coerceAtLeast(MIN_HEIGHT)
            val bmp = Bitmap.createBitmap(WIDTH, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(Color.WHITE)
            var y = 0
            header.draw(c, y); y += header.height
            blocks.forEach { it.draw(c, y); y += it.height }
            footer.draw(c, h - footer.height) // chân trang luôn ở đáy ảnh
            bmp
        }
    }

    private fun Int.darken(): Int = Color.rgb((Color.red(this) * 0.75).toInt(), (Color.green(this) * 0.6).toInt(), (Color.blue(this) * 0.5).toInt())
}
