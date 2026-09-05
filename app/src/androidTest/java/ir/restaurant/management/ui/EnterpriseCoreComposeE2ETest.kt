package ir.restaurant.management.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import ir.restaurant.management.MainActivity
import ir.restaurant.management.RestaurantManagementApplication
import ir.restaurant.management.core.MoneyRial
import ir.restaurant.management.core.GlobalId
import ir.restaurant.management.core.QuantityMicros
import ir.restaurant.management.core.currentLocalEpochDay
import ir.restaurant.management.domain.assets.AssetAcquisitionSource
import ir.restaurant.management.domain.assets.AssetDraft
import ir.restaurant.management.domain.branch.BranchDraft
import ir.restaurant.management.domain.control.BudgetCategory
import ir.restaurant.management.domain.control.BudgetDraft
import ir.restaurant.management.domain.operations.SupplierDraft
import ir.restaurant.management.domain.purchase.PurchaseOrderDraft
import ir.restaurant.management.domain.purchase.PurchaseRequisitionDraft
import ir.restaurant.management.domain.purchase.RequisitionLineDraft
import ir.restaurant.management.domain.inventory.CreateInventoryCountSessionCommand
import ir.restaurant.management.domain.inventory.InventoryCountActionCommand
import ir.restaurant.management.domain.inventory.InventoryCountScope
import ir.restaurant.management.domain.inventory.InventoryCountStatus
import ir.restaurant.management.domain.inventory.CreateInventoryTransferCommand
import ir.restaurant.management.domain.inventory.CreateInventoryTransferLine
import ir.restaurant.management.domain.inventory.InventoryBalanceQuery
import ir.restaurant.management.domain.inventory.InventoryLocationDraft
import ir.restaurant.management.domain.inventory.InventoryLocationType
import ir.restaurant.management.domain.inventory.InventoryTransferStatus
import ir.restaurant.management.domain.inventory.InventoryCommandContext
import ir.restaurant.management.domain.inventory.InventoryMovementType
import ir.restaurant.management.domain.inventory.InventoryReasonCode
import ir.restaurant.management.domain.inventory.InventoryReferenceType
import ir.restaurant.management.domain.inventory.ReceiveInventoryCommand
import ir.restaurant.management.domain.operations.InventoryItemDraft
import ir.restaurant.management.domain.recipe.RecipeIngredientInput
import ir.restaurant.management.domain.receivables.DailySalesReceivableOriginDraft
import ir.restaurant.management.domain.receivables.ReceivableType
import ir.restaurant.management.domain.sales.CustomerDraft
import ir.restaurant.management.domain.sales.SalesPaymentMethod
import ir.restaurant.management.domain.operations.UserDraft
import ir.restaurant.management.domain.operations.UserDataScope
import ir.restaurant.management.domain.operations.UserRole
import ir.restaurant.management.domain.personnel.ApprovePayrollBatchCommand
import ir.restaurant.management.domain.personnel.AttendanceDraft
import ir.restaurant.management.domain.personnel.CalculatePayrollBatchCommand
import ir.restaurant.management.domain.personnel.EmployeeContractDraft
import ir.restaurant.management.domain.personnel.EmployeeDraft
import ir.restaurant.management.domain.personnel.PayrollBatchDraftV2
import ir.restaurant.management.domain.personnel.PayrollBatchStatus
import ir.restaurant.management.domain.personnel.PayrollPaymentStatus
import ir.restaurant.management.domain.personnel.PayrollPayslipStatus
import ir.restaurant.management.domain.personnel.PayrollPeriodDraftV2
import ir.restaurant.management.domain.personnel.PayrollPolicyDraft
import ir.restaurant.management.domain.personnel.ReviewPayrollBatchCommand
import ir.restaurant.management.domain.personnel.ShiftCategory
import ir.restaurant.management.domain.personnel.ShiftTemplateDraft
import ir.restaurant.management.domain.personnel.WorkScheduleDayRule
import ir.restaurant.management.domain.personnel.WorkScheduleDraft
import ir.restaurant.management.domain.personnel.WorkSchedulePatternType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * UI-to-database smoke/e2e coverage for Enterprise-core vertical slices.
 *
 * No repository is mocked. The test boots the production MainActivity, creates the first OWNER
 * through the real SecurityRepository bootstrap path, drives the production Compose screen, and
 * verifies the persisted treasury ledger/balance after the UI command completes.
 */
class EnterpriseCoreComposeE2ETest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val app: RestaurantManagementApplication
        get() = composeRule.activity.application as RestaurantManagementApplication

    @Before
    fun ensureAuthenticatedOwner() {
        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodesWithText("امنیت و پشتیبان‌گیری").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag("home_dashboard").fetchSemanticsNodes().isNotEmpty()
        }
        runBlocking {
            val security = app.container.securityRepository
            val users = security.users.first()
            if (users.isEmpty()) {
                security.save(
                    id = null,
                    draft = UserDraft(
                        username = TEST_OWNER_USERNAME,
                        displayName = "مالک آزمون enterprise-core",
                        pin = TEST_OWNER_PIN,
                        role = UserRole.OWNER,
                        recoveryCode = TEST_OWNER_RECOVERY,
                    ),
                )
            }
            val owner = security.users.first().firstOrNull {
                it.role == UserRole.OWNER && it.username in KNOWN_OWNER_USERNAMES
            } ?: error("E2E database contains no known deterministic test owner")
            if (security.currentUser.first()?.id != owner.id) {
                security.switchUser(owner.id, TEST_OWNER_PIN)
            }
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("home_dashboard").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun treasuryReceipt_uiToLedgerAndBalance_isPersistedAndPosted() {
        val amount = 12_345L
        val sourceId = 9_876_543L
        val sourceType = "OTHER_INCOME"
        val account = app.container.treasuryUseCases.activeAccounts().first()
        val beforeBalance = runBlocking { app.container.treasuryUseCases.balance(account.id).first() }

        openModule(AppScreen.TREASURY)
        scrollTo("treasury_list", "treasury_amount")
        composeRule.onNodeWithTag("treasury_amount").assertIsDisplayed().performTextReplacement(amount.toString())
        scrollTo("treasury_list", "treasury_source_type")
        composeRule.onNodeWithTag("treasury_source_type_${sourceType}").performClick()
        scrollTo("treasury_list", "treasury_source_id")
        composeRule.onNodeWithTag("treasury_source_id").performTextReplacement(sourceId.toString())
        scrollTo("treasury_list", "treasury_reason")
        composeRule.onNodeWithTag("treasury_reason").performTextReplacement("ثبت دریافت E2E واقعی")
        scrollTo("treasury_list", "treasury_submit")
        composeRule.onNodeWithTag("treasury_submit").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking {
                app.container.treasuryLedgerReader.recentTransactions.first().any {
                    it.sourceType == sourceType && it.sourceId == sourceId && it.amountRial == amount
                }
            }
        }

        val persisted = runBlocking {
            app.container.treasuryLedgerReader.recentTransactions.first().first {
                it.sourceType == sourceType && it.sourceId == sourceId && it.amountRial == amount
            }
        }
        val afterBalance = runBlocking { app.container.treasuryUseCases.balance(account.id).first() }

        assertEquals("POSTED", persisted.status)
        assertNotNull("Treasury UI receipt must create a balanced accounting journal", persisted.journalEntryId)
        assertEquals(beforeBalance + amount, afterBalance)
    }


