package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.AppUserEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase81UserOptimisticConcurrencyIntegrationTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun staleAdminUpdateCannotSilentlyOverwriteNewerUserMaster() = runBlocking {
        val dao = database.securityDao()
        val id = dao.insert(
            AppUserEntity(
                username = "phase81-user",
                displayName = "Original",
                pinHash = "hash",
                role = "ADMIN",
                isActive = true,
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
                rowVersion = 0L,
            ),
        )
        val versionA = dao.byId(id)!!.rowVersion
        val versionB = dao.byId(id)!!.rowVersion
        val a = dao.updateMasterCas(id, "phase81-user", "Admin A", "hash-a", "", "MANAGER", true, 0, 0L, 2L, versionA)
        val b = dao.updateMasterCas(id, "phase81-user", "Admin B", "hash-b", "", "ADMIN", true, 0, 0L, 3L, versionB)
        assertEquals(1, a)
        assertEquals(0, b)
        val current = dao.byId(id)!!
        assertEquals(1L, current.rowVersion)
        assertEquals("Admin A", current.displayName)
        assertTrue(current.role == "MANAGER")
    }
}
