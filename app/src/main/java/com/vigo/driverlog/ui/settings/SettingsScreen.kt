package com.vigo.driverlog.ui.settings

import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vigo.driverlog.BuildConfig
import com.vigo.driverlog.appContainer
import com.vigo.driverlog.data.backup.BackupException
import com.vigo.driverlog.data.db.AppDatabase
import com.vigo.driverlog.data.settings.AppSettings
import com.vigo.driverlog.domain.DistanceUnit
import com.vigo.driverlog.domain.Formatters
import com.vigo.driverlog.ui.common.LabelValue
import com.vigo.driverlog.ui.common.SectionCard
import com.vigo.driverlog.ui.theme.VigoColors
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.appContainer
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val eventCount = c.trips.eventCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val tripCount = c.trips.tripCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    private val _msg = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages = _msg.asSharedFlow()

    fun saveProfile(d: String, p: String, co: String) = viewModelScope.launch {
        if (d.isBlank() || p.isBlank()) { _msg.tryEmit("Tên lái xe và biển số xe không được để trống."); return@launch }
        c.settings.saveProfile(d, p, co); _msg.tryEmit("Đã lưu thông tin lái xe và phương tiện.")
    }
    fun set24h(v: Boolean) = viewModelScope.launch { c.settings.setUse24h(v) }
    fun setUnit(v: DistanceUnit) = viewModelScope.launch { c.settings.setDistanceUnit(v) }
    fun setOnlineMap(v: Boolean) = viewModelScope.launch { c.settings.setOnlineMap(v) }

    fun backup(uri: Uri) = viewModelScope.launch {
        try {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { c.backup.exportJson(it) } ?: error("Không mở được file")
            _msg.tryEmit("Đã sao lưu toàn bộ dữ liệu.")
        } catch (e: Exception) { _msg.tryEmit("Sao lưu thất bại: ${e.message}") }
    }

    fun exportCsv(uri: Uri) = viewModelScope.launch {
        try {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { c.backup.exportCsv(it) } ?: error("Không mở được file")
            _msg.tryEmit("Đã xuất dữ liệu CSV.")
        } catch (e: Exception) { _msg.tryEmit("Xuất CSV thất bại: ${e.message}") }
    }

    fun restore(uri: Uri) = viewModelScope.launch {
        try {
            val r = getApplication<Application>().contentResolver.openInputStream(uri)?.use { c.backup.importJson(it) } ?: error("Không mở được file")
            _msg.tryEmit("Khôi phục xong: thêm ${r.trips} chuyến, ${r.events} sự kiện, ${r.logs} dòng nhật ký. Bỏ qua ${r.skipped} bản ghi đã có.")
        } catch (e: BackupException) { _msg.tryEmit(e.message ?: "File không hợp lệ.")
        } catch (e: Exception) { _msg.tryEmit("Khôi phục thất bại: ${e.message}") }
    }

    fun clearMapCache() { c.mapRenderer.clearCache(); _msg.tryEmit("Đã xóa bộ nhớ đệm bản đồ.") }

    fun storageInfo(): Pair<Long, Long> {
        val app = getApplication<Application>()
        val dbFile = app.getDatabasePath(AppDatabase.NAME)
        val dbSize = listOf("", "-wal", "-shm").sumOf { java.io.File(dbFile.path + it).takeIf { f -> f.exists() }?.length() ?: 0L }
        return dbSize to c.reports.freeBytes()
    }
}

