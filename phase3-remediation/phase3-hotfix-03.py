#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: phase3-hotfix-03.py <phase3-source-root>')
root = Path(sys.argv[1]).resolve()
if not (root / 'app/src').is_dir():
    raise SystemExit(f'invalid source root: {root}')


def replace_once(rel: str, old: str, new: str) -> None:
    path = root / rel
    text = path.read_text(encoding='utf-8')
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{rel}: expected exactly one fixture pattern, found {count}')
    path.write_text(text.replace(old, new, 1), encoding='utf-8')

# Room 2.8.4 migration-testing serializers are binary-incompatible with kotlinx-serialization 1.8.x.
# Keep the compatibility pin scoped to androidTest; production/runtime dependency resolution is untouched.
replace_once(
    'app/build.gradle.kts',
    '    androidTestImplementation(libs.androidx.sqlite.framework)\n',
    '''    androidTestImplementation(libs.androidx.sqlite.framework)\n    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3") {\n        version { strictly("1.7.3") }\n    }\n    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3") {\n        version { strictly("1.7.3") }\n    }\n''',
)

# Inventory transfer fixtures must use explicit branch-owned locations and explicit user scopes.
replace_once(
    'app/src/androidTest/java/ir/restaurant/management/data/repository/InventoryTransferWorkflowIntegrationTest.kt',
    '''        val sourceLocationId = requireNotNull(database.inventoryLocationDao().defaultLocationId())\n        val destinationLocationId = database.inventoryLocationDao().insert(\n            StorageLocationEntity(\n                code = "KITCHEN-TEST",\n                name = "آشپزخانه آزمون",\n                kind = "KITCHEN",\n                createdAtEpochMillis = now,\n            ),\n        )\n''',
    '''        val branchId = requireNotNull(database.branchDao().listActive().firstOrNull()?.id)\n        val sourceLocationId = database.inventoryLocationDao().insert(\n            StorageLocationEntity(\n                code = "TRANSFER-SOURCE-TEST",\n                name = "انبار مبدأ آزمون انتقال",\n                branchName = "شعبه آزمون انتقال",\n                branchId = branchId,\n                kind = "WAREHOUSE",\n                createdAtEpochMillis = now,\n            ),\n        )\n        val destinationLocationId = database.inventoryLocationDao().insert(\n            StorageLocationEntity(\n                code = "KITCHEN-TEST",\n                name = "آشپزخانه آزمون",\n                branchName = "شعبه آزمون انتقال",\n                branchId = branchId,\n                kind = "KITCHEN",\n                createdAtEpochMillis = now,\n            ),\n        )\n        val scopeDb = database.openHelper.writableDatabase\n        listOf(ownerId, cashierId).forEach { userId ->\n            scopeDb.execSQL(\n                "INSERT OR REPLACE INTO user_scope_profiles(userId, primaryBranchId, updatedAtEpochMillis) VALUES (?, ?, ?)",\n                arrayOf<Any?>(userId, branchId, now),\n            )\n            scopeDb.execSQL(\n                "INSERT OR IGNORE INTO user_branch_scopes(userId, branchId, createdAtEpochMillis) VALUES (?, ?, ?)",\n                arrayOf<Any?>(userId, branchId, now),\n            )\n            listOf(sourceLocationId, destinationLocationId).forEach { locationId ->\n                scopeDb.execSQL(\n                    "INSERT OR IGNORE INTO user_warehouse_scopes(userId, locationId, createdAtEpochMillis) VALUES (?, ?, ?)",\n                    arrayOf<Any?>(userId, locationId, now),\n                )\n            }\n        }\n''',
)

