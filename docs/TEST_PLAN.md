# Test Plan

Automated results are reported in `TEST_REPORT.md` (passed / failed / skipped separately). Unit tests are never
presented as proof of real-camera behaviour.

## 1. Automated
| Area | Type | Phase |
|---|---|---|
| Plate normalization/validation, OCR confusions, consensus, quality, ranking, best frame | JVM unit (table-driven) | 6 |
| Tracker association/expiry, motion classification, scene shift, dedup | JVM unit | 6 |
| Coordinate transforms, ImageProxy closing, ring buffer, capture rate limiting | JVM unit | 3 |
| Room DAOs, FTS search, migrations (MigrationTestHelper) | Robolectric/instrumented | 4 |
| Atomic writes, crash recovery, orphan sweep, SHA-256, retention/cap, export ZIP | JVM/Robolectric | 4, 8 |
| Model golden tests, missing/corrupt model | JVM/instrumented | 5 |
| Pipeline replay (injected Clock) | JVM | 6–7 |
| Session state machine, watchdog, thermal adaptation | JVM unit | 7 |
| Compose UI flows (fake camera) | Compose UI | 8 |
| Startup/scroll macrobenchmark, inference microbenchmark | Device | 5, 10 |

## 2. Edge case matrix
| Edge case | Expected behaviour | Test |
|---|---|---|
| Night, headlights into camera | Exposure comp; low-quality candidates rejected; status UNREADABLE rather than guess | Manual D/F (night) + quality unit |
| Plate glare / reflective plate | Clipping detector lowers quality; other frames preferred | Quality unit + manual F |
| Rain, fog, dirty plate | NEEDS_REVIEW/UNREADABLE, raw OCR kept | Manual F |
| Vehicle at an angle | Deskew; lower confidence | Preprocessing unit + manual |
| Two-line (square) plate | Two-line handling in OCR crop | Parser unit + golden |
| Motorcycle | Vehicle class kept; rear plate small → honest status | Manual E/F |
| Partially occluded plate | No invented characters; NEEDS_REVIEW | Consensus unit |
| Trailer | Separate plate may differ; no forced merge | Manual H |
| Vehicle without plate | NO_PLATE_VISIBLE observation | Replay |
| Very fast vehicle (blur) | Blur score; best available frame; UNREADABLE if needed | Quality unit + manual |
| Reversing / U-turn | Single track, direction updated; one observation | Tracker unit + replay |
| Traffic jam (many slow tracks) | Bounded track count, OCR budget, no spam | Replay |
| Vehicle stops in front of house | Optional single "stopped" event | Motion unit |
| Parked cars always in view | Stationary → no repeated observations | Motion unit + replay + manual E |
| Pedestrians / bicycles | Not stored as vehicles (class filter) | Detector filter unit |
| Reflections through window glass | Guidance + ROI; documented limitation | Manual C |
| Camera bumped / drifting | Scene-shift → pause + warn; ROI recalibration prompt | Motion unit + manual |
| Sunrise/sunset transitions | AE adapts; quality scoring | Manual D |
| Clock / time-zone change, DST | UTC epoch + zone id stored; UI shows local time correctly | Time unit |
| Storage full mid-write | Atomic temp+rename; no partial files; monitoring degrades → stops with warning | Storage unit |
| App killed mid-save | Temp files swept; no DB row without files | Storage unit |
| Corrupted / missing model | Typed error, ModelUnavailable state, Turkish message | Model unit |
| Device lacks camera features | Capability report; graceful fallback | Camera unit + manual C |
| Very old / slow device | Adaptive scheduler, Düşük load profile | Benchmark |
| Two similar vehicles close together | Two observations | Tracker/dedup unit + manual H |
| Foreign plates | Not forced to TR format; NEEDS_REVIEW | Parser unit |
| Special/official/diplomatic/military plates | NEEDS_REVIEW with raw text | Parser unit |
| Camera taken by another app / call | Interrupted state, auto-recover, notification | Lifecycle + manual L |
| Permission revoked while running | Stop safely, explain | Lifecycle |
| Reboot | No silent auto-start; "resume" flow | Manual L |
| Overheating | Degrade → pause with warning | Thermal unit + manual I |

## 3. Manual tests (Türkçe)
A–L testleri `turkish-test-guide` biçimiyle Faz 9'da (ve ilgili fazlarda) buraya eklenecek:
A Kurulum · B İzinler · C Önizleme ve kalibrasyon · D Görüntü uygunluğu (GO/NO-GO) · E Araç tespiti ve hareket ·
F Plaka okuma · G En iyi kare · H Tekrar kaydı engelleme · I Uzun süreli çalışma · J Olay Bul ve dışa aktarma ·
K Depolama ve saklama süresi · L Hata kurtarma.
