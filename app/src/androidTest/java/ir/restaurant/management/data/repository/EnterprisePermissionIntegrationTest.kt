package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.core.CorrelationId
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.QuantityMicros
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.BranchEntity
import ir.restaurant.management.data.db.CustomerEntity
import ir.restaurant.management.data.db.InventoryItemEntity
import ir.restaurant.management.data.security.AccessDeniedException
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.data.treasury.DefaultTreasuryAccountCatalog
import ir.restaurant.management.data.treasury.LocalTreasuryServiceV2
import ir.restaurant.management.domain.assets.AssetAcquisitionSource
import ir.restaurant.management.domain.assets.AssetDraft
import ir.restaurant.management.domain.assets.AssetTransferDraft
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import ir.restaurant.management.domain.recipe.RecipeDraftInput
import ir.restaurant.management.domain.recipe.RecipeIngredientInput
import ir.restaurant.management.domain.security.Permission
import ir.restaurant.management.domain.treasury.TreasuryAccountId
import ir.restaurant.management.domain.treasury.TreasuryChannel
import ir.restaurant.management.domain.treasury.TreasuryBusinessIntent
import ir.restaurant.management.domain.treasury.TreasuryCommand
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EnterprisePermissionIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var authorizer: SessionAuthorizer
    private lateinit var security: LocalSecurityRepository
    private var now = 5_000_000L
    private val today = 23_000L
    private var ownerId: Long = 0
    private var cashierId: Long = 0
    private var storekeeperId: Long = 0

    @Before
    fun setUp(): Unit = runBlocking {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        authorizer = SessionAuthorizer(database)
        security = LocalSecurityRepository(database, authorizer = authorizer, clock = { ++now })
        ownerId = security.save(null, UserDraft("owner-perm", "مالک مجوز", "123456", UserRole.OWNER, "87654321"))
        security.switchUser(ownerId, "123456")
        cashierId = security.save(null, UserDraft("cashier-perm", "صندوقدار مجوز", "654321", UserRole.CASHIER))
        storekeeperId = security.save(null, UserDraft("storekeeper-perm", "انباردار مجوز", "456789", UserRole.STOREKEEPER))
        security.switchUser(ownerId, "123456")
        val branchOne = requireNotNull(database.branchDao().byId(1L))
        database.branchDao().update(branchOne.copy(code = "P1", name = "شعبه یک", updatedAtEpochMillis = now))
        database.branchDao().insert(BranchEntity(id = 2L, globalId = "test:permission-branch:2", code = "P2", name = "شعبه دو", createdAtEpochMillis = now, updatedAtEpochMillis = now))
        val scopeDb = database.openHelper.writableDatabase
        listOf(ownerId, cashierId, storekeeperId).forEach { userId ->
            scopeDb.execSQL(
                "INSERT OR REPLACE INTO user_scope_profiles(userId, primaryBranchId, updatedAtEpochMillis) VALUES (?, 1, ?)",
                arrayOf<Any?>(userId, now),
            )
            listOf(1L, 2L).forEach { branchId ->
                scopeDb.execSQL(
                    "INSERT OR IGNORE INTO user_branch_scopes(userId, branchId, createdAtEpochMillis) VALUES (?, ?, ?)",
                    arrayOf<Any?>(userId, branchId, now),
                )
            }
        }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun treasuryReceipt_requiresTreasuryPermissionAtDomainBoundary() = runBlocking {
        val service = LocalTreasuryServiceV2(
            database = database,
            accounting = LocalAccountingPostingEngine(database, clock = { ++now }),
            authorizer = authorizer,
            accountCatalog = DefaultTreasuryAccountCatalog(),
            clock = { ++now },
        )
        security.switchUser(cashierId, "654321")
        val commandId = GlobalId.new()

        expectDenied(Permission.TREASURY) {
            service.execute(
                TreasuryCommand.Receipt(
                    commandId = commandId,
                    businessEpochDay = today,
                    correlationId = CorrelationId.forCommand("permission_test", commandId),
                    businessIntent = TreasuryBusinessIntent.OTHER_INCOME,
                    sourceId = 99,
                    reason = "آزمون مجوز خزانه",
                    accountId = TreasuryAccountId.parse("cash_main"),
                    channel = TreasuryChannel.CASH,
                    amount = MoneyRial.of(10_000L),
                ),
            )
        }
        assertEquals(0L, scalar("SELECT COUNT(*) FROM treasury_transactions"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM journal_entries WHERE sourceType='OTHER_INCOME'"))
    }

@Test
    fun customerMerge_requiresCustomerMergePermission_andDoesNotMoveReferencesOnDenial() = runBlocking {
        val source = insertCustomer("CUS-PERM-1", "مشتری یک")
        val target = insertCustomer("CUS-PERM-2", "مشتری دو")
        val service = LocalCustomerAccountService(database, authorizer, clock = { ++now })
        security.switchUser(cashierId, "654321")

        expectDenied(Permission.CUSTOMER_MERGE) {
            service.merge(source, target, "آزمون منع ادغام مشتری")
        }
        assertEquals("ACTIVE", requireNotNull(database.salesDao().customerById(source)).status)
        assertEquals(0L, scalar("SELECT COUNT(*) FROM customer_merge_history"))
    }

    @Test
    fun assetTransfer_requiresLifecyclePermission_andLeavesAssetUntouchedOnDenial() = runBlocking {
        val assets = LocalAssetRepository(database, clock = { ++now }, authorizer = authorizer)
        val assetId = assets.save(
            null,
            AssetDraft(
                assetCode = "AST-PERM-1",
                name = "دارایی مجوز",
                category = "تجهیزات",
                quantity = 1,
                purchaseEpochDay = today,
                purchaseCostRial = 2_000_000L,
                salvageValueRial = 200_000L,
                usefulLifeMonths = 24,
                location = "محل اولیه",
                notes = "",
                acquisitionSource = AssetAcquisitionSource.BANK,
                branch = "شعبه یک",
                responsiblePerson = "مسئول یک",
                branchId = 1L,
            ),
        )
        security.switchUser(cashierId, "654321")

        expectDenied(Permission.ASSET_LIFECYCLE) {
            assets.transfer(AssetTransferDraft(assetId, "محل جدید", "شعبه دو", "مسئول دو", today + 1, "آزمون منع انتقال", toBranchId = 2L))
        }
        val asset = requireNotNull(database.assetDao().assetById(assetId))
        assertEquals("محل اولیه", asset.location)
        assertEquals("شعبه یک", asset.branch)
        assertEquals(0L, scalar("SELECT COUNT(*) FROM asset_lifecycle_events WHERE assetId=$assetId AND eventType='TRANSFER'"))
    }

    @Test
    fun recipeActivation_requiresDedicatedPermission_evenWhenRoleCanEditRecipes() = runBlocking {
        val itemId = database.inventoryDao().insert(
            InventoryItemEntity(
                name = "ماده مجوز رسپی",
                category = "TEST",
                unit = "عدد",
                alertEnabled = false,
                createdAtEpochMillis = ++now,
                updatedAtEpochMillis = ++now,
            ),
        )
        val recipes = LocalRecipeRepository(database, authorizer = authorizer, clock = { ++now }, epochDay = { today })
        val menuId = recipes.saveMenuItem(null, "غذای مجوز", "TEST", 200_000L, listOf(RecipeIngredientInput(itemId, QuantityMicros.SCALE)))
        security.switchUser(storekeeperId, "456789")
        val draftId = recipes.createDraft(
            RecipeDraftInput(menuId, listOf(RecipeIngredientInput(itemId, QuantityMicros.SCALE)), note = "ویرایش مجاز"),
        )

        expectDenied(Permission.RECIPE_ACTIVATE) {
            recipes.activate(draftId, today)
        }
        assertEquals("DRAFT", requireNotNull(database.recipeLifecycleDao().versionById(draftId)).status)
    }

    private suspend fun insertCustomer(code: String, name: String): Long = database.salesDao().insertCustomer(
        CustomerEntity(
            customerCode = code,
            name = name,
            phone = "",
            nationalId = "",
            creditLimitRial = 0,
            notes = "",
            createdAtEpochMillis = ++now,
            updatedAtEpochMillis = ++now,
        ),
    )

    private suspend fun expectDenied(permission: Permission, block: suspend () -> Unit) {
        try {
            block()
            fail("Expected AccessDeniedException for $permission")
        } catch (denied: AccessDeniedException) {
            assertEquals(permission, denied.permission)
        }
    }

    private fun scalar(sql: String): Long = database.openHelper.writableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }
}
