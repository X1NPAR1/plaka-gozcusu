# Technical Decisions (ADR)

## ADR-001 ML runtime: LiteRT
- **Context:** One on-device runtime required; avoid two runtimes without evidence.
- **Options:** LiteRT (`com.google.ai.edge.litert` 2.3.2, verified on Google Maven 2026-10-28); ONNX Runtime Mobile.
- **Decision:** LiteRT. Chosen by the user after explanation; also the most common Android runtime, XNNPACK CPU built in, GPU delegate available.
- **Consequences:** Models must be `.tflite` or convertible; ONNX-only candidates need conversion (verified per model) or are rejected. ML Kit (separate bundled runtime) allowed only as OCR baseline/fallback — revisit if it becomes primary.

## ADR-002 Toolchain versions
- **Decision:** Gradle 9.8.2, AGP 9.4.1, Kotlin 2.4.21, KSP 2.3.12, compileSdk 37 / targetSdk 37, minSdk 29.
- **Update (Phase 2, measured):** core-ktx 1.19.1 and other current AndroidX libs refuse compileSdk 36 (build error) → compileSdk 37 (android-37.0 installed). Lint `OldTargetApi` flagged targetSdk 36 → targetSdk 37; runtime behaviour changes of API 37 must be checked on device (Phase 9).
- Built-in Kotlin (AGP 9 default) is used; KGP 2.4.21 pinned through the build-logic classpath. kapt not used (KSP only).
- **Rationale for minSdk 29:** mid/low-tier target phone; API 29 gives `PowerManager` thermal status listener, scoped storage model, modern CameraX support. Older devices are out of scope (documented).
- **Source:** maven-metadata.xml on dl.google.com / repo1.maven.org, services.gradle.org (2026-10-28).
- **Risk:** Detekt 1.23.8 vs Kotlin 2.4 — verify in Phase 2.

## ADR-003 Multi-module clean architecture + Hilt
- **Decision:** Module map in ARCHITECTURE.md; Hilt for DI; convention plugins in `build-logic`.
- **Consequences:** Faster isolated tests, enforced boundaries; more Gradle setup.

## ADR-004 Model license policy
- **Decision:** Bundle only models with clear permissive licenses (Apache-2.0, MIT, BSD). AGPL/GPL/non-commercial rejected unless the user explicitly approves.

## ADR-005 Local-only, no INTERNET in release
- **Decision:** Release manifest has no INTERNET permission; no telemetry; FileProvider only for explicit exports.

## ADR-006 Monitoring host: camera-type foreground service
- **Decision:** Monitoring runs in a user-started FGS with `foregroundServiceType="camera"`. Current Android 14+/targetSdk 36 rules (FOREGROUND_SERVICE_CAMERA permission, while-in-use start restrictions) to be verified from developer.android.com and cited here in Phase 7.

Pending ADRs: Turkish plate format sources (Phase 6), model selections (Phase 5), app-level encryption (Phase 10), video clip feasibility (Phase 7/10).

## ADR-007 Model selection (Phase 5)
- **Vehicle detector:** EfficientDet-Lite0 int8 from the official `tensorflow/efficientdet` Kaggle model (Apache 2.0, verified via Kaggle API). Lite0 chosen over Lite2 for the mid/low-tier target phone (smaller input, ~half compute); revisit with on-device benchmark.
- **Plate detection + OCR:** ML Kit Text Recognition v2 bundled Latin model as baseline behind `PlateReader`; no permissively licensed Turkish plate model found. Network permissions are removed from the merged manifest so the closed-source SDK cannot transmit anything.
- **Runtime:** LiteRT 2.3.2 classic `Interpreter` API (CompiledModel API exists but adds NPU/GPU paths we cannot measure yet).
- **Consequences:** Plate accuracy is unproven until the Phase 9 field test; status stays honest (NEEDS_REVIEW/UNREADABLE).

## ADR-008 Turkish plate rules
- Source: en.wikipedia.org "Vehicle registration plates of Turkey" (accessed 2026-10-28). The Karayolları Trafik Yönetmeliği text could not be retrieved; rules to be re-checked against the regulation when available.
- Province 01–81; 1L+4–5D, 2L+3–4D, 3L+2–3D; letters exclude Ç Ş İ Ö Ü Ğ Q W X. Special series (CD, CC, M*, G, T, B single, GMR, SGH, J**) → NEEDS_REVIEW.
- OCR confusion fixes (O→0, I→1, Z→2, S→5, B→8, G→6) only in digit positions; minimal-fix reading wins; O/I at the letter/digit boundary with an alternative split → ambiguous (not valid).

## ADR-006 (update, Phase 7) Camera foreground service — verified rules
Source: developer.android.com/develop/background-work/services/fgs/service-types (2026-10-28):
FOREGROUND_SERVICE + FOREGROUND_SERVICE_CAMERA + CAMERA; `foregroundServiceType="camera"`; a camera FGS
cannot be started from the background nor from BOOT_COMPLETED (while-in-use restriction; targetSdk 35+).
Consequence: monitoring only starts from the visible UI; after reboot/process death the app offers
"resume" instead of auto-starting. START_NOT_STICKY. No timeout applies to the camera type.

## ADR-009 UI module consolidation
Spec listed :feature:{monitor,records,detail,incident,settings,onboarding}. Implemented as
:feature:monitor (dashboard + live view + service), :feature:records (timeline, incident finder, detail,
export) and :feature:settings, plus :feature:onboarding (onboarding + calibration). Detail/incident share
the record row, export and bulk-action code, so one module avoids cross-feature dependencies.
Evidence export uses the Storage Access Framework (user picks destination); no FileProvider/auto-share.

## ADR-010 Opt-in cloud AI plate check (0.3.0, user decision)
- **Context:** User asked for an optional AI second opinion with their own API key (preferably free).
- **Decision:** Google Gemini `generateContent` (default model `gemini-2.5-flash`, user-editable). Off by default;
  requires the user's key, stored AES-GCM-encrypted with an Android Keystore key. Sends only the plate (or vehicle)
  crop + on-device candidate strings, via WorkManager with a network constraint and exponential backoff.
- **Trust rules:** answer must parse as a valid Turkish civilian plate; agreement → HIGH_CONFIDENCE; disagreement →
  NEEDS_REVIEW suggestion the user can accept; user-confirmed records are never changed; raw OCR is never changed.
- **Consequences:** INTERNET + ACCESS_NETWORK_STATE are now in the manifest (supersedes ADR-005's "no INTERNET").
  Free-tier Gemini content may be used by Google to improve products (pricing page, 2026-10-28) — shown in the UI.
  Room schema v2 (migration 1→2, tested from the committed v1 schema).
