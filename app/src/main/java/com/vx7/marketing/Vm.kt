package com.vx7.marketing

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.File

class DraftLine(val productId: Long, val name: String, unit: String = "Piece", val origReal: Double? = null, val origLast: Double? = null) {
    var qty by mutableStateOf("")
    var unit by mutableStateOf(unit)
    var custom by mutableStateOf("")
    var note by mutableStateOf("")
    var real by mutableStateOf(rateText(origReal))
    var pvt by mutableStateOf("")
}

class PeriodState {
    var key by mutableStateOf("all")
    var from by mutableLongStateOf(0L)
    var to by mutableLongStateOf(Long.MAX_VALUE)

    fun set(k: String, f: Long, t: Long) { key = k; from = f; to = t }
    fun apply(k: String) {
        val now = System.currentTimeMillis()
        when (k) {
            "today" -> set(k, dayStart(now), dayEnd(now))
            "week" -> set(k, daysAgoStart(now, 6), dayEnd(now))
            "month" -> set(k, monthStart(now), dayEnd(now))
            else -> set("all", 0L, Long.MAX_VALUE)
        }
    }
    fun label(): String = when (key) {
        "all" -> "All dates"
        "today" -> "Today"
        "week" -> "Last 7 days"
        "month" -> "This month"
        else -> if (fileDay(from) == fileDay(to)) fmtDate(from) else "${fmtDate(from)} - ${fmtDate(to)}"
    }
}

data class Stats(val products: Int, val normal: Int, val pvt: Int, val collections: Int, val entries: Int, val today: Int = 0)

class Vm(app: Application) : AndroidViewModel(app) {
    val db: AppDb = AppDb.get(app)
    val repo = Repo(app, db)
    private val prefs = app.getSharedPreferences("vx7", Context.MODE_PRIVATE)
    private val ctx: Context get() = getApplication()

    // ------------------------------------------------------------ settings
    var theme by mutableStateOf(prefs.getString("theme", "dark") ?: "dark"); private set
    var defaultUnit by mutableStateOf(prefs.getString("unit", "Piece") ?: "Piece"); private set
    var defaultRange by mutableStateOf(prefs.getString("range", "all") ?: "all"); private set
    var pvtAsk by mutableStateOf(prefs.getBoolean("pvtAsk", true)); private set
    var message by mutableStateOf<String?>(null)
    var pendingRoute by mutableStateOf<String?>(null)
    var pvtRevealed by mutableStateOf(false)
    var notifOk by mutableStateOf(Notifier.canPost(app)); private set

    val hist = PeriodState().also { it.apply(defaultRange) }
    val rep = PeriodState().also { it.apply(defaultRange) }

    fun changeTheme(v: String) { theme = v; prefs.edit().putString("theme", v).apply() }
    fun changeDefaultUnit(v: String) { defaultUnit = v; prefs.edit().putString("unit", v).apply() }
    fun changeDefaultRange(v: String) { defaultRange = v; prefs.edit().putString("range", v).apply(); hist.apply(v); rep.apply(v) }
    fun changePvtAsk(v: Boolean) { pvtAsk = v; prefs.edit().putBoolean("pvtAsk", v).apply() }
    fun refreshNotif() { notifOk = Notifier.canPost(ctx) }
    fun askedNotifPermission() = prefs.getBoolean("askedNotif", false)
    fun markAskedNotifPermission() { prefs.edit().putBoolean("askedNotif", true).apply() }

