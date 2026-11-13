# Development Roadmap
## DriftMobile: Phased Implementation Plan

---

## Overview

The development of DriftMobile follows a rigorous, test-verified incremental plan. Each milestone produces a fully buildable and verifiable Android state before advancing to subsequent features.

---

## Phased Milestones

```
[Phase 0: Architecture & Research] (COMPLETED)
       │
       ▼
[Phase 1: Native Android Foundation & Shell]
  - Gradle, Kotlin DSL, Jetpack Compose, Material 3
  - Hilt Dependency Injection, Navigation
  - Room Database & Project Metadata Entity
  - Home Screen (Project List, New Project, Import)
  - Media Picker integration with SAF
       │
       ▼
[Phase 2: Media & Playback Foundation]
  - Media3 ExoPlayer integration
  - Video import, metadata parsing, thumbnail generator
  - Single-clip video player & frame-accurate scrubbing
  - Baseline Media3 Transformer export pipeline (Proof-of-concept MP4 export)
       │
       ▼
[Phase 3: Multi-Track Timeline Engine]
  - Immutable domain models (Project, Track, Clip)
  - Integer microsecond timeline clock (`Long Us`)
  - Touch-based multi-track timeline UI (Pinch-zoom, scroll, playhead scrub)
  - Non-destructive clip editing (Split, Trim, Move, Delete, Duplicate)
  - Command-pattern Undo/Redo manager
  - Automatic debounced project persistence & crash recovery
       │
       ▼
[Phase 4: OpenGL ES 3.0 Rendering & GPU Effects]
  - EGL context & GPU surface compositor
  - Multi-layer visual compositing (Track stacking order)
  - Spatial transformations (Scale, Translation, Rotation, Anchor)
  - GLSL Shader effects pipeline (Brightness, Contrast, Saturation, Vignette, Blur, Grayscale)
  - GPU transitions (Cross Dissolve, Dip to Black/White, Wipe, Push, Zoom)
  - Shared preview & export shader execution guarantee
       │
       ▼
[Phase 5: Keyframing, Animation & Advanced Visuals]
  - Keyframe data structures & temporal tracks
  - Interpolation engine: Linear, Ease-in/out, Elastic, Bounce, Cubic Bezier
  - Chroma Key shader (Green/Blue screen removal with spill suppression)
  - Vector text rendering & animated title overlays
       │
       ▼
[Phase 6: Multi-Track Audio Engine]
  - Audio mixing pipeline (ExoPlayer audio sink / multi-stream mixing)
  - Volume envelopes & logarithmic dB scaling
  - Fade-in / Fade-out curves
  - Audio waveform peak generation & cached timeline rendering
  - Voiceover recording & audio ducking
       │
       ▼
[Phase 7: Optional On-Device AI Assistance]
  - Local speech-to-text inference (offline Whisper / Vosk / TFLite)
  - Automatic subtitle generation & timeline cue synchronization
  - Silence detection & jump-cut generation
  - Scene boundary detection
       │
       ▼
[Phase 8: Optimization, Hardening & Release]
  - Hardware decoder limit management & virtual track pooling
  - Low-memory & thermal throttling adaptors
  - Extensive unit tests & integration tests across API levels
  - Signed release build & Play Store packaging
```

---

## Detailed Milestone Checklists

### Milestone 1: Native Android Foundation & Home Screen
- [x] Analyze reference repository and produce technical documentation.
- [ ] Initialize Android project with Gradle Kotlin DSL (`build.gradle.kts`, `settings.gradle.kts`, `libs.versions.toml`).
- [ ] Configure target SDK 35, compile SDK 35, min SDK 26, Java 21, Kotlin 2.0+.
- [ ] Set up Jetpack Compose with Material 3 dark studio theme.
- [ ] Configure Hilt DI container.
- [ ] Set up Room database for `ProjectEntity`, `TrackEntity`, `ClipEntity`.
- [ ] Implement Home Screen:
  - Display recent project cards with title, aspect ratio, duration, date.
  - "New Project" bottom sheet with aspect ratio and FPS selection.
  - Media picker integration using Android SAF / Photo Picker.
- [ ] Verify clean compilation and launch on Android emulator/device.

### Milestone 2: Media & Playback Foundation
- [ ] Integrate AndroidX Media3 ExoPlayer and Media3 Transformer.
- [ ] Implement `MediaMetadataExtractor` (resolution, duration, bitrate, frame rate, audio channels).
- [ ] Implement `ThumbnailGenerator` with memory and disk caching.
- [ ] Build basic single-clip preview player with play/pause, scrub slider, and timestamp.
- [ ] Build basic single-clip trim and export pipeline producing a valid MP4 file.
- [ ] Verify exported MP4 playback in system video player.

### Milestone 3: Multi-Track Timeline Engine
- [ ] Implement domain models: `Project`, `Track`, `Clip`, `EditCommand`.
- [ ] Implement `TimelineEngine` with microsecond accuracy.
- [ ] Build interactive Compose `TimelineView`:
  - Canvas-based multi-track layout.
  - Pinch-to-zoom gesture detector for time scale.
  - Horizontal scroll gesture detector for scrubbing.
  - Draggable playhead indicator.
  - Clip cards with thumbnail filmstrips.
  - Clip trimming handles with edge detection.
- [ ] Implement `CommandManager` supporting Undo and Redo.
- [ ] Implement Split at Playhead, Trim, Move, Delete, and Duplicate.
- [ ] Implement Room-backed autosave and crash recovery manager.
- [ ] Unit tests for timeline calculations and command stack.

### Milestone 4: GPU Rendering & Effect Pipeline
- [ ] Implement OpenGL ES 3.0 renderer with EGL display and context management.
- [ ] Implement `TextureManager` and `SurfaceTexture` listeners for hardware video decoding.
- [ ] Build shader program compiler and uniform binder.
- [ ] Implement core GLSL shaders:
  - Transform & Alpha Blending.
  - Color adjustments (Brightness, Contrast, Saturation, Temperature, Tint).
  - Vignette, Gaussian Blur, Grayscale, Sepia.
- [ ] Implement GPU Transitions (Cross Dissolve, Dip to Black, Wipe, Push, Zoom).
- [ ] Build Media3 Transformer custom `GlEffect` pipeline using the exact same shaders for export.
- [ ] Verify preview/export visual consistency.

### Milestone 5: Keyframes & Advanced Editing
- [ ] Keyframe data model and timeline visualization.
- [ ] Cubic Bezier curve evaluator and standard easing presets.
- [ ] Chroma Key shader with color picker, tolerance, smoothness, and spill suppression.
- [ ] Vector text overlay engine with font styling and animations.

### Milestone 6: Audio Engine
- [ ] Multi-track audio mixer.
- [ ] Waveform extractor generating peak min/max arrays.
- [ ] Compose waveform display along audio clips.
- [ ] Volume envelopes, fade-in/fade-out, and auto-ducking.

### Milestone 7: On-Device AI Features
- [ ] Local speech-to-text pipeline for automatic subtitle generation.
- [ ] Model download manager with progress tracking and storage verification.
- [ ] Silence detection algorithm for automatic jump cuts.

### Milestone 8: Hardening & Performance
- [ ] Memory profiling and leak checks.
- [ ] Thermal throttling and adaptive preview quality scaling.
- [ ] Release signing and ProGuard/R8 optimization rules.
