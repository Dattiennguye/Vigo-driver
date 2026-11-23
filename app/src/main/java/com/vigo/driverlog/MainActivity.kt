package com.vigo.driverlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vigo.driverlog.ui.history.EventDetailScreen
import com.vigo.driverlog.ui.history.HistoryScreen
import com.vigo.driverlog.ui.home.HomeScreen
import com.vigo.driverlog.ui.report.ReportScreen
import com.vigo.driverlog.ui.settings.SettingsScreen
import com.vigo.driverlog.ui.theme.VigoColors
import com.vigo.driverlog.ui.theme.VigoTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VigoTheme { VigoAppRoot() } }
    }
}

private data class Tab(val route: String, val label: String, val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Hành trình", "VIGO Driver Log", Icons.Filled.DirectionsCar),
    Tab("history", "Lịch sử", "Lịch sử hành trình", Icons.Filled.History),
    Tab("report", "Báo cáo", "Báo cáo hình ảnh", Icons.Filled.Assessment),
    Tab("settings", "Cài đặt", "Cài đặt", Icons.Filled.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VigoAppRoot() {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { msg ->
        scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg, withDismissAction = true, duration = SnackbarDuration.Short) }
    }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val isDetail = route?.startsWith("event/") == true
    val tab = tabs.firstOrNull { it.route == route }

    fun go(r: String) = nav.navigate(r) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        topBar = {
            if (route != "home") {
                TopAppBar(
                    title = { Text(if (isDetail) "Chi tiết sự kiện" else tab?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        if (isDetail) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = Color.White) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = VigoColors.Navy, titleContentColor = Color.White),
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route || (isDetail && t.route == "history"),
                        onClick = { go(t.route) },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = VigoColors.Blue, selectedTextColor = VigoColors.Blue, indicatorColor = Color(0xFFDCEAFA)),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = VigoColors.Background,
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") { HomeScreen(onOpenSettings = { go("settings") }, onOpenEvent = { nav.navigate("event/$it") }, showMessage = showMessage) }
            composable("history") { HistoryScreen(onOpenEvent = { nav.navigate("event/$it") }) }
            composable("report") { ReportScreen(showMessage) }
            composable("settings") { SettingsScreen(showMessage) }
            composable("event/{eventId}", arguments = listOf(navArgument("eventId") { type = NavType.StringType })) { EventDetailScreen(showMessage) }
        }
    }
}
