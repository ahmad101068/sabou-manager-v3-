package ir.restaurant.management.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.data.db.AppDatabase
import ir.restaurant.management.data.db.BranchEntity
import ir.restaurant.management.data.db.CustomerEntity
import ir.restaurant.management.data.db.CustomerReceivableLedgerEntity
import ir.restaurant.management.data.db.InventoryItemEntity
import ir.restaurant.management.data.db.InventoryBalanceEntity
import ir.restaurant.management.data.db.InventoryLotEntity
import ir.restaurant.management.data.db.MenuItemEntity
import ir.restaurant.management.data.db.RecipeVersionEntity
import ir.restaurant.management.data.db.RecipeVersionIngredientEntity
import ir.restaurant.management.data.db.StockMovementEntity
import ir.restaurant.management.data.db.StorageLocationEntity
import ir.restaurant.management.data.security.SessionAuthorizer
import ir.restaurant.management.domain.control.ActualCostDataQuality
import ir.restaurant.management.domain.brief.DailyManagementKpiReadModelFactory
import ir.restaurant.management.data.treasury.DefaultTreasuryAccountCatalog
import ir.restaurant.management.data.treasury.LocalTreasuryServiceV2
import ir.restaurant.management.domain.assets.AssetAcquisitionSource
import ir.restaurant.management.domain.assets.AssetDraft
import ir.restaurant.management.domain.assets.AssetMaintenanceDraft
import ir.restaurant.management.domain.personnel.ApprovePayrollBatchCommand
import ir.restaurant.management.domain.personnel.AttendanceDraft
import ir.restaurant.management.domain.personnel.CalculatePayrollBatchCommand
import ir.restaurant.management.domain.personnel.EmployeeContractDraft
import ir.restaurant.management.domain.personnel.EmployeeDraft
import ir.restaurant.management.domain.personnel.PayrollBatchDraftV2
import ir.restaurant.management.domain.personnel.PayrollPeriodDraftV2
import ir.restaurant.management.domain.personnel.PayrollPolicyDraft
import ir.restaurant.management.domain.personnel.ReviewPayrollBatchCommand
import ir.restaurant.management.domain.personnel.ShiftCategory
import ir.restaurant.management.domain.personnel.ShiftTemplateDraft
import ir.restaurant.management.domain.personnel.WorkScheduleDayRule
import ir.restaurant.management.domain.personnel.WorkScheduleDraft
import ir.restaurant.management.domain.personnel.WorkSchedulePatternType
import ir.restaurant.management.domain.crm.CustomerOpeningBalanceCommand
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserRole
import ir.restaurant.management.domain.receivables.ReceivableCollectionDraft
import ir.restaurant.management.domain.receivables.ReceivableCollectionMethod
import ir.restaurant.management.domain.receivables.ReceivableCollectionReversalDraft
import ir.restaurant.management.domain.sales.DailyMenuSaleDraft
import ir.restaurant.management.domain.sales.DailySalesDraft
import ir.restaurant.management.domain.sales.DailySalesReversalDraft
import ir.restaurant.management.domain.sales.DailySalesSettlementDraft
import ir.restaurant.management.domain.sales.DailySalesStatus
import ir.restaurant.management.domain.sales.SalesSettlementType
import ir.restaurant.management.domain.treasury.TreasuryCommand
import ir.restaurant.management.domain.treasury.TreasuryLedgerReader
import ir.restaurant.management.domain.treasury.TreasuryService
import ir.restaurant.management.domain.treasury.TreasuryTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase2CorrectionIntegrationTest {
    private lateinit var database: AppDatabase
    private lateinit var authorizer: SessionAuthorizer
    private lateinit var sales: LocalDailySalesRepository
    private lateinit var receivables: LocalReceivableService
    private var now = 1_900_000_000_000L
    private val day = 22_000L
    private var branchLocationId = 0L

    @Before
    fun setUp() = runBlocking {
        database = AppDatabase.createInMemory(ApplicationProvider.getApplicationContext<Context>())
        authorizer = SessionAuthorizer(database)
        LocalSecurityRepository(database, clock = { now }, authorizer = authorizer)
            .save(null, UserDraft("owner-correction", "مالک اصلاح", "123456", UserRole.OWNER, "87654321"))
        val branchOne = requireNotNull(database.branchDao().byId(1L))
        database.branchDao().update(
            branchOne.copy(code = "B1", name = "شعبه ۱", updatedAtEpochMillis = now),
        )
        branchLocationId = database.managementControlDao().insertLocation(
            StorageLocationEntity(
                code = "B1-TEST",
                name = "انبار تست شعبه ۱",
                branchName = "شعبه ۱",
                branchId = 1L,
                kind = "WAREHOUSE",
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        sales = LocalDailySalesRepository(database, authorizer, clock = { now })
        receivables = LocalReceivableService(database, authorizer, clock = { now })
    }

    @After fun tearDown() = database.close()

    @Test
    fun fiveSettlementLifecycleDuplicatePostingCollectionAndReverseAreReal() = runBlocking {
        val fixture = seedSaleFixture()
        val draft = mixedDraft(fixture.menuId, fixture.personId, fixture.companyId)

        val saleId = sales.createDraft(draft)
        assertEquals(DailySalesStatus.DRAFT.name, database.dailySalesDao().summary(saleId)?.status)
        assertEquals(0L, scalar("SELECT COUNT(*) FROM journal_entries WHERE sourceId=$saleId AND sourceType='DAILY_SALES'"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM receivables WHERE sourceType='DAILY_SALES' AND sourceId=$saleId"))

        sales.confirm(saleId)
        assertEquals(DailySalesStatus.CONFIRMED.name, database.dailySalesDao().summary(saleId)?.status)
        assertEquals(0L, scalar("SELECT COUNT(*) FROM journal_entries WHERE sourceId=$saleId AND sourceType='DAILY_SALES'"))

        sales.postConfirmed(saleId)
        sales.postConfirmed(saleId)
        assertEquals(DailySalesStatus.POSTED.name, database.dailySalesDao().summary(saleId)?.status)
        assertEquals(1L, scalar("SELECT COUNT(*) FROM journal_entries WHERE sourceId=$saleId AND sourceType='DAILY_SALES'"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM journal_entries WHERE sourceId=$saleId AND sourceType='DAILY_SALES_COGS'"))
        assertEquals(2L, scalar("SELECT COUNT(*) FROM receivables WHERE sourceType='DAILY_SALES' AND sourceId=$saleId"))
        assertEquals(125_000_000L, revenueBalance())
        assertEquals(10_000_000L, accountDebitBalance("1201"))
        assertEquals(25_000_000L, accountDebitBalance("1202"))
        assertEquals(10_000_000L, database.customerReceivableDao().balanceRial(fixture.personId))
        assertEquals(25_000_000L, database.customerReceivableDao().balanceRial(fixture.companyId))
        assertEquals(3L, scalar("SELECT COUNT(*) FROM treasury_transactions WHERE sourceType='DAILY_SALES_SETTLEMENT' AND sourceId=$saleId"))
        assertEquals(15_000_000L, scalar("SELECT COALESCE(SUM(debitRial-creditRial),0) FROM journal_lines WHERE accountCode='1101'"))
        assertEquals(5_000_000L, scalar("SELECT COALESCE(SUM(debitRial-creditRial),0) FROM journal_lines WHERE accountCode='1102'"))
        assertEquals(70_000_000L, scalar("SELECT COALESCE(SUM(debitRial-creditRial),0) FROM journal_lines WHERE accountCode='1104'"))
        assertEquals(0L, scalar("SELECT COALESCE(SUM(debitRial-creditRial),0) FROM journal_lines WHERE accountCode='2199'"))

        val companyReceivableId = scalar("SELECT id FROM receivables WHERE sourceType='DAILY_SALES' AND sourceId=$saleId AND partyId=${fixture.companyId}")
        val collectionCommandId = GlobalId.new().value
        val collectionCommand = ReceivableCollectionDraft(
            receivableId = companyReceivableId,
            amountRial = 10_000_000L,
            method = ReceivableCollectionMethod.BANK_TRANSFER,
            businessEpochDay = day + 1,
            commandId = collectionCommandId,
        )
        val collectionId = receivables.collect(collectionCommand)
        assertEquals(collectionId, receivables.collect(collectionCommand))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM receivable_collections WHERE globalId='$collectionCommandId'"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM treasury_transactions WHERE commandId='$collectionCommandId'"))
        assertEquals(15_000_000L, database.businessOperationsDao().receivable(companyReceivableId)?.outstandingAmountRial)
        assertEquals(15_000_000L, database.customerReceivableDao().balanceRial(fixture.companyId))
        assertEquals(15_000_000L, accountDebitBalance("1202"))
        assertEquals(125_000_000L, revenueBalance())

        try {
            sales.reverse(DailySalesReversalDraft(saleId, day + 2, "برگشت با وصول فعال"))
            fail("فروش دارای وصول فعال نباید برگشت بخورد")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("ابتدا وصول"))
        }

        val collectionReversal = ReceivableCollectionReversalDraft(collectionId, "اصلاح وصول", day + 2)
        receivables.reverseCollection(collectionReversal)
        receivables.reverseCollection(collectionReversal)
        assertEquals(1L, scalar("SELECT COUNT(*) FROM journal_entries WHERE sourceType='RECEIVABLE_COLLECTION_REVERSAL' AND sourceId=$companyReceivableId"))
        assertEquals(25_000_000L, database.customerReceivableDao().balanceRial(fixture.companyId))
        assertEquals(25_000_000L, accountDebitBalance("1202"))
        val saleReversal = DailySalesReversalDraft(saleId, day + 2, "اصلاح فروش اعتباری")
        sales.reverse(saleReversal)
        sales.reverse(saleReversal)
        assertEquals(DailySalesStatus.VOIDED.name, database.dailySalesDao().summary(saleId)?.status)
        assertEquals(0L, database.customerReceivableDao().balanceRial(fixture.personId))
        assertEquals(0L, database.customerReceivableDao().balanceRial(fixture.companyId))
        assertEquals(0L, accountDebitBalance("1201"))
        assertEquals(0L, accountDebitBalance("1202"))
        assertEquals(0L, scalar("SELECT SUM(outstandingAmountRial) FROM receivables WHERE sourceType='DAILY_SALES' AND sourceId=$saleId"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM journal_entries WHERE sourceType='DAILY_SALES_REVERSAL' AND sourceId=$saleId"))
    }

    @Test
    fun receivableCollection_rollsBackTreasuryJournalLedgerAndMaster_whenFailureOccursAfterTreasury() = runBlocking {
        val fixture = seedSaleFixture()
        val saleId = sales.createDraft(mixedDraft(fixture.menuId, fixture.personId, fixture.companyId))
        sales.confirm(saleId)
        sales.postConfirmed(saleId)
        val receivableId = scalar(
            "SELECT id FROM receivables WHERE sourceType='DAILY_SALES' AND sourceId=$saleId AND partyId=${fixture.personId}",
        )
        val before = requireNotNull(database.businessOperationsDao().receivable(receivableId))
        val realTreasury = LocalTreasuryServiceV2(
            database = database,
            accounting = LocalAccountingPostingEngine(database, clock = { ++now }),
            authorizer = authorizer,
            accountCatalog = DefaultTreasuryAccountCatalog(),
            clock = { ++now },
        )
        val failingTreasury = object : TreasuryService by realTreasury, TreasuryLedgerReader by realTreasury {
            override suspend fun execute(command: TreasuryCommand): TreasuryTransaction {
                realTreasury.execute(command)
                error("forced_after_treasury")
            }
        }
        val failingReceivables = LocalReceivableService(
            database = database,
            authorizer = authorizer,
            clock = { ++now },
            treasury = failingTreasury,
            treasuryReader = failingTreasury,
        )
        val commandId = GlobalId.new().value
        val command = ReceivableCollectionDraft(
            receivableId = receivableId,
            amountRial = 4_000_000L,
            method = ReceivableCollectionMethod.CASH,
            businessEpochDay = day + 1,
            commandId = commandId,
        )

        try {
            failingReceivables.collect(command)
            fail("خرابی پس از Treasury باید کل وصول را rollback کند")
        } catch (expected: IllegalStateException) {
            assertEquals("forced_after_treasury", expected.message)
        }

        val after = requireNotNull(database.businessOperationsDao().receivable(receivableId))
        assertEquals(before.paidAmountRial, after.paidAmountRial)
        assertEquals(before.outstandingAmountRial, after.outstandingAmountRial)
        assertEquals(before.status, after.status)
        assertEquals(0L, scalar("SELECT COUNT(*) FROM receivable_collections WHERE globalId='$commandId'"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM treasury_transactions WHERE commandId='$commandId'"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM customer_receivable_ledger WHERE sourceType='RECEIVABLE_COLLECTION' AND reference='RECEIVABLE:$receivableId'"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM journal_entries WHERE sourceType='RECEIVABLE_COLLECTION' AND sourceId=$receivableId"))
        assertEquals(0L, scalar("SELECT COUNT(*) FROM audit_logs WHERE action='COLLECT' AND entityType='RECEIVABLE' AND entityId=$receivableId"))
    }

    @Test
    fun legacyOpeningBalanceAndNewCreditShareOneCanonicalPartyBalanceAndAging() = runBlocking {
        val fixture = seedSaleFixture()
        LocalCustomerAccountService(database, authorizer, clock = { ++now }).postOpeningBalance(
            CustomerOpeningBalanceCommand(
                customerId = fixture.personId,
                businessEpochDay = day - 10,
                amountRial = 8_000_000L,
                dueEpochDay = day - 1,
                reason = "مانده واقعی پیش از فروش جدید",
                commandId = "correction-opening-8m",
            ),
        )

        val saleId = sales.createDraft(
            DailySalesDraft(
                businessEpochDay = day, discountRial = 0, serviceRial = 0, taxRial = 0,
                cashRial = 0, cardRial = 0, transferRial = 0,
                lines = listOf(DailyMenuSaleDraft(fixture.menuId, 1_000_000L, 4_000_000L)),
                branchId = 1L,
                locationId = branchLocationId,
                settlements = listOf(
                    DailySalesSettlementDraft(
                        SalesSettlementType.PERSONAL_CREDIT, 4_000_000L,
                        partyId = fixture.personId, dueEpochDay = day + 7,
                    ),
                ),
            ),
        )
        sales.confirm(saleId)
        sales.postConfirmed(saleId)

        assertEquals(12_000_000L, database.customerReceivableDao().balanceRial(fixture.personId))
        val customer = LocalSalesHistoryRepository(database, authorizer).customers.first()
            .first { it.id == fixture.personId }
        assertEquals(12_000_000L, customer.outstandingRial)
        // Opening balances are organization-scoped legacy lots and must not be fabricated as
        // branch 1. Customer aging is the canonical party-level API for this combined balance.
        val aging = LocalCustomerAccountService(database, authorizer, clock = { ++now })
            .aging(fixture.personId, day)
        assertEquals(12_000_000L, aging.totalRial)
        assertEquals(4_000_000L, scalar("SELECT outstandingAmountRial FROM receivables WHERE sourceType='DAILY_SALES' AND sourceId=$saleId"))
    }

    @Test
    fun actualFoodCostIsUnavailableWithoutIndependentEvidenceAndChangesWithWaste() = runBlocking {
        val fixture = seedSaleFixture()
        val saleId = sales.createDraft(cashOnlyDraft(fixture.menuId, 125_000_000L))
        sales.confirm(saleId)
        sales.postConfirmed(saleId)
        val service = LocalCostControlReadService(database, authorizer)

        val missing = service.consumptionVariance(1L, day, day)
        assertNull(missing.actualLedgerCostRial)
        assertEquals(ActualCostDataQuality.ACTUAL_NOT_AVAILABLE, missing.actualDataQuality)

        database.stockMovementDao().insert(
            StockMovementEntity(
                itemId = fixture.itemId,
                movementType = "WASTE",
                quantityDeltaMicros = -100_000,
                valueDeltaRial = -5_000_000L,
                referenceType = "WASTE",
                referenceId = 991,
                movementEpochDay = day,
                notes = "waste evidence",
                createdAtEpochMillis = ++now,
                globalId = GlobalId.new().value,
                idempotencyKey = "correction:waste:991",
                correlationId = "correction:waste:991",
                actorId = 1,
                locationId = branchLocationId,
                deviceId = "test",
                unitCostRial = 50_000_000L,
                reasonCode = "WASTE",
            ),
        )
        val actual = service.consumptionVariance(1L, day, day)
        assertEquals(ActualCostDataQuality.ACTUAL_LEDGER_ESTIMATE, actual.actualDataQuality)
        assertNotEquals(actual.theoreticalCostRial, actual.actualLedgerCostRial)
        assertEquals(5_000_000L, actual.varianceCostRial)
    }

    @Test
    fun dailyBriefReadsRealCogsExpensesPayrollAndEstimatedProfit() = runBlocking {
        val fixture = seedSaleFixture()
        val saleId = sales.createDraft(cashOnlyDraft(fixture.menuId, 125_000_000L))
        sales.confirm(saleId)
        sales.postConfirmed(saleId)
        postBranchMaintenanceExpense(branchId = 1L, amountRial = 12_000_000L)
        postBranchPayroll(branchId = 1L, baseSalaryRial = 9_000_000L)

        val brief = LocalDailyManagementBriefService(database, authorizer, clock = { now }).compose(1L, day)
        val home = DailyManagementKpiReadModelFactory.from(brief)
        assertEquals(125_000_000L, brief.profitability.netSalesRial)
        assertEquals(48_000_000L, brief.profitability.cogsRial)
        assertEquals(77_000_000L, brief.profitability.grossProfitRial)
        assertEquals(12_000_000L, brief.profitability.operatingExpensesRial)
        assertEquals(9_000_000L, brief.profitability.payrollRial)
        assertEquals(56_000_000L, brief.profitability.estimatedOperatingProfitRial)
        assertTrue(brief.profitability.isEstimatedProfitAvailable)
        assertEquals(brief.profitability.revenueRial, home.revenueRial)
        assertEquals(brief.profitability.grossProfitRial, home.grossProfitRial)
        assertEquals(
            brief.foodCost.actualLedgerCostRial
                ?.times(10_000L)
                ?.div(brief.profitability.revenueRial),
            home.foodCostBasisPoints,
        )
        assertEquals(brief.profitability.estimatedOperatingProfitRial, home.estimatedOperatingProfitRial)
        assertEquals(brief.liquidity.newReceivablesRial, home.newReceivablesRial)
        assertEquals(brief.liquidity.oldReceivableCollectionsRial, home.collectionsRial)
        assertEquals(brief.criticalIssues, home.criticalIssues)
        assertEquals(brief.overdueTasks, home.overdueTasks)
    }

    @Test
    fun globalThresholdIsUniqueAndBranchPrecedenceIsDeterministic() = runBlocking {
        val settings = ControlSettingsService(database, authorizer, clock = { ++now })
        settings.updateBasisPointThreshold(null, "FOOD_COST_VARIANCE_BP", 500)
        settings.updateBasisPointThreshold(null, "FOOD_COST_VARIANCE_BP", 600)
        settings.updateBasisPointThreshold(2L, "FOOD_COST_VARIANCE_BP", 800)

        assertEquals(1L, scalar("SELECT COUNT(*) FROM management_rule_thresholds WHERE branchScopeId=0 AND `key`='FOOD_COST_VARIANCE_BP'"))
        val branch2 = database.businessOperationsDao().thresholds(2L).filter { it.key == "FOOD_COST_VARIANCE_BP" }
        assertEquals(2L, branch2.size.toLong())
        assertEquals(2L, branch2.first().branchScopeId)
        assertEquals(800, branch2.first().valueBasisPoints)
        val branch1 = database.businessOperationsDao().thresholds(1L).first { it.key == "FOOD_COST_VARIANCE_BP" }
        assertEquals(0L, branch1.branchScopeId)
        assertEquals(600, branch1.valueBasisPoints)
    }

    @Test
    fun cashierCannotBypassNewDailySalesPermissionWithLegacySalesPermission() = runBlocking {
        val security = LocalSecurityRepository(database, clock = { ++now }, authorizer = authorizer)
        val cashierId = security.save(
            null,
            UserDraft("cashier-correction", "صندوقدار", "123456", UserRole.CASHIER, "87654321"),
        )
        security.switchUser(cashierId, "123456")
        try {
            sales.createDraft(DailySalesDraft(day,0,0,0,1,0,0,lines=listOf(DailyMenuSaleDraft(1,1_000_000,1)),branchId=1L,locationId=branchLocationId))
            fail("Permission.SALES قدیمی نباید DAILY_SALES_CREATE را bypass کند")
        } catch (expected: Exception) {
            assertTrue(expected::class.simpleName.orEmpty().contains("AccessDenied") || expected.message.orEmpty().contains("مجوز"))
        }
    }

    @Test
    fun dailySalesWeightedAverageAggregatesIngredientBeforeRoundingAndClearsResidualValue() = runBlocking {
        val itemId = database.inventoryDao().insert(
            InventoryItemEntity(
                name = "ماده باقیمانده میانگین موزون",
                category = "مواد اولیه",
                unit = "واحد",
                stockMicros = 3_000_000L,
                inventoryValueRial = 1_000_000L,
                trackLot = false,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        val menuOne = database.recipeDao().insertMenuItem(
            MenuItemEntity(name = "منوی یک واحدی", salePriceRial = 500L, createdAtEpochMillis = now, updatedAtEpochMillis = now),
        )
        val recipeOne = database.recipeDao().insertVersion(
            RecipeVersionEntity(
                menuItemId = menuOne,
                revisionNo = 1,
                effectiveFromEpochDay = 1,
                createdBy = "TEST",
                createdAtEpochMillis = now,
            ),
        )
        database.recipeDao().insertVersionIngredients(
            listOf(RecipeVersionIngredientEntity(recipeOne, itemId, 1_000_000L)),
        )
        val menuTwo = database.recipeDao().insertMenuItem(
            MenuItemEntity(name = "منوی دو واحدی", salePriceRial = 500L, createdAtEpochMillis = now, updatedAtEpochMillis = now),
        )
        val recipeTwo = database.recipeDao().insertVersion(
            RecipeVersionEntity(
                menuItemId = menuTwo,
                revisionNo = 1,
                effectiveFromEpochDay = 1,
                createdBy = "TEST",
                createdAtEpochMillis = now,
            ),
        )
        database.recipeDao().insertVersionIngredients(
            listOf(RecipeVersionIngredientEntity(recipeTwo, itemId, 2_000_000L)),
        )

        val saleId = sales.post(
            DailySalesDraft(
                businessEpochDay = day,
                discountRial = 0,
                serviceRial = 0,
                taxRial = 0,
                cashRial = 0,
                cardRial = 0,
                transferRial = 0,
                lines = listOf(
                    DailyMenuSaleDraft(menuOne, 1_000_000L, 500L),
                    DailyMenuSaleDraft(menuTwo, 1_000_000L, 500L),
                ),
                branchId = 1L,
                locationId = branchLocationId,
                settlements = listOf(DailySalesSettlementDraft(SalesSettlementType.CASH, 1_000L)),
            ),
        )

        val movement = database.stockMovementDao().dailySalesConsumptions(saleId).single()
        assertEquals(-3_000_000L, movement.quantityDeltaMicros)
        assertEquals(-1_000_000L, movement.valueDeltaRial)
        assertEquals(1_000_000L, database.dailySalesDao().summary(saleId)?.theoreticalCostRial)
        assertEquals(1_000_000L, database.dailySalesDao().lines(saleId).sumOf { it.theoreticalCostRial })
        assertEquals(333_333L, database.dailySalesDao().lines(saleId)[0].theoreticalCostRial)
        assertEquals(666_667L, database.dailySalesDao().lines(saleId)[1].theoreticalCostRial)
        assertEquals(0L, database.inventoryDao().byId(itemId)?.stockMicros)
        assertEquals(0L, database.inventoryDao().byId(itemId)?.inventoryValueRial)
        assertEquals(0L, database.inventoryBalanceDao().byKey(itemId, branchLocationId)?.onHandMicros)
        assertEquals(0L, database.inventoryBalanceDao().byKey(itemId, branchLocationId)?.inventoryValueRial)
        assertEquals(
            1_000_000L,
            scalar(
                "SELECT COALESCE(SUM(l.debitRial),0) FROM journal_lines l " +
                    "JOIN journal_entries e ON e.id=l.entryId " +
                    "WHERE e.sourceType='DAILY_SALES_COGS' AND e.sourceId=$saleId AND l.accountCode='5101'",
            ),
        )
    }

    @Test
    fun dailySalesWeightedAverageUsesExactSourceLocationInsteadOfAggregateItemAverage() = runBlocking {
        val sourceLocationId = requireNotNull(database.managementControlDao().defaultLocationId())
        val itemId = database.inventoryDao().insert(
            InventoryItemEntity(
                name = "ماده چندمحله",
                category = "مواد اولیه",
                unit = "واحد",
                stockMicros = 10_000_000L,
                inventoryValueRial = 1_100_000L,
                trackLot = false,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        database.inventoryBalanceDao().initialize(
            InventoryBalanceEntity(
                itemId = itemId,
                locationId = sourceLocationId,
                onHandMicros = 2_000_000L,
                inventoryValueRial = 200_000L,
                updatedAtEpochMillis = now,
            ),
        )
        database.inventoryBalanceDao().initialize(
            InventoryBalanceEntity(
                itemId = itemId,
                locationId = branchLocationId,
                onHandMicros = 8_000_000L,
                inventoryValueRial = 900_000L,
                updatedAtEpochMillis = now,
            ),
        )
        val menuId = database.recipeDao().insertMenuItem(
            MenuItemEntity(name = "منوی چندمحله", salePriceRial = 500L, createdAtEpochMillis = now, updatedAtEpochMillis = now),
        )
        val recipeId = database.recipeDao().insertVersion(
            RecipeVersionEntity(
                menuItemId = menuId,
                revisionNo = 1,
                effectiveFromEpochDay = 1,
                createdBy = "TEST",
                createdAtEpochMillis = now,
            ),
        )
        database.recipeDao().insertVersionIngredients(
            listOf(RecipeVersionIngredientEntity(recipeId, itemId, 1_000_000L)),
        )

        val saleId = sales.post(
            DailySalesDraft(
                businessEpochDay = day,
                discountRial = 0,
                serviceRial = 0,
                taxRial = 0,
                cashRial = 0,
                cardRial = 0,
                transferRial = 0,
                lines = listOf(DailyMenuSaleDraft(menuId, 1_000_000L, 500L)),
                branchId = 1L,
                locationId = branchLocationId,
                settlements = listOf(DailySalesSettlementDraft(SalesSettlementType.CASH, 500L)),
            ),
        )

        val movement = database.stockMovementDao().dailySalesConsumptions(saleId).single()
        assertEquals(branchLocationId, movement.locationId)
        assertEquals(-112_500L, movement.valueDeltaRial)
        assertEquals(112_500L, database.dailySalesDao().summary(saleId)?.theoreticalCostRial)
        assertEquals(2_000_000L, database.inventoryBalanceDao().byKey(itemId, sourceLocationId)?.onHandMicros)
        assertEquals(200_000L, database.inventoryBalanceDao().byKey(itemId, sourceLocationId)?.inventoryValueRial)
        assertEquals(7_000_000L, database.inventoryBalanceDao().byKey(itemId, branchLocationId)?.onHandMicros)
        assertEquals(787_500L, database.inventoryBalanceDao().byKey(itemId, branchLocationId)?.inventoryValueRial)
        assertEquals(9_000_000L, database.inventoryDao().byId(itemId)?.stockMicros)
        assertEquals(987_500L, database.inventoryDao().byId(itemId)?.inventoryValueRial)
    }

    private suspend fun postBranchMaintenanceExpense(branchId: Long, amountRial: Long) {
        val repository = LocalAssetRepository(database, clock = { ++now }, authorizer = authorizer)
        val assetId = repository.save(
            null,
            AssetDraft(
                assetCode = "ASSET-BR-$branchId",
                name = "دارایی شعبه $branchId",
                category = "تجهیزات",
                quantity = 1,
                purchaseEpochDay = day - 1,
                purchaseCostRial = 100_000_000L,
                salvageValueRial = 0,
                usefulLifeMonths = 60,
                location = "شعبه $branchId",
                notes = "fixture branch expense",
                acquisitionSource = AssetAcquisitionSource.BANK,
                branchId = branchId,
            ),
        )
        repository.recordMaintenance(
            AssetMaintenanceDraft(
                assetId = assetId,
                serviceType = "سرویس دوره‌ای",
                serviceEpochDay = day,
                costRial = amountRial,
                contractor = "پیمانکار تست",
                note = "هزینه عملیاتی از producer واقعی دارایی",
                paymentSource = AssetAcquisitionSource.CASH,
            ),
        )
        assertEquals(
            branchId,
            scalar("SELECT branchId FROM journal_entries WHERE sourceType='ASSET_MAINTENANCE' AND sourceId=$assetId"),
        )
    }

    private suspend fun postBranchPayroll(branchId: Long, baseSalaryRial: Long) {
        val security = LocalSecurityRepository(database, clock = { ++now }, authorizer = authorizer)
        val approverId = security.save(
            null,
            UserDraft("payroll-approver", "تأییدکننده حقوق", "654321", UserRole.OWNER, "11223344"),
        )
        val personnel = LocalPersonnelRepository(database, clock = { ++now }, authorizer = authorizer)
        val policyId = personnel.savePayrollPolicy(
            PayrollPolicyDraft(
                title = "سیاست حقوق تست شعبه",
                effectiveFromEpochDay = day - 30,
                overtimeHourlyRateRial = 0,
                absenceDailyDeductionRial = 0,
                lateMinuteDeductionRial = 0,
                insuranceBasisPoints = 0,
                taxBasisPoints = 0,
            ),
        )
        val employeeId = personnel.saveEmployee(
            null,
            EmployeeDraft(
                name = "کارمند شعبه",
                fatherName = "تست",
                jobTitle = "کارشناس",
                phone = "",
                monthlySalaryRial = baseSalaryRial,
                hireEpochDay = day - 30,
                employeeCode = "BRPAY1",
                department = "عملیات",
                branchId = branchId,
            ),
        )
        val shiftTemplateId = personnel.saveShiftTemplate(
            null,
            ShiftTemplateDraft(
                code = "BRSHIFT1",
                name = "شیفت شعبه",
                category = ShiftCategory.MORNING,
                startMinute = 8 * 60,
                endMinute = 16 * 60,
                overtimeRequiresApproval = false,
                branchId = branchId,
            ),
        )
        val workScheduleId = personnel.saveWorkSchedule(
            null,
            WorkScheduleDraft(
                code = "BRSCHED1",
                name = "برنامه شعبه",
                patternType = WorkSchedulePatternType.WEEKLY_FIXED,
                cycleLengthDays = 7,
                effectiveFromEpochDay = day - 30,
                effectiveToEpochDay = day + 30,
                branchId = branchId,
                days = (0..6).map { sequenceDay ->
                    WorkScheduleDayRule(
                        sequenceDay = sequenceDay,
                        dayOfWeek = sequenceDay + 1,
                        shiftTemplateId = shiftTemplateId,
                        isOffDay = false,
                    )
                },
            ),
        )
        val contractId = personnel.saveContract(
            null,
            EmployeeContractDraft(
                employeeId = employeeId,
                contractType = "PERMANENT",
                startEpochDay = day - 30,
                endEpochDay = day + 30,
                baseSalaryRial = baseSalaryRial,
                dailyWorkMinutes = 480,
                weeklyWorkDays = 7,
                payrollPolicyId = policyId,
                workScheduleId = workScheduleId,
                defaultShiftTemplateId = shiftTemplateId,
                notes = "قرارداد تست branch payroll",
            ),
        )
        security.switchUser(approverId, "654321")
        personnel.approveContract(contractId)
        security.switchUser(1L, "123456")
        personnel.saveAttendance(
            null,
            AttendanceDraft(
                employeeId = employeeId,
                workEpochDay = day,
                status = "PRESENT",
                checkInMinute = 8 * 60,
                checkOutMinute = 16 * 60,
                scheduledStartMinute = 8 * 60,
                scheduledEndMinute = 16 * 60,
                notes = "حضور واقعی branch payroll",
            ),
        )

        val accounting = LocalAccountingPostingEngine(database, clock = { ++now })
        val treasury = LocalTreasuryServiceV2(
            database = database,
            accounting = accounting,
            authorizer = authorizer,
            accountCatalog = DefaultTreasuryAccountCatalog(),
            clock = { ++now },
        )
        val payroll = LocalHrPayrollService(
            database = database,
            authorizer = authorizer,
            accountingPosting = accounting,
            treasury = treasury,
            clock = { ++now },
        )
        val periodId = payroll.openPeriod(
            PayrollPeriodDraftV2(
                periodKey = "BR-PNL-1",
                startEpochDay = day,
                endEpochDay = day,
                paymentDueEpochDay = day,
            ),
        )
        val batchId = payroll.createBatch(
            PayrollBatchDraftV2(
                periodId = periodId,
                scope = "BRANCH",
                branchId = branchId,
                notes = "branch payroll production flow",
            ),
        )
        val outcome = payroll.calculateBatch(
            CalculatePayrollBatchCommand(batchId = batchId, employeeIds = listOf(employeeId)),
        )
        assertTrue("payroll fixture blocked: ${outcome.exceptions}", !outcome.hasBlockingExceptions)
        payroll.submitBatchForReview(ReviewPayrollBatchCommand(batchId, "بازبینی branch payroll"))
        security.switchUser(approverId, "654321")
        payroll.approveBatch(ApprovePayrollBatchCommand(batchId, "تأیید branch payroll"))
        security.switchUser(1L, "123456")

        assertEquals(
            branchId,
            scalar("SELECT branchId FROM journal_entries WHERE sourceType='PAYROLL_ACCRUAL' AND accountingScope='BRANCH'"),
        )
        assertEquals(
            baseSalaryRial,
            scalar("SELECT COALESCE(SUM(l.debitRial-l.creditRial),0) FROM journal_lines l JOIN journal_entries e ON e.id=l.entryId WHERE e.sourceType='PAYROLL_ACCRUAL' AND e.branchId=$branchId AND l.accountCode='6101'"),
        )
    }

    private data class Fixture(val itemId: Long, val menuId: Long, val personId: Long, val companyId: Long)

    private suspend fun seedSaleFixture(): Fixture {
        val itemId = database.inventoryDao().insert(
            InventoryItemEntity(
                name="ماده اصلاح", category="مواد اولیه", unit="واحد", stockMicros=10_000_000L, inventoryValueRial=480_000_000L,
                trackLot=true, createdAtEpochMillis=now, updatedAtEpochMillis=now,
            ),
        )
        val menuId = database.recipeDao().insertMenuItem(MenuItemEntity(name="منوی اصلاح", salePriceRial=125_000_000L, createdAtEpochMillis=now, updatedAtEpochMillis=now))
        val recipeId = database.recipeDao().insertVersion(
            RecipeVersionEntity(menuItemId=menuId,revisionNo=1,effectiveFromEpochDay=1,preparationWasteBasisPoints=0,cookingWasteBasisPoints=0,createdBy="TEST",createdAtEpochMillis=now),
        )
        database.recipeDao().insertVersionIngredients(listOf(RecipeVersionIngredientEntity(recipeId,itemId,1_000_000L)))
        val locationId = branchLocationId
        database.inventoryLotDao().insert(
            InventoryLotEntity(itemId=itemId,locationId=locationId,lotCode="CORR-LOT",receivedEpochDay=day-10,expiryEpochDay=null,quantityMicros=10_000_000L,unitCostRial=48_000_000L,barcode=null,createdByActorId=1,createdAtEpochMillis=now,updatedAtEpochMillis=now),
        )
        val personId = database.salesDao().insertCustomer(
            CustomerEntity(customerCode="P-CORR",name="شخص اصلاح",phone="09120000001",nationalId="",creditLimitRial=100_000_000L,notes="",createdAtEpochMillis=now,updatedAtEpochMillis=now,paymentTermsDays=7,partyType="PERSON"),
        )
        val companyId = database.salesDao().insertCustomer(
            CustomerEntity(customerCode="C-CORR",name="شرکت اصلاح",phone="02100000001",nationalId="",creditLimitRial=200_000_000L,notes="",createdAtEpochMillis=now,updatedAtEpochMillis=now,paymentTermsDays=14,partyType="COMPANY"),
        )
        return Fixture(itemId,menuId,personId,companyId)
    }

    private fun mixedDraft(menuId: Long, personId: Long, companyId: Long) = DailySalesDraft(
        businessEpochDay=day,discountRial=0,serviceRial=0,taxRial=0,cashRial=0,cardRial=0,transferRial=0,
        lines=listOf(DailyMenuSaleDraft(menuId,1_000_000L,125_000_000L)),
        branchId=1L,
        locationId=branchLocationId,
        settlements=listOf(
            DailySalesSettlementDraft(SalesSettlementType.CASH,15_000_000L),
            DailySalesSettlementDraft(SalesSettlementType.CARD,70_000_000L,referenceNumber="CARD-1"),
            DailySalesSettlementDraft(SalesSettlementType.BANK_TRANSFER,5_000_000L,referenceNumber="BANK-1"),
            DailySalesSettlementDraft(SalesSettlementType.PERSONAL_CREDIT,10_000_000L,partyId=personId,dueEpochDay=day+7),
            DailySalesSettlementDraft(SalesSettlementType.CORPORATE_CREDIT,25_000_000L,partyId=companyId,dueEpochDay=day+14,referenceNumber="CONTRACT-1"),
        ),
    )

    private fun cashOnlyDraft(menuId: Long, amount: Long) = DailySalesDraft(
        businessEpochDay=day,discountRial=0,serviceRial=0,taxRial=0,cashRial=0,cardRial=0,transferRial=0,
        lines=listOf(DailyMenuSaleDraft(menuId,1_000_000L,amount)),
        branchId=1L,
        locationId=branchLocationId,
        settlements=listOf(DailySalesSettlementDraft(SalesSettlementType.CASH,amount)),
    )

    private fun revenueBalance(): Long = scalar("SELECT COALESCE(SUM(l.creditRial-l.debitRial),0) FROM journal_lines l JOIN journal_entries e ON e.id=l.entryId WHERE l.accountCode='4101' AND e.status='POSTED'")
    private fun accountDebitBalance(code: String): Long = scalar("SELECT COALESCE(SUM(l.debitRial-l.creditRial),0) FROM journal_lines l JOIN journal_entries e ON e.id=l.entryId WHERE l.accountCode='$code' AND e.status='POSTED'")

    private fun scalar(sql: String): Long = database.openHelper.writableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        if (cursor.isNull(0)) 0L else cursor.getLong(0)
    }
}
