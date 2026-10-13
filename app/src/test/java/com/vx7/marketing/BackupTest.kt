package com.vx7.marketing

import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    private fun sample() = Snapshot(
        products = listOf(Product(1, "White Box", "Box", "desc", "Piece", "", 50.0, 40.0, 40.0, 1L, 2L)),
        listEntries = listOf(
            ListEntry(1, NORMAL, 1L, "White Box", 10.0, "Piece", realRate = 50.0, lastRate = 40.0, createdAt = 1L, updatedAt = 1L),
            ListEntry(2, PVT, 1L, "White Box", 10.0, "Piece", pvtRate = 40.0, createdAt = 1L, updatedAt = 1L)
        ),
        records = listOf(CollectionRecord(1, 1000L, "Shop A", "Pune", "n", "Morning round")),
        recordEntries = listOf(Entry(1, 1, 1, "White Box", 5.0, "Box", "", 50.0, 40.0, 40.0)),
        notes = listOf(QuickNote(1, "T", "B", 1L, true, 2L)),
        includesPrivate = true
    )

    @Test fun roundTripWithPrivateKeepsEverything() {
        val back = Backup.parse(Backup.build(sample(), true))
        assertTrue(back.includesPrivate)
        assertEquals(1, back.products.size); assertEquals(40.0, back.products[0].pvtRate!!, 0.0)
        assertEquals(50.0, back.products[0].realRate!!, 0.0); assertEquals(40.0, back.products[0].lastRate!!, 0.0)
        assertEquals(2, back.listEntries.size)
        assertEquals(40.0, back.listEntries.first { it.listType == PVT }.pvtRate!!, 0.0)
        assertEquals(1, back.records.size); assertEquals(1, back.recordEntries.size)
        assertEquals(back.records[0].id, back.recordEntries[0].recordId)
        assertEquals("Morning round", back.records[0].title)
        assertTrue(back.notes[0].pinned)
    }

    @Test fun backupWithoutPrivateLeavesOutPvtListAndRates() {
        val text = Backup.build(sample(), false)
        assertFalse(text.contains("pvtRate"))
        val back = Backup.parse(text)
        assertFalse(back.includesPrivate)
        assertEquals(1, back.listEntries.size); assertEquals(NORMAL, back.listEntries[0].listType)
        assertNull(back.products[0].pvtRate); assertNull(back.recordEntries[0].pvtRate)
    }

    @Test fun rejectsDamagedFiles() {
        assertThrows(IllegalArgumentException::class.java) { Backup.parse("not json") }
        assertThrows(IllegalArgumentException::class.java) { Backup.parse("{\"format\":\"other\"}") }
        assertThrows(IllegalArgumentException::class.java) { Backup.parse("{\"format\":\"${Backup.FORMAT}\",\"version\":99,\"products\":[],\"collections\":[]}") }
        assertThrows(IllegalArgumentException::class.java) { Backup.parse("{\"format\":\"${Backup.FORMAT}\",\"version\":2,\"products\":[{\"name\":\"\"}],\"collections\":[]}") }
    }

    @Test fun readsOldVersion1Files() {
        val v1 = "{\"format\":\"${Backup.FORMAT}\",\"version\":1,\"products\":[{\"id\":3,\"name\":\"Soap\",\"category\":\"\",\"notes\":\"\"}]," +
            "\"records\":[{\"createdAt\":5,\"shop\":\"S\",\"location\":\"\",\"note\":\"\",\"entries\":[{\"productId\":3,\"productName\":\"Soap\",\"quantity\":2,\"unit\":\"Box\",\"note\":\"\"}]}],\"notes\":[]}"
        val s = Backup.parse(v1)
        assertEquals("Soap", s.products[0].name); assertEquals(1, s.records.size); assertEquals(2.0, s.recordEntries[0].quantity, 0.0)
    }
}
