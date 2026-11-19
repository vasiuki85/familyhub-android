#!/usr/bin/env bash
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [[ "${1:-}" == "--self-test" ]]; then
  exec python3 "${script_dir}/verify_android_test_results.py" --self-test
fi
exec python3 "${script_dir}/verify_android_test_results.py" "$@"
