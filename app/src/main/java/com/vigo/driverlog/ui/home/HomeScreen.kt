package com.vigo.driverlog.ui.home

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vigo.driverlog.data.location.FailReason
import com.vigo.driverlog.data.location.GpsStatus
import com.vigo.driverlog.data.location.LocationService
import com.vigo.driverlog.domain.EventType
import com.vigo.driverlog.domain.Formatters
import com.vigo.driverlog.domain.TripPhase
import com.vigo.driverlog.ui.common.BigActionButton
import com.vigo.driverlog.ui.common.EventBadge
import com.vigo.driverlog.ui.common.LabelValue
import com.vigo.driverlog.ui.common.SectionCard
import com.vigo.driverlog.ui.common.WarningCard
import com.vigo.driverlog.ui.theme.VigoColors
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenEvent: (String) -> Unit,
    showMessage: (String) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val gps by vm.gps.collectAsStateWithLifecycle()
    val capturing by vm.capturing.collectAsStateWithLifecycle()
    val dialog by vm.dialog.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.messages.collect { showMessage(it) } }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        vm.onPermissionChanged()
        if (res.values.none { it }) showMessage("Bạn đã từ chối quyền vị trí. Vẫn có thể ghi nhận bằng cách nhập địa điểm thủ công.")
    }
    val requestPerm = { permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
    LaunchedEffect(Unit) { if (!vm.hasPermission()) requestPerm() }

    // Đồng hồ cập nhật mỗi 15 giây cho thời lượng đang chạy
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Header(st, now)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (st.loaded && !st.settings.isProfileComplete) {
                WarningCard("Chưa có thông tin lái xe", "Nhập tên lái xe và biển số xe một lần, ứng dụng sẽ tự dùng cho các ngày sau.") {
                    Button(onClick = onOpenSettings) { Text("MỞ CÀI ĐẶT") }
                }
            }
            if (st.isStaleTrip) {
                val t = st.activeTrip!!
                WarningCard(
                    "Chuyến đi ngày ${Formatters.date(t.startTime)} chưa kết thúc",
                    "Chuyến đi bắt đầu lúc ${Formatters.time(t.startTime, st.settings.use24h)} ngày ${Formatters.date(t.startTime)} chưa có sự kiện kết thúc. " +
                        "Bạn có thể tiếp tục chuyến đi, kết thúc ngay bây giờ (ghi nhận giờ và vị trí hiện tại), hoặc đánh dấu chưa hoàn thành (không tạo giờ kết thúc giả)."
                ) {
                    OutlinedButton(onClick = { vm.markStaleIncomplete() }, modifier = Modifier.fillMaxWidth()) { Text("ĐÁNH DẤU CHƯA HOÀN THÀNH") }
                }
            }

            GpsCard(gps, st.settings.use24h, now, onRequestPermission = requestPerm) {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }

            TripCard(st, now)

            // Nút thao tác chính theo trạng thái thực tế
            st.actions.forEach { type ->
                BigActionButton(
                    type = type,
                    onClick = { vm.onAction(type) },
                    enabled = capturing == null,
                    loading = capturing == type,
                )
            }

            OutlinedButton(
                onClick = { vm.onAction(EventType.QUICK_LOG) },
                enabled = capturing == null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Filled.AddLocationAlt, null, tint = VigoColors.Purple)
                Spacer(Modifier.width(10.dp))
                Text(if (capturing == EventType.QUICK_LOG) "ĐANG LẤY VỊ TRÍ GPS…" else "GHI NHẬN NHANH", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = VigoColors.Purple)
            }

            if (st.tripEvents.isNotEmpty()) {
                SectionCard {
                    Text("Sự kiện gần đây", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(8.dp))
                    st.tripEvents.takeLast(4).reversed().forEach { e ->
                        val type = EventType.from(e.eventType)
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EventBadge(type, 34)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${Formatters.time(e.eventTimestamp, st.settings.use24h)} · ${type.label}", fontWeight = FontWeight.SemiBold)
                                Text(e.address ?: if (e.latitude != null) "Chưa xác định địa chỉ" else "Không có tọa độ",
                                    style = MaterialTheme.typography.bodyMedium, color = VigoColors.Muted, maxLines = 2)
                            }
                            TextButton(onClick = { onOpenEvent(e.id) }) { Text("Chi tiết") }
                        }
                    }
                }
            }
        }
    }

    CaptureDialogs(dialog, vm, onRequestPermission = requestPerm, onOpenLocationSettings = {
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    })
}

