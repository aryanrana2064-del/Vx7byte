package com.vx7.marketing

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject

/** Everything that goes into / comes out of a backup file. Entities are used directly so relationships (ids) are kept. */
class Snapshot(
    val products: List<Product>,
    val listEntries: List<ListEntry>,
    val records: List<CollectionRecord>,
    val recordEntries: List<Entry>,
    val notes: List<QuickNote>,
    val includesPrivate: Boolean,
    val version: Int = Backup.VERSION,
    val createdAt: Long = System.currentTimeMillis()
)

enum class RestoreMode { MERGE, REPLACE }

class RestoreResult(val products: Int, val listEntries: Int, val collections: Int, val notes: Int, val skipped: Int)

/**
 * Versioned JSON backup.
 * - includePrivate = false  -> Pvt List rows and every Pvt Rate are left out of the file entirely.
 * - includePrivate = true   -> file is flagged "includesPrivate": true and contains them.
 * Restore runs inside one database transaction, so a failure leaves existing data untouched.
 */
object Backup {
    const val FORMAT = "VX7-Marketing-Backup"
    const val VERSION = 2

    // ---------------------------------------------------------------- build (pure)
    private fun JSONObject.putN(k: String, v: Double?): JSONObject = put(k, v ?: JSONObject.NULL)

