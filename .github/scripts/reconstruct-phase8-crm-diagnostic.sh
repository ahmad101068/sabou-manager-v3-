#!/usr/bin/env bash
set -euo pipefail
workspace="${GITHUB_WORKSPACE:-$(pwd)}"
target="${1:-phase8-source}"
root="${workspace}/${target}"

# The focused diagnostic must exercise the exact same hash-pinned Phase-8
# candidate as final verification. Do not layer a second, diagnostic-only
# source mutation on top of the release candidate.
bash "${workspace}/.github/scripts/reconstruct-phase8-candidate.sh" "$target"

e2e="$root/app/src/androidTest/java/ir/restaurant/management/ui/EnterpriseCoreComposeE2ETest.kt"
crm="$root/app/src/main/java/ir/restaurant/management/ui/CrmScreen.kt"

test -s "$e2e"
test -s "$crm"
grep -Fq 'fun crmCollection_viaCrmUi_updatesReceivableLedgerAndAgingBalance()' "$e2e"
grep -Fq 'listTestTag = "receivables_open_list"' "$crm"
grep -Fq 'rowTestTag = { "receivable_select_${it.id}" }' "$crm"
grep -Fq 'scrollTo("receivables_open_list", "receivable_select_$receivableId")' "$e2e"
grep -Fq 'composeRule.onNodeWithTag("receivable_select_$receivableId").performClick()' "$e2e"
grep -Fq 'receivable_collection_confirm").assertIsEnabled()' "$e2e"
grep -Fq 'entryType == "COLLECTION"' "$e2e"
if grep -Fq 'composeRule.onAllNodesWithText(customerName)[0].performClick()' "$e2e"; then
  echo '::error::stale viewport-dependent CRM click remains in diagnostic candidate'
  exit 1
fi

echo PHASE8_CRM_DIAGNOSTIC_RECONSTRUCTION=PASS
