package com.vx7.marketing

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

const val NORMAL = "NORMAL"
const val PVT = "PVT"

/** A product. realRate = current rate, lastRate = previous realRate, pvtRate = private rate (independent of the other two). */
@Entity(indices = [Index(value = ["name"], unique = true)])
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String = "",
    /** Product description (column kept as "notes" for compatibility with older installs). */
    @ColumnInfo(defaultValue = "''") val notes: String = "",
    @ColumnInfo(defaultValue = "'Piece'") val unit: String = "Piece",
    @ColumnInfo(defaultValue = "''") val photoPath: String = "",
    val realRate: Double? = null,
    val lastRate: Double? = null,
    val pvtRate: Double? = null,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0
)

/** One row of the Normal List (listType NORMAL, shows realRate) or the Pvt List (listType PVT, shows pvtRate). Separate rows, never shared. */
@Entity(
    tableName = "ListEntry",
    foreignKeys = [ForeignKey(entity = Product::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("productId"), Index("listType")]
)
data class ListEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listType: String,
    val productId: Long? = null,
    val name: String,
    val quantity: Double,
    val unit: String,
    val realRate: Double? = null,
    val lastRate: Double? = null,
    val pvtRate: Double? = null,
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long
)

@Entity
data class CollectionRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val shop: String = "",
    @ColumnInfo(defaultValue = "''") val location: String = "",
    val note: String = "",
    @ColumnInfo(defaultValue = "''") val title: String = ""
)

/** productName is a snapshot so history stays correct when a product is renamed or deleted. */
@Entity(
    foreignKeys = [ForeignKey(entity = CollectionRecord::class, parentColumns = ["id"], childColumns = ["recordId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("recordId")]
)
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Double,
    val unit: String,
    @ColumnInfo(defaultValue = "''") val note: String = "",
    val realRate: Double? = null,
    val lastRate: Double? = null,
    val pvtRate: Double? = null
)

@Entity
data class QuickNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0
)

/** Persistent in-app notification / activity history. delivered = a system notification was actually posted. */
@Entity
data class ActivityLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val message: String,
    val route: String,
    val delivered: Boolean,
    val createdAt: Long
)

data class RecordRow(@Embedded val record: CollectionRecord, val entryCount: Int)
data class QtyRow(val name: String, val unit: String, val total: Double, val cnt: Int)
data class UnitRow(val unit: String, val total: Double, val cnt: Int)

@Dao
interface ProductDao {
    @Query("SELECT * FROM Product ORDER BY name COLLATE NOCASE") fun observeAll(): Flow<List<Product>>
    @Query("SELECT * FROM Product WHERE id = :id") fun observe(id: Long): Flow<Product?>
    @Query("SELECT * FROM Product WHERE id = :id") suspend fun get(id: Long): Product?
    @Query("SELECT * FROM Product") suspend fun allOnce(): List<Product>
    @Query("SELECT COUNT(*) FROM Product") fun count(): Flow<Int>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(p: Product): Long
    @Update suspend fun update(p: Product)
    @Query("DELETE FROM Product WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM Product") suspend fun deleteAll()
}

@Dao
interface ListDao {
    @Query("SELECT * FROM ListEntry WHERE listType = :type ORDER BY name COLLATE NOCASE") fun observe(type: String): Flow<List<ListEntry>>
    @Query("SELECT * FROM ListEntry WHERE id = :id") fun observeOne(id: Long): Flow<ListEntry?>
    @Query("SELECT * FROM ListEntry WHERE id = :id") suspend fun get(id: Long): ListEntry?
    @Query("SELECT * FROM ListEntry WHERE listType = :type") suspend fun byTypeOnce(type: String): List<ListEntry>
    @Query("SELECT * FROM ListEntry") suspend fun allOnce(): List<ListEntry>
    @Query("SELECT COUNT(*) FROM ListEntry WHERE listType = :type") fun count(type: String): Flow<Int>
    @Insert suspend fun insert(e: ListEntry): Long
    @Update suspend fun update(e: ListEntry)
    @Query("DELETE FROM ListEntry WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM ListEntry") suspend fun deleteAll()
}

@Dao
abstract class RecordDao {
    @Query(
        "SELECT r.*, (SELECT COUNT(*) FROM Entry e WHERE e.recordId = r.id) AS entryCount FROM CollectionRecord r " +
            "WHERE r.createdAt BETWEEN :from AND :to AND (:q = '' OR r.shop LIKE '%' || :q || '%' OR r.title LIKE '%' || :q || '%' " +
            "OR r.id IN (SELECT recordId FROM Entry WHERE productName LIKE '%' || :q || '%')) " +
            "ORDER BY r.createdAt DESC LIMIT :limit"
    )
    abstract fun search(q: String, from: Long, to: Long, limit: Int): Flow<List<RecordRow>>

    @Query(
        "SELECT e.productName AS name, e.unit AS unit, SUM(e.quantity) AS total, COUNT(*) AS cnt FROM Entry e JOIN CollectionRecord r ON r.id = e.recordId " +
            "WHERE r.createdAt BETWEEN :from AND :to GROUP BY e.productName COLLATE NOCASE, e.unit ORDER BY e.productName COLLATE NOCASE, e.unit"
    )
    abstract fun qtyByProduct(from: Long, to: Long): Flow<List<QtyRow>>

    @Query(
        "SELECT e.unit AS unit, SUM(e.quantity) AS total, COUNT(*) AS cnt FROM Entry e JOIN CollectionRecord r ON r.id = e.recordId " +
            "WHERE r.createdAt BETWEEN :from AND :to GROUP BY e.unit ORDER BY e.unit"
    )
    abstract fun qtyByUnit(from: Long, to: Long): Flow<List<UnitRow>>

