#!/usr/bin/env bash
# Native 16 KiB page-size guard for Android APKs (zipalign -P 16 + ELF PT_LOAD).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec python3 "${SCRIPT_DIR}/verify_native_page_size.py" "$@"
