@file:OptIn(ExperimentalMaterial3Api::class)

package com.vx7.marketing

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: Vm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifier.ensureChannel(this)
        if (savedInstanceState == null) intent?.getStringExtra(Notifier.EXTRA_ROUTE)?.let { vm.pendingRoute = it }
        setContent { App(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Notifier.EXTRA_ROUTE)?.let { vm.pendingRoute = it }
    }
}

private class NavItem(val key: String, val label: String, val icon: ImageVector)

private val NAV_ITEMS = listOf(
    NavItem("home", "Dashboard", Icons.Filled.Dashboard),
    NavItem("products", "Products", Icons.Filled.Inventory2),
    NavItem("normal", "Normal List", Icons.AutoMirrored.Filled.List),
    NavItem("pvt", "Pvt List", Icons.Filled.Lock),
    NavItem("form", "New Collection", Icons.Filled.CreateNewFolder),
    NavItem("history", "Collection History", Icons.Filled.History),
    NavItem("notes", "Notes", Icons.Filled.StickyNote2),
    NavItem("reports", "Reports", Icons.Filled.BarChart),
    NavItem("notifications", "Notifications", Icons.Filled.Notifications),
    NavItem("backup", "Backup & Restore", Icons.Filled.Backup),
    NavItem("settings", "Settings", Icons.Filled.Settings),
    NavItem("about", "About VX7 Marketing", Icons.Filled.Info)
)

/** Which drawer item should be highlighted for a given route (sub-screens highlight their parent). */
private fun selectedKey(route: String): String = when {
    route.startsWith("product:") || route.startsWith("productform:") -> "products"
    route.startsWith("detail:") -> "history"
    route == "pvtreport" -> "reports"
    else -> route
}

