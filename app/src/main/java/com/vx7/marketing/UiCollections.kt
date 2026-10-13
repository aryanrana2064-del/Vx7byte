@file:OptIn(ExperimentalMaterial3Api::class)

package com.vx7.marketing

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun ProductPicker(vm: Vm, existing: Set<Long>, onDone: (List<Product>) -> Unit, onClose: () -> Unit) {
    val all by vm.products.collectAsStateWithLifecycle()
    var q by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<Long>() }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Select products") },
        text = {
            Column {
                SearchField(q, { q = it }, "Search products")
                val list = all
                if (list == null) Loading()
                else if (list.isEmpty()) Empty(Icons.Filled.Inventory2, "No products yet. Add a product first from the Products screen.")
                else {
                    val shown = list.filter { it.id !in existing && (q.isBlank() || it.name.contains(q.trim(), true)) }
                    if (shown.isEmpty()) Text("Nothing to add.", Modifier.padding(16.dp))
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(shown, key = { it.id }) { p ->
                            Row(Modifier.fillMaxWidth().clickable { if (p.id in picked) picked.remove(p.id) else picked.add(p.id) }, verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = p.id in picked, onCheckedChange = { if (it) picked.add(p.id) else picked.remove(p.id) })
                                Column { Text(p.name); if (p.category.isNotBlank()) Text(p.category, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = picked.isNotEmpty(), onClick = { onDone(all.orEmpty().filter { it.id in picked }); onClose() }) { Text("Add ${picked.size}") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}

@Composable
private fun DraftCard(vm: Vm, l: DraftLine, onRemove: () -> Unit) {
    var askPvt by remember { mutableStateOf(false) }
    PremiumCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(l.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Remove entry") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(l.qty, { l.qty = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(12) }, label = { Text("Quantity") }, singleLine = true,
                modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            UnitMenu(l.unit, { l.unit = it }, Modifier.weight(1f))
        }
        if (l.unit == CUSTOM) OutlinedTextField(l.custom, { l.custom = it.take(20) }, label = { Text("Custom unit") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RateField(l.real, { l.real = it }, "Real Rate", Modifier.weight(1f))
            Text("Last Rate\n" + fmtRate(l.origLast), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.7f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            if (vm.pvtRevealed) RateField(l.pvt, { l.pvt = it }, "Pvt Rate (private)", Modifier.weight(1f))
            else {
                Icon(Icons.Filled.Lock, null, tint = Gold, modifier = Modifier.size(16.dp))
                Text("  Pvt Rate hidden", fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { if (vm.pvtAsk) askPvt = true else vm.revealPvt() }) { Text("Reveal") }
            }
        }
        OutlinedTextField(l.note, { l.note = it.take(200) }, label = { Text("Entry note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
    }
    if (askPvt) Confirm("Show private rate?", "Pvt Rates are private. Make sure nobody else is looking at your screen.", "Show", false, { askPvt = false; vm.revealPvt() }, { askPvt = false })
}

@Composable
fun FormScreen(vm: Vm, pad: PaddingValues, back: () -> Unit, replace: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var picker by remember { mutableStateOf(false) }
    var dateDlg by remember { mutableStateOf(false) }
    var timeDlg by remember { mutableStateOf(false) }
    ScreenBox {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPad(pad)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(vm.title, { vm.title = it.take(80) }, label = { Text("Collection title *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(vm.shop, { vm.shop = it.take(80) }, label = { Text("Shop / customer name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(vm.location, { vm.location = it.take(80) }, label = { Text("Location") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { dateDlg = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Today, null); Spacer(Modifier.width(6.dp)); Text(fmtDate(vm.draftTime)) }
                OutlinedButton(onClick = { timeDlg = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.History, null); Spacer(Modifier.width(6.dp)); Text(fmtTime(vm.draftTime)) }
            }
            OutlinedTextField(vm.notesText, { vm.notesText = it.take(1000) }, label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            SectionTitle("PRODUCT ENTRIES (${vm.lines.size})")
            if (vm.lines.isEmpty()) Text("No products added yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            vm.lines.toList().forEach { l -> key(l.productId) { DraftCard(vm, l) { vm.lines.remove(l) } } }
            OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Add products") }
            ErrorText(vm.formError)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = back, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(enabled = !vm.saving, modifier = Modifier.weight(1f), onClick = {
                    scope.launch {
                        val wasEdit = vm.editingId != 0L
                        val id = vm.saveCollection()
                        if (id != null) { vm.message = "Collection saved."; if (wasEdit) back() else replace("detail:$id") }
                    }
                }) { Text(if (vm.editingId == 0L) "Save collection" else "Save changes") }
            }
        }
    }
    if (picker) ProductPicker(vm, vm.lines.map { it.productId }.toSet(), { vm.addProducts(it) }, { picker = false })
    if (dateDlg) DatePick(vm.draftTime, { dateDlg = false }) { vm.draftTime = withDate(vm.draftTime, it) }
    if (timeDlg) TimePick(vm.draftTime, { timeDlg = false }) { h, m -> vm.draftTime = withTime(vm.draftTime, h, m) }
}

@Composable
fun DetailScreen(vm: Vm, id: Long, pad: PaddingValues, go: (String) -> Unit, back: () -> Unit) {
    val rec by remember(id) { vm.db.records().recordFlow(id).map { Loaded(it) } }.collectAsStateWithLifecycle(null)
    val entries by remember(id) { vm.db.records().entriesFlow(id) }.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var del by remember { mutableStateOf(false) }
    ScreenBox {
        val r = rec?.value
        if (rec == null) Loading()
        else if (r == null) Empty(Icons.Filled.CreateNewFolder, "This collection no longer exists.")
        else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPad(pad)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(r.title.ifBlank { r.shop.ifBlank { "Collection" } }, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            PremiumCard(Modifier.fillMaxWidth()) {
                KeyValue("Date", fmtLongDate(r.createdAt))
                KeyValue("Time", fmtTime(r.createdAt))
                if (r.shop.isNotBlank()) KeyValue("Shop / customer", r.shop)
                if (r.location.isNotBlank()) KeyValue("Location", r.location)
                KeyValue("Product entries", "${entries.size}")
            }
            if (r.note.isNotBlank()) PremiumCard(Modifier.fillMaxWidth()) { Text("Notes", fontSize = 12.sp, color = Gold); Text(r.note) }
            SectionTitle("PRODUCTS")
            entries.forEach { e ->
                PremiumCard(Modifier.fillMaxWidth()) {
                    Row { Text(e.productName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("${fmtQty(e.quantity)} ${e.unit}", color = ElectricBlue, fontWeight = FontWeight.Bold) }
                    if (e.realRate != null || e.lastRate != null) { KeyValue("Real Rate", fmtRate(e.realRate)); KeyValue("Last Rate", fmtRate(e.lastRate)) }
                    if (e.pvtRate != null) PvtRateRow(vm, e.pvtRate)
                    if (e.note.isNotBlank()) Text(e.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            ExportButtons(vm, "VX7_Collection_${fileStamp(r.createdAt)}", { inc -> vm.collectionDoc(id, inc) }, offerPrivate = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { scope.launch { if (vm.loadDraft(id)) go("form") else vm.message = "Collection not found." } }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Edit, null); Spacer(Modifier.width(6.dp)); Text("Edit") }
                OutlinedButton(onClick = { del = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(6.dp)); Text("Delete") }
            }
            if (del) Confirm("Delete collection?", "This collection and its ${entries.size} product entries will be deleted.", "Delete", true,
                { del = false; scope.launch { if (vm.deleteCollection(r)) { vm.message = "Collection deleted."; back() } } }, { del = false })
        }
    }
}

@Composable
fun HistoryScreen(vm: Vm, pad: PaddingValues, go: (String) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    val from = vm.hist.from
    val to = vm.hist.to
    val rows by remember(q, from, to) { vm.db.records().search(q.trim(), from, to, 500) }.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var delRow by remember { mutableStateOf<CollectionRecord?>(null) }
    ScreenBox {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(top = pad.calculateTopPadding() + 8.dp, start = 16.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchField(q, { q = it }, "Search title, product or shop")
                PeriodBar(vm.hist)
            }
            val list = rows
            if (list == null) Loading()
            else if (list.isEmpty()) Empty(Icons.Filled.History, "No collections found for this search and date range.")
            else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp, 10.dp, 16.dp, pad.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.record.id }) { row ->
                    val r = row.record
                    PremiumCard(Modifier.fillMaxWidth(), onClick = { go("detail:${r.id}") }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.title.ifBlank { r.shop.ifBlank { "Collection" } }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(fmtStamp(r.createdAt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(listOf(r.shop, "${row.entryCount} product entries").filter { it.isNotBlank() }.joinToString(" \u2022 "), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { scope.launch { if (vm.loadDraft(r.id)) go("form") } }) { Icon(Icons.Filled.Edit, "Edit") }
                            IconButton(onClick = { delRow = r }) { Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
    delRow?.let { r ->
        Confirm("Delete collection?", "\"${r.title.ifBlank { r.shop.ifBlank { "Collection" } }}\" and all its product entries will be deleted.", "Delete", true,
            { delRow = null; scope.launch { if (vm.deleteCollection(r)) vm.message = "Collection deleted." } }, { delRow = null })
    }
}
