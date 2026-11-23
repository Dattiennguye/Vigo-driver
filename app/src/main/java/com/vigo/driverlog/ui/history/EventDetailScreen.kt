package com.vigo.driverlog.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vigo.driverlog.domain.AddressStatus
import com.vigo.driverlog.domain.EventType
import com.vigo.driverlog.domain.Formatters
import com.vigo.driverlog.domain.LocationSource
import com.vigo.driverlog.ui.common.EventBadge
import com.vigo.driverlog.ui.common.LabelValue
import com.vigo.driverlog.ui.common.SectionCard
import com.vigo.driverlog.ui.theme.VigoColors

@Composable
fun EventDetailScreen(showMessage: (String) -> Unit, vm: EventDetailViewModel = viewModel()) {
    val e by vm.event.collectAsStateWithLifecycle()
    val logs by vm.logs.collectAsStateWithLifecycle()
    val trip by vm.trip.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.messages.collect { showMessage(it) } }

    var editNote by rememberSaveable { mutableStateOf(false) }
    var editAddress by rememberSaveable { mutableStateOf(false) }

    val ev = e ?: run {
        Text("Đang tải…", Modifier.padding(24.dp)); return
    }
    val type = EventType.from(ev.eventType)
    val use24h = settings.use24h

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EventBadge(type, 48)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(type.label, style = MaterialTheme.typography.titleLarge, color = VigoColors.forType(type))
                    Text("${Formatters.time(ev.eventTimestamp, use24h, seconds = true)} · ${Formatters.date(ev.eventTimestamp)}", color = VigoColors.Muted)
                }
            }
        }

        SectionCard {
            Text("Dữ liệu gốc (không thể sửa)", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(6.dp))
            LabelValue("Thời gian ghi nhận", Formatters.dateTime(ev.eventTimestamp))
            LabelValue("Nguồn vị trí", LocationSource.from(ev.locationSource).label)
            if (ev.latitude != null && ev.longitude != null) {
                LabelValue("Vĩ độ", Formatters.coord(ev.latitude))
                LabelValue("Kinh độ", Formatters.coord(ev.longitude))
            }
            ev.accuracy?.let { LabelValue("Độ chính xác", Formatters.accuracy(it), if (ev.lowAccuracy) VigoColors.Orange else VigoColors.Text) }
            ev.locationTime?.let { LabelValue("Giờ của bản tin vị trí", Formatters.dateTime(it)) }
            ev.locationAgeMs?.let { if (it > 30_000) LabelValue("Tuổi vị trí lúc ghi", Formatters.minutesText(it), VigoColors.Orange) }
            LabelValue("Múi giờ", ev.timezoneId)
            if (ev.isMockLocation) Text("⚠ Hệ thống báo vị trí đến từ ứng dụng giả lập vị trí.", color = VigoColors.Red, style = MaterialTheme.typography.bodyMedium)
            ev.clockWarning?.let { Text("⚠ $it", color = VigoColors.Orange, style = MaterialTheme.typography.bodyMedium) }
            trip?.let {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                LabelValue("Lái xe", it.driverName)
                LabelValue("Biển số", it.vehiclePlate)
                LabelValue("Chuyến bắt đầu", Formatters.dateTime(it.startTime))
            }
        }

        SectionCard {
            Text("Địa chỉ", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(4.dp))
            Text(addressText(ev))
            if (ev.addressStatus == AddressStatus.CORRECTED.name) Text("Đã được người dùng sửa – xem nhật ký bên dưới.", color = VigoColors.Muted, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ev.latitude != null && (ev.addressStatus == AddressStatus.NOT_FOUND.name || ev.addressStatus == AddressStatus.PENDING.name)) {
                    OutlinedButton(onClick = { vm.retryAddress() }) { Text("Xác định lại") }
                }
                OutlinedButton(onClick = { editAddress = true }) { Text("Sửa địa chỉ") }
            }
        }

        SectionCard {
            Text("Ghi chú", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(4.dp))
            Text(ev.note ?: "Chưa có ghi chú", color = if (ev.note == null) VigoColors.Muted else VigoColors.Text)
            OutlinedButton(onClick = { editNote = true }) { Text(if (ev.note == null) "Thêm ghi chú" else "Sửa ghi chú") }
        }

        SectionCard {
            Text("Nhật ký chỉnh sửa", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(4.dp))
            if (logs.isEmpty()) Text("Chưa có chỉnh sửa nào.", color = VigoColors.Muted)
            logs.forEach { l ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text("${Formatters.dateTime(l.editedAt)} · ${if (l.fieldName == "note") "Ghi chú" else if (l.fieldName == "address") "Địa chỉ" else l.fieldName}", fontWeight = FontWeight.SemiBold)
                    Text("Cũ: ${l.oldValue ?: "(trống)"}", style = MaterialTheme.typography.bodySmall, color = VigoColors.Muted)
                    Text("Mới: ${l.newValue ?: "(trống)"}", style = MaterialTheme.typography.bodySmall)
                    l.reason?.let { Text("Lý do: $it", style = MaterialTheme.typography.bodySmall, color = VigoColors.Muted) }
                }
            }
        }
    }

    if (editNote) {
        var text by rememberSaveable { mutableStateOf(ev.note ?: "") }
        AlertDialog(
            onDismissRequest = { editNote = false },
            title = { Text("Ghi chú") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it.take(300) }, maxLines = 5, label = { Text("Nội dung ghi chú") }) },
            confirmButton = { Button(onClick = { vm.saveNote(text); editNote = false }) { Text("LƯU") } },
            dismissButton = { TextButton(onClick = { editNote = false }) { Text("Hủy") } },
        )
    }
    if (editAddress) {
        var text by rememberSaveable { mutableStateOf(ev.address ?: "") }
        var reason by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { editAddress = false },
            title = { Text("Sửa địa chỉ") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tọa độ và thời gian gốc được giữ nguyên. Địa chỉ cũ, địa chỉ mới và thời điểm sửa sẽ được lưu vào nhật ký.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = text, onValueChange = { text = it.take(250) }, maxLines = 3, label = { Text("Địa chỉ đúng") })
                    OutlinedTextField(value = reason, onValueChange = { reason = it.take(150) }, maxLines = 2, label = { Text("Lý do sửa (không bắt buộc)") })
                }
            },
            confirmButton = { Button(onClick = { vm.correctAddress(text, reason); editAddress = false }, enabled = text.isNotBlank()) { Text("LƯU") } },
            dismissButton = { TextButton(onClick = { editAddress = false }) { Text("Hủy") } },
        )
    }
}
