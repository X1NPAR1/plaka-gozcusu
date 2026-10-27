# CLAUDE.md — Plaka Gözcüsü

## Language rule (HIGHEST PRIORITY)
**Always speak Turkish to the user** (questions, reports, plans, test instructions, errors). App UI is Turkish.
Code, identifiers, commit messages and technical docs may be English. `docs/DEVICE_SETUP.md`,
`docs/TEST_PLAN.md` (manual part), `docs/USER_GUIDE.md`, `docs/PRIVACY_AND_DATA_RETENTION.md` are Turkish.

## Session start
1. Read `CLAUDE.md`, `MEMORY.md`, `docs/PROGRESS.md`. 2. Continue from the current phase.
If context fills up: update `MEMORY.md` + `docs/PROGRESS.md` first. Full spec: `prompt.md`.

## Mission
Android app; phone mounted at home, watching the road. On-device only: detect vehicles, track, read
Turkish plates, save scene still + vehicle crop + plate crop + honest recognition status + timestamps
+ SHA-256. Fast incident search by time window and verifiable evidence ZIP export.
Out of scope: speed measurement, owner identification, cloud, telemetry.

## Hard rules
- Never invent results, metrics, checksums, versions, licenses. Uncertain OCR stays uncertain; raw OCR always kept.
- Every phase ends buildable + tested. Phase-close protocol (`phase-close` skill) after every phase, then auto-advance.
- No `GlobalScope`, no main-thread blocking, no silent catch, no magic numbers. Every `ImageProxy` closed.
- No INTERNET permission in release. No plate text in logs/notifications by default.
- No real personal plates in the repo. Private eval data → `datasets/private/` (git-ignored).
- Local commits only (Conventional Commits). Never push unless the user asks.
- Feature modules depend on `:vision:api`, never on concrete model adapters.

## Toolchain (verified 2026-10-28)
Windows 10, JDK 21.0.11, Android SDK `C:\Users\krmkr\AppData\Local\Android\Sdk` (platforms 35–37,
build-tools 37.0.0). **`ANDROID_HOME` env var points to a non-existent path** → `local.properties` sdk.dir is used.
No Android Studio. Python 3.12, Node, `uv`/`uvx` (user Scripts dir). Versions: see `gradle/libs.versions.toml` and `MEMORY.md`.

## Commands
```
gradlew.bat spotlessApply                 # format
gradlew.bat spotlessCheck detekt lint     # static analysis
gradlew.bat testDebugUnitTest             # unit tests
gradlew.bat assembleDebug                 # build
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s PlakaGozcusu:*              # app tag only, no plate text
```

## Architecture (see docs/ARCHITECTURE.md)
`:app`, `:core:{common,designsystem,model,data,database,storage,datastore}`,
`:feature:{monitor,records,detail,incident,settings,onboarding}`,
`:vision:{api,runtime,pipeline,models}`, `:benchmark`, `build-logic` convention plugins.
ML runtime: LiteRT (user decision). Camera: CameraX + Camera2 interop in a camera-type foreground service.

## Phases (current phase in docs/PROGRESS.md)
0 Audit/plan · 1 Setup/docs · 2 Foundation · 3 Camera · 4 Data · 5 Models · 6 Vision pipeline ·
7 Monitoring workflow · 8 Product UI · 9 Device pilot (user) · 10 Hardening · 11 Release

## Project skills (`.claude/skills/`)
| Skill | When |
|---|---|
| `android-verify` | Before every commit / phase close: format, detekt, lint, unit tests, build |
| `device-test` | Device connected: install APK, launch, filtered logcat, screenshots |
| `model-integration` | Adding/replacing any ML model |
| `phase-close` | End of every phase |
| `turkish-test-guide` | Writing any manual test step for the user |

Installed aitmpl agents/skills/commands/hooks/MCPs are listed in `MEMORY.md`. Use `code-reviewer` agent at
every phase close, `security-auditor` in Phase 10, `computer-vision-engineer`/`ml-engineer` in Phase 5–6.

## Disk rule (user, 2026-10-28)
**Never install anything on C:** (only ~20 GB free). All caches/tools live on F:
`GRADLE_USER_HOME=F:\DevCache\gradle-home`, `UV_CACHE_DIR`, `UV_PYTHON_INSTALL_DIR`, `npm_config_cache` under
`F:\DevCache\` (set as user env vars; also pass explicitly in commands since the running shell predates them).

## Session limit guard (user, 2026-10-28)
`.claude/limit-guard/statusline.py` samples `rate_limits` every 60 s → `state.json` + `usage.log`.
`.claude/limit-guard/guard_hook.py` (PreToolUse, all tools) denies work at >= 98 % of the 5-hour/7-day limit.
When denied: update MEMORY.md + docs/PROGRESS.md, CronCreate one-shot at reset + 1 min with prompt
"Kaldığın yerden profesyonel bir şekilde devam et", end the turn, do NOT wait for user approval.
On that prompt: re-read CLAUDE.md, MEMORY.md, docs/PROGRESS.md and continue exactly where work stopped.
**Measured limitation:** the VS Code extension does not run the status line (no state.json was ever written),
so the 98 % hook only works in the terminal CLI. Fallback active in VS Code: session cron job (every minute,
fires only while idle) sending the resume prompt, so work restarts within ~1 min after a limit reset.
Delete it (CronList/CronDelete) before shutting down or when deliberately waiting for the user.
