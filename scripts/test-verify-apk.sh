#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
verify_script="$repo_root/scripts/verify-apk.sh"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

apk_dir="$tmp/out"
mkdir -p "$apk_dir"
printf 'test-apk' > "$apk_dir/app-debug.apk"

make_common_tools() {
  local bin_dir="$1"
  mkdir -p "$bin_dir"
  for tool in dirname basename tee; do
    ln -s "$(command -v "$tool")" "$bin_dir/$tool"
  done
  cat > "$bin_dir/apksigner" <<'SH'
#!/bin/bash
[[ "$1" == "verify" && "$2" == "--verbose" && "$3" == "--print-certs" && -s "$4" ]]
printf '%s\n' "$4" > "$APKSIGNER_CAPTURE"
SH
  chmod +x "$bin_dir/apksigner"
}

sha_bin="$tmp/sha-bin"
make_common_tools "$sha_bin"
cat > "$sha_bin/sha256sum" <<'SH'
#!/bin/bash
[[ "$1" == "app-debug.apk" ]]
printf 'fake-sha256  %s\n' "$1"
SH
chmod +x "$sha_bin/sha256sum"
PATH="$sha_bin" APKSIGNER_CAPTURE="$tmp/apksigner-sha.args" /bin/bash "$verify_script" "$apk_dir/app-debug.apk"
[[ "$(/bin/cat "$tmp/apksigner-sha.args")" == "$apk_dir/app-debug.apk" ]]
[[ "$(/bin/cat "$apk_dir/app-debug.apk.sha256")" == "fake-sha256  app-debug.apk" ]]

shasum_bin="$tmp/shasum-bin"
make_common_tools "$shasum_bin"
cat > "$shasum_bin/shasum" <<'SH'
#!/bin/bash
[[ "$1" == "-a" && "$2" == "256" && "$3" == "app-debug.apk" ]]
printf 'fake-shasum  %s\n' "$3"
SH
chmod +x "$shasum_bin/shasum"
PATH="$shasum_bin" APKSIGNER_CAPTURE="$tmp/apksigner-shasum.args" /bin/bash "$verify_script" "$apk_dir/app-debug.apk"
[[ "$(/bin/cat "$apk_dir/app-debug.apk.sha256")" == "fake-shasum  app-debug.apk" ]]

if PATH="$sha_bin" /bin/bash "$verify_script" "$apk_dir/missing.apk" >/dev/null 2>&1; then
  printf 'Expected missing APK to fail.\n' >&2
  exit 1
fi

printf 'APK verification helper tests passed.\n'
