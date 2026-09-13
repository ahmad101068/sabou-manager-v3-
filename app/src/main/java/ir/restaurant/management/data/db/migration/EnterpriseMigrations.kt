package ir.restaurant.management.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Enterprise ledger upgrade edges. */
internal val MIGRATION_40_41 = object : Migration(40, 41) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN purchaseUnit TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN purchaseToStockNumerator INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN purchaseToStockDenominator INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN recipeUnit TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN recipeToStockNumerator INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN recipeToStockDenominator INTEGER NOT NULL DEFAULT 1")
        // Existing installations are 1:1; preserve the previous unit as all three semantic units.
        db.execSQL("UPDATE inventory_items SET purchaseUnit = unit, recipeUnit = unit")
    }
}

internal val MIGRATION_41_42 = object : Migration(41, 42) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE journal_entries ADD COLUMN globalId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE journal_entries ADD COLUMN idempotencyKey TEXT")
        db.execSQL("ALTER TABLE journal_entries ADD COLUMN correlationId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE journal_entries ADD COLUMN reversalOfEntryId INTEGER")
        db.execSQL("ALTER TABLE journal_entries ADD COLUMN postedAtEpochMillis INTEGER")
        db.execSQL("ALTER TABLE journal_entries ADD COLUMN postedByActorId INTEGER")
        db.execSQL(
            """UPDATE journal_entries
            SET globalId = 'legacy:journal_entry:' || id,
                idempotencyKey = 'legacy:journal:' || id,
                correlationId = 'legacy:journal:' || id,
                postedAtEpochMillis = CASE WHEN status = 'POSTED' THEN createdAtEpochMillis ELSE NULL END""",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_journal_entries_globalId ON journal_entries(globalId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_journal_entries_idempotencyKey ON journal_entries(idempotencyKey)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_journal_entries_correlationId ON journal_entries(correlationId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_journal_entries_reversalOfEntryId ON journal_entries(reversalOfEntryId)")

        AccountSeedCallback.seedSystemLocations(db)
        db.execSQL("ALTER TABLE stock_movements ADD COLUMN globalId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE stock_movements ADD COLUMN idempotencyKey TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE stock_movements ADD COLUMN correlationId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE stock_movements ADD COLUMN actorId INTEGER")
        db.execSQL("ALTER TABLE stock_movements ADD COLUMN deviceId TEXT NOT NULL DEFAULT 'legacy-unknown'")
        db.execSQL("ALTER TABLE stock_movements ADD COLUMN locationId INTEGER")
        db.execSQL("ALTER TABLE stock_movements ADD COLUMN unitCostRial INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE stock_movements ADD COLUMN reasonCode TEXT NOT NULL DEFAULT 'LEGACY'")
        db.execSQL("ALTER TABLE stock_movements ADD COLUMN reversalOfMovementId INTEGER")
        db.execSQL(
            """UPDATE stock_movements
            SET globalId = 'legacy:stock_movement:' || id,
                idempotencyKey = 'legacy:stock:' || id,
                correlationId = 'legacy:stock:' || id,
                deviceId = 'legacy-local',
                locationId = (
                    SELECT id FROM storage_locations
                    WHERE isActive = 1
                    ORDER BY CASE WHEN kind = 'PRIMARY' THEN 0 ELSE 1 END, id
                    LIMIT 1
                ),
                reasonCode = 'LEGACY_BACKFILL'""",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_stock_movements_globalId ON stock_movements(globalId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_stock_movements_idempotencyKey ON stock_movements(idempotencyKey)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_correlationId ON stock_movements(correlationId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_actorId ON stock_movements(actorId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_locationId ON stock_movements(locationId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_stock_movements_reversalOfMovementId ON stock_movements(reversalOfMovementId)")

        db.execSQL("ALTER TABLE inventory_counts ADD COLUMN globalId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE inventory_counts ADD COLUMN idempotencyKey TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE inventory_counts ADD COLUMN correlationId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE inventory_counts ADD COLUMN actorId INTEGER")
        db.execSQL("ALTER TABLE inventory_counts ADD COLUMN deviceId TEXT NOT NULL DEFAULT 'legacy-unknown'")
        db.execSQL("ALTER TABLE inventory_counts ADD COLUMN locationId INTEGER")
        db.execSQL(
            """UPDATE inventory_counts
            SET globalId = 'legacy:inventory_count:' || id,
                idempotencyKey = 'legacy:inventory_count:' || id,
                correlationId = 'legacy:inventory_count:' || id,
                deviceId = 'legacy-local',
                locationId = (
                    SELECT id FROM storage_locations
                    WHERE isActive = 1
                    ORDER BY CASE WHEN kind = 'PRIMARY' THEN 0 ELSE 1 END, id
                    LIMIT 1
                )""",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_counts_globalId ON inventory_counts(globalId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_counts_idempotencyKey ON inventory_counts(idempotencyKey)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_counts_correlationId ON inventory_counts(correlationId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_counts_actorId ON inventory_counts(actorId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_counts_locationId ON inventory_counts(locationId)")

        db.execSQL("ALTER TABLE stock_transfers ADD COLUMN globalId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE stock_transfers ADD COLUMN idempotencyKey TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE stock_transfers ADD COLUMN correlationId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE stock_transfers ADD COLUMN actorId INTEGER")
        db.execSQL("ALTER TABLE stock_transfers ADD COLUMN deviceId TEXT NOT NULL DEFAULT 'legacy-unknown'")
        db.execSQL("UPDATE stock_transfers SET globalId = 'legacy:stock_transfer:' || id, idempotencyKey = 'legacy:stock_transfer:' || id, correlationId = 'legacy:stock_transfer:' || id WHERE globalId = ''")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_stock_transfers_globalId ON stock_transfers(globalId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_stock_transfers_idempotencyKey ON stock_transfers(idempotencyKey)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_transfers_actorId ON stock_transfers(actorId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_transfers_correlationId ON stock_transfers(correlationId)")

        db.execSQL("ALTER TABLE audit_logs ADD COLUMN globalId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE audit_logs ADD COLUMN actorId INTEGER")
        db.execSQL("ALTER TABLE audit_logs ADD COLUMN businessEpochDay INTEGER")
        db.execSQL("ALTER TABLE audit_logs ADD COLUMN deviceId TEXT NOT NULL DEFAULT 'legacy-unknown'")
        db.execSQL("ALTER TABLE audit_logs ADD COLUMN referenceType TEXT")
        db.execSQL("ALTER TABLE audit_logs ADD COLUMN referenceId INTEGER")
        db.execSQL("ALTER TABLE audit_logs ADD COLUMN reason TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE audit_logs ADD COLUMN beforeSnapshot TEXT")
        db.execSQL("ALTER TABLE audit_logs ADD COLUMN afterSnapshot TEXT")
        db.execSQL("ALTER TABLE audit_logs ADD COLUMN correlationId TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            """UPDATE audit_logs
            SET globalId = 'legacy:audit_event:' || id,
                deviceId = 'legacy-local',
                reason = 'LEGACY_BACKFILL',
                correlationId = 'legacy:audit:' || id""",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_audit_logs_globalId ON audit_logs(globalId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_logs_actorId ON audit_logs(actorId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_logs_businessEpochDay ON audit_logs(businessEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_logs_referenceType_referenceId ON audit_logs(referenceType,referenceId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_audit_logs_correlationId ON audit_logs(correlationId)")

        db.execSQL("ALTER TABLE performance_goals ADD COLUMN targetValueMicros INTEGER")
        db.execSQL(
            """UPDATE performance_goals
            SET targetValueMicros = CAST(ROUND(targetValue * 1000000) AS INTEGER)
            WHERE targetValue IS NOT NULL""",
        )
        db.execSQL("ALTER TABLE performance_scores ADD COLUMN achievedValueMicros INTEGER")
        db.execSQL(
            """UPDATE performance_scores
            SET achievedValueMicros = CAST(ROUND(achievedValue * 1000000) AS INTEGER)
            WHERE achievedValue IS NOT NULL""",
        )

        db.execSQL("ALTER TABLE purchase_requisitions ADD COLUMN globalId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE purchase_requisitions ADD COLUMN requestedByActorId INTEGER")
        db.execSQL("ALTER TABLE purchase_requisitions ADD COLUMN approvedByActorId INTEGER")
        db.execSQL("ALTER TABLE purchase_requisitions ADD COLUMN firstApprovedByActorId INTEGER")
        db.execSQL("ALTER TABLE purchase_requisitions ADD COLUMN secondApprovedByActorId INTEGER")
        db.execSQL("ALTER TABLE purchase_requisitions ADD COLUMN correlationId TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            """UPDATE purchase_requisitions
            SET globalId = 'legacy:purchase_requisition:' || id,
                correlationId = 'legacy:purchase_requisition:' || id""",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_purchase_requisitions_globalId ON purchase_requisitions(globalId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_requisitions_requestedByActorId ON purchase_requisitions(requestedByActorId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_requisitions_approvedByActorId ON purchase_requisitions(approvedByActorId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_requisitions_correlationId ON purchase_requisitions(correlationId)")

        db.execSQL("ALTER TABLE payroll_runs ADD COLUMN globalId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE payroll_runs ADD COLUMN createdByActorId INTEGER")
        db.execSQL("ALTER TABLE payroll_runs ADD COLUMN approvedByActorId INTEGER")
        db.execSQL("ALTER TABLE payroll_runs ADD COLUMN correlationId TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            """UPDATE payroll_runs
            SET globalId = 'legacy:payroll_run:' || id,
                correlationId = 'legacy:payroll_run:' || id""",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_payroll_runs_globalId ON payroll_runs(globalId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_payroll_runs_status ON payroll_runs(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_payroll_runs_createdByActorId ON payroll_runs(createdByActorId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_payroll_runs_approvedByActorId ON payroll_runs(approvedByActorId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_payroll_runs_correlationId ON payroll_runs(correlationId)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS inventory_waste_documents (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                globalId TEXT NOT NULL DEFAULT '',
                idempotencyKey TEXT NOT NULL,
                correlationId TEXT NOT NULL,
                itemId INTEGER NOT NULL,
                locationId INTEGER NOT NULL,
                quantityMicros INTEGER NOT NULL,
                valueRial INTEGER NOT NULL,
                wasteEpochDay INTEGER NOT NULL,
                reason TEXT NOT NULL,
                notes TEXT NOT NULL,
                actorId INTEGER NOT NULL,
                deviceId TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(itemId) REFERENCES inventory_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(locationId) REFERENCES storage_locations(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )""",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_waste_documents_globalId ON inventory_waste_documents(globalId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_waste_documents_idempotencyKey ON inventory_waste_documents(idempotencyKey)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_waste_documents_itemId ON inventory_waste_documents(itemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_waste_documents_locationId ON inventory_waste_documents(locationId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_waste_documents_wasteEpochDay ON inventory_waste_documents(wasteEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_waste_documents_actorId ON inventory_waste_documents(actorId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_waste_documents_correlationId ON inventory_waste_documents(correlationId)")

        installPostedJournalGuards(db)
        installStockMovementGuards(db)
        installInventoryCountGuards(db)
        installWasteDocumentGuards(db)
        installStockTransferGuards(db)
        installAuditLogGuards(db)
    }
}

/** Inventory 2.0 data-preserving upgrade. Later Inventory phases extend this single edge. */
internal val MIGRATION_42_43 = object : Migration(42, 43) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN sku TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN itemType TEXT NOT NULL DEFAULT 'INGREDIENT'")
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN primaryBarcode TEXT")
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN brand TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN storageCondition TEXT NOT NULL DEFAULT 'AMBIENT'")
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN shelfLifeDays INTEGER")
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN trackLot INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN trackExpiry INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN minimumStockMicros INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN maximumStockMicros INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN safetyStockMicros INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN reorderPointMicros INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE inventory_items ADD COLUMN leadTimeDays INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """UPDATE inventory_items
            SET sku = 'SKU-' || printf('%010d', id),
                itemType = CASE
                    WHEN category LIKE '%بسته%' THEN 'PACKAGING'
                    WHEN category LIKE '%شوینده%' OR category LIKE '%ملزومات%' THEN 'CONSUMABLE'
                    ELSE 'INGREDIENT'
                END,
                minimumStockMicros = alertThresholdMicros,
                reorderPointMicros = alertThresholdMicros""",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_items_sku ON inventory_items(sku)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_items_primaryBarcode ON inventory_items(primaryBarcode)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_items_itemType ON inventory_items(itemType)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_items_category ON inventory_items(category)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_items_isActive ON inventory_items(isActive)")
        db.execSQL(
            """UPDATE inventory_items
            SET trackLot = CASE
                    WHEN stockMicros = COALESCE((SELECT SUM(lot.quantityMicros) FROM inventory_lots lot WHERE lot.itemId=inventory_items.id),0) THEN 1
                    ELSE 0
                END,
                trackExpiry = CASE
                    WHEN stockMicros = COALESCE((SELECT SUM(lot.quantityMicros) FROM inventory_lots lot WHERE lot.itemId=inventory_items.id),0)
                     AND NOT EXISTS(SELECT 1 FROM inventory_lots lot WHERE lot.itemId=inventory_items.id AND lot.quantityMicros > 0 AND lot.expiryEpochDay IS NULL)
                    THEN 1
                    ELSE 0
                END
            WHERE EXISTS(SELECT 1 FROM inventory_lots lot WHERE lot.itemId=inventory_items.id)""",
        )

        db.execSQL("ALTER TABLE inventory_lots ADD COLUMN globalId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE inventory_lots ADD COLUMN supplierLotNumber TEXT")
        db.execSQL("ALTER TABLE inventory_lots ADD COLUMN productionEpochDay INTEGER")
        db.execSQL("ALTER TABLE inventory_lots ADD COLUMN initialQuantityMicros INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE inventory_lots ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'")
        db.execSQL("ALTER TABLE inventory_lots ADD COLUMN sourceReceiptId INTEGER")
        db.execSQL("ALTER TABLE inventory_lots ADD COLUMN correlationId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE inventory_lots ADD COLUMN createdByActorId INTEGER")
        db.execSQL("ALTER TABLE inventory_lots ADD COLUMN createdAtEpochMillis INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """UPDATE inventory_lots
            SET globalId = 'legacy:inventory_lot:' || id,
                initialQuantityMicros = quantityMicros,
                status = CASE WHEN quantityMicros = 0 THEN 'DEPLETED' ELSE 'ACTIVE' END,
                correlationId = 'legacy:inventory_lot:' || id,
                createdAtEpochMillis = updatedAtEpochMillis""",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_lots_globalId ON inventory_lots(globalId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_lots_itemId_locationId_expiryEpochDay ON inventory_lots(itemId,locationId,expiryEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_lots_status_expiryEpochDay ON inventory_lots(status,expiryEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_lots_sourceReceiptId ON inventory_lots(sourceReceiptId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_lots_correlationId ON inventory_lots(correlationId)")

        db.execSQL("ALTER TABLE inventory_lot_consumptions ADD COLUMN unitCostRial INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE inventory_lot_consumptions ADD COLUMN lotStatusSnapshot TEXT NOT NULL DEFAULT 'ACTIVE'")
        db.execSQL(
            """UPDATE inventory_lot_consumptions
            SET unitCostRial = COALESCE((SELECT lot.unitCostRial FROM inventory_lots lot WHERE lot.id=inventory_lot_consumptions.lotId),0),
                lotStatusSnapshot = COALESCE((SELECT lot.status FROM inventory_lots lot WHERE lot.id=inventory_lot_consumptions.lotId),'ACTIVE')""",
        )

        db.execSQL("ALTER TABLE goods_receipt_lines ADD COLUMN lotNumber TEXT")
        db.execSQL("ALTER TABLE goods_receipt_lines ADD COLUMN supplierLotNumber TEXT")
        db.execSQL("ALTER TABLE goods_receipt_lines ADD COLUMN productionEpochDay INTEGER")
        db.execSQL("ALTER TABLE goods_receipt_lines ADD COLUMN expiryEpochDay INTEGER")
        db.execSQL("ALTER TABLE goods_receipt_lines ADD COLUMN lotBarcode TEXT")

        db.execSQL("ALTER TABLE storage_locations ADD COLUMN code TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE storage_locations ADD COLUMN updatedAtEpochMillis INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            """UPDATE storage_locations
            SET code = CASE WHEN kind = 'PRIMARY' THEN 'MAIN' ELSE 'LOC-' || printf('%06d', id) END,
                kind = CASE kind WHEN 'PRIMARY' THEN 'WAREHOUSE' WHEN 'COLD' THEN 'COLD_STORAGE' ELSE kind END,
                updatedAtEpochMillis = createdAtEpochMillis""",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_storage_locations_code ON storage_locations(code)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_storage_locations_kind ON storage_locations(kind)")

        upgradeWasteDocumentsToV43(db)
        upgradeStockTransfersToV43(db)

        db.execSQL("DROP TRIGGER IF EXISTS prevent_closed_inventory_movement_insert")
        db.execSQL(
            """INSERT OR IGNORE INTO stock_movements(
                itemId,movementType,quantityDeltaMicros,valueDeltaRial,referenceType,referenceId,
                movementEpochDay,notes,createdAtEpochMillis,globalId,idempotencyKey,correlationId,
                actorId,deviceId,locationId,unitCostRial,reasonCode,reversalOfMovementId
            )
            SELECT i.id,
                   'OPENING_BALANCE',
                   i.stockMicros - COALESCE((SELECT SUM(sm.quantityDeltaMicros) FROM stock_movements sm WHERE sm.itemId=i.id),0),
                   i.inventoryValueRial - COALESCE((SELECT SUM(sm.valueDeltaRial) FROM stock_movements sm WHERE sm.itemId=i.id),0),
                   'MIGRATION',i.id,
                   CAST(strftime('%s','now') AS INTEGER) / 86400,
                   'Inventory 2.0 ledger reconciliation',
                   CAST(strftime('%s','now') AS INTEGER) * 1000,
                   'migration:42:43:opening:' || i.id,
                   'migration:42:43:opening:' || i.id,
                   'migration:42:43:item:' || i.id,
                   COALESCE((SELECT id FROM app_users WHERE isActive=1 ORDER BY id LIMIT 1),1),
                   'schema-migration-42-43',
                   (SELECT id FROM storage_locations WHERE code='MAIN' AND isActive=1 LIMIT 1),
                   0,
                   'MIGRATION_OPENING_BALANCE',
                   NULL
            FROM inventory_items i
            WHERE i.stockMicros != COALESCE((SELECT SUM(sm.quantityDeltaMicros) FROM stock_movements sm WHERE sm.itemId=i.id),0)
               OR i.inventoryValueRial != COALESCE((SELECT SUM(sm.valueDeltaRial) FROM stock_movements sm WHERE sm.itemId=i.id),0)""",
        )
        AccountSeedCallback.installClosedPeriodGuards(db)

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS inventory_balances(
                itemId INTEGER NOT NULL,
                locationId INTEGER NOT NULL,
                onHandMicros INTEGER NOT NULL,
                reservedMicros INTEGER NOT NULL,
                inTransitMicros INTEGER NOT NULL,
                damagedMicros INTEGER NOT NULL,
                quarantinedMicros INTEGER NOT NULL,
                inventoryValueRial INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL,
                PRIMARY KEY(itemId,locationId),
                FOREIGN KEY(itemId) REFERENCES inventory_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(locationId) REFERENCES storage_locations(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )""",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_balances_locationId ON inventory_balances(locationId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_balances_updatedAtEpochMillis ON inventory_balances(updatedAtEpochMillis)")
        db.execSQL(
            """INSERT OR REPLACE INTO inventory_balances(
                itemId,locationId,onHandMicros,reservedMicros,inTransitMicros,damagedMicros,
                quarantinedMicros,inventoryValueRial,updatedAtEpochMillis
            )
            SELECT itemId,locationId,SUM(quantityDeltaMicros),0,0,0,0,SUM(valueDeltaRial),
                   MAX(createdAtEpochMillis)
            FROM stock_movements
            WHERE locationId IS NOT NULL
            GROUP BY itemId,locationId""",
        )
        db.execSQL(
            """INSERT OR IGNORE INTO inventory_balances(
                itemId,locationId,onHandMicros,reservedMicros,inTransitMicros,damagedMicros,
                quarantinedMicros,inventoryValueRial,updatedAtEpochMillis
            )
            SELECT i.id,location.id,0,0,0,0,0,0,i.updatedAtEpochMillis
            FROM inventory_items i
            INNER JOIN storage_locations location ON location.code='MAIN' AND location.isActive=1""",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_itemId_locationId_movementEpochDay ON stock_movements(itemId,locationId,movementEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_locationId_movementEpochDay ON stock_movements(locationId,movementEpochDay)")

        db.execSQL(
            """CREATE TABLE IF NOT EXISTS inventory_count_sessions(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                globalId TEXT NOT NULL,
                documentNumber TEXT NOT NULL,
                idempotencyKey TEXT NOT NULL,
                postCommandId TEXT,
                locationId INTEGER NOT NULL,
                scope TEXT NOT NULL,
                blindCount INTEGER NOT NULL,
                createdByActorId INTEGER NOT NULL,
                assignedToActorId INTEGER,
                status TEXT NOT NULL,
                snapshotEpochMillis INTEGER NOT NULL,
                businessEpochDay INTEGER NOT NULL,
                startedAtEpochMillis INTEGER,
                submittedAtEpochMillis INTEGER,
                approvedByActorId INTEGER,
                approvedAtEpochMillis INTEGER,
                postedByActorId INTEGER,
                postedAtEpochMillis INTEGER,
                cancelledAtEpochMillis INTEGER,
                notes TEXT NOT NULL,
                correlationId TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(locationId) REFERENCES storage_locations(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(createdByActorId) REFERENCES app_users(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(assignedToActorId) REFERENCES app_users(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(approvedByActorId) REFERENCES app_users(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(postedByActorId) REFERENCES app_users(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )""",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_count_sessions_globalId ON inventory_count_sessions(globalId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_count_sessions_documentNumber ON inventory_count_sessions(documentNumber)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_count_sessions_idempotencyKey ON inventory_count_sessions(idempotencyKey)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_count_sessions_postCommandId ON inventory_count_sessions(postCommandId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_count_sessions_locationId ON inventory_count_sessions(locationId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_count_sessions_status ON inventory_count_sessions(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_count_sessions_businessEpochDay ON inventory_count_sessions(businessEpochDay)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_count_sessions_createdByActorId ON inventory_count_sessions(createdByActorId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_count_sessions_assignedToActorId ON inventory_count_sessions(assignedToActorId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_count_sessions_approvedByActorId ON inventory_count_sessions(approvedByActorId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_count_sessions_postedByActorId ON inventory_count_sessions(postedByActorId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_count_sessions_correlationId ON inventory_count_sessions(correlationId)")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS inventory_count_lines(
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sessionId INTEGER NOT NULL,
                itemId INTEGER NOT NULL,
                lotId INTEGER,
                lotKey INTEGER NOT NULL,
                systemQuantitySnapshotMicros INTEGER NOT NULL,
                systemValueSnapshotRial INTEGER NOT NULL,
                firstCountQuantityMicros INTEGER,
                secondCountQuantityMicros INTEGER,
                finalCountQuantityMicros INTEGER,
                finalCountValueRial INTEGER,
                varianceQuantityMicros INTEGER,
                varianceValueRial INTEGER,
                status TEXT NOT NULL,
                reason TEXT NOT NULL,
                countedByActorId INTEGER,
                countedAtEpochMillis INTEGER,
                updatedAtEpochMillis INTEGER NOT NULL,
                FOREIGN KEY(sessionId) REFERENCES inventory_count_sessions(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(itemId) REFERENCES inventory_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(lotId) REFERENCES inventory_lots(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(countedByActorId) REFERENCES app_users(id) ON UPDATE NO ACTION ON DELETE RESTRICT
            )""",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_inventory_count_lines_sessionId_itemId_lotKey ON inventory_count_lines(sessionId,itemId,lotKey)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_count_lines_sessionId ON inventory_count_lines(sessionId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_count_lines_itemId ON inventory_count_lines(itemId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_count_lines_lotId ON inventory_count_lines(lotId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_count_lines_status ON inventory_count_lines(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_inventory_count_lines_countedByActorId ON inventory_count_lines(countedByActorId)")
        installInventoryBalanceGuards(db)
        installInventoryLotGuards(db)
        installInventoryCountSessionGuards(db)
        installWasteDocumentGuards(db)
        installStockTransferGuards(db)
    }
}

/** Rebuild is required to add the lot FK while preserving every posted v42 waste document. */
private fun upgradeWasteDocumentsToV43(db: SupportSQLiteDatabase) {
    db.execSQL("DROP TRIGGER IF EXISTS trg_inventory_waste_documents_validate_insert")
    db.execSQL("DROP TRIGGER IF EXISTS trg_inventory_waste_documents_no_update")
    db.execSQL("DROP TRIGGER IF EXISTS trg_inventory_waste_documents_no_delete")
    db.execSQL(
        """CREATE TABLE inventory_waste_documents_v43(
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            globalId TEXT NOT NULL DEFAULT '',
            documentNumber TEXT NOT NULL,
            idempotencyKey TEXT NOT NULL,
            postCommandId TEXT,
            correlationId TEXT NOT NULL,
            itemId INTEGER NOT NULL,
            locationId INTEGER NOT NULL,
            lotId INTEGER,
            quantityMicros INTEGER NOT NULL,
            unitCostRial INTEGER,
            valueRial INTEGER NOT NULL,
            stockQuantitySnapshotMicros INTEGER,
            stockValueSnapshotRial INTEGER,
            lotQuantitySnapshotMicros INTEGER,
            wasteEpochDay INTEGER NOT NULL,
            reasonCode TEXT NOT NULL,
            reason TEXT NOT NULL,
            notes TEXT NOT NULL,
            status TEXT NOT NULL,
            actorId INTEGER NOT NULL,
            approvedByActorId INTEGER,
            approvedAtEpochMillis INTEGER,
            postedByActorId INTEGER,
            postedAtEpochMillis INTEGER,
            deviceId TEXT NOT NULL,
            createdAtEpochMillis INTEGER NOT NULL,
            updatedAtEpochMillis INTEGER NOT NULL,
            FOREIGN KEY(itemId) REFERENCES inventory_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(locationId) REFERENCES storage_locations(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(lotId) REFERENCES inventory_lots(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
    )
    db.execSQL(
        """INSERT INTO inventory_waste_documents_v43(
            id,globalId,documentNumber,idempotencyKey,postCommandId,correlationId,itemId,locationId,
            lotId,quantityMicros,unitCostRial,valueRial,stockQuantitySnapshotMicros,
            stockValueSnapshotRial,lotQuantitySnapshotMicros,wasteEpochDay,reasonCode,reason,notes,
            status,actorId,approvedByActorId,approvedAtEpochMillis,postedByActorId,postedAtEpochMillis,
            deviceId,createdAtEpochMillis,updatedAtEpochMillis
        )
        SELECT id,
               CASE WHEN globalId='' THEN 'legacy:inventory_waste:' || id ELSE globalId END,
               'WD-LEGACY-' || printf('%010d',id),
               idempotencyKey,
               CASE WHEN globalId='' THEN 'legacy:inventory_waste:' || id ELSE globalId END,
               correlationId,itemId,locationId,NULL,quantityMicros,NULL,valueRial,NULL,NULL,NULL,
               wasteEpochDay,'LEGACY_UNKNOWN',reason,notes,'POSTED',actorId,NULL,NULL,actorId,
               createdAtEpochMillis,deviceId,createdAtEpochMillis,createdAtEpochMillis
        FROM inventory_waste_documents""",
    )
    db.execSQL("DROP TABLE inventory_waste_documents")
    db.execSQL("ALTER TABLE inventory_waste_documents_v43 RENAME TO inventory_waste_documents")
    db.execSQL("CREATE UNIQUE INDEX index_inventory_waste_documents_globalId ON inventory_waste_documents(globalId)")
    db.execSQL("CREATE UNIQUE INDEX index_inventory_waste_documents_documentNumber ON inventory_waste_documents(documentNumber)")
    db.execSQL("CREATE UNIQUE INDEX index_inventory_waste_documents_idempotencyKey ON inventory_waste_documents(idempotencyKey)")
    db.execSQL("CREATE UNIQUE INDEX index_inventory_waste_documents_postCommandId ON inventory_waste_documents(postCommandId)")
    db.execSQL("CREATE INDEX index_inventory_waste_documents_itemId ON inventory_waste_documents(itemId)")
    db.execSQL("CREATE INDEX index_inventory_waste_documents_locationId ON inventory_waste_documents(locationId)")
    db.execSQL("CREATE INDEX index_inventory_waste_documents_lotId ON inventory_waste_documents(lotId)")
    db.execSQL("CREATE INDEX index_inventory_waste_documents_status ON inventory_waste_documents(status)")
    db.execSQL("CREATE INDEX index_inventory_waste_documents_wasteEpochDay ON inventory_waste_documents(wasteEpochDay)")
    db.execSQL("CREATE INDEX index_inventory_waste_documents_actorId ON inventory_waste_documents(actorId)")
    db.execSQL("CREATE INDEX index_inventory_waste_documents_correlationId ON inventory_waste_documents(correlationId)")
}

/** Legacy transfers were immediate and therefore migrate as completed, balanced documents. */
private fun upgradeStockTransfersToV43(db: SupportSQLiteDatabase) {
    db.execSQL("DROP TRIGGER IF EXISTS trg_stock_transfers_validate_insert")
    db.execSQL("DROP TRIGGER IF EXISTS trg_stock_transfers_no_update")
    db.execSQL("DROP TRIGGER IF EXISTS trg_stock_transfers_no_delete")
    db.execSQL("DROP TRIGGER IF EXISTS trg_stock_transfer_lines_validate_insert")
    db.execSQL("DROP TRIGGER IF EXISTS trg_stock_transfer_lines_no_update")
    db.execSQL("DROP TRIGGER IF EXISTS trg_stock_transfer_lines_no_delete")
    db.execSQL(
        """CREATE TABLE stock_transfers_v43(
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            transferNo TEXT NOT NULL,
            sourceLocationId INTEGER NOT NULL,
            destinationLocationId INTEGER NOT NULL,
            transferEpochDay INTEGER NOT NULL,
            note TEXT NOT NULL,
            globalId TEXT NOT NULL DEFAULT '',
            idempotencyKey TEXT NOT NULL DEFAULT '',
            issueCommandId TEXT,
            receiveCommandId TEXT,
            correlationId TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL,
            requestedByActorId INTEGER NOT NULL,
            actorDisplayNameSnapshot TEXT NOT NULL,
            requestedAtEpochMillis INTEGER NOT NULL,
            approvedByActorId INTEGER,
            approvedAtEpochMillis INTEGER,
            issuedByActorId INTEGER,
            issuedAtEpochMillis INTEGER,
            receivedByActorId INTEGER,
            receivedAtEpochMillis INTEGER,
            deviceId TEXT NOT NULL DEFAULT 'legacy-unknown',
            createdAtEpochMillis INTEGER NOT NULL,
            updatedAtEpochMillis INTEGER NOT NULL,
            FOREIGN KEY(sourceLocationId) REFERENCES storage_locations(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(destinationLocationId) REFERENCES storage_locations(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
    )
    db.execSQL(
        """INSERT INTO stock_transfers_v43(
            id,transferNo,sourceLocationId,destinationLocationId,transferEpochDay,note,globalId,
            idempotencyKey,issueCommandId,receiveCommandId,correlationId,status,
            requestedByActorId,actorDisplayNameSnapshot,requestedAtEpochMillis,approvedByActorId,
            approvedAtEpochMillis,issuedByActorId,issuedAtEpochMillis,receivedByActorId,
            receivedAtEpochMillis,deviceId,createdAtEpochMillis,updatedAtEpochMillis
        )
        SELECT id,transferNo,sourceLocationId,destinationLocationId,transferEpochDay,note,globalId,
               idempotencyKey,globalId || ':issue',globalId || ':receive',correlationId,'COMPLETED',
               COALESCE(actorId,(SELECT id FROM app_users WHERE isActive=1 ORDER BY id LIMIT 1),1),
               transferredBy,createdAtEpochMillis,
               COALESCE(actorId,(SELECT id FROM app_users WHERE isActive=1 ORDER BY id LIMIT 1),1),
               createdAtEpochMillis,
               COALESCE(actorId,(SELECT id FROM app_users WHERE isActive=1 ORDER BY id LIMIT 1),1),
               createdAtEpochMillis,
               COALESCE(actorId,(SELECT id FROM app_users WHERE isActive=1 ORDER BY id LIMIT 1),1),
               createdAtEpochMillis,deviceId,createdAtEpochMillis,createdAtEpochMillis
        FROM stock_transfers""",
    )
    db.execSQL(
        """CREATE TABLE stock_transfer_lines_v43(
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            transferId INTEGER NOT NULL,
            itemId INTEGER NOT NULL,
            lotId INTEGER,
            lotKey INTEGER NOT NULL,
            lotCodeSnapshot TEXT NOT NULL,
            requestedQuantityMicros INTEGER NOT NULL,
            issuedQuantityMicros INTEGER,
            receivedQuantityMicros INTEGER,
            varianceQuantityMicros INTEGER,
            unitCostRial INTEGER,
            valueRial INTEGER,
            updatedAtEpochMillis INTEGER NOT NULL,
            FOREIGN KEY(transferId) REFERENCES stock_transfers(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(itemId) REFERENCES inventory_items(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(lotId) REFERENCES inventory_lots(id) ON UPDATE NO ACTION ON DELETE RESTRICT
        )""",
    )
    db.execSQL(
        """INSERT INTO stock_transfer_lines_v43(
            id,transferId,itemId,lotId,lotKey,lotCodeSnapshot,requestedQuantityMicros,
            issuedQuantityMicros,receivedQuantityMicros,varianceQuantityMicros,unitCostRial,
            valueRial,updatedAtEpochMillis
        )
        SELECT line.id,line.transferId,line.itemId,lot.id,COALESCE(lot.id,0),line.lotCode,
               line.quantityMicros,line.quantityMicros,line.quantityMicros,0,lot.unitCostRial,NULL,
               transfer.createdAtEpochMillis
        FROM stock_transfer_lines line
        INNER JOIN stock_transfers transfer ON transfer.id=line.transferId
        LEFT JOIN inventory_lots lot ON lot.itemId=line.itemId
            AND lot.locationId=transfer.sourceLocationId AND lot.lotCode=line.lotCode""",
    )
    db.execSQL("DROP TABLE stock_transfer_lines")
    db.execSQL("DROP TABLE stock_transfers")
    db.execSQL("ALTER TABLE stock_transfers_v43 RENAME TO stock_transfers")
    db.execSQL("ALTER TABLE stock_transfer_lines_v43 RENAME TO stock_transfer_lines")
    db.execSQL("CREATE UNIQUE INDEX index_stock_transfers_transferNo ON stock_transfers(transferNo)")
    db.execSQL("CREATE UNIQUE INDEX index_stock_transfers_globalId ON stock_transfers(globalId)")
    db.execSQL("CREATE UNIQUE INDEX index_stock_transfers_idempotencyKey ON stock_transfers(idempotencyKey)")
    db.execSQL("CREATE UNIQUE INDEX index_stock_transfers_issueCommandId ON stock_transfers(issueCommandId)")
    db.execSQL("CREATE UNIQUE INDEX index_stock_transfers_receiveCommandId ON stock_transfers(receiveCommandId)")
    db.execSQL("CREATE INDEX index_stock_transfers_sourceLocationId ON stock_transfers(sourceLocationId)")
    db.execSQL("CREATE INDEX index_stock_transfers_destinationLocationId ON stock_transfers(destinationLocationId)")
    db.execSQL("CREATE INDEX index_stock_transfers_transferEpochDay ON stock_transfers(transferEpochDay)")
    db.execSQL("CREATE INDEX index_stock_transfers_status ON stock_transfers(status)")
    db.execSQL("CREATE INDEX index_stock_transfers_requestedByActorId ON stock_transfers(requestedByActorId)")
    db.execSQL("CREATE INDEX index_stock_transfers_approvedByActorId ON stock_transfers(approvedByActorId)")
    db.execSQL("CREATE INDEX index_stock_transfers_issuedByActorId ON stock_transfers(issuedByActorId)")
    db.execSQL("CREATE INDEX index_stock_transfers_receivedByActorId ON stock_transfers(receivedByActorId)")
    db.execSQL("CREATE INDEX index_stock_transfers_correlationId ON stock_transfers(correlationId)")
    db.execSQL("CREATE UNIQUE INDEX index_stock_transfer_lines_transferId_itemId_lotKey ON stock_transfer_lines(transferId,itemId,lotKey)")
    db.execSQL("CREATE INDEX index_stock_transfer_lines_transferId ON stock_transfer_lines(transferId)")
    db.execSQL("CREATE INDEX index_stock_transfer_lines_itemId ON stock_transfer_lines(itemId)")
    db.execSQL("CREATE INDEX index_stock_transfer_lines_lotId ON stock_transfer_lines(lotId)")
}
