#!/usr/bin/env bash
set -euo pipefail

workspace="${GITHUB_WORKSPACE:-$(pwd)}"
target="${1:-phase3-source}"

bash "${workspace}/.github/scripts/reconstruct-part3b-candidate.sh" "${target}"

phase2_patch="${workspace}/phase2-financial.patch"
base64 --decode "${workspace}/phase2-financial.patch.gz.b64" | gzip --decompress > "${phase2_patch}"

git -C "${workspace}" apply --check --directory="${target}" "${phase2_patch}"
git -C "${workspace}" apply --directory="${target}" "${phase2_patch}"

source_root="${workspace}/${target}"

# Phase 2 financial truth fail-closed reconstruction gates.
grep -Fq 'enum class TreasuryBusinessIntent' \
  "${source_root}/app/src/main/java/ir/restaurant/management/domain/treasury/TreasuryModels.kt"
grep -Fq 'SemanticAccountRole.CARD_SETTLEMENT' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/treasury/DefaultTreasuryAccountCatalog.kt"
grep -Fq 'SemanticAccountRole.PETTY_CASH' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/treasury/DefaultTreasuryAccountCatalog.kt"
grep -Fq 'businessIntent = TreasuryBusinessIntent.DAILY_SALES_SETTLEMENT' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/LocalDailySalesRepository.kt"
grep -Fq 'collectionByGlobalId' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/LocalReceivableService.kt"
grep -Fq 'daily_sales_confirmed_cost_snapshot_changed' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/repository/LocalDailySalesRepository.kt"
grep -Fq 'internal const val APP_DATABASE_SCHEMA_VERSION = 55' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/db/AppDatabase.kt"

schema_dir="${source_root}/app/schemas/ir.restaurant.management.data.db.AppDatabase"
test -s "${schema_dir}/55.json"
if test -e "${schema_dir}/56.json"; then
  echo '::error::Phase 2 must not create Room schema 56'
  exit 1
fi

if grep -R -E 'sourceType\.contains|contains\("(CUSTOMER|RECEIVABLE|SUPPLIER|PURCHASE|PAYROLL|EMPLOYEE)' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/treasury"; then
  echo '::error::Free-text treasury financial classification remains'
  exit 1
fi

if grep -R -E 'SemanticAccountRole\.(CASH|BANK|CARD_SETTLEMENT|PETTY_CASH)' \
  "${source_root}/app/src/main/java/ir/restaurant/management/data/repository"; then
  echo '::error::Repository direct liquidity GL posting remains outside Treasury'
  exit 1
fi

echo 'PHASE2_RECONSTRUCTION=PASS'
echo 'ROOM_VERSION=55'
echo 'SCHEMA_CHANGED=NO'
echo 'MIGRATION_ADDED=NO'
