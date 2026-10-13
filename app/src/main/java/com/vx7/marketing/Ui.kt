@file:OptIn(ExperimentalMaterial3Api::class)

package com.vx7.marketing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

val Navy = Color(0xFF0B1228)
val NavyDeep = Color(0xFF050914)
val Blue = Color(0xFF2677E8)
val ElectricBlue = Color(0xFF3D8BFF)
val Gold = Color(0xFFD5B05D)
val CardDark = Color(0xFF121C3D)

@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) darkColorScheme(
        primary = ElectricBlue, onPrimary = Color.White, secondary = Gold, onSecondary = Navy,
        background = Navy, surface = CardDark, onBackground = Color.White, onSurface = Color.White,
        surfaceVariant = Color(0xFF1C2A55), onSurfaceVariant = Color(0xFFB8C2DD), outline = Color(0xFF3A4A7D),
        error = Color(0xFFFF6B6B)
    ) else lightColorScheme(
        primary = Blue, onPrimary = Color.White, secondary = Gold, onSecondary = Navy,
        background = Color(0xFFF3F6FC), surface = Color.White, onBackground = Navy, onSurface = Navy,
        surfaceVariant = Color(0xFFE6ECF8), onSurfaceVariant = Color(0xFF4B566E)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

fun contentPad(pad: PaddingValues) = PaddingValues(
    start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 12.dp, bottom = pad.calculateBottomPadding() + 24.dp
)

@Composable
fun Bar(title: String, onMenu: (() -> Unit)?, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    Surface(color = Navy) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            else if (onMenu != null) IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, "Menu", tint = Color.White) }
            else Spacer(Modifier.width(12.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            actions()
        }
    }
}

@Composable
fun ScreenBox(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Navy, NavyDeep))), content = content)
}

@Composable
fun PremiumCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    val m = modifier.clip(shape).border(1.dp, Gold.copy(alpha = 0.35f), shape)
    if (onClick != null) {
        Card(onClick = onClick, modifier = m, shape = shape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(14.dp), content = content) }
    } else {
        Card(modifier = m, shape = shape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(14.dp), content = content) }
    }
}

@Composable
fun SectionTitle(t: String) = Text(t, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Gold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))

@Composable
fun Empty(icon: ImageVector, text: String) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Gold, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun Loading() {
    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
}

@Composable
fun ErrorText(msg: String?) { if (msg != null) Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp)) }

@Composable
fun Confirm(title: String, text: String, yes: String, destructive: Boolean, onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onYes) { Text(yes, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) } },
        dismissButton = { TextButton(onClick = onNo) { Text("Cancel") } }
    )
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, modifier = modifier.fillMaxWidth(),
        placeholder = { Text(hint) }, leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, "Clear") } },
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
fun RateField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(14)) }, label = { Text(label) }, singleLine = true,
        enabled = enabled, prefix = { Text("\u20B9") }, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}

