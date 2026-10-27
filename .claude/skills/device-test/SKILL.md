---
name: device-test
description: Install and exercise the Plaka Gözcüsü debug APK on a USB-connected Android phone via ADB - check device, collect capabilities, install, launch, collect privacy-filtered logcat, capture screenshots. Use whenever a phone is connected or the user asks to test on device.
---

# device-test

ADB path: `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe` (not on PATH; `ANDROID_HOME` is wrong on this machine).
Application id: read `applicationId` (+ debug suffix) from `app/build.gradle.kts`.

1. `adb devices -l` → empty or `unauthorized`: STOP and give the user Turkish instructions
   (enable USB debugging, accept the RSA prompt). Continue other non-blocked work.
2. Capabilities → record in MEMORY.md: `getprop ro.product.model`, `ro.build.version.release`,
   `ro.build.version.sdk`, `ro.product.cpu.abi`, `/proc/meminfo` first line,
   `dumpsys media.camera` (camera ids, facing, max sizes), in-app CameraCapabilityReport if available.
3. `./gradlew assembleDebug` → `adb install -r app/build/outputs/apk/debug/app-debug.apk`
4. Launch: `adb shell monkey -p <applicationId> 1`; then
   `adb logcat -d -s PlakaGozcusu:* AndroidRuntime:E > device-logs/logcat.txt` (device-logs/ is git-ignored).
5. Screenshot: `adb exec-out screencap -p > device-logs/screen-<name>.png`.
6. Privacy check: grep logs for plate-like text `[0-8][0-9] ?[A-Z]{1,3} ?[0-9]{2,5}`; any hit is a bug.

Report in Turkish what ran and what was actually observed. Never claim camera behaviour not observed.
