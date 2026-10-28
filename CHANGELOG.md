# Changelog
All notable changes to this project are documented here. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [0.1.0] - 2026-10-28
### Added
- Multi-module Gradle foundation with convention plugins, design system (dark-first M3 theme), onboarding shell, splash screen, adaptive icon, backup/data-extraction exclusions, static analysis.
- Session limit guard (status line sampler + PreToolUse hook) and resume cron.
- Camera subsystem (CameraX), calibration (zoom, road area, focus lock, exposure, test capture).
- Data layer: Room, atomic evidence store with SHA-256, retention worker, settings.
- Models: EfficientDet-Lite0 vehicle detector (LiteRT), ML Kit plate reading baseline.
- Vision pipeline: tracker, motion, Turkish plate parser, multi-frame consensus, quality, best frame, duplicate linking.
- Camera foreground monitoring service with watchdog and thermal adaptation.
- UI: dashboard, live view, records, incident finder, detail with correction, evidence ZIP export, settings.
- Turkish setup/user guides and manual test plan (A–L).
- Project setup: CLAUDE.md, MEMORY.md, project skills, documentation skeletons, ADRs, curated aitmpl components.
