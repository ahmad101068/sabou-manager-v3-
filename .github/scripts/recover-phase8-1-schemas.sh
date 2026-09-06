#!/usr/bin/env bash
set -euo pipefail
: "${GITHUB_TOKEN:?GITHUB_TOKEN required}"
repo="${REPO:-ahmad101068/sabou-manager-v3-}"
artifact_id="${PHASE8_1_FINAL_ARTIFACT_ID:-9671596355}"
verified_head="5d9f1bda813225edf2f0b82cbc3ef6599e4c5017"
verified_source_sha256="19d6c3e992e18e71a8a94f44705e259d7c93e86baf2d171f21bb2108682a17bc"
root="${1:-phase8-1-source}"
work="history/phase8-1-final"
mkdir -p "$work"

# Recover the Room history only from the retained artifact produced by the
# successful Phase 8.1 Final Production Verification for verified_head.
curl -fsSL \
  -H "Authorization: Bearer ${GITHUB_TOKEN}" \
  -H 'X-GitHub-Api-Version: 2022-11-28' \
  "https://api.github.com/repos/${repo}/actions/artifacts/${artifact_id}/zip" \
  -o "$work/artifact.zip"
unzip -q "$work/artifact.zip" -d "$work/artifact"
source_zip="$(find "$work/artifact" -type f -name "restaurant-management-production-final-source-${verified_head:0:7}.zip" -print -quit)"
test -s "$source_zip"
test "$(sha256sum "$source_zip" | awk '{print $1}')" = "$verified_source_sha256"

schema_dir="${root}/app/schemas/ir.restaurant.management.data.db.AppDatabase"
mkdir -p "$schema_dir"
for v in 56 57 58 60; do
  unzip -p "$source_zip" "app/schemas/ir.restaurant.management.data.db.AppDatabase/${v}.json" > "${schema_dir}/${v}.json"
done

test "$(sha256sum "${schema_dir}/56.json" | awk '{print $1}')" = '3218181977b7fb8079bd478db3eac97c72874628da427d0b39b6ef2fe12f92fd'
test "$(sha256sum "${schema_dir}/57.json" | awk '{print $1}')" = '50eb41e5dc49e6b0f03275510f32c2ef9ac49db0ad157253be0531650cb88894'
test "$(sha256sum "${schema_dir}/58.json" | awk '{print $1}')" = '3ff188efb092b87ecaa6b3db3a4285a1f6749a992e2ea8611e15c61aade0a0d5'
test "$(sha256sum "${schema_dir}/60.json" | awk '{print $1}')" = '209deaa5ffa9bb562609395d6cdbf09dc70a6fd7105c32693939e82db81e5389'
echo AUTHENTIC_ROOM_56_57_58_60=PASS
