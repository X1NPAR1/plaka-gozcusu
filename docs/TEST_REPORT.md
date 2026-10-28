# Test Report

Only executed results. Format per phase: command, date, passed / failed / skipped, notes.

## Phase 1
No code yet. Verified manually: force-push-blocker hook denies a force-push command and allows `ls -f` (2026-10-28).

## Phase 2 (2026-10-28)
Command: `./gradlew spotlessCheck detekt lint testDebugUnitTest test assembleDebug --continue`
Result: BUILD SUCCESSFUL. Unit tests: total=6 passed=6 failed=0 skipped=0
(AppResultTest 2, RecognitionStatusTest 2, OnboardingStateTest 2). No device tests (no device connected).
Known: Gradle deprecation warnings (Configuration.setVisible, ReportingExtension.file) originate in third-party
plugins (Detekt 1.23.8), not project scripts.

## Phase 3 (2026-10-28)
`./gradlew spotlessCheck detekt lint testDebugUnitTest test assembleDebug --continue` → BUILD SUCCESSFUL.
Unit tests: total=50 passed=50 failed=0 skipped=0. Device tests: not run (no device).

## Phase 4 (2026-10-28)
Full verify → BUILD SUCCESSFUL. Unit tests total=77 passed=77 failed=0 skipped=0
(new: EvidenceFileStoreTest 7, RetentionPlannerTest 4, ObservationDaoTest 6 [Robolectric], SettingsRepositoryTest 2, ObservationRepositoryTest 6 [Robolectric], AppSettingsTest 2).

## Phases 5–6 (2026-10-28)
Full verify → BUILD SUCCESSFUL. Unit tests total=117 passed=117 failed=0 skipped=0.
New: DetectionDecoderTest 5, EfficientDetReferenceTest 5 (Python LiteRT reference dump), TurkishPlateParserTest 12,
PipelineUnitsTest 10, PipelineReplayTest 8 (replay harness: single pass, parked, two cars, ROI, unreadable,
no plate, disagreement → review, camera jolt pause). On-device inference/benchmarks: not run (no device).

## Phase 7 + review fixes (2026-10-28)
Full verify → BUILD SUCCESSFUL. Unit tests total=129 passed=129 failed=0 skipped=0
(new: MonitoringSessionTest 8; repository/pipeline regression tests for the phase 4–6 review findings).
Service lifecycle on a real device (screen off, lock, background, interruption) is untested — Phase 9.
