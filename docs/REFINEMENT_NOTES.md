# Refinement Notes

## Near-Term Improvements

- Add a visible scan freeze / lock button. This would make selecting from dense
  labels easier than relying only on stable tracking.
- Add a small HID speed setting:
  - fast: `0 ms` gap, risky
  - normal: `1 ms` key-down, current default
  - compatible: `1 ms` key-down + `1 ms` inter-key gap
- Add parser previews for known distributors:
  - Mouser ANSI / ECIA labels
  - LCSC JSON-ish labels
  - Digi-Key labels
- Add a "send parsed MPN only" mode as a fallback, but keep full raw normalized
  output as the default because SmartParts can extract quantity and supplier SKU.

## Architecture Ideas

- Replace plain status text with:
  - `BluetoothState`
  - `ScannerState`
  - `PermissionState`
- Introduce repositories if the app starts storing settings.
- Move CameraX binding behind a `BarcodeDetector` interface if we add tests or
  alternate detection engines.
- Move HID profile callbacks into a reducer/state-machine if connection edge
  cases keep growing.

## Testing Ideas

- Unit-test `BarcodeTextNormalizer` with raw GS / RS / EOT byte samples.
- Unit-test `StableBarcodeTracker`:
  - detections survive the configured number of missed frames
  - detections are removed after the miss budget is exceeded
  - reappearing identities get a fresh smoothing filter
  - selection identity remains stable across jittery boxes
- Add screenshot tests only after the UI stabilizes; the current layout is still
  intentionally easy to change.

## Known External Constraint

SmartParts still needs supplier-side support to make scanned LCSC labels useful
end-to-end. The phone can emit the label payload, but SmartParts needs parser
logic and/or an LCSC API/import source to enrich parts automatically.