# Inventory count fixtures must exercise permission checks inside an allowed branch/location scope.
replace_once(
    'app/src/androidTest/java/ir/restaurant/management/data/repository/InventoryCountWorkflowIntegrationTest.kt',
    'import ir.restaurant.management.data.db.InventoryItemEntity\n',
    'import ir.restaurant.management.data.db.InventoryItemEntity\nimport ir.restaurant.management.data.db.StorageLocationEntity\n',
)
replace_once(
    'app/src/androidTest/java/ir/restaurant/management/data/repository/InventoryCountWorkflowIntegrationTest.kt',
    '        val locationId = requireNotNull(database.inventoryLocationDao().defaultLocationId())\n',
    '''        val branchId = requireNotNull(database.branchDao().listActive().firstOrNull()?.id)\n        val locationId = database.inventoryLocationDao().insert(\n            StorageLocationEntity(\n                code = "COUNT-WH-TEST",\n                name = "انبار آزمون شمارش",\n                branchName = "شعبه آزمون شمارش",\n                branchId = branchId,\n                kind = "WAREHOUSE",\n                createdAtEpochMillis = now,\n            ),\n        )\n        val scopeDb = database.openHelper.writableDatabase\n        listOf(ownerId, managerId, cashierId).forEach { userId ->\n            scopeDb.execSQL(\n                "INSERT OR REPLACE INTO user_scope_profiles(userId, primaryBranchId, updatedAtEpochMillis) VALUES (?, ?, ?)",\n                arrayOf<Any?>(userId, branchId, now),\n            )\n            scopeDb.execSQL(\n                "INSERT OR IGNORE INTO user_branch_scopes(userId, branchId, createdAtEpochMillis) VALUES (?, ?, ?)",\n                arrayOf<Any?>(userId, branchId, now),\n            )\n            scopeDb.execSQL(\n                "INSERT OR IGNORE INTO user_warehouse_scopes(userId, locationId, createdAtEpochMillis) VALUES (?, ?, ?)",\n                arrayOf<Any?>(userId, locationId, now),\n            )\n        }\n''',
)

# Goods receipt fixtures now carry the same branch/location from requisition through PO to receipt.
replace_once(
    'app/src/androidTest/java/ir/restaurant/management/data/repository/GoodsReceiptTransactionIntegrationTest.kt',
    'import ir.restaurant.management.data.db.SupplierEntity\n',
    'import ir.restaurant.management.data.db.SupplierEntity\nimport ir.restaurant.management.data.db.StorageLocationEntity\n',
)
replace_once(
    'app/src/androidTest/java/ir/restaurant/management/data/repository/GoodsReceiptTransactionIntegrationTest.kt',
    '''    private var orderLineId: Long = 0\n    private var itemId: Long = 0\n''',
    '''    private var orderLineId: Long = 0\n    private var itemId: Long = 0\n    private var branchId: Long = 0\n    private var destinationLocationId: Long = 0\n''',
)
replace_once(
    'app/src/androidTest/java/ir/restaurant/management/data/repository/GoodsReceiptTransactionIntegrationTest.kt',
    '''        LocalSecurityRepository(\n            db = database,\n            clock = { NOW },\n            authorizer = authorizer,\n            sensitiveActionGate = SensitiveActionGate(clockMillis = { NOW }),\n        ).save(null, UserDraft("owner", "مالک", "123456", UserRole.OWNER, "87654321"))\n        repository = LocalProcurementRepository(database, authorizer, clock = { NOW })\n''',
    '''        val ownerId = LocalSecurityRepository(\n            db = database,\n            clock = { NOW },\n            authorizer = authorizer,\n            sensitiveActionGate = SensitiveActionGate(clockMillis = { NOW }),\n        ).save(null, UserDraft("owner", "مالک", "123456", UserRole.OWNER, "87654321"))\n        branchId = requireNotNull(database.branchDao().listActive().firstOrNull()?.id)\n        destinationLocationId = database.inventoryLocationDao().insert(\n            StorageLocationEntity(\n                code = "GR-DEST-TEST",\n                name = "انبار مقصد رسید آزمون",\n                branchName = "شعبه آزمون دریافت",\n                branchId = branchId,\n                kind = "WAREHOUSE",\n                createdAtEpochMillis = NOW,\n            ),\n        )\n        val scopeDb = database.openHelper.writableDatabase\n        scopeDb.execSQL(\n            "INSERT OR REPLACE INTO user_scope_profiles(userId, primaryBranchId, updatedAtEpochMillis) VALUES (?, ?, ?)",\n            arrayOf<Any?>(ownerId, branchId, NOW),\n        )\n        scopeDb.execSQL(\n            "INSERT OR IGNORE INTO user_branch_scopes(userId, branchId, createdAtEpochMillis) VALUES (?, ?, ?)",\n            arrayOf<Any?>(ownerId, branchId, NOW),\n        )\n        scopeDb.execSQL(\n            "INSERT OR IGNORE INTO user_warehouse_scopes(userId, locationId, createdAtEpochMillis) VALUES (?, ?, ?)",\n            arrayOf<Any?>(ownerId, destinationLocationId, NOW),\n        )\n        repository = LocalProcurementRepository(database, authorizer, clock = { NOW })\n''',
)
replace_once(
    'app/src/androidTest/java/ir/restaurant/management/data/repository/GoodsReceiptTransactionIntegrationTest.kt',
    '''                requiredEpochDay = RECEIPT_DAY,\n                status = RequisitionStatus.APPROVED.name,\n''',
    '''                requiredEpochDay = RECEIPT_DAY,\n                branchId = branchId,\n                destinationLocationId = destinationLocationId,\n                status = RequisitionStatus.APPROVED.name,\n''',
)
replace_once(
    'app/src/androidTest/java/ir/restaurant/management/data/repository/GoodsReceiptTransactionIntegrationTest.kt',
    '''                requisitionId = requisitionId,\n                orderEpochDay = RECEIPT_DAY - 1,\n''',
    '''                requisitionId = requisitionId,\n                branchId = branchId,\n                destinationLocationId = destinationLocationId,\n                orderEpochDay = RECEIPT_DAY - 1,\n''',
)
replace_once(
    'app/src/androidTest/java/ir/restaurant/management/data/repository/GoodsReceiptTransactionIntegrationTest.kt',
    '''        deliveryNoteNo = "DN-TEST-1",\n        note = "sealed delivery",\n''',
    '''        deliveryNoteNo = "DN-TEST-1",\n        destinationLocationId = destinationLocationId,\n        note = "sealed delivery",\n''',
)

