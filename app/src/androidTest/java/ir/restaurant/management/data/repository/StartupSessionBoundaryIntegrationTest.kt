package ir.restaurant.management.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.AppSessionEntity
import ir.restaurant.management.data.db.AppUserEntity
import ir.restaurant.management.data.security.StartupSessionBoundary
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupSessionBoundaryIntegrationTest {
    private lateinit var database: AppDatabase

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() { database.close() }

    @Test
    fun persistedOrExpiredSession_isInvalidatedBeforeProtectedGraphCanUseIt() = runBlocking {
        val userId = database.securityDao().insert(
            AppUserEntity(
                username = "stale-session",
                displayName = "Stale Session",
                pinHash = "not-used-by-boundary-test",
                role = "OWNER",
                createdAtEpochMillis = 1,
                updatedAtEpochMillis = 1,
            ),
        )
        database.securityDao().setSession(AppSessionEntity(currentUserId = userId, updatedAtEpochMillis = 1))
        assertNotNull(database.securityDao().currentUser())

        StartupSessionBoundary.invalidatePersistedSession(database.openHelper.writableDatabase)

        assertNull(database.securityDao().currentUser())
    }
}
