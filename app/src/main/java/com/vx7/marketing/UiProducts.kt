@file:OptIn(ExperimentalMaterial3Api::class)

package com.vx7.marketing

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class Loaded<T>(val value: T?)

@Composable
fun LocalPhoto(path: String, modifier: Modifier) {
    val img by produceState<ImageBitmap?>(null, path) {
        value = if (path.isEmpty()) null else withContext(Dispatchers.IO) {
            try { BitmapFactory.decodeFile(path)?.asImageBitmap() } catch (e: Throwable) { null }
        }
    }
    val i = img
    if (i != null) Image(i, null, modifier, contentScale = ContentScale.Crop)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Inventory2, null, tint = Gold) }
}

private val SORTS = listOf("name" to "Name A-Z", "name_desc" to "Name Z-A", "newest" to "Newest first", "oldest" to "Oldest first", "updated" to "Recently updated")

@Composable
fun ProductsScreen(vm: Vm, pad: PaddingValues, go: (String) -> Unit) {
    val all by vm.products.collectAsStateWithLifecycle()
    var q by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("name") }
    var sortOpen by remember { mutableStateOf(false) }
    ScreenBox {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(top = pad.calculateTopPadding() + 8.dp, start = 16.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchField(q, { q = it }, "Search products by name")
                val cats = all.orEmpty().map { it.category }.filter { it.isNotBlank() }.distinct().sortedBy { it.lowercase() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = cat.isEmpty(), onClick = { cat = "" }, label = { Text("All") })
                        cats.forEach { c -> FilterChip(selected = cat == c, onClick = { cat = if (cat == c) "" else c }, label = { Text(c) }) }
                    }
                    Box {
                        IconButton(onClick = { sortOpen = true }) { Icon(Icons.Filled.Sort, "Sort") }
                        DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                            SORTS.forEach { (k, l) -> DropdownMenuItem(text = { Text(l, fontWeight = if (k == sort) FontWeight.Bold else FontWeight.Normal) }, onClick = { sort = k; sortOpen = false }) }
                        }
                    }
                }
            }
            val list = all
            if (list == null) Loading() else {
                val shown = remember(list, q, cat, sort) {
                    val f = list.filter { (q.isBlank() || it.name.contains(q.trim(), true)) && (cat.isEmpty() || it.category == cat) }
                    when (sort) {
                        "name_desc" -> f.sortedByDescending { it.name.lowercase() }
                        "newest" -> f.sortedByDescending { it.createdAt }
                        "oldest" -> f.sortedBy { it.createdAt }
                        "updated" -> f.sortedByDescending { it.updatedAt }
                        else -> f.sortedBy { it.name.lowercase() }
                    }
                }
                if (shown.isEmpty()) Empty(Icons.Filled.Inventory2, if (list.isEmpty()) "No products yet.\nTap + to add your first product." else "No products match your search.")
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = pad.calculateBottomPadding() + 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shown, key = { it.id }) { p ->
                        PremiumCard(Modifier.fillMaxWidth(), onClick = { go("product:${p.id}") }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LocalPhoto(p.photoPath, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(listOf(p.category, p.unit).filter { it.isNotBlank() }.joinToString(" \u2022 "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                                Text(fmtRate(p.realRate), color = ElectricBlue, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { go("productform:0") }, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = pad.calculateBottomPadding() + 20.dp), containerColor = ElectricBlue, contentColor = androidx.compose.ui.graphics.Color.White) {
            Icon(Icons.Filled.Add, "Add product")
        }
    }
}

@Composable
fun ProductDetailScreen(vm: Vm, id: Long, pad: PaddingValues, go: (String) -> Unit, back: () -> Unit) {
    val state by remember(id) { vm.db.products().observe(id).map { Loaded(it) } }.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var del by remember { mutableStateOf(false) }
    ScreenBox {
        val p = state?.value
        if (state == null) Loading()
        else if (p == null) Empty(Icons.Filled.Inventory2, "This product no longer exists.")
        else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPad(pad)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (p.photoPath.isNotEmpty()) LocalPhoto(p.photoPath, Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(18.dp)))
            Text(p.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            PremiumCard(Modifier.fillMaxWidth()) {
                KeyValue("Category", p.category.ifBlank { "\u2014" })
                KeyValue("Default unit", p.unit)
                KeyValue("Real Rate (current)", fmtRate(p.realRate))
                KeyValue("Last Rate (previous)", fmtRate(p.lastRate))
                PvtRateRow(vm, p.pvtRate)
            }
            if (p.notes.isNotBlank()) PremiumCard(Modifier.fillMaxWidth()) { Text("Description", fontSize = 12.sp, color = Gold); Text(p.notes) }
            Text("Created ${fmtStamp(p.createdAt)}  \u2022  Updated ${fmtStamp(p.updatedAt)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { go("productform:${p.id}") }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Edit, null); Spacer(Modifier.width(6.dp)); Text("Edit") }
                OutlinedButton(onClick = { del = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(6.dp)); Text("Delete") }
            }
            if (del) Confirm("Delete product?", "\"${p.name}\" will be deleted. Past collections keep the product name.", "Delete", true,
                { del = false; scope.launch { vm.deleteProduct(p); back() } }, { del = false })
        }
    }
}

@Composable
fun ProductFormScreen(vm: Vm, id: Long, pad: PaddingValues, back: () -> Unit) {
    val scope = rememberCoroutineScope()
    var old by remember { mutableStateOf<Product?>(null) }
    var ready by remember { mutableStateOf(id == 0L) }
    var missing by remember { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var desc by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(if (vm.defaultUnit in UNITS) vm.defaultUnit else "Piece") }
    var custom by rememberSaveable { mutableStateOf("") }
    var real by rememberSaveable { mutableStateOf("") }
    var last by rememberSaveable { mutableStateOf("") }
    var pvt by rememberSaveable { mutableStateOf("") }
    var photo by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var similar by remember { mutableStateOf<List<String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var askPvt by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != 0L) {
            val p = vm.db.products().get(id)
            if (p == null) missing = true else {
                old = p
                name = p.name; category = p.category; desc = p.notes
                if (isStdUnit(p.unit)) unit = p.unit else { unit = CUSTOM; custom = p.unit }
                real = rateText(p.realRate); last = rateText(p.lastRate); pvt = rateText(p.pvtRate); photo = p.photoPath
            }
            ready = true
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val path = vm.savePhoto(uri)
            if (path == null) error = "Could not use that photo." else {
                if (photo.isNotEmpty() && photo != old?.photoPath) vm.repo.deletePhotoFile(photo)
                photo = path
            }
        }
    }

    fun submit(force: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            val lastEdited = old != null && parseRate(last).second != old?.lastRate
            val r = vm.saveProduct(old, ProductForm(name, category, desc, unit, custom, photo, real, last, lastEdited, pvt, force))
            busy = false
            when (r) {
                is Saved.Ok -> {
                    val prev = old?.photoPath.orEmpty()
                    if (prev.isNotEmpty() && prev != photo) vm.repo.deletePhotoFile(prev)
                    vm.message = "Product saved."; back()
                }
                is Saved.Error -> error = r.message
                is Saved.Similar -> similar = r.names
            }
        }
    }

    ScreenBox {
        if (!ready) Loading()
        else if (missing) Empty(Icons.Filled.Inventory2, "This product no longer exists.")
        else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPad(pad)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LocalPhoto(photo, Modifier.size(84.dp).clip(RoundedCornerShape(16.dp)))
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Icon(Icons.Filled.Image, null); Spacer(Modifier.width(6.dp)); Text(if (photo.isEmpty()) "Add photo" else "Change photo") }
                    if (photo.isNotEmpty()) TextButton(onClick = { photo = "" }) { Text("Remove photo") }
                }
            }
            OutlinedTextField(name, { name = it.take(80) }, label = { Text("Product name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(category, { category = it.take(40) }, label = { Text("Category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Default unit", fontSize = 12.sp, color = Gold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                UnitMenu(unit, { unit = it }, Modifier.weight(1f))
                if (unit == CUSTOM) OutlinedTextField(custom, { custom = it.take(20) }, label = { Text("Custom unit") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(desc, { desc = it.take(500) }, label = { Text("Description") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            SectionTitle("RATES (reference only)")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RateField(real, { real = it }, "Real Rate", Modifier.weight(1f))
                RateField(last, { last = it }, "Last Rate", Modifier.weight(1f))
            }
            Text(if (old == null) "Last Rate is filled automatically when the Real Rate changes later." else "When you change the Real Rate, the old Real Rate is saved as Last Rate automatically.",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vm.pvtRevealed) RateField(pvt, { pvt = it }, "Pvt Rate (private)", Modifier.weight(1f))
                else {
                    Icon(Icons.Filled.Lock, null, tint = Gold)
                    Text("  Pvt Rate is hidden", modifier = Modifier.weight(1f))
                    TextButton(onClick = { if (vm.pvtAsk) askPvt = true else vm.revealPvt() }) { Text("Reveal") }
                }
                if (vm.pvtRevealed) IconButton(onClick = { vm.hidePvt() }) { Icon(Icons.Filled.VisibilityOff, "Hide") }
            }
            ErrorText(error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = back, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(onClick = { error = null; submit(false) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text(if (old == null) "Save product" else "Save changes") }
            }
        }
    }
    similar?.let { names ->
        Confirm("Similar product exists", "Already in your list: ${names.joinToString(", ")}.\nSave \"${name.trim()}\" anyway?", "Save anyway", false,
            { similar = null; submit(true) }, { similar = null })
    }
    if (askPvt) Confirm("Show private rate?", "Pvt Rates are private. Make sure nobody else is looking at your screen.", "Show", false, { askPvt = false; vm.revealPvt() }, { askPvt = false })
}