@Composable
fun UnitMenu(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
            Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            UNITS.forEach { u -> DropdownMenuItem(text = { Text(u) }, onClick = { onChange(u); open = false }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePick(initial: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val st = rememberDatePickerState(initialSelectedDateMillis = localToUtcPicker(initial))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { val s = st.selectedDateMillis; onDismiss(); if (s != null) onPick(utcPickerToLocal(s)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) { DatePicker(st) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePick(initial: Long, onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = initial }
    val st = rememberTimePickerState(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onDismiss(); onPick(st.hour, st.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(st) }
    )
}

@Composable
fun PeriodBar(ps: PeriodState) {
    var pick by remember { mutableStateOf("") }
    var tmpFrom by remember { mutableLongStateOf(0L) }
    val chips = listOf("all" to "All", "today" to "Today", "week" to "Last 7 Days", "month" to "This Month", "date" to "Pick Date", "range" to "Custom Range")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { (k, l) ->
            FilterChip(selected = ps.key == k, onClick = {
                when (k) { "date" -> pick = "date"; "range" -> pick = "from"; else -> ps.apply(k) }
            }, label = { Text(l) })
        }
    }
    Text(ps.label(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    val now = System.currentTimeMillis()
    if (pick == "date") DatePick(now, { pick = "" }) { d -> ps.set("date", dayStart(d), dayEnd(d)) }
    if (pick == "from") DatePick(now, { pick = "" }) { d -> tmpFrom = d; pick = "to" }
    if (pick == "to") DatePick(tmpFrom, { pick = "" }) { d -> ps.set("range", dayStart(minOf(tmpFrom, d)), dayEnd(maxOf(tmpFrom, d))) }
}

/** Reusable "Rate" line: label + value. */
@Composable
fun KeyValue(k: String, v: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.weight(0.45f))
        Text(v, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.55f), textAlign = TextAlign.End)
    }
}

/** PDF / PNG buttons -> build file off the main thread -> dialog with Save / Share / Open. Private documents need explicit confirmation. */
@Composable
fun ExportButtons(vm: Vm, base: String, build: suspend (Boolean) -> ReportDoc, isPrivate: Boolean = false, offerPrivate: Boolean = false) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var includePvt by remember { mutableStateOf(false) }
    var confirmPdf by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<File, Boolean>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }

    fun copyOut(uri: android.net.Uri?) {
        val r = result ?: return
        if (uri == null) return
        scope.launch {
            try { withContext(Dispatchers.IO) { Exporter.copyTo(ctx, r.first, uri) }; vm.message = "File saved." }
            catch (e: Exception) { vm.message = "Could not save the file: ${e.message}" }
        }
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { copyOut(it) }
    val savePng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { copyOut(it) }

    fun run(pdf: Boolean) {
        scope.launch {
            busy = true
            try {
                val doc = build(isPrivate || (offerPrivate && includePvt))
                result = vm.makeFile(doc, base, pdf) to pdf
            } catch (e: Exception) { err = e.message ?: "Export failed." } finally { busy = false }
        }
    }
    fun start(pdf: Boolean) { if (isPrivate || (offerPrivate && includePvt)) confirmPdf = pdf else run(pdf) }

    Column {
        if (offerPrivate) Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = includePvt, onCheckedChange = { includePvt = it })
            Text("Include private rates (Pvt Rate)", fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { start(true) }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text("PDF") }
            OutlinedButton(onClick = { start(false) }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Image, null); Spacer(Modifier.width(6.dp)); Text("PNG") }
        }
    }
    confirmPdf?.let { pdf ->
        Confirm("Export private information?", "This file will contain private Pvt Rates. Anyone you share it with will see them.", "Export", true,
            { confirmPdf = null; run(pdf) }, { confirmPdf = null })
    }
    if (busy) AlertDialog(onDismissRequest = {}, confirmButton = {}, title = { Text("Creating file...") }, text = { LinearProgressIndicator(Modifier.fillMaxWidth()) })
    err?.let { e -> AlertDialog(onDismissRequest = { err = null }, confirmButton = { TextButton(onClick = { err = null }) { Text("OK") } }, title = { Text("Export failed") }, text = { Text(e) }) }
    result?.let { (f, pdf) ->
        val mime = if (pdf) "application/pdf" else "image/png"
        AlertDialog(
            onDismissRequest = { result = null },
            title = { Text(if (pdf) "PDF ready" else "PNG ready") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(f.name, fontSize = 13.sp)
                    Button(onClick = { if (pdf) savePdf.launch(f.name) else savePng.launch(f.name) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Save, null); Spacer(Modifier.width(6.dp)); Text("Save to device") }
                    OutlinedButton(onClick = { try { Exporter.share(ctx, f, mime, f.name) } catch (e: Exception) { vm.message = "Could not share: ${e.message}" } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text("Share") }
                    OutlinedButton(onClick = { if (!Exporter.open(ctx, f, mime)) vm.message = "No app on this phone can open this file. Use Save or Share." }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("Open") }
                }
            },
            confirmButton = { TextButton(onClick = { result = null }) { Text("Close") } }
        )
    }
}

