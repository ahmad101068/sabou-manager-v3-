#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit('usage: phase3-hotfix-04.py <phase3-source-root>')
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

build = 'app/build.gradle.kts'
# Room 2.8.4 migration bundle serializers were generated against the pre-1.8
# GeneratedSerializer ABI. Pin the serialization runtime on every configuration
# (including app APK, androidTest APK and KSP) so Android parent-first classloading
# cannot reintroduce the 1.8.x interface at instrumentation runtime.
replace_once(
    build,
    '''    androidTestImplementation(libs.androidx.sqlite.framework)\n    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3") {\n        version { strictly("1.7.3") }\n    }\n    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3") {\n        version { strictly("1.7.3") }\n    }\n''',
    '''    androidTestImplementation(libs.androidx.sqlite.framework)\n''',
)
path = root / build
text = path.read_text(encoding='utf-8')
anchor = 'dependencies {\n'
if text.count(anchor) != 1:
    raise SystemExit(f'{build}: dependencies block anchor is not unique')
compat = '''// Room 2.8.4 migration serializers are binary-incompatible with kotlinx.serialization 1.8.x.\n// The application does not use kotlinx.serialization directly; keep Room's migration runtime on 1.7.3\n// across app/test/KSP classpaths until Room is upgraded to a compatible release.\nconfigurations.configureEach {\n    resolutionStrategy.eachDependency {\n        if (requested.group == "org.jetbrains.kotlinx" && requested.name in setOf(\n                "kotlinx-serialization-core",\n                "kotlinx-serialization-json",\n                "kotlinx-serialization-json-okio",\n            )\n        ) {\n            useVersion("1.7.3")\n            because("Room 2.8.4 migration serializer ABI requires kotlinx.serialization < 1.8")\n        }\n    }\n}\n\n'''
text = text.replace(anchor, compat + anchor, 1)
path.write_text(text, encoding='utf-8')

phase2 = 'app/src/androidTest/java/ir/restaurant/management/data/repository/Phase2CorrectionIntegrationTest.kt'
# Every daily-sales fixture must name the actual branch-owned consumption location.
replace_once(
    phase2,
    '''                branchId = 1L,\n                settlements = listOf(\n''',
    '''                branchId = 1L,\n                locationId = branchLocationId,\n                settlements = listOf(\n''',
)
replace_once(
    phase2,
    '''            sales.createDraft(DailySalesDraft(day,0,0,0,1,0,0,lines=listOf(DailyMenuSaleDraft(1,1_000_000,1)),branchId=1L))\n''',
    '''            sales.createDraft(DailySalesDraft(day,0,0,0,1,0,0,lines=listOf(DailyMenuSaleDraft(1,1_000_000,1)),branchId=1L,locationId=branchLocationId))\n''',
)
# First weighted-average regression: consume from the explicit branch location and assert it is cleared.
replace_once(
    phase2,
    '''                branchId = 1L,\n                settlements = listOf(DailySalesSettlementDraft(SalesSettlementType.CASH, 1_000L)),\n''',
    '''                branchId = 1L,\n                locationId = branchLocationId,\n                settlements = listOf(DailySalesSettlementDraft(SalesSettlementType.CASH, 1_000L)),\n''',
)
replace_once(
    phase2,
    '''        val sourceLocationId = requireNotNull(database.managementControlDao().defaultLocationId())\n        assertEquals(0L, database.inventoryBalanceDao().byKey(itemId, sourceLocationId)?.onHandMicros)\n        assertEquals(0L, database.inventoryBalanceDao().byKey(itemId, sourceLocationId)?.inventoryValueRial)\n''',
    '''        assertEquals(0L, database.inventoryBalanceDao().byKey(itemId, branchLocationId)?.onHandMicros)\n        assertEquals(0L, database.inventoryBalanceDao().byKey(itemId, branchLocationId)?.inventoryValueRial)\n''',
)
# Exact-source regression now selects branchLocationId explicitly. The other location remains untouched,
# and costing must use 900,000 / 8,000,000 rather than either default-location or aggregate averages.
replace_once(
    phase2,
    '''                branchId = 1L,\n                settlements = listOf(DailySalesSettlementDraft(SalesSettlementType.CASH, 500L)),\n''',
    '''                branchId = 1L,\n                locationId = branchLocationId,\n                settlements = listOf(DailySalesSettlementDraft(SalesSettlementType.CASH, 500L)),\n''',
)
replace_once(
    phase2,
    '''        assertEquals(sourceLocationId, movement.locationId)\n        assertEquals(-100_000L, movement.valueDeltaRial)\n        assertEquals(100_000L, database.dailySalesDao().summary(saleId)?.theoreticalCostRial)\n        assertEquals(1_000_000L, database.inventoryBalanceDao().byKey(itemId, sourceLocationId)?.onHandMicros)\n        assertEquals(100_000L, database.inventoryBalanceDao().byKey(itemId, sourceLocationId)?.inventoryValueRial)\n        assertEquals(8_000_000L, database.inventoryBalanceDao().byKey(itemId, branchLocationId)?.onHandMicros)\n        assertEquals(900_000L, database.inventoryBalanceDao().byKey(itemId, branchLocationId)?.inventoryValueRial)\n        assertEquals(9_000_000L, database.inventoryDao().byId(itemId)?.stockMicros)\n        assertEquals(1_000_000L, database.inventoryDao().byId(itemId)?.inventoryValueRial)\n''',
    '''        assertEquals(branchLocationId, movement.locationId)\n        assertEquals(-112_500L, movement.valueDeltaRial)\n        assertEquals(112_500L, database.dailySalesDao().summary(saleId)?.theoreticalCostRial)\n        assertEquals(2_000_000L, database.inventoryBalanceDao().byKey(itemId, sourceLocationId)?.onHandMicros)\n        assertEquals(200_000L, database.inventoryBalanceDao().byKey(itemId, sourceLocationId)?.inventoryValueRial)\n        assertEquals(7_000_000L, database.inventoryBalanceDao().byKey(itemId, branchLocationId)?.onHandMicros)\n        assertEquals(787_500L, database.inventoryBalanceDao().byKey(itemId, branchLocationId)?.inventoryValueRial)\n        assertEquals(9_000_000L, database.inventoryDao().byId(itemId)?.stockMicros)\n        assertEquals(987_500L, database.inventoryDao().byId(itemId)?.inventoryValueRial)\n''',
)
# Lot-controlled seed data must live in the same explicit consumption location as the sale.
replace_once(
    phase2,
    '''        val locationId = requireNotNull(database.managementControlDao().defaultLocationId())\n        database.inventoryLotDao().insert(\n''',
    '''        val locationId = branchLocationId\n        database.inventoryLotDao().insert(\n''',
)
# Shared sales fixtures used by multiple Phase-2 regression tests inherit the explicit Phase-3 location contract.
replace_once(
    phase2,
    '''        branchId=1L,\n        settlements=listOf(\n''',
    '''        branchId=1L,\n        locationId=branchLocationId,\n        settlements=listOf(\n''',
)
replace_once(
    phase2,
    '''        branchId=1L,\n        settlements=listOf(DailySalesSettlementDraft(SalesSettlementType.CASH,amount)),\n''',
    '''        branchId=1L,\n        locationId=branchLocationId,\n        settlements=listOf(DailySalesSettlementDraft(SalesSettlementType.CASH,amount)),\n''',
)

print('PHASE3_HOTFIX_04=APPLIED')
