# PartyBox Photobooth (Android)

Kotlin Android photobooth app (minSdk 29 / targetSdk 34) with:
- Landscape-only UI
- Pink primary action styling
- Camera capture + preview/retake/print flow
- UART printer protocol (64-byte frames on `/dev/ttyS3` @ 115200 8N1) via JNI/NDK termios serial layer
- Sequential print queue + status states
- Optional heartbeat (`HBTB`) every ~30 seconds
- Ring LED brightness + flash wrappers via vendor reflection APIs

## Project setup

1. Open in Android Studio (Hedgehog+ recommended).
2. Let Gradle sync download dependencies.
3. Build/install the `app` module.

## Build from CLI

```bash
./gradlew assembleDebug
```

## Permissions/runtime setup

The app shows a one-time setup screen if required permissions are missing.

## Android 10 one-time ADB permission grant commands

```bash
adb shell pm grant com.photobooth android.permission.CAMERA
adb shell pm grant com.photobooth android.permission.READ_EXTERNAL_STORAGE
adb shell pm grant com.photobooth android.permission.WRITE_EXTERNAL_STORAGE
```

## Printer protocol notes

- Header `[0..3]`: `1B 2A 43 41`
- Fixed frame size: `64 bytes`
- `IMGI`: group `0x02`, id `0x01`, payload `[8..15]` = `print_1.`
- `PRRQ`: group `0x02`, id `0x00`, payload `[8]` = copies
- `GOPR`: group `0x02`, id `0x02`
- `HBTB`: group `0x04`, id `0x55`
- Target image path: `/sdcard/DCIM/Printer/Image/print_1.jpg`

## SELinux/device access note

On some vendor images, `/dev/ttyS3` access may require privileged/system deployment and matching SELinux policy.
