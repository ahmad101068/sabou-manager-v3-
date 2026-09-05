#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/evidence"
mkdir -p "$OUT"

mapfile -d '' apks < <(find "$ROOT/app/build/outputs/apk" -type f -name '*.apk' -print0 2>/dev/null | sort -z)
mapfile -d '' aabs < <(find "$ROOT/app/build/outputs/bundle" -type f -name '*.aab' -print0 2>/dev/null | sort -z)
[[ ${#apks[@]} -gt 0 ]] || { echo 'ARTIFACT_INSPECTION=FAIL no APK found' >&2; exit 1; }
[[ ${#aabs[@]} -gt 0 ]] || { echo 'ARTIFACT_INSPECTION=FAIL no AAB found' >&2; exit 1; }

ZIPALIGN="${ZIPALIGN:-}"
if [[ -z "$ZIPALIGN" ]]; then
  sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  if [[ -n "$sdk_root" && -d "$sdk_root/build-tools" ]]; then
    ZIPALIGN="$(find "$sdk_root/build-tools" -type f -name zipalign -perm -u+x | sort -V | tail -n 1)"
  fi
fi
[[ -n "$ZIPALIGN" && -x "$ZIPALIGN" ]] || { echo 'ARTIFACT_INSPECTION=FAIL zipalign unavailable' >&2; exit 1; }
command -v readelf >/dev/null || { echo 'ARTIFACT_INSPECTION=FAIL readelf unavailable' >&2; exit 1; }

native_list="$OUT/native-libraries.txt"
: > "$native_list"
abi_list="$OUT/abis.txt"
: > "$abi_list"
elf_report="$OUT/elf-alignment.txt"
: > "$elf_report"
zip_report="$OUT/apk-zip-alignment.txt"
: > "$zip_report"

check_elf_archive() {
  local archive="$1"
  local tmp
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' RETURN
  while IFS= read -r entry; do
    [[ -n "$entry" ]] || continue
    echo "$(basename "$archive"):$entry" >> "$native_list"
    abi="$(awk -F/ '{for(i=1;i<=NF;i++) if($i=="lib" && (i+1)<=NF){print $(i+1); exit}}' <<< "$entry")"
    [[ -n "$abi" ]] && echo "$abi" >> "$abi_list"
    mkdir -p "$tmp/$(dirname "$entry")"
    unzip -p "$archive" "$entry" > "$tmp/$entry"
    while read -r align; do
      [[ -n "$align" ]] || continue
      value=$((align))
      printf '%s:%s LOAD_ALIGN=%s\n' "$(basename "$archive")" "$entry" "$align" >> "$elf_report"
      if (( value < 0x4000 )); then
        echo "ARTIFACT_INSPECTION=FAIL ELF LOAD alignment below 16KB: $entry $align" >&2
        exit 1
      fi
    done < <(readelf -lW "$tmp/$entry" | awk '$1=="LOAD" {print $NF}')
  done < <(zipinfo -1 "$archive" | awk '/(^|\/)lib\/[^/]+\/[^/]+\.so$/')
  rm -rf "$tmp"
  trap - RETURN
}

for apk in "${apks[@]}"; do
  "$ZIPALIGN" -c -P 16 -v 4 "$apk" > "$OUT/zipalign-$(basename "$apk").txt"
  echo "$(basename "$apk")=PASS" >> "$zip_report"
  check_elf_archive "$apk"
done
for aab in "${aabs[@]}"; do
  check_elf_archive "$aab"
done

sort -u "$abi_list" -o "$abi_list"
sort -u "$native_list" -o "$native_list"

bundletool="${BUNDLETOOL_JAR:-}"
if [[ -z "$bundletool" ]]; then
  for base in "${GRADLE_USER_HOME:-}" "$HOME/.gradle"; do
    [[ -d "$base" ]] || continue
    search_dir="$base/caches/modules-2/files-2.1/com.android.tools.build/bundletool"
    [[ -d "$search_dir" ]] || continue
    candidate="$(find "$search_dir" -type f -name 'bundletool-*.jar' -print | sort -V | tail -n 1)"
    if [[ -n "$candidate" ]]; then bundletool="$candidate"; break; fi
  done
fi
[[ -n "$bundletool" && -f "$bundletool" ]] || { echo 'ARTIFACT_INSPECTION=FAIL bundletool unavailable for AAB config check' >&2; exit 1; }

run_bundletool_dump_config() {
  local aab="$1"
  local config="$2"
  if unzip -p "$bundletool" META-INF/MANIFEST.MF 2>/dev/null | grep -q '^Main-Class:'; then
    java -jar "$bundletool" dump config --bundle="$aab" > "$config"
    return
  fi

  local cache_root
  cache_root="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1"
  [[ -d "$cache_root" ]] || {
    echo 'ARTIFACT_INSPECTION=FAIL bundletool dependency classpath unavailable' >&2
    exit 1
  }
  local classpath
  classpath="$(find "$cache_root" -type f -name '*.jar' -print | sort | paste -sd: -)"
  [[ -n "$classpath" ]] || {
    echo 'ARTIFACT_INSPECTION=FAIL bundletool dependency classpath empty' >&2
    exit 1
  }
  java -cp "$classpath" com.android.tools.build.bundletool.BundleToolMain dump config --bundle="$aab" > "$config"
}

for aab in "${aabs[@]}"; do
  config="$OUT/bundle-config-$(basename "$aab").txt"
  run_bundletool_dump_config "$aab" "$config"
  if ! grep -Eq 'PAGE_ALIGNMENT_16K|page_alignment_16k|16K' "$config"; then
    echo "ARTIFACT_INSPECTION=FAIL AAB bundle config does not declare 16KB alignment: $aab" >&2
    exit 1
  fi
done

native_count="$(wc -l < "$native_list" | tr -d ' ')"
abis="$(paste -sd, "$abi_list")"
echo "ARTIFACT_INSPECTION=PASS"
echo "NATIVE_LIBRARIES=$native_count"
echo "ABIS=${abis:-NONE}"
echo "APK_ZIP_ALIGNMENT=PASS_16KB"
echo "AAB_PAGE_ALIGNMENT=PASS_16KB"
echo "ELF_ALIGNMENT=PASS_16KB"
