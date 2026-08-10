#!/usr/bin/env bash

set -euo pipefail

workspace="${GITHUB_WORKSPACE:-$(pwd)}"
log_dir="${workspace}/_ci/logs"
project_root="${workspace}/_ci/project"
mkdir -p "${log_dir}"

requested="${SABOU_SOURCE_ZIP:-}"
source_zip=""

if [[ -n "${requested}" ]]; then
  if [[ "${requested}" = /* ]]; then
    source_zip="${requested}"
  else
    source_zip="${workspace}/${requested}"
  fi
  if [[ ! -f "${source_zip}" ]]; then
    echo "::error::Requested Sabou source ZIP does not exist: ${requested}"
    exit 1
  fi
else
  mapfile -t source_zips < <(
    find "${workspace}" -maxdepth 1 -type f \
      -name 'Sabou-Restaurant-ERP-Alpha*.zip' \
      -print | sort -V
  )

  if [[ ${#source_zips[@]} -eq 0 ]]; then
    echo "::error::No Sabou-Restaurant-ERP-Alpha*.zip source archive was found in the repository root."
    exit 1
  fi

  source_zip="${source_zips[$((${#source_zips[@]} - 1))]}"
  if [[ ${#source_zips[@]} -gt 1 ]]; then
    echo "::warning::Multiple Sabou source ZIPs were found. CI will build the highest version according to sort -V: $(basename "${source_zip}")"
    printf '  candidate: %s\n' "${source_zips[@]}"
  fi
fi

rm -rf "${project_root}"
mkdir -p "${project_root}"
unzip -q "${source_zip}" -d "${project_root}"

mapfile -t wrappers < <(find "${project_root}" -type f -name gradlew -print | sort)
if [[ ${#wrappers[@]} -ne 1 ]]; then
  echo "::error::Expected exactly one Gradle wrapper after extraction; found ${#wrappers[@]}."
  printf '%s\n' "${wrappers[@]}"
  exit 1
fi

project_dir="$(cd "${wrappers[0]%/gradlew}" && pwd)"
test -f "${project_dir}/settings.gradle.kts" || {
  echo "::error::settings.gradle.kts was not found beside gradlew."
  exit 1
}
test -d "${project_dir}/app" || {
  echo "::error::Android app module was not found beside the project settings."
  exit 1
}
test -f "${project_dir}/gradle/wrapper/gradle-wrapper.properties" || {
  echo "::error::Gradle wrapper properties were not found."
  exit 1
}

chmod +x "${project_dir}/gradlew"

source_sha256="$(sha256sum "${source_zip}" | awk '{print $1}')"
{
  echo "selected_source=$(basename "${source_zip}")"
  echo "source_sha256=${source_sha256}"
  echo "project_dir=${project_dir}"
  echo "gradle_distribution=$(grep '^distributionUrl=' "${project_dir}/gradle/wrapper/gradle-wrapper.properties" || true)"
} | tee "${log_dir}/01-source-selection.log"

if [[ -n "${GITHUB_ENV:-}" ]]; then
  {
    echo "PROJECT_DIR=${project_dir}"
    echo "SOURCE_ZIP=${source_zip}"
    echo "SOURCE_SHA256=${source_sha256}"
  } >> "${GITHUB_ENV}"
fi
