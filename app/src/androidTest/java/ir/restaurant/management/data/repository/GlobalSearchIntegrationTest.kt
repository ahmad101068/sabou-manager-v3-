package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.CustomerEntity
import ir.restaurant.management.data.db.InventoryItemEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import ir.restaurant.management.domain.search.GlobalSearchTarget
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlobalSearchIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var authorizer: SessionAuthorizer
    private lateinit var security: LocalSecurityRepository
    private var now = 1_800_200_000_000L

    @Before
    fun setUp() = runBlocking {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        authorizer = SessionAuthorizer(database)
        security = LocalSecurityRepository(database, authorizer = authorizer, clock = { now })
        security.save(null, UserDraft("phase7-search-owner", "مالک جست‌وجوی فاز هفت", "123456", UserRole.OWNER, "87654321"))
        Unit
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun searchesRealDatabaseWithPersianNormalizationAndTypedTargets() = runBlocking {
        val itemId = database.inventoryDao().insert(
            InventoryItemEntity(
                name = "چای کیسه‌ای ویژه",
                category = "نوشیدنی",
                unit = "عدد",
                sku = "TEA-123",
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        val customerId = database.salesDao().insertCustomer(
            CustomerEntity(
                customerCode = "C-456",
                name = "مشتری نمونه",
                phone = "07112345678",
                nationalId = "0012345678",
                creditLimitRial = 0,
                notes = "",
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )

        val repository = LocalGlobalSearchRepository(database, authorizer)

        val normalizedKafResults = repository.search("كي")
        assertTrue(
            normalizedKafResults.any { result ->
                result.target == GlobalSearchTarget.InventoryItem(itemId) && result.title.contains("کیسه")
            },
        )

        val persianDigitResults = repository.search("۱۲۳")
        assertTrue(persianDigitResults.any { it.target == GlobalSearchTarget.InventoryItem(itemId) })

        val customerResults = repository.search("مشتری نمونه")
        assertTrue(customerResults.any { it.target == GlobalSearchTarget.Customer(customerId) })
    }
}
