#!/usr/bin/env bash
# Fails on modified, deleted, or untracked files under app/schemas.
set -euo pipefail

schema_status="$(git status --porcelain=v1 --untracked-files=all -- app/schemas)"
if [[ -n "${schema_status}" ]]; then
  echo "Schema drift: uncommitted changes under app/schemas:"
  printf '%s\n' "${schema_status}"
  exit 1
fi

db_version="$(
  grep -E 'version\s*=\s*[0-9]+' app/src/main/java/com/familyhub/data/local/db/FamilyHubDatabase.kt \
    | head -1 \
    | grep -oE '[0-9]+'
)"
schema_file="app/schemas/com.familyhub.data.local.db.FamilyHubDatabase/${db_version}.json"

if [[ ! -f "${schema_file}" ]]; then
  echo "Missing schema JSON for current DB version ${db_version}: ${schema_file}"
  exit 1
fi

git ls-files --error-unmatch "${schema_file}" >/dev/null

echo "Schema drift guard OK for tracked version ${db_version} (${schema_file})"
