#!/usr/bin/env python3
from pathlib import Path
import subprocess, tempfile

PATCH = r'''--- a/app/src/androidTest/java/ir/sabou/inventory/ui/StartupAuthenticationBoundaryComposeTest.kt
+++ b/app/src/androidTest/java/ir/sabou/inventory/ui/StartupAuthenticationBoundaryComposeTest.kt
@@ -1,6 +1,7 @@
 package ir.sabou.inventory.ui
 
 import androidx.compose.ui.test.assertDoesNotExist
 import androidx.compose.ui.test.assertIsDisplayed
+import androidx.compose.ui.test.hasTestTag
 import androidx.compose.ui.test.junit4.createAndroidComposeRule
 import androidx.compose.ui.test.onAllNodesWithTag
 import androidx.compose.ui.test.onNodeWithTag
@@ -8,6 +9,7 @@
 import androidx.compose.ui.test.onNodeWithText
 import androidx.compose.ui.test.performClick
 import androidx.compose.ui.test.performScrollTo
+import androidx.compose.ui.test.performScrollToNode
 import androidx.compose.ui.test.performTextReplacement
 import ir.sabou.inventory.MainActivity
 import ir.sabou.inventory.SabouApplication
@@ -58,7 +60,9 @@
         composeRule.onNodeWithTag("home_action_personnel").assertDoesNotExist()
 
         composeRule.onNodeWithTag("nav_more").performClick()
-        composeRule.onNodeWithTag("module_SECURITY").performScrollTo().performClick()
+        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("more_hub").fetchSemanticsNodes().isNotEmpty() }
+        composeRule.onNodeWithTag("more_hub").performScrollToNode(hasTestTag("module_SECURITY"))
+        composeRule.onNodeWithTag("module_SECURITY").performClick()
         composeRule.onNodeWithTag("security_logout").performClick()
         composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("security_root").fetchSemanticsNodes().isNotEmpty() }
         composeRule.onNodeWithTag("home_dashboard").assertDoesNotExist()
@@ -76,8 +80,10 @@
         composeRule.onNodeWithTag("security_login_confirm").performClick()
         composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("home_dashboard").fetchSemanticsNodes().isNotEmpty() }
         composeRule.onNodeWithTag("nav_more").performClick()
+        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("more_hub").fetchSemanticsNodes().isNotEmpty() }
+        composeRule.onNodeWithTag("more_hub").performScrollToNode(hasTestTag("module_PERSONNEL"))
         composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("module_PERSONNEL").fetchSemanticsNodes().isNotEmpty() }
-        composeRule.onNodeWithTag("module_PERSONNEL").performScrollTo().performClick()
+        composeRule.onNodeWithTag("module_PERSONNEL").performClick()
         composeRule.onNodeWithText("منابع انسانی و حقوق").assertIsDisplayed()
     }
 
--- a/app/src/androidTest/java/ir/sabou/inventory/ui/EnterpriseCoreComposeE2ETest.kt
+++ b/app/src/androidTest/java/ir/sabou/inventory/ui/EnterpriseCoreComposeE2ETest.kt
@@ -1,10 +1,12 @@
 package ir.sabou.inventory.ui
 
 import androidx.compose.ui.test.assertIsDisplayed
+import androidx.compose.ui.test.hasTestTag
 import androidx.compose.ui.test.junit4.createAndroidComposeRule
 import androidx.compose.ui.test.onAllNodesWithTag
 import androidx.compose.ui.test.onAllNodesWithText
 import androidx.compose.ui.test.onNodeWithTag
 import androidx.compose.ui.test.performClick
 import androidx.compose.ui.test.performScrollTo
+import androidx.compose.ui.test.performScrollToNode
 import androidx.compose.ui.test.performTextReplacement
 import ir.sabou.inventory.MainActivity
 import ir.sabou.inventory.SabouApplication
@@ -162,7 +164,7 @@
     @Test
     fun professionalSale_uiToInventoryCogsAndAccounting_isAtomic() {
         val fixture = seedSaleFixture("ui-sale")
         val beforeInvoiceIds = runBlocking { app.container.salesUseCases.invoices("").first().map { it.id }.toSet() }
-        val beforeJournalIds = runBlocking { app.container.accountingUseCases.journals("SALES_INVOICE").first().map { it.id }.toSet() }
+        val beforeJournalIds = runBlocking { app.container.accountingUseCases.journals("").first().filter { it.sourceType == "SALES_INVOICE" }.map { it.id }.toSet() }
         val beforeStock = runBlocking {
             app.container.operationsRepository.inventoryItems.first().first { it.id == fixture.inventoryItemId }.stockMicros
         }
@@ -188,7 +190,7 @@
         assertEquals(fixture.salePriceRial, invoice.netRial)
         assertEquals(1, details.payments.size)
         assertEquals(SalesPaymentMethod.CASH, details.payments.single().method)
         val saleJournal = runBlocking {
-            app.container.accountingUseCases.journals("SALES_INVOICE").first().first { it.id !in beforeJournalIds }
+            app.container.accountingUseCases.journals("").first().filter { it.sourceType == "SALES_INVOICE" }.first { it.id !in beforeJournalIds }
         }
         assertEquals(saleJournal.totalDebitRial, saleJournal.totalCreditRial)
         assertTrue("COGS snapshot must be positive", details.invoice.theoreticalCostRial > 0L)
@@ -218,7 +220,7 @@
         }
         val stockAfterSale = runBlocking {
             app.container.operationsRepository.inventoryItems.first().first { it.id == fixture.inventoryItemId }.stockMicros
         }
         val beforeReturnJournalIds = runBlocking {
-            app.container.accountingUseCases.journals("SALES_RETURN").first().map { it.id }.toSet()
+            app.container.accountingUseCases.journals("").first().filter { it.sourceType == "SALES_RETURN" }.map { it.id }.toSet()
         }
@@ -249,7 +251,7 @@
         }
         assertEquals(stockAfterSale + QuantityMicros.SCALE, stockAfterReturn)
         val returnJournal = runBlocking {
-            app.container.accountingUseCases.journals("SALES_RETURN").first().first { it.id !in beforeReturnJournalIds }
+            app.container.accountingUseCases.journals("").first().filter { it.sourceType == "SALES_RETURN" }.first { it.id !in beforeReturnJournalIds }
         }
         assertEquals(returnJournal.totalDebitRial, returnJournal.totalCreditRial)
     }
@@ -267,7 +269,8 @@
         composeRule.waitUntil(timeoutMillis = 10_000) {
             composeRule.onAllNodesWithTag("recipe_product_${fixture.menuItemId}").fetchSemanticsNodes().isNotEmpty()
         }
         composeRule.onNodeWithTag("recipe_product_${fixture.menuItemId}").performClick()
+        composeRule.onNodeWithTag("recipe_editor_list").performScrollToNode(hasTestTag("recipe_create_draft_${originalActive.id}"))
         composeRule.onNodeWithTag("recipe_create_draft_${originalActive.id}").performClick()
 
         val draftId = runBlocking {
@@ -279,6 +282,7 @@
         composeRule.waitUntil(timeoutMillis = 10_000) {
             composeRule.onAllNodesWithTag("recipe_activate_${draftId}").fetchSemanticsNodes().isNotEmpty()
         }
+        composeRule.onNodeWithTag("recipe_editor_list").performScrollToNode(hasTestTag("recipe_activate_${draftId}"))
         composeRule.onNodeWithTag("recipe_activate_${draftId}").performClick()
         composeRule.onNodeWithTag("recipe_activate_confirm").performClick()
@@ -300,7 +304,8 @@
         val beforeStock = inventoryOnHand(fixture.inventoryItemId, fixture.locationId)
 
         openModule(AppScreen.INVENTORY)
-        composeRule.onNodeWithTag("inventory_section_COUNTS").performScrollTo().performClick()
+        composeRule.onNodeWithTag("inventory_overview_list").performScrollToNode(hasTestTag("inventory_section_COUNTS"))
+        composeRule.onNodeWithTag("inventory_section_COUNTS").performClick()
@@ -345,7 +350,8 @@
         val beforeDestination = inventoryOnHand(fixture.inventoryItemId, fixture.destinationLocationId)
 
         openModule(AppScreen.INVENTORY)
-        composeRule.onNodeWithTag("inventory_section_TRANSFERS").performScrollTo().performClick()
+        composeRule.onNodeWithTag("inventory_overview_list").performScrollToNode(hasTestTag("inventory_section_TRANSFERS"))
+        composeRule.onNodeWithTag("inventory_section_TRANSFERS").performClick()
@@ -378,7 +384,7 @@
             app.container.operationsRepository.inventoryItems.first().first { it.id == fixture.inventoryItemId }.stockMicros
         }
         val beforeJournalIds = runBlocking {
-            app.container.accountingUseCases.journals("GOODS_RECEIPT").first().map { it.id }.toSet()
+            app.container.accountingUseCases.journals("").first().filter { it.sourceType == "GOODS_RECEIPT" }.map { it.id }.toSet()
         }
@@ -400,7 +406,7 @@
         assertEquals(beforeStock + fixture.quantityMicros, afterStock)
         val journal = runBlocking {
-            app.container.accountingUseCases.journals("GOODS_RECEIPT").first().first { it.id !in beforeJournalIds }
+            app.container.accountingUseCases.journals("").first().filter { it.sourceType == "GOODS_RECEIPT" }.first { it.id !in beforeJournalIds }
         }
@@ -432,7 +438,7 @@
         val beforeAsset = runBlocking { app.container.assetUseCases.assets.first().first { it.id == assetId } }
         val beforeDepIds = runBlocking { app.container.assetUseCases.depreciations.first().map { it.id }.toSet() }
         val beforeJournalIds = runBlocking {
-            app.container.accountingUseCases.journals("ASSET_DEPRECIATION").first().map { it.id }.toSet()
+            app.container.accountingUseCases.journals("").first().filter { it.sourceType == "ASSET_DEPRECIATION" }.map { it.id }.toSet()
         }
@@ -452,7 +458,7 @@
             app.container.assetUseCases.depreciations.first().first { it.id !in beforeDepIds && it.assetName == assetName }
         }
         val journal = runBlocking {
-            app.container.accountingUseCases.journals("ASSET_DEPRECIATION").first().first { it.id !in beforeJournalIds }
+            app.container.accountingUseCases.journals("").first().filter { it.sourceType == "ASSET_DEPRECIATION" }.first { it.id !in beforeJournalIds }
         }
@@ -595,7 +601,8 @@
                 .first { it.id == table.id }.currentOrderId ?: error("restaurant order was not opened")
         }
 
-        composeRule.onNodeWithTag("restaurant_add_menu_${fixture.menuItemId}").performScrollTo().performClick()
+        composeRule.onNodeWithTag("restaurant_workspace_list").performScrollToNode(hasTestTag("restaurant_add_menu_${fixture.menuItemId}"))
+        composeRule.onNodeWithTag("restaurant_add_menu_${fixture.menuItemId}").performClick()
@@ -748,11 +755,15 @@
     private fun openModule(screen: AppScreen) {
         if (composeRule.onAllNodesWithTag("module_${screen.name}").fetchSemanticsNodes().isEmpty()) {
             composeRule.onNodeWithTag("nav_more").performClick()
             composeRule.waitUntil(timeoutMillis = 10_000) {
+                composeRule.onAllNodesWithTag("more_hub").fetchSemanticsNodes().isNotEmpty()
+            }
+            composeRule.onNodeWithTag("more_hub").performScrollToNode(hasTestTag("module_${screen.name}"))
+            composeRule.waitUntil(timeoutMillis = 10_000) {
                 composeRule.onAllNodesWithTag("module_${screen.name}").fetchSemanticsNodes().isNotEmpty()
             }
         }
-        composeRule.onNodeWithTag("module_${screen.name}").performScrollTo().performClick()
+        composeRule.onNodeWithTag("module_${screen.name}").performClick()
     }
--- a/app/src/androidTest/java/ir/sabou/inventory/data/db/ReplenishmentMigration25To26Test.kt
+++ b/app/src/androidTest/java/ir/sabou/inventory/data/db/ReplenishmentMigration25To26Test.kt
@@ -20,31 +20,81 @@
 
     @Test
     fun addsReplenishmentPoliciesAndKeepsExistingData() {
-        open(25).use { helper ->
+        val helper = open(25)
+        try {
             val db = helper.writableDatabase
-            db.execSQL("CREATE TABLE inventory_items (id INTEGER PRIMARY KEY NOT NULL)")
-            db.execSQL("CREATE TABLE suppliers (id INTEGER PRIMARY KEY NOT NULL)")
-            db.execSQL("CREATE TABLE legacy_marker (id INTEGER PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
-            db.execSQL("INSERT INTO legacy_marker(id, value) VALUES (1, 'محفوظ')")
-
-            db.beginTransaction()
-            try {
-                MIGRATION_25_26.migrate(db)
-                db.version = 26
-                db.setTransactionSuccessful()
-            } finally {
-                db.endTransaction()
-            }
-
-            assertEquals(26, db.version)
-            db.query("SELECT value FROM legacy_marker WHERE id = 1").use { cursor ->
-                assertTrue(cursor.moveToFirst())
-                assertEquals("محفوظ", cursor.getString(0))
-            }
-            db.query("PRAGMA table_info(inventory_replenishment_policies)").use { cursor ->
-                val columns = mutableSetOf<String>()
-                while (cursor.moveToNext()) columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
-                assertTrue(columns.containsAll(setOf("itemId", "preferredSupplierId", "targetCoverDays", "leadTimeDays", "safetyStockMicros", "orderMultipleMicros", "isEnabled")))
-            }
-            db.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
+            createVersion25Fixture(db)
+            migrateTo26(db)
+            assertVersionAndLegacyData(db)
+            assertReplenishmentColumns(db)
+            assertNoForeignKeyViolations(db)
+        } finally {
+            helper.close()
         }
     }
+
+    private fun createVersion25Fixture(db: SupportSQLiteDatabase) {
+        db.execSQL("CREATE TABLE inventory_items (id INTEGER PRIMARY KEY NOT NULL)")
+        db.execSQL("CREATE TABLE suppliers (id INTEGER PRIMARY KEY NOT NULL)")
+        db.execSQL("CREATE TABLE legacy_marker (id INTEGER PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
+        db.execSQL("INSERT INTO legacy_marker(id, value) VALUES (1, 'محفوظ')")
+    }
+
+    private fun migrateTo26(db: SupportSQLiteDatabase) {
+        db.beginTransaction()
+        try {
+            MIGRATION_25_26.migrate(db)
+            db.version = 26
+            db.setTransactionSuccessful()
+        } finally {
+            db.endTransaction()
+        }
+    }
+
+    private fun assertVersionAndLegacyData(db: SupportSQLiteDatabase) {
+        assertEquals(26, db.version)
+        val cursor = db.query("SELECT value FROM legacy_marker WHERE id = 1")
+        try {
+            assertTrue(cursor.moveToFirst())
+            assertEquals("محفوظ", cursor.getString(0))
+        } finally {
+            cursor.close()
+        }
+    }
+
+    private fun assertReplenishmentColumns(db: SupportSQLiteDatabase) {
+        var itemId = false
+        var preferredSupplierId = false
+        var targetCoverDays = false
+        var leadTimeDays = false
+        var safetyStockMicros = false
+        var orderMultipleMicros = false
+        var isEnabled = false
+        val cursor = db.query("PRAGMA table_info(inventory_replenishment_policies)")
+        try {
+            val nameIndex = cursor.getColumnIndexOrThrow("name")
+            while (cursor.moveToNext()) {
+                when (cursor.getString(nameIndex)) {
+                    "itemId" -> itemId = true
+                    "preferredSupplierId" -> preferredSupplierId = true
+                    "targetCoverDays" -> targetCoverDays = true
+                    "leadTimeDays" -> leadTimeDays = true
+                    "safetyStockMicros" -> safetyStockMicros = true
+                    "orderMultipleMicros" -> orderMultipleMicros = true
+                    "isEnabled" -> isEnabled = true
+                }
+            }
+        } finally {
+            cursor.close()
+        }
+        assertTrue(itemId)
+        assertTrue(preferredSupplierId)
+        assertTrue(targetCoverDays)
+        assertTrue(leadTimeDays)
+        assertTrue(safetyStockMicros)
+        assertTrue(orderMultipleMicros)
+        assertTrue(isEnabled)
+    }
+
+    private fun assertNoForeignKeyViolations(db: SupportSQLiteDatabase) {
+        val cursor = db.query("PRAGMA foreign_key_check")
+        try {
+            assertEquals(0, cursor.count)
+        } finally {
+            cursor.close()
+        }
+    }
 
     private fun open(version: Int): SupportSQLiteOpenHelper {
--- a/app/src/main/java/ir/sabou/inventory/ui/RestaurantScreens.kt
+++ b/app/src/main/java/ir/sabou/inventory/ui/RestaurantScreens.kt
@@ -86,7 +86,7 @@
     val selectedOrder = state.openOrders.firstOrNull { it.id == state.selectedOrderId }
 
     LazyColumn(
-        modifier = Modifier.fillMaxSize().padding(16.dp),
+        modifier = Modifier.fillMaxSize().padding(16.dp).testTag("restaurant_workspace_list"),
         verticalArrangement = Arrangement.spacedBy(12.dp),
     ) {
--- a/app/src/main/java/ir/sabou/inventory/ui/InventoryWorkspaceScreen.kt
+++ b/app/src/main/java/ir/sabou/inventory/ui/InventoryWorkspaceScreen.kt
@@ -77,7 +77,7 @@
     val dashboard = state.dashboard
     val actionableReplenishment = state.replenishment.count { it.isActionable }
     LazyColumn(
-        modifier = Modifier.fillMaxSize(),
+        modifier = Modifier.fillMaxSize().testTag("inventory_overview_list"),
         contentPadding = PaddingValues(16.dp),
         verticalArrangement = Arrangement.spacedBy(12.dp),
--- a/app/src/main/java/ir/sabou/inventory/ui/RecipeScreens.kt
+++ b/app/src/main/java/ir/sabou/inventory/ui/RecipeScreens.kt
@@ -344,7 +344,7 @@
         },
         text = {
             LazyColumn(
-                modifier = Modifier.fillMaxWidth().heightIn(max = 570.dp),
+                modifier = Modifier.fillMaxWidth().heightIn(max = 570.dp).testTag("recipe_editor_list"),
                 verticalArrangement = Arrangement.spacedBy(12.dp),
             ) {
--- a/app/src/main/java/ir/sabou/inventory/domain/security/Permission.kt
+++ b/app/src/main/java/ir/sabou/inventory/domain/security/Permission.kt
@@ -138,6 +138,7 @@
             Permission.PAYROLL_CREATE,
             Permission.PAYROLL_CALCULATE,
             Permission.PAYROLL_REVIEW,
+            Permission.PAYROLL_APPROVE,
             Permission.JOURNAL_REVERSE,
             Permission.ACCOUNTING_PERIOD_CLOSE,
             Permission.SALES_DAY_CLOSE,
'''

