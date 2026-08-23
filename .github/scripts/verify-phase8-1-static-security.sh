#!/usr/bin/env bash
set -euo pipefail
root="${1:-phase8-1-source}"
cd "$root"
if grep -R -n 'fallbackToDestructiveMigration' app/src/main/java; then exit 1; fi
if grep -R -nE '@Ignore|@Disabled' app/src; then exit 1; fi
if grep -R -nE 'TODO\(|TODO:|FIXME' app/src/main; then exit 1; fi
if grep -R -nF -e 'MAX(revisionNo)' -e 'MAX(integritySequence)' app/src/main/java; then exit 1; fi
if grep -R -nE 'AppDatabase|inventoryDao\(|treasuryDao\(|securityDao\(' app/src/main/java/ir/restaurant/management/ui; then exit 1; fi
if grep -Fq 'Permission.ACCOUNTING' app/src/main/java/ir/restaurant/management/data/repository/LocalAlertRepository.kt; then echo 'global ACCOUNTING alert gate remains'; exit 1; fi
if grep -R -n 'Workforce / HR' app/src/main; then exit 1; fi
# Financial truth must remain integral Rial. Ratios derived for presentation are allowed,
# but no monetary field/property declaration may use Float or Double.
if grep -R -nE '(val|var)[[:space:]]+[A-Za-z0-9_]*(Amount|Rial|Price|Cost|Balance)[A-Za-z0-9_]*[[:space:]]*:[[:space:]]*(Float|Double)' app/src/main/java; then
  echo 'floating-point monetary field found'; exit 1
fi
grep -R -Fq 'FLAG_SECURE' app/src/main/java/ir/restaurant/management/ui
grep -Fq 'rowVersion' app/src/main/java/ir/restaurant/management/data/db/SecurityEntities.kt
grep -Fq 'expectedVersion' app/src/main/java/ir/restaurant/management/data/db/SecurityDao.kt
grep -Fq 'previousEventHash' app/src/main/java/ir/restaurant/management/data/db/ControlEntities.kt
grep -Fq 'eventHash' app/src/main/java/ir/restaurant/management/data/db/ControlEntities.kt
grep -Fq 'AndroidKeyStore' app/src/main/java/ir/restaurant/management/data/security/ForensicIntegrityLedger.kt
grep -Fq 'noBackupFilesDir' app/src/main/java/ir/restaurant/management/data/security/ForensicIntegrityLedger.kt
grep -Fq 'formatRialMoneyInput' app/src/main/java/ir/restaurant/management/ui/TreasuryScreen.kt
echo STATIC_SECURITY_GATE=PASS
