# Security

## Threat model (initial)
| Asset | Threat | Mitigation |
|---|---|---|
| Evidence images + DB | Cloud backup / device transfer leak | dataExtractionRules + fullBackupContent exclusions |
| Evidence integrity | Tampering after capture | SHA-256 at save, verification in detail + export manifest |
| Records | Someone else using the phone | Optional biometric/device-credential app lock (evaluate Phase 10) |
| Exports | Accidental sharing | Only explicit user action via SAF/share sheet; FileProvider scoped |
| Logs | Plate text leakage | Logging facade forbids plate text; device-test grep |
| Models | Tampered model file | SHA-256 verified at load |

Encryption at rest: relies on Android file-based encryption; app-level encryption evaluated in Phase 10. No claim of app-level encryption until implemented and verified.
