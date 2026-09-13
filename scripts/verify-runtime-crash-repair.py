#!/usr/bin/env python3
"""Host-side contract checks for TASK-03-A; supplements, never replaces, Android tests."""

from __future__ import annotations

import re
import sqlite3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def source(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def section(text: str, start: str, end: str) -> str:
    start_index = text.index(start)
    return text[start_index : text.index(end, start_index)]


def expect_guard_failure(connection: sqlite3.Connection, sql: str, message: str) -> None:
    try:
        connection.execute(sql)
    except sqlite3.IntegrityError as error:
        if message not in str(error):
            raise AssertionError(f"unexpected guard failure for {sql!r}: {error}") from error
    else:
        raise AssertionError(f"guard did not reject {sql!r}")


def sales_guard_sql() -> list[str]:
    lifecycle = source(
        "app/src/main/java/ir/restaurant/management/data/db/migration/DatabaseIntegrityLifecycle.kt",
    )
    body = section(
        lifecycle,
        "internal fun installSalesDayGuards(",
        "internal fun installAccountingPeriodGuards(",
    )
    triggers = re.findall(r'db\.execSQL\("""(.*?)"""\)', body, flags=re.DOTALL)
    if len(triggers) != 9:
        raise AssertionError(f"expected 9 sales-day guards, found {len(triggers)}")
    return triggers


def new_sales_database(has_status: bool) -> sqlite3.Connection:
    connection = sqlite3.connect(":memory:")
    status = ", status TEXT NOT NULL DEFAULT 'CLOSED'" if has_status else ""
    connection.executescript(
        f"""
        PRAGMA foreign_keys=ON;
        CREATE TABLE daily_sales_summaries(
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            businessEpochDay INTEGER NOT NULL
        );
        CREATE TABLE daily_sales_menu_lines(
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            summaryId INTEGER NOT NULL
        );
        CREATE TABLE stock_movements(
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            referenceType TEXT NOT NULL,
            referenceId INTEGER NOT NULL
        );
        CREATE TABLE sales_day_closures(
            businessEpochDay INTEGER PRIMARY KEY NOT NULL,
            summaryId INTEGER NOT NULL
            {status}
        );
        """,
    )
    return connection


def install_sales_guards(connection: sqlite3.Connection, has_status: bool) -> None:
    closed_clause = "c.status='CLOSED' AND " if has_status else ""
    for trigger in sales_guard_sql():
        connection.executescript(trigger.replace("${closedClosure}", closed_clause))


def verify_version_35_sales_guards() -> None:
    connection = new_sales_database(has_status=False)
    try:
        install_sales_guards(connection, has_status=False)
        columns = {
            row[1]
            for row in connection.execute("PRAGMA table_info('sales_day_closures')")
        }
        if "status" in columns:
            raise AssertionError("version 35 must not expose the version-36 status column")

        connection.executescript(
            """
            INSERT INTO daily_sales_summaries(id,businessEpochDay) VALUES(1,100);
            INSERT INTO daily_sales_menu_lines(id,summaryId) VALUES(1,1);
            INSERT INTO stock_movements(id,referenceType,referenceId) VALUES(1,'DAILY_SALES',1);
            INSERT INTO sales_day_closures(businessEpochDay,summaryId) VALUES(100,1);
            """,
        )
        guarded = (
            "INSERT INTO daily_sales_summaries(businessEpochDay) VALUES(100)",
            "UPDATE daily_sales_summaries SET businessEpochDay=101 WHERE id=1",
            "DELETE FROM daily_sales_summaries WHERE id=1",
            "INSERT INTO daily_sales_menu_lines(summaryId) VALUES(1)",
            "UPDATE daily_sales_menu_lines SET summaryId=1 WHERE id=1",
            "DELETE FROM daily_sales_menu_lines WHERE id=1",
            "INSERT INTO stock_movements(referenceType,referenceId) VALUES('DAILY_SALES',1)",
            "UPDATE stock_movements SET referenceId=1 WHERE id=1",
            "DELETE FROM stock_movements WHERE id=1",
        )
        for sql in guarded:
            expect_guard_failure(connection, sql, "SALES_DAY_CLOSED")
        connection.execute("INSERT INTO daily_sales_summaries(businessEpochDay) VALUES(101)")
    finally:
        connection.close()


def verify_version_36_reopen_guards() -> None:
    connection = new_sales_database(has_status=True)
    try:
        connection.execute(
            "INSERT INTO daily_sales_summaries(id,businessEpochDay) VALUES(1,100)",
        )
        install_sales_guards(connection, has_status=True)
        connection.execute(
            "INSERT INTO sales_day_closures(businessEpochDay,summaryId,status) "
            "VALUES(100,1,'CLOSED')",
        )
        expect_guard_failure(
            connection,
            "UPDATE daily_sales_summaries SET businessEpochDay=101 WHERE id=1",
            "SALES_DAY_CLOSED",
        )
        connection.execute(
            "UPDATE sales_day_closures SET status='REOPENED' WHERE businessEpochDay=100",
        )
        connection.execute(
            "UPDATE daily_sales_summaries SET businessEpochDay=101 WHERE id=1",
        )
    finally:
        connection.close()


def inventory_lot_insert_guard_sql() -> str:
    guards = source(
        "app/src/main/java/ir/restaurant/management/data/db/EnterpriseLedgerGuards.kt",
    )
    match = re.search(
        r'"""(CREATE TRIGGER IF NOT EXISTS trg_inventory_lots_validate_insert.*?)"""',
        guards,
        flags=re.DOTALL,
    )
    if match is None:
        raise AssertionError("inventory-lot insert guard was not found")
    return match.group(1)


def verify_inventory_lot_reference_contract() -> None:
    connection = sqlite3.connect(":memory:")
    try:
        connection.executescript(
            """
            CREATE TABLE inventory_items(id INTEGER PRIMARY KEY, trackExpiry INTEGER NOT NULL);
            CREATE TABLE goods_receipts(id INTEGER PRIMARY KEY);
            CREATE TABLE inventory_lots(
                globalId TEXT NOT NULL,
                correlationId TEXT NOT NULL,
                itemId INTEGER NOT NULL,
                locationId INTEGER NOT NULL,
                lotCode TEXT NOT NULL,
                receivedEpochDay INTEGER NOT NULL,
                quantityMicros INTEGER NOT NULL,
                initialQuantityMicros INTEGER NOT NULL,
                unitCostRial INTEGER NOT NULL,
                status TEXT NOT NULL,
                createdByActorId INTEGER,
                createdAtEpochMillis INTEGER NOT NULL,
                productionEpochDay INTEGER,
                expiryEpochDay INTEGER,
                sourceReceiptId INTEGER
            );
            INSERT INTO inventory_items(id,trackExpiry) VALUES(1,1);
            """,
        )
        connection.executescript(inventory_lot_insert_guard_sql())
        connection.execute(
            """INSERT INTO inventory_lots VALUES(
                'opening-global','opening-correlation',1,1,'OPENING-LOT',80,
                2000000,2000000,200000,'ACTIVE',1,1000,NULL,90,NULL
            )""",
        )
        fake_receipt = """INSERT INTO inventory_lots VALUES(
            'fake-receipt-global','fake-receipt-correlation',1,1,'FAKE-RECEIPT',80,
            2000000,2000000,200000,'ACTIVE',1,1000,NULL,90,71
        )"""
        expect_guard_failure(connection, fake_receipt, "INVALID_INVENTORY_LOT")
        connection.execute("INSERT INTO goods_receipts(id) VALUES(71)")
        connection.execute(fake_receipt)
    finally:
        connection.close()


def verify_test_contracts() -> None:
    junit_files = (
        "DailySalesReversalIntegrationTest.kt",
        "ManagementControlTransactionIntegrationTest.kt",
        "RecipeVersionIntegrationTest.kt",
    )
    repository_tests = ROOT / "app/src/androidTest/java/ir/restaurant/management/data/repository"
    for name in junit_files:
        test = (repository_tests / name).read_text(encoding="utf-8")
        if "fun setUp(): Unit = runBlocking" not in test:
            raise AssertionError(f"{name} has no explicit Unit @Before contract")

    transfer = (repository_tests / "InventoryTransferWorkflowIntegrationTest.kt").read_text(
        encoding="utf-8",
    )
    transfer_setup = section(transfer, "fun setUp() = runBlocking", "    @After")
    for required in (
        "movementType = InventoryMovementType.OPENING_BALANCE",
        "referenceType = InventoryReferenceType.MIGRATION",
        "reasonCode = InventoryReasonCode.OPENING_BALANCE",
    ):
        if required not in transfer_setup:
            raise AssertionError(f"transfer fixture is missing {required}")

    waste = (repository_tests / "InventoryWasteWorkflowIntegrationTest.kt").read_text(
        encoding="utf-8",
    )
    waste_case = section(
        waste,
        "fun expiredLotWasteUsesExactLotCostAndPostsAllEffectsAtomically()",
        "    @Test\n    fun approvalRequiresPermission",
    )
    for required in (
        "movementType = InventoryMovementType.OPENING_BALANCE",
        "referenceType = InventoryReferenceType.MIGRATION",
    ):
        if required not in waste_case:
            raise AssertionError(f"waste fixture is missing {required}")

    asset = (repository_tests / "AssetOutboxIntegrationTest.kt").read_text(encoding="utf-8")
    disposal = section(
        asset,
        "fun successfulDisposalCommitsAssetAndVersionedOutboxTogether()",
        "    @Test\n    fun legacyAssetCanBeRecognized",
    )
    for required in (
        "syncRecorder = null",
        "assertEquals(1, changes.size)",
        "assertEquals(1L, change.revision)",
        "assertEquals(1, afterReplay.size)",
    ):
        if required not in disposal:
            raise AssertionError(f"asset disposal contract is missing {required}")
    if "assertEquals(2, changes.size)" in disposal:
        raise AssertionError("asset disposal test still accepts the polluted two-event fixture")

    migration = source(
        "app/src/main/java/ir/restaurant/management/data/db/migration/OperationsMigrations.kt",
    )
    migration_34_35 = section(migration, "MIGRATION_34_35", "MIGRATION_35_36")
    if "installSalesDayGuards(db, hasClosureStatusColumn = false)" not in migration_34_35:
        raise AssertionError("34 -> 35 migration did not select the version-35 guard contract")


def main() -> None:
    verify_version_35_sales_guards()
    verify_version_36_reopen_guards()
    verify_inventory_lot_reference_contract()
    verify_test_contracts()
    print(
        "RUNTIME_CRASH_REPAIR_HOST_PASS "
        "sales_day_guards=9 version35=no_status version36=controlled_reopen "
        "lot_reference_guard=strict junit_setup_unit=3 asset_outbox_fixture=isolated",
    )


if __name__ == "__main__":
    main()