@Composable
private fun Header(st: HomeUiState, now: Long) {
    Column(
        Modifier.fillMaxWidth().background(VigoColors.Navy).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("VIGO DRIVER LOG", color = Color(0xFF9CC4F0), fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp)
        Text(st.settings.driverName.ifBlank { "Chưa nhập tên lái xe" }, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Biển số: ${st.settings.vehiclePlate.ifBlank { "—" }}", color = Color.White, fontSize = 16.sp)
        Text(Formatters.weekdayDate(LocalDate.now(ZoneId.systemDefault())), color = Color(0xFFD6E4F5), fontSize = 14.sp)
        Spacer(Modifier.size(6.dp))
        val (label, color) = when (st.phase) {
            TripPhase.NOT_STARTED -> (if (st.lastFinished != null && isToday(st.lastFinished.endTime ?: 0)) "Đã hoàn thành chuyến đi" else "Chưa bắt đầu") to Color(0xFF9AA8BA)
            TripPhase.MOVING -> "Đang di chuyển" to VigoColors.Green
            TripPhase.ARRIVED -> "Đã đến nơi" to VigoColors.Blue
            TripPhase.PARKED -> "Đang đỗ xe" to VigoColors.Orange
            TripPhase.LEFT_PARKING -> "Đã rời điểm đỗ" to VigoColors.Teal
        }
        Box(Modifier.background(color, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp)) {
            Text("Trạng thái: $label", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

private fun isToday(ms: Long): Boolean {
    val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return ms >= start
}

@Composable
private fun GpsCard(gps: GpsStatus, use24h: Boolean, now: Long, onRequestPermission: () -> Unit, onOpenLocationSettings: () -> Unit) {
    SectionCard {
        val fix = gps.lastFix
        val (icon, title, color) = when {
            !gps.permissionGranted -> Triple(Icons.Filled.GpsOff, "Chưa cấp quyền vị trí", VigoColors.Red)
            !gps.locationEnabled -> Triple(Icons.Filled.GpsOff, "Định vị đang tắt", VigoColors.Red)
            fix == null -> Triple(Icons.Filled.GpsNotFixed, "Đang tìm vị trí…", VigoColors.Orange)
            fix.accuracy > LocationService.GOOD_ACCURACY_M -> Triple(Icons.Filled.GpsNotFixed, "GPS hoạt động – độ chính xác thấp", VigoColors.Orange)
            else -> Triple(Icons.Filled.GpsFixed, "GPS đang hoạt động", VigoColors.Green)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Text(title, fontWeight = FontWeight.Bold, color = color, fontSize = 16.sp)
        }
        if (fix != null && gps.permissionGranted && gps.locationEnabled) {
            Spacer(Modifier.size(6.dp))
            LabelValue("Vị trí hiện tại", Formatters.coords(fix.latitude, fix.longitude))
            LabelValue("Độ chính xác", Formatters.accuracy(fix.accuracy))
            LabelValue("Cập nhật lúc", Formatters.time(fix.time, use24h, seconds = true))
            if (fix.isMock) Text("⚠ Hệ thống báo vị trí giả lập đang bật.", color = VigoColors.Red, style = MaterialTheme.typography.bodyMedium)
        }
        if (!gps.permissionGranted) {
            Spacer(Modifier.size(8.dp))
            Button(onClick = onRequestPermission) { Text("CẤP QUYỀN VỊ TRÍ") }
        } else if (!gps.locationEnabled) {
            Spacer(Modifier.size(8.dp))
            Button(onClick = onOpenLocationSettings) { Text("BẬT ĐỊNH VỊ") }
        }
    }
}

@Composable
private fun TripCard(st: HomeUiState, now: Long) {
    val trip = st.activeTrip
    if (trip == null) {
        val last = st.lastFinished
        if (last != null && isToday(last.endTime ?: 0)) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = VigoColors.Green)
                    Spacer(Modifier.width(8.dp))
                    Text("Chuyến đi đã hoàn thành", fontWeight = FontWeight.Bold, color = VigoColors.Green)
                }
                Spacer(Modifier.size(6.dp))
                LabelValue("Bắt đầu", Formatters.time(last.startTime, st.settings.use24h))
                last.endTime?.let {
                    LabelValue("Kết thúc", Formatters.time(it, st.settings.use24h))
                    LabelValue("Tổng thời gian", Formatters.duration(it - last.startTime))
                }
                Text("Nhấn BẮT ĐẦU ĐI để bắt đầu chuyến mới, hoặc vào Báo cáo để xuất ảnh.", style = MaterialTheme.typography.bodyMedium, color = VigoColors.Muted)
            }
        }
        return
    }
    SectionCard {
        Text("Chuyến đi hiện tại", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(6.dp))
        LabelValue("Bắt đầu lúc", Formatters.time(trip.startTime, st.settings.use24h) +
            if (!isToday(trip.startTime)) " (${Formatters.date(trip.startTime)})" else "")
        LabelValue("Đã đi được", Formatters.duration(now - trip.startTime))
        LabelValue("Số địa điểm đã đến", st.tripEvents.count { it.eventType == EventType.ARRIVE.name }.toString())
        LabelValue("Số lần đỗ xe", st.tripEvents.count { it.eventType == EventType.PARK_START.name }.toString())
        if (st.phase == TripPhase.PARKED) {
            st.tripEvents.lastOrNull { it.eventType == EventType.PARK_START.name }?.let {
                LabelValue("Đang đỗ từ", Formatters.time(it.eventTimestamp, st.settings.use24h), VigoColors.Orange)
                LabelValue("Thời gian đỗ", Formatters.duration(now - it.eventTimestamp), VigoColors.Orange)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CaptureDialogs(d: CaptureDialog?, vm: HomeViewModel, onRequestPermission: () -> Unit, onOpenLocationSettings: () -> Unit) {
    when (d) {
        null -> {}
        is CaptureDialog.ConfirmEnd -> AlertDialog(
            onDismissRequest = vm::dismissDialog,
            title = { Text("Kết thúc chuyến đi?") },
            text = { Text("Ứng dụng sẽ ghi nhận giờ ${Formatters.time(d.pending.tap.wallClockMs, seconds = true)} và vị trí hiện tại làm điểm kết thúc. Sau khi kết thúc không thể ghi thêm sự kiện vào chuyến này.") },
            confirmButton = { Button(onClick = { vm.confirmEnd(d.pending) }, colors = ButtonDefaults.buttonColors(containerColor = VigoColors.Red)) { Text("KẾT THÚC") } },
            dismissButton = { TextButton(onClick = vm::dismissDialog) { Text("Hủy") } },
        )

        is CaptureDialog.LowAccuracy -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Độ chính xác GPS thấp") },
            text = {
                Text("Vị trí hiện tại chỉ chính xác trong khoảng ${Formatters.accuracy(d.fix.accuracy)} (khuyến nghị dưới ${LocationService.GOOD_ACCURACY_M.toInt()} m). " +
                    "Bạn có thể ra chỗ thoáng rồi thử lại, hoặc vẫn lưu – sự kiện sẽ được đánh dấu \"độ chính xác thấp\".\n\n" +
                    "Thời điểm ghi nhận: ${Formatters.time(d.pending.tap.wallClockMs, seconds = true)}")
            },
            confirmButton = { Button(onClick = { vm.saveLowAccuracy(d) }) { Text("VẪN LƯU") } },
            dismissButton = {
                Row {
                    TextButton(onClick = vm::dismissDialog) { Text("Hủy") }
                    TextButton(onClick = { vm.acquire(d.pending) }) { Text("Thử lại") }
                }
            },
        )

        is CaptureDialog.Failed -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Chưa lấy được vị trí") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(d.reason.message)
                    Text("Thời điểm ghi nhận (đã lưu tạm): ${Formatters.time(d.pending.tap.wallClockMs, seconds = true)}", color = VigoColors.Muted)
                    when (d.reason) {
                        FailReason.NO_PERMISSION -> TextButton(onClick = { vm.dismissDialog(); onRequestPermission() }) { Text("Cấp quyền vị trí") }
                        FailReason.LOCATION_DISABLED -> TextButton(onClick = onOpenLocationSettings) { Text("Mở cài đặt định vị") }
                        else -> {}
                    }
                    d.cached?.let { f ->
                        OutlinedButton(onClick = { vm.saveCached(d) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Dùng vị trí gần nhất (${Formatters.minutesText(f.ageMs())} trước, ${Formatters.accuracy(f.accuracy)}) – sẽ được ghi rõ là vị trí cache")
                        }
                    }
                    OutlinedButton(onClick = { vm.openManual(d.pending) }, modifier = Modifier.fillMaxWidth()) { Text("Nhập địa điểm thủ công") }
                }
            },
            confirmButton = { Button(onClick = { vm.acquire(d.pending) }) { Text("THỬ LẠI") } },
            dismissButton = { TextButton(onClick = vm::dismissDialog) { Text("Hủy") } },
        )

        is CaptureDialog.Manual -> {
            var place by rememberSaveable { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Nhập địa điểm thủ công") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Sự kiện \"${d.pending.type.label}\" lúc ${Formatters.time(d.pending.tap.wallClockMs, seconds = true)} sẽ được đánh dấu là vị trí nhập thủ công, không có tọa độ GPS.",
                            style = MaterialTheme.typography.bodyMedium)
                        OutlinedTextField(value = place, onValueChange = { place = it.take(200) }, label = { Text("Tên / địa chỉ địa điểm") }, singleLine = false, maxLines = 3)
                    }
                },
                confirmButton = { Button(onClick = { vm.saveManual(d.pending, place) }, enabled = place.isNotBlank()) { Text("LƯU") } },
                dismissButton = { TextButton(onClick = vm::dismissDialog) { Text("Hủy") } },
            )
        }

        is CaptureDialog.AddNote -> {
            var note by rememberSaveable(d.event.id) { mutableStateOf(d.event.note ?: "") }
            val presets = listOf("Ghé ngân hàng", "Ghé kho hàng", "Đón khách", "Trả khách", "Giao tài liệu", "Đổ nhiên liệu", "Dừng phát sinh")
            val type = EventType.from(d.event.eventType)
            AlertDialog(
                onDismissRequest = vm::dismissDialog,
                title = { Text(if (type == EventType.QUICK_LOG) "Đã lưu ghi nhận nhanh" else "Thêm ghi chú") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text("Lúc ${Formatters.time(d.event.eventTimestamp, seconds = true)}. Thêm ghi chú ngắn (không bắt buộc):", style = MaterialTheme.typography.bodyMedium)
                        if (type == EventType.QUICK_LOG) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                presets.forEach { p -> FilterChip(selected = note == p, onClick = { note = p }, label = { Text(p) }) }
                            }
                        }
                        OutlinedTextField(value = note, onValueChange = { note = it.take(300) }, label = { Text("Ghi chú") }, maxLines = 4)
                    }
                },
                confirmButton = { Button(onClick = { vm.saveNote(d.event.id, note) }) { Text("LƯU GHI CHÚ") } },
                dismissButton = { TextButton(onClick = vm::dismissDialog) { Text("Bỏ qua") } },
            )
        }
    }
}
