#!/usr/bin/env bash
set -euo pipefail
workspace="${GITHUB_WORKSPACE:-$(pwd)}"
target="${1:-phase6-source}"
root="${workspace}/${target}"
patch_b64="${workspace}/.phase6-final.patch.xz.b64"
patch_file="${workspace}/.phase6-final.patch"

bash "${workspace}/.github/scripts/reconstruct-phase5-candidate.sh" "$target"

chunks=(
  phase6-remediation/phase6-final.patch.xz.b64.00
  phase6-remediation/phase6-final.patch.xz.b64.01
  phase6-remediation/phase6-final.patch.xz.b64.02
  phase6-remediation/phase6-final.patch.xz.b64.03
  phase6-remediation/phase6-final.patch.xz.b64.04
  phase6-remediation/phase6-final.patch.xz.b64.05
)
: > "$patch_b64"
for rel in "${chunks[@]}"; do
  test -s "${workspace}/${rel}" || { echo "::error::missing ${rel}"; exit 1; }
  cat "${workspace}/${rel}" >> "$patch_b64"
done
base64 --decode "$patch_b64" | xz --decompress > "$patch_file"
actual="$(sha256sum "$patch_file" | awk '{print $1}')"
expected="331389fd31e7fddaa2bd1b6806e29905615b7691fccc9aeb9ea7a9588017b937"
test "$actual" = "$expected" || { echo "::error::Phase6 patch digest mismatch $actual"; exit 1; }
git -C "$workspace" apply --check --directory="$target" "$patch_file"
git -C "$workspace" apply --directory="$target" "$patch_file"

grep -Fq 'APP_DATABASE_SCHEMA_VERSION = 59' "$root/app/src/main/java/ir/restaurant/management/data/db/AppDatabase.kt"
grep -Rq 'MIGRATION_58_59' "$root/app/src/main/java/ir/restaurant/management/data/db/migration"
grep -Fq 'actorRoleSnapshot' "$root/app/src/main/java/ir/restaurant/management/data/db/ControlEntities.kt"
grep -Fq 'snoozedUntilEpochMillis' "$root/app/src/main/java/ir/restaurant/management/data/db/AlertEntities.kt"
grep -Fq 'completedByUserId' "$root/app/src/main/java/ir/restaurant/management/data/db/BusinessOperationsEntities.kt"
grep -Fq 'LocalDataScopeService(database, authorizer)' "$root/app/src/main/java/ir/restaurant/management/data/repository/LocalManagementWorkflowService.kt"
grep -Fq 'FROM receivables r' "$root/app/src/main/java/ir/restaurant/management/data/db/AlertDao.kt"
test -s "$root/app/src/androidTest/java/ir/restaurant/management/data/db/Migration58To59Test.kt"
test -s "$root/app/src/androidTest/java/ir/restaurant/management/data/repository/Phase6SecurityManagementIntegrationTest.kt"
if grep -R -n 'fallbackToDestructiveMigration' "$root/app/src/main/java"; then echo '::error::destructive migration fallback'; exit 1; fi

echo PHASE6_RECONSTRUCTION=PASS
echo PHASE5_BASELINE_SHA=5465031036dbe4514a93f34ff9208230fb864e38
echo ROOM_VERSION=59
echo PATCH_SHA256=$expected
