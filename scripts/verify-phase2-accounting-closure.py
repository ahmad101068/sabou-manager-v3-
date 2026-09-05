#!/usr/bin/env python3
"""Host-side executable evidence for the Phase-2 final accounting closure patch.

This does not replace Room migration/instrumentation tests. It executes the actual deterministic
backfill UPDATE statements and the actual canonical branch-P&L SQL extracted from Kotlin source
against SQLite so branch isolation/backfill semantics can be checked even when Gradle is offline.
"""
from __future__ import annotations

import re
import sqlite3
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MIGRATION = ROOT / "app/src/main/java/ir/restaurant/management/data/db/migration/AccountingBranchScopeMigration.kt"
DAO = ROOT / "app/src/main/java/ir/restaurant/management/data/db/AccountingDao.kt"


def extract_backfill_updates() -> list[str]:
    text = MIGRATION.read_text(encoding="utf-8")
    updates = [textwrap.dedent(sql).strip() for sql in re.findall(r'"""(UPDATE journal_entries.*?)"""\.trimIndent\(\)', text, re.S)]
    if len(updates) < 5:
        raise AssertionError(f"expected >=5 journal backfill/safety UPDATEs, found {len(updates)}")
    return updates


def extract_branch_pnl_query() -> str:
    text = DAO.read_text(encoding="utf-8")
    function_at = text.index("suspend fun branchProfitLoss")
    query_at = text.rfind("@Query(", 0, function_at)
    if query_at < 0:
        raise AssertionError("branchProfitLoss @Query not found")
    block = text[query_at:function_at]
    match = re.search(r'"""(.*?)"""', block, re.S)
    if not match:
        raise AssertionError("branchProfitLoss SQL not found")
    return textwrap.dedent(match.group(1)).strip()


def migration_semantics() -> None:
    db = sqlite3.connect(":memory:")
    db.executescript(
        """
        CREATE TABLE daily_sales_summaries(id INTEGER PRIMARY KEY, branchId INTEGER NOT NULL, isLegacyArchive INTEGER NOT NULL);
        CREATE TABLE receivables(id INTEGER PRIMARY KEY, branchId INTEGER NOT NULL);
        CREATE TABLE receivable_collections(id INTEGER PRIMARY KEY, receivableId INTEGER NOT NULL);
        CREATE TABLE journal_entries(
            id INTEGER PRIMARY KEY,
            sourceType TEXT NOT NULL,
            sourceId INTEGER NOT NULL,
            reversalOfEntryId INTEGER,
            branchId INTEGER,
            accountingScope TEXT NOT NULL DEFAULT 'UNASSIGNED_LEGACY'
        );
        INSERT INTO daily_sales_summaries VALUES(10,3,0);
        INSERT INTO daily_sales_summaries VALUES(11,1,1);
        INSERT INTO receivables VALUES(20,2);
        INSERT INTO receivable_collections VALUES(30,20);
        INSERT INTO journal_entries(id,sourceType,sourceId,reversalOfEntryId) VALUES(1,'DAILY_SALES',10,NULL);
        INSERT INTO journal_entries(id,sourceType,sourceId,reversalOfEntryId) VALUES(2,'DAILY_SALES',11,NULL);
        INSERT INTO journal_entries(id,sourceType,sourceId,reversalOfEntryId) VALUES(3,'RECEIVABLE_COLLECTION',30,NULL);
        INSERT INTO journal_entries(id,sourceType,sourceId,reversalOfEntryId) VALUES(4,'UNKNOWN_LEGACY',99,NULL);
        INSERT INTO journal_entries(id,sourceType,sourceId,reversalOfEntryId) VALUES(5,'CUSTOM_REVERSAL',999,3);
        """
    )
    for sql in extract_backfill_updates():
        db.execute(sql)

    rows = {row[0]: row[1:] for row in db.execute("SELECT id,branchId,accountingScope FROM journal_entries ORDER BY id")}
    assert rows[1] == (3, "BRANCH"), rows
    # Phase-1 converted archive had branchId=1 only as a compatibility default; never trust it.
    assert rows[2] == (None, "UNASSIGNED_LEGACY"), rows
    assert rows[3] == (2, "BRANCH"), rows
    assert rows[4] == (None, "UNASSIGNED_LEGACY"), rows
    assert rows[5] == (2, "BRANCH"), rows


