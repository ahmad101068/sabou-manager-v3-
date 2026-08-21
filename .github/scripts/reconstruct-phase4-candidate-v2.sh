#!/usr/bin/env bash
set -euo pipefail
workspace="${GITHUB_WORKSPACE:-$(pwd)}"
target="${1:-phase4-source}"
root="${workspace}/${target}"
verify(){ local f="$1" e="$2"; test -s "$f"; local a; a="$(sha256sum "$f"|awk '{print $1}')"; test "$a" = "$e" || { echo "::error::digest mismatch $f $a"; return 1; }; }
patchfix(){ local n="$1" es="$2" ps="$3"; local e="${workspace}/phase4-remediation/phase4-hotfix-${n}.patch.xz.b64" p="${workspace}/.phase4-${n}.patch"; verify "$e" "$es"; base64 --decode "$e"|xz -d >"$p"; verify "$p" "$ps"; git -C "$workspace" apply --check --directory="$target" "$p"; git -C "$workspace" apply --directory="$target" "$p"; echo "PHASE4_HOTFIX_${n}=APPLIED"; }
pyfix(){ local n="$1" s="$2"; local f="${workspace}/phase4-remediation/phase4-hotfix-${n}.py"; verify "$f" "$s"; python3 "$f" "$root"; }
bash "${workspace}/.github/scripts/reconstruct-phase3-candidate.sh" "$target"
patchfix 01 1a5513b2b1588ee725b5ef53dcf458c0bfb0a641d1a30b853ea275546d631db1 74e9ba4a6c9ee73149bfe50e8b7bc2eaf58b960c70d7e23fe2d421b0fece7bd4
patchfix 02 526a9625be1185d58a0c23a470bba4c3d3195703a0d5337109ffbc698c63f3ca 6524a6e0abe24ab87875b614defdb8d2ce8aa93ad1e548570508da23a54ca8bd
pyfix 03 0c4e5a6bd81f9aa1b72fbf0fa8063882f4508f21f65fbd4829ab38d5e083c064
patchfix 04 e8d4cc1488f202737db44e1d224ec10d7a02d62ed267a8310c77c5bdbb4bba12 9718c14d6a9027e268591a58e3ba8b2098d0e8bee7548424eecedf15c12de1aa
pyfix 05 bcf5405e6b2ac0409824b207503dc1d129409e5dca1e65d4d6530a54d338083d
patchfix 06 ba21dbfea78cab8d92a6235c4bd3d8a3106359bb6aca673c799b93e0b1799706 7f8c5e5c49b56debe7d610cae610e37cdda0cabe2d98cdc24d52931019e56c2c
for f in \
 app/src/main/java/ir/restaurant/management/domain/personnel/PersonnelReferenceCode.kt \
 app/src/main/java/ir/restaurant/management/domain/personnel/AttendanceSessionCalculator.kt \
 app/src/main/java/ir/restaurant/management/domain/personnel/AttendancePunchSequencePolicy.kt \
 app/src/test/java/ir/restaurant/management/domain/personnel/AttendancePunchSequencePolicyTest.kt \
 app/src/androidTest/java/ir/restaurant/management/data/db/Migration56To57Test.kt; do test -s "${root}/${f}"; done
grep -Fq 'PersonnelReferenceCode.newShiftCode()' "${root}/app/src/main/java/ir/restaurant/management/data/repository/PersonnelSchedulingService.kt"
grep -Fq 'PersonnelReferenceCode.newWorkScheduleCode()' "${root}/app/src/main/java/ir/restaurant/management/data/repository/PersonnelSchedulingService.kt"
grep -Fq 'AttendanceSessionCalculator.summarize' "${root}/app/src/main/java/ir/restaurant/management/data/repository/PayrollBatchPreparationService.kt"
grep -Fq 'suspend fun recordPunch' "${root}/app/src/main/java/ir/restaurant/management/data/repository/PersonnelAttendanceService.kt"
grep -Fq 'suspend fun rejectCorrection' "${root}/app/src/main/java/ir/restaurant/management/data/repository/PersonnelAttendanceService.kt"
grep -Fq "NEW.status NOT IN ('APPROVED','REJECTED')" "${root}/app/src/main/java/ir/restaurant/management/data/db/HrPayrollGuards.kt"
grep -Fq 'internal const val APP_DATABASE_SCHEMA_VERSION = 57' "${root}/app/src/main/java/ir/restaurant/management/data/db/AppDatabase.kt"
if grep -R -n -E 'MAX\([^)]+\)[^\n]*\+[[:space:]]*1' "${root}/app/src/main/java/ir/restaurant/management/data/db/HrPayrollDao.kt" "${root}/app/src/main/java/ir/restaurant/management/data/db/PersonnelDao.kt" "${root}/app/src/main/java/ir/restaurant/management/data/repository" "${root}/app/src/main/java/ir/restaurant/management/domain/personnel"; then echo '::error::Unsafe MAX()+1 HR/payroll allocation detected'; exit 1; fi
if grep -n 'val firstIn = events.filter' "${root}/app/src/main/java/ir/restaurant/management/data/repository/PayrollBatchPreparationService.kt"; then echo '::error::First-in/last-out payroll span remains active'; exit 1; fi
echo 'PHASE4_RECONSTRUCTION=PASS'
echo 'ROOM_VERSION=57'
echo 'SCHEMA_CHANGED=YES'
echo 'MIGRATION_ADDED=YES'
