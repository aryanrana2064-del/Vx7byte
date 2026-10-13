@file:OptIn(ExperimentalMaterial3Api::class)

package com.vx7.marketing

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

// ===================================================================== NOTES
@Composable
fun NoteDialog(vm: Vm, old: QuickNote?, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var t by remember { mutableStateOf(old?.title.orEmpty()) }
    var b by remember { mutableStateOf(old?.body.orEmpty()) }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (old == null) "New note" else "Edit note") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(t, { t = it.take(100) }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(b, { b = it.take(5000) }, label = { Text("Note") }, minLines = 5, modifier = Modifier.fillMaxWidth())
                if (old != null) Text("Created ${fmtStamp(old.createdAt)}\nUpdated ${fmtStamp(old.updatedAt)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ErrorText(err)
            }
        },
        confirmButton = { TextButton(onClick = { scope.launch { val e = vm.saveNote(old, t, b); if (e == null) onClose() else err = e } }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}

@Composable
fun NotesScreen(vm: Vm, pad: PaddingValues) {
    val all by vm.notes.collectAsStateWithLifecycle()
    var q by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<QuickNote?>(null) }
    var adding by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf<QuickNote?>(null) }
    val scope = rememberCoroutineScope()
    ScreenBox {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.padding(top = pad.calculateTopPadding() + 8.dp, start = 16.dp, end = 16.dp)) { SearchField(q, { q = it }, "Search notes") }
            val list = all
            if (list == null) Loading() else {
                val shown = remember(list, q) { list.filter { q.isBlank() || it.title.contains(q.trim(), true) || it.body.contains(q.trim(), true) } }
                if (shown.isEmpty()) Empty(Icons.Filled.StickyNote2, if (list.isEmpty()) "No notes yet.\nTap + to write one." else "No notes match your search.")
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, pad.calculateBottomPadding() + 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.id }) { n ->
                        PremiumCard(Modifier.fillMaxWidth(), onClick = { editing = n }) {
                            Row(verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    Text(n.title.ifBlank { "(No title)" }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (n.body.isNotBlank()) Text(n.body, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Created ${fmtStamp(n.createdAt)}  \u2022  Updated ${fmtStamp(n.updatedAt)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                                }
                                IconButton(onClick = { scope.launch { vm.togglePin(n) } }) { Icon(Icons.Filled.PushPin, if (n.pinned) "Unpin" else "Pin", tint = if (n.pinned) Gold else MaterialTheme.colorScheme.onSurfaceVariant) }
                                IconButton(onClick = { del = n }) { Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = pad.calculateBottomPadding() + 20.dp), containerColor = ElectricBlue, contentColor = Color.White) { Icon(Icons.Filled.Add, "New note") }
    }
    if (adding) NoteDialog(vm, null) { adding = false }
    editing?.let { n -> NoteDialog(vm, n) { editing = null } }
    del?.let { n -> Confirm("Delete note?", "\"${n.title.ifBlank { "This note" }}\" will be deleted.", "Delete", true, { del = null; scope.launch { vm.deleteNote(n) } }, { del = null }) }
}

// ===================================================================== REPORTS
@Composable
private fun SummaryTable(rows: List<List<String>>, empty: String) {
    if (rows.isEmpty()) { Text(empty, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    rows.forEach { r ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            r.forEachIndexed { i, c -> Text(c, Modifier.weight(if (i == 0) 1.6f else 1f), fontSize = 13.sp, textAlign = if (i == 0) TextAlign.Start else TextAlign.End, fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
fun ReportsScreen(vm: Vm, pad: PaddingValues, go: (String) -> Unit) {
    val from = vm.rep.from
    val to = vm.rep.to
    val recCount by remember(from, to) { vm.db.records().recordCount(from, to) }.collectAsStateWithLifecycle(null)
    val entCount by remember(from, to) { vm.db.records().entryCount(from, to) }.collectAsStateWithLifecycle(null)
    val byUnit by remember(from, to) { vm.db.records().qtyByUnit(from, to) }.collectAsStateWithLifecycle(null)
    val byProduct by remember(from, to) { vm.db.records().qtyByProduct(from, to) }.collectAsStateWithLifecycle(null)
    val recent by remember(from, to) { vm.db.records().search("", from, to, 8) }.collectAsStateWithLifecycle(null)
    val normal by vm.normalList.collectAsStateWithLifecycle()
    ScreenBox {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPad(pad)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PeriodBar(vm.rep)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Collections", recCount?.toString() ?: "-", Icons.Filled.CreateNewFolder, Modifier.weight(1f))
                StatCard("Product entries", entCount?.toString() ?: "-", Icons.Filled.CheckCircle, Modifier.weight(1f))
            }
            ExportButtons(vm, "VX7_Activity_Report", { vm.activityDoc(vm.rep) })
            SectionTitle("UNIT-WISE QUANTITY (never added across units)")
            PremiumCard(Modifier.fillMaxWidth()) {
                val u = byUnit
                if (u == null) Loading() else SummaryTable(u.map { listOf(it.unit, fmtQty(it.total), "${it.cnt} entries") }, "No product entries in this period.")
            }
            SectionTitle("PRODUCT-WISE QUANTITY")
            PremiumCard(Modifier.fillMaxWidth()) {
                val p = byProduct
                if (p == null) Loading() else SummaryTable(p.map { listOf(it.name, "${fmtQty(it.total)} ${it.unit}", "${it.cnt}x") }, "No product entries in this period.")
            }
            SectionTitle("RECENT ACTIVITY")
            PremiumCard(Modifier.fillMaxWidth()) {
                val r = recent
                if (r == null) Loading() else if (r.isEmpty()) Text("No collections in this period.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else r.forEach { row ->
                    Row(Modifier.fillMaxWidth().clickable { go("detail:${row.record.id}") }.padding(vertical = 6.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(row.record.title.ifBlank { row.record.shop.ifBlank { "Collection" } }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(fmtStamp(row.record.createdAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${row.entryCount} items", fontSize = 12.sp)
                    }
                }
            }
            SectionTitle("NORMAL LIST SUMMARY")
            PremiumCard(Modifier.fillMaxWidth()) {
                val n = normal
                if (n == null) Loading() else {
                    Text("${n.size} entries", fontWeight = FontWeight.Bold)
                    SummaryTable(vm.unitTotals(n).map { listOf(it.first, fmtQty(it.second), "${it.third} entries") }, "The Normal List is empty.")
                }
            }
            SectionTitle("PRIVATE")
            OutlinedButton(onClick = { go("pvtreport") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Lock, null, tint = Gold); Spacer(Modifier.width(8.dp)); Text("Pvt List Summary (private, separate)") }
        }
    }
}

@Composable
fun PvtReportScreen(vm: Vm, pad: PaddingValues) {
    val pvt by vm.pvtList.collectAsStateWithLifecycle()
    ScreenBox {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPad(pad)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x33B3261E)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, tint = Gold); Spacer(Modifier.width(8.dp))
                Text("Private report. It shows Pvt Rates and is never included in normal reports or exports.", fontSize = 12.sp)
            }
            val l = pvt
            if (l == null) Loading() else {
                PremiumCard(Modifier.fillMaxWidth()) {
                    Text("${l.size} Pvt List entries", fontWeight = FontWeight.Bold)
                    SummaryTable(vm.unitTotals(l).map { listOf(it.first, fmtQty(it.second), "${it.third} entries") }, "The Pvt List is empty.")
                }
                SectionTitle("ENTRIES")
                PremiumCard(Modifier.fillMaxWidth()) {
                    SummaryTable(l.map { listOf(it.name, "${fmtQty(it.quantity)} ${it.unit}", fmtRate(it.pvtRate)) }, "No entries.")
                }
                ExportButtons(vm, "VX7_Pvt_Summary", { vm.pvtSummaryDoc() }, isPrivate = true)
            }
        }
    }
}

// ===================================================================== NOTIFICATIONS
private fun iconFor(type: String): ImageVector = when (type) {
    "PRODUCT" -> Icons.Filled.Inventory2
    "COLLECTION" -> Icons.Filled.CreateNewFolder
    "BACKUP" -> Icons.Filled.Backup
    "LIST" -> Icons.AutoMirrored.Filled.List
    else -> Icons.Filled.Notifications
}

@Composable
fun NotificationsScreen(vm: Vm, pad: PaddingValues, go: (String) -> Unit) {
    val ctx = LocalContext.current
    val log by vm.log.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var clear by remember { mutableStateOf(false) }
    val req = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.refreshNotif()
        vm.message = if (granted) "Notifications allowed." else "Permission denied. The app still works; entries are kept in this history only."
    }
    ScreenBox {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(top = pad.calculateTopPadding() + 8.dp, start = 16.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PremiumCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (vm.notifOk) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff, null, tint = if (vm.notifOk) Gold else MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(10.dp))
                        Text(if (vm.notifOk) "System notifications are on." else "System notifications are off. Activity is still saved in this history.", fontSize = 13.sp)
                    }
                    if (!vm.notifOk) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (Build.VERSION.SDK_INT >= 33 && !Notifier.permissionGranted(ctx))
                            Button(onClick = { req.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow") }
                        OutlinedButton(onClick = {
                            try { ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)) } catch (e: Exception) { vm.message = "Could not open system settings." }
                        }) { Text("System settings") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("History", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(enabled = !log.isNullOrEmpty(), onClick = { clear = true }) { Icon(Icons.Filled.DeleteSweep, null); Spacer(Modifier.width(4.dp)); Text("Clear history") }
                }
            }
            val l = log
            if (l == null) Loading()
            else if (l.isEmpty()) Empty(Icons.Filled.Notifications, "No notifications yet.")
            else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, pad.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(l, key = { it.id }) { a ->
                    PremiumCard(Modifier.fillMaxWidth(), onClick = if (a.route.isNotBlank()) ({ go(a.route) }) else null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(iconFor(a.type), null, tint = ElectricBlue)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.message, fontSize = 14.sp)
                                Text(fmtStamp(a.createdAt) + if (a.delivered) "  \u2022  system notification shown" else "", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
    if (clear) Confirm("Clear history?", "All entries in the notification history will be removed. Your data is not affected.", "Clear", true, { clear = false; scope.launch { vm.clearLog() } }, { clear = false })
}

// ===================================================================== BACKUP & RESTORE
@Composable
fun BackupScreen(vm: Vm, pad: PaddingValues) {
    val scope = rememberCoroutineScope()
    var includePrivate by rememberSaveable { mutableStateOf(false) }
    var askPrivate by remember { mutableStateOf(false) }
    var pendingPrivate by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var parsed by remember { mutableStateOf<Snapshot?>(null) }
    var mode by remember { mutableStateOf(RestoreMode.MERGE) }
    var confirmReplace by remember { mutableStateOf(false) }
    val stats by vm.stats.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { busy = true; status = vm.exportBackup(uri, pendingPrivate); busy = false }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try { parsed = vm.readBackup(uri); mode = RestoreMode.MERGE; status = null }
            catch (e: Exception) { status = "Restore failed: ${e.message ?: "could not read the file"}" }
            busy = false
        }
    }
    fun startExport() { pendingPrivate = includePrivate; exportLauncher.launch("VX7-Marketing-Backup-${fileStamp(System.currentTimeMillis())}${if (includePrivate) "-PRIVATE" else ""}.json") }

    ScreenBox {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPad(pad)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PremiumCard(Modifier.fillMaxWidth()) {
                Text("Create backup", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Saves products, Normal List, rates, collections and notes to a JSON file you choose. Nothing is uploaded anywhere.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = includePrivate, onCheckedChange = { includePrivate = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Include private data (Pvt List and Pvt Rates)", fontSize = 13.sp)
                }
                Button(enabled = !busy, onClick = { if (includePrivate) askPrivate = true else startExport() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Icon(Icons.Filled.Save, null); Spacer(Modifier.width(8.dp)); Text("Export backup") }
            }
            PremiumCard(Modifier.fillMaxWidth()) {
                Text("Restore backup", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Pick a VX7 backup file. You can merge it with your data or replace your data. The file is checked first; if anything is wrong nothing is changed.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
                OutlinedButton(enabled = !busy, onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Choose backup file") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            status?.let { PremiumCard(Modifier.fillMaxWidth()) { Text(it, fontSize = 13.sp) } }
        }
    }
    if (askPrivate) Confirm("Backup with private information", "This backup will contain your Pvt List and all Pvt Rates, and the file name will end in -PRIVATE. Keep it somewhere safe and do not share it.", "Create backup", true, { askPrivate = false; startExport() }, { askPrivate = false })

    parsed?.let { s ->
        AlertDialog(
            onDismissRequest = { parsed = null },
            title = { Text("Restore this backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Created ${fmtStamp(s.createdAt)}\n${s.products.size} products, ${s.listEntries.count { it.listType == NORMAL }} Normal List entries, ${s.listEntries.count { it.listType == PVT }} Pvt List entries, ${s.records.size} collections, ${s.notes.size} notes.", fontSize = 13.sp)
                    Text(if (s.includesPrivate) "This file contains private data." else "This file does not contain private data.", fontSize = 12.sp, color = Gold)
                    Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(mode == RestoreMode.MERGE, { mode = RestoreMode.MERGE }); Text("Merge: add what is missing, keep everything else", fontSize = 13.sp) }
                    Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(mode == RestoreMode.REPLACE, { mode = RestoreMode.REPLACE }); Text("Replace: delete current data first", fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (mode == RestoreMode.REPLACE) confirmReplace = true
                    else { val snap = s; parsed = null; scope.launch { busy = true; status = vm.restoreBackup(snap, RestoreMode.MERGE); busy = false } }
                }) { Text(if (mode == RestoreMode.REPLACE) "Replace..." else "Merge") }
            },
            dismissButton = { TextButton(onClick = { parsed = null }) { Text("Cancel") } }
        )
        if (confirmReplace) Confirm("Delete current data and replace?", "This permanently deletes ALL current data (${stats?.products ?: 0} products, ${stats?.normal ?: 0} Normal List and ${stats?.pvt ?: 0} Pvt List entries, ${stats?.collections ?: 0} collections and all notes) and replaces it with the backup." + (if (!s.includesPrivate) " This backup contains no private data, so your current Pvt List and all Pvt Rates will be lost." else "") + " Create a backup first if unsure.", "Replace all data", true,
            { confirmReplace = false; val snap = s; parsed = null; scope.launch { busy = true; status = vm.restoreBackup(snap, RestoreMode.REPLACE); busy = false } }, { confirmReplace = false })
    }
}

// ===================================================================== SETTINGS
fun appVersion(ctx: android.content.Context): String = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0.0" } catch (e: Exception) { "1.0.0" }

@Composable
fun SettingsScreen(vm: Vm, pad: PaddingValues, go: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var clear by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    ScreenBox {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPad(pad)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("THEME")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("dark" to "Dark", "light" to "Light", "system" to "System").forEach { (k, l) -> FilterChip(selected = vm.theme == k, onClick = { vm.changeTheme(k) }, label = { Text(l) }) }
            }
            SectionTitle("DEFAULT REPORT AND HISTORY RANGE")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "All dates", "today" to "Today", "week" to "Last 7 days", "month" to "This month").forEach { (k, l) -> FilterChip(selected = vm.defaultRange == k, onClick = { vm.changeDefaultRange(k) }, label = { Text(l) }) }
            }
            SectionTitle("DEFAULT UNIT")
            UnitMenu(vm.defaultUnit, { vm.changeDefaultUnit(it) }, Modifier.fillMaxWidth())
            SectionTitle("PRIVATE RATES")
            PremiumCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Ask before showing Pvt Rates", fontWeight = FontWeight.SemiBold)
                        Text("Pvt Rates stay hidden in product and collection screens until you reveal them. They also hide again when the app restarts.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = vm.pvtAsk, onCheckedChange = { vm.changePvtAsk(it) })
                }
                if (vm.pvtRevealed) TextButton(onClick = { vm.hidePvt() }) { Text("Hide Pvt Rates now") }
            }
            SectionTitle("NOTIFICATIONS")
            PremiumCard(Modifier.fillMaxWidth(), onClick = { go("notifications") }) {
                Text(if (vm.notifOk) "System notifications are on" else "System notifications are off", fontWeight = FontWeight.SemiBold)
                Text("Product, collection and backup confirmations. Tap to manage permission and view history.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionTitle("BACKUP")
            OutlinedButton(onClick = { go("backup") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Backup, null); Spacer(Modifier.width(8.dp)); Text("Backup & Restore") }
            SectionTitle("APP")
            PremiumCard(Modifier.fillMaxWidth()) { KeyValue("Version", appVersion(ctx)); KeyValue("Package", ctx.packageName) }
            SectionTitle("DANGER ZONE")
            OutlinedButton(onClick = { typed = ""; clear = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.DeleteForever, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(8.dp)); Text("Clear all data", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (clear) AlertDialog(
        onDismissRequest = { clear = false },
        title = { Text("Delete ALL data?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Products, both lists, collections, notes, photos and notification history will be permanently deleted. Backups you saved are not affected. Type DELETE to confirm.")
                OutlinedTextField(typed, { typed = it }, singleLine = true, label = { Text("Type DELETE") })
            }
        },
        confirmButton = { TextButton(enabled = typed == "DELETE", onClick = { clear = false; scope.launch { vm.clearAll() } }) { Text("Delete everything", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancel") } }
    )
}

// ===================================================================== ABOUT
@Composable
fun AboutScreen(pad: PaddingValues) {
    val ctx = LocalContext.current
    ScreenBox {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPad(pad)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(12.dp))
            Image(painterResource(R.drawable.vx7_logo), null, Modifier.size(110.dp).clip(RoundedCornerShape(26.dp)))
            Text("VX7 Marketing", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("PRODUCT \u2022 RATE \u2022 QUANTITY", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("Version ${appVersion(ctx)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            PremiumCard(Modifier.fillMaxWidth()) {
                Text("VX7 Marketing keeps your products, rates, quantities, collections and notes in one offline app. It has a Normal List and a separate private Pvt List, remembers the previous rate of every product, and creates PDF and PNG reports and JSON backups on your phone.", fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text("Everything is stored on this device. No account, no internet and no ads. It is not a billing or accounting app.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
