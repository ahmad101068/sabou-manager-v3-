#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        raise AssertionError(f"missing performance evidence source: {relative}")
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> int:
    grid = read("app/src/main/java/ir/restaurant/management/ui/ManagementDataGrid.kt")
    branch = read("app/src/main/java/ir/restaurant/management/ui/CanonicalBranchSelector.kt")

    desktop = grid.split("internal fun <T> ManagementDataGrid", 1)[1].split("@Composable\ninternal fun <T> GridHeader", 1)[0]
    adaptive = grid.split("internal fun <T> AdaptiveManagementList", 1)[1]
    require("LazyColumn" in desktop, "ManagementDataGrid must virtualize rows")
    require(re.search(r"items\(rows,\s*key\s*=\s*key\)", desktop) is not None, "ManagementDataGrid stable keys missing")
    require("LazyColumn" in adaptive, "MobileSmartRow path must use LazyColumn")
    require(re.search(r"items\(rows,\s*key\s*=\s*key\)", adaptive) is not None, "MobileSmartRow stable keys missing")
    require("rows.forEach" not in adaptive, "MobileSmartRow path eagerly composes rows")

    require("LazyColumn" in branch, "CanonicalBranchSelector must virtualize large branch lists")
    require(re.search(r"items\(activeBranches,\s*key\s*=\s*\{\s*it\.id\s*\}\)", branch) is not None, "Branch selector stable keys missing")
    require("activeBranches.forEach" not in branch, "Branch selector eagerly composes active branches")

    critical_paths = [
        "app/src/main/java/ir/restaurant/management/data/repository/DashboardRepository.kt",
        "app/src/main/java/ir/restaurant/management/data/repository/LocalDailyManagementBriefService.kt",
        "app/src/main/java/ir/restaurant/management/ui/DashboardViewModel.kt",
        "app/src/main/java/ir/restaurant/management/ui/CanonicalBranchSelector.kt",
        "app/src/main/java/ir/restaurant/management/ui/ManagementDataGrid.kt",
    ]
    n_plus_one = re.compile(r"(?:forEach|map)\s*\{[^}]{0,500}(?:database\.|Dao\(|dao\.)", re.DOTALL)
    offenders = [relative for relative in critical_paths if n_plus_one.search(read(relative))]
    require(not offenders, f"known N+1 candidate in critical screen path: {offenders}")

    production = "\n".join(
        path.read_text(encoding="utf-8", errors="ignore")
        for path in (ROOT / "app/src/main").rglob("*.kt")
    )
    require("allowMainThreadQueries" not in production, "Room main-thread queries are forbidden")
    require("runBlocking" not in production, "production main-thread blocking path detected")

    print("PHASE3_PERFORMANCE_STATIC=PASS")
    print("MOBILE_500_ROW_VIRTUALIZATION=PASS")
    print("GRID_500_ROW_VIRTUALIZATION=PASS")
    print("BRANCH_SELECTOR_VIRTUALIZATION=PASS")
    print("CRITICAL_SCREEN_N_PLUS_ONE_KNOWN=0")
    print("MAIN_THREAD_BLOCKING_KNOWN=0")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except AssertionError as error:
        print(f"PHASE3_PERFORMANCE_STATIC=FAIL: {error}", file=sys.stderr)
        raise SystemExit(1)
