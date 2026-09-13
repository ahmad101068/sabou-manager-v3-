package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.FixedAssetEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.assets.AssetDraft
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssetOutboxIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var authorizer: SessionAuthorizer
    private val now = 2_000_000L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = AppDatabase.createInMemory(context)
        authorizer = SessionAuthorizer(database)
        val security = LocalSecurityRepository(database, clock = { now }, authorizer = authorizer)
        val ownerId = security.save(null, UserDraft("owner", "مالک", "123456", UserRole.OWNER, "87654321"))
        security.switchUser(ownerId, "123456")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun recorderFailureRollsBackAssetDisposal() = runBlocking {
        val failingRecorder = object : SyncRecorder(database, "test-device") {
            override suspend fun record(entityType: String, entityId: Long, changeType: String, occurredAt: Long, payloadFields: Map<String, String>, recordAudit: Boolean) {
                error("forced outbox failure")
            }
        }
        val id = LocalAssetRepository(database, clock = { now }, syncRecorder = null, authorizer = authorizer).save(null, draft("A-1"))
        val repository = LocalAssetRepository(database, clock = { now }, syncRecorder = failingRecorder, authorizer = authorizer)
        try {
            repository.dispose(id)
            fail("Expected disposal failure")
        } catch (_: IllegalStateException) {
            // expected
        }
        assertEquals("ACTIVE", database.assetDao().assetById(id)?.status)
        assertTrue(database.syncDao().pending(now, 10).isEmpty())
    }

    @Test
    fun successfulDisposalCommitsAssetAndVersionedOutboxTogether() = runBlocking {
        val id = LocalAssetRepository(database, clock = { now }, syncRecorder = null, authorizer = authorizer)
            .save(null, draft("A-2"))
        val repository = LocalAssetRepository(database, clock = { now }, syncRecorder = SyncRecorder(database, "test-device"), authorizer = authorizer)
        repository.dispose(id)
        assertEquals("DISPOSED", database.assetDao().assetById(id)?.status)
        val changes = database.syncDao().pending(now, 10)
        assertEquals(1, changes.size)
        val change = changes.single()
        assertEquals("DISPOSE", change.changeType)
        assertEquals(1L, change.revision)
        assertEquals(1, change.payloadVersion)
        assertEquals(64, change.payloadHash.length)
        assertTrue(change.payload.isNotBlank())
        val acquisition = database.accountingDao().entryBySource("ASSET_ACQUISITION", id)
        assertTrue(acquisition != null)
        val disposal = database.accountingDao().entryBySource("ASSET_DISPOSAL", id)
        assertTrue(disposal != null)
        val disposalLines = database.accountingDao().linesByEntry(requireNotNull(disposal).id)
        assertEquals(disposalLines.sumOf { it.debitRial }, disposalLines.sumOf { it.creditRial })

        try {
            repository.dispose(id)
            fail("Repeated disposal must be rejected")
        } catch (_: IllegalArgumentException) {
            // The already-disposed aggregate cannot emit another domain event.
        }
        val afterReplay = database.syncDao().pending(now, 10)
        assertEquals(1, afterReplay.size)
        assertEquals(change.changeId, afterReplay.single().changeId)
    }

    @Test
    fun legacyAssetCanBeRecognizedAsOpeningBalanceWithoutChangingCash() = runBlocking {
        val legacyId = database.assetDao().insertAsset(
            FixedAssetEntity(
                assetCode = "LEGACY-1",
                name = "دارایی قدیمی",
                category = "تجهیزات",
                purchaseEpochDay = 19_000,
                purchaseCostRial = 5_000_000,
                salvageValueRial = 500_000,
                usefulLifeMonths = 60,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        val repository = LocalAssetRepository(database, clock = { now }, syncRecorder = null, authorizer = authorizer)

        repository.recognizeImportedAsset(legacyId)

        val entry = requireNotNull(database.accountingDao().entryBySource("ASSET_ACQUISITION", legacyId))
        val lines = database.accountingDao().linesByEntry(entry.id)
        assertEquals(5_000_000L, lines.single { it.accountCode == "1501" }.debitRial)
        assertEquals(5_000_000L, lines.single { it.accountCode == "3101" }.creditRial)
    }

    private fun draft(code: String) = AssetDraft(code, "دارایی تست", "تجهیزات", 1, 20_000, 1_000_000, 100_000, 24, "آشپزخانه", "")
}
