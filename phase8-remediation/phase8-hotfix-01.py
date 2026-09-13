#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path.cwd()

def replace_exact(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding='utf-8')
    if old not in text:
        if new in text:
            print(f'{label}=ALREADY_APPLIED')
            return
        raise SystemExit(f'{label}: expected source block not found in {path}')
    if text.count(old) != 1:
        raise SystemExit(f'{label}: expected exactly one source block in {path}, found {text.count(old)}')
    path.write_text(text.replace(old, new), encoding='utf-8')
    print(f'{label}=APPLIED')

receipt = root / 'app/src/test/java/ir/restaurant/management/data/repository/GoodsReceiptIdempotencyTest.kt'
replace_exact(
    receipt,
    '''        lines = listOf(\n            GoodsReceiptLineDraft(\n                purchaseOrderLineId = ORDER_LINE_ID,\n                deliveredQtyMicros = 900_000,\n                acceptedQtyMicros = 700_000,\n                rejectionReason = "آسیب بسته‌بندی",\n            ),\n        ),\n    ).validated()''',
    '''        lines = listOf(\n            GoodsReceiptLineDraft(\n                purchaseOrderLineId = ORDER_LINE_ID,\n                deliveredQtyMicros = 900_000,\n                acceptedQtyMicros = 700_000,\n                rejectionReason = "آسیب بسته‌بندی",\n            ),\n        ),\n        destinationLocationId = LOCATION_ID,\n    ).validated()''',
    'PHASE8_HOTFIX_01_GOODS_RECEIPT_LOCATION',
)
replace_exact(
    receipt,
    '''        const val RECEIPT_ID = 30L\n''',
    '''        const val RECEIPT_ID = 30L\n        const val LOCATION_ID = 40L\n''',
    'PHASE8_HOTFIX_01_GOODS_RECEIPT_LOCATION_CONSTANT',
)

performance = root / 'app/src/test/java/ir/restaurant/management/ui/Part3BPerformanceContractTest.kt'
replace_exact(
    performance,
    '''        assertTrue(adaptive.contains("LazyColumn"))\n        assertTrue(adaptive.contains("items(rows, key = key)"))\n        assertTrue(!adaptive.contains("rows.forEach"))''',
    '''        assertTrue(adaptive.contains("LazyColumn"))\n        assertTrue(adaptive.contains("val visibleRows = rows.page(pageWindow)"))\n        assertTrue(adaptive.contains("items(visibleRows, key = key)"))\n        assertTrue(adaptive.contains("rows = visibleRows"))\n        assertTrue(!adaptive.contains("rows.forEach"))''',
    'PHASE8_HOTFIX_01_PAGED_LAZY_STABLE_KEYS',
)

# Fail closed on the exact Phase-8 invariants this hotfix is intended to preserve.
receipt_text = receipt.read_text(encoding='utf-8')
performance_text = performance.read_text(encoding='utf-8')
required_receipt = [
    'destinationLocationId = LOCATION_ID',
    'const val LOCATION_ID = 40L',
    ').validated()',
]
required_performance = [
    'assertTrue(desktop.contains("items(rows, key = key)"))',
    'assertTrue(adaptive.contains("val visibleRows = rows.page(pageWindow)"))',
    'assertTrue(adaptive.contains("items(visibleRows, key = key)"))',
    'assertTrue(adaptive.contains("rows = visibleRows"))',
    'assertTrue(!adaptive.contains("rows.forEach"))',
]
for token in required_receipt:
    if token not in receipt_text:
        raise SystemExit(f'missing goods-receipt invariant after hotfix: {token}')
for token in required_performance:
    if token not in performance_text:
        raise SystemExit(f'missing performance invariant after hotfix: {token}')
print('PHASE8_HOTFIX_01=PASS')
