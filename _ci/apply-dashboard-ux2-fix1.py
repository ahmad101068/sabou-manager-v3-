#!/usr/bin/env python3
from pathlib import Path

ROOT = Path.cwd()
MAIN = ROOT / "app/src/main/java"
HELPER = MAIN / "ir/sabou/inventory/core/BigIntegerCompat.kt"
TEST = ROOT / "app/src/test/java/ir/sabou/inventory/core/BigIntegerCompatTest.kt"
IMPORT = "import ir.sabou.inventory.core.toLongExactCompat"

changed_calls = 0
changed_files = 0
for path in MAIN.rglob("*.kt"):
    text = path.read_text(encoding="utf-8")
    count = text.count(".longValueExact()")
    if not count:
        continue
    changed_calls += count
    changed_files += 1
    text = text.replace(".longValueExact()", ".toLongExactCompat()")
    if path != HELPER and "package ir.sabou.inventory.core" not in text and IMPORT not in text:
        lines = text.splitlines()
        package_index = next(i for i, line in enumerate(lines) if line.startswith("package "))
        insert_at = package_index + 1
        while insert_at < len(lines) and not lines[insert_at].strip():
            insert_at += 1
        lines.insert(insert_at, IMPORT)
        text = "\n".join(lines) + "\n"
    path.write_text(text, encoding="utf-8")

if changed_calls != 19:
    raise SystemExit(f"Expected exactly 19 longValueExact() calls, found {changed_calls}")

HELPER.write_text('''package ir.sabou.inventory.core

import java.math.BigInteger

private val LONG_MIN_BIG_INTEGER: BigInteger = BigInteger.valueOf(Long.MIN_VALUE)
private val LONG_MAX_BIG_INTEGER: BigInteger = BigInteger.valueOf(Long.MAX_VALUE)

/** API-23 compatible exact BigInteger-to-Long conversion. */
fun BigInteger.toLongExactCompat(): Long {
    if (this < LONG_MIN_BIG_INTEGER || this > LONG_MAX_BIG_INTEGER) {
        throw ArithmeticException("BigInteger value is outside Long range: $this")
    }
    return toLong()
}
''', encoding="utf-8")

TEST.parent.mkdir(parents=True, exist_ok=True)
TEST.write_text('''package ir.sabou.inventory.core

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BigIntegerCompatTest {
    @Test fun `converts values inside Long range exactly`() {
        assertEquals(Long.MIN_VALUE, BigInteger.valueOf(Long.MIN_VALUE).toLongExactCompat())
        assertEquals(0L, BigInteger.ZERO.toLongExactCompat())
        assertEquals(Long.MAX_VALUE, BigInteger.valueOf(Long.MAX_VALUE).toLongExactCompat())
    }

    @Test fun `rejects values above Long range`() {
        val value = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)
        assertThrows(ArithmeticException::class.java) { value.toLongExactCompat() }
    }

    @Test fun `rejects values below Long range`() {
        val value = BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE)
        assertThrows(ArithmeticException::class.java) { value.toLongExactCompat() }
    }
}
''', encoding="utf-8")

remaining = sum(p.read_text(encoding="utf-8").count(".longValueExact()") for p in MAIN.rglob("*.kt"))
if remaining:
    raise SystemExit(f"longValueExact() still present: {remaining}")
print(f"DASHBOARD_UX2_API23_BIG_INTEGER_FIX=PASS calls={changed_calls} files={changed_files}")
