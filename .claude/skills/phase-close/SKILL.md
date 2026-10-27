---
name: phase-close
description: Execute the Plaka Gözcüsü phase-closing protocol - verify, code review, docs update, local conventional commit, Turkish phase report, then auto-advance to the next phase. Use at the end of every phase.
---

# phase-close

1. Run the `android-verify` skill (plus relevant instrumentation/replay tests). Fix failures the phase
   introduced; re-run.
2. Launch the `code-reviewer` agent on the phase diff (since the previous phase commit); fix valid findings.
3. Update with **actual** commands/results: `docs/PROGRESS.md`, `MEMORY.md`, `docs/TEST_REPORT.md`,
   `CHANGELOG.md` (Keep a Changelog, Unreleased), and any doc the phase touched.
4. `git add -A` and commit with a Conventional Commit (e.g. `feat(camera): phase 3 camera subsystem`) ending
   with the Co-Authored-By trailer. Never push.
5. Turkish phase report:
   1. Ne değişti 2. Oluşturulan/değiştirilen dosyalar 3. Çalıştırılan komutlar
   4. Gerçek build ve test sonuçları (geçen / kalan / atlanan ayrı) 5. Hatalar ve nasıl çözüldükleri
   6. Eksik kalanlar 7. Kullanıcıdan bir işlem gerekiyor mu 8. Sonraki adım + "otomatik olarak Faz N+1'e geçiyorum"
6. Start the next phase immediately unless a genuine blocker needs the user.