@Composable
fun SettingsScreen(showMessage: (String) -> Unit, vm: SettingsViewModel = viewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val events by vm.eventCount.collectAsStateWithLifecycle()
    val trips by vm.tripCount.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.messages.collect { showMessage(it) } }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::backup) }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let(vm::exportCsv) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::restore) }
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    var permTick by rememberSaveable { mutableStateOf(0) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permTick++ }

    val current = s ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProfileCard(current, vm)

        SectionCard {
            Text("Hiển thị", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Định dạng giờ 24h", Modifier.weight(1f))
                Switch(checked = current.use24h, onCheckedChange = vm::set24h)
            }
            Text("Đơn vị khoảng cách", Modifier.padding(top = 8.dp))
            DistanceUnit.entries.forEach { u ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = current.distanceUnit == u, onClick = { vm.setUnit(u) })
                    Text(u.label)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Nền bản đồ trong báo cáo")
                    Text("Tải ô bản đồ OpenStreetMap khi có mạng (gửi khu vực tọa độ tới máy chủ bản đồ). Tắt: chỉ vẽ sơ đồ theo tọa độ.",
                        style = MaterialTheme.typography.bodySmall, color = VigoColors.Muted)
                }
                Switch(checked = current.onlineMapInReport, onCheckedChange = vm::setOnlineMap)
            }
        }

        SectionCard {
            Text("Quyền GPS", style = MaterialTheme.typography.titleMedium)
            val loc = context.appContainer.location
            val fine = remember(permTick) { loc.hasFinePermission() }
            val any = remember(permTick) { loc.hasPermission() }
            val enabled = remember(permTick) { loc.isLocationEnabled() }
            LabelValue("Quyền vị trí", when {
                fine -> "Đã cấp (chính xác)"
                any -> "Chỉ vị trí gần đúng"
                else -> "Chưa cấp"
            }, if (fine) VigoColors.Green else VigoColors.Red)
            LabelValue("Định vị trên máy", if (enabled) "Đang bật" else "Đang tắt", if (enabled) VigoColors.Green else VigoColors.Red)
            TextButton(onClick = { permTick++ }) { Text("Kiểm tra lại") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { permLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text("Yêu cầu quyền") }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Cài đặt ứng dụng") }
            }
        }

        SectionCard {
            Text("Lưu trữ & sao lưu", style = MaterialTheme.typography.titleMedium)
            val (dbSize, free) = vm.storageInfo()
            LabelValue("Số chuyến đi", "$trips")
            LabelValue("Số sự kiện", "$events")
            LabelValue("Dung lượng dữ liệu", "${dbSize / 1024} KB")
            LabelValue("Bộ nhớ trống", if (free == Long.MAX_VALUE) "Không xác định" else "${free / (1024 * 1024)} MB", if (free < 100L * 1024 * 1024) VigoColors.Orange else VigoColors.Text)
            Text("Dữ liệu chỉ lưu trên điện thoại này. Nên sao lưu định kỳ ra file và gửi lên Google Drive/Zalo để phòng mất máy.",
                style = MaterialTheme.typography.bodySmall, color = VigoColors.Muted)
            Spacer(Modifier.size(6.dp))
            Button(onClick = { backupLauncher.launch("VIGO_DriverLog_saoluu_${Formatters.fileDate(LocalDate.now())}.json") }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("SAO LƯU DỮ LIỆU") }
            OutlinedButton(onClick = { confirmRestore = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("KHÔI PHỤC TỪ FILE SAO LƯU") }
            OutlinedButton(onClick = { csvLauncher.launch("VIGO_DriverLog_${Formatters.fileDate(LocalDate.now())}.csv") }, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("XUẤT DỮ LIỆU CSV (EXCEL)") }
            TextButton(onClick = vm::clearMapCache) { Text("Xóa bộ nhớ đệm bản đồ") }
        }

        SectionCard {
            Text("Thông tin ứng dụng", style = MaterialTheme.typography.titleMedium)
            LabelValue("Phiên bản", BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")")
            Text("Ứng dụng không tự gửi vị trí lên máy chủ. Mạng chỉ dùng để chuyển tọa độ thành địa chỉ (dịch vụ Geocoder của Android) và tải nền bản đồ cho ảnh báo cáo.",
                style = MaterialTheme.typography.bodySmall, color = VigoColors.Muted)
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Khôi phục dữ liệu") },
            text = { Text("Dữ liệu trong file sẽ được GỘP vào dữ liệu hiện có. Bản ghi đã tồn tại được giữ nguyên, không bị ghi đè hay nhân đôi.") },
            confirmButton = { Button(onClick = { confirmRestore = false; restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }) { Text("CHỌN FILE") } },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Hủy") } },
        )
    }
}

@Composable
private fun ProfileCard(s: AppSettings, vm: SettingsViewModel) {
    var driver by rememberSaveable(s.driverName) { mutableStateOf(s.driverName) }
    var plate by rememberSaveable(s.vehiclePlate) { mutableStateOf(s.vehiclePlate) }
    var company by rememberSaveable(s.companyName) { mutableStateOf(s.companyName) }
    SectionCard {
        Text("Lái xe & phương tiện", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(6.dp))
        OutlinedTextField(driver, { driver = it.take(60) }, Modifier.fillMaxWidth(), label = { Text("Tên lái xe *") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        OutlinedTextField(plate, { plate = it.take(20) }, Modifier.fillMaxWidth(), label = { Text("Biển số xe * (VD: 30A-123.45)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
        OutlinedTextField(company, { company = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Tên công ty (không bắt buộc)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        Spacer(Modifier.size(8.dp))
        val changed = driver != s.driverName || plate != s.vehiclePlate || company != s.companyName
        Button(onClick = { vm.saveProfile(driver, plate, company) }, enabled = changed, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("LƯU THÔNG TIN") }
        Text("Thông tin mới áp dụng cho các chuyến đi bắt đầu sau khi lưu.", style = MaterialTheme.typography.bodySmall, color = VigoColors.Muted)
    }
}

