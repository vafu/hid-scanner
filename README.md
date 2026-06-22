# SmartParts HID Scanner

Android Kotlin / Jetpack Compose app for using a phone camera as a Bluetooth HID
keyboard-wedge barcode scanner.

## Disclaimer

The app is vibecoded and has not been reviewed yet. Expect the worst

## Goal

- Scan distributor labels with the phone camera.
- Preserve raw decoded bytes for debugging.
- Emit a normalized, printable barcode string plus `Enter` over Bluetooth HID.
- Work with SmartParts PureScan or any focused text field that accepts scanner-style keyboard input.

## Current State

- Kotlin and Jetpack Compose UI.
- ViewModel-owned app state.
- CameraX preview and ML Kit barcode analyzer.
- Data Matrix, QR, Code 128, and Code 39 enabled.
- Multi-barcode overlay with tap selection.
- Stable detection tracking with One Euro filtering.
- Raw text and raw-byte hex display.
- Android Bluetooth HID keyboard output.
- US keyboard HID keymap for printable ASCII plus `Enter`.

## Important Limitation

Bluetooth HID keyboards send key usages, not raw bytes. ASCII control separators such as GS, RS, and EOT should be parsed on-device and converted to a printable convention before sending to SmartParts.

## Build

```sh
./gradlew assembleDebug
```

## Notes

- Architecture notes: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- Refinement notes: [docs/REFINEMENT_NOTES.md](docs/REFINEMENT_NOTES.md)
