# MASTER ENGINEERING PROMPT
# "Plaka Gözcüsü" — Android Edge-Based Vehicle Observation & License Plate Recognition System

> **LANGUAGE RULE (HIGHEST PRIORITY):** This prompt is written in English for precision. **Every message you send to the user — questions, phase reports, plans, test instructions, error explanations, summaries — MUST be written in Turkish.** The application UI is Turkish. Source code, identifiers, commit messages, and conventional technical documentation may be in English. User-facing guides (`docs/DEVICE_SETUP.md`, `docs/TEST_PLAN.md`, `docs/USER_GUIDE.md`) are written in Turkish.

---

## 0. MISSION BRIEF

The user will mount an Android phone in a fixed position at their home (window, balcony, or similar authorized vantage point) overlooking the road and the area in front of the house. The application must continuously and autonomously:

1. Observe vehicles passing on the road.
2. Detect and recognize each vehicle's license plate (Turkish plates).
3. Save, for every vehicle observation:
   - a **high-resolution full-scene photograph** of the moment,
   - a **vehicle crop** (the vehicle itself),
   - a **plate crop** (the plate region),
   - the **recognized plate text** (or an honest "uncertain/unreadable" status),
   - precise timestamps and confidence/quality metadata.
4. Make it **fast and easy to find a vehicle later** — for example, if someone enters the property, if there is an accident in front of the house, or if the user's parked car is hit. The user must be able to say "show me every vehicle between 14:00 and 14:30 yesterday" and get a clear, visual timeline within seconds, then export an evidence package.

The application processes everything **on the device**. No cloud, no external services, no identity lookups.

This is a **production-grade engineering project**, not a demo. You will think, plan, design, implement, test, and document like a senior engineering team.

---

## 1. YOUR ROLE AND STANDARDS

Act simultaneously as:
- **Senior Android Engineer** (Kotlin, Jetpack Compose, CameraX, Camera2 interop, lifecycle, services, power management)
- **Computer Vision / ML Engineer** (object detection, tracking, OCR, on-device inference, quantization, model evaluation)
- **Software Architect** (clean architecture, modularization, testability, failure isolation)
- **Senior Product / UI-UX Designer** (professional, modern, polished, accessible Material 3 design)
- **QA Engineer** (test strategy, automation, real-device protocols, metrics)
- **Security & Privacy Engineer** (local data protection, evidence integrity, retention)
- **Technical Project Lead** (phased delivery, risk management, honest reporting)

### 1.1 Non-negotiable standards
- **No superficial work.** No mockups, no fake data, no placeholder logic presented as working, no hardcoded plate results.
- **Evidence over assumptions.** Never claim a build, test, model metric, camera capability, accuracy number, license, URL, checksum, or benchmark that you did not actually execute or verify. If something is unknown, state how it will be measured.
- **Verify before you use.** Look up current official documentation (Android Developers, CameraX, LiteRT/TensorFlow Lite, ONNX Runtime, ML Kit, model repositories) before selecting dependencies, APIs, or models. Never rely on remembered version numbers.
- **Incremental and always buildable.** Every phase ends in a buildable, tested state.
- **Honest uncertainty.** An uncertain OCR result is stored as uncertain. A syntactically valid plate string is not proof of a correct reading. Never invent missing characters.
- **Think through every edge case** and write it into the plan. If you discover a new risk or requirement mid-project, add it to the plan and handle it — do not silently skip it.
- **Senior-level code quality:** small cohesive classes, explicit interfaces, immutable UI state, no `GlobalScope`, no blocking the main thread, no silent exception swallowing, no magic numbers, comments explain *why*.

---

## 2. MANDATORY SETUP: AITMPL COMPONENTS, MEMORY FILES, PROJECT SKILLS

### 2.1 Install components from https://www.aitmpl.com/
Before implementation, **thoroughly research https://www.aitmpl.com/** and install every skill, agent, command, hook, and MCP server that materially improves this project's quality. Verify each component's current install syntax on its own page (typically `npx claude-code-templates@latest --<type> <path>`).

Search the site with at least these keywords and install the relevant results:
- **Android / mobile:** `android`, `kotlin`, `mobile`, `jetpack`, `compose`, `gradle`
- **ML / CV:** `machine-learning`, `ml`, `computer-vision`, `ai-engineer`, `data-scientist`, `model`, `tensorflow`, `pytorch`, `onnx`
- **Design:** `ui`, `ux`, `design`, `material`, `accessibility`, `frontend-design`
- **Quality:** `code-review`, `code-reviewer`, `test`, `testing`, `qa`, `debugger`, `performance`, `refactor`
- **Security / privacy:** `security`, `security-auditor`, `privacy`
- **Architecture / docs:** `architect`, `architecture`, `documentation`, `technical-writer`
- **Workflow:** `git`, `planning`, `project-manager`, `changelog`
- **MCP:** any MCP that helps with Android device interaction (ADB), documentation lookup (e.g. Context7-style documentation MCP), filesystem, or GitHub — only if trustworthy and actually useful.

Also install `skill-creator`.

Record every installed component in `MEMORY.md`: name, why it was installed, which phase uses it. Remove any component that conflicts with the project and document why. Use the installed agents actively (e.g. code reviewer after each phase, security auditor in the hardening phase, ML engineer during model selection).