    // ------------------------------------------------------------ reactive data (null = still loading)
    private fun <T> Flow<T>.state(): StateFlow<T?> = this.stateIn<T?>(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val products: StateFlow<List<Product>?> = db.products().observeAll().state()
    val normalList: StateFlow<List<ListEntry>?> = db.lists().observe(NORMAL).state()
    val pvtList: StateFlow<List<ListEntry>?> = db.lists().observe(PVT).state()
    val notes: StateFlow<List<QuickNote>?> = db.notes().all().state()
    val log: StateFlow<List<ActivityLog>?> = db.activity().all().state()

    /** Activity count for the current calendar day; re-queries when midnight passes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun todayCount(): Flow<Int> = flow {
        while (true) { emit(dayStart(System.currentTimeMillis())); delay(60_000L) }
    }.distinctUntilChanged().flatMapLatest { ds -> db.activity().countBetween(ds, dayEnd(ds)) }

    val stats: StateFlow<Stats?> = combine(
        db.products().count(), db.lists().count(NORMAL), db.lists().count(PVT), db.records().totalRecords(), db.records().totalEntries()
    ) { a, b, c, d, e -> Stats(a, b, c, d, e) }
        .combine(todayCount()) { s, t -> s.copy(today = t) }
        .state()

    // ------------------------------------------------------------ private rate reveal
    fun hidePvt() { pvtRevealed = false }
    fun revealPvt() { pvtRevealed = true }

    // ------------------------------------------------------------ collection draft
    var editingId by mutableLongStateOf(0L)
    var draftTime by mutableLongStateOf(System.currentTimeMillis())
    var title by mutableStateOf("")
    var shop by mutableStateOf("")
    var location by mutableStateOf("")
    var notesText by mutableStateOf("")
    var formError by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)
    val lines = mutableStateListOf<DraftLine>()

    fun newDraft() {
        editingId = 0L; draftTime = System.currentTimeMillis()
        title = ""; shop = ""; location = ""; notesText = ""; formError = null
        lines.clear()
    }

    suspend fun loadDraft(id: Long): Boolean {
        val r = db.records().get(id) ?: return false
        val es = db.records().entries(id)
        newDraft()
        editingId = r.id; draftTime = r.createdAt
        title = r.title.ifBlank { r.shop }; shop = r.shop; location = r.location; notesText = r.note
        es.forEach { e ->
            val std = isStdUnit(e.unit)
            val l = DraftLine(e.productId, e.productName, if (std) e.unit else CUSTOM, e.realRate, e.lastRate)
            l.qty = fmtQty(e.quantity)
            if (!std) l.custom = e.unit
            l.note = e.note
            l.pvt = rateText(e.pvtRate)
            lines.add(l)
        }
        return true
    }

    fun addProducts(ps: List<Product>) {
        ps.forEach { p ->
            if (lines.none { it.productId == p.id }) {
                val u = if (p.unit.isNotBlank() && isStdUnit(p.unit)) p.unit else if (defaultUnit in UNITS) defaultUnit else "Piece"
                val l = DraftLine(p.id, p.name, u, p.realRate, p.lastRate)
                if (!isStdUnit(p.unit) && p.unit.isNotBlank()) { l.unit = CUSTOM; l.custom = p.unit }
                l.pvt = rateText(p.pvtRate)
                lines.add(l)
            }
        }
    }

    private fun validate(): String? {
        if (title.isBlank()) return "Enter a collection title."
        if (lines.isEmpty()) return "Add at least one product entry."
        for (l in lines) {
            if (parseQty(l.qty) == null) return "Enter a valid quantity greater than zero for \"${l.name}\"."
            if (l.unit == CUSTOM && l.custom.isBlank()) return "Enter a custom unit name for \"${l.name}\"."
            if (!parseRate(l.real).first) return "Real Rate for \"${l.name}\" must be a number of 0 or more."
            if (!parseRate(l.pvt).first) return "Pvt Rate for \"${l.name}\" must be a number of 0 or more."
        }
        return null
    }

    /** Returns the saved collection id, or null on validation / database failure (formError is set). */
    suspend fun saveCollection(): Long? {
        if (saving) return null
        val err = validate()
        if (err != null) { formError = err; return null }
        saving = true
        try {
            val rec = CollectionRecord(id = editingId, createdAt = draftTime, shop = shop.trim(), location = location.trim(), note = notesText.trim(), title = title.trim())
            val es = lines.map {
                val real = parseRate(it.real).second
                val r = nextRates(it.origReal, it.origLast, real)
                Entry(recordId = 0, productId = it.productId, productName = it.name, quantity = parseQty(it.qty)!!, unit = resolveUnit(it.unit, it.custom),
                    note = it.note.trim(), realRate = r.real, lastRate = r.last, pvtRate = parseRate(it.pvt).second)
            }
            return try {
                val id = repo.saveCollection(rec, es, editingId != 0L)
                formError = null
                id
            } catch (e: Exception) {
                formError = "Could not save the collection: ${e.message ?: "database error"}"
                null
            }
        } finally { saving = false }
    }

    suspend fun deleteCollection(r: CollectionRecord): Boolean {
        val err = repo.deleteCollection(r.id, r.title.ifBlank { r.shop.ifBlank { "Collection" } })
        if (err != null) message = err
        return err == null
    }

    // ------------------------------------------------------------ products / lists / notes (thin wrappers)
    suspend fun saveProduct(old: Product?, f: ProductForm): Saved = repo.saveProduct(old, f)
    suspend fun deleteProduct(p: Product) { message = repo.deleteProduct(p) ?: "Product deleted. Past collections keep its name." }
    suspend fun saveEntry(old: ListEntry?, f: EntryForm): Saved = repo.saveEntry(old, f)
    suspend fun deleteEntry(e: ListEntry) { message = repo.deleteEntry(e) ?: "Entry deleted." }
    suspend fun saveNote(old: QuickNote?, t: String, b: String): String? = repo.saveNote(old, t, b)
    suspend fun deleteNote(n: QuickNote) { try { db.notes().delete(n.id); message = "Note deleted." } catch (e: Exception) { message = "Could not delete: ${e.message}" } }
    suspend fun togglePin(n: QuickNote) { try { db.notes().update(n.copy(pinned = !n.pinned)) } catch (e: Exception) { message = "Could not update: ${e.message}" } }
    suspend fun savePhoto(uri: Uri): String? = repo.savePhoto(uri)
    suspend fun clearLog() { try { db.activity().clear(); message = "Notification history cleared." } catch (e: Exception) { message = "Could not clear: ${e.message}" } }
    suspend fun clearAll() { try { repo.clearAll(); newDraft(); hidePvt(); message = "All data deleted." } catch (e: Exception) { message = "Could not delete everything: ${e.message}" } }

    // ------------------------------------------------------------ backup
    suspend fun exportBackup(uri: Uri, includePrivate: Boolean): String = try {
        val text = withContext(Dispatchers.IO) {
            val json = Backup.build(Backup.snapshot(db), includePrivate)
            ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) } ?: throw IllegalStateException("Could not open the chosen file.")
            // Verify the file we just wrote can be read back and parsed.
            val back = ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: throw IllegalStateException("Could not read the file back.")
            val s = Backup.parse(back)
            "${s.products.size} products, ${s.listEntries.count { it.listType == NORMAL }} Normal List and ${s.listEntries.count { it.listType == PVT }} Pvt List entries, " +
                "${s.records.size} collections, ${s.notes.size} notes"
        }
        repo.log("BACKUP", "Backup completed" + if (includePrivate) " (includes private rates)" else "", "backup", true)
        "Backup saved and verified: $text."
    } catch (e: Exception) { "Backup failed: ${e.message ?: "unknown error"}" }

    suspend fun readBackup(uri: Uri): Snapshot = withContext(Dispatchers.IO) {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalArgumentException("Could not open the selected file.")
        if (bytes.size > 25_000_000) throw IllegalArgumentException("This file is too large to be a VX7 backup.")
        Backup.parse(String(bytes, Charsets.UTF_8))
    }

    suspend fun restoreBackup(s: Snapshot, mode: RestoreMode): String = try {
        val r = Backup.restore(db, s, mode)
        repo.log("BACKUP", "Restore completed (${if (mode == RestoreMode.REPLACE) "replace" else "merge"})", "backup", true)
        "Restore complete: ${r.products} products, ${r.listEntries} list entries, ${r.collections} collections and ${r.notes} notes added" +
            if (r.skipped > 0) "; ${r.skipped} duplicates skipped." else "."
    } catch (e: Exception) { "Restore failed and nothing was changed: ${e.message ?: "database error"}" }

    // ------------------------------------------------------------ export files
    suspend fun makeFile(doc: ReportDoc, base: String, pdf: Boolean): File =
        withContext(Dispatchers.Default) { if (pdf) Exporter.pdf(ctx, doc, base) else Exporter.png(ctx, doc, base) }

    private fun priceCell(e: ListEntry) = fmtRate(if (e.listType == PVT) e.pvtRate else e.realRate)

    suspend fun collectionDoc(id: Long, includePrivate: Boolean): ReportDoc {
        val r = db.records().get(id) ?: throw IllegalStateException("Collection not found.")
        val es = db.records().entries(id)
        val headers = mutableListOf("No.", "Product", "Qty", "Unit", "Real Rate", "Last Rate")
        val weights = mutableListOf(0.6f, 3f, 1.1f, 1.2f, 1.3f, 1.3f)
        if (includePrivate) { headers.add("Pvt Rate"); weights.add(1.3f) }
        val rows = es.mapIndexed { i, e ->
            val row = mutableListOf("${i + 1}", e.productName + if (e.note.isNotBlank()) "\n${e.note}" else "", fmtQty(e.quantity), e.unit, fmtRate(e.realRate), fmtRate(e.lastRate))
            if (includePrivate) row.add(fmtRate(e.pvtRate))
            row
        }
        val info = mutableListOf("Date: ${fmtDate(r.createdAt)}   Time: ${fmtTime(r.createdAt)}")
        if (r.shop.isNotBlank()) info.add("Shop / customer: ${r.shop}")
        if (r.location.isNotBlank()) info.add("Location: ${r.location}")
        info.add("Product entries: ${es.size}")
        val secs = mutableListOf(Section("Details", info, Table(headers, weights, rows, setOf(2, 4, 5) + if (includePrivate) setOf(6) else emptySet())))
        if (r.note.isNotBlank()) secs.add(Section("Notes", listOf(r.note)))
        return ReportDoc(r.title.ifBlank { "Collection" }, "Collection record", secs, includePrivate)
    }

    suspend fun normalListDoc(): ReportDoc {
        val l = db.lists().byTypeOnce(NORMAL).sortedBy { it.name.lowercase() }
        val t = Table(listOf("Name", "Quantity", "Price"), listOf(3f, 1.6f, 1.2f), l.map { listOf(it.name, "${fmtQty(it.quantity)} ${it.unit}", priceCell(it)) }, setOf(2))
        return ReportDoc("Normal List", "${l.size} entries \u2022 ${fmtDate(System.currentTimeMillis())}", listOf(Section("Items", table = t)))
    }

    /** Private document: only ever built after an explicit confirmation in the UI. */
    suspend fun pvtListDoc(): ReportDoc {
        val l = db.lists().byTypeOnce(PVT).sortedBy { it.name.lowercase() }
        val t = Table(listOf("Name", "Quantity", "Price (Pvt Rate)"), listOf(3f, 1.6f, 1.6f), l.map { listOf(it.name, "${fmtQty(it.quantity)} ${it.unit}", priceCell(it)) }, setOf(2))
        return ReportDoc("Pvt List (Private)", "${l.size} entries \u2022 ${fmtDate(System.currentTimeMillis())}", listOf(Section("Items", table = t)), true)
    }

    /** Totals are kept per unit: pieces, kilograms and litres are never added together. */
    fun unitTotals(l: List<ListEntry>): List<Triple<String, Double, Int>> =
        l.groupBy { it.unit.lowercase() }.map { (_, g) -> Triple(g.first().unit, g.sumOf { it.quantity }, g.size) }.sortedBy { it.first.lowercase() }

    suspend fun pvtSummaryDoc(): ReportDoc {
        val l = db.lists().byTypeOnce(PVT)
        val ut = unitTotals(l)
        return ReportDoc("Pvt List Summary (Private)", fmtDate(System.currentTimeMillis()), listOf(
            Section("Summary", listOf("Pvt List entries: ${l.size}")),
            Section("Quantity by unit", table = Table(listOf("Unit", "Total quantity", "Entries"), listOf(2f, 2f, 1f), ut.map { listOf(it.first, fmtQty(it.second), "${it.third}") }, setOf(1, 2)))
        ), true)
    }

    /** Normal report: never contains Pvt Rate or any Pvt List row. */
    suspend fun activityDoc(ps: PeriodState): ReportDoc {
        val from = ps.from; val to = ps.to
        val recs = db.records().search("", from, to, 1000).first()
        val entryCount = db.records().entryCount(from, to).first()
        val byProduct = db.records().qtyByProduct(from, to).first()
        val byUnit = db.records().qtyByUnit(from, to).first()
        val normal = db.lists().byTypeOnce(NORMAL)
        val nUnits = unitTotals(normal)
        val secs = listOf(
            Section("Summary", listOf("Collections: ${recs.size}", "Product entries: $entryCount", "Normal List entries: ${normal.size}")),
            Section("Unit-wise quantity", table = Table(listOf("Unit", "Total quantity", "Entries"), listOf(2f, 2f, 1f), byUnit.map { listOf(it.unit, fmtQty(it.total), "${it.cnt}") }, setOf(1, 2))),
            Section("Product-wise quantity", table = Table(listOf("Product", "Unit", "Quantity", "Entries"), listOf(3f, 1.4f, 1.6f, 1f), byProduct.map { listOf(it.name, it.unit, fmtQty(it.total), "${it.cnt}") }, setOf(2, 3))),
            Section("Collections", table = Table(listOf("Date", "Title", "Shop", "Items"), listOf(1.6f, 2.6f, 2f, 0.8f),
                recs.take(60).map { listOf(fmtDate(it.record.createdAt), it.record.title.ifBlank { "-" }, it.record.shop.ifBlank { "-" }, "${it.entryCount}") }, setOf(3))),
            Section("Normal List summary", listOf("Quantity by unit (units are not added together)"),
                Table(listOf("Unit", "Total quantity", "Entries"), listOf(2f, 2f, 1f), nUnits.map { listOf(it.first, fmtQty(it.second), "${it.third}") }, setOf(1, 2)))
        )
        return ReportDoc("Activity Report", ps.label(), secs, false)
    }
}
