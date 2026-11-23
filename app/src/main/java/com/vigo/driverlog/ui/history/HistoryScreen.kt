package com.vigo.driverlog.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vigo.driverlog.data.db.EventEntity
import com.vigo.driverlog.domain.AddressStatus
import com.vigo.driverlog.domain.EventType
import com.vigo.driverlog.domain.Formatters
import com.vigo.driverlog.domain.LocationSource
import com.vigo.driverlog.ui.common.EventBadge
import com.vigo.driverlog.ui.common.SectionCard
import com.vigo.driverlog.ui.theme.VigoColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun HistoryScreen(onOpenEvent: (String) -> Unit, vm: HistoryViewModel = viewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        DayBar(st.day, onShift = vm::shift, onPick = vm::setDay)
        if (st.pendingAddresses > 0) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${st.pendingAddresses} bản ghi đang chờ mạng để xác định địa chỉ.", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = vm::retryAddresses) { Text("Thử lại") }
                }
            }
        }
        if (st.events.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Không có sự kiện nào được ghi nhận trong ngày ${Formatters.date(st.day)}.", textAlign = TextAlign.Center, color = VigoColors.Muted)
            }
        } else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("${st.events.size} sự kiện · ${st.events.mapNotNull { it.tripId }.distinct().size} chuyến đi", color = VigoColors.Muted, style = MaterialTheme.typography.bodyMedium)
            }
            items(st.events, key = { it.id }) { e -> EventRow(e, st.settings.use24h) { onOpenEvent(e.id) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayBar(day: LocalDate, onShift: (Long) -> Unit, onPick: (LocalDate) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShift(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Ngày trước") }
            Row(Modifier.weight(1f).clickable { picking = true }.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.CalendarMonth, null, tint = VigoColors.Blue)
                Spacer(Modifier.width(8.dp))
                Text(if (day == today) "Hôm nay, ${Formatters.date(day)}" else Formatters.weekdayDate(day), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
            IconButton(onClick = { onShift(1) }, enabled = day < today) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Ngày sau") }
        }
    }
    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                Button(onClick = {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text("Chọn") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Hủy") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun EventRow(e: EventEntity, use24h: Boolean, onClick: () -> Unit) {
    val type = EventType.from(e.eventType)
    SectionCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.Top) {
            EventBadge(type, 40)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Formatters.time(e.eventTimestamp, use24h), fontWeight = FontWeight.Bold, color = VigoColors.Navy)
                    Spacer(Modifier.width(8.dp))
                    Text(type.label, fontWeight = FontWeight.Bold, color = VigoColors.forType(type))
                }
                Text(addressText(e), style = MaterialTheme.typography.bodyMedium)
                if (e.latitude != null && e.longitude != null) {
                    Text("GPS: ${Formatters.coords(e.latitude, e.longitude)}" + (e.accuracy?.let { " · ${Formatters.accuracy(it)}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = VigoColors.Muted)
                }
                val flags = buildList {
                    when (LocationSource.from(e.locationSource)) {
                        LocationSource.MANUAL -> add("Nhập thủ công")
                        LocationSource.GPS_CACHED -> add("Vị trí cache")
                        else -> {}
                    }
                    if (e.lowAccuracy) add("Độ chính xác thấp")
                    if (e.clockWarning != null) add("Cảnh báo đồng hồ")
                }
                if (flags.isNotEmpty()) Text("⚠ " + flags.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = VigoColors.Orange)
                e.note?.let { Text("Ghi chú: $it", style = MaterialTheme.typography.bodySmall, color = VigoColors.Text) }
            }
        }
        Spacer(Modifier.size(2.dp))
    }
}

fun addressText(e: EventEntity): String = when (AddressStatus.from(e.addressStatus)) {
    AddressStatus.RESOLVED, AddressStatus.CORRECTED, AddressStatus.MANUAL -> e.address ?: "Chưa xác định địa chỉ"
    AddressStatus.PENDING -> "Chưa xác định địa chỉ (đang chờ mạng)"
    AddressStatus.NOT_FOUND -> "Chưa xác định địa chỉ"
    AddressStatus.NONE -> "Không có vị trí"
}