### 2.2 Memory and context files (create first, keep current)
| File | Purpose |
|---|---|
| `CLAUDE.md` | Project mission, **language rule (always speak Turkish to the user)**, senior-level standards, architecture summary, module map, build/test/install commands, toolchain versions, phase list and current phase, hard rules. Read at the start of every session. |
| `MEMORY.md` | Decision log with rationale, verified dependency/model versions and sources, resolved problems and their fixes, open risks, installed aitmpl components, user feedback and test results received, next steps. Updated after every meaningful step. |
| `docs/PROGRESS.md` | Phase-by-phase checklist with status. |

Rules:
- At the start of every session: read `CLAUDE.md`, `MEMORY.md`, `docs/PROGRESS.md`, and continue from where work stopped.
- If context is getting full: update `MEMORY.md` and `docs/PROGRESS.md` first, then continue.

### 2.3 Project-specific skills (`.claude/skills/<name>/SKILL.md`)
Using `skill-creator`, create at least:
- `android-verify` — run formatting, static analysis, unit tests, build; summarize results.
- `device-test` — install debug APK via ADB, launch, collect filtered logcat (no plate text), capture screenshots.
- `model-integration` — checklist for integrating a new model (provenance, license, tensor I/O verification, golden test, benchmark, documentation).
- `phase-close` — execute the phase-closing protocol (Section 17).
- `turkish-test-guide` — produce a manual test step in the required Turkish format (Section 15).

List each skill and when to use it in `CLAUDE.md`.

---

## 3. WORKFLOW: PLAN MODE FIRST, THEN AUTONOMOUS PHASED EXECUTION

### 3.1 Plan Mode
1. **Enter Plan Mode** before modifying anything.
2. In Plan Mode, perform the Phase 0 audit (read-only) and produce the complete plan:
   - architecture, module structure, data flow,
   - technology choices with justification,
   - candidate models and evaluation approach,
   - all phases with tasks, dependencies, acceptance criteria, test commands, deliverables,
   - risk register (technical, hardware, legal/privacy, schedule) with mitigations,
   - every edge case identified (Section 14 is a minimum, not a limit).
