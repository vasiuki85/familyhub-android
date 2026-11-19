# Agent Guidelines

Scope: entire repository.

Encoding and text
- Always save files in UTF-8 with LF line endings.
- Do not introduce Windows-1251 (CP1251) or other legacy encodings anywhere (including CSV/JSON test assets). If you need Cyrillic, store it in plain UTF-8.
- Gradle is already configured to use UTF-8 via `-Dfile.encoding=UTF-8`. Do not remove these flags.
- When reading unknown external text, prefer explicit charset handling. For Kotlin/Java I/O always pass an explicit `Charset`, defaulting to `StandardCharsets.UTF_8`.
- If you must handle legacy user data with mojibake, use `com.familyhub.util.EncodingUtils.fixMojibakeIfNeeded()` to heuristically repair strings, and confine fixes to mapping layers.

Compose and navigation
- Keep bottom navigation to 3–5 top-level destinations. Overflow screens must be grouped under a “More” entry that opens a modal sheet with a clear grid of the remaining destinations.
- Do not create multi-row bottom nav bars.

General
- Follow Kotlin official code style. Prefer immutable data and pure mappers.
- Do not hardcode strings in UI; use `strings.xml`.

Localization
- Default locale (`app/src/main/res/values/`) must be **English only** (no Cyrillic).
- Russian translations live in `app/src/main/res/values-ru/` with **matching string keys**.
- Add new strings to both locales in the same PR; split by feature file (`strings_nav.xml`, `strings_errors_safe.xml`, etc.).
- CI runs `.github/scripts/guard-default-locale-strings.sh` to enforce parity and block Cyrillic in `values/`.