def pnl_semantics() -> None:
    db = sqlite3.connect(":memory:")
    db.executescript(
        """
        CREATE TABLE accounts(code TEXT PRIMARY KEY, type TEXT NOT NULL);
        CREATE TABLE journal_entries(
            id INTEGER PRIMARY KEY,
            status TEXT NOT NULL,
            accountingScope TEXT NOT NULL,
            branchId INTEGER,
            entryEpochDay INTEGER NOT NULL
        );
        CREATE TABLE journal_lines(
            id INTEGER PRIMARY KEY,
            entryId INTEGER NOT NULL,
            accountCode TEXT NOT NULL,
            debitRial INTEGER NOT NULL,
            creditRial INTEGER NOT NULL
        );
        INSERT INTO accounts VALUES('1101','ASSET');
        INSERT INTO accounts VALUES('2103','LIABILITY');
        INSERT INTO accounts VALUES('4101','REVENUE');
        INSERT INTO accounts VALUES('4103','REVENUE');
        INSERT INTO accounts VALUES('5101','EXPENSE');
        INSERT INTO accounts VALUES('6101','EXPENSE');
        INSERT INTO accounts VALUES('6105','EXPENSE');
        INSERT INTO accounts VALUES('6113','EXPENSE');
        INSERT INTO accounts VALUES('6114','EXPENSE');
        INSERT INTO accounts VALUES('6115','EXPENSE');

        -- Branch 2: Revenue 105M, tax 9M (liability), COGS 48M, OpEx 12M, Payroll 9M.
        INSERT INTO journal_entries VALUES(100,'POSTED','BRANCH',2,200);
        INSERT INTO journal_lines VALUES(1,100,'1101',114000000,0);
        INSERT INTO journal_lines VALUES(2,100,'4101',0,95000000);
        INSERT INTO journal_lines VALUES(3,100,'4103',0,10000000);
        INSERT INTO journal_lines VALUES(4,100,'2103',0,9000000);
        INSERT INTO journal_entries VALUES(101,'POSTED','BRANCH',2,200);
        INSERT INTO journal_lines VALUES(5,101,'5101',48000000,0);
        INSERT INTO journal_entries VALUES(102,'POSTED','BRANCH',2,200);
        INSERT INTO journal_lines VALUES(6,102,'6105',12000000,0);
        INSERT INTO journal_entries VALUES(103,'POSTED','BRANCH',2,200);
        INSERT INTO journal_lines VALUES(7,103,'6101',9000000,0);

        -- Other scopes must not leak into branch 2.
        INSERT INTO journal_entries VALUES(104,'POSTED','BRANCH',1,200);
        INSERT INTO journal_lines VALUES(8,104,'6105',100000000,0);
        INSERT INTO journal_entries VALUES(105,'POSTED','ORGANIZATION',NULL,200);
        INSERT INTO journal_lines VALUES(9,105,'6105',500000000,0);

        -- Separate period: unassigned history marks data incomplete but is never allocated.
        INSERT INTO journal_entries VALUES(106,'POSTED','UNASSIGNED_LEGACY',NULL,201);
        INSERT INTO journal_lines VALUES(10,106,'6105',300000000,0);
        """
    )
    query = extract_branch_pnl_query()
    row = db.execute(query, {"branchId": 2, "fromEpochDay": 200, "toEpochDay": 200}).fetchone()
    assert row is not None
    revenue, cogs, opex, payroll, un_rev, un_cogs, un_opex, un_payroll = row
    assert revenue == 105_000_000, row
    assert cogs == 48_000_000, row
    assert opex == 12_000_000, row
    assert payroll == 9_000_000, row
    assert (un_rev, un_cogs, un_opex, un_payroll) == (0, 0, 0, 0), row
    assert revenue - cogs == 57_000_000
    assert revenue - cogs - opex - payroll == 36_000_000
    # Tax payable (9M) was in the same journal but is not revenue.
    assert revenue != 114_000_000

    incomplete = db.execute(query, {"branchId": 2, "fromEpochDay": 201, "toEpochDay": 201}).fetchone()
    assert incomplete is not None and incomplete[6] == 1, incomplete
    assert incomplete[2] == 0, incomplete


def source_guards() -> None:
    migration = MIGRATION.read_text(encoding="utf-8")
    assert "ds.isLegacyArchive = 0" in migration
    assert "branchId ?: 1L" not in migration
    assert "DEFAULT_BRANCH" not in migration
    dao = DAO.read_text(encoding="utf-8")
    assert "je.accountingScope = 'BRANCH'" in dao
    assert "jl.accountCode IN ('4101','4103')" in dao
    assert "jl.accountCode = '5101'" in dao
    assert "NOT IN ('5101','6101','6113','6114','6115')" in dao


def main() -> None:
    source_guards()
    migration_semantics()
    pnl_semantics()
    print("PHASE2_ACCOUNTING_CLOSURE_HOST_VERIFICATION=PASS")
    print("DETERMINISTIC_BACKFILL=PASS")
    print("BRANCH_PNL_ISOLATION=PASS")
    print("TAX_EXCLUDED_FROM_REVENUE=PASS")
    print("UNASSIGNED_LEGACY_NOT_ALLOCATED=PASS")


if __name__ == "__main__":
    main()