@Test
    fun recipeActivation_uiCreatesDraftAndActivatesImmutableVersion() {
        val fixture = seedSaleFixture("ui-recipe")
        val before = runBlocking { app.container.recipeRepository.observeRevisions(fixture.menuItemId).first() }
        val originalActive = before.first { it.state.name == "ACTIVE" }

        openModule(AppScreen.RECIPES)
        scrollTo("recipe_list", "recipe_product_${fixture.menuItemId}")
        composeRule.onNodeWithTag("recipe_product_${fixture.menuItemId}").performClick()
        composeRule.onNodeWithTag("recipe_editor_list").performScrollToNode(hasTestTag("recipe_create_draft_${originalActive.id}"))
        composeRule.onNodeWithTag("recipe_create_draft_${originalActive.id}").performClick()

        val draftId = runBlocking {
            app.container.recipeRepository.observeRevisions(fixture.menuItemId).first { revisions ->
                revisions.any { it.id != originalActive.id && it.state.name == "DRAFT" }
            }.first { it.id != originalActive.id && it.state.name == "DRAFT" }.id
        }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("recipe_activate_${draftId}").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("recipe_editor_list").performScrollToNode(hasTestTag("recipe_activate_${draftId}"))
        composeRule.onNodeWithTag("recipe_activate_${draftId}").performClick()
        composeRule.onNodeWithTag("recipe_activate_confirm").performClick()

        val after = runBlocking {
            app.container.recipeRepository.observeRevisions(fixture.menuItemId).first { revisions ->
                revisions.any { it.id == draftId && it.state.name == "ACTIVE" }
            }
        }
        assertEquals("ACTIVE", after.first { it.id == draftId }.state.name)
        assertEquals("RETIRED", after.first { it.id == originalActive.id }.state.name)
        assertTrue(after.first { it.id == draftId }.revisionNo > originalActive.revisionNo)
    }





    @Test
    fun inventoryCount_uiRecordApproveAndPost_reachesPostedWithoutChangingExactBalance() {
        val fixture = seedInventoryCountFixture("ui-count")
        val beforeStock = inventoryOnHand(fixture.inventoryItemId, fixture.locationId)

        openModule(AppScreen.INVENTORY)
        composeRule.onNodeWithTag("inventory_overview_list").performScrollToNode(hasTestTag("inventory_section_COUNTS"))
        composeRule.onNodeWithTag("inventory_section_COUNTS").performClick()
        composeRule.onNodeWithTag("inventory_count_list").performScrollToNode(hasTestTag("inventory_count_select_${fixture.sessionId}"))
        composeRule.onNodeWithTag("inventory_count_select_${fixture.sessionId}").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("inventory_count_record_${fixture.lineId}").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("inventory_count_record_${fixture.lineId}").performClick()
        composeRule.onNodeWithTag("inventory_count_quantity").performTextReplacement("10")
        composeRule.onNodeWithTag("inventory_count_record_submit").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("inventory_count_submit").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("inventory_count_submit").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { app.container.inventoryCountService.session(fixture.sessionId).status == InventoryCountStatus.PENDING_APPROVAL } &&
                composeRule.onAllNodesWithTag("inventory_count_close").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("inventory_count_close").performClick()

        runBlocking { app.container.securityRepository.switchUser(fixture.managerId, TEST_MANAGER_PIN) }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { app.container.securityRepository.currentUser.first()?.id == fixture.managerId } &&
                composeRule.onAllNodesWithTag("inventory_overview_list").fetchSemanticsNodes().isNotEmpty()
        }
        // User identity changes intentionally recreate the protected ViewModelStore. Re-enter the
        // Counts workspace as the manager instead of relying on stale owner-session UI state.
        composeRule.onNodeWithTag("inventory_overview_list").performScrollToNode(hasTestTag("inventory_section_COUNTS"))
        composeRule.onNodeWithTag("inventory_section_COUNTS").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("inventory_count_list").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("inventory_count_list").performScrollToNode(hasTestTag("inventory_count_approve_${fixture.sessionId}"))
        composeRule.onNodeWithTag("inventory_count_approve_${fixture.sessionId}").performClick()
        composeRule.onNodeWithTag("inventory_count_list").performScrollToNode(hasTestTag("inventory_count_post_${fixture.sessionId}"))
        composeRule.onNodeWithTag("inventory_count_post_${fixture.sessionId}").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { app.container.inventoryCountService.session(fixture.sessionId).status == InventoryCountStatus.POSTED }
        }
        val afterStock = inventoryOnHand(fixture.inventoryItemId, fixture.locationId)
        assertEquals(InventoryCountStatus.POSTED, runBlocking { app.container.inventoryCountService.session(fixture.sessionId).status })
        assertEquals(beforeStock, afterStock)
    }

    @Test
    fun inventoryTransfer_uiApproveIssueReceive_preservesTotalOnHand() {
        val fixture = seedTransferFixture("ui-transfer")
        val beforeSource = inventoryOnHand(fixture.inventoryItemId, fixture.sourceLocationId)
        val beforeDestination = inventoryOnHand(fixture.inventoryItemId, fixture.destinationLocationId)

        openModule(AppScreen.INVENTORY)
        composeRule.onNodeWithTag("inventory_overview_list").performScrollToNode(hasTestTag("inventory_section_TRANSFERS"))
        composeRule.onNodeWithTag("inventory_section_TRANSFERS").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("inventory_transfer_approve_${fixture.transferId}").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("inventory_transfer_approve_${fixture.transferId}").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("inventory_transfer_issue_${fixture.transferId}").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("inventory_transfer_issue_${fixture.transferId}").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("inventory_transfer_receive_${fixture.transferId}").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("inventory_transfer_receive_${fixture.transferId}").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { app.container.inventoryTransferService.document(fixture.transferId).status == InventoryTransferStatus.COMPLETED }
        }
        val document = runBlocking { app.container.inventoryTransferService.document(fixture.transferId) }
        val afterSource = inventoryOnHand(fixture.inventoryItemId, fixture.sourceLocationId)
        val afterDestination = inventoryOnHand(fixture.inventoryItemId, fixture.destinationLocationId)

        assertEquals(InventoryTransferStatus.COMPLETED, document.status)
        assertEquals(beforeSource - fixture.quantityMicros, afterSource)
        assertEquals(beforeDestination + fixture.quantityMicros, afterDestination)
        assertEquals(beforeSource + beforeDestination, afterSource + afterDestination)
    }

    @Test
    fun purchaseGoodsReceipt_uiIncreasesStockAndPostsBalancedReceiptJournal() {
        val fixture = seedGoodsReceiptFixture("ui-grn")
        val beforeStock = runBlocking {
            app.container.operationsRepository.inventoryItems.first().first { it.id == fixture.inventoryItemId }.stockMicros
        }
        val beforeJournalIds = runBlocking {
            app.container.accountingUseCases.journals("").first().filter { it.sourceType == "GOODS_RECEIPT" }.map { it.id }.toSet()
        }
        val deliveryNote = "E2E-DN-${System.nanoTime().toString().takeLast(8)}"

        openModule(AppScreen.PURCHASES)
        scrollTo("purchase_list", "procurement_control_panel")
        composeRule.onNodeWithTag("procurement_receive_${fixture.orderId}").performScrollTo().performClick()
        composeRule.onNodeWithTag("procurement_delivery_no").performTextReplacement(deliveryNote)
        composeRule.onNodeWithTag("procurement_receive_submit").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking {
                app.container.operationsRepository.inventoryItems.first()
                    .first { it.id == fixture.inventoryItemId }.stockMicros > beforeStock
            }
        }
        val afterStock = runBlocking {
            app.container.operationsRepository.inventoryItems.first().first { it.id == fixture.inventoryItemId }.stockMicros
        }
        val journal = runBlocking {
            app.container.accountingUseCases.journals("").first().filter { it.sourceType == "GOODS_RECEIPT" }.first { it.id !in beforeJournalIds }
        }
        assertEquals(beforeStock + fixture.quantityMicros, afterStock)
        assertEquals(journal.totalDebitRial, journal.totalCreditRial)
        assertTrue(journal.totalDebitRial > 0L)
    }

    @Test
    fun assetDepreciation_uiUpdatesBookValueAndPostsBalancedJournal() {
        val nonce = System.nanoTime().toString().takeLast(8)
        val assetName = "دارایی E2E $nonce"
        val assetId = runBlocking {
            app.container.assetUseCases.save(
                id = null,
                draft = AssetDraft(
                    assetCode = "",
                    name = assetName,
                    category = "تجهیزات E2E",
                    quantity = 1,
                    purchaseEpochDay = currentLocalEpochDay(),
                    purchaseCostRial = 1_200_000L,
                    salvageValueRial = 0L,
                    usefulLifeMonths = 12,
                    location = "آشپزخانه E2E",
                    notes = "پیش‌نیاز تست استهلاک از مسیر واقعی AssetRepository",
                    acquisitionSource = AssetAcquisitionSource.BANK,
                ),
            )
        }
        val beforeAsset = runBlocking { app.container.assetUseCases.assets.first().first { it.id == assetId } }
        val beforeDepIds = runBlocking { app.container.assetUseCases.depreciations.first().map { it.id }.toSet() }
        val beforeJournalIds = runBlocking {
            app.container.accountingUseCases.journals("").first().filter { it.sourceType == "ASSET_DEPRECIATION" }.map { it.id }.toSet()
        }

        openModule(AppScreen.ASSETS)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("asset_depreciate_open").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("asset_depreciate_open").performClick()
        composeRule.onNodeWithTag("asset_depreciation_picker").performClick()
        composeRule.onNodeWithTag("asset_depreciation_$assetId").performClick()
        composeRule.onNodeWithTag("asset_depreciation_reason").performTextReplacement("استهلاک ماهانه E2E")
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching { composeRule.onNodeWithTag("asset_depreciation_submit").assertIsEnabled(); true }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("asset_depreciation_submit").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { app.container.assetUseCases.depreciations.first().any { it.id !in beforeDepIds && it.assetName == assetName } }
        }
        val afterAsset = runBlocking { app.container.assetUseCases.assets.first().first { it.id == assetId } }
        val depreciation = runBlocking {
            app.container.assetUseCases.depreciations.first().first { it.id !in beforeDepIds && it.assetName == assetName }
        }
        val journal = runBlocking {
            app.container.accountingUseCases.journals("").first().filter { it.sourceType == "ASSET_DEPRECIATION" }.first { it.id !in beforeJournalIds }
        }

        assertTrue(depreciation.amountRial > 0L)
        assertEquals(beforeAsset.accumulatedDepreciationRial + depreciation.amountRial, afterAsset.accumulatedDepreciationRial)
        assertEquals(beforeAsset.bookValueRial - depreciation.amountRial, afterAsset.bookValueRial)
        assertEquals(journal.totalDebitRial, journal.totalCreditRial)
    }




    @Test
    fun payrollRegistrationAndApproval_uiCalculatesReviewsAndPostsAccrual() {
        val fixture = seedPayrollDraftFixture("ui-payroll-approval")

        openModule(AppScreen.PERSONNEL)
        scrollTo("hr_workspace_navigation", "hr_section_PAYROLL")
        composeRule.onNodeWithTag("hr_section_PAYROLL").performClick()
        scrollTo("payroll_center_list", "payroll_calculate_${fixture.batchId}")
        composeRule.onNodeWithTag("payroll_calculate_${fixture.batchId}").performClick()
        composeRule.onNodeWithTag("payroll_calculate_submit").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { payrollBatchStatus(fixture.batchId) == PayrollBatchStatus.CALCULATED }
        }
        scrollTo("payroll_center_list", "payroll_review_${fixture.batchId}")
        composeRule.onNodeWithTag("payroll_review_${fixture.batchId}").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { payrollBatchStatus(fixture.batchId) == PayrollBatchStatus.UNDER_REVIEW }
        }

        runBlocking { app.container.securityRepository.switchUser(fixture.managerId, TEST_MANAGER_PIN) }
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { app.container.securityRepository.currentUser.first()?.id == fixture.managerId } &&
                composeRule.onAllNodesWithTag("payroll_center_list").fetchSemanticsNodes().isNotEmpty()
        }
        scrollTo("payroll_center_list", "payroll_approve_${fixture.batchId}")
        composeRule.onNodeWithTag("payroll_approve_${fixture.batchId}").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { payrollBatchStatus(fixture.batchId) == PayrollBatchStatus.PAYMENT_PENDING }
        }
        val payslip = runBlocking {
            app.container.payrollUseCases.employeePayslips(fixture.employeeId).first()
                .first { it.batchId == fixture.batchId }
        }
        val detail = runBlocking { app.container.payrollUseCases.payslipDetail(payslip.id) }
        val journalId = requireNotNull(detail.accrualJournalEntryId)
        val journal = runBlocking { app.container.accountingUseCases.journalDetails(journalId).first() }
            ?: error("payroll accrual journal missing")

        assertEquals(PayrollPayslipStatus.PAYMENT_PENDING, detail.payslip.status)
        assertEquals("PAYROLL_ACCRUAL", journal.sourceType)
        assertEquals(journal.totalDebitRial, journal.totalCreditRial)
        assertTrue(journal.totalDebitRial > 0L)
        assertTrue(detail.approvalHistory.any { it.eventType == "FINAL_APPROVAL" && it.actorId == fixture.managerId })
    }

    @Test
    fun payrollPayment_uiPostsTreasuryAndClosesPayslipBalance() {
        val fixture = seedPayrollReadyForPaymentFixture("ui-payroll-payment")
        val payslipId = requireNotNull(fixture.payslipId)
        val beforeTransactions = runBlocking {
            app.container.treasuryUseCases.recentTransactions.first().map { it.id }.toSet()
        }
        val paymentReference = "E2E-PAY-${System.nanoTime().toString().takeLast(8)}"

        openModule(AppScreen.PERSONNEL)
        scrollTo("hr_workspace_navigation", "hr_section_PAYROLL")
        composeRule.onNodeWithTag("hr_section_PAYROLL").performClick()
        composeRule.onNodeWithTag("payroll_center_list").performScrollToNode(hasTestTag("payroll_employee_row"))
        composeRule.onNodeWithTag("payroll_employee_row").performScrollToNode(hasTestTag("payroll_employee_${fixture.employeeId}"))
        composeRule.onNodeWithTag("payroll_employee_${fixture.employeeId}").performClick()
        composeRule.onNodeWithTag("payroll_center_list").performScrollToNode(hasTestTag("payroll_payslip_${payslipId}"))
        composeRule.onNodeWithTag("payroll_payslip_${payslipId}").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("payroll_pay_open_${payslipId}").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("payroll_pay_open_${payslipId}").performClick()
        composeRule.onNodeWithTag("payroll_payment_reference").performTextReplacement(paymentReference)
        composeRule.onNodeWithTag("payroll_payment_submit").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { app.container.payrollUseCases.payslipDetail(payslipId).payslip.status == PayrollPayslipStatus.PAID }
        }
        val detail = runBlocking { app.container.payrollUseCases.payslipDetail(payslipId) }
        val payment = detail.payments.first { it.paymentReference == paymentReference }
        val paymentJournal = runBlocking {
            app.container.accountingUseCases.journalDetails(requireNotNull(payment.journalEntryId)).first()
        } ?: error("payroll payment journal missing")
        val treasuryTransaction = runBlocking {
            app.container.treasuryUseCases.recentTransactions.first()
                .first { it.id !in beforeTransactions && it.sourceType == "PAYROLL_PAYMENT" && it.sourceId == payslipId }
        }

        assertEquals(PayrollPaymentStatus.POSTED, payment.status)
        assertEquals(0L, detail.payslip.remainingAmount.value)
        assertEquals(detail.payslip.netPay.value, detail.payslip.paidAmount.value)
        assertEquals("POSTED", treasuryTransaction.status)
        assertEquals("PAYROLL_PAYMENT", paymentJournal.sourceType)
        assertEquals(paymentJournal.totalDebitRial, paymentJournal.totalCreditRial)
        assertEquals(PayrollBatchStatus.PAID, detail.batch.status)
    }

