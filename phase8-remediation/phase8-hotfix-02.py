from pathlib import Path
import sys

root = Path(sys.argv[1] if len(sys.argv) > 1 else 'phase8-source')

asset = root / 'app/src/main/java/ir/restaurant/management/data/repository/LocalAssetRepository.kt'
recipe = root / 'app/src/main/java/ir/restaurant/management/data/repository/RecipeMaterialResolver.kt'

for path in (asset, recipe):
    text = path.read_text()
    import_line = 'import ir.restaurant.management.core.toLongExactCompat\n'
    if import_line not in text:
        if path == asset:
            anchor = 'import ir.restaurant.management.core.currentLocalEpochDay\n'
        else:
            anchor = 'import ir.restaurant.management.data.db.AppDatabase\n'
        if anchor not in text:
            raise SystemExit(f'import anchor missing: {path}')
        text = text.replace(anchor, anchor + import_line, 1)
    path.write_text(text)

replacements = {
    asset: (
'''    private fun mulDivExact(a: Long, b: Long, divisor: Long): Long {
        require(a >= 0 && b >= 0 && divisor > 0)
        return BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).divide(BigInteger.valueOf(divisor)).longValueExact()
    }
''',
'''    private fun mulDivExact(a: Long, b: Long, divisor: Long): Long {
        require(a >= 0 && b >= 0 && divisor > 0)
        return BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).divide(BigInteger.valueOf(divisor)).toLongExactCompat()
    }
'''),
    recipe: (
'''    private fun mulDiv(a: Long, b: Long, divisor: Long): Long {
        require(a >= 0 && b >= 0 && divisor > 0)
        return BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).divide(BigInteger.valueOf(divisor)).longValueExact()
    }
''',
'''    private fun mulDiv(a: Long, b: Long, divisor: Long): Long {
        require(a >= 0 && b >= 0 && divisor > 0)
        return BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)).divide(BigInteger.valueOf(divisor)).toLongExactCompat()
    }
'''),
}

for path, (old, new) in replacements.items():
    text = path.read_text()
    if old in text:
        if text.count(old) != 1:
            raise SystemExit(f'expected exactly one arithmetic block: {path}')
        text = text.replace(old, new, 1)
        path.write_text(text)
    elif new not in text:
        raise SystemExit(f'expected API23 arithmetic block missing: {path}')

for path in replacements:
    text = path.read_text()
    if '.longValueExact()' in text:
        raise SystemExit(f'API23-incompatible BigInteger.longValueExact remains: {path}')
    if '.toLongExactCompat()' not in text or 'import ir.restaurant.management.core.toLongExactCompat' not in text:
        raise SystemExit(f'canonical API23 exact conversion missing: {path}')

print('PHASE8_HOTFIX_02_API23_BIG_INTEGER=PASS')
