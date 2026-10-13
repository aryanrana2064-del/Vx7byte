package com.vx7.marketing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

sealed interface Saved {
    class Ok(val id: Long) : Saved
    class Error(val message: String) : Saved
    /** Names that are very close to the one typed; the user may confirm and save anyway. */
    class Similar(val names: List<String>) : Saved
}

class ProductForm(
    val name: String, val category: String, val description: String, val unit: String, val customUnit: String,
    val photoPath: String, val realRate: String, val lastRate: String, val lastEdited: Boolean, val pvtRate: String,
    val force: Boolean
)

class EntryForm(
    val listType: String, val productId: Long?, val name: String, val qty: String, val unit: String, val customUnit: String,
    val rate: String, val lastRate: String, val lastEdited: Boolean, val notes: String
)

/** All database work. Every function is a suspend function that runs off the main thread (Room dispatches suspend DAOs itself). */
class Repo(val ctx: Context, val db: AppDb) {

    suspend fun log(type: String, message: String, route: String, notify: Boolean) {
        val delivered = if (notify) withContext(Dispatchers.Default) { Notifier.post(ctx, "VX7 Marketing", message, route) } else false
        try {
            db.activity().insert(ActivityLog(type = type, message = message, route = route, delivered = delivered, createdAt = System.currentTimeMillis()))
        } catch (_: Exception) { /* history must never break a save */ }
    }

    // ------------------------------------------------------------ products
    suspend fun saveProduct(old: Product?, f: ProductForm): Saved {
        val n = f.name.trim().replace(Regex("\\s+"), " ")
        if (n.isEmpty()) return Saved.Error("Enter a product name.")
        if (n.length > 80) return Saved.Error("Product name is too long (max 80 characters).")
        val unit = resolveUnit(f.unit, f.customUnit)
        if (unit.isEmpty()) return Saved.Error("Enter a custom unit name.")
        val (okR, real) = parseRate(f.realRate)
        if (!okR) return Saved.Error("Real Rate must be a number of 0 or more.")
        val (okL, last) = parseRate(f.lastRate)
        if (!okL) return Saved.Error("Last Rate must be a number of 0 or more.")
        val (okP, pvt) = parseRate(f.pvtRate)
        if (!okP) return Saved.Error("Pvt Rate must be a number of 0 or more.")
        return try {
            val others = db.products().allOnce().filter { it.id != (old?.id ?: -1L) }
            others.firstOrNull { normName(it.name) == normName(n) }?.let { return Saved.Error("A product named \"${it.name}\" already exists.") }
            if (!f.force) {
                val sim = others.filter { similarNames(it.name, n) }.map { it.name }
                if (sim.isNotEmpty()) return Saved.Similar(sim.take(4))
            }
            val now = System.currentTimeMillis()
            if (old == null) {
                val id = db.products().insert(
                    Product(name = n, category = f.category.trim(), notes = f.description.trim(), unit = unit, photoPath = f.photoPath,
                        realRate = real, lastRate = last, pvtRate = pvt, createdAt = now, updatedAt = now)
                )
                log("PRODUCT", "Product saved: $n", "product:$id", true)
                Saved.Ok(id)
            } else {
                val r = resolveRates(old.realRate, old.lastRate, real, last, f.lastEdited)
                db.products().update(
                    old.copy(name = n, category = f.category.trim(), notes = f.description.trim(), unit = unit, photoPath = f.photoPath,
                        realRate = r.real, lastRate = r.last, pvtRate = pvt, updatedAt = now)
                )
                log("PRODUCT", "Product updated: $n", "product:${old.id}", true)
                Saved.Ok(old.id)
            }
        } catch (e: Exception) {
            Saved.Error("Could not save the product: ${e.message ?: "database error"}")
        }
    }

    suspend fun deleteProduct(p: Product): String? = try {
        db.products().delete(p.id)
        if (p.photoPath.isNotEmpty()) withContext(Dispatchers.IO) { File(p.photoPath).delete() }
        log("PRODUCT", "Product deleted: ${p.name}", "products", false)
        null
    } catch (e: Exception) { "Could not delete: ${e.message}" }

