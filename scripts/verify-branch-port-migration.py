#!/usr/bin/env python3
"""Host-side evidence for the Phase 2 Branch 53->54 migration.

This does not replace Room instrumentation. It exercises the migration's critical
SQLite operations against a Version-53-shaped fixture and fails closed if the
source no longer preserves the expected 53->54 edge. Later schema versions are
allowed as long as the canonical Branch migration itself remains registered.
"""
from __future__ import annotations

import re
import sqlite3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MIGRATION_SOURCE = ROOT / "app/src/main/java/ir/restaurant/management/data/db/migration/BranchCanonicalizationMigration.kt"
APP_DATABASE = ROOT / "app/src/main/java/ir/restaurant/management/data/db/AppDatabase.kt"


def require_source_contract() -> None:
    migration = MIGRATION_SOURCE.read_text(encoding="utf-8")
    database = APP_DATABASE.read_text(encoding="utf-8")
    assert re.search(r"Migration\(\s*53\s*,\s*54\s*\)", migration), "missing MIGRATION_53_54 edge"
    match = re.search(r"APP_DATABASE_SCHEMA_VERSION\s*=\s*(\d+)\b", database)
    assert match and int(match.group(1)) >= 54, "current Room version must preserve the v54 Branch canonicalization baseline"
    assert "fallbackToDestructiveMigration" not in database, "destructive migration fallback detected"
    assert "branchId = 1L" not in migration and "?: 1L" not in migration, "Branch-1 fallback detected"
    expected_daily_sales_source = (
        "SELECT DISTINCT branchId AS id FROM daily_sales_summaries "
        "WHERE branchId > 0 AND isLegacyArchive = 0"
    )
    assert expected_daily_sales_source in migration, "legacy Daily Sales are not excluded from Branch discovery"
    assert "WHERE branchId != 1" not in migration and "branchId <> 1" not in migration, "Branch ID special-case detected"


def create_v53_fixture(db: sqlite3.Connection, *, seed_default: bool = True) -> None:
    db.executescript(
        """
        PRAGMA foreign_keys=ON;
        CREATE TABLE daily_sales_summaries(
          id INTEGER PRIMARY KEY NOT NULL,
          branchId INTEGER NOT NULL DEFAULT 1,
          isLegacyArchive INTEGER NOT NULL,
          note TEXT NOT NULL DEFAULT ''
        );
        CREATE TABLE daily_sales_menu_lines(
          id INTEGER PRIMARY KEY NOT NULL,
          summaryId INTEGER NOT NULL,
          value INTEGER NOT NULL DEFAULT 0,
          FOREIGN KEY(summaryId) REFERENCES daily_sales_summaries(id) ON DELETE RESTRICT
        );
        CREATE TABLE sales_day_closures(
          id INTEGER PRIMARY KEY NOT NULL,
          summaryId INTEGER NOT NULL,
          FOREIGN KEY(summaryId) REFERENCES daily_sales_summaries(id) ON DELETE RESTRICT
        );
        CREATE TABLE daily_sales_settlements(
          id INTEGER PRIMARY KEY NOT NULL,
          summaryId INTEGER NOT NULL,
          FOREIGN KEY(summaryId) REFERENCES daily_sales_summaries(id) ON DELETE RESTRICT
        );
        CREATE INDEX idx_daily_sales_branch_fixture ON daily_sales_summaries(branchId);
        CREATE TRIGGER trg_daily_sales_note AFTER UPDATE OF note ON daily_sales_summaries BEGIN SELECT NEW.note; END;

        CREATE TABLE journal_entries(id INTEGER PRIMARY KEY NOT NULL, branchId INTEGER, accountingScope TEXT NOT NULL DEFAULT 'UNASSIGNED_LEGACY');
        CREATE TABLE receivables(id INTEGER PRIMARY KEY NOT NULL, branchId INTEGER NOT NULL);
        CREATE TABLE management_issues(id INTEGER PRIMARY KEY NOT NULL, branchId INTEGER NOT NULL);
        CREATE TABLE management_tasks(id INTEGER PRIMARY KEY NOT NULL, branchId INTEGER NOT NULL);
        CREATE TABLE checklist_templates(id INTEGER PRIMARY KEY NOT NULL, branchId INTEGER);
        CREATE TABLE checklist_runs(id INTEGER PRIMARY KEY NOT NULL, branchId INTEGER NOT NULL);
        CREATE TABLE shift_templates(id INTEGER PRIMARY KEY NOT NULL, branchId INTEGER);
        CREATE TABLE employees(id INTEGER PRIMARY KEY NOT NULL, branchName TEXT NOT NULL DEFAULT '');
        CREATE TABLE employment_assignments(id INTEGER PRIMARY KEY NOT NULL, branchName TEXT NOT NULL DEFAULT '');
        CREATE TABLE payroll_batches(id INTEGER PRIMARY KEY NOT NULL, branchName TEXT);
        CREATE TABLE storage_locations(id INTEGER PRIMARY KEY NOT NULL, branchName TEXT NOT NULL DEFAULT '');
        CREATE TABLE purchases(id INTEGER PRIMARY KEY NOT NULL, branchName TEXT NOT NULL DEFAULT '');
        CREATE TABLE fixed_assets(id INTEGER PRIMARY KEY NOT NULL, branch TEXT NOT NULL DEFAULT '');
        CREATE TABLE work_schedules(id INTEGER PRIMARY KEY NOT NULL, branchName TEXT NOT NULL DEFAULT '');
        CREATE TABLE sales_cash_reconciliations(id INTEGER PRIMARY KEY NOT NULL);

        """
    )
    if seed_default:
        db.executescript(
            """
            INSERT INTO daily_sales_summaries(id, branchId, isLegacyArchive, note) VALUES(1,2,0,'keep');
            INSERT INTO daily_sales_menu_lines(id, summaryId, value) VALUES(1,1,7);
            INSERT INTO sales_day_closures(id, summaryId) VALUES(1,1);
            INSERT INTO daily_sales_settlements(id, summaryId) VALUES(1,1);
            INSERT INTO journal_entries(id, branchId, accountingScope) VALUES(1,2,'BRANCH');
            INSERT INTO employees(id, branchName) VALUES(1,'ونک'),(2,'VANAK');
            INSERT INTO payroll_batches(id, branchName) VALUES(1,'ونک');
            INSERT INTO storage_locations(id, branchName) VALUES(1,'ونک'),(2,'vanak');
            INSERT INTO purchases(id, branchName) VALUES(1,'');
            INSERT INTO fixed_assets(id, branch) VALUES(1,'');
            INSERT INTO work_schedules(id, branchName) VALUES(1,'');
            """
        )