# Permission integration tests reuse the default branch created by the Phase-3 security bootstrap.
replace_once(
    'app/src/androidTest/java/ir/restaurant/management/data/repository/EnterprisePermissionIntegrationTest.kt',
    '''        database.branchDao().insert(BranchEntity(id = 1L, globalId = "test:permission-branch:1", code = "P1", name = "شعبه یک", createdAtEpochMillis = now, updatedAtEpochMillis = now))\n        database.branchDao().insert(BranchEntity(id = 2L, globalId = "test:permission-branch:2", code = "P2", name = "شعبه دو", createdAtEpochMillis = now, updatedAtEpochMillis = now))\n''',
    '''        val branchOne = requireNotNull(database.branchDao().byId(1L))\n        database.branchDao().update(branchOne.copy(code = "P1", name = "شعبه یک", updatedAtEpochMillis = now))\n        database.branchDao().insert(BranchEntity(id = 2L, globalId = "test:permission-branch:2", code = "P2", name = "شعبه دو", createdAtEpochMillis = now, updatedAtEpochMillis = now))\n        val scopeDb = database.openHelper.writableDatabase\n        listOf(ownerId, cashierId, storekeeperId).forEach { userId ->\n            scopeDb.execSQL(\n                "INSERT OR REPLACE INTO user_scope_profiles(userId, primaryBranchId, updatedAtEpochMillis) VALUES (?, 1, ?)",\n                arrayOf<Any?>(userId, now),\n            )\n            listOf(1L, 2L).forEach { branchId ->\n                scopeDb.execSQL(\n                    "INSERT OR IGNORE INTO user_branch_scopes(userId, branchId, createdAtEpochMillis) VALUES (?, ?, ?)",\n                    arrayOf<Any?>(userId, branchId, now),\n                )\n            }\n        }\n''',
)

# Phase-2 regression fixture also reuses the canonical default branch instead of inserting duplicate PK=1.
replace_once(
    'app/src/androidTest/java/ir/restaurant/management/data/repository/Phase2CorrectionIntegrationTest.kt',
    '''        database.branchDao().insert(\n            BranchEntity(id = 1L, globalId = "test:branch:1", code = "B1", name = "شعبه ۱", createdAtEpochMillis = now, updatedAtEpochMillis = now),\n        )\n''',
    '''        val branchOne = requireNotNull(database.branchDao().byId(1L))\n        database.branchDao().update(\n            branchOne.copy(code = "B1", name = "شعبه ۱", updatedAtEpochMillis = now),\n        )\n''',
)

print('PHASE3_HOTFIX_03=APPLIED')