3. Present the plan **in Turkish** and exit Plan Mode. (Exiting Plan Mode requires the user's single approval in the Claude Code interface; that is the only plan-level approval.)
4. After exiting, write the plan into the documentation files (Section 4) and begin Phase 1 immediately.

### 3.2 Autonomous phase execution
- Execute phases **strictly in order**.
- When a phase passes its acceptance criteria and the phase-closing protocol, **automatically continue to the next phase without waiting for approval.**
- Stop **only** for genuine blockers that require the user, such as:
  - a physical action (connecting the phone, granting USB debugging, mounting the phone, running a real-world test),
  - missing hardware or toolchain that cannot be installed,
  - a decision with legal, privacy, or cost consequences.
  When blocked, explain in Turkish exactly what is needed, then continue with any other work that is not blocked.
- Real-world device tests (Phase 9) require the user's participation; provide precise Turkish instructions and continue with non-blocked work while waiting.

---

## 4. DOCUMENTATION DELIVERABLES

Create and maintain:
- `README.md` (Turkish summary + English technical sections)
- `docs/PROJECT_PLAN.md` — phases, tasks, acceptance criteria, risks
- `docs/ARCHITECTURE.md` — modules, layers, data flow diagrams (Mermaid/text), threading model, state machines
- `docs/TECHNICAL_DECISIONS.md` — ADR-style records (context, options, decision, consequences)
- `docs/MODEL_CARD.md` — per model (Section 20)
- `docs/MODEL_EVALUATION.md` — evaluation protocol, datasets, metrics, results (only measured ones)
- `docs/TEST_PLAN.md` — automated + manual tests (manual parts in Turkish)
- `docs/TEST_REPORT.md` — actual executed results per phase
- `docs/DEVICE_SETUP.md` — Turkish: installation, mounting, calibration, power, troubleshooting
- `docs/USER_GUIDE.md` — Turkish: daily use, finding an incident, exporting evidence
- `docs/PRIVACY_AND_DATA_RETENTION.md` — what is stored, where, for how long, how to delete; KVKK-aware but no legal guarantees
- `docs/SECURITY.md` — threat model and mitigations
- `docs/PERFORMANCE.md` — benchmark protocol and measured results
- `docs/KNOWN_LIMITATIONS.md`
- `CHANGELOG.md` (Keep a Changelog)

---

## 5. PHASE 0 — ENVIRONMENT AND FEASIBILITY AUDIT (READ-ONLY, IN PLAN MODE)

Inspect and record actual findings:
- Repository: structure, README, Gradle files, version catalog, manifests, existing code/tests, `git status`. Preserve user work. No destructive Git operations.
- Toolchain: OS, JDK version, Android SDK presence and installed platforms/build-tools, Gradle wrapper, AGP version, Android Studio presence.
- Devices: `adb devices`; if a phone is connected, collect model, Android version, SoC/ABI, RAM, available camera IDs and capabilities (via a capability-inspection plan; do not assume).
- Python availability (for model conversion/evaluation tooling), if needed.
- Network access availability (for documentation and model research).

Camera feasibility variables to document (the exact phone is unknown until inspected): sensor resolution, focal length/FOV, optical telephoto availability, digital zoom limits, autofocus/exposure behavior, low-light performance, motion blur at typical speeds, mounting angle, camera-to-road distance (initial assumption 10–40 m), glare from headlights, night-time plate illumination, through-glass reflections, thermal behavior while charging.

Define the **GO / CONDITIONAL GO / NO-GO** feasibility experiment (executed in Phase 9 with the user): plate pixel width in original captures, character legibility, daylight vs. low-light vs. night, preview vs. still quality. The practical minimum plate width threshold is determined **empirically**, not assumed. If the hardware cannot capture enough detail, say so clearly and document alternatives (closer mounting, better angle, phone with telephoto lens, external IP camera in a future version).

---

## 6. TECHNICAL STACK (VERIFY CURRENT VERSIONS BEFORE USE)

| Area | Preferred choice | Notes |
|---|---|---|
| Language | Kotlin | Explicit API mode for core modules if helpful |
| UI | Jetpack Compose + Material 3 | Dynamic color optional; custom brand theme |
| Camera | CameraX (Preview, ImageAnalysis, ImageCapture) + Camera2 interop where needed | Zoom, focus, exposure control |
| Architecture | Clean Architecture + MVVM/MVI, multi-module | See Section 7 |
| DI | Hilt | Justify if replaced |
| Async | Coroutines + Flow, structured concurrency | No `GlobalScope` |
| Database | Room (with migrations, schema export) | |
| Preferences | DataStore | Typed settings |
| Background work | Foreground Service (camera type) for monitoring; WorkManager for retention cleanup | Verify current Android rules |
| ML runtime | LiteRT (TensorFlow Lite) **or** ONNX Runtime Mobile — choose ONE primary runtime based on model compatibility | Avoid two runtimes without evidence |
| Acceleration | CPU (XNNPACK) first; GPU delegate / NNAPI only if measured benefit and stable | Graceful fallback |
| OCR | Dedicated plate OCR model preferred; ML Kit Text Recognition evaluated as baseline | Section 9.5 |
| Image loading | Coil | Thumbnails, caching |
| Testing | JUnit5/JUnit4, Turbine, MockK or fakes, Robolectric (selective), Compose UI tests, instrumentation tests, Macrobenchmark/Baseline Profiles | |
| Static analysis | ktlint/Spotless + Detekt + Android Lint | Zero new warnings policy |
| Build | Gradle version catalog, convention plugins (build-logic) | Reproducible builds |
| Crash/diagnostics | Local only, no third-party telemetry | Section 18 |

Choose `minSdk` based on dependencies and realistic device support (document the trade-off). Use a `compileSdk`/`targetSdk` supported by the installed environment and current Play policy.

---

## 7. ARCHITECTURE

### 7.1 Module structure (adapt if justified)
```
:app                         — application entry, navigation, DI graph
:core:common                 — result types, dispatchers, time abstraction (Clock), logging facade
:core:designsystem           — theme, typography, colors, components, motion tokens
:core:model                  — domain models (Observation, Track, PlateReading, QualityScore…)
:core:data                   — repositories
:core:database               — Room DB, DAOs, migrations
:core:storage                — image file store, atomic writes, integrity hashing, retention
:core:datastore              — settings
:feature:monitor             — live monitoring screen + foreground service
:feature:records             — records list, timeline, search, filters
:feature:detail              — observation detail, image viewer, evidence export
:feature:incident            — incident search ("what happened between X and Y")
:feature:settings            — settings, calibration, storage, privacy
:feature:onboarding          — first-run setup, permissions, mounting/calibration wizard
:vision:api                  — interfaces: VehicleDetector, PlateDetector, PlateOcr, Tracker, QualityEvaluator
:vision:runtime              — model loading, runtime/delegate management, tensor utils
:vision:pipeline             — frame scheduler, preprocessing, tracking, motion, candidate ranking, dedup
:vision:models               — concrete adapters for selected models
:benchmark                   — macrobenchmark + inference microbenchmarks
```
Feature modules depend on `:core:*` and `:vision:api`, never on concrete model adapters.

### 7.2 Processing pipeline (data flow)
```
CameraX ImageAnalysis (KEEP_ONLY_LATEST, bounded executor)
  → FrameScheduler (adaptive analysis rate, thermal-aware)
  → Preprocessor (YUV→RGB/tensor with minimal copies, ROI cropping)
  → VehicleDetector → Tracker (track IDs, lifecycle) → MotionClassifier
  → [for tracks in the region of interest and moving]
      PlateDetector (on vehicle crop) → Plate crop preprocessing (deskew, contrast)
      → PlateOcr → TurkishPlateParser/Validator → multi-frame consensus
  → QualityEvaluator → CandidateRanker (best candidate per track)
  → StillCaptureController (rate-limited high-res capture when a strong candidate appears)
  → TrackFinalizer (on track end / decision threshold)
  → ObservationRepository (Room + file store, atomic, deduplicated)
  → UI (Flow) + optional diagnostic overlay
```

### 7.3 State machines (document and test)
- **Monitoring session:** `Idle → RequestingPermission → InitializingCamera → LoadingModels → Running → Degraded (thermal/perf) → Paused/Interrupted → Stopping → Idle`, plus `RecoverableError` and `FatalError`.
- **Track lifecycle:** `Tentative → Confirmed → (Moving|Stationary) → CandidateCollecting → Finalizing → Persisted → Expired`.
- **Observation recognition status:** `PENDING`, `HIGH_CONFIDENCE` (heuristic, not human-verified), `NEEDS_REVIEW`, `UNREADABLE`, `NO_PLATE_VISIBLE`, `FAILED`, plus `USER_VERIFIED` / `USER_CORRECTED` when the user manually confirms or edits.

### 7.4 Threading
- Inference on dedicated single-thread (or small bounded) dispatchers per model; never on main.
- Bounded queues only; stale frames are dropped, not queued.
- Every `ImageProxy` closed in every path (enforced by a wrapper + tests).

---

## 8. CAMERA SUBSYSTEM

Implement behind a `CameraController` abstraction (fakeable for tests):
- Permission flow (granted / denied / permanently denied with settings deep link).
- Preview + ImageAnalysis + ImageCapture bound together; verify the device supports the use-case combination and resolutions; fallback strategy documented.
- **Resolution strategy:** analysis at a modest resolution (measured), still capture at maximum useful resolution.
- **Zoom:** user-adjustable zoom during calibration (optical lens selection where available via camera ID / zoom ratio); persist settings.
- **Region of Interest (ROI):** user draws the road area during calibration; detection/OCR focus on ROI to save compute and avoid irrelevant areas.
- **Focus & exposure:** tap-to-focus/meter during calibration, option to lock focus at the road distance, exposure compensation for night/headlight glare; document device variance.
- Orientation, rotation, aspect-ratio and coordinate mapping (sensor ↔ analysis ↔ preview ↔ still) implemented with unit-tested transforms.
- Camera interruption (another app, incoming call), recovery, and rebinding.
- Lifecycle-safe binding to the foreground service lifecycle (not only the Activity).
- Still capture policy: rate-limited, cooldown, maximum captures per track, never "capture every frame".
- **Pre-event context (ring buffer):** keep the last N analysis frames (low resolution, memory-bounded) so the observation can include a short "before" sequence. Optionally evaluate a short video clip per observation (e.g. 3–5 s) using CameraX VideoCapture or encoding of buffered frames — only if measured cost is acceptable; otherwise document as future work.

---

## 9. COMPUTER VISION

Every component sits behind an interface in `:vision:api` and is replaceable.

### 9.1 Model selection process (mandatory)
For each candidate (vehicle detector, plate detector, OCR):
1. Provenance, maintainer, last update.
2. **License and redistribution rights** (reject unclear licenses; note that some popular detector families use copyleft/commercial licenses — verify, do not assume).
3. Architecture, input size, preprocessing, output tensor format, postprocessing (NMS, anchors, decoding).
4. Runtime/operator compatibility with the chosen runtime; quantization options (FP16, INT8) and their measured effect.
5. Size, latency, memory on the actual device (measured in `:benchmark`).
6. Suitability for Turkish plates and the expected distances.
7. Integrity: compute SHA-256 of the actual artifact you obtained; never invent checksums.

Record all of this in `docs/MODEL_CARD.md` and the decision in `docs/TECHNICAL_DECISIONS.md`.

### 9.2 Vehicle detection
- Lightweight mobile detector with vehicle classes (car, truck, bus, motorcycle, van if available).
- Configurable confidence and class filters; ROI filtering.
- Golden tests: fixed input image → expected decoded boxes (within tolerance) to verify tensor I/O and coordinate mapping.

### 9.3 Tracking
- Start with IoU/centroid association + simple Kalman-style motion prediction (SORT-like). Upgrade only with evidence.
- Track fields: id, bbox history (bounded), first/last seen, hits, misses, velocity estimate (pixels/s), motion state, ROI membership, direction of travel (toward/away/left/right), best candidates, processing state.
- Handle occlusion, overlapping vehicles, detector dropouts, camera vibration; bounded timeouts.
- Plate text is never the sole track identity.

### 9.4 Motion classification
- Temporal displacement + tracking consistency + minimum track age; configurable thresholds.
- Global scene-motion check (sudden whole-frame change = camera bumped) → pause and warn rather than producing false events.
- Pixel displacement is **not** physical speed; no speed claims.
- Parked/stationary vehicles in view must not generate repeated events. (Optional, configurable: log a single "vehicle stopped in front of the house" event when a vehicle stops within a user-defined zone for longer than N seconds — useful for the security use case.)

### 9.5 Plate detection & OCR
- Dedicated plate detector run on vehicle crops (higher effective resolution). If no suitable verified pretrained model exists, implement the integration boundary, document the gap, and describe a fine-tuning pipeline (dataset requirements, labeling, training, export, evaluation) — **never fabricate datasets or results**.
- Plate crop preprocessing: perspective correction/deskew, upscaling where justified, contrast normalization, single-line vs. two-line plate handling.
- OCR: prefer a dedicated plate-recognition model; evaluate ML Kit Text Recognition as baseline. Select based on measured exact-match accuracy on the evaluation set.
- **Multi-frame consensus:** aggregate OCR results across frames of the same track (character-level voting weighted by confidence and quality). Store per-character confidences where available.

### 9.6 Turkish plate parser/validator
- Normalize: uppercase, remove spaces/separators, map common OCR confusions **only in context-aware, documented ways** (e.g. in the numeric province-code position, `O`→`0` may be considered; in letter positions, digits are not silently converted) — always keep the raw OCR string.
- Validate against **documented and verified** Turkish civilian plate structure: two-digit province code (01–81), followed by a letter group and a number group in the officially used combinations. Research and cite the current rules (allowed letters, letter-count/number-count combinations) before implementing; document sources in `docs/TECHNICAL_DECISIONS.md`.
- Special plates (official, diplomatic, military, temporary, foreign) → do not force into civilian format; store as `NEEDS_REVIEW` with raw text.
- Validation affects status/confidence, never fabricates characters.

### 9.7 Quality evaluation & best-frame selection
Score per candidate: plate detector confidence, OCR confidence, consensus agreement, plate crop pixel width/height, sharpness (e.g. variance of Laplacian on the crop — one signal, not the truth), motion blur estimate, exposure/clipping, glare, completeness (plate fully inside frame), track association confidence.
- Keep the best candidate per track; never replace a better candidate with a worse one.
- Trigger a high-res still capture when the predicted best moment is reached (e.g. vehicle size/plate width peaking while approaching); map the still back to the track by timestamp and bbox.
- Save originals untouched; any annotation is a separate derivative file.

### 9.8 Deduplication
- One observation per vehicle pass (track).
- Cross-track dedup: same plate text (high confidence) within a configurable window → merge or link, never silently drop data.
- Two similar vehicles close together must remain separate (test explicitly).

---

## 10. DATA, STORAGE, AND EVIDENCE INTEGRITY

### 10.1 Room schema (minimum)
**Observation**: `id (UUID)`, `sessionId`, `trackId`, `plateText?`, `rawOcrText?`, `perCharConfidence?`, `plateConfidence`, `detectionConfidence`, `qualityScore`, `recognitionStatus`, `motionStatus`, `direction`, `vehicleClass`, `firstSeenAt`, `bestFrameAt`, `lastSeenAt` (UTC epoch millis + zone id), `sceneImageRef`, `vehicleCropRef`, `plateCropRef?`, `contextFramesRef?`, `videoClipRef?`, `sha256` for each stored file, `modelVersions`, `appVersion`, `userNote?`, `isStarred`, `isProtected` (excluded from auto-deletion), `createdAt`, `updatedAt`.

**MonitoringSession**: `id`, `startedAt`, `endedAt`, `deviceInfo`, `settingsSnapshot`, `stats` (frames analyzed/dropped, vehicles, plates read, uncertain, unreadable), `endReason`.

**DiagnosticEvent** (optional, privacy-safe): `timestamp`, `stage`, `code`, `durationMs`, no plate text.

Indexes on `bestFrameAt`, `plateText`, `recognitionStatus`, `isProtected`. Full-text search (FTS4/FTS5 if supported) or prefix/fuzzy search on plate text (partial plate search, e.g. "34 AB" or "…123").

### 10.2 File store
- App-private storage; directory layout by date; JPEG with controlled quality (original still), WebP/JPEG thumbnails.
- **Atomic writes** (temp file → fsync → rename), transaction coordination with Room, orphan cleanup job, missing-file handling in UI.
- **SHA-256 hash** computed and stored at save time for every evidence file; verification available in the detail screen and export.
- EXIF: keep capture timestamp; strip location (no location collected).

### 10.3 Retention & storage management
- Configurable retention period (default sensible, e.g. 30 days) and storage cap; oldest non-protected records deleted first by WorkManager.
- **"Koru" (Protect)** flag: records related to an incident are never auto-deleted.
- Storage usage dashboard; low-storage warning; monitoring degrades gracefully (e.g. stops saving context frames) before failing.

### 10.4 Evidence export
- From detail or incident screen: export selected observations as a ZIP containing original images, crops, a human-readable Turkish report (PDF or HTML) with timestamps, recognition status, confidence, and SHA-256 hashes, plus a machine-readable JSON manifest.
- Share via Android Storage Access Framework / share sheet only on explicit user action. No automatic sharing.
- Clearly state in the report that OCR results are automated and may be wrong.

---

## 11. USER INTERFACE AND DESIGN (TURKISH, PROFESSIONAL)

### 11.1 Design direction
- **Professional, modern, premium, calm** — a security/monitoring product feel. Material 3 with a custom brand theme (dark theme default for night monitoring, light theme available).
- Consistent design system in `:core:designsystem`: color tokens, typography scale, spacing, shape (rounded surfaces), elevation, iconography, motion tokens.
- Purposeful motion: smooth screen transitions, list item enter animations, shared-element transition from record thumbnail to detail image, subtle status-pulse for live monitoring. Animations must never slow down the monitoring pipeline; respect system "remove animations".
- Accessibility: TalkBack labels, minimum touch targets, contrast, scalable fonts, no color-only meaning.
- App icon and splash screen (Android 12+ SplashScreen API) designed to match the brand.
- Clean empty states, loading skeletons, actionable error states. **No fabricated numbers anywhere.**

### 11.2 Screens
1. **Onboarding / Kurulum sihirbazı:** purpose and privacy explanation, permissions, notification permission (for the foreground service), battery optimization guidance, **mounting & calibration wizard** (zoom, ROI drawing, focus lock, test capture with plate-width readout and GO/WARN indicator).
2. **Ana ekran (Dashboard):** monitoring status, start/stop, session duration, today's counts (vehicles, plates read, uncertain, unreadable), storage usage, thermal/performance status, last 5 observations with thumbnails, clear recovery instructions on errors.
3. **Canlı izleme:** camera preview; optional overlays (vehicle boxes, track IDs, plate boxes, motion state, ROI outline, FPS/latency in diagnostic mode); "dim screen" mode for long sessions (preview off / black screen with minimal status while processing continues, to save power and reduce burn-in).
4. **Kayıtlar (Timeline):** chronological timeline grouped by day/hour with thumbnails; search by full or partial plate; filters (date/time range, status, direction, vehicle class, starred, protected); sort; multi-select delete/protect/export.
5. **Olay Bul (Incident Finder):** pick a date and time window (with quick presets like "son 1 saat", "dün 14:00–15:00"); shows a visual timeline/scrubber of every observation in that window, including uncertain/unreadable ones; one tap to protect all and export an evidence package.
6. **Kayıt detayı:** zoomable full-scene image, vehicle crop, plate crop, context frames (and clip if implemented), plate text with status badge and confidence, raw OCR text, timestamps, direction, hash verification, user note, manual correction ("Plakayı düzelt" → `USER_CORRECTED`, original preserved), protect/star/delete/export.
7. **Ayarlar:** analysis load (Düşük/Dengeli/Yüksek), motion threshold, confidence and quality thresholds, duplicate window, still capture policy, context frames/clip on/off, retention period, storage cap, overlays, theme, recalibration, diagnostics export, privacy info, model and app versions, licenses (open-source notices).

### 11.3 Notifications
- Ongoing foreground-service notification (required) with status and a stop action.
- Optional local notification for warnings (camera interrupted, storage low, overheating, monitoring stopped unexpectedly). No plate text in notifications by default.

---

## 12. BACKGROUND EXECUTION, POWER, AND LONG-RUN STABILITY

- Research the **current** official rules for foreground services with `camera` type, required permissions, notification requirements, and restrictions on starting camera use from the background for the target SDK. Implement accordingly and cite the docs in `TECHNICAL_DECISIONS.md`.
- Monitoring runs as an explicit user-started session inside a camera-type foreground service so it can continue with the screen off where the platform allows; verify actual behavior on the user's device and document it.
- Handle: Home pressed, screen off, device locked, app swiped away, process death, device reboot (offer a clear "resume monitoring" flow; do not silently auto-start the camera after boot unless permitted and explicitly enabled by the user), battery saver, Doze, OEM battery killers (provide Turkish guidance for common manufacturers), other app taking the camera, permission revocation, low storage, overheating.
- **Thermal management:** listen to thermal status APIs; adapt analysis rate and resolution, then pause with a warning if critical.
- Watchdog: detect a stalled pipeline (no frames processed for N seconds) and recover the camera session; log the event.
- Recommend plugged-in operation; monitor battery temperature; document charger/heat considerations.
- Never promise continuous operation that was not tested on the actual device.

---

## 13. PERFORMANCE

Measure (never assume) on the actual device: analysis frame rate, per-model latency (mean, median, p95), end-to-end latency, dropped frames, memory (PSS/heap), CPU where measurable, thermal trend over ≥1 hour, storage growth per hour of traffic.
- Adaptive scheduler: run the vehicle detector at a stable rate; run plate detection/OCR only on confirmed, moving tracks inside the ROI; skip redundant OCR when consensus is already strong.
- Avoid unnecessary bitmap allocations and repeated YUV→RGB conversions; reuse buffers.
- Baseline Profiles for app startup and UI smoothness.
- Defaults are chosen from measured results and documented in `docs/PERFORMANCE.md`.

---

## 14. EDGE CASES (MINIMUM LIST — EXTEND IT)

Night with headlights facing the camera; plate glare/reflective plates; rain, fog, dirty plates; vehicles at an angle; two-line (square) plates; motorcycles; partially occluded plates; trailers; vehicles without plates; very fast vehicles (motion blur); vehicles reversing or U-turning; traffic jams (many slow tracks); a vehicle stopping in front of the house; parked cars always in view; pedestrians/bicycles (must not be stored as vehicles); reflections through window glass; camera bumped or slowly drifting; sunrise/sunset exposure transitions; clock/time zone changes and DST; storage full mid-write; app killed mid-save; corrupted model file; device without required camera features; very old device (performance); duplicate similar vehicles; foreign plates; special/official plates.

Each edge case has: expected behavior, implementation note, and either an automated test or a manual test entry.

---

## 15. TESTING STRATEGY

Testing is a core deliverable. Report executed, passed, failed, and skipped tests separately. Never present unit tests as proof of real-camera behavior.

### 15.1 Unit tests
Plate normalization and validation (table-driven, many cases), OCR confusion handling, consensus voting, confidence aggregation, quality scoring and ranking, best-frame selection, track association/expiration, motion classification, dedup, coordinate transforms, timestamp/time-zone handling, retention and storage-cap logic, state machines, settings validation, error mapping, hash computation.

### 15.2 Vision integration tests
Golden-image tests for each model adapter (tensor shapes, normalization, decoding, NMS, coordinate mapping), missing/corrupt model handling, empty and invalid frames, multi-vehicle frames, low-quality crops. Use only synthetic or appropriately licensed fixtures; **no real personal plates committed to the repository**. Provide a documented local (git-ignored) dataset folder for private evaluation.

### 15.3 Data & storage tests
Room DAO tests, migrations (MigrationTestHelper), atomic write and crash recovery, orphan cleanup, delete cascades files, missing-file handling, export ZIP contents and hash verification.

### 15.4 Pipeline replay tests
A **replay harness** that feeds recorded image sequences or video files (licensed/synthetic, or user-recorded and kept local) through the full pipeline deterministically (injected Clock) to verify tracking, dedup, best-frame selection, and persistence end-to-end without a live camera. This is also the primary tool for tuning thresholds.

### 15.5 UI tests
Compose UI tests for key flows: onboarding, start/stop monitoring (with fake camera), records search/filter, incident finder, detail, delete/protect/export, settings validation. Screenshot tests if a suitable library is adopted.

### 15.6 Device / lifecycle tests
Permission scenarios, camera start/stop repetition, interruption, rotation, screen off, background, service restart, thermal simulation where possible.

### 15.7 Performance tests
Microbenchmarks for preprocessing and inference; Macrobenchmark for startup and scrolling; long-run soak test protocol.

### 15.8 Real-world evaluation (with the user)
Metrics with explicit denominators: vehicle detection recall, plate detection recall, OCR exact-match accuracy, full-plate read rate, character error rate, false positive rate, duplicate rate, needs-review rate, image-quality failure rate — split by daylight / dusk / night / rain / glare. Start with a small pilot (GO/NO-GO), then a larger evaluation. No accuracy claims from tiny or handpicked samples.

---

## 16. MANUAL TEST GUIDE FOR THE USER (TURKISH, BEGINNER-FRIENDLY)

The user is a beginner at testing Android computer-vision apps. Write `docs/DEVICE_SETUP.md` and the manual part of `docs/TEST_PLAN.md` in Turkish. Every manual test must contain:
- **Test ID**, **Amaç**, **Gerekenler**, **Hazırlık**, **Adım adım yapılacaklar**, **Beklenen sonuç**, **Başarısız sayılır eğer…**, **Toplanacak ekran görüntüsü/log**, **Sonucu Claude Code'a nasıl bildireceksin** (a copy-paste template), **Gizlilik/güvenlik notu**.

Minimum tests:
- **A — Kurulum:** enabling Developer Options and USB debugging, `adb devices`, installing the debug APK (copy-paste commands), Android Studio alternative, verifying the package.
- **B — İzinler:** grant/deny/permanently deny camera and notification permissions.
- **C — Kamera önizleme ve kalibrasyon:** orientation, zoom, ROI drawing, focus lock, test capture.
- **D — Görüntü uygunluğu (GO/NO-GO):** mounting the phone safely and securely (no risk of falling), measuring plate pixel width in daylight, dusk, and night using own/consenting vehicles first; how to share a **redacted** sample when asking for help.
- **E — Araç tespiti ve hareket:** moving vs. parked vehicles, overlapping vehicles.
- **F — Plaka okuma:** known test plates (own/consenting vehicles), comparing ground truth vs. result, recording exact/partial/unreadable/false.
- **G — En iyi kare seçimi.**
- **H — Tekrar kaydı engelleme:** single pass, multiple vehicles, two similar vehicles.
- **I — Uzun süreli çalışma:** plugged in, ≥2 hours, screen off, lock screen, background; record crashes, temperature, storage growth.
- **J — Olay Bul ve kanıt dışa aktarma:** find a known pass by time window, protect, export ZIP, verify hashes.
- **K — Depolama ve silme / saklama süresi.**
- **L — Hata kurtarma:** camera taken by another app, low storage, missing model, reboot.

Provide copy-paste ADB/logcat commands filtered by the app's tag; logs must not contain plate text by default.

---

## 17. PHASES (EXECUTE IN ORDER, AUTO-ADVANCE)

| Phase | Scope | Acceptance criteria |
|---|---|---|
| **0** | Audit (Plan Mode, read-only) + full plan | Findings and plan presented in Turkish; Plan Mode exited |
| **1** | aitmpl components, `CLAUDE.md`, `MEMORY.md`, skills, all docs skeletons, ADRs | All files exist; components recorded |
| **2** | Project foundation: multi-module Gradle, version catalog, convention plugins, design system, theme, navigation, onboarding shell, static analysis, CI-ready scripts | `./gradlew build` and checks pass |
| **3** | Camera subsystem: permissions, preview/analysis/capture, zoom, ROI, focus/exposure, coordinate transforms, fake camera for tests | Unit tests pass; installs and previews on device (or blocked with evidence) |
| **4** | Data layer: Room, migrations, file store, atomic writes, hashing, retention worker, settings DataStore | DAO/migration/storage tests pass |
| **5** | Model research & selection: candidates, licenses, conversion if needed, golden tests, on-device microbenchmarks | `MODEL_CARD.md` complete; golden tests pass; measured latency recorded |
| **6** | Vision pipeline: scheduler, detector, tracker, motion, plate detection, OCR, Turkish parser, consensus, quality, ranking, still capture, dedup | Unit + integration + replay tests pass |
| **7** | Monitoring workflow: foreground service, session state machine, watchdog, thermal adaptation, persistence end-to-end, notifications | Replay end-to-end test produces correct observations; lifecycle tests pass |
| **8** | Product UI: dashboard, live view + overlays, timeline, search, incident finder, detail, manual correction, export, settings, accessibility, animations | UI tests pass; screenshots reviewed against design system |
| **9** | Real-device pilot with user: install, calibration, GO/NO-GO feasibility, first recognition evaluation | Turkish guide delivered; results recorded honestly |
| **10** | Optimization & hardening: profiling, adaptive defaults, Baseline Profiles, security/privacy audit (backup rules, data extraction rules, file exposure), code review with installed agents | Measured improvements documented; audit findings closed |
| **11** | Release readiness: debug + (unsigned unless requested) release build, docs complete, final report | All acceptance criteria met or explicitly blocked with evidence |

### 17.1 Phase-closing protocol (every phase)
1. Run formatting, Detekt, Android Lint, unit tests, relevant instrumentation/replay tests, and the build.
2. Fix failures introduced by the phase; re-run.
3. Run the installed code-review agent on the phase's changes; address findings.
4. Update `docs/PROGRESS.md`, `MEMORY.md`, `docs/TEST_REPORT.md`, `CHANGELOG.md`, and relevant docs with **actual** commands and results.
5. Create a local Git commit (Conventional Commits). **Do not push** to any remote unless the user asks.
6. Send the user a Turkish phase report (Section 21).
7. **Automatically start the next phase.**

---

## 18. ERROR HANDLING AND OBSERVABILITY

- Explicit, typed states for: initializing camera, camera ready, waiting for permission, loading models, model unavailable, detecting, recognizing, saving, storage full, camera interrupted, thermal degraded, recoverable failure, fatal failure.
- Structured local diagnostic events (stage, code, duration, timestamp) — no plate text, no images.
- User-facing errors in Turkish, actionable ("Kamera başka bir uygulama tarafından kullanılıyor. O uygulamayı kapatıp 'Yeniden dene'ye dokun.").
- No stack traces shown to users; crash information stored locally and included in an exportable diagnostics bundle that excludes photos and plate text by default.

---

## 19. SECURITY AND PRIVACY

- Local processing only; **no INTERNET permission** in the release build unless a documented, user-approved need arises.
- No analytics, ads, telemetry, cloud upload, owner/identity lookup, or government database queries.
- No precise location collection.
- App-private storage; FileProvider only for explicit user exports.
- Configure `android:allowBackup`, `dataExtractionRules`, and `fullBackupContent` to exclude sensitive images and the database from cloud backup and device transfer (verify per API level).
- Do not claim encryption at rest unless implemented and verified; document the difference between Android file-based encryption and optional app-level encryption. Evaluate optional app-level encryption (e.g. Jetpack Security / Tink-based) for evidence files and the database, and record the decision.
- Optional app lock (biometric / device credential) to open the records — evaluate and implement if low risk.
- `docs/PRIVACY_AND_DATA_RETENTION.md` in Turkish: purpose limitation (home security), what is recorded, retention, deletion, user responsibilities; KVKK-aware wording **without** claiming legal compliance. Advise pointing the camera primarily at the user's own property frontage and the adjacent road, not into neighbors' private spaces.
- Security audit with the installed security agent in Phase 10.

---

## 20. MODEL AND DEPENDENCY GOVERNANCE

For every model in `docs/MODEL_CARD.md`: name, version, source URL (verified), license, redistribution status, architecture, training data description (as published), input dims and preprocessing, output format and postprocessing, runtime and operator compatibility, quantization, file size, **SHA-256 of the artifact actually used**, measured latency/memory on the device, measured evaluation results, known limitations.

For every important dependency: role, verified version, license, compatibility notes. No unverified binaries, no dynamic downloads from untrusted URLs. If a model cannot be redistributed, document lawful acquisition and integrity verification steps.

---

## 21. REPORTING FORMAT (ALWAYS IN TURKISH)

### After each phase
1. Ne değişti
2. Oluşturulan/değiştirilen dosyalar
3. Çalıştırılan komutlar
4. Gerçek build ve test sonuçları (geçen / kalan / atlanan ayrı)
5. Hatalar ve nasıl çözüldükleri
6. Eksik kalanlar
7. Kullanıcıdan bir işlem gerekiyor mu
8. Kullanıcının yapacağı bir sonraki adım (varsa) — sonra otomatik olarak sonraki faza geçildiğini belirt

### End of session
A. Uygulama özeti · B. Mimari · C. Build durumu · D. Test sonuçları · E. Model durumu (gerçek modeller, kaynaklar, lisanslar, sınırlamalar) · F. Kurulum komutları · G. İlk gerçek cihaz testi · H. Bilinen sınırlamalar · I. Sonraki kilometre taşı ve nedeni

---

## 22. FINAL ACCEPTANCE CRITERIA

The release is complete only when each item is met or explicitly marked **blocked with evidence**:
1. Project builds; all static checks pass.
2. App installs and runs on the user's device (or absence documented).
3. Onboarding, permissions, calibration (zoom, ROI, focus) work.
4. Preview, analysis, and high-res capture work with correct coordinate mapping.
5. Vision pipeline is modular, uses verified models, and passes golden and replay tests.
6. Moving vehicles produce exactly one observation per pass; parked vehicles do not spam records.
7. Each observation stores scene image, vehicle crop, plate crop (when available), honest plate status, timestamps, confidences, and SHA-256 hashes.
8. Uncertain OCR is represented honestly; manual correction preserves the original.
9. Records persist across restarts; deletion removes files; retention and protection work.
10. Timeline search (full and partial plate) and the Incident Finder return results quickly; evidence export produces a verifiable ZIP with a Turkish report.
11. Monitoring runs as a foreground service with watchdog and thermal adaptation; behavior in screen-off/locked/background states is tested and documented on the real device.
12. UI is Turkish, professional, accessible, and consistent with the design system.
13. Automated tests executed and reported truthfully.
14. Turkish manual test guide and user guide delivered.
15. Model provenance, licenses, hashes, and measured limitations documented.
16. Privacy, retention, and security documentation complete; backup rules configured.
17. Final report clearly separates completed, untested, and blocked work.

---

## 23. START NOW

1. **Enter Plan Mode.**
2. Perform the Phase 0 audit (read-only) and present the complete plan **in Turkish**.
3. Exit Plan Mode.
4. Install aitmpl components, create `CLAUDE.md`, `MEMORY.md`, project skills, and documentation.
5. Execute the phases in order, automatically moving to the next phase after each phase-closing protocol.

Never invent results. Prioritize a reliable, testable end-to-end pipeline and genuinely useful evidence for the user's home-security purpose. Precise speed measurement, owner identification, and cloud features are explicitly out of scope.

**Reminder: speak to the user in Turkish at all times.**
