package com.vigo.driverlog.ui.report

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vigo.driverlog.domain.Formatters
import com.vigo.driverlog.ui.common.SectionCard
import com.vigo.driverlog.ui.common.WarningCard
import com.vigo.driverlog.ui.history.DayBar
import com.vigo.driverlog.ui.theme.VigoColors

@Composable
fun ReportScreen(showMessage: (String) -> Unit, vm: ReportViewModel = viewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Luôn dựng lại khi mở màn hình để báo cáo phản ánh dữ liệu mới nhất.
    LaunchedEffect(Unit) { vm.refresh() }

    val legacyPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.export() else showMessage("Cần quyền lưu trữ để lưu ảnh vào thư mục Pictures.")
    }
    val doExport = {
        if (Build.VERSION.SDK_INT < 29 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) legacyPerm.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else vm.export()
    }
    fun launch(intent: android.content.Intent?) {
        if (intent == null) return
        try { context.startActivity(intent) } catch (e: ActivityNotFoundException) { showMessage("Không tìm thấy ứng dụng phù hợp để mở ảnh.") }
    }

    Column(Modifier.fillMaxSize()) {
        DayBar(st.day, onShift = vm::shift, onPick = vm::setDay)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            st.error?.let { err -> item { WarningCard("Không tạo được báo cáo", err) { OutlinedButton(onClick = vm::refresh) { Text("THỬ LẠI") } } } }

            val exported = st.exported
            if (exported != null) {
                item {
                    SectionCard {
                        Text("Đã xuất báo cáo thành công", fontWeight = FontWeight.Bold, color = VigoColors.Green)
                        Text("${exported.fileNames.joinToString(", ")}\nThư mục: ${exported.folder}", style = MaterialTheme.typography.bodyMedium, color = VigoColors.Muted)
                        Spacer(Modifier.size(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { launch(vm.viewIntent()) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                                Icon(Icons.Filled.Image, null); Spacer(Modifier.width(6.dp)); Text("XEM ẢNH")
                            }
                            Button(onClick = { launch(vm.shareIntent()) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = VigoColors.Green)) {
                                Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text("CHIA SẺ")
                            }
                        }
                        Spacer(Modifier.size(8.dp))
                        OutlinedButton(onClick = vm::reExport, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), enabled = !st.loading && !st.saving) {
                            Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(6.dp)); Text("XUẤT LẠI")
                        }
                    }
                }
            } else if (st.previews.isNotEmpty()) {
                item {
                    Button(
                        onClick = doExport,
                        enabled = !st.loading && !st.saving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        if (st.saving) { CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 3.dp); Spacer(Modifier.width(10.dp)); Text("ĐANG LƯU ẢNH…") }
                        else { Icon(Icons.Filled.Save, null); Spacer(Modifier.width(10.dp)); Text("XUẤT ẢNH PNG & LƯU VÀO THƯ VIỆN") }
                    }
                }
            }

            if (st.loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.size(10.dp))
                            Text("Đang dựng báo cáo từ dữ liệu đã ghi nhận…", color = VigoColors.Muted, textAlign = TextAlign.Center)
                        }
                    }
                }
            } else if (st.model?.isEmpty == true) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Ngày ${Formatters.date(st.day)} không có dữ liệu ghi nhận nào.\nBáo cáo chỉ được tạo từ dữ liệu thật.", textAlign = TextAlign.Center, color = VigoColors.Muted)
                    }
                }
            }

            if (!st.loading && st.previews.isNotEmpty()) {
                item {
                    Text(
                        "Xem trước" + if (st.previews.size > 1) " (${st.previews.size} trang)" else "",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                itemsIndexed(st.previews) { _, bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Xem trước ảnh báo cáo",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height).border(1.dp, VigoColors.Line)
                    )
                }
            }
        }
    }
}
