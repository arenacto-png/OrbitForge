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
  apksigner_bin="$(find "$sdk_root/build-tools" -type f -name apksigner -print | sort -V | tail -n 1)"
fi

if [[ -z "${apksigner_bin:-}" || ! -x "$apksigner_bin" ]]; then
  printf 'Could not find an executable apksigner in PATH or Android SDK Build Tools.\n' >&2
  exit 2
fi

"$apksigner_bin" verify --verbose --print-certs "$apk_path"
(
  cd "$(dirname "$apk_path")"
  sha256sum "$(basename "$apk_path")" | tee "$(basename "$apk_path").sha256"
)
