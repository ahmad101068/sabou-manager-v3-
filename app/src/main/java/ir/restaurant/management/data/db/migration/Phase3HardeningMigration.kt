package ir.restaurant.management.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Phase 3 — Branch/Warehouse/Inventory/Procurement/Supplier/AP hardening.
 *
 * This migration is append-only from the official v55 Phase-2 handoff. Historical rows are never
 * guessed into a warehouse. New operational writes are fail-closed by application policy plus the
 * database triggers created here.
 */
internal val MIGRATION_55_56 = object : Migration(55, 56) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE branches ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")
        db.execSQL("UPDATE branches SET status=CASE WHEN isActive=1 THEN 'ACTIVE' ELSE 'CLOSED' END")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_branches_status ON branches(status)")

        // Deterministic legacy ownership: only a database with exactly one active branch can safely
        // adopt previously-unassigned storage locations. Multi-branch databases remain fail-closed.
        db.execSQL(
            """
            UPDATE storage_locations
            SET branchId=(SELECT id FROM branches WHERE isActive=1 AND status='ACTIVE' LIMIT 1),
                branchName=(SELECT name FROM branches WHERE isActive=1 AND status='ACTIVE' LIMIT 1)
            WHERE branchId IS NULL
              AND (SELECT COUNT(*) FROM branches WHERE isActive=1 AND status='ACTIVE')=1
            """.trimIndent(),
        )

        // Supplier legal/financial identity. Legacy records receive stable migration-only codes.
        db.execSQL("ALTER TABLE suppliers ADD COLUMN code TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE suppliers ADD COLUMN normalizedName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE suppliers ADD COLUMN partyType TEXT NOT NULL DEFAULT 'COMPANY'")
        db.execSQL("ALTER TABLE suppliers ADD COLUMN legalId TEXT")
        db.execSQL("ALTER TABLE suppliers ADD COLUMN economicCode TEXT")
        db.execSQL("ALTER TABLE suppliers ADD COLUMN bankIban TEXT")
        db.execSQL("UPDATE suppliers SET code='LEGACY-SUP-' || printf('%08d', id) WHERE trim(code)=''")
        db.execSQL("UPDATE suppliers SET normalizedName=lower(trim(name)) WHERE trim(normalizedName)=''")
        db.execSQL("DROP INDEX IF EXISTS index_suppliers_name")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_suppliers_code ON suppliers(code)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_suppliers_name ON suppliers(name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_suppliers_normalizedName ON suppliers(normalizedName)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_suppliers_legalId ON suppliers(legalId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_suppliers_bankIban ON suppliers(bankIban)")

        // Direct/Emergency purchase identity now carries an explicit inventory location.
        db.execSQL("ALTER TABLE purchases ADD COLUMN locationId INTEGER")
        db.execSQL("ALTER TABLE purchases ADD COLUMN commandId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE purchases ADD COLUMN normalizedInvoiceNo TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE purchases SET commandId='legacy:purchase:' || id WHERE trim(commandId)=''")
        db.execSQL("UPDATE purchases SET normalizedInvoiceNo=upper(trim(invoiceNo))")
        for ((from, to) in listOf(
            " " to "", "\t" to "", "\n" to "", "\r" to "",
            "۰" to "0", "۱" to "1", "۲" to "2", "۳" to "3", "۴" to "4",
            "۵" to "5", "۶" to "6", "۷" to "7", "۸" to "8", "۹" to "9",
            "٠" to "0", "١" to "1", "٢" to "2", "٣" to "3", "٤" to "4",
            "٥" to "5", "٦" to "6", "٧" to "7", "٨" to "8", "٩" to "9",
            "ي" to "ی", "ى" to "ی", "ك" to "ک",
        )) {
            db.execSQL(
                "UPDATE purchases SET normalizedInvoiceNo=replace(normalizedInvoiceNo, ?, ?)",
                arrayOf(from, to),
            )
        }
        db.query(
            """
            SELECT supplierId, normalizedInvoiceNo, COUNT(*) AS duplicateCount
            FROM purchases
            GROUP BY supplierId, normalizedInvoiceNo
            HAVING normalizedInvoiceNo='' OR COUNT(*) > 1
            LIMIT 1
            """.trimIndent(),
        ).use { cursor ->
            check(!cursor.moveToFirst()) {
                "Phase3 migration blocked: duplicate/blank normalized supplier invoice identity exists; manual data reconciliation is required."
            }
        }
        db.execSQL("DROP INDEX IF EXISTS index_purchases_invoiceNo")
        db.execSQL("DROP INDEX IF EXISTS index_purchases_supplierId_invoiceNo")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_purchases_supplierId_normalizedInvoiceNo ON purchases(supplierId, normalizedInvoiceNo)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchases_locationId ON purchases(locationId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_purchases_commandId ON purchases(commandId)")

        // Procurement scope is frozen at requisition and propagated to all dependent documents.
        db.execSQL("ALTER TABLE purchase_requisitions ADD COLUMN branchId INTEGER")
        db.execSQL("ALTER TABLE purchase_requisitions ADD COLUMN destinationLocationId INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_requisitions_branchId ON purchase_requisitions(branchId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_requisitions_destinationLocationId ON purchase_requisitions(destinationLocationId)")

        db.execSQL("ALTER TABLE purchase_orders ADD COLUMN branchId INTEGER")
        db.execSQL("ALTER TABLE purchase_orders ADD COLUMN destinationLocationId INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_orders_branchId ON purchase_orders(branchId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_orders_destinationLocationId ON purchase_orders(destinationLocationId)")

        db.execSQL("ALTER TABLE goods_receipts ADD COLUMN branchId INTEGER")
        db.execSQL("ALTER TABLE goods_receipts ADD COLUMN destinationLocationId INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_goods_receipts_branchId ON goods_receipts(branchId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_goods_receipts_destinationLocationId ON goods_receipts(destinationLocationId)")

        db.execSQL("ALTER TABLE procurement_invoice_links ADD COLUMN branchId INTEGER")
        db.execSQL("DROP INDEX IF EXISTS index_procurement_invoice_links_purchaseOrderId")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_procurement_invoice_links_purchaseOrderId ON procurement_invoice_links(purchaseOrderId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_procurement_invoice_links_branchId ON procurement_invoice_links(branchId)")

        db.execSQL("ALTER TABLE purchase_returns ADD COLUMN branchId INTEGER")
        db.execSQL("ALTER TABLE purchase_returns ADD COLUMN locationId INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_returns_branchId ON purchase_returns(branchId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_returns_locationId ON purchase_returns(locationId)")

        db.execSQL("ALTER TABLE purchase_return_lines ADD COLUMN lotId INTEGER")
        db.execSQL("ALTER TABLE purchase_return_lines ADD COLUMN locationId INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_return_lines_lotId ON purchase_return_lines(lotId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_return_lines_locationId ON purchase_return_lines(locationId)")

        db.execSQL("ALTER TABLE daily_sales_summaries ADD COLUMN locationId INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_sales_summaries_locationId ON daily_sales_summaries(locationId)")

        // AP trace for fixed-asset acquisition/maintenance. Legacy rows are intentionally not guessed into a supplier.
        db.execSQL("ALTER TABLE fixed_assets ADD COLUMN acquisitionSource TEXT NOT NULL DEFAULT 'BANK'")
        db.execSQL("ALTER TABLE fixed_assets ADD COLUMN supplierId INTEGER")
        db.execSQL("ALTER TABLE fixed_assets ADD COLUMN payableDueEpochDay INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_fixed_assets_supplierId ON fixed_assets(supplierId)")
        db.execSQL("ALTER TABLE asset_maintenance ADD COLUMN paymentSource TEXT NOT NULL DEFAULT 'BANK'")
        db.execSQL("ALTER TABLE asset_maintenance ADD COLUMN supplierId INTEGER")
        db.execSQL("ALTER TABLE asset_maintenance ADD COLUMN payableDueEpochDay INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_asset_maintenance_supplierId ON asset_maintenance(supplierId)")

        createScopeTables(db)
        createSupplierGovernanceTables(db)
        createPayableTables(db)
        createInvoiceLineMatchTable(db)
        createReceiptLotAllocationTable(db)
        createPhase3IntegrityTriggers(db)
    }

    private fun createScopeTables(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user_scope_profiles(
                userId INTEGER NOT NULL PRIMARY KEY,
                primaryBranchId INTEGER,
                updatedAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(userId) REFERENCES app_users(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(primaryBranchId) REFERENCES branches(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_user_scope_profiles_primaryBranchId ON user_scope_profiles(primaryBranchId)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user_branch_scopes(
                userId INTEGER NOT NULL,
                branchId INTEGER NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                PRIMARY KEY(userId, branchId),
                FOREIGN KEY(userId) REFERENCES app_users(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(branchId) REFERENCES branches(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_user_branch_scopes_branchId ON user_branch_scopes(branchId)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user_warehouse_scopes(
                userId INTEGER NOT NULL,
                locationId INTEGER NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                PRIMARY KEY(userId, locationId),
                FOREIGN KEY(userId) REFERENCES app_users(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(locationId) REFERENCES storage_locations(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_user_warehouse_scopes_locationId ON user_warehouse_scopes(locationId)")

        // Preserve usability without broadening scope: only single-branch databases can be safely inferred.
        db.execSQL(
            """
            INSERT OR IGNORE INTO user_scope_profiles(userId, primaryBranchId, updatedAtEpochMillis)
            SELECT u.id, (SELECT id FROM branches WHERE isActive=1 LIMIT 1), 0
            FROM app_users u
            WHERE u.role <> 'OWNER' AND (SELECT COUNT(*) FROM branches WHERE isActive=1)=1
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO user_branch_scopes(userId, branchId, createdAtEpochMillis)
            SELECT u.id, b.id, 0 FROM app_users u CROSS JOIN branches b
            WHERE u.role <> 'OWNER' AND b.isActive=1 AND (SELECT COUNT(*) FROM branches WHERE isActive=1)=1
            """.trimIndent(),
        )
    }

    private fun createSupplierGovernanceTables(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS supplier_merge_history(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sourceSupplierId INTEGER NOT NULL,
                targetSupplierId INTEGER NOT NULL,
                mergedByActorId INTEGER NOT NULL,
                reason TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(sourceSupplierId) REFERENCES suppliers(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(targetSupplierId) REFERENCES suppliers(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(mergedByActorId) REFERENCES app_users(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_merge_history_sourceSupplierId ON supplier_merge_history(sourceSupplierId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_merge_history_targetSupplierId ON supplier_merge_history(targetSupplierId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_merge_history_mergedByActorId ON supplier_merge_history(mergedByActorId)")
    }

    private fun createPayableTables(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS supplier_payables(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                globalId TEXT NOT NULL,
                supplierId INTEGER NOT NULL,
                branchId INTEGER,
                sourceType TEXT NOT NULL,
                sourceId INTEGER NOT NULL,
                sourceDocumentNo TEXT NOT NULL,
                issueEpochDay INTEGER NOT NULL,
                dueEpochDay INTEGER NOT NULL,
                originalRial INTEGER NOT NULL,
                settledRial INTEGER NOT NULL,
                status TEXT NOT NULL,
                idempotencyKey TEXT NOT NULL,
                correlationId TEXT NOT NULL,
                createdByActorId INTEGER NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(supplierId) REFERENCES suppliers(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(branchId) REFERENCES branches(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_supplier_payables_globalId ON supplier_payables(globalId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_supplier_payables_sourceType_sourceId ON supplier_payables(sourceType, sourceId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_supplier_payables_idempotencyKey ON supplier_payables(idempotencyKey)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_payables_supplierId ON supplier_payables(supplierId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_payables_branchId ON supplier_payables(branchId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_payables_status ON supplier_payables(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_payables_dueEpochDay ON supplier_payables(dueEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_payables_correlationId ON supplier_payables(correlationId)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS supplier_payable_ledger(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                payableId INTEGER NOT NULL,
                supplierId INTEGER NOT NULL,
                branchId INTEGER,
                businessEpochDay INTEGER NOT NULL,
                entryType TEXT NOT NULL,
                amountDeltaRial INTEGER NOT NULL,
                treasuryTransactionId TEXT,
                journalEntryId INTEGER,
                commandId TEXT NOT NULL,
                correlationId TEXT NOT NULL,
                reason TEXT NOT NULL,
                actorId INTEGER NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(payableId) REFERENCES supplier_payables(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(supplierId) REFERENCES suppliers(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(branchId) REFERENCES branches(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_payable_ledger_payableId ON supplier_payable_ledger(payableId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_payable_ledger_supplierId ON supplier_payable_ledger(supplierId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_payable_ledger_branchId ON supplier_payable_ledger(branchId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_payable_ledger_businessEpochDay ON supplier_payable_ledger(businessEpochDay)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_supplier_payable_ledger_commandId ON supplier_payable_ledger(commandId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_payable_ledger_correlationId ON supplier_payable_ledger(correlationId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_payable_ledger_treasuryTransactionId ON supplier_payable_ledger(treasuryTransactionId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_supplier_payable_ledger_journalEntryId ON supplier_payable_ledger(journalEntryId)")

        // Backfill canonical AP only from existing open purchase truth; never fabricate asset/maintenance suppliers.
        db.execSQL(
            """
            INSERT OR IGNORE INTO supplier_payables(
                globalId,supplierId,branchId,sourceType,sourceId,sourceDocumentNo,issueEpochDay,dueEpochDay,
                originalRial,settledRial,status,idempotencyKey,correlationId,createdByActorId,createdAtEpochMillis,updatedAtEpochMillis
            )
            SELECT 'legacy:ap:purchase:' || p.id, p.supplierId, p.branchId, 'PURCHASE', p.id, p.invoiceNo,
                   p.purchaseEpochDay, p.dueEpochDay, p.totalRial, p.paidRial,
                   CASE WHEN p.paidRial<=0 THEN 'OPEN' WHEN p.paidRial<p.totalRial THEN 'PARTIAL' ELSE 'SETTLED' END,
                   'AP:PURCHASE:' || p.id, 'purchase:' || p.id,
                   COALESCE((SELECT currentUserId FROM app_session WHERE singletonId=1), (SELECT id FROM app_users ORDER BY id LIMIT 1), 1),
                   p.createdAtEpochMillis, p.createdAtEpochMillis
            FROM purchases p
            WHERE p.paymentStatus IN ('UNPAID','PARTIAL','PAID')
            """.trimIndent(),
        )
    }

    private fun createInvoiceLineMatchTable(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS procurement_invoice_line_matches(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                invoiceLinkId INTEGER NOT NULL,
                purchaseOrderLineId INTEGER NOT NULL,
                purchaseLineId INTEGER NOT NULL,
                poQtyMicros INTEGER NOT NULL,
                receivedQtyMicros INTEGER NOT NULL,
                invoiceQtyMicros INTEGER NOT NULL,
                poUnitCostRial INTEGER NOT NULL,
                invoiceUnitCostRial INTEGER NOT NULL,
                quantityVarianceMicros INTEGER NOT NULL,
                priceVarianceRial INTEGER NOT NULL,
                FOREIGN KEY(invoiceLinkId) REFERENCES procurement_invoice_links(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(purchaseOrderLineId) REFERENCES purchase_order_lines(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(purchaseLineId) REFERENCES purchase_lines(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_procurement_invoice_line_matches_invoiceLinkId ON procurement_invoice_line_matches(invoiceLinkId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_procurement_invoice_line_matches_purchaseOrderLineId ON procurement_invoice_line_matches(purchaseOrderLineId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_procurement_invoice_line_matches_purchaseLineId ON procurement_invoice_line_matches(purchaseLineId)")
    }

    private fun createReceiptLotAllocationTable(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS procurement_receipt_lot_allocations(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                goodsReceiptId INTEGER NOT NULL,
                purchaseOrderLineId INTEGER NOT NULL,
                lotId INTEGER NOT NULL,
                receivedQuantityMicros INTEGER NOT NULL,
                returnedQuantityMicros INTEGER NOT NULL DEFAULT 0,
                createdAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(goodsReceiptId) REFERENCES goods_receipts(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(purchaseOrderLineId) REFERENCES purchase_order_lines(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(lotId) REFERENCES inventory_lots(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_procurement_receipt_lot_allocations_goodsReceiptId ON procurement_receipt_lot_allocations(goodsReceiptId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_procurement_receipt_lot_allocations_purchaseOrderLineId ON procurement_receipt_lot_allocations(purchaseOrderLineId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_procurement_receipt_lot_allocations_lotId ON procurement_receipt_lot_allocations(lotId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_procurement_receipt_lot_allocations_goodsReceiptId_purchaseOrderLineId_lotId ON procurement_receipt_lot_allocations(goodsReceiptId,purchaseOrderLineId,lotId)")
    }

    private fun createPhase3IntegrityTriggers(db: SupportSQLiteDatabase) {
        val operationalTables = listOf(
            Triple("purchases", "branchId", "locationId"),
            Triple("daily_sales_summaries", "branchId", "locationId"),
            Triple("purchase_requisitions", "branchId", "destinationLocationId"),
            Triple("purchase_orders", "branchId", "destinationLocationId"),
            Triple("goods_receipts", "branchId", "destinationLocationId"),
            Triple("purchase_returns", "branchId", "locationId"),
        )
        operationalTables.forEach { (table, branchColumn, locationColumn) ->
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS phase3_${table}_scope_insert
                BEFORE INSERT ON $table
                BEGIN
                    SELECT CASE WHEN NEW.$branchColumn IS NULL OR NOT EXISTS(SELECT 1 FROM branches b WHERE b.id=NEW.$branchColumn AND b.isActive=1 AND b.status='ACTIVE')
                        THEN RAISE(ABORT, 'phase3_branch_required_or_inactive') END;
                    SELECT CASE WHEN NEW.$locationColumn IS NULL OR NOT EXISTS(
                        SELECT 1 FROM storage_locations l WHERE l.id=NEW.$locationColumn AND l.isActive=1 AND l.branchId=NEW.$branchColumn
                    ) THEN RAISE(ABORT, 'phase3_location_required_or_wrong_branch') END;
                END
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS phase3_${table}_scope_update
                BEFORE UPDATE OF $branchColumn, $locationColumn ON $table
                BEGIN
                    SELECT CASE WHEN NEW.$branchColumn IS NULL OR NOT EXISTS(SELECT 1 FROM branches b WHERE b.id=NEW.$branchColumn AND b.isActive=1 AND b.status='ACTIVE')
                        THEN RAISE(ABORT, 'phase3_branch_required_or_inactive') END;
                    SELECT CASE WHEN NEW.$locationColumn IS NULL OR NOT EXISTS(
                        SELECT 1 FROM storage_locations l WHERE l.id=NEW.$locationColumn AND l.isActive=1 AND l.branchId=NEW.$branchColumn
                    ) THEN RAISE(ABORT, 'phase3_location_required_or_wrong_branch') END;
                END
                """.trimIndent(),
            )
        }

        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS phase3_storage_location_branch_insert
            BEFORE INSERT ON storage_locations WHEN NEW.branchId IS NOT NULL
            BEGIN
                SELECT CASE WHEN NOT EXISTS(SELECT 1 FROM branches b WHERE b.id=NEW.branchId)
                    THEN RAISE(ABORT, 'phase3_location_branch_fk') END;
            END
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS phase3_storage_location_branch_update
            BEFORE UPDATE OF branchId ON storage_locations
            WHEN COALESCE(NEW.branchId,-1) <> COALESCE(OLD.branchId,-1)
            BEGIN
                SELECT CASE WHEN NEW.branchId IS NULL OR NOT EXISTS(SELECT 1 FROM branches b WHERE b.id=NEW.branchId)
                    THEN RAISE(ABORT, 'phase3_location_branch_fk') END;
                SELECT CASE WHEN EXISTS(SELECT 1 FROM stock_movements m WHERE m.locationId=OLD.id)
                    OR EXISTS(SELECT 1 FROM inventory_lots l WHERE l.locationId=OLD.id)
                    OR EXISTS(SELECT 1 FROM inventory_balances ib WHERE ib.locationId=OLD.id AND (ib.onHandMicros<>0 OR ib.reservedMicros<>0 OR ib.damagedMicros<>0 OR ib.quarantinedMicros<>0 OR ib.inTransitMicros<>0))
                    THEN RAISE(ABORT, 'phase3_location_branch_change_requires_transfer_workflow') END;
            END
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TRIGGER IF NOT EXISTS phase3_supplier_new_identity
            BEFORE INSERT ON suppliers
            BEGIN
                SELECT CASE WHEN trim(NEW.code)='' OR trim(NEW.normalizedName)=''
                    THEN RAISE(ABORT, 'phase3_supplier_identity_required') END;
            END
            """.trimIndent(),
        )
    }
}
