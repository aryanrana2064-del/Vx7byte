@file:OptIn(ExperimentalMaterial3Api::class)

package com.vx7.marketing

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private fun priceOf(e: ListEntry) = if (e.listType == PVT) e.pvtRate else e.realRate

@Composable
fun ListScreen(vm: Vm, type: String, pad: PaddingValues, go: (String) -> Unit) {
    val src by (if (type == PVT) vm.pvtList else vm.normalList).collectAsStateWithLifecycle()
    var q by rememberSaveable(type) { mutableStateOf("") }
    var adding by rememberSaveable(type) { mutableStateOf(false) }
    val isPvt = type == PVT
    ScreenBox {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(top = pad.calculateTopPadding() + 8.dp, start = 16.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isPvt) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Gold.copy(alpha = 0.15f)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, null, tint = Gold, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
                    Text("Private list. Price shows the Pvt Rate.", fontSize = 12.sp)
                }
                SearchField(q, { q = it }, "Search by name")
                if (isPvt) ExportButtons(vm, "VX7_Pvt_List", { vm.pvtListDoc() }, isPrivate = true)
                else ExportButtons(vm, "VX7_Normal_List", { vm.normalListDoc() })
            }
            val list = src
            if (list == null) Loading() else {
                val shown = remember(list, q) { list.filter { q.isBlank() || it.name.contains(q.trim(), true) } }
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp).clip(RoundedCornerShape(10.dp)).background(Blue.copy(alpha = 0.35f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text("Name", Modifier.weight(0.42f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Quantity", Modifier.weight(0.33f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Price", Modifier.weight(0.25f), fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.End)
                }
                if (shown.isEmpty()) Empty(if (isPvt) Icons.Filled.Lock else Icons.AutoMirrored.Filled.List,
                    if (list.isEmpty()) "No entries yet.\nTap + to add the first one." else "Nothing matches your search.")
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = pad.calculateBottomPadding() + 88.dp)) {
                    items(shown, key = { it.id }) { e ->
                        Row(Modifier.fillMaxWidth().clickable { go("entry:${e.id}") }.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(e.name, Modifier.weight(0.42f), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text("${fmtQty(e.quantity)} ${e.unit}", Modifier.weight(0.33f), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                            Text(fmtRate(priceOf(e)), Modifier.weight(0.25f), textAlign = TextAlign.End, color = ElectricBlue, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    }
                }
            }
        }
        FloatingActionButton(onClick = { adding = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = pad.calculateBottomPadding() + 20.dp), containerColor = ElectricBlue, contentColor = Color.White) {
            Icon(Icons.Filled.Add, "Add entry")
        }
    }
    if (adding) EntryDialog(vm, type, null) { adding = false }
}

