# CHIPWELL Android Inspection

CHIPWELL is an Android camera inspection app for chips moving on a conveyor. It uses Jetpack Compose, CameraX, TensorFlow Lite, Room, Bluetooth, and ESP32 integration.

## Inspection model and pipeline

The inspection workflow uses one model: `app/src/main/assets/chipwell_yolo26n.tflite`.

The checked asset SHA-256 is `7f564d6f8057d4968ac68994b702781f9c4326180876437df8893e10fd78fd00`. Its FlatBuffer tensor metadata reports input `[1, 3, 512, 512]` and output `[1, 6, 5376]`, both float tensors. The dense 5,376-candidate output has no embedded NMS operator; Android applies class-aware greedy NMS at IoU `0.70` with a 300-box cap. Detector input is RGB, float32 scaled to `[0,1]`, letterboxed to 512 square with value 114. The Android decoder treats each output candidate as `(centerX, centerY, width, height, chipScore, markingScore)`. That numeric decoder and preprocessing still need device-side comparison against the Colab reference before parity can be claimed.

Class ID 0 is `chip`; class ID 1 is `marking`. The post-processing configuration is centralized in `InspectionPostProcessor`:

- Chip confidence: `0.80`
- Marking confidence: `0.70`
- Relative chip and marking size tolerance: `0.25`
- Marking-to-chip overlap: at least `0.50` of marking area
- Markings nearest the top or right chip edge pass; left or bottom fail

Only accepted chips and retained markings appear in the camera overlay. The result popup contains only `PASS` or `FAIL`. Technical model or inference failures use the app's `ERROR` state and do not become product FAIL records. Inference and post-processing run away from the UI thread.

## Existing application features

Camera preview, camera stability handling, Bluetooth/ESP32 connectivity, conveyor workflow, result audio/voice alerts, result timing, diagnostics, Room inspection history, and navigation remain in the app.

Simulation Mode remains an explicit UI testing option. It does not act as a fallback model for production inspection.

## Architecture

- `com.example.chipvision.inference`: `ModelManager`, `DetectorService`, dense-output decoder, and NMS.
- `com.example.chipvision.engine`: inspection coordination, confidence eligibility, chip/marking post-processing, result dispatch, and performance metrics.
- `com.example.chipvision.tracking`: camera detection tracking and stability handling.
- `com.example.chipvision.camera`: CameraX preview and image analysis.
- `com.example.chipvision.audio`: result beep and voice announcements.
- `com.example.chipvision.ui`: Compose inspection, result popup, history, settings, and diagnostics screens.
