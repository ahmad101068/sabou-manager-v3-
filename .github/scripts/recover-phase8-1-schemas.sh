#!/usr/bin/env bash
set -euo pipefail
: "${GITHUB_TOKEN:?GITHUB_TOKEN required}"
repo="${REPO:-ahmad101068/sabou-manager-v3-}"
p4="${PHASE4_ARTIFACT_ID:-9443574109}"
p5="${PHASE5_ARTIFACT_ID:-9466675671}"
root="${1:-phase8-1-source}"
mkdir -p history/phase4 history/phase5
curl -fsSL -H "Authorization: Bearer ${GITHUB_TOKEN}" -H 'X-GitHub-Api-Version: 2022-11-28' "https://api.github.com/repos/${repo}/actions/artifacts/${p4}/zip" -o history/phase4.zip
curl -fsSL -H "Authorization: Bearer ${GITHUB_TOKEN}" -H 'X-GitHub-Api-Version: 2022-11-28' "https://api.github.com/repos/${repo}/actions/artifacts/${p5}/zip" -o history/phase5.zip
unzip -q history/phase4.zip -d history/phase4
unzip -q history/phase5.zip -d history/phase5
p4src="$(find history/phase4 -type f -name 'restaurant-management-remediation-phase4-source-*.zip' -print -quit)"
p5src="$(find history/phase5 -type f -name 'restaurant-management-remediation-phase5-source-*.zip' -print -quit)"
test -s "$p4src"; test -s "$p5src"
schema_dir="${root}/app/schemas/ir.restaurant.management.data.db.AppDatabase"
mkdir -p "$schema_dir"
unzip -p "$p4src" 'app/schemas/ir.restaurant.management.data.db.AppDatabase/56.json' > "${schema_dir}/56.json"
unzip -p "$p4src" 'app/schemas/ir.restaurant.management.data.db.AppDatabase/57.json' > "${schema_dir}/57.json"
unzip -p "$p5src" 'app/schemas/ir.restaurant.management.data.db.AppDatabase/58.json' > "${schema_dir}/58.json"
test "$(sha256sum "${schema_dir}/56.json" | awk '{print $1}')" = '3218181977b7fb8079bd478db3eac97c72874628da427d0b39b6ef2fe12f92fd'
test "$(sha256sum "${schema_dir}/57.json" | awk '{print $1}')" = '50eb41e5dc49e6b0f03275510f32c2ef9ac49db0ad157253be0531650cb88894'
test "$(sha256sum "${schema_dir}/58.json" | awk '{print $1}')" = '3ff188efb092b87ecaa6b3db3a4285a1f6749a992e2ea8611e15c61aade0a0d5'
echo AUTHENTIC_ROOM_56_57_58=PASS
