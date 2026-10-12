# CHIPVISION — Automated Chip Inspection System

CHIPVISION is an industrial-grade automated vision inspection application for electronic chips moving on a conveyor belt. It runs offline on native Android using Jetpack Compose, CameraX, LiteRT/TensorFlow Lite, and Room database persistence.

---

## Key Capabilities & Core Rules

1. **Continuous Real-Time Conveyor Monitoring**
   - The live camera feed observes the conveyor continuously.
   - The operator moves the physical conveyor with the paddle; CHIPVISION automatically locks onto each new stationary scene and conducts the inspection cycle without requiring manual Start presses after every cycle.

2. **1.5-Second Active Observation Stability Validation**
   - Candidates must maintain strict geometric stability (width/height ratio $\le 20\%$, $\text{IoU} \ge 0.80$, center displacement) and global scene stability for at least **1500 ms** uninterrupted.
   - Any conveyor or scene motion instantly resets the stability interval, invalidates pending crops, and hides affected candidates.

3. **Completely Hidden Unstable Overlays**
   - In compliance with the non-negotiable specification, **unstable detections are completely invisible**. No bounding boxes, labels, or placeholders appear until candidates have achieved verified stability.

4. **Empty-Impression-First Decision Rule**
   - Evaluates confirmed stable empty impressions first.
   - If **at least one confirmed empty impression exists**, the cycle immediately finalizes as **FAIL** (`FAIL_EMPTY_IMPRESSION_PRESENT`).
   - Empty impressions are displayed in **red** labelled `EMPTY IMPRESSION`. Empty impressions are never sent to the classifier, and chips in that failed cycle are not classified.

5. **Chip Classification & Centralized PASS/FAIL Engine**
   - When no empty impressions exist, every valid stable chip is cropped from the retained stable source frame.
   - Identifies chip type (`PCS.06.K` or `PCS.26.C`) and orientation (`correct_upward`, `downward`, `flipped`).
   - Minimum classifier confidence threshold is $\ge 0.70$.
   - **PASS** is granted only when all chips are confidently identified as `PCS.06.K` or `PCS.26.C` and classified as `correct_upward`.

6. **Audio & Voice Alerts**
   - Multi-tone industrial buzzer (950 Hz Industrial Beep, 1600 Hz High Tone, 600 Hz Warning Pulse) with configurable count, gap, and volume.
   - Android TextToSpeech voice announcements ("Pass", "Fail: Empty impression detected", etc.).

7. **Local Persistence & Statistics**
   - Real-time frame statistics, current cycle metrics, and cumulative totals (Total, PASS, FAIL, Yield %).
   - Comprehensive filterable inspection history stored locally via Room Database.

---

## Integrating Your Trained TFLite Models

Place your two trained `.tflite` model files into the Android assets directory:

```
app/src/main/assets/chipvision_detector_direct.tflite
app/src/main/assets/chipvision_classifier.tflite
```

### Verified Tensor Specifications
- **Detector** (`chipvision_detector_direct.tflite`):
  - Input: `[1, 3, 512, 512]` float32 (or `[1, 512, 512, 3]`), automatically detected
  - Output: `[1, 300, 6]` `[x1, y1, x2, y2, confidence, classId]`
  - Confidence threshold: strictly $> 0.85$ (rejection at $\le 0.85$)
  - Class 0 = `chip`, Class 1 = `empty`

- **Classifier** (`chipvision_classifier.tflite`):
  - Input: `[1, 3, 224, 224]` float32 (or `[1, 224, 224, 3]`) with ImageNet normalization
  - Output: 6 classes (`PCS_06_K_correct_upward`, `PCS_06_K_downward`, `PCS_06_K_flipped`, `PCS_26_C_correct_upward`, `PCS_26_C_downward`, `PCS_26_C_flipped`)
  - Confidence threshold: $\ge 0.70$

*Note: If testing without physical models loaded, enable "Lab / Demo Simulation Mode" in the Settings tab to test state transitions, audio alerts, and UI flows.*

---

## Architecture

- **`com.example.chipvision.data`**: Domain models (`Detection`, `TrackedCandidate`, `InspectionDecision`), Room database (`InspectionRecord`, `InspectionDao`, `InspectionDatabase`), and DataStore `SettingsRepository`.
- **`com.example.chipvision.inference`**: `ModelManager`, `DetectorService`, `ClassifierService`.
- **`com.example.chipvision.tracking`**: `DetectionTracker`, `StabilityValidator`.
- **`com.example.chipvision.engine`**: `InspectionCoordinator`, `InspectionDecisionEngine`.
- **`com.example.chipvision.camera`**: `CameraController` (CameraX Preview + ImageAnalysis).
- **`com.example.chipvision.audio`**: `AudioAlertManager` (PCM tone generator + TTS).
- **`com.example.chipvision.ui`**: `ChipVisionViewModel`, `InspectionScreen`, `HistoryScreen`, `SettingsScreen`, `BoundingBoxOverlay`.