@Composable
fun App(vm: Vm) {
    val dark = when (vm.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    AppTheme(dark) {
        // The back stack is a "|"-joined string so it survives rotation and process recreation.
        var stackStr by rememberSaveable { mutableStateOf("home") }
        val stack = stackStr.split("|")
        val route = stack.last()
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        fun go(r: String) { if (r != route) stackStr = "$stackStr|$r" }
        fun back() { if (stack.size > 1) stackStr = stack.dropLast(1).joinToString("|") }
        fun replace(r: String) { stackStr = (stack.dropLast(1) + r).joinToString("|") }
        /** Drawer navigation: always "Dashboard > screen" so the back stack never grows or duplicates. */
        fun navTop(r: String) {
            scope.launch { drawerState.close() }
            if (r == "form") vm.newDraft()
            if (r == route && r != "form") return
            stackStr = if (r == "home") "home" else "home|$r"
        }
        fun toggleDrawer() { scope.launch { if (drawerState.isOpen) drawerState.close() else drawerState.open() } }

        BackHandler(enabled = !drawerState.isClosed) { scope.launch { drawerState.close() } }
        BackHandler(enabled = drawerState.isClosed && stack.size > 1) { back() }

        // Notification taps: open the target screen once.
        val pending = vm.pendingRoute
        LaunchedEffect(pending) {
            if (pending != null) {
                vm.pendingRoute = null
                val valid = pending.startsWith("detail:") || pending.startsWith("product:") || pending.startsWith("entry:") || NAV_ITEMS.any { it.key == pending }
                if (valid) stackStr = if (pending == "home") "home" else "home|$pending"
            }
        }
        LifecycleResumeEffect(Unit) { vm.refreshNotif(); onPauseOrDispose {} }

        // Ask for notification permission once (Android 13+). Denial is fine: history still works.
        val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refreshNotif() }
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33 && !Notifier.permissionGranted(vm.getApplication<android.app.Application>()) && !vm.askedNotifPermission()) {
                vm.markAskedNotifPermission()
                permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val snack = remember { SnackbarHostState() }
        LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message = null } }

        val title = when {
            route == "home" -> "VX7 Marketing"
            route == "products" -> "Products"
            route.startsWith("product:") -> "Product Details"
            route.startsWith("productform:") -> if (route.endsWith(":0")) "Add Product" else "Edit Product"
            route == "normal" -> "Normal List"
            route == "pvt" -> "Pvt List"
            route.startsWith("entry:") -> "Item Details"
            route == "form" -> if (vm.editingId == 0L) "New Collection" else "Edit Collection"
            route.startsWith("detail:") -> "Collection Details"
            route == "history" -> "Collection History"
            route == "notes" -> "Notes"
            route == "reports" -> "Reports"
            route == "pvtreport" -> "Pvt List Summary"
            route == "notifications" -> "Notifications"
            route == "backup" -> "Backup & Restore"
            route == "settings" -> "Settings"
            route == "about" -> "About VX7 Marketing"
            else -> "VX7 Marketing"
        }
        val isTop = NAV_ITEMS.any { it.key == route } && route != "form"

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = true,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = Navy) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
                        Row(Modifier.statusBarsPadding().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.foundation.Image(painterResource(R.drawable.vx7_logo), null, Modifier.size(44.dp))
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("VX7 Marketing", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("Product \u2022 Rate \u2022 Quantity", color = Gold, fontSize = 11.sp)
                            }
                        }
                        HorizontalDivider(color = Gold.copy(alpha = 0.4f))
                        Spacer(Modifier.height(6.dp))
                        val sel = selectedKey(route)
                        NAV_ITEMS.forEach { item ->
                            NavigationDrawerItem(
                                label = { Text(item.label) },
                                icon = { Icon(item.icon, null) },
                                selected = item.key == sel,
                                onClick = { navTop(item.key) },
                                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = ElectricBlue.copy(alpha = 0.28f), selectedIconColor = Gold, selectedTextColor = androidx.compose.ui.graphics.Color.White,
                                    unselectedIconColor = androidx.compose.ui.graphics.Color(0xFFB8C2DD), unselectedTextColor = androidx.compose.ui.graphics.Color(0xFFB8C2DD)
                                )
                            )
                        }
                    }
                }
            }
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = { Bar(title, if (isTop) ({ toggleDrawer() }) else null, if (!isTop && stack.size > 1) ({ back() }) else null) },
                snackbarHost = { SnackbarHost(snack) }
            ) { pad ->
                when {
                    route == "home" -> HomeScreen(vm, pad, ::go)
                    route == "products" -> ProductsScreen(vm, pad, ::go)
                    route.startsWith("product:") -> ProductDetailScreen(vm, route.substringAfter(":").toLongOrNull() ?: 0L, pad, ::go, ::back)
                    route.startsWith("productform:") -> ProductFormScreen(vm, route.substringAfter(":").toLongOrNull() ?: 0L, pad, ::back)
                    route == "normal" -> ListScreen(vm, NORMAL, pad, ::go)
                    route == "pvt" -> ListScreen(vm, PVT, pad, ::go)
                    route.startsWith("entry:") -> EntryDetailScreen(vm, route.substringAfter(":").toLongOrNull() ?: 0L, pad, ::back)
                    route == "form" -> FormScreen(vm, pad, ::back, ::replace)
                    route.startsWith("detail:") -> DetailScreen(vm, route.substringAfter(":").toLongOrNull() ?: 0L, pad, ::go, ::back)
                    route == "history" -> HistoryScreen(vm, pad, ::go)
                    route == "notes" -> NotesScreen(vm, pad)
                    route == "reports" -> ReportsScreen(vm, pad, ::go)
                    route == "pvtreport" -> PvtReportScreen(vm, pad)
                    route == "notifications" -> NotificationsScreen(vm, pad, ::go)
                    route == "backup" -> BackupScreen(vm, pad)
                    route == "settings" -> SettingsScreen(vm, pad, ::go)
                    route == "about" -> AboutScreen(pad)
                    else -> HomeScreen(vm, pad, ::go)
                }
            }
        }
    }
}
