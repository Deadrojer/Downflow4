package com.downflow.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.downflow.app.aria2.DownloadState
import com.downflow.app.data.db.AppDatabase
import com.downflow.app.data.db.DownloadHistoryDao
import com.downflow.app.data.db.DownloadHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation test for [DownloadHistoryDao] using an in-memory Room database.
 * These tests run on an Android device or emulator.
 */
@RunWith(AndroidJUnit4::class)
class DownloadHistoryDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: DownloadHistoryDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.downloadHistoryDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private fun entity(
        gid: String = "abc123",
        fileName: String = "ubuntu.iso",
        url: String = "https://releases.ubuntu.com/ubuntu.iso",
        state: DownloadState = DownloadState.COMPLETE,
        totalBytes: Long = 1_000_000L,
        completedAtMs: Long = System.currentTimeMillis(),
    ) = DownloadHistoryEntity(
        gid = gid,
        fileName = fileName,
        url = url,
        destinationPath = "/sdcard/Download/$fileName",
        totalBytes = totalBytes,
        completedBytes = totalBytes,
        stateOrdinal = state.ordinal,
        errorMessage = null,
        isTorrent = false,
        torrentName = null,
        completedAtMs = completedAtMs,
    )

    // ─── Insert / observe ─────────────────────────────────────────────────────

    @Test
    fun insertAndObserve() = runTest {
        val e = entity()
        dao.upsert(e)
        val all = dao.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("abc123", all[0].gid)
        assertEquals("ubuntu.iso", all[0].fileName)
    }

    @Test
    fun upsertUpdatesExistingRow() = runTest {
        dao.upsert(entity(totalBytes = 500L))
        dao.upsert(entity(totalBytes = 1_000_000L))   // same gid → update
        val all = dao.observeAll().first()
        assertEquals(1, all.size)
        assertEquals(1_000_000L, all[0].totalBytes)
    }

    @Test
    fun multipleEntriesOrdered() = runTest {
        dao.upsert(entity(gid = "a", completedAtMs = 1000L))
        dao.upsert(entity(gid = "b", completedAtMs = 3000L))
        dao.upsert(entity(gid = "c", completedAtMs = 2000L))
        val all = dao.observeAll().first()
        // should be newest first
        assertEquals("b", all[0].gid)
        assertEquals("c", all[1].gid)
        assertEquals("a", all[2].gid)
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    @Test
    fun deleteByGidRemovesOnlyThatRow() = runTest {
        dao.upsert(entity(gid = "x"))
        dao.upsert(entity(gid = "y"))
        dao.deleteByGid("x")
        val all = dao.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("y", all[0].gid)
    }

    @Test
    fun deleteAllClearsTable() = runTest {
        dao.upsert(entity(gid = "x"))
        dao.upsert(entity(gid = "y"))
        dao.deleteAll()
        val all = dao.observeAll().first()
        assertTrue(all.isEmpty())
    }

    // ─── Count ────────────────────────────────────────────────────────────────

    @Test
    fun countReturnsCorrectNumber() = runTest {
        assertEquals(0, dao.count())
        dao.upsert(entity(gid = "1"))
        dao.upsert(entity(gid = "2"))
        assertEquals(2, dao.count())
    }

    // ─── Error states stored correctly ───────────────────────────────────────

    @Test
    fun errorEntityPreservesMessage() = runTest {
        val e = entity(gid = "err", state = DownloadState.ERROR).copy(
            errorMessage = "Connection reset by peer"
        )
        dao.upsert(e)
        val loaded = dao.observeAll().first()[0]
        assertEquals(DownloadState.ERROR.ordinal, loaded.stateOrdinal)
        assertEquals("Connection reset by peer", loaded.errorMessage)
    }

    // ─── Torrent entry ────────────────────────────────────────────────────────

    @Test
    fun torrentEntityRoundTrip() = runTest {
        val e = entity(gid = "tor1", fileName = "My.Movie.2024").copy(
            isTorrent = true,
            torrentName = "My.Movie.2024"
        )
        dao.upsert(e)
        val loaded = dao.observeAll().first()[0]
        assertTrue(loaded.isTorrent)
        assertEquals("My.Movie.2024", loaded.torrentName)
    }
}
