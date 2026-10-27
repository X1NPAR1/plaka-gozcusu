# Plaka Gözcüsü

**Türkçe özet:** Evinize sabitlediğiniz bir Android telefonla önünüzdeki yoldan geçen araçları tamamen cihaz üzerinde izleyen, plakaları okumaya çalışan ve her geçiş için kanıt niteliğinde fotoğraf ve kayıt saklayan uygulama. İnternet ve bulut kullanmaz. Belirli bir zaman aralığındaki tüm araçları hızlıca bulup doğrulanabilir bir kanıt paketi olarak dışa aktarabilirsiniz.

## Technical overview
Kotlin · Jetpack Compose (Material 3) · CameraX · LiteRT · Room · Hilt · multi-module clean architecture.
See `docs/ARCHITECTURE.md`, `docs/PROJECT_PLAN.md`, `docs/PROGRESS.md`.

## Build
```
gradlew.bat assembleDebug
```
Requires JDK 21 and Android SDK (platform 36). Set `sdk.dir` in `local.properties`.
