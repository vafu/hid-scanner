# Refinement Notes

## Current Backlog

- Replace remaining status strings with explicit UI state:
  - scanner/camera state
  - permission state
  - HID registration state
- Move CameraX and ML Kit binding behind a `BarcodeDetector` interface before
  adding alternate detector implementations or deeper test coverage.
- Move HID profile callbacks into a reducer/state-machine if connection edge
  cases keep growing.
- Add stronger parser coverage for real distributor labels:
  - Mouser ANSI / ECIA labels
  - LCSC JSON-ish labels
  - Digi-Key labels
- Add `StableBarcodeTracker` tests after splitting geometry away from Android
  `RectF` or adding Robolectric.
- Add screenshot tests after the UI settles.

## Completed

- Add a visible scan freeze / lock control.
- Add HID speed presets:
  - fast: `0 ms` key-down, `0 ms` inter-key gap
  - normal: `1 ms` key-down, `0 ms` inter-key gap
  - compatible: `1 ms` key-down, `1 ms` inter-key gap
- Add parser preview and an MPN-only send mode with full normalized output as
  the default.
- Persist scanner settings through a small settings repository.
- Add barcode format toggles.
- Add missed-frame tolerance to `StableBarcodeTracker`.
- Add unit tests for `BarcodeTextNormalizer` and basic parser heuristics.

## Known External Constraint

SmartParts still needs supplier-side support to make scanned LCSC labels useful
end-to-end. The phone can emit the label payload, but SmartParts needs parser
logic and/or an LCSC API/import source to enrich parts automatically.
