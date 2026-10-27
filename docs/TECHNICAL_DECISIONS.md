# Technical Decisions (ADR)

## ADR-001 ML runtime: LiteRT
- **Context:** One on-device runtime required; avoid two runtimes without evidence.
- **Options:** LiteRT (`com.google.ai.edge.litert` 2.3.2, verified on Google Maven 2026-10-28); ONNX Runtime Mobile.
- **Decision:** LiteRT. Chosen by the user after explanation; also the most common Android runtime, XNNPACK CPU built in, GPU delegate available.
- **Consequences:** Models must be `.tflite` or convertible; ONNX-only candidates need conversion (verified per model) or are rejected. ML Kit (separate bundled runtime) allowed only as OCR baseline/fallback — revisit if it becomes primary.

## ADR-002 Toolchain versions
- **Decision:** Gradle 9.8.2, AGP 9.4.1, Kotlin 2.4.21, KSP 2.3.12, compileSdk/targetSdk 36 (installed, stable), minSdk 29.
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
