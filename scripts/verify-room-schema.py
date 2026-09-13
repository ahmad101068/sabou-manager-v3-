#!/usr/bin/env python3
"""Strict Room schema evidence verifier.

Exit codes:
  0 = current schema JSON exists and is structurally consistent with source
  1 = invalid/inconsistent evidence
  2 = current schema JSON has not been exported yet
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

FORBIDDEN_TABLES = {
    "restaurant_halls",
    "restaurant_tables",
    "restaurant_reservations",
    "restaurant_orders",
    "restaurant_order_lines",
    "restaurant_bill_splits",
    "kitchen_tickets",
    "kitchen_ticket_events",
    "sales_holds",
    "sales_hold_lines",
}


def current_room_version(project_root: Path) -> int:
    source = project_root / "app/src/main/java/ir/restaurant/management/data/db/AppDatabase.kt"
    text = source.read_text(encoding="utf-8")
    match = re.search(r"APP_DATABASE_SCHEMA_VERSION\s*=\s*(\d+)", text)
    if match is None:
        match = re.search(r"version\s*=\s*(\d+)", text)
    if match is None:
        raise ValueError("cannot determine Room version from AppDatabase.kt")
    return int(match.group(1))


def exported_versions(project_root: Path) -> list[int]:
    schema_dir = project_root / "app/schemas/ir.restaurant.management.data.db.AppDatabase"
    if not schema_dir.exists():
        return []
    return sorted(int(p.stem) for p in schema_dir.glob("*.json") if p.stem.isdigit())


def verify(project_root: Path) -> tuple[str, list[str]]:
    problems: list[str] = []
    try:
        version = current_room_version(project_root)
    except (OSError, ValueError) as error:
        return "FAIL", [str(error)]

    versions = exported_versions(project_root)
    latest = versions[-1] if versions else None
    print(f"CURRENT_ROOM_VERSION={version}")
    print(f"LATEST_SCHEMA_FILE={latest if latest is not None else 'NONE'}")

    schema_path = project_root / f"app/schemas/ir.restaurant.management.data.db.AppDatabase/{version}.json"
    if not schema_path.exists():
        print("ROOM_SCHEMA_EVIDENCE=PENDING")
        return "PENDING", [f"missing Room-generated schema: {schema_path.relative_to(project_root)}"]

    if (project_root / ".git").exists():
        tracked = subprocess.run(
            ["git", "-C", str(project_root), "ls-files", "--error-unmatch", str(schema_path.relative_to(project_root))],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
        ).returncode == 0
        if not tracked:
            print("ROOM_SCHEMA_EVIDENCE=FAIL")
            return "FAIL", [f"current Room schema is not tracked by Git: {schema_path.relative_to(project_root)}"]

    try:
        payload = json.loads(schema_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        print("ROOM_SCHEMA_EVIDENCE=FAIL")
        return "FAIL", [f"cannot parse {schema_path.name}: {error}"]

    database = payload.get("database")
    if not isinstance(database, dict):
        problems.append("schema JSON has no database object")
    else:
        if database.get("version") != version:
            problems.append(
                f"schema JSON version {database.get('version')!r} does not match source version {version}"
            )
        identity_hash = database.get("identityHash")
        if not isinstance(identity_hash, str) or not re.fullmatch(r"[0-9a-f]{32}", identity_hash):
            problems.append("schema JSON identityHash is missing or malformed")
        entities = database.get("entities")
        if not isinstance(entities, list) or not entities:
            problems.append("schema JSON entities are missing or empty")
        else:
            table_names = {entity.get("tableName") for entity in entities if isinstance(entity, dict)}
            forbidden = sorted(FORBIDDEN_TABLES & table_names)
            if forbidden:
                problems.append(f"forbidden POS/table/KDS tables present: {forbidden}")
        setup_queries = database.get("setupQueries")
        if not isinstance(setup_queries, list) or not any("room_master_table" in str(q) for q in setup_queries):
            problems.append("schema JSON lacks Room master-table setup evidence")

    if problems:
        print("ROOM_SCHEMA_EVIDENCE=FAIL")
        return "FAIL", problems

    print("ROOM_SCHEMA_EVIDENCE=PASS")
    return "PASS", []


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project-root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    status, problems = verify(args.project_root.resolve())
    for problem in problems:
        print(f" - {problem}")
    return 0 if status == "PASS" else 2 if status == "PENDING" else 1


if __name__ == "__main__":
    raise SystemExit(main())