@Test
    fun treasuryReversal_uiCreatesCompensatingJournalLedgerReferenceAndMarksOriginalReversed() {
        val amount = 21_345L
        val sourceId = (System.nanoTime() and Long.MAX_VALUE) % 9_000_000L + 100L
        val sourceType = "OTHER_INCOME"

        openModule(AppScreen.TREASURY)
        scrollTo("treasury_list", "treasury_amount")
        composeRule.onNodeWithTag("treasury_amount").performTextReplacement(amount.toString())
        scrollTo("treasury_list", "treasury_source_type")
        composeRule.onNodeWithTag("treasury_source_type_${sourceType}").performClick()
        scrollTo("treasury_list", "treasury_source_id")
        composeRule.onNodeWithTag("treasury_source_id").performTextReplacement(sourceId.toString())
        scrollTo("treasury_list", "treasury_reason")
        composeRule.onNodeWithTag("treasury_reason").performTextReplacement("دریافت مبنای برگشت E2E")
        scrollTo("treasury_list", "treasury_submit")
        composeRule.onNodeWithTag("treasury_submit").performClick()

        val originalId = waitForTreasuryTransaction(sourceType, sourceId, amount)
        composeRule.onNodeWithTag("treasury_list").performScrollToNode(hasTestTag("treasury_reverse_$originalId"))
        composeRule.onNodeWithTag("treasury_reverse_$originalId").performClick()
        composeRule.onNodeWithTag("treasury_reverse_reason").performTextReplacement("ابطال دریافت از رابط کاربری")
        composeRule.onNodeWithTag("treasury_reverse_confirm").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            app.container.databaseForTesting.openHelper.writableDatabase.query(
                "SELECT status FROM treasury_transactions WHERE id=?",
                arrayOf(originalId),
            ).use { it.moveToFirst() && it.getString(0) == "REVERSED" }
        }
        val reversalId = app.container.databaseForTesting.openHelper.writableDatabase.query(
            "SELECT id FROM treasury_transactions WHERE reversalOfTransactionId=? LIMIT 1",
            arrayOf(originalId),
        ).use { cursor -> check(cursor.moveToFirst()); cursor.getString(0) }
        val reversalJournalId = app.container.databaseForTesting.openHelper.writableDatabase.query(
            "SELECT journalEntryId FROM treasury_transactions WHERE id=?",
            arrayOf(reversalId),
        ).use { cursor -> check(cursor.moveToFirst()); cursor.getLong(0) }
        assertTrue(reversalJournalId > 0L)
        assertEquals(
            1L,
            scalar("SELECT COUNT(*) FROM treasury_ledger_entries WHERE transactionId='$reversalId' AND reference='REVERSAL_OF:$originalId'"),
        )
        assertEquals(
            scalar("SELECT COALESCE(SUM(debitRial),0) FROM journal_lines WHERE entryId=$reversalJournalId"),
            scalar("SELECT COALESCE(SUM(creditRial),0) FROM journal_lines WHERE entryId=$reversalJournalId"),
        )
    }

    @Test
    fun crmCollection_viaCrmUi_updatesReceivableLedgerAndAgingBalance() {
        val customerId = seedCrmCustomer("receipt")
        val openingAmount = 60_000L
        val receiptAmount = 22_000L
        val branchId = runBlocking { ensureE2EBranchId() }
        val customerName = runBlocking {
            app.container.salesHistoryUseCases.customers.first().first { it.id == customerId }.name
        }
        val receivableId = runBlocking {
            app.container.receivableService.createFromDailySales(
                DailySalesReceivableOriginDraft(
                    commandId = GlobalId.new().value,
                    branchId = branchId,
                    partyId = customerId,
                    type = ReceivableType.PERSONAL,
                    dailySalesId = (System.nanoTime() and Long.MAX_VALUE) % 9_000_000L + 100L,
                    amountRial = openingAmount,
                    issueEpochDay = currentLocalEpochDay(),
                    dueEpochDay = currentLocalEpochDay() + 30,
                ),
            )
        }

        openModule(AppScreen.CRM)
        scrollTo("crm_list", "receivables_branch_selector")
        composeRule.onNodeWithTag("receivables_branch_selector").performClick()
        composeRule.onNodeWithTag("receivables_branch_selector_branch_$branchId").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { app.container.receivableService.observeOpen(branchId).first().any { it.id == receivableId } }
        }
        scrollTo("crm_list", "receivables_open_list")
        scrollTo("receivables_open_list", "receivable_select_$receivableId")
        composeRule.onNodeWithTag("receivable_select_$receivableId").performClick()
        composeRule.onNodeWithTag("receivable_collection_amount").performTextReplacement(receiptAmount.toString())
        composeRule.waitUntil(timeoutMillis = 20_000) {
            runCatching { composeRule.onNodeWithTag("receivable_collection_confirm").assertIsEnabled(); true }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("receivable_collection_confirm").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking {
                app.container.crmUseCases.ledger(customerId).first().any {
                    it.entryType == "COLLECTION" && it.creditRial == receiptAmount
                }
            }
        }
        val ledger = runBlocking { app.container.crmUseCases.ledger(customerId).first() }
        val remaining = runBlocking {
            app.container.receivableService.observeOpen(branchId).first().first { it.id == receivableId }.outstandingAmountRial
        }
        assertTrue(ledger.any { it.entryType == "COLLECTION" && it.creditRial == receiptAmount })
        assertEquals(openingAmount - receiptAmount, ledger.sumOf { it.debitRial - it.creditRial })
        assertEquals(openingAmount - receiptAmount, remaining)
    }

    @Test
    fun crmAdjustment_uiToUseCaseLedgerAccountingAndAudit_isPersisted() {
        val customerId = seedCrmCustomer("adjust")
        val amount = 31_000L
        val beforeJournalCount = scalar("SELECT COUNT(*) FROM journal_entries WHERE sourceType='CRM_ADJUSTMENT'")

        openModule(AppScreen.CRM)
        scrollTo("crm_list", "crm_customer_list")
        scrollTo("crm_customer_list", "crm_select_$customerId")
        composeRule.onNodeWithTag("crm_select_$customerId").performClick()
        scrollTo("crm_list", "crm_adjustment_action")
        composeRule.onNodeWithTag("crm_adjustment_action").performClick()
        composeRule.onNodeWithTag("crm_account_amount").performTextReplacement(amount.toString())
        composeRule.onNodeWithTag("crm_account_reason").performTextReplacement("تعدیل E2E حساب مشتری")
        composeRule.onNodeWithTag("crm_adjustment_confirm").performClick()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking { app.container.crmUseCases.ledger(customerId).first().any { it.entryType == "ADJUSTMENT" && it.debitRial == amount } }
        }
        val row = runBlocking { app.container.crmUseCases.ledger(customerId).first().first { it.entryType == "ADJUSTMENT" && it.debitRial == amount } }
        assertTrue(row.reference.isNotBlank())
        assertEquals(beforeJournalCount + 1, scalar("SELECT COUNT(*) FROM journal_entries WHERE sourceType='CRM_ADJUSTMENT'"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM audit_logs WHERE action='ADJUST' AND entityType='CUSTOMER_RECEIVABLE' AND entityId=${row.id}"))
    }


    private fun scrollTo(containerTag: String, targetTag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(containerTag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(containerTag).performScrollToNode(hasTestTag(targetTag))
    }

    private fun openModule(screen: AppScreen) {
        val moduleTag = when (screen) {
            AppScreen.INVENTORY -> "module_INVENTORY_موجودی"
            AppScreen.ASSETS -> "module_ASSETS_دارایی‌ها"
            AppScreen.RECIPES -> "module_RECIPES_رسپی و Costing"
            AppScreen.CRM -> "module_CRM_مطالبات"
            AppScreen.PERSONNEL -> "module_PERSONNEL_پرسنل"
            AppScreen.TREASURY -> "module_TREASURY_خزانه"
            AppScreen.PURCHASES -> "module_PURCHASES_خرید"
            else -> error("E2E module route is not configured: ${screen.name}")
        }
        val topLevel = screen.topLevelDestination()
        val hubTag = when (topLevel) {
            AppScreen.OPERATIONS_HUB -> "operations_hub"
            AppScreen.FINANCE_HUB -> "finance_hub"
            AppScreen.CONTROL_HUB -> "control_hub"
            AppScreen.MORE -> "more_hub"
            else -> error("E2E screen is not routed through a module hub: ${screen.name}")
        }
        composeRule.onNodeWithTag("nav_${topLevel.name.lowercase()}").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(hubTag).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(hubTag).performScrollToNode(hasTestTag(moduleTag))
        composeRule.onNodeWithTag(moduleTag).performClick()
    }

    private fun waitForTreasuryTransaction(sourceType: String, sourceId: Long, amountRial: Long): String {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking {
                app.container.treasuryLedgerReader.recentTransactions.first().any {
                    it.sourceType == sourceType && it.sourceId == sourceId && it.amountRial == amountRial && it.status == "POSTED"
                }
            }
        }
        return runBlocking {
            app.container.treasuryLedgerReader.recentTransactions.first().first {
                it.sourceType == sourceType && it.sourceId == sourceId && it.amountRial == amountRial && it.status == "POSTED"
            }.id
        }
    }

    private fun seedCrmCustomer(prefix: String): Long = runBlocking {
        val nonce = System.nanoTime().toString().takeLast(8)
        app.container.salesHistoryUseCases.saveCustomer(
            null,
            CustomerDraft(
                name = "مشتری CRM $prefix $nonce",
                phone = "09${nonce.padStart(9, '1').takeLast(9)}",
                creditLimitRial = 1_000_000L,
                branch = "E2E",
                paymentTermsDays = 30,
                status = "ACTIVE",
                notes = "E2E fixture",
            ),
        )
    }

    private fun scalar(sql: String): Long = app.container.databaseForTesting.openHelper.writableDatabase.query(sql).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getLong(0)
    }

    private data class PayrollFixture(
        val employeeId: Long,
        val periodId: Long,
        val batchId: Long,
        val managerId: Long,
        val payslipId: Long? = null,
    )

    private fun seedPayrollDraftFixture(prefix: String): PayrollFixture = runBlocking {
        val security = app.container.securityRepository
        val owner = requireNotNull(security.currentUser.first())
        val manager = ensureTestManager()
        val payrollApprover = ensureTestPayrollApprover()
        val nonce = System.nanoTime().toString().takeLast(8)
        val day = currentLocalEpochDay()
        val branchId = app.container.branchRepository.findDeterministicLegacyMapping("شعبه E2E")?.id
            ?: app.container.branchRepository.create(BranchDraft(name = "شعبه E2E", code = "E2E"))
        val policyId = ensurePayrollPolicy(day)
        val employeeId = app.container.personnelUseCases.saveEmployee(
            id = null,
            draft = EmployeeDraft(
                name = "کارمند $prefix $nonce",
                fatherName = "آزمون",
                jobTitle = "کارشناس E2E",
                phone = "",
                monthlySalaryRial = 9_000_000L,
                hireEpochDay = day - 30,
                employeeCode = "E2E$nonce",
                branchName = "شعبه E2E",
                department = "کنترل کیفیت E2E",
                branchId = branchId,
            ),
        )
        val shiftTemplateId = app.container.personnelUseCases.saveShiftTemplate(
            id = null,
            draft = ShiftTemplateDraft(
                code = "E2ESHIFT$nonce",
                name = "شیفت E2E $nonce",
                category = ShiftCategory.MORNING,
                startMinute = 8 * 60,
                endMinute = 16 * 60,
                overtimeRequiresApproval = false,
                branchId = branchId,
                notes = "شیفت واقعی پیش‌نیاز Payroll E2E",
            ),
        )
        val workScheduleId = app.container.personnelUseCases.saveWorkSchedule(
            id = null,
            draft = WorkScheduleDraft(
                code = "E2ESCHED$nonce",
                name = "برنامه کاری E2E $nonce",
                patternType = WorkSchedulePatternType.WEEKLY_FIXED,
                cycleLengthDays = 7,
                effectiveFromEpochDay = day - 30,
                effectiveToEpochDay = day + 30,
                branchName = "شعبه E2E",
                branchId = branchId,
                notes = "برنامه واقعی پیش‌نیاز Payroll E2E",
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
        val contractId = app.container.personnelUseCases.saveContract(
            id = null,
            draft = EmployeeContractDraft(
                employeeId = employeeId,
                contractType = "PERMANENT",
                startEpochDay = day - 30,
                endEpochDay = day + 30,
                baseSalaryRial = 9_000_000L,
                dailyWorkMinutes = 480,
                weeklyWorkDays = 7,
                payrollPolicyId = policyId,
                workScheduleId = workScheduleId,
                defaultShiftTemplateId = shiftTemplateId,
                notes = "قرارداد پیش‌نیاز E2E",
            ),
        )
        security.switchUser(manager.id, TEST_MANAGER_PIN)
        app.container.personnelUseCases.approveContract(contractId)
        security.switchUser(owner.id, TEST_OWNER_PIN)
        app.container.attendanceUseCases.save(
            id = null,
            draft = AttendanceDraft(
                employeeId = employeeId,
                workEpochDay = day,
                status = "PRESENT",
                checkInMinute = 8 * 60,
                checkOutMinute = 16 * 60,
                scheduledStartMinute = 8 * 60,
                scheduledEndMinute = 16 * 60,
                notes = "حضور واقعی پیش‌نیاز Payroll E2E",
            ),
        )
        val periodId = app.container.payrollUseCases.openPeriod(
            PayrollPeriodDraftV2(
                periodKey = "E2E-$nonce",
                startEpochDay = day,
                endEpochDay = day,
                paymentDueEpochDay = day,
            ),
        )
        val batchId = app.container.payrollUseCases.createBatch(
            PayrollBatchDraftV2(
                periodId = periodId,
                scope = "BRANCH",
                branchId = branchId,
                notes = "E2E payroll batch $prefix",
            ),
        )
        PayrollFixture(employeeId, periodId, batchId, payrollApprover.id)
    }

    private fun seedPayrollReadyForPaymentFixture(prefix: String): PayrollFixture = runBlocking {
        val fixture = seedPayrollDraftFixture(prefix)
        val security = app.container.securityRepository
        val owner = requireNotNull(security.currentUser.first())
        val outcome = app.container.payrollUseCases.calculateBatch(
            CalculatePayrollBatchCommand(
                batchId = fixture.batchId,
                employeeIds = listOf(fixture.employeeId),
            ),
        )
        require(!outcome.hasBlockingExceptions) { "payroll fixture blocked: ${outcome.exceptions}" }
        app.container.payrollUseCases.submitBatch(
            ReviewPayrollBatchCommand(fixture.batchId, "بازبینی E2E مستقل از پرداخت"),
        )
        security.switchUser(fixture.managerId, TEST_MANAGER_PIN)
        app.container.payrollUseCases.approveBatch(
            ApprovePayrollBatchCommand(fixture.batchId, "تأیید نهایی E2E توسط مدیر مستقل"),
        )
        security.switchUser(owner.id, TEST_OWNER_PIN)
        val payslip = app.container.payrollUseCases.employeePayslips(fixture.employeeId).first()
            .first { it.batchId == fixture.batchId }
        require(payslip.status == PayrollPayslipStatus.PAYMENT_PENDING) { "fixture payslip not ready for payment: ${payslip.status}" }
        fixture.copy(payslipId = payslip.id)
    }

    private suspend fun ensurePayrollPolicy(day: Long): Long {
        app.container.personnelUseCases.payrollPolicies.first().firstOrNull {
            it.effectiveFromEpochDay <= day && (it.effectiveToEpochDay == null || it.effectiveToEpochDay >= day)
        }?.let { return it.id }
        return app.container.personnelUseCases.savePayrollPolicy(
            PayrollPolicyDraft(
                title = "سیاست حقوق E2E",
                effectiveFromEpochDay = day - 365,
                effectiveToEpochDay = null,
                overtimeHourlyRateRial = 0L,
                absenceDailyDeductionRial = 0L,
                lateMinuteDeductionRial = 0L,
                insuranceBasisPoints = 0,
                taxBasisPoints = 0,
            ),
        )
    }

    private suspend fun payrollBatchStatus(batchId: Long): PayrollBatchStatus =
        app.container.payrollUseCases.batches.first().first { it.id == batchId }.status

    private suspend fun ensureE2EBranchId(): Long =
        app.container.branchRepository.findDeterministicLegacyMapping("شعبه E2E")?.id
            ?: app.container.branchRepository.create(BranchDraft(name = "شعبه E2E", code = "E2E"))

    private suspend fun createE2EInventoryLocation(
        label: String,
        codePrefix: String,
        branchId: Long,
        type: InventoryLocationType = InventoryLocationType.WAREHOUSE,
    ): Long {
        val nonce = System.nanoTime().toString().takeLast(8)
        return app.container.inventoryUseCases.saveLocation(
            id = null,
            draft = InventoryLocationDraft(
                code = "E2E${codePrefix.take(3).uppercase()}${nonce.takeLast(6)}",
                name = "$label $nonce",
                type = type,
                branchName = "شعبه E2E",
                branchId = branchId,
            ),
        )
    }

    private suspend fun grantManagerE2EBranch(managerId: Long, branchId: Long) {
        app.container.securityRepository.updateDataScope(
            UserDataScope(
                userId = managerId,
                primaryBranchId = branchId,
                allowedBranchIds = setOf(branchId),
                allowedWarehouseIds = emptySet(),
            ),
            reason = "E2E branch-scoped manager workflow",
        )
    }

    private data class InventoryCountFixture(
        val sessionId: Long,
        val lineId: Long,
        val inventoryItemId: Long,
        val locationId: Long,
        val managerId: Long,
    )

    private fun seedInventoryCountFixture(prefix: String): InventoryCountFixture = runBlocking {
        val security = app.container.securityRepository
        val owner = requireNotNull(security.currentUser.first())
        val manager = ensureTestManager()
        val nonce = System.nanoTime().toString().takeLast(8)
        val itemId = app.container.operationsRepository.createInventoryItem(
            InventoryItemDraft(
                name = "کالای $prefix $nonce",
                category = "E2E",
                unit = "عدد",
                alertEnabled = false,
                alertThresholdMicros = 0,
                supplierId = null,
            ),
        )
        val branchId = ensureE2EBranchId()
        val locationId = createE2EInventoryLocation("انبار شمارش $prefix", "CNT", branchId)
        grantManagerE2EBranch(manager.id, branchId)
        app.container.inventoryUseCases.receive(
            ReceiveInventoryCommand(
                itemId = itemId,
                quantityMicros = 10 * QuantityMicros.SCALE,
                valueRial = 1_000_000L,
                movementType = InventoryMovementType.OPENING_BALANCE,
                referenceType = InventoryReferenceType.MIGRATION,
                referenceId = itemId,
                businessEpochDay = currentLocalEpochDay(),
                context = InventoryCommandContext.local(
                    referenceType = InventoryReferenceType.MIGRATION,
                    referenceId = itemId,
                    suffix = "count-opening-$nonce",
                    actorId = owner.id,
                    reasonCode = InventoryReasonCode.OPENING_BALANCE,
                    reason = "E2E count opening stock",
                    locationId = locationId,
                ),
                notes = "E2E count prerequisite",
            ),
        )
        val sessionId = app.container.inventoryCountService.create(
            CreateInventoryCountSessionCommand(
                locationId = locationId,
                scope = InventoryCountScope.ITEM_SELECTION,
                itemIds = setOf(itemId),
                blindCount = false,
                assignedToActorId = owner.id,
                businessEpochDay = currentLocalEpochDay(),
                notes = "E2E count UI workflow",
            ),
        )
        app.container.inventoryCountService.open(
            InventoryCountActionCommand(sessionId, owner.id, "باز کردن جلسه E2E"),
        )
        val line = app.container.inventoryCountService.lines(sessionId, canReviewVariance = true).single()
        InventoryCountFixture(sessionId, line.lineId, itemId, locationId, manager.id)
    }

    private data class TransferFixture(
        val transferId: Long,
        val inventoryItemId: Long,
        val sourceLocationId: Long,
        val destinationLocationId: Long,
        val quantityMicros: Long,
    )

    private fun seedTransferFixture(prefix: String): TransferFixture = runBlocking {
        val actor = requireNotNull(app.container.securityRepository.currentUser.first())
        val nonce = System.nanoTime().toString().takeLast(8)
        val itemId = app.container.operationsRepository.createInventoryItem(
            InventoryItemDraft(
                name = "کالای $prefix $nonce",
                category = "E2E",
                unit = "عدد",
                alertEnabled = false,
                alertThresholdMicros = 0,
                supplierId = null,
            ),
        )
        val branchId = ensureE2EBranchId()
        val sourceId = createE2EInventoryLocation("مبدأ انتقال $prefix", "SRC", branchId)
        val destinationId = createE2EInventoryLocation(
            label = "مقصد انتقال $prefix",
            codePrefix = "DST",
            branchId = branchId,
            type = InventoryLocationType.KITCHEN,
        )
        app.container.inventoryUseCases.receive(
            ReceiveInventoryCommand(
                itemId = itemId,
                quantityMicros = 10 * QuantityMicros.SCALE,
                valueRial = 1_000_000L,
                movementType = InventoryMovementType.OPENING_BALANCE,
                referenceType = InventoryReferenceType.MIGRATION,
                referenceId = itemId,
                businessEpochDay = currentLocalEpochDay(),
                context = InventoryCommandContext.local(
                    referenceType = InventoryReferenceType.MIGRATION,
                    referenceId = itemId,
                    suffix = "transfer-opening-$nonce",
                    actorId = actor.id,
                    reasonCode = InventoryReasonCode.OPENING_BALANCE,
                    reason = "E2E transfer opening stock",
                    locationId = sourceId,
                ),
                notes = "E2E transfer prerequisite",
            ),
        )
        val quantity = 3 * QuantityMicros.SCALE
        val transfer = app.container.inventoryTransferService.create(
            CreateInventoryTransferCommand(
                sourceLocationId = sourceId,
                destinationLocationId = destinationId,
                businessEpochDay = currentLocalEpochDay(),
                lines = listOf(CreateInventoryTransferLine(itemId = itemId, requestedQuantityMicros = quantity)),
                notes = "E2E transfer UI state workflow",
                actorId = actor.id,
            ),
        )
        TransferFixture(transfer.id, itemId, sourceId, destinationId, quantity)
    }

    private fun inventoryOnHand(itemId: Long, locationId: Long): Long = runBlocking {
        app.container.inventoryUseCases.balances(InventoryBalanceQuery(locationId = locationId))
            .firstOrNull { it.itemId == itemId }?.onHandMicros ?: 0L
    }

    private data class GoodsReceiptFixture(
        val orderId: Long,
        val inventoryItemId: Long,
        val quantityMicros: Long,
    )

    private fun seedGoodsReceiptFixture(prefix: String): GoodsReceiptFixture = runBlocking {
        val security = app.container.securityRepository
        val owner = requireNotNull(security.currentUser.first())
        val manager = ensureTestManager()
        val nonce = System.nanoTime().toString().takeLast(8)
        val department = "آشپزخانه E2E $nonce"
        val day = currentLocalEpochDay()
        val branchId = ensureE2EBranchId()
        val destinationLocationId = createE2EInventoryLocation("انبار دریافت $prefix", "GRN", branchId)
        grantManagerE2EBranch(manager.id, branchId)
        val quantity = 2 * QuantityMicros.SCALE
        val unitCost = 75_000L
        val supplierId = app.container.operationsRepository.createSupplier(
            SupplierDraft(name = "تأمین‌کننده $prefix $nonce", paymentTermsDays = 7),
        )
        val itemId = app.container.operationsRepository.createInventoryItem(
            InventoryItemDraft(
                name = "کالای $prefix $nonce",
                category = "E2E",
                unit = "عدد",
                alertEnabled = false,
                alertThresholdMicros = 0,
                supplierId = supplierId,
            ),
        )
        app.container.managementControlRepository.saveBudget(
            id = null,
            draft = BudgetDraft(
                name = "بودجه $prefix $nonce",
                category = BudgetCategory.PURCHASE,
                costCenter = department,
                fromEpochDay = day - 1,
                toEpochDay = day + 30,
                limitRial = 10_000_000L,
            ),
        )
        val requisitionId = app.container.procurementUseCases.submitRequisition(
            PurchaseRequisitionDraft(
                department = department,
                requiredEpochDay = day,
                note = "E2E requisition prerequisite",
                lines = listOf(RequisitionLineDraft(itemId, quantity, unitCost)),
                branchId = branchId,
                destinationLocationId = destinationLocationId,
            ),
        )
        security.switchUser(manager.id, TEST_MANAGER_PIN)
        app.container.procurementUseCases.reviewRequisition(requisitionId, approve = true, note = "E2E independent approval")
        security.switchUser(owner.id, TEST_OWNER_PIN)
        val orderId = app.container.procurementUseCases.createOrder(
            PurchaseOrderDraft(
                requisitionId = requisitionId,
                supplierId = supplierId,
                orderEpochDay = day,
                expectedEpochDay = day + 1,
                note = "E2E open PO for UI receipt",
            ),
        )
        GoodsReceiptFixture(orderId, itemId, quantity)
    }

    private suspend fun ensureTestPayrollApprover() = app.container.securityRepository.users.first()
        .firstOrNull { it.username == TEST_PAYROLL_APPROVER_USERNAME }
        ?: run {
            app.container.securityRepository.save(
                id = null,
                draft = UserDraft(
                    username = TEST_PAYROLL_APPROVER_USERNAME,
                    displayName = "مالک مستقل تأیید حقوق enterprise-core",
                    pin = TEST_MANAGER_PIN,
                    role = UserRole.OWNER,
                    recoveryCode = TEST_PAYROLL_APPROVER_RECOVERY,
                ),
            )
            app.container.securityRepository.users.first().first { it.username == TEST_PAYROLL_APPROVER_USERNAME }
        }

    private suspend fun ensureTestManager() = app.container.securityRepository.users.first()
        .firstOrNull { it.username == TEST_MANAGER_USERNAME }
        ?: run {
            app.container.securityRepository.save(
                id = null,
                draft = UserDraft(
                    username = TEST_MANAGER_USERNAME,
                    displayName = "مدیر آزمون enterprise-core",
                    pin = TEST_MANAGER_PIN,
                    role = UserRole.MANAGER,
                    recoveryCode = TEST_MANAGER_RECOVERY,
                ),
            )
            app.container.securityRepository.users.first().first { it.username == TEST_MANAGER_USERNAME }
        }

    private data class SaleFixture(
        val inventoryItemId: Long,
        val menuItemId: Long,
        val salePriceRial: Long,
    )

    private fun seedSaleFixture(prefix: String): SaleFixture = runBlocking {
        val actor = requireNotNull(app.container.securityRepository.currentUser.first())
        val nonce = System.nanoTime().toString().takeLast(8)
        val inventoryItemId = app.container.operationsRepository.createInventoryItem(
            InventoryItemDraft(
                name = "ماده $prefix $nonce",
                category = "E2E",
                unit = "عدد",
                alertEnabled = false,
                alertThresholdMicros = 0,
                supplierId = null,
            ),
        )
        val locationId = app.container.inventoryRepository.defaultLocationId()
        app.container.inventoryCommandService.receive(
            ReceiveInventoryCommand(
                itemId = inventoryItemId,
                quantityMicros = 20 * QuantityMicros.SCALE,
                valueRial = 200_000L,
                movementType = InventoryMovementType.OPENING_BALANCE,
                referenceType = InventoryReferenceType.MIGRATION,
                referenceId = inventoryItemId,
                businessEpochDay = currentLocalEpochDay(),
                context = InventoryCommandContext.local(
                    referenceType = InventoryReferenceType.MIGRATION,
                    referenceId = inventoryItemId,
                    suffix = "e2e_opening",
                    actorId = actor.id,
                    reasonCode = InventoryReasonCode.OPENING_BALANCE,
                    reason = "E2E opening stock",
                    locationId = locationId,
                ),
                notes = "E2E prerequisite stock",
            ),
        )
        val salePrice = 50_000L
        val menuItemId = app.container.recipeRepository.saveMenuItem(
            id = null,
            name = "محصول $prefix $nonce",
            category = "E2E",
            salePriceRial = salePrice,
            ingredients = listOf(RecipeIngredientInput(inventoryItemId, QuantityMicros.SCALE)),
        )
        SaleFixture(inventoryItemId, menuItemId, salePrice)
    }

    private companion object {
        const val TEST_OWNER_USERNAME = "e2eowner"
        const val TEST_OWNER_PIN = "246810"
        val KNOWN_OWNER_USERNAMES = setOf(TEST_OWNER_USERNAME, "ux2owner", "authowner")
        const val TEST_OWNER_RECOVERY = "13572468"
        const val TEST_MANAGER_USERNAME = "e2emanager"
        const val TEST_MANAGER_PIN = "654321"
        const val TEST_MANAGER_RECOVERY = "24681357"
        const val TEST_PAYROLL_APPROVER_USERNAME = "e2epayrollapprover"
        const val TEST_PAYROLL_APPROVER_RECOVERY = "97531864"
    }
}