@Composable
fun EntryDialog(vm: Vm, type: String, old: ListEntry?, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val products by vm.products.collectAsStateWithLifecycle()
    val isPvt = type == PVT
    var pid by remember { mutableStateOf(old?.productId) }
    var name by remember { mutableStateOf(old?.name.orEmpty()) }
    var qty by remember { mutableStateOf(old?.let { fmtQty(it.quantity) }.orEmpty()) }
    var unit by remember { mutableStateOf(old?.let { if (isStdUnit(it.unit)) it.unit else CUSTOM } ?: if (vm.defaultUnit in UNITS) vm.defaultUnit else "Piece") }
    var custom by remember { mutableStateOf(old?.let { if (isStdUnit(it.unit)) "" else it.unit }.orEmpty()) }
    var rate by remember { mutableStateOf(rateText(if (isPvt) old?.pvtRate else old?.realRate)) }
    var last by remember { mutableStateOf(rateText(old?.lastRate)) }
    var notes by remember { mutableStateOf(old?.notes.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pickOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text((if (old == null) "Add to " else "Edit ") + if (isPvt) "Pvt List" else "Normal List") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (old == null && !products.isNullOrEmpty()) Box {
                    OutlinedButton(onClick = { pickOpen = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Inventory2, null); Spacer(Modifier.width(6.dp)); Text("Fill from a product") }
                    DropdownMenu(expanded = pickOpen, onDismissRequest = { pickOpen = false }) {
                        products.orEmpty().forEach { p ->
                            DropdownMenuItem(text = { Text(p.name) }, onClick = {
                                pickOpen = false; pid = p.id; name = p.name
                                if (isStdUnit(p.unit)) { unit = p.unit; custom = "" } else { unit = CUSTOM; custom = p.unit }
                                if (isPvt) rate = rateText(p.pvtRate) else { rate = rateText(p.realRate); last = rateText(p.lastRate) }
                            })
                        }
                    }
                }
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(qty, { qty = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(12) }, label = { Text("Quantity *") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                    UnitMenu(unit, { unit = it }, Modifier.weight(1f))
                }
                if (unit == CUSTOM) OutlinedTextField(custom, { custom = it.take(20) }, label = { Text("Custom unit") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                RateField(rate, { rate = it }, if (isPvt) "Price (Pvt Rate)" else "Price (Real Rate)", Modifier.fillMaxWidth())
                if (!isPvt) {
                    RateField(last, { last = it }, "Last Rate", Modifier.fillMaxWidth())
                    Text("Changing the Real Rate saves the old one as Last Rate automatically.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(notes, { notes = it.take(300) }, label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                ErrorText(error)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true; error = null
                scope.launch {
                    val lastEdited = old != null && !isPvt && parseRate(last).second != old.lastRate
                    val r = vm.saveEntry(old, EntryForm(type, pid, name, qty, unit, custom, rate, last, lastEdited, notes))
                    busy = false
                    when (r) {
                        is Saved.Ok -> { vm.message = "Saved."; onClose() }
                        is Saved.Error -> error = r.message
                        is Saved.Similar -> error = "Similar entry exists."
                    }
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text("Cancel") } }
    )
}

@Composable
fun EntryDetailScreen(vm: Vm, id: Long, pad: PaddingValues, back: () -> Unit) {
    val state by remember(id) { vm.db.lists().observeOne(id).map { Loaded(it) } }.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf(false) }
    ScreenBox {
        val e = state?.value
        if (state == null) Loading()
        else if (e == null) Empty(Icons.AutoMirrored.Filled.List, "This entry no longer exists.")
        else {
            val isPvt = e.listType == PVT
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPad(pad)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(e.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                AssistChip(onClick = {}, label = { Text(if (isPvt) "Pvt List (private)" else "Normal List") }, leadingIcon = { Icon(if (isPvt) Icons.Filled.Lock else Icons.AutoMirrored.Filled.List, null) })
                PremiumCard(Modifier.fillMaxWidth()) {
                    KeyValue("Quantity", "${fmtQty(e.quantity)} ${e.unit}")
                    if (isPvt) KeyValue("Pvt Rate", fmtRate(e.pvtRate))
                    else { KeyValue("Real Rate (current)", fmtRate(e.realRate)); KeyValue("Last Rate (previous)", fmtRate(e.lastRate)) }
                }
                if (e.notes.isNotBlank()) PremiumCard(Modifier.fillMaxWidth()) { Text("Notes", fontSize = 12.sp, color = Gold); Text(e.notes) }
                Text("Created ${fmtStamp(e.createdAt)}  \u2022  Updated ${fmtStamp(e.updatedAt)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { edit = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Edit, null); Spacer(Modifier.width(6.dp)); Text("Edit") }
                    OutlinedButton(onClick = { del = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(6.dp)); Text("Delete") }
                }
            }
            if (edit) EntryDialog(vm, e.listType, e) { edit = false }
            if (del) Confirm("Delete entry?", "\"${e.name}\" will be removed from the ${if (isPvt) "Pvt" else "Normal"} List.", "Delete", true,
                { del = false; scope.launch { vm.deleteEntry(e); back() } }, { del = false })
        }
    }
}