    fun build(s: Snapshot, includePrivate: Boolean): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("createdAt", s.createdAt)
        root.put("includesPrivate", includePrivate)
        root.put("products", JSONArray().also { a ->
            s.products.forEach {
                a.put(JSONObject().put("id", it.id).put("name", it.name).put("category", it.category).put("description", it.notes)
                    .put("unit", it.unit).putN("realRate", it.realRate).putN("lastRate", it.lastRate)
                    .also { o -> if (includePrivate) o.putN("pvtRate", it.pvtRate) }
                    .put("createdAt", it.createdAt).put("updatedAt", it.updatedAt))
            }
        })
        root.put("listEntries", JSONArray().also { a ->
            s.listEntries.filter { includePrivate || it.listType != PVT }.forEach {
                a.put(JSONObject().put("id", it.id).put("listType", it.listType).put("productId", it.productId ?: JSONObject.NULL)
                    .put("name", it.name).put("quantity", it.quantity).put("unit", it.unit)
                    .putN("realRate", it.realRate).putN("lastRate", it.lastRate)
                    .also { o -> if (includePrivate) o.putN("pvtRate", it.pvtRate) }
                    .put("notes", it.notes).put("createdAt", it.createdAt).put("updatedAt", it.updatedAt))
            }
        })
        val byRec = s.recordEntries.groupBy { it.recordId }
        root.put("collections", JSONArray().also { a ->
            s.records.forEach { r ->
                val ea = JSONArray()
                (byRec[r.id] ?: emptyList()).forEach {
                    ea.put(JSONObject().put("productId", it.productId).put("productName", it.productName).put("quantity", it.quantity)
                        .put("unit", it.unit).put("note", it.note).putN("realRate", it.realRate).putN("lastRate", it.lastRate)
                        .also { o -> if (includePrivate) o.putN("pvtRate", it.pvtRate) })
                }
                a.put(JSONObject().put("id", r.id).put("title", r.title).put("createdAt", r.createdAt).put("shop", r.shop)
                    .put("location", r.location).put("note", r.note).put("entries", ea))
            }
        })
        root.put("notes", JSONArray().also { a ->
            s.notes.forEach {
                a.put(JSONObject().put("id", it.id).put("title", it.title).put("body", it.body).put("pinned", it.pinned)
                    .put("createdAt", it.createdAt).put("updatedAt", it.updatedAt))
            }
        })
        return root.toString(2)
    }

    suspend fun snapshot(db: AppDb): Snapshot = Snapshot(
        db.products().allOnce(), db.lists().allOnce(), db.records().allRecords(), db.records().allEntries(), db.notes().allOnce(), true
    )

    // ---------------------------------------------------------------- parse + validate (pure)
    private fun fail(m: String): Nothing = throw IllegalArgumentException(m)
    private fun JSONObject.dbl(k: String): Double? = if (!has(k) || isNull(k)) null else optDouble(k, Double.NaN).takeIf { it.isFinite() && it >= 0.0 }
    private fun JSONObject.lng(k: String): Long? = if (!has(k) || isNull(k)) null else optLong(k)

    /** Throws IllegalArgumentException with a readable message for any damaged or unsupported file. Nothing touches the database. */
    fun parse(text: String): Snapshot {
        val root = try { JSONObject(text) } catch (e: Exception) { fail("This file is not a valid JSON backup.") }
        if (root.optString("format") != FORMAT) fail("This is not a VX7 Marketing backup file.")
        val ver = root.optInt("version", -1)
        if (ver < 1 || ver > VERSION) fail("Unsupported backup version ($ver). Update the app and try again.")
        val now = System.currentTimeMillis()
        val pa = root.optJSONArray("products") ?: fail("Backup is missing the products list.")
        val products = (0 until pa.length()).map { i ->
            val o = pa.optJSONObject(i) ?: fail("Product #${i + 1} is damaged.")
            val name = o.optString("name").trim()
            if (name.isEmpty()) fail("Product #${i + 1} has no name.")
            Product(
                id = o.optLong("id", 0), name = name, category = o.optString("category"),
                notes = o.optString("description", o.optString("notes")), unit = o.optString("unit", "Piece").ifBlank { "Piece" },
                realRate = o.dbl("realRate"), lastRate = o.dbl("lastRate"), pvtRate = o.dbl("pvtRate"),
                createdAt = o.optLong("createdAt", now), updatedAt = o.optLong("updatedAt", now)
            )
        }
        val la = root.optJSONArray("listEntries") ?: JSONArray()
        val list = (0 until la.length()).map { i ->
            val o = la.optJSONObject(i) ?: fail("List entry #${i + 1} is damaged.")
            val type = o.optString("listType")
            if (type != NORMAL && type != PVT) fail("List entry #${i + 1} has an unknown list type.")
            val q = o.optDouble("quantity", Double.NaN)
            if (!(q > 0.0) || !q.isFinite()) fail("List entry #${i + 1} has an invalid quantity.")
            val name = o.optString("name").trim()
            val unit = o.optString("unit").trim()
            if (name.isEmpty() || unit.isEmpty()) fail("List entry #${i + 1} is missing a name or unit.")
            ListEntry(
                id = o.optLong("id", 0), listType = type, productId = o.lng("productId"), name = name, quantity = q, unit = unit,
                realRate = o.dbl("realRate"), lastRate = o.dbl("lastRate"), pvtRate = o.dbl("pvtRate"), notes = o.optString("notes"),
                createdAt = o.optLong("createdAt", now), updatedAt = o.optLong("updatedAt", now)
            )
        }
        val ra = root.optJSONArray("collections") ?: root.optJSONArray("records") ?: fail("Backup is missing the collections list.")
        val records = ArrayList<CollectionRecord>()
        val entries = ArrayList<Entry>()
        for (i in 0 until ra.length()) {
            val o = ra.optJSONObject(i) ?: fail("Collection #${i + 1} is damaged.")
            if (!o.has("createdAt")) fail("Collection #${i + 1} has no date.")
            val ea = o.optJSONArray("entries") ?: fail("Collection #${i + 1} has no entries list.")
            val rid = (i + 1).toLong() // local id used only to tie entries to their collection inside this file
            records.add(CollectionRecord(id = rid, createdAt = o.optLong("createdAt"), shop = o.optString("shop"), location = o.optString("location"), note = o.optString("note"), title = o.optString("title")))
            for (j in 0 until ea.length()) {
                val e = ea.optJSONObject(j) ?: fail("Collection #${i + 1}, entry #${j + 1} is damaged.")
                val q = e.optDouble("quantity", Double.NaN)
                if (!(q > 0.0) || !q.isFinite()) fail("Collection #${i + 1}, entry #${j + 1} has an invalid quantity.")
                val name = e.optString("productName").trim()
                val unit = e.optString("unit").trim()
                if (name.isEmpty() || unit.isEmpty()) fail("Collection #${i + 1}, entry #${j + 1} is missing a product or unit.")
                entries.add(Entry(recordId = rid, productId = e.optLong("productId", 0), productName = name, quantity = q, unit = unit, note = e.optString("note"),
                    realRate = e.dbl("realRate"), lastRate = e.dbl("lastRate"), pvtRate = e.dbl("pvtRate")))
            }
        }
        val na = root.optJSONArray("notes") ?: JSONArray()
        val notes = (0 until na.length()).map { i ->
            val o = na.optJSONObject(i) ?: fail("Note #${i + 1} is damaged.")
            val c = o.optLong("createdAt", now)
            QuickNote(title = o.optString("title"), body = o.optString("body"), createdAt = c, pinned = o.optBoolean("pinned", false), updatedAt = o.optLong("updatedAt", c))
        }
        return Snapshot(products, list, records, entries, notes, root.optBoolean("includesPrivate", false), ver, root.optLong("createdAt", now))
    }

    // ---------------------------------------------------------------- restore (one transaction)
    private fun entrySig(e: ListEntry) = "${e.listType}|${e.name.lowercase()}|${e.unit.lowercase()}|${e.quantity}|${e.realRate}|${e.lastRate}|${e.pvtRate}|${e.createdAt}"
    private fun recSig(r: CollectionRecord, es: List<Entry>) =
        "${r.createdAt}|${r.title}|${r.shop}|${r.location}|${r.note}|" + es.joinToString(";") { "${it.productName}~${it.quantity}~${it.unit}" }

    /**
     * REPLACE: clears every data table first, then inserts the file. MERGE: never deletes or overwrites; products are matched by name,
     * everything already present (same content) is skipped. Any exception rolls the whole thing back.
     */
    suspend fun restore(db: AppDb, s: Snapshot, mode: RestoreMode): RestoreResult = db.withTransaction {
        if (mode == RestoreMode.REPLACE) {
            db.lists().deleteAll(); db.records().deleteAllRecords(); db.notes().deleteAll(); db.products().deleteAll()
        }
        val byName = db.products().allOnce().associateBy { normName(it.name) }.toMutableMap()
        val idMap = HashMap<Long, Long>()
        var skipped = 0
        var pAdded = 0
        for (p in s.products) {
            val hit = byName[normName(p.name)]
            if (hit != null) { idMap[p.id] = hit.id; continue }
            val newId = db.products().insert(p.copy(id = 0))
            byName[normName(p.name)] = p.copy(id = newId)
            idMap[p.id] = newId
            pAdded++
        }
        val haveEntries = db.lists().allOnce().map { entrySig(it) }.toMutableSet()
        var eAdded = 0
        for (e in s.listEntries) {
            val pid = e.productId?.let { idMap[it] }
            val fixed = e.copy(id = 0, productId = pid)
            if (!haveEntries.add(entrySig(fixed))) { skipped++; continue }
            db.lists().insert(fixed); eAdded++
        }
        val dao = db.records()
        val existingEntries = dao.allEntries().groupBy { it.recordId }
        val haveRecs = dao.allRecords().map { recSig(it, existingEntries[it.id] ?: emptyList()) }.toMutableSet()
        val entriesByRec = s.recordEntries.groupBy { it.recordId }
        var cAdded = 0
        for (r in s.records) {
            val es = entriesByRec[r.id] ?: emptyList()
            if (!haveRecs.add(recSig(r, es))) { skipped++; continue }
            val rid = dao.insertRecord(r.copy(id = 0))
            dao.insertEntries(es.map { it.copy(id = 0, recordId = rid, productId = idMap[it.productId] ?: 0L) })
            cAdded++
        }
        val haveNotes = db.notes().allOnce().map { "${it.createdAt}|${it.title}|${it.body}" }.toMutableSet()
        var nAdded = 0
        for (n in s.notes) {
            if (!haveNotes.add("${n.createdAt}|${n.title}|${n.body}")) { skipped++; continue }
            db.notes().add(n.copy(id = 0)); nAdded++
        }
        RestoreResult(pAdded, eAdded, cAdded, nAdded, skipped)
    }
}