with tempfile.NamedTemporaryFile("w", encoding="utf-8", delete=False, suffix=".patch") as f:
    f.write(PATCH)
    patch_path = f.name
try:
    subprocess.run(["patch", "--forward", "--batch", "-p1", "-i", patch_path], check=True)
finally:
    Path(patch_path).unlink(missing_ok=True)

checks = {
    "manager_payroll_approve": ("app/src/main/java/ir/sabou/inventory/domain/security/Permission.kt", "Permission.PAYROLL_APPROVE,"),
    "more_hub_scroll": ("app/src/androidTest/java/ir/sabou/inventory/ui/EnterpriseCoreComposeE2ETest.kt", "performScrollToNode(hasTestTag(\"module_${screen.name}\"))"),
    "journal_source_filter": ("app/src/androidTest/java/ir/sabou/inventory/ui/EnterpriseCoreComposeE2ETest.kt", "filter { it.sourceType == \"SALES_INVOICE\" }"),
    "api23_small_helpers": ("app/src/androidTest/java/ir/sabou/inventory/data/db/ReplenishmentMigration25To26Test.kt", "private fun assertReplenishmentColumns"),
}
for name, (path, needle) in checks.items():
    text = Path(path).read_text(encoding="utf-8")
    if needle not in text:
        raise SystemExit(f"FIX12_VERIFY_FAIL:{name}")
print("DASHBOARD_UX2_FIX12_FINAL_DEVICE_ROOTS=PASS api35_lazy_journal_permission=1 api23_art_refactor=1")
