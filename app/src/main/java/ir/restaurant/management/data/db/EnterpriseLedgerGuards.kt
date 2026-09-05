package ir.restaurant.management.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

/** Schema-42 append-only and posting invariants, shared by migrations and fresh databases. */
internal fun installAuditLogGuards(db: SupportSQLiteDatabase) {
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_audit_logs_validate_insert
        BEFORE INSERT ON audit_logs
        WHEN NEW.globalId = '' OR NEW.actorId IS NULL OR NEW.actorId <= 0
          OR NEW.deviceId = '' OR NEW.reason = '' OR NEW.correlationId = ''
          OR NEW.integritySequence <= 0 OR length(NEW.eventHash) != 64
          OR (NEW.integritySequence = 1 AND NEW.previousEventHash != '')
          OR (NEW.integritySequence > 1 AND length(NEW.previousEventHash) != 64)
          OR NEW.integritySequence != COALESCE((SELECT integritySequence+1 FROM audit_logs ORDER BY integritySequence DESC LIMIT 1),1)
          OR NEW.previousEventHash != COALESCE((SELECT eventHash FROM audit_logs ORDER BY integritySequence DESC LIMIT 1),'')
          OR ((NEW.referenceType IS NULL) != (NEW.referenceId IS NULL))
        BEGIN SELECT RAISE(ABORT, 'INVALID_AUDIT_EVENT'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_audit_logs_no_update
        BEFORE UPDATE ON audit_logs
        BEGIN SELECT RAISE(ABORT, 'audit log is append-only'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_audit_logs_no_delete
        BEFORE DELETE ON audit_logs
        BEGIN SELECT RAISE(ABORT, 'audit log is append-only'); END""",
    )
}

internal fun installPostedJournalGuards(db: SupportSQLiteDatabase) {
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_journal_entries_draft_only_insert
        BEFORE INSERT ON journal_entries
        WHEN NEW.status != 'DRAFT'
          OR NEW.globalId = ''
          OR NEW.idempotencyKey IS NULL OR NEW.idempotencyKey = ''
          OR NEW.correlationId = ''
          OR NEW.postedAtEpochMillis IS NOT NULL
          OR NEW.postedByActorId IS NOT NULL
        BEGIN SELECT RAISE(ABORT, 'JOURNAL_MUST_START_AS_DRAFT'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_journal_entries_status_transition
        BEFORE UPDATE OF status ON journal_entries
        WHEN OLD.status != NEW.status
          AND NOT (OLD.status = 'DRAFT' AND NEW.status = 'POSTED')
        BEGIN SELECT RAISE(ABORT, 'INVALID_JOURNAL_STATUS_TRANSITION'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_journal_entries_validate_post
        BEFORE UPDATE OF status ON journal_entries
        WHEN OLD.status = 'DRAFT' AND NEW.status = 'POSTED' AND (
          NEW.postedAtEpochMillis IS NULL OR NEW.postedAtEpochMillis <= 0
          OR NEW.postedByActorId IS NULL OR NEW.postedByActorId <= 0
          OR (SELECT COUNT(*) FROM journal_lines WHERE entryId = OLD.id) < 2
          OR (SELECT COALESCE(SUM(debitRial), 0) FROM journal_lines WHERE entryId = OLD.id)
             != (SELECT COALESCE(SUM(creditRial), 0) FROM journal_lines WHERE entryId = OLD.id)
        )
        BEGIN SELECT RAISE(ABORT, 'INVALID_JOURNAL_POSTING'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_posted_journal_entries_no_update
        BEFORE UPDATE ON journal_entries
        WHEN OLD.status = 'POSTED'
        BEGIN SELECT RAISE(ABORT, 'POSTED_JOURNAL_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_posted_journal_entries_no_delete
        BEFORE DELETE ON journal_entries
        WHEN OLD.status = 'POSTED'
        BEGIN SELECT RAISE(ABORT, 'POSTED_JOURNAL_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_posted_journal_lines_no_insert
        BEFORE INSERT ON journal_lines
        WHEN EXISTS(SELECT 1 FROM journal_entries e WHERE e.id = NEW.entryId AND e.status = 'POSTED')
        BEGIN SELECT RAISE(ABORT, 'POSTED_JOURNAL_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_posted_journal_lines_no_update
        BEFORE UPDATE ON journal_lines
        WHEN EXISTS(SELECT 1 FROM journal_entries e WHERE e.id IN (OLD.entryId, NEW.entryId) AND e.status = 'POSTED')
        BEGIN SELECT RAISE(ABORT, 'POSTED_JOURNAL_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_posted_journal_lines_no_delete
        BEFORE DELETE ON journal_lines
        WHEN EXISTS(SELECT 1 FROM journal_entries e WHERE e.id = OLD.entryId AND e.status = 'POSTED')
        BEGIN SELECT RAISE(ABORT, 'POSTED_JOURNAL_IMMUTABLE'); END""",
    )
}

internal fun installStockMovementGuards(db: SupportSQLiteDatabase) {
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_stock_movements_validate_insert
        BEFORE INSERT ON stock_movements
        WHEN NEW.globalId = ''
          OR NEW.idempotencyKey = ''
          OR NEW.correlationId = ''
          OR NEW.actorId IS NULL OR NEW.actorId <= 0
          OR NEW.deviceId = '' OR NEW.locationId IS NULL OR NEW.locationId <= 0
          OR NOT EXISTS(SELECT 1 FROM storage_locations location WHERE location.id = NEW.locationId AND location.isActive = 1)
          OR NEW.reasonCode = '' OR NEW.reasonCode IN ('LEGACY', 'LEGACY_BACKFILL')
          OR (NEW.quantityDeltaMicros = 0 AND NEW.valueDeltaRial = 0)
          OR NEW.unitCostRial < 0
        BEGIN SELECT RAISE(ABORT, 'INVALID_STOCK_MOVEMENT'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_stock_movements_no_update
        BEFORE UPDATE ON stock_movements
        BEGIN SELECT RAISE(ABORT, 'STOCK_MOVEMENT_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_stock_movements_no_delete
        BEFORE DELETE ON stock_movements
        BEGIN SELECT RAISE(ABORT, 'STOCK_MOVEMENT_IMMUTABLE'); END""",
    )
}

internal fun installInventoryBalanceGuards(db: SupportSQLiteDatabase) {
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_inventory_balances_validate_insert
        BEFORE INSERT ON inventory_balances
        WHEN NEW.onHandMicros < 0 OR NEW.inventoryValueRial < 0
          OR NEW.reservedMicros < 0 OR NEW.inTransitMicros < 0
          OR NEW.damagedMicros < 0 OR NEW.quarantinedMicros < 0
          OR NEW.reservedMicros + NEW.damagedMicros + NEW.quarantinedMicros > NEW.onHandMicros
        BEGIN SELECT RAISE(ABORT, 'INVALID_INVENTORY_BALANCE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_inventory_balances_validate_update
        BEFORE UPDATE ON inventory_balances
        WHEN NEW.onHandMicros < 0 OR NEW.inventoryValueRial < 0
          OR NEW.reservedMicros < 0 OR NEW.inTransitMicros < 0
          OR NEW.damagedMicros < 0 OR NEW.quarantinedMicros < 0
          OR NEW.reservedMicros + NEW.damagedMicros + NEW.quarantinedMicros > NEW.onHandMicros
        BEGIN SELECT RAISE(ABORT, 'INVALID_INVENTORY_BALANCE'); END""",
    )
}

internal fun installInventoryLotGuards(db: SupportSQLiteDatabase) {
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_inventory_lots_validate_insert
        BEFORE INSERT ON inventory_lots
        WHEN NEW.globalId = '' OR NEW.correlationId = '' OR NEW.itemId <= 0 OR NEW.locationId <= 0
          OR NEW.lotCode = '' OR NEW.receivedEpochDay <= 0 OR NEW.quantityMicros <= 0
          OR NEW.initialQuantityMicros < NEW.quantityMicros OR NEW.unitCostRial < 0
          OR NEW.status != 'ACTIVE' OR NEW.createdByActorId IS NULL OR NEW.createdByActorId <= 0
          OR NEW.createdAtEpochMillis < 0
          OR (NEW.productionEpochDay IS NOT NULL AND NEW.productionEpochDay > NEW.receivedEpochDay)
          OR (NEW.expiryEpochDay IS NOT NULL AND NEW.expiryEpochDay < COALESCE(NEW.productionEpochDay,NEW.receivedEpochDay))
          OR EXISTS(SELECT 1 FROM inventory_items item WHERE item.id=NEW.itemId AND item.trackExpiry=1 AND NEW.expiryEpochDay IS NULL)
          OR (NEW.sourceReceiptId IS NOT NULL AND NOT EXISTS(SELECT 1 FROM goods_receipts receipt WHERE receipt.id=NEW.sourceReceiptId))
        BEGIN SELECT RAISE(ABORT, 'INVALID_INVENTORY_LOT'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_inventory_lots_validate_update
        BEFORE UPDATE ON inventory_lots
        WHEN NEW.globalId != OLD.globalId OR NEW.itemId != OLD.itemId OR NEW.locationId != OLD.locationId
          OR NEW.lotCode != OLD.lotCode OR NEW.receivedEpochDay != OLD.receivedEpochDay
          OR NEW.productionEpochDay IS NOT OLD.productionEpochDay OR NEW.expiryEpochDay IS NOT OLD.expiryEpochDay
          OR NEW.unitCostRial != OLD.unitCostRial OR NEW.barcode IS NOT OLD.barcode
          OR NEW.sourceReceiptId IS NOT OLD.sourceReceiptId OR NEW.correlationId != OLD.correlationId
          OR NEW.createdByActorId IS NOT OLD.createdByActorId OR NEW.createdAtEpochMillis != OLD.createdAtEpochMillis
          OR NEW.quantityMicros < 0 OR NEW.initialQuantityMicros < NEW.quantityMicros
          OR NEW.status NOT IN ('ACTIVE','QUARANTINED','EXPIRED','DEPLETED','BLOCKED')
          OR (NEW.quantityMicros = 0 AND NEW.status != 'DEPLETED')
          OR (NEW.quantityMicros > 0 AND NEW.status = 'DEPLETED')
        BEGIN SELECT RAISE(ABORT, 'INVALID_INVENTORY_LOT_UPDATE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_inventory_lots_no_delete
        BEFORE DELETE ON inventory_lots
        BEGIN SELECT RAISE(ABORT, 'INVENTORY_LOT_HISTORY_REQUIRED'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_inventory_lot_consumptions_validate_insert
        BEFORE INSERT ON inventory_lot_consumptions
        WHEN NEW.quantityMicros <= 0 OR NEW.reversedQuantityMicros != 0 OR NEW.unitCostRial < 0
          OR NEW.lotStatusSnapshot NOT IN ('ACTIVE','QUARANTINED','EXPIRED','BLOCKED')
        BEGIN SELECT RAISE(ABORT, 'INVALID_INVENTORY_LOT_ALLOCATION'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_inventory_lot_consumptions_validate_update
        BEFORE UPDATE ON inventory_lot_consumptions
        WHEN NEW.stockMovementId != OLD.stockMovementId OR NEW.lotId != OLD.lotId
          OR NEW.quantityMicros != OLD.quantityMicros OR NEW.unitCostRial != OLD.unitCostRial
          OR NEW.lotStatusSnapshot != OLD.lotStatusSnapshot
          OR NEW.reversedQuantityMicros <= OLD.reversedQuantityMicros
          OR NEW.reversedQuantityMicros > NEW.quantityMicros
          OR OLD.reversedQuantityMicros >= OLD.quantityMicros
        BEGIN SELECT RAISE(ABORT, 'INVALID_INVENTORY_LOT_ALLOCATION_REVERSAL'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_inventory_lot_consumptions_no_delete
        BEFORE DELETE ON inventory_lot_consumptions
        BEGIN SELECT RAISE(ABORT, 'INVENTORY_LOT_ALLOCATION_IMMUTABLE'); END""",
    )
}

internal fun installInventoryCountSessionGuards(db: SupportSQLiteDatabase) {
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_inventory_count_sessions_validate_insert
        BEFORE INSERT ON inventory_count_sessions
        WHEN NEW.globalId='' OR NEW.documentNumber='' OR NEW.idempotencyKey='' OR NEW.correlationId=''
          OR NEW.locationId<=0 OR NEW.createdByActorId<=0 OR NEW.businessEpochDay<=0
          OR NEW.scope NOT IN ('ALL_LOCATION','ITEM_SELECTION') OR NEW.status!='DRAFT'
          OR NEW.snapshotEpochMillis<0 OR NEW.createdAtEpochMillis<0 OR NEW.updatedAtEpochMillis<0
        BEGIN SELECT RAISE(ABORT,'INVALID_INVENTORY_COUNT_SESSION'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_inventory_count_sessions_validate_update
        BEFORE UPDATE ON inventory_count_sessions
        WHEN NEW.globalId!=OLD.globalId OR NEW.documentNumber!=OLD.documentNumber
          OR NEW.idempotencyKey!=OLD.idempotencyKey OR NEW.locationId!=OLD.locationId
          OR NEW.scope!=OLD.scope OR NEW.blindCount!=OLD.blindCount
          OR NEW.createdByActorId!=OLD.createdByActorId OR NEW.assignedToActorId IS NOT OLD.assignedToActorId
          OR NEW.snapshotEpochMillis!=OLD.snapshotEpochMillis OR NEW.businessEpochDay!=OLD.businessEpochDay
          OR NEW.notes!=OLD.notes OR NEW.correlationId!=OLD.correlationId
          OR NEW.createdAtEpochMillis!=OLD.createdAtEpochMillis
          OR NOT (
              (OLD.status='DRAFT' AND NEW.status IN ('OPEN','CANCELLED')) OR
              (OLD.status='OPEN' AND NEW.status IN ('COUNTING','CANCELLED')) OR
              (OLD.status='COUNTING' AND NEW.status IN ('COUNTING','RECOUNT_REQUIRED','PENDING_APPROVAL','CANCELLED')) OR
              (OLD.status='RECOUNT_REQUIRED' AND NEW.status IN ('COUNTING','PENDING_APPROVAL','CANCELLED')) OR
              (OLD.status='PENDING_APPROVAL' AND NEW.status IN ('APPROVED','RECOUNT_REQUIRED','CANCELLED')) OR
              (OLD.status='APPROVED' AND NEW.status='POSTED')
          )
          OR (NEW.status='APPROVED' AND (NEW.approvedByActorId IS NULL OR NEW.approvedAtEpochMillis IS NULL))
          OR (NEW.status='POSTED' AND (NEW.postCommandId IS NULL OR NEW.postedByActorId IS NULL OR NEW.postedAtEpochMillis IS NULL))
          OR (NEW.status='CANCELLED' AND NEW.cancelledAtEpochMillis IS NULL)
        BEGIN SELECT RAISE(ABORT,'INVALID_INVENTORY_COUNT_TRANSITION'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_inventory_count_sessions_no_delete
        BEFORE DELETE ON inventory_count_sessions
        BEGIN SELECT RAISE(ABORT,'INVENTORY_COUNT_HISTORY_REQUIRED'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_inventory_count_lines_validate_insert
        BEFORE INSERT ON inventory_count_lines
        WHEN NEW.sessionId<=0 OR NEW.itemId<=0 OR NEW.lotKey!=COALESCE(NEW.lotId,0)
          OR NEW.systemQuantitySnapshotMicros<0 OR NEW.systemValueSnapshotRial<0
          OR NEW.status!='PENDING' OR NEW.firstCountQuantityMicros IS NOT NULL
          OR NEW.secondCountQuantityMicros IS NOT NULL OR NEW.finalCountQuantityMicros IS NOT NULL
          OR (NEW.lotId IS NOT NULL AND NOT EXISTS(
              SELECT 1 FROM inventory_lots lot
              INNER JOIN inventory_count_sessions session ON session.id=NEW.sessionId
              WHERE lot.id=NEW.lotId AND lot.itemId=NEW.itemId AND lot.locationId=session.locationId
          ))
        BEGIN SELECT RAISE(ABORT,'INVALID_INVENTORY_COUNT_LINE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_inventory_count_lines_validate_update
        BEFORE UPDATE ON inventory_count_lines
        WHEN NEW.sessionId!=OLD.sessionId OR NEW.itemId!=OLD.itemId OR NEW.lotId IS NOT OLD.lotId
          OR NEW.lotKey!=OLD.lotKey OR NEW.systemQuantitySnapshotMicros!=OLD.systemQuantitySnapshotMicros
          OR NEW.systemValueSnapshotRial!=OLD.systemValueSnapshotRial
          OR NOT (
              (OLD.status='PENDING' AND NEW.status IN ('FINALIZED','RECOUNT_REQUIRED')) OR
              (OLD.status='RECOUNT_REQUIRED' AND NEW.status='FINALIZED')
          )
          OR NEW.firstCountQuantityMicros IS NULL OR NEW.countedByActorId IS NULL
          OR (NEW.status='RECOUNT_REQUIRED' AND NEW.finalCountQuantityMicros IS NOT NULL)
          OR (NEW.status='FINALIZED' AND (
              NEW.finalCountQuantityMicros IS NULL OR NEW.finalCountValueRial IS NULL
              OR NEW.varianceQuantityMicros IS NULL OR NEW.varianceValueRial IS NULL
          ))
        BEGIN SELECT RAISE(ABORT,'INVALID_INVENTORY_COUNT_LINE_TRANSITION'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_inventory_count_lines_no_delete
        BEFORE DELETE ON inventory_count_lines
        BEGIN SELECT RAISE(ABORT,'INVENTORY_COUNT_LINE_HISTORY_REQUIRED'); END""",
    )
}

internal fun installInventoryCountGuards(db: SupportSQLiteDatabase) {
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_inventory_counts_validate_insert
        BEFORE INSERT ON inventory_counts
        WHEN NEW.globalId = '' OR NEW.idempotencyKey = '' OR NEW.correlationId = ''
          OR NEW.actorId IS NULL OR NEW.actorId <= 0 OR NEW.deviceId = ''
          OR NEW.locationId IS NULL OR NEW.locationId <= 0 OR NEW.reason = ''
          OR NEW.previousQuantityMicros < 0 OR NEW.countedQuantityMicros < 0
          OR NEW.previousValueRial < 0 OR NEW.countedValueRial < 0
          OR NOT EXISTS(SELECT 1 FROM storage_locations location WHERE location.id = NEW.locationId AND location.isActive = 1)
        BEGIN SELECT RAISE(ABORT, 'INVALID_INVENTORY_COUNT'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_inventory_counts_no_update
        BEFORE UPDATE ON inventory_counts
        BEGIN SELECT RAISE(ABORT, 'INVENTORY_COUNT_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_inventory_counts_no_delete
        BEFORE DELETE ON inventory_counts
        BEGIN SELECT RAISE(ABORT, 'INVENTORY_COUNT_IMMUTABLE'); END""",
    )
}

internal fun installWasteDocumentGuards(db: SupportSQLiteDatabase) {
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_inventory_waste_documents_validate_insert
        BEFORE INSERT ON inventory_waste_documents
        WHEN NEW.globalId = '' OR NEW.documentNumber = '' OR NEW.idempotencyKey = ''
          OR NEW.postCommandId IS NOT NULL OR NEW.correlationId = ''
          OR NEW.quantityMicros <= 0 OR NEW.unitCostRial IS NULL OR NEW.unitCostRial < 0
          OR NEW.valueRial < 0 OR NEW.actorId <= 0 OR NEW.wasteEpochDay <= 0
          OR NEW.deviceId = '' OR NEW.reasonCode NOT IN (
              'SPOILAGE','EXPIRED','PREPARATION_WASTE','OVERPRODUCTION','QUALITY_REJECT',
              'DAMAGE','STAFF_MEAL','COMPLIMENTARY','OTHER'
          )
          OR NEW.status NOT IN ('PENDING_APPROVAL','APPROVED')
          OR NEW.stockQuantitySnapshotMicros IS NULL OR NEW.stockValueSnapshotRial IS NULL
          OR NEW.stockQuantitySnapshotMicros < NEW.quantityMicros
          OR NEW.stockValueSnapshotRial < NEW.valueRial
          OR NEW.lotQuantitySnapshotMicros IS NOT NULL AND NEW.lotId IS NULL
          OR NEW.lotId IS NOT NULL AND NEW.lotQuantitySnapshotMicros IS NULL
          OR NEW.lotId IS NOT NULL AND NEW.lotQuantitySnapshotMicros < NEW.quantityMicros
          OR NEW.reasonCode = 'EXPIRED' AND NEW.lotId IS NULL
          OR NOT EXISTS(SELECT 1 FROM storage_locations location WHERE location.id = NEW.locationId AND location.isActive = 1)
          OR NEW.lotId IS NOT NULL AND NOT EXISTS(
              SELECT 1 FROM inventory_lots lot
              WHERE lot.id=NEW.lotId AND lot.itemId=NEW.itemId AND lot.locationId=NEW.locationId
          )
        BEGIN SELECT RAISE(ABORT, 'INVALID_WASTE_DOCUMENT'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_inventory_waste_documents_no_update
        BEFORE UPDATE ON inventory_waste_documents
        WHEN NEW.globalId!=OLD.globalId OR NEW.documentNumber!=OLD.documentNumber
          OR NEW.idempotencyKey!=OLD.idempotencyKey OR NEW.correlationId!=OLD.correlationId
          OR NEW.itemId!=OLD.itemId OR NEW.locationId!=OLD.locationId OR NEW.lotId IS NOT OLD.lotId
          OR NEW.quantityMicros!=OLD.quantityMicros OR NEW.unitCostRial IS NOT OLD.unitCostRial
          OR NEW.valueRial!=OLD.valueRial
          OR NEW.stockQuantitySnapshotMicros IS NOT OLD.stockQuantitySnapshotMicros
          OR NEW.stockValueSnapshotRial IS NOT OLD.stockValueSnapshotRial
          OR NEW.lotQuantitySnapshotMicros IS NOT OLD.lotQuantitySnapshotMicros
          OR NEW.wasteEpochDay!=OLD.wasteEpochDay OR NEW.reasonCode!=OLD.reasonCode
          OR NEW.reason!=OLD.reason OR NEW.notes!=OLD.notes OR NEW.actorId!=OLD.actorId
          OR NEW.deviceId!=OLD.deviceId OR NEW.createdAtEpochMillis!=OLD.createdAtEpochMillis
          OR NOT (
              (OLD.status='PENDING_APPROVAL' AND NEW.status='APPROVED') OR
              (OLD.status='APPROVED' AND NEW.status='POSTED')
          )
          OR (OLD.status='PENDING_APPROVAL' AND (
              NEW.approvedByActorId IS NULL OR NEW.approvedAtEpochMillis IS NULL
              OR NEW.postCommandId IS NOT NULL OR NEW.postedByActorId IS NOT NULL
          ))
          OR (OLD.status='APPROVED' AND (
              NEW.approvedByActorId IS NOT OLD.approvedByActorId
              OR NEW.approvedAtEpochMillis IS NOT OLD.approvedAtEpochMillis
              OR NEW.postCommandId IS NULL OR NEW.postedByActorId IS NULL
              OR NEW.postedAtEpochMillis IS NULL
          ))
        BEGIN SELECT RAISE(ABORT, 'WASTE_DOCUMENT_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_inventory_waste_documents_no_delete
        BEFORE DELETE ON inventory_waste_documents
        BEGIN SELECT RAISE(ABORT, 'WASTE_DOCUMENT_IMMUTABLE'); END""",
    )
}

internal fun installStockTransferGuards(db: SupportSQLiteDatabase) {
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_stock_transfers_validate_insert
        BEFORE INSERT ON stock_transfers
        WHEN NEW.globalId = '' OR NEW.transferNo = '' OR NEW.idempotencyKey = ''
          OR NEW.issueCommandId IS NOT NULL OR NEW.receiveCommandId IS NOT NULL
          OR NEW.correlationId = '' OR NEW.requestedByActorId <= 0
          OR NEW.actorDisplayNameSnapshot = '' OR NEW.deviceId = ''
          OR NEW.sourceLocationId = NEW.destinationLocationId OR NEW.transferEpochDay <= 0
          OR NEW.status != 'REQUESTED' OR NEW.requestedAtEpochMillis < 0
          OR NOT EXISTS(SELECT 1 FROM storage_locations location WHERE location.id=NEW.sourceLocationId AND location.isActive=1)
          OR NOT EXISTS(SELECT 1 FROM storage_locations location WHERE location.id=NEW.destinationLocationId AND location.isActive=1)
        BEGIN SELECT RAISE(ABORT, 'INVALID_STOCK_TRANSFER'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_stock_transfers_no_update
        BEFORE UPDATE ON stock_transfers
        WHEN NEW.transferNo!=OLD.transferNo OR NEW.sourceLocationId!=OLD.sourceLocationId
          OR NEW.destinationLocationId!=OLD.destinationLocationId OR NEW.transferEpochDay!=OLD.transferEpochDay
          OR NEW.note!=OLD.note OR NEW.globalId!=OLD.globalId OR NEW.idempotencyKey!=OLD.idempotencyKey
          OR NEW.correlationId!=OLD.correlationId OR NEW.requestedByActorId!=OLD.requestedByActorId
          OR NEW.actorDisplayNameSnapshot!=OLD.actorDisplayNameSnapshot
          OR NEW.requestedAtEpochMillis!=OLD.requestedAtEpochMillis OR NEW.deviceId!=OLD.deviceId
          OR NEW.createdAtEpochMillis!=OLD.createdAtEpochMillis
          OR NOT (
              (OLD.status='REQUESTED' AND NEW.status='APPROVED') OR
              (OLD.status='APPROVED' AND NEW.status='IN_TRANSIT') OR
              (OLD.status='IN_TRANSIT' AND NEW.status='COMPLETED')
          )
          OR (OLD.status='REQUESTED' AND (
              NEW.approvedByActorId IS NULL OR NEW.approvedAtEpochMillis IS NULL
              OR NEW.issueCommandId IS NOT NULL OR NEW.issuedByActorId IS NOT NULL
              OR NOT EXISTS(SELECT 1 FROM stock_transfer_lines line WHERE line.transferId=OLD.id)
          ))
          OR (OLD.status='APPROVED' AND (
              NEW.approvedByActorId IS NOT OLD.approvedByActorId
              OR NEW.approvedAtEpochMillis IS NOT OLD.approvedAtEpochMillis
              OR NEW.issueCommandId IS NULL OR NEW.issuedByActorId IS NULL
              OR NEW.issuedAtEpochMillis IS NULL OR NEW.receiveCommandId IS NOT NULL
              OR EXISTS(SELECT 1 FROM stock_transfer_lines line
                  WHERE line.transferId=OLD.id AND line.issuedQuantityMicros IS NULL)
          ))
          OR (OLD.status='IN_TRANSIT' AND (
              NEW.issueCommandId IS NOT OLD.issueCommandId
              OR NEW.issuedByActorId IS NOT OLD.issuedByActorId
              OR NEW.issuedAtEpochMillis IS NOT OLD.issuedAtEpochMillis
              OR NEW.receiveCommandId IS NULL OR NEW.receivedByActorId IS NULL
              OR NEW.receivedAtEpochMillis IS NULL
              OR EXISTS(SELECT 1 FROM stock_transfer_lines line
                  WHERE line.transferId=OLD.id
                    AND (line.receivedQuantityMicros IS NULL OR line.varianceQuantityMicros!=0))
          ))
        BEGIN SELECT RAISE(ABORT, 'STOCK_TRANSFER_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_stock_transfers_no_delete
        BEFORE DELETE ON stock_transfers
        BEGIN SELECT RAISE(ABORT, 'STOCK_TRANSFER_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_stock_transfer_lines_validate_insert
        BEFORE INSERT ON stock_transfer_lines
        WHEN NEW.requestedQuantityMicros <= 0 OR NEW.lotKey != COALESCE(NEW.lotId,0)
          OR NEW.issuedQuantityMicros IS NOT NULL OR NEW.receivedQuantityMicros IS NOT NULL
          OR NEW.varianceQuantityMicros IS NOT NULL OR NEW.unitCostRial IS NOT NULL OR NEW.valueRial IS NOT NULL
          OR NOT EXISTS(SELECT 1 FROM stock_transfers transfer WHERE transfer.id=NEW.transferId AND transfer.status='REQUESTED')
          OR NEW.lotId IS NOT NULL AND NOT EXISTS(
              SELECT 1 FROM inventory_lots lot
              INNER JOIN stock_transfers transfer ON transfer.id=NEW.transferId
              WHERE lot.id=NEW.lotId AND lot.itemId=NEW.itemId AND lot.locationId=transfer.sourceLocationId
          )
        BEGIN SELECT RAISE(ABORT, 'INVALID_STOCK_TRANSFER_LINE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_stock_transfer_lines_no_update
        BEFORE UPDATE ON stock_transfer_lines
        WHEN NEW.transferId!=OLD.transferId OR NEW.itemId!=OLD.itemId OR NEW.lotId IS NOT OLD.lotId
          OR NEW.lotKey!=OLD.lotKey OR NEW.lotCodeSnapshot!=OLD.lotCodeSnapshot
          OR NEW.requestedQuantityMicros!=OLD.requestedQuantityMicros
          OR NOT (
              (OLD.issuedQuantityMicros IS NULL AND NEW.issuedQuantityMicros=OLD.requestedQuantityMicros
                  AND NEW.receivedQuantityMicros IS NULL AND NEW.varianceQuantityMicros IS NULL
                  AND NEW.unitCostRial IS NOT NULL AND NEW.unitCostRial>=0
                  AND NEW.valueRial IS NOT NULL AND NEW.valueRial>=0
                  AND EXISTS(SELECT 1 FROM stock_transfers transfer
                      WHERE transfer.id=OLD.transferId AND transfer.status='APPROVED')) OR
              (OLD.issuedQuantityMicros IS NOT NULL AND OLD.receivedQuantityMicros IS NULL
                  AND NEW.issuedQuantityMicros=OLD.issuedQuantityMicros
                  AND NEW.unitCostRial IS OLD.unitCostRial AND NEW.valueRial IS OLD.valueRial
                  AND NEW.receivedQuantityMicros IS NOT NULL AND NEW.receivedQuantityMicros>=0
                  AND NEW.varianceQuantityMicros=NEW.receivedQuantityMicros-NEW.issuedQuantityMicros
                  AND EXISTS(SELECT 1 FROM stock_transfers transfer
                      WHERE transfer.id=OLD.transferId AND transfer.status='IN_TRANSIT'))
          )
        BEGIN SELECT RAISE(ABORT, 'STOCK_TRANSFER_IMMUTABLE'); END""",
    )
    db.execSQL(
        """CREATE TRIGGER IF NOT EXISTS trg_stock_transfer_lines_no_delete
        BEFORE DELETE ON stock_transfer_lines
        BEGIN SELECT RAISE(ABORT, 'STOCK_TRANSFER_IMMUTABLE'); END""",
    )
}
