#!/usr/bin/env bash
set -euo pipefail

apk_path="${1:-app/build/outputs/apk/debug/app-debug.apk}"
if [[ ! -s "$apk_path" ]]; then
  printf 'APK is missing or empty: %s\n' "$apk_path" >&2
  exit 1
fi

if command -v apksigner >/dev/null 2>&1; then
  apksigner_bin="$(command -v apksigner)"
else
  sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [[ -z "$sdk_root" || ! -d "$sdk_root/build-tools" ]]; then
    printf 'Set ANDROID_HOME or ANDROID_SDK_ROOT to an Android SDK with Build Tools.\n' >&2
    exit 2
  fi
  if sort -V </dev/null >/dev/null 2>&1; then
    sort_args=(-V)
  else
    sort_args=(-t. -k1,1n -k2,2n -k3,3n)
  fi
  apksigner_bin="$(
    find "$sdk_root/build-tools" -mindepth 2 -maxdepth 2 -type f -name apksigner -print |
      awk -F/ '{ print $(NF-1) "\t" $0 }' |
      sort "${sort_args[@]}" |
      tail -n 1 |
      cut -f2-
  )"
fi

if [[ -z "${apksigner_bin:-}" || ! -x "$apksigner_bin" ]]; then
  printf 'Could not find an executable apksigner in PATH or Android SDK Build Tools.\n' >&2
  exit 2
fi

"$apksigner_bin" verify --verbose --print-certs "$apk_path"
(
  cd "$(dirname "$apk_path")"
  if command -v sha256sum >/dev/null 2>&1; then
    checksum_command=(sha256sum)
  elif command -v shasum >/dev/null 2>&1; then
    checksum_command=(shasum -a 256)
  else
    printf 'Install sha256sum or shasum to create the APK checksum.\n' >&2
    exit 2
  fi
  "${checksum_command[@]}" "$(basename "$apk_path")" | tee "$(basename "$apk_path").sha256"
)