    @Query("SELECT COUNT(*) FROM CollectionRecord WHERE createdAt BETWEEN :from AND :to") abstract fun recordCount(from: Long, to: Long): Flow<Int>
    @Query("SELECT COUNT(*) FROM Entry e JOIN CollectionRecord r ON r.id = e.recordId WHERE r.createdAt BETWEEN :from AND :to") abstract fun entryCount(from: Long, to: Long): Flow<Int>
    @Query("SELECT COUNT(*) FROM CollectionRecord") abstract fun totalRecords(): Flow<Int>
    @Query("SELECT COUNT(*) FROM Entry") abstract fun totalEntries(): Flow<Int>

    @Query("SELECT * FROM CollectionRecord WHERE id = :id") abstract fun recordFlow(id: Long): Flow<CollectionRecord?>
    @Query("SELECT * FROM CollectionRecord WHERE id = :id") abstract suspend fun get(id: Long): CollectionRecord?
    @Query("SELECT * FROM Entry WHERE recordId = :id ORDER BY id") abstract fun entriesFlow(id: Long): Flow<List<Entry>>
    @Query("SELECT * FROM Entry WHERE recordId = :id ORDER BY id") abstract suspend fun entries(id: Long): List<Entry>
    @Query("SELECT * FROM CollectionRecord ORDER BY createdAt") abstract suspend fun allRecords(): List<CollectionRecord>
    @Query("SELECT * FROM Entry ORDER BY id") abstract suspend fun allEntries(): List<Entry>

    @Insert abstract suspend fun insertRecord(r: CollectionRecord): Long
    @Update abstract suspend fun updateRecord(r: CollectionRecord)
    @Insert abstract suspend fun insertEntries(e: List<Entry>)
    @Query("DELETE FROM Entry WHERE recordId = :id") abstract suspend fun deleteEntries(id: Long)
    @Query("DELETE FROM CollectionRecord WHERE id = :id") abstract suspend fun delete(id: Long)
    @Query("DELETE FROM CollectionRecord") abstract suspend fun deleteAllRecords()

    @Transaction
    open suspend fun saveNew(r: CollectionRecord, es: List<Entry>): Long {
        val id = insertRecord(r.copy(id = 0))
        insertEntries(es.map { it.copy(id = 0, recordId = id) })
        return id
    }

    @Transaction
    open suspend fun saveEdit(r: CollectionRecord, es: List<Entry>) {
        updateRecord(r)
        deleteEntries(r.id)
        insertEntries(es.map { it.copy(id = 0, recordId = r.id) })
    }
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM QuickNote ORDER BY pinned DESC, updatedAt DESC") fun all(): Flow<List<QuickNote>>
    @Query("SELECT * FROM QuickNote") suspend fun allOnce(): List<QuickNote>
    @Insert suspend fun add(n: QuickNote): Long
    @Update suspend fun update(n: QuickNote)
    @Query("DELETE FROM QuickNote WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM QuickNote") suspend fun deleteAll()
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM ActivityLog ORDER BY createdAt DESC, id DESC LIMIT 500") fun all(): Flow<List<ActivityLog>>
    @Query("SELECT COUNT(*) FROM ActivityLog WHERE createdAt BETWEEN :from AND :to") fun countBetween(from: Long, to: Long): Flow<Int>
    @Insert suspend fun insert(a: ActivityLog): Long
    @Query("DELETE FROM ActivityLog") suspend fun clear()
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Product ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE CollectionRecord ADD COLUMN location TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE Entry ADD COLUMN note TEXT NOT NULL DEFAULT ''")
    }
}

/** Adds rates, units, photos, titles, pinned notes, the two lists and the activity log. Existing rows are kept. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Product ADD COLUMN unit TEXT NOT NULL DEFAULT 'Piece'")
        db.execSQL("ALTER TABLE Product ADD COLUMN photoPath TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE Product ADD COLUMN realRate REAL")
        db.execSQL("ALTER TABLE Product ADD COLUMN lastRate REAL")
        db.execSQL("ALTER TABLE Product ADD COLUMN pvtRate REAL")
        db.execSQL("ALTER TABLE Product ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE Product ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE Product SET createdAt = " + System.currentTimeMillis() + ", updatedAt = " + System.currentTimeMillis())
        db.execSQL("ALTER TABLE CollectionRecord ADD COLUMN title TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE Entry ADD COLUMN realRate REAL")
        db.execSQL("ALTER TABLE Entry ADD COLUMN lastRate REAL")
        db.execSQL("ALTER TABLE Entry ADD COLUMN pvtRate REAL")
        db.execSQL("ALTER TABLE QuickNote ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE QuickNote ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE QuickNote SET updatedAt = createdAt")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ListEntry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `listType` TEXT NOT NULL, `productId` INTEGER, " +
                "`name` TEXT NOT NULL, `quantity` REAL NOT NULL, `unit` TEXT NOT NULL, `realRate` REAL, `lastRate` REAL, `pvtRate` REAL, `notes` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`productId`) REFERENCES `Product`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ListEntry_productId` ON `ListEntry` (`productId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ListEntry_listType` ON `ListEntry` (`listType`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `ActivityLog` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `message` TEXT NOT NULL, " +
                "`route` TEXT NOT NULL, `delivered` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
    }
}

@Database(
    entities = [Product::class, ListEntry::class, CollectionRecord::class, Entry::class, QuickNote::class, ActivityLog::class],
    version = 3, exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun products(): ProductDao
    abstract fun lists(): ListDao
    abstract fun records(): RecordDao
    abstract fun notes(): NoteDao
    abstract fun activity(): ActivityDao

    companion object {
        @Volatile private var instance: AppDb? = null
        fun get(c: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "vx7-marketing.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { instance = it }
        }
    }
}