def apply_equivalent_migration(db: sqlite3.Connection) -> None:
    db.executescript(
        """
        CREATE TABLE IF NOT EXISTS branches (
          id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
          globalId TEXT NOT NULL,
          organizationId INTEGER,
          code TEXT,
          name TEXT NOT NULL,
          isActive INTEGER NOT NULL,
          createdAtEpochMillis INTEGER NOT NULL,
          updatedAtEpochMillis INTEGER NOT NULL
        );
        CREATE UNIQUE INDEX IF NOT EXISTS index_branches_globalId ON branches(globalId);
        CREATE UNIQUE INDEX IF NOT EXISTS index_branches_organizationId_code ON branches(organizationId,code);
        CREATE INDEX IF NOT EXISTS index_branches_name ON branches(name);
        CREATE INDEX IF NOT EXISTS index_branches_isActive ON branches(isActive);
        """
    )

    numeric_sources = " UNION ".join(
        [
            "SELECT DISTINCT branchId AS id FROM daily_sales_summaries WHERE branchId > 0 AND isLegacyArchive = 0",
            "SELECT branchId AS id FROM journal_entries WHERE branchId IS NOT NULL AND branchId > 0",
            "SELECT branchId AS id FROM receivables WHERE branchId > 0",
            "SELECT branchId AS id FROM management_issues WHERE branchId > 0",
            "SELECT branchId AS id FROM management_tasks WHERE branchId > 0",
            "SELECT branchId AS id FROM checklist_templates WHERE branchId IS NOT NULL AND branchId > 0",
            "SELECT branchId AS id FROM checklist_runs WHERE branchId > 0",
            "SELECT branchId AS id FROM shift_templates WHERE branchId IS NOT NULL AND branchId > 0",
        ]
    )
    db.execute(
        f"""
        INSERT OR IGNORE INTO branches(id,globalId,organizationId,code,name,isActive,createdAtEpochMillis,updatedAtEpochMillis)
        SELECT id,'legacy:branch:id:' || id,NULL,NULL,'Legacy Branch #' || id,1,0,0 FROM ({numeric_sources})
        """
    )

    parent = "daily_sales_summaries"
    children = ["daily_sales_menu_lines", "sales_day_closures", "daily_sales_settlements"]
    parent_sql = db.execute("SELECT sql FROM sqlite_master WHERE type='table' AND name=?", (parent,)).fetchone()[0]
    parent_sql_without_default = re.sub(
        r'([`"]?branchId[`"]?\s+INTEGER\s+NOT\s+NULL)\s+DEFAULT\s+(?:1|\'1\'|"1"|\(\s*1\s*\))',
        r"\1",
        parent_sql,
        flags=re.IGNORECASE,
    )
    assert parent_sql_without_default != parent_sql, "daily sales Branch-1 default was not found"

    definitions: dict[str, str] = {parent: parent_sql_without_default}
    for child in children:
        row = db.execute("SELECT sql FROM sqlite_master WHERE type='table' AND name=?", (child,)).fetchone()
        if row:
            definitions[child] = row[0]
    affected = list(definitions)
    schema_objects: list[str] = []
    for table in affected:
        schema_objects.extend(
            row[0]
            for row in db.execute(
                "SELECT sql FROM sqlite_master WHERE tbl_name=? AND type IN ('index','trigger') AND sql IS NOT NULL ORDER BY type,name",
                (table,),
            )
        )
    for table in affected:
        backup = f"phase2_branch_backup_{table}"
        db.execute(f'DROP TABLE IF EXISTS temp."{backup}"')
        db.execute(f'CREATE TEMP TABLE "{backup}" AS SELECT * FROM "{table}"')
    for table in reversed(affected):
        db.execute(f'DROP TABLE "{table}"')
    for table in affected:
        db.execute(definitions[table])
        backup = f"phase2_branch_backup_{table}"
        db.execute(f'INSERT INTO "{table}" SELECT * FROM temp."{backup}"')
    for sql in schema_objects:
        db.execute(sql)
    for table in affected:
        db.execute(f'DROP TABLE temp."phase2_branch_backup_{table}"')

    legacy_tables = [
        ("employees", "branchName"),
        ("employment_assignments", "branchName"),
        ("payroll_batches", "branchName"),
        ("storage_locations", "branchName"),
        ("purchases", "branchName"),
        ("fixed_assets", "branch"),
        ("work_schedules", "branchName"),
    ]
    for table, _ in legacy_tables:
        db.execute(f'ALTER TABLE "{table}" ADD COLUMN branchId INTEGER')
    db.execute('ALTER TABLE "sales_cash_reconciliations" ADD COLUMN branchId INTEGER')

    legacy_names = """
      SELECT trim(branchName) AS rawName FROM employees WHERE trim(branchName) <> ''
      UNION ALL SELECT trim(branchName) FROM employment_assignments WHERE trim(branchName) <> ''
      UNION ALL SELECT trim(branchName) FROM payroll_batches WHERE branchName IS NOT NULL AND trim(branchName) <> ''
      UNION ALL SELECT trim(branchName) FROM storage_locations WHERE trim(branchName) <> ''
      UNION ALL SELECT trim(branchName) FROM purchases WHERE trim(branchName) <> ''
      UNION ALL SELECT trim(branch) FROM fixed_assets WHERE trim(branch) <> ''
      UNION ALL SELECT trim(branchName) FROM work_schedules WHERE trim(branchName) <> ''
    """
    db.execute(
        f"""
        INSERT OR IGNORE INTO branches(globalId,organizationId,code,name,isActive,createdAtEpochMillis,updatedAtEpochMillis)
        SELECT 'legacy:branch:name:' || lower(rawName),NULL,NULL,MIN(rawName),1,0,0
        FROM ({legacy_names}) GROUP BY lower(rawName) HAVING COUNT(DISTINCT rawName)=1
        """
    )
    for table, column in legacy_tables:
        db.execute(
            f"""
            UPDATE "{table}"
            SET branchId=(SELECT b.id FROM branches b WHERE b.globalId='legacy:branch:name:' || lower(trim("{table}"."{column}")) LIMIT 1)
            WHERE branchId IS NULL AND trim("{column}") <> ''
              AND EXISTS(SELECT 1 FROM branches b WHERE b.globalId='legacy:branch:name:' || lower(trim("{table}"."{column}")))
            """
        )
    for table, _ in legacy_tables:
        db.execute(f'CREATE INDEX IF NOT EXISTS "index_{table}_branchId" ON "{table}"("branchId")')
    db.execute('CREATE INDEX IF NOT EXISTS index_sales_cash_reconciliations_branchId ON sales_cash_reconciliations(branchId)')


