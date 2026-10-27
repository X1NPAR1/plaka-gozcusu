---
name: model-integration
description: Checklist for integrating or replacing an ML model (vehicle detector, plate detector, plate OCR) in Plaka Gözcüsü with LiteRT - provenance, license, tensor I/O verification, SHA-256, golden test, benchmark, documentation. Use whenever a .tflite model is added, converted, or swapped.
---

# model-integration

Do every step and keep the evidence. Never invent a URL, license, checksum or metric.

1. **Provenance**: official source URL (actually opened), maintainer, release/commit, date.
2. **License**: read the real LICENSE. Reject AGPL/GPL/unclear/non-commercial for bundled models unless the
   user explicitly approves. Note redistribution rights (may it ship inside the APK?).
3. **Obtain** only from the official URL into its own empty folder, then copy to
   `vision/models/src/main/assets/models/<name>.tflite`. Compute `sha256sum`; store it in
   `docs/MODEL_CARD.md` and in the adapter's `ModelSpec` (verified at load time).
4. **Inspect I/O**: `python -I tools/inspect_tflite.py <file>` → input shape/dtype/quant params,
   outputs, op list. Write into MODEL_CARD.
5. **Adapter** in `:vision:models` implementing the `:vision:api` interface; preprocessing, decoding and NMS
   matching the inspected I/O. Missing/corrupt file → typed error, never a crash.
6. **Golden test**: synthetic or licensed fixture → expected decoded output within tolerance; cross-check
   against the Python reference run.
7. **Benchmark** in `:benchmark` on the real device (mean/median/p95, memory). No device → "ölçülmedi".
8. **Docs**: MODEL_CARD.md (all fields), ADR in TECHNICAL_DECISIONS.md, open-source notices.