    // ------------------------------------------------------------ Normal / Pvt list entries
    suspend fun saveEntry(old: ListEntry?, f: EntryForm): Saved {
        val n = f.name.trim().replace(Regex("\\s+"), " ")
        if (n.isEmpty()) return Saved.Error("Enter a name.")
        val q = parseQty(f.qty) ?: return Saved.Error("Enter a valid quantity greater than zero.")
        val unit = resolveUnit(f.unit, f.customUnit)
        if (unit.isEmpty()) return Saved.Error("Enter a custom unit name.")
        val (okR, rate) = parseRate(f.rate)
        if (!okR) return Saved.Error(if (f.listType == PVT) "Pvt Rate must be a number of 0 or more." else "Price must be a number of 0 or more.")
        val (okL, last) = parseRate(f.lastRate)
        if (!okL) return Saved.Error("Last Rate must be a number of 0 or more.")
        return try {
            val same = db.lists().byTypeOnce(f.listType).firstOrNull {
                it.id != (old?.id ?: -1L) && normName(it.name) == normName(n) && it.unit.equals(unit, true)
            }
            if (same != null) return Saved.Error("\"${same.name}\" (${same.unit}) is already in this list. Edit that entry instead.")
            val now = System.currentTimeMillis()
            val label = if (f.listType == PVT) "Pvt List" else "Normal List"
            if (old == null) {
                val e = if (f.listType == PVT)
                    ListEntry(listType = PVT, productId = f.productId, name = n, quantity = q, unit = unit, pvtRate = rate, notes = f.notes.trim(), createdAt = now, updatedAt = now)
                else
                    ListEntry(listType = NORMAL, productId = f.productId, name = n, quantity = q, unit = unit, realRate = rate, lastRate = last, notes = f.notes.trim(), createdAt = now, updatedAt = now)
                val id = db.lists().insert(e)
                log("LIST", "$label entry added: $n", "entry:$id", false)
                Saved.Ok(id)
            } else {
                val e = if (old.listType == PVT) {
                    // Pvt entries never touch real/last rate.
                    old.copy(productId = f.productId, name = n, quantity = q, unit = unit, pvtRate = rate, notes = f.notes.trim(), updatedAt = now)
                } else {
                    val r = resolveRates(old.realRate, old.lastRate, rate, last, f.lastEdited)
                    old.copy(productId = f.productId, name = n, quantity = q, unit = unit, realRate = r.real, lastRate = r.last, notes = f.notes.trim(), updatedAt = now)
                }
                db.lists().update(e)
                log("LIST", "$label entry updated: $n", "entry:${old.id}", false)
                Saved.Ok(old.id)
            }
        } catch (e: Exception) {
            Saved.Error("Could not save: ${e.message ?: "database error"}")
        }
    }

    suspend fun deleteEntry(e: ListEntry): String? = try {
        db.lists().delete(e.id)
        log("LIST", "${if (e.listType == PVT) "Pvt List" else "Normal List"} entry deleted: ${e.name}", if (e.listType == PVT) "pvt" else "normal", false)
        null
    } catch (ex: Exception) { "Could not delete: ${ex.message}" }

    // ------------------------------------------------------------ collections
    suspend fun saveCollection(r: CollectionRecord, es: List<Entry>, editing: Boolean): Long? {
        val id = if (!editing) db.records().saveNew(r, es) else { db.records().saveEdit(r, es); r.id }
        val label = r.title.ifBlank { r.shop.ifBlank { "Collection" } }
        log("COLLECTION", (if (editing) "Collection updated: " else "Collection saved: ") + label, "detail:$id", true)
        return id
    }

    suspend fun deleteCollection(id: Long, label: String): String? = try {
        db.records().delete(id)
        log("COLLECTION", "Collection deleted: $label", "history", false)
        null
    } catch (e: Exception) { "Could not delete: ${e.message}" }

    // ------------------------------------------------------------ notes
    suspend fun saveNote(old: QuickNote?, title: String, body: String): String? {
        if (title.isBlank() && body.isBlank()) return "Write a title or some text."
        return try {
            val now = System.currentTimeMillis()
            if (old == null) db.notes().add(QuickNote(title = title.trim(), body = body.trim(), createdAt = now, updatedAt = now))
            else db.notes().update(old.copy(title = title.trim(), body = body.trim(), updatedAt = now))
            null
        } catch (e: Exception) { "Could not save the note: ${e.message}" }
    }

    // ------------------------------------------------------------ photos (stored privately in the app's files folder)
    suspend fun savePhoto(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val dir = File(ctx.filesDir, "photos").apply { mkdirs() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
            val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                ?: return@withContext null
            val f = File(dir, "p_${UUID.randomUUID()}.jpg")
            FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            bmp.recycle()
            f.absolutePath
        } catch (e: Exception) { null }
    }

    suspend fun deletePhotoFile(path: String) { if (path.isNotEmpty()) withContext(Dispatchers.IO) { File(path).delete() } }

    // ------------------------------------------------------------ clear all
    suspend fun clearAll() {
        db.withTransaction {
            db.lists().deleteAll()
            db.records().deleteAllRecords()
            db.notes().deleteAll()
            db.products().deleteAll()
            db.activity().clear()
        }
        withContext(Dispatchers.IO) { File(ctx.filesDir, "photos").deleteRecursively() }
    }
}
