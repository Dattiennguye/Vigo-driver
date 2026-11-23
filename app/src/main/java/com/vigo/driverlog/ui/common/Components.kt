package com.vigo.driverlog.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vigo.driverlog.domain.EventType
import com.vigo.driverlog.ui.theme.VigoColors

fun EventType.icon(): ImageVector = when (this) {
    EventType.START_TRIP -> Icons.Filled.PlayArrow
    EventType.ARRIVE -> Icons.Filled.Place
    EventType.PARK_START -> Icons.Filled.LocalParking
    EventType.PARK_END -> Icons.AutoMirrored.Filled.ExitToApp
    EventType.RESUME -> Icons.Filled.DirectionsCar
    EventType.END_TRIP -> Icons.Filled.Flag
    EventType.QUICK_LOG -> Icons.Filled.AddLocationAlt
}

/** Nhãn nút thao tác chính (chữ in hoa, đúng như đặc tả). */
fun EventType.buttonLabel(): String = when (this) {
    EventType.START_TRIP -> "BẮT ĐẦU ĐI"
    EventType.ARRIVE -> "ĐÃ ĐẾN NƠI"
    EventType.PARK_START -> "ĐỖ XE"
    EventType.PARK_END -> "RỜI ĐIỂM ĐỖ"
    EventType.RESUME -> "TIẾP TỤC ĐI"
    EventType.END_TRIP -> "KẾT THÚC CHUYẾN ĐI"
    EventType.QUICK_LOG -> "GHI NHẬN NHANH"
}

@Composable
fun EventBadge(type: EventType, size: Int = 40) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(VigoColors.forType(type)),
        contentAlignment = Alignment.Center
    ) {
        Icon(type.icon(), contentDescription = type.label, tint = Color.White, modifier = Modifier.size((size * 0.55).dp))
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun WarningCard(title: String, body: String, actions: @Composable ColumnScope.() -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E5)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, null, tint = VigoColors.Orange)
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, color = VigoColors.Text)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = VigoColors.Text)
            actions()
        }
    }
}

/** Nút thao tác lớn, dễ bấm bằng một tay (cao tối thiểu 68dp). */
@Composable
fun BigActionButton(
    type: EventType,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = VigoColors.forType(type), disabledContainerColor = VigoColors.forType(type).copy(alpha = 0.45f), disabledContentColor = Color.White),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(26.dp), color = Color.White, strokeWidth = 3.dp)
            Spacer(Modifier.width(12.dp))
            Text("ĐANG LẤY VỊ TRÍ GPS…", fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        } else {
            Icon(type.icon(), null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Text(type.buttonLabel(), fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun LabelValue(label: String, value: String, valueColor: Color = VigoColors.Text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(0.42f), color = VigoColors.Muted, style = MaterialTheme.typography.bodyMedium)
        Text(value, Modifier.weight(0.58f), color = valueColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
