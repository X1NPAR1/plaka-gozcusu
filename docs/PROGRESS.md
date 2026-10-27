# PROGRESS

Legend: ✅ done · 🔄 in progress · ⏳ pending · ⛔ blocked (with evidence)

| Phase | Scope | Status | Notes |
|---|---|---|---|
| 0 | Audit + plan | ✅ | Plan approved 2026-10-28; findings in MEMORY.md |
| 1 | aitmpl, CLAUDE.md, MEMORY.md, skills, docs, ADRs | ✅ | |
| 2 | Gradle foundation, design system, navigation, onboarding shell, static analysis | ✅ | build + checks green |
| 3 | Camera subsystem | ✅ (device check ⛔ no phone) | Device acceptance blocked until phone connected |
| 4 | Data layer | ⏳ | |
| 5 | Model research & selection | ⏳ | |
| 6 | Vision pipeline | ⏳ | |
| 7 | Monitoring workflow | ⏳ | |
| 8 | Product UI | ⏳ | |
| 9 | Real-device pilot (user) | ⏳ | Requires user |
| 10 | Optimization & hardening | ⏳ | |
| 11 | Release readiness | ⏳ | |

## Phase 1 checklist
- [x] git init, .gitignore, local.properties (sdk.dir fix)
- [x] aitmpl components installed + audited (MEMORY.md)
- [x] CLAUDE.md, MEMORY.md
- [x] Project skills: android-verify, device-test, model-integration, phase-close, turkish-test-guide
- [x] docs/ skeletons + ADRs
- [x] Phase-1 commit

## Phase 2 checklist
- [x] Gradle 9.8.2 wrapper (distribution SHA-256 verified against services.gradle.org)
- [x] build-logic convention plugins (application, library, compose, feature, hilt, jvm.library)
- [x] Modules: app, core:common, core:model, core:designsystem, feature:onboarding
- [x] Design system tokens/theme/components; onboarding shell; splash; adaptive icon; nav host
- [x] Spotless(ktlint) + Detekt(+compose rules) + Lint (warningsAsErrors) green
- [x] Caches moved to F:\DevCache (user disk rule)

## Phase 3 checklist
- [x] vision:api geometry (FrameTransform, ROI, CameraOptics), CaptureRateLimiter, BoundedRingBuffer + tests
- [x] core:camera: CameraController, CameraXController (preview/analysis/capture, fallback, zoom, focus lock, exposure, capabilities), ClosingAnalyzer, FakeCameraController (testFixtures)
- [x] Calibration screen + camera permission gate (granted/denied/permanent), wired after onboarding
- [x] Code review: 8 findings fixed (bind/unbind race, observer leak, main-thread blocking, metering crop, aspect fallback, exceptions, executor leak, zoom clamp)
- [ ] On-device preview check: blocked, no phone connected
- PAUSED by user 2026-10-28 after Phase 3 commit. Next: Phase 4 (data layer). Resume cron deleted; recreate on resume.
