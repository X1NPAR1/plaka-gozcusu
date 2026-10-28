# Performance

## Protocol
On the actual device: analysis FPS, per-model latency (mean/median/p95), end-to-end latency, dropped frames, PSS/heap, CPU, thermal trend >= 1 h, storage growth per hour.

## Results
None measured yet.

## Current defaults (starting points, NOT measured)
| Setting | Value | Where |
|---|---|---|
| Analysis rate | Low 2 / Balanced 4 / High 8 fps; ×0.75 moderate heat, ×0.5 severe, pause at critical | FrameScheduler |
| Analysis resolution | 1280×960 target (closest supported) | CameraConfig |
| Detector | EfficientDet-Lite0 int8, 2 CPU threads | EfficientDetVehicleDetector |
| OCR budget | 1 track per frame, ≤40 calls per track, stops at strong consensus (≥0.85, 3 frames) | PipelineConfig |
| Stills | cooldown 1.5 s, ≤2 per track, ≤20 per minute | MonitoringService |
| Watchdog | rebind camera after 10 s without frames | MonitoringService |
All to be replaced by measured values after the Phase 9 pilot.
