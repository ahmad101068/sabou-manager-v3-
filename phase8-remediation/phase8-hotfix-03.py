from pathlib import Path
import re, sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else 'phase8-source')
path = root / 'app/src/androidTest/java/ir/restaurant/management/ui/EnterpriseCoreComposeE2ETest.kt'
text = path.read_text()

for old in (
    'import ir.restaurant.management.domain.crm.CustomerOpeningBalanceCommand\n',
    'import ir.restaurant.management.domain.crm.ReceivableAdjustmentDirection\n',
):
    text = text.replace(old, '')

imports = [
    ('import ir.restaurant.management.core.MoneyRial\n', 'import ir.restaurant.management.core.GlobalId\n'),
    ('import ir.restaurant.management.domain.operations.UserDraft\n', 'import ir.restaurant.management.domain.operations.UserDataScope\n'),
    ('import ir.restaurant.management.domain.recipe.RecipeIngredientInput\n', 'import ir.restaurant.management.domain.receivables.DailySalesReceivableOriginDraft\nimport ir.restaurant.management.domain.receivables.ReceivableType\n'),
]
for anchor, addition in imports:
    if addition not in text:
        if anchor not in text:
            raise SystemExit(f'import anchor missing: {anchor.strip()}')
        text = text.replace(anchor, anchor + addition, 1)

text = text.replace('val sourceType = "UI_E2E_RECEIPT"', 'val sourceType = "OTHER_INCOME"')
text = text.replace('val sourceType = "UI_E2E_REVERSAL"', 'val sourceType = "OTHER_INCOME"')

crm_pattern = re.compile(
    r'''    @Test\n    fun crmReceipt_viaTreasuryUi_updatesReceivableLedgerAndAgingBalance\(\) \{.*?\n    \}\n\n    @Test\n    fun crmAdjustment_uiToUseCaseLedgerAccountingAndAudit_isPersisted''',
    re.S,
)
crm_replacement = '''    @Test
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
            runBlocking { app.container.receivableService.observeOpen(branchId).first().any { it.id == receivableId } } &&
                composeRule.onAllNodesWithText(customerName).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText(customerName)[0].performClick()
        composeRule.onNodeWithTag("receivable_collection_amount").performTextReplacement(receiptAmount.toString())
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
    fun crmAdjustment_uiToUseCaseLedgerAccountingAndAudit_isPersisted'''
text, n = crm_pattern.subn(crm_replacement, text, count=1)
if n != 1 and 'fun crmCollection_viaCrmUi_updatesReceivableLedgerAndAgingBalance()' not in text:
    raise SystemExit('CRM collection test block not found')

helper_anchor = '''    private data class InventoryCountFixture(
'''
helpers = '''    private suspend fun ensureE2EBranchId(): Long =
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

'''
if helpers not in text:
    if helper_anchor not in text:
        raise SystemExit('inventory fixture helper anchor missing')
    text = text.replace(helper_anchor, helpers + helper_anchor, 1)

old = '''        val locationId = app.container.inventoryUseCases.defaultLocationId()
        app.container.inventoryUseCases.receive(
'''
new = '''        val branchId = ensureE2EBranchId()
        val locationId = createE2EInventoryLocation("انبار شمارش $prefix", "CNT", branchId)
        grantManagerE2EBranch(manager.id, branchId)
        app.container.inventoryUseCases.receive(
'''
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise SystemExit('inventory count legacy location block missing')

old = '''        val sourceId = app.container.inventoryUseCases.defaultLocationId()
        val destinationId = app.container.inventoryUseCases.saveLocation(
            id = null,
            draft = InventoryLocationDraft(
                code = "E2E${nonce.takeLast(6)}",
                name = "مقصد انتقال $nonce",
                type = InventoryLocationType.KITCHEN,
            ),
        )
'''
new = '''        val branchId = ensureE2EBranchId()
        val sourceId = createE2EInventoryLocation("مبدأ انتقال $prefix", "SRC", branchId)
        val destinationId = createE2EInventoryLocation(
            label = "مقصد انتقال $prefix",
            codePrefix = "DST",
            branchId = branchId,
            type = InventoryLocationType.KITCHEN,
        )
'''
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise SystemExit('transfer legacy location block missing')

old = '''        val department = "آشپزخانه E2E $nonce"
        val day = currentLocalEpochDay()
        val quantity = 2 * QuantityMicros.SCALE
'''
new = '''        val department = "آشپزخانه E2E $nonce"
        val day = currentLocalEpochDay()
        val branchId = ensureE2EBranchId()
        val destinationLocationId = createE2EInventoryLocation("انبار دریافت $prefix", "GRN", branchId)
        grantManagerE2EBranch(manager.id, branchId)
        val quantity = 2 * QuantityMicros.SCALE
'''
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise SystemExit('goods receipt setup anchor missing')

old = '''            PurchaseRequisitionDraft(
                department = department,
                requiredEpochDay = day,
                note = "E2E requisition prerequisite",
                lines = listOf(RequisitionLineDraft(itemId, quantity, unitCost)),
            ),
'''
new = '''            PurchaseRequisitionDraft(
                department = department,
                requiredEpochDay = day,
                note = "E2E requisition prerequisite",
                lines = listOf(RequisitionLineDraft(itemId, quantity, unitCost)),
                branchId = branchId,
                destinationLocationId = destinationLocationId,
            ),
'''
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise SystemExit('goods receipt requisition fixture block missing')

path.write_text(text)

final = path.read_text()
for forbidden in ('UI_E2E_RECEIPT', 'UI_E2E_REVERSAL', 'performTextReplacement("CUSTOMER_RECEIVABLE")',
                  'app.container.inventoryUseCases.defaultLocationId()'):
    if forbidden in final:
        raise SystemExit(f'stale Phase8 E2E boundary remains: {forbidden}')
required = (
    'val sourceType = "OTHER_INCOME"',
    'fun crmCollection_viaCrmUi_updatesReceivableLedgerAndAgingBalance()',
    'app.container.receivableService.createFromDailySales(',
    'receivable_collection_confirm',
    'entryType == "COLLECTION"',
    'UserDataScope(',
    'allowedBranchIds = setOf(branchId)',
    'branchId = branchId,\n                destinationLocationId = destinationLocationId,',
    'branchId = branchId,\n            ),\n        )',
)
for needle in required:
    if needle not in final:
        raise SystemExit(f'required Phase8 E2E invariant missing: {needle}')
if final.count('val sourceType = "OTHER_INCOME"') < 2:
    raise SystemExit('both manual treasury E2E flows must use explicit OTHER_INCOME intent')
print('PHASE8_HOTFIX_03_SCOPED_E2E_AND_TYPED_TREASURY=PASS')
