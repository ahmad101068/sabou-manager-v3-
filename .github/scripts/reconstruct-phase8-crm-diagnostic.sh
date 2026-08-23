#!/usr/bin/env bash
set -euo pipefail
workspace="${GITHUB_WORKSPACE:-$(pwd)}"
target="${1:-phase8-source}"
hotfix="${workspace}/phase8-remediation/phase8-hotfix-05.py"
expected="6343104427312e1d5bcc8d8626cc4a0a87a6fbae6b6cdffd49ca7bb33e66fc9a"

bash "${workspace}/.github/scripts/reconstruct-phase8-candidate.sh" "$target"
test -s "$hotfix"
actual="$(sha256sum "$hotfix" | awk '{print $1}')"
test "$actual" = "$expected" || { echo "::error::Phase8 CRM diagnostic hotfix digest mismatch: $actual"; exit 1; }
python3 "$hotfix" "${workspace}/${target}"
grep -Fq 'PHASE8_CRM_COLLECTION_UI_ERROR' "${workspace}/${target}/app/src/androidTest/java/ir/restaurant/management/ui/EnterpriseCoreComposeE2ETest.kt"
grep -Fq 'crm_command_error' "${workspace}/${target}/app/src/main/java/ir/restaurant/management/ui/CrmScreen.kt"
echo PHASE8_CRM_DIAGNOSTIC_RECONSTRUCTION=PASS