def verify(db: sqlite3.Connection) -> None:
    branch_info = next(row for row in db.execute("PRAGMA table_info(daily_sales_summaries)") if row[1] == "branchId")
    assert branch_info[4] is None, "daily_sales_summaries.branchId still has a default"
    assert db.execute("SELECT branchId,isLegacyArchive,note FROM daily_sales_summaries WHERE id=1").fetchone() == (2, 0, "keep")
    assert db.execute("SELECT value FROM daily_sales_menu_lines WHERE id=1").fetchone() == (7,)
    assert db.execute("SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='idx_daily_sales_branch_fixture'").fetchone()[0] == 1
    assert db.execute("SELECT COUNT(*) FROM sqlite_master WHERE type='trigger' AND name='trg_daily_sales_note'").fetchone()[0] == 1
    assert db.execute("SELECT COUNT(*) FROM branches WHERE id=2 AND globalId='legacy:branch:id:2'").fetchone()[0] == 1

    employee = db.execute("SELECT branchId FROM employees WHERE id=1").fetchone()[0]
    payroll = db.execute("SELECT branchId FROM payroll_batches WHERE id=1").fetchone()[0]
    storage = db.execute("SELECT branchId FROM storage_locations WHERE id=1").fetchone()[0]
    assert employee is not None and employee == payroll == storage
    assert db.execute("SELECT branchId FROM employees WHERE id=2").fetchone()[0] is None
    assert db.execute("SELECT branchId FROM storage_locations WHERE id=2").fetchone()[0] is None
    assert db.execute("SELECT branchId,accountingScope FROM journal_entries WHERE id=1").fetchone() == (2, "BRANCH")

    examined = db.execute(
        """SELECT
          (SELECT COUNT(*) FROM employees WHERE trim(branchName)<>'')+
          (SELECT COUNT(*) FROM payroll_batches WHERE trim(branchName)<>'')+
          (SELECT COUNT(*) FROM storage_locations WHERE trim(branchName)<>'')"""
    ).fetchone()[0]
    backfilled = db.execute(
        """SELECT
          (SELECT COUNT(*) FROM employees WHERE branchId IS NOT NULL)+
          (SELECT COUNT(*) FROM payroll_batches WHERE branchId IS NOT NULL)+
          (SELECT COUNT(*) FROM storage_locations WHERE branchId IS NOT NULL)"""
    ).fetchone()[0]
    ambiguous = db.execute(
        """SELECT
          (SELECT COUNT(*) FROM employees WHERE trim(branchName)<>'' AND branchId IS NULL)+
          (SELECT COUNT(*) FROM storage_locations WHERE trim(branchName)<>'' AND branchId IS NULL)"""
    ).fetchone()[0]
    print("MIGRATION_53_54_SQLITE_SIMULATION=PASS")
    print(f"FIXTURE_LEGACY_ROWS_EXAMINED={examined}")
    print(f"FIXTURE_LEGACY_ROWS_BACKFILLED={backfilled}")
    print(f"FIXTURE_AMBIGUOUS_UNASSIGNED={ambiguous}")


