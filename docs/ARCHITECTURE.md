# Architecture

This app is a Kotlin / Jetpack Compose rewrite of the original Java prototype.
The package and application ID remain `dev.partscanner.hid` so debug APKs upgrade
the existing installation.

## Layers

`domain`

- Plain scanner models:
  - `ScannedBarcode`
  - `DetectedBarcode`
  - `BluetoothHost`
  - `ScannerUiState`
- No Android UI dependencies except platform value types already emitted by
  CameraX (`RectF`) and Bluetooth device handles.

`barcode`

- Camera analyzer and barcode-specific processing.
- `BarcodeAnalyzer` owns CameraX `ImageAnalysis.Analyzer` + ML Kit decoding.
- Barcode formats are app-owned settings mapped into ML Kit scanner options
  whenever the camera analyzer is rebound.
- `BarcodeTextNormalizer` converts raw control bytes to printable SmartParts
  tokens such as `{GS}`, `{RS}`, and `{EOT}` before HID typing.
- `BarcodeParser` provides lightweight distributor-label previews and extracts
  a likely manufacturer part number for MPN-only send mode.
- `StableBarcodeTracker` preserves barcode identity across a small number of
  missed analyzer results and smooths box geometry.
- `OneEuroFilter` is used per rectangle edge to reduce jitter without making box
  motion feel too sluggish.

`bluetooth`

- Android Bluetooth HID profile wrapper.
- `BluetoothHidManager` is intentionally separate from Compose and ViewModel
  code.
- `HidKeyMapper` maps printable ASCII to USB HID keyboard usages.
- HID send speed is configurable with key-down and inter-key delays.
- This is Bluetooth Classic HID Device, not BLE. The name can be changed later
  if you introduce a separate BLE scanner mode.

`ui`

- Compose UI and ViewModel.
- `ScannerViewModel` owns app state, selection, scan lock, stable detection
  updates, parser preview state, and HID send calls.
- `ScannerSettingsRepository` persists scanner settings in SharedPreferences.
- `CameraPreview` binds CameraX and feeds decoded detections to the ViewModel.
- `BarcodeOverlay` draws transformed rectangles and handles tap selection.
- `ScannerScreen` handles permissions, host picker, and the fixed-height control
  panel.

## Data Flow

```text
CameraX frame
  -> BarcodeAnalyzer / ML Kit
  -> transformed DetectedBarcode list
  -> ScannerViewModel
  -> StableBarcodeTracker
  -> ScannerUiState
  -> Compose overlay + control panel
  -> user taps or auto-selects
  -> BarcodeTextNormalizer
  -> BluetoothHidManager
  -> host receives keyboard text + Enter
```

## Intentional Choices

- The bottom panel has a fixed height so decoded text cannot resize the preview.
- The overlay uses CameraX `ImageProxy -> PreviewView` coordinate transforms,
  not hand-rolled scaling.
- Multi-barcode frames require tap selection unless there is exactly one stable
  barcode.
- Stable boxes tolerate a small number of missed analyzer frames by count, not
  by elapsed wall-clock time.
- Scan lock freezes the current detection set without stopping CameraX.
- HID pacing keeps a 1 ms key-down delay. Removing all pacing caused Android /
  Linux HID queue behavior to feel worse.

## Current Caveats

- `BluetoothHidManager` still wraps callback-style Android APIs directly. A
  future pass could expose a stronger typed state machine instead of status text.
- The UI currently uses local Compose functions rather than a formal design
  system.
- `DetectedBarcode` carries `RectF`; if you want a purer domain layer, split
  geometry into an app-owned rectangle type.
- Dependency injection is manual. Hilt/Koin is unnecessary right now.
