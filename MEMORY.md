# MEMORY.md — Decision log & project memory

## Current state
- Phase 3 done 2026-10-28 (device check blocked). USER PAUSED work; next = Phase 4 data layer when user says continue.
- Gradle memory: Xmx3g + in-process Kotlin (pagefile exhaustion seen with 4g + Kotlin daemons).
- User rules: nothing on C: (F:\DevCache), auto-resume after limit, shutdown PC when all non-blocked work is done.
- Next: Phase 2 foundation (Gradle multi-module).

## User decisions (2026-10-28)
- Target phone: mid/low-tier, no telephoto (model unknown until connected). → high risk on plate pixel width.
- aitmpl: install all relevant components listed in prompt (curated by relevance, see below).
- Git: local repo initialised, no remote, no push.
- ML runtime: **LiteRT** (user chose after explanation).
- User is a beginner; explain ML/Android terms plainly in Turkish.

## Phase 0 audit findings (measured)
- Windows 10 Pro 19045; JDK 21.0.11 (also 17, 8 installed); Python 3.12; Node present; Git present.
- Android SDK at `%LOCALAPPDATA%\Android\Sdk`: platforms android-35, 36, 36.1, 37.0; build-tools 34.0.0–37.0.0; cmdline-tools latest.
- `ANDROID_HOME`/`ANDROID_SDK_ROOT` = `D:\BurgerDukkanim\tools\android-sdk` (does NOT exist) → use `local.properties`.
- No Android Studio. `adb devices`: no device attached.

## Verified dependency versions (latest stable from maven metadata, 2026-10-28)
Sources: dl.google.com/android/maven2, repo1.maven.org, services.gradle.org.
Gradle 9.8.2 · AGP 9.4.1 · Kotlin 2.4.21 · KSP 2.3.12 · Compose BOM 2026.09.00 · CameraX 1.6.2 ·
Room 2.8.5 · DataStore 1.3.0 · WorkManager 2.12.0 · Navigation 2.10.2 · Lifecycle 2.11.0 ·
Activity 1.13.0 · Core 1.19.1 · SplashScreen 1.2.0 · Hilt 2.60.1 · androidx.hilt 1.4.0 ·
LiteRT 2.3.2 · LiteRT support 2.3.2 · ML Kit text-recognition 16.0.1 · Benchmark 1.5.0 ·
ProfileInstaller 1.4.1 · Biometric 1.1.0 · Coroutines 1.11.0 · Serialization 1.12.0 · Coil 3.6.3 ·
Turbine 1.2.1 · MockK 1.14.11 · Robolectric 4.17 · JUnit 4.13.2 · Truth 1.4.5 · Detekt 1.23.8 ·
Spotless 8.10.4 · ktlint 1.8.0.
Open question: Detekt 1.23.8 compatibility with Kotlin 2.4 → verify in Phase 2 build.

## Installed aitmpl components (`npx claude-code-templates@latest`, 2026-10-28)
Catalog source: https://www.aitmpl.com/components.json (1,900+ components, filtered by prompt keywords + relevance).
- **Agents** (.claude/agents): kotlin-specialist, mobile-developer, mobile-app-developer (Ph 2–8 Android code);
  computer-vision-engineer, ml-engineer, machine-learning-engineer, ai-engineer, data-scientist (Ph 5–6 models/eval);
  ui-ux-designer, accessibility-tester (Ph 2, 8); code-reviewer, architect-reviewer, refactoring-specialist (every phase close);
  debugger, performance-engineer (Ph 6–10); qa-expert, test-automator (all phases); security-auditor,
  supply-chain-security (Ph 5, 10); technical-writer, documentation-engineer, changelog-generator, project-manager (docs/phases).
- **Skills**: skill-creator, kotlin-coroutines-expert, android-cicd, mobile-design, accessibility-auditor,
  senior-computer-vision, senior-ml-engineer, architecture-decision-records, senior-qa, code-reviewer,
  testing-patterns, test-driven-development, senior-security, security-threat-model, data-privacy-compliance,
  performance-profiling, changelog-generator, planning-with-files.
- **Commands**: code-review, security-audit, dependency-audit, write-tests, test-coverage, architecture-review,
  add-to-changelog, update-docs.
- **Hooks** (.claude/settings.local.json): conventional-commits, dangerous-command-blocker, secret-scanner, force-push-blocker.
- **MCP** (.mcp.json): context7 (docs lookup), android-mcp (ADB device control, via uvx + Python 3.13).

### Excluded / modified (with reason)
- Excluded web/React/GraphQL/cloud/pentest components (irrelevant to an offline Android app).
- Excluded hooks `auto-git-add`, `smart-commit`, `git-add-changes`, `run-tests-after-changes`: conflict with the
  phase-close commit protocol / would run Gradle on every edit.
- **force-push-blocker**: shipped inline hook used an `if` filter that was ignored → it denied EVERY Bash command.
  Replaced with `.claude/hooks/force-push-blocker.py` (regex on the actual command). Tested deny + allow.
- **dangerous-command-blocker**: relative script path → changed to `$CLAUDE_PROJECT_DIR`.
- **context7**: removed placeholder `CONTEXT7_API_KEY` (key optional; placeholder would be sent as a bogus key).
- **android-mcp**: `uvx` was missing → installed `uv` 0.13.6 via `pip --user`; .mcp.json uses absolute uvx path.

## Resolved problems
- See "Excluded / modified" above.
- Plugins on build-logic classpath can't be used via `plugins { id(...) }` without a version → use convention plugins or `apply(plugin = ...)`.
- AndroidX core 1.19.1 etc. require compileSdk 37 → compileSdk/targetSdk 37 (ADR-002).
- detekt compose-rules: `CompositionLocalAllowlist.allowedCompositionLocals` rejected as invalid property → removed override.
- Heredocs with Turkish text break Git Bash parsing here → write such files with the Write tool.
- Temp Gradle 9.8.2 dist in %TEMP%\gradle-dist (≈0.3 GB on C:) is locked by an idle daemon; delete later.
- VS Code extension does not run statusLine → limit hook has no data; per-minute resume cron used instead.

## Open risks
- Plate pixel width with mid-tier phone at 10–40 m (GO/NO-GO in Phase 9).
- No verified, permissively licensed Turkish plate OCR model yet (Phase 5 research).
- Device-specific behaviour (OEM battery killers, screen-off camera) untestable until phone is connected.

## User feedback / test results
- (none yet)