def verify_daily_sales_branch_evidence_scenarios() -> None:
    scenarios = [
        (
            "LEGACY_ONLY",
            [(1, 1, 1, "legacy-only")],
            {1: 0},
        ),
        (
            "REAL_ONLY",
            [(1, 1, 0, "real-one")],
            {1: 1},
        ),
        (
            "MIXED",
            [(1, 1, 1, "legacy-one"), (2, 2, 0, "real-two")],
            {1: 0, 2: 1},
        ),
        (
            "SAME_ID_LEGACY_AND_REAL",
            [(1, 2, 1, "legacy-two"), (2, 2, 0, "real-two")],
            {2: 1},
        ),
    ]
    for name, rows, expected_branch_counts in scenarios:
        db = sqlite3.connect(":memory:")
        try:
            create_v53_fixture(db, seed_default=False)
            db.executemany(
                "INSERT INTO daily_sales_summaries(id,branchId,isLegacyArchive,note) VALUES(?,?,?,?)",
                rows,
            )
            apply_equivalent_migration(db)
            for branch_id, expected_count in expected_branch_counts.items():
                actual = db.execute("SELECT COUNT(*) FROM branches WHERE id=?", (branch_id,)).fetchone()[0]
                assert actual == expected_count, f"{name}: branch {branch_id} count {actual} != {expected_count}"
            expected_rows = [(row[0], row[1], row[2], row[3]) for row in rows]
            actual_rows = list(
                db.execute(
                    "SELECT id,branchId,isLegacyArchive,note FROM daily_sales_summaries ORDER BY id"
                )
            )
            assert actual_rows == expected_rows, f"{name}: legacy/real Daily Sales rows changed"
            print(f"MIGRATION_53_54_{name}=PASS")
        finally:
            db.close()
    print("MIGRATION_53_54_DATA_PRESERVATION=PASS")


def main() -> int:
    require_source_contract()
    verify_daily_sales_branch_evidence_scenarios()
    db = sqlite3.connect(":memory:")
    try:
        create_v53_fixture(db)
        apply_equivalent_migration(db)
        verify(db)
        return 0
    finally:
        db.close()


if __name__ == "__main__":
    raise SystemExit(main())
