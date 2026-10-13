package com.vx7.marketing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ------------------------------------------------------------------ Record details
@Composable
fun DetailScreen(vm: Vm, id: Long, pad: PaddingValues, go: (String) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val rec by remember(id) { vm.db.records().recordFlow(id) }.collectAsStateWithLifecycle(null)
    val es by remember(id) { vm.db.records().entriesFlow(id) }.collectAsStateWithLifecycle(emptyList())
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val r = rec
    if (r == null) {
        Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { Text("Record not found.") }
        return
    }
    LazyColumn(contentPadding = contentPad(pad), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(fmtLongDate(r.createdAt), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(fmtTime(r.createdAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (r.shop.isNotBlank()) Text("Shop: ${r.shop}")
                    if (r.location.isNotBlank()) Text("Location: ${r.location}")
                }
            }
        }
        item { SectionTitle("PRODUCTS (${es.size})") }
        itemsIndexedCompat(es) { i, e ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}.", Modifier.width(32.dp), fontWeight = FontWeight.Bold)
                    Column(Modifier.weight(1f)) {
                        Text(e.productName, fontWeight = FontWeight.SemiBold)
                        if (e.note.isNotBlank()) Text(e.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${fmtQty(e.quantity)} ${e.unit}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (r.note.isNotBlank()) {
            item { SectionTitle("NOTES") }
            item { Text(r.note) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = { doShare(scope, ctx, vm, id, true) { busy = it } }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PictureAsPdf, null); Text("  Share PDF") }
                Button(enabled = !busy, onClick = { doShare(scope, ctx, vm, id, false) { busy = it } }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Image, null); Text("  Share Image") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { scope.launch { if (vm.loadDraft(id)) go("form") } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Edit, null); Text("  Edit Record") }
            OutlinedButton(onClick = { vm.newDraft(); go("form") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("  Create New Record") }
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error); Text("  Delete Record", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmDelete) Confirm("Delete this record?", "This record and its product entries will be permanently deleted.", "Delete", true,
        { confirmDelete = false; scope.launch { if (vm.deleteRecord(id)) { vm.message = "Record deleted."; back() } } }, { confirmDelete = false })
}

private fun <T> androidx.compose.foundation.lazy.LazyListScope.itemsIndexedCompat(list: List<T>, content: @Composable (Int, T) -> Unit) {
    items(list.size) { i -> content(i, list[i]) }
}

// ------------------------------------------------------------------ History
@Composable
fun HistoryScreen(vm: Vm, pad: PaddingValues, go: (String) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    val ps = vm.hist
    val rows by remember(q, ps.from, ps.to) { vm.db.records().search(q.trim(), ps.from, ps.to, Int.MAX_VALUE) }.collectAsStateWithLifecycle(emptyList())
    var deleting by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    LazyColumn(contentPadding = contentPad(pad), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            OutlinedTextField(q, { q = it }, label = { Text("Search shop or product") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            PeriodBar(ps)
            Text("${rows.size} record(s)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (rows.isEmpty()) item { Empty(Icons.Default.History, "No records match. Try a different search or date filter.") }
        items(rows, key = { it.record.id }) { r ->
            RecordCard(r, { go("detail:${r.record.id}") }, RecordActions(vm, r.record.id, go) { deleting = it })
        }
    }
    deleting?.let { id ->
        Confirm("Delete this record?", "This record and its product entries will be permanently deleted.", "Delete", true,
            { deleting = null; scope.launch { if (vm.deleteRecord(id)) vm.message = "Record deleted." } }, { deleting = null })
    }
}

// ------------------------------------------------------------------ Reports (no money, units never combined)
@Composable
fun ReportsScreen(vm: Vm, pad: PaddingValues, go: (String) -> Unit) {
    val ps = vm.rep
    val rows by remember(ps.from, ps.to) { vm.db.records().search("", ps.from, ps.to, Int.MAX_VALUE) }.collectAsStateWithLifecycle(emptyList())
    val ents by remember(ps.from, ps.to) { vm.db.records().entryRows(ps.from, ps.to) }.collectAsStateWithLifecycle(emptyList())
    var deleting by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val distinct = remember(ents) { ents.map { normName(it.entry.productName) }.toSet().size }
    val byDay = remember(rows) { rows.groupBy { fmtDate(it.record.createdAt) }.map { (d, l) -> Triple(d, l.size, l.sumOf { it.entryCount }) } }
    val byShop = remember(rows) {
        rows.filter { it.record.shop.isNotBlank() }.groupBy { it.record.shop.trim().lowercase() }
            .map { (_, l) -> Triple(l.first().record.shop.trim(), l.size, l.sumOf { it.entryCount }) }.sortedByDescending { it.second }
    }
    val totals = remember(ents) {
        ents.groupBy { normName(it.entry.productName) + "|" + it.entry.unit.trim().lowercase() }
            .map { (_, l) -> Triple(l.first().entry.productName, l.first().entry.unit, l.sumOf { it.entry.quantity }) }
            .sortedWith(compareBy<Triple<String, String, Double>>({ it.first.lowercase() }, { it.second.lowercase() }))
    }
    LazyColumn(contentPadding = contentPad(pad), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            PeriodBar(ps)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Records", rows.size.toString(), Modifier.weight(1f))
                Stat("Products", distinct.toString(), Modifier.weight(1f))
                Stat("Entries", ents.size.toString(), Modifier.weight(1f))
            }
        }
        if (rows.isEmpty()) item { Empty(Icons.Default.BarChart, "No records in this period.") }
        if (byDay.isNotEmpty()) {
            item { SectionTitle("RECORDS BY DAY") }
            items(byDay) { (d, n, e) -> ReportLine(d, "$n record(s), $e entries") }
        }
        if (byShop.isNotEmpty()) {
            item { SectionTitle("RECORDS BY SHOP") }
            items(byShop) { (s, n, e) -> ReportLine(s, "$n record(s), $e entries") }
        }
        if (totals.isNotEmpty()) {
            item { SectionTitle("TOTALS PER PRODUCT AND UNIT") }
            item { Text("Different units are never added together.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(totals) { (n, u, q) -> ReportLine(n, "${fmtQty(q)} $u") }
        }
        if (ents.isNotEmpty()) {
            item { SectionTitle("PRODUCT COLLECTION HISTORY") }
            items(ents.size) { i ->
                val e = ents[i]
                Card(Modifier.fillMaxWidth().clickable { go("detail:${e.entry.recordId}") }, shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.entry.productName, fontWeight = FontWeight.SemiBold)
                            Text(fmtDate(e.createdAt) + if (e.shop.isNotBlank()) "  \u2022  ${e.shop}" else "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${fmtQty(e.entry.quantity)} ${e.entry.unit}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        if (rows.isNotEmpty()) {
            item { SectionTitle("SHARE A RECORD") }
            items(rows, key = { "rr" + it.record.id }) { r ->
                RecordCard(r, { go("detail:${r.record.id}") }, RecordActions(vm, r.record.id, go) { deleting = it })
            }
        }
    }
    deleting?.let { id ->
        Confirm("Delete this record?", "This record and its product entries will be permanently deleted.", "Delete", true,
            { deleting = null; scope.launch { if (vm.deleteRecord(id)) vm.message = "Record deleted." } }, { deleting = null })
    }
}

@Composable
fun ReportLine(left: String, right: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp)) {
        Text(left, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Text(right, color = MaterialTheme.colorScheme.primary)
    }
    HorizontalDivider()
}

// ------------------------------------------------------------------ Backup & restore
@Composable
fun BackupScreen(vm: Vm, pad: PaddingValues) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Backup.Parsed?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            status = try {
                val text = Backup.export(vm.db)
                withContext(Dispatchers.IO) {
                    val out = ctx.contentResolver.openOutputStream(uri, "wt") ?: throw IllegalStateException("Could not open the chosen file.")
                    out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                "Backup saved successfully."
            } catch (e: Exception) { "Backup failed: ${e.message ?: "unknown error"}" }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    val input = ctx.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Could not open the chosen file.")
                    input.use { String(it.readBytes(), Charsets.UTF_8) }
                }
                pending = Backup.parse(text)
                status = null
            } catch (e: Exception) { status = "Restore failed: ${e.message ?: "unreadable file"}" }
        }
    }
    LazyColumn(contentPadding = contentPad(pad), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Backups are plain JSON files containing your products, records and notes. You choose where they are saved.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Button(onClick = { export.launch("VX7-Marketing-Backup-${fileDay(System.currentTimeMillis())}.json") }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Default.Backup, null); Text("  Create Backup") }
        }
        item {
            OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Icon(Icons.Default.Restore, null); Text("  Restore From Backup") }
        }
        item { Text("Restore merges the backup into your current data. Nothing is deleted, and duplicate records are skipped.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        status?.let { s -> item { Text(s, fontWeight = FontWeight.SemiBold, color = if (s.contains("failed")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) } }
    }
    pending?.let { p ->
        Confirm("Restore this backup?", "The file contains ${p.products.size} product(s), ${p.records.size} record(s) and ${p.notes.size} note(s). They will be merged with your current data. Nothing existing will be deleted.", "Restore", false,
            {
                pending = null
                scope.launch {
                    status = try {
                        val r = Backup.restore(vm.db, p)
                        "Restore complete: ${r.recordsAdded} record(s) added, ${r.recordsSkipped} duplicate(s) skipped, ${r.productsAdded} new product(s), ${r.notesAdded} note(s)."
                    } catch (e: Exception) { "Restore failed: ${e.message ?: "database error"}. No changes were made." }
                }
            }, { pending = null })
    }
}

// ------------------------------------------------------------------ Settings / About / Notes
@Composable
fun SettingsScreen(vm: Vm, pad: PaddingValues, go: (String) -> Unit) {
    val ctx = LocalContext.current
    val version = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0.0" } catch (e: Exception) { "1.0.0" } }
    LazyColumn(contentPadding = contentPad(pad), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { SectionTitle("THEME") }
        items(listOf("system" to "Follow system", "light" to "Light", "dark" to "Dark")) { (k, l) ->
            Row(Modifier.fillMaxWidth().clickable { vm.changeTheme(k) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = vm.theme == k, onClick = { vm.changeTheme(k) }); Text(l)
            }
        }
        item {
            SectionTitle("DEFAULT QUANTITY UNIT")
            var open by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { open = true }) { Text(vm.defaultUnit); Icon(Icons.Default.ArrowDropDown, null) }
                DropdownMenu(open, { open = false }) { UNITS.filter { it != "Other" }.forEach { u -> DropdownMenuItem(text = { Text(u) }, onClick = { vm.changeDefaultUnit(u); open = false }) } }
            }
        }
        item { SectionTitle("DATA") }
        item { OutlinedButton(onClick = { go("backup") }, modifier = Modifier.fillMaxWidth()) { Text("Backup & Restore") } }
        item { SectionTitle("PRIVACY") }
        item { Text("VX7 Marketing works fully offline. Your data stays on this device. There is no login, no advertising, no analytics and no tracking. Files are shared only when you choose to share them.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { SectionTitle("APP") }
        item { Text("Version $version") }
        item { OutlinedButton(onClick = { go("about") }, modifier = Modifier.fillMaxWidth()) { Text("About VX7 Marketing") } }
    }
}

@Composable
fun AboutScreen(pad: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(pad).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(R.drawable.vx7_logo), "VX7 Marketing logo", Modifier.size(140.dp))
        Spacer(Modifier.height(12.dp))
        Text("VX7 MARKETING", fontWeight = FontWeight.Black, fontSize = 22.sp)
        Text("TRACK  \u2022  PURCHASE  \u2022  GROW", color = Gold, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Text("Record the products you collect from shops and the quantity of each, then share the record as a professional PDF or image. Works offline. No prices, no billing, no accounts.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun NotesScreen(vm: Vm, pad: PaddingValues) {
    val notes by remember { vm.db.notes().all() }.collectAsStateWithLifecycle(emptyList())
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<QuickNote?>(null) }
    val scope = rememberCoroutineScope()
    LazyColumn(contentPadding = contentPad(pad), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            OutlinedTextField(title, { title = it.take(100) }, label = { Text("Note title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(body, { body = it.take(2000) }, label = { Text("Write your note") }, minLines = 3, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            Button(onClick = {
                if (title.isBlank() && body.isBlank()) vm.message = "Write something first."
                else scope.launch {
                    try { vm.db.notes().add(QuickNote(title = title.trim().ifBlank { "Quick note" }, body = body.trim())); title = ""; body = ""; vm.message = "Note saved." }
                    catch (e: Exception) { vm.message = "Could not save the note." }
                }
            }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Icon(Icons.Default.Save, null); Text("  Save Note") }
        }
        if (notes.isEmpty()) item { Empty(Icons.Default.EditNote, "No notes yet.") }
        items(notes, key = { it.id }) { n ->
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(n.title, fontWeight = FontWeight.Bold)
                        if (n.body.isNotBlank()) Text(n.body)
                        Text(fmtDate(n.createdAt) + "  " + fmtTime(n.createdAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { deleting = n }) { Icon(Icons.Default.Delete, "Delete note", tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    deleting?.let { n -> Confirm("Delete note?", "This note will be permanently deleted.", "Delete", true, { deleting = null; scope.launch { vm.db.notes().delete(n) } }, { deleting = null }) }
}
