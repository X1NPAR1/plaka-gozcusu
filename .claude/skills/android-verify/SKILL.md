---
name: android-verify
description: Run the full local verification for Plaka Gözcüsü (Spotless format check, Detekt, Android Lint, unit tests, debug build) and summarize real results. Use before every commit, at every phase close, and whenever asked to "verify", "check the build" or "run the tests".
---

# android-verify

Run from the repo root (Windows: `gradlew.bat`, Git Bash: `./gradlew`). Use `--continue` so one failure
does not hide others.

1. `./gradlew spotlessApply` then `./gradlew spotlessCheck`
2. `./gradlew detekt`
3. `./gradlew lintDebug`
4. `./gradlew testDebugUnitTest test` (Android + pure JVM modules)
5. `./gradlew assembleDebug`

## Report (to the user, in Turkish)
Table: | Adım | Durum | Ayrıntı |
- Tests: count **passed / failed / skipped separately**:
  `python -I .claude/skills/android-verify/count_tests.py`
- Never report a step as passing unless it ran in this session and exited 0.
- New warnings count as failures (zero-new-warnings policy).
- On failure: show the first real error lines, fix it if current work introduced it, re-run.