/** Shown wherever a Pvt Rate may appear in general (non-Pvt-List) screens. */
@Composable
fun PvtRateRow(vm: Vm, value: Double?) {
    var ask by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Lock, null, tint = Gold, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Pvt Rate (private)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(if (vm.pvtRevealed) fmtRate(value) else "\u2022\u2022\u2022\u2022", fontWeight = FontWeight.SemiBold)
        IconButton(onClick = { if (vm.pvtRevealed) vm.hidePvt() else if (vm.pvtAsk) ask = true else vm.revealPvt() }) {
            Icon(if (vm.pvtRevealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (vm.pvtRevealed) "Hide private rate" else "Show private rate")
        }
    }
    if (ask) Confirm("Show private rate?", "Pvt Rates are private. Make sure nobody else is looking at your screen.", "Show", false, { ask = false; vm.revealPvt() }, { ask = false })
}

// ===================================================================== DASHBOARD
private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return if (h < 12) "Good morning" else if (h < 17) "Good afternoon" else "Good evening"
}

@Composable
fun StatCard(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    PremiumCard(modifier) {
        Icon(icon, null, tint = Gold, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = ElectricBlue)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

@Composable
fun ActionCard(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    PremiumCard(modifier, onClick) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = ElectricBlue, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, minLines = 2)
        }
    }
}

@Composable
fun HomeScreen(vm: Vm, pad: PaddingValues, go: (String) -> Unit) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    ScreenBox {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPad(pad)), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFF1B3A8A), Color(0xFF0E1A3F)))).border(1.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(22.dp)).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(painterResource(R.drawable.vx7_logo), null, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(greeting(), color = Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("VX7 Marketing", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(fmtLongDate(System.currentTimeMillis()), color = Color(0xFFB8C2DD), fontSize = 12.sp)
                    }
                }
            }
            SectionTitle("OVERVIEW")
            val s = stats
            if (s == null) Loading() else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Total products", "${s.products}", Icons.Filled.Inventory2, Modifier.weight(1f))
                    StatCard("Collections", "${s.collections}", Icons.Filled.CreateNewFolder, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Normal List entries", "${s.normal}", Icons.AutoMirrored.Filled.List, Modifier.weight(1f))
                    StatCard("Pvt List entries", "${s.pvt}", Icons.Filled.Lock, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Product entries", "${s.entries}", Icons.Filled.CheckCircle, Modifier.weight(1f))
                    StatCard("Today's activity", "${s.today}", Icons.Filled.Today, Modifier.weight(1f))
                }
            }
            SectionTitle("QUICK ACTIONS")
            val actions: List<Triple<String, ImageVector, String>> = listOf(
                Triple("Add Product", Icons.Filled.Add, "productform:0"),
                Triple("Normal List", Icons.AutoMirrored.Filled.List, "normal"),
                Triple("Pvt List", Icons.Filled.Lock, "pvt"),
                Triple("New Collection", Icons.Filled.CreateNewFolder, "form"),
                Triple("Collection History", Icons.Filled.History, "history"),
                Triple("Notes", Icons.Filled.StickyNote2, "notes"),
                Triple("Reports", Icons.Filled.BarChart, "reports"),
                Triple("Notifications", Icons.Filled.Notifications, "notifications"),
                Triple("Backup & Restore", Icons.Filled.Backup, "backup")
            )
            actions.chunked(3).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowItems.forEach { (l, i, r) -> ActionCard(l, i, Modifier.weight(1f)) { if (r == "form") vm.newDraft(); go(r) } }
                }
            }
            SectionTitle("RECENT ACTIVITY")
            val recent = log?.take(6)
            if (recent == null) Loading()
            else if (recent.isEmpty()) PremiumCard(Modifier.fillMaxWidth()) { Empty(Icons.Filled.History, "No activity yet. Add a product or save a collection to get started.") }
            else PremiumCard(Modifier.fillMaxWidth()) {
                recent.forEachIndexed { i, a ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    Row(Modifier.fillMaxWidth().clickable { if (a.route.isNotBlank()) go(a.route) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(a.message, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(fmtStamp(a.createdAt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
