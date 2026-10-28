# Model Card

## 1. Vehicle detector — EfficientDet-Lite0 (int8)
| Field | Value |
|---|---|
| Name / version | `tensorflow/efficientdet`, variation `lite0-detection-metadata`, version 1 |
| Source (verified 2026-10-28) | https://www.kaggle.com/models/tensorflow/efficientdet/TfLite/lite0-detection-metadata (download via Kaggle API `/api/v1/models/tensorflow/efficientdet/tfLite/lite0-detection-metadata/1/download`) |
| License | **Apache 2.0** (Kaggle model API `licenseName` for every tfLite instance of `tensorflow/efficientdet`) |
| Redistribution | Allowed under Apache 2.0; bundled in the APK (`vision/models/src/main/assets/models/efficientdet_lite0_int8.tflite`) |
| Architecture | EfficientDet-Lite0 (EfficientNet-Lite0 backbone + BiFPN), TFLite detection post-process built in |
| Training data | COCO (80 classes, 90-index label map embedded as `labelmap.txt`) |
| Input | `serving_default_images:0` [1,320,320,3] **uint8**, quantization (0.0078125, 127); preprocessing = stretch resize to 320×320 RGB (measured with `tools/inspect_tflite.py`) |
| Outputs (measured) | `StatefulPartitionedCall:3` boxes [1,25,4] (ymin,xmin,ymax,xmax normalized) · `:2` classes [1,25] (integer label index) · `:1` scores [1,25] · `:0` count [1] |
| Classes used | 2 car, 3 motorcycle, 5 bus, 7 truck (person 0 / bicycle 1 are discarded) |
| Runtime | LiteRT 2.3.2 `org.tensorflow.lite.Interpreter`, CPU (XNNPACK), 2 threads |
| File size | 4,563,519 bytes |
| **SHA-256** | `2e04c53bfeac0ac2a30c057c7e2a777594ce39baaac35a92f74fb1e8c4fc4e0b` (verified at load time by `ModelAsset`) |
| Measured latency/memory on device | **Not measured** — no device connected (Phase 9/10). Google lists ~29 ms CPU for the MediaPipe int8 build on Pixel 6; that is a different build/device and is not used as our result. |
| Measured accuracy | **Not measured** on our data (Phase 9 evaluation). |
| Known limitations | 320×320 input: distant vehicles become tiny; motion blur; night. Vehicle crops for OCR are taken from the full-resolution frame, not the 320 input. |
| Golden test | `EfficientDetReferenceTest` decodes outputs dumped by the Python LiteRT reference run on a synthetic image (`efficientdet_lite0_reference.json`) — verifies tensor contract and decoding, not accuracy. |

Rejected candidates: Ultralytics YOLO family (AGPL-3.0, ADR-004). The MediaPipe bucket build of the same model (`storage.googleapis.com/mediapipe-models/...`) was not used because its page states no model licence.

## 2. Plate detection + OCR — ML Kit Text Recognition v2 (Latin, bundled)
| Field | Value |
|---|---|
| Artifact | `com.google.mlkit:text-recognition:16.0.1` (bundled; official docs: "Model is statically linked to your app at build time") |
| License | Google ML Kit Terms of Service (proprietary, free; redistribution as part of the app SDK) |
| Network | Model is bundled. The app ships **without INTERNET/ACCESS_NETWORK_STATE** (removed in the merged manifest), so no data can leave the device. |
| Role | Baseline. Each text line in the vehicle crop is a plate candidate; `TurkishPlateParser` + multi-frame consensus decide. The line box is used as the plate crop. |
| Size impact | ~4 MB per ABI (official docs) |
| Measured accuracy | **Not measured** (Phase 9). Expected weak spots: small/distant plates, glare, two-line plates, the blue TR band. |

**Gap (honest):** no dedicated, permissively licensed Turkish plate detector / plate-OCR model was found. Fine-tuning plan (not executed): collect consented/own-vehicle captures in `datasets/private/` with bounding boxes + text labels (CVAT), train a small plate detector (e.g. EfficientDet-Lite0 via TFLite Model Maker successor / Apache-2.0 code) and a CRNN/PARSeq-style recogniser, export INT8 `.tflite`, evaluate exact-match and CER per lighting split before replacing the baseline via the `PlateReader` interface.
