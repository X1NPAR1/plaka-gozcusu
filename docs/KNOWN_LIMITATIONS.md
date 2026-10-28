# Known Limitations

- No speed measurement, owner identification or cloud features (out of scope).
- Target phone is mid/low tier without telephoto: plate readability at distance is unproven until the Phase 9 GO/NO-GO test.
- OCR results are automated and may be wrong; HIGH_CONFIDENCE is heuristic, not human-verified.

- **Not yet run on a real device.** Camera behaviour, screen-off monitoring, OEM battery killers, thermal behaviour, detector/OCR latency and accuracy are all unmeasured until the Phase 9 pilot.
- Plate reading uses ML Kit general text recognition (no dedicated, permissively licensed Turkish plate model was found); expect weak results at distance, at night and on two-line plates.
- Vehicle detector input is 320×320; small/distant vehicles may be missed.
- Analysis frames are converted per frame without buffer reuse; CPU/heat cost to be measured and optimised.
- Universal APK is ~71 MB (LiteRT + ML Kit native libraries for all ABIs); per-ABI splits/App Bundle can reduce it.
- Video clips / pre-event context frames are not stored yet (ring buffer exists in :vision:api; persisting it is future work).
- Turkish plate rules are from a secondary source (Wikipedia); verify against the regulation.
- Merged manifest contains WAKE_LOCK, RECEIVE_BOOT_COMPLETED and FOREGROUND_SERVICE_DATA_SYNC from WorkManager (daily retention). The camera is never started at boot.
