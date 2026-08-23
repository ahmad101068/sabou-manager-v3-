#!/usr/bin/env bash
set -euo pipefail
root="${1:-phase8-1-source}"
cd "$root"
if grep -R -n 'fallbackToDestructiveMigration' app/src/main/java; then exit 1; fi
if grep -R -nE '@Ignore|@Disabled' app/src; then exit 1; fi
if grep -R -nE 'TODO\(|TODO:|FIXME' app/src/main; then exit 1; fi
if grep -R -nE 'MAX\(revisionNo\)|MAX\(integritySequence\)[[:space:]]*\+[[:space:]]*1' app/src/main/java; then exit 1; fi
if grep -R -nE 'AppDatabase|inventoryDao\(|treasuryDao\(|securityDao\(' app/src/main/java/ir/restaurant/management/ui; then exit 1; fi
if grep -Fq 'Permission.ACCOUNTING' app/src/main/java/ir/restaurant/management/data/repository/LocalAlertRepository.kt; then echo 'global ACCOUNTING alert gate remains'; exit 1; fi
if grep -R -n 'Workforce / HR' app/src/main; then exit 1; fi
grep -R -Fq 'FLAG_SECURE' app/src/main/java/ir/restaurant/management/ui
grep -Fq 'rowVersion' app/src/main/java/ir/restaurant/management/data/db/SecurityEntities.kt
grep -Fq 'expectedVersion' app/src/main/java/ir/restaurant/management/data/db/SecurityDao.kt
grep -Fq 'previousEventHash' app/src/main/java/ir/restaurant/management/data/db/ControlEntities.kt
grep -Fq 'eventHash' app/src/main/java/ir/restaurant/management/data/db/ControlEntities.kt
grep -Fq 'AndroidKeyStore' app/src/main/java/ir/restaurant/management/data/security/ForensicIntegrityLedger.kt
grep -Fq 'noBackupFilesDir' app/src/main/java/ir/restaurant/management/data/security/ForensicIntegrityLedger.kt
grep -Fq 'formatRialMoneyInput' app/src/main/java/ir/restaurant/management/ui/TreasuryScreen.kt
echo STATIC_SECURITY_GATE=PASS
