#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import json
import tempfile
from pathlib import Path

SCRIPT = Path(__file__).with_name("verify-room-schema.py")
spec = importlib.util.spec_from_file_location("verify_room_schema", SCRIPT)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


def make_project(root: Path, version: int) -> Path:
    db_source = root / "app/src/main/java/ir/restaurant/management/data/db/AppDatabase.kt"
    db_source.parent.mkdir(parents=True)
    db_source.write_text(
        f"internal const val APP_DATABASE_SCHEMA_VERSION = {version}\n"
        "@Database(entities = [], version = APP_DATABASE_SCHEMA_VERSION, exportSchema = true)\n",
        encoding="utf-8",
    )
    return root / "app/schemas/ir.restaurant.management.data.db.AppDatabase"


def main() -> None:
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        fixture_version = 73
        schema_dir = make_project(root, fixture_version)
        schema_dir.mkdir(parents=True)
        (schema_dir / "50.json").write_text(
            json.dumps({"formatVersion": 1, "database": {"version": 50, "identityHash": "a" * 32, "entities": [{"tableName": "ok"}], "setupQueries": ["CREATE TABLE room_master_table"]}}),
            encoding="utf-8",
        )
        status, _ = module.verify(root)
        assert status == "PENDING", status

        (schema_dir / f"{fixture_version}.json").write_text(
            json.dumps({"formatVersion": 1, "database": {"version": fixture_version, "identityHash": "b" * 32, "entities": [{"tableName": "daily_sales_summaries"}], "setupQueries": ["CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)"]}}),
            encoding="utf-8",
        )
        status, problems = module.verify(root)
        assert status == "PASS", problems

        payload = json.loads((schema_dir / f"{fixture_version}.json").read_text(encoding="utf-8"))
        payload["database"]["entities"].append({"tableName": "restaurant_orders"})
        (schema_dir / f"{fixture_version}.json").write_text(json.dumps(payload), encoding="utf-8")
        status, problems = module.verify(root)
        assert status == "FAIL" and any("forbidden" in p for p in problems), problems

    print("ROOM_SCHEMA_VERIFIER_TEST=PASS")


if __name__ == "__main__":
    main()
