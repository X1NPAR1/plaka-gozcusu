# Project Plan — Plaka Gözcüsü

Source of truth for scope: `prompt.md`. Status: `docs/PROGRESS.md`.

## Phases, deliverables, acceptance criteria

| # | Scope | Key tasks | Acceptance | Test commands |
|---|---|---|---|---|
| 0 | Audit + plan | Toolchain/device inspection, plan | Plan approved (2026-10-28) | — |
| 1 | Setup | aitmpl, CLAUDE/MEMORY, skills, docs, ADRs | All files exist, components recorded | — |
| 2 | Foundation | `build-logic` convention plugins, version catalog, all modules, design system (tokens, M3 theme, dark default), navigation graph, onboarding shell, splash, Spotless/Detekt/Lint | `assembleDebug`, `spotlessCheck detekt lint testDebugUnitTest` pass | android-verify |
| 3 | Camera | `CameraController` interface + CameraX impl, permission flow, Preview/Analysis/Capture binding + fallbacks, zoom, ROI model, tap focus / AF lock, exposure comp, coordinate transforms, `SafeImageProxy`, capability report, FakeCameraController, ring buffer | Transform + permission + ring-buffer unit tests; on-device preview (or ⛔ no device) | android-verify, device-test |
| 4 | Data | Room entities/DAOs/FTS4, schema export + migration tests, `EvidenceFileStore` atomic writes + SHA-256, orphan cleanup, retention/cap WorkManager worker, protect flag, DataStore settings | DAO, migration, storage, retention tests pass | android-verify |
| 5 | Models | Candidate research (license-first), download from official sources, I/O inspection, adapters, golden tests, benchmark module | MODEL_CARD complete, golden tests pass, latency measured or ⛔ | model-integration |
| 6 | Pipeline | FrameScheduler, preprocessing, tracker (SORT-like + Kalman), motion + scene-shift, plate detect, OCR, TurkishPlateParser, consensus, quality, ranker, still capture controller, finalizer, dedup, replay harness | Unit + integration + replay tests pass | android-verify |
| 7 | Monitoring | Camera FGS, session state machine, watchdog, thermal adaptation, persistence E2E, notifications, boot "resume" flow | Replay E2E produces correct observations; lifecycle tests | android-verify |
| 8 | UI | Dashboard, live view + overlays + dim mode, timeline/search/filters, Incident Finder, detail (zoom, hashes, correction), evidence ZIP (Turkish HTML report + JSON manifest), settings, a11y, motion | Compose UI tests pass; screenshots reviewed | android-verify |
| 9 | Device pilot | Turkish guides A–L, GO/NO-GO plate pixel width | Results recorded honestly | device-test, turkish-test-guide |
| 10 | Hardening | Profiling, adaptive defaults, Baseline Profiles, security/privacy audit, agent code review | Measured improvements, findings closed | security-audit |
| 11 | Release | Debug + unsigned release, docs complete, final report | Section 22 met or ⛔ with evidence | android-verify |

Every phase ends with the `phase-close` skill and auto-advances.

## Risk register

| ID | Risk | Type | Likelihood/Impact | Mitigation |
|---|---|---|---|---|
| R1 | Mid-tier phone, no telephoto: plate too few pixels at 10–40 m | Hardware | High/High | Max-res stills, digital zoom on ROI, vehicle-crop plate detection, GO/NO-GO experiment, document closer mounting / telephoto phone / IP camera |
| R2 | No permissively licensed Turkish plate detector/OCR | Technical/legal | High/High | ML Kit baseline, license-first search, documented fine-tuning pipeline, honest NEEDS_REVIEW status |
| R3 | Popular detectors are AGPL (e.g. Ultralytics) | Legal | High/Med | Reject; use Apache/MIT models only, record in ADR |
| R4 | OEM battery killers / Doze stop the service | Platform | Med/High | Camera FGS, wakelock policy, Turkish OEM guide, watchdog, unexpected-stop notification |
| R5 | Thermal throttling while charging in sun | Hardware | Med/High | Thermal listener → lower rate/resolution → pause with warning |
| R6 | Night glare/headlights | Environment | High/Med | Exposure compensation, AE lock option, night evaluation split |
| R7 | No device available for long stretches | Schedule | Med/Med | Replay harness, fakes; device criteria marked ⛔ with evidence |
| R8 | Detekt 1.23.x vs Kotlin 2.4 incompatibility | Toolchain | Med/Low | Verify in Phase 2; fall back to Detekt 2.x or ADR exception |
| R9 | Storage growth fills device | Product | Med/High | Cap + retention worker + low-storage degradation |
| R10 | Privacy/KVKK concerns (recording public road) | Legal | Med/High | Purpose limitation, ROI, local-only, retention, guidance; no compliance claims |
| R11 | `ANDROID_HOME` misconfigured | Toolchain | Certain/Low | `local.properties`; documented in CLAUDE.md |
| R12 | Coordinate mapping errors between analysis/still | Technical | Med/High | Unit-tested transforms, golden tests, timestamp matching |
