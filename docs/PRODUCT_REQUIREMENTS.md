# Product Requirements Document (PRD)
## DriftMobile: Professional Android Video Editor

---

## 1. Executive Summary
**DriftMobile** is a professional, native Android multi-track video editing application inspired by the creative ambition and feature depth of [Drift](https://github.com/CutWire-Studios/Drift), while completely redesigned from the ground up for mobile ergonomics and the Android operating system. 

Unlike desktop ports or web-wrapper video apps, DriftMobile is built natively with **Kotlin**, **Jetpack Compose**, **Material 3**, **AndroidX Media3**, and **OpenGL ES 3.0**. It delivers a touch-first editing experience rivaling tools like CapCut and VN, with:
- Zero forced subscriptions or accounts.
- Zero watermarks imposed on user exports.
- Complete privacy: 100% on-device editing and local processing.
- Non-destructive multi-track timeline engine.
- High-performance GPU shader pipeline shared identically across live preview and final export.

---

## 2. Product Vision & Principles
1. **Touch-First Ergonomics:** Designed for thumbs and fingers. Minimum touch target size of 48dp, fluid pinch-to-zoom timeline, tactile trim handles, and context-sensitive bottom toolbars.
2. **Determinism (What You See Is What You Export):** The exact shader math, keyframe easing curves, alpha compositing, and effect parameters evaluated during live preview are executed identically during final video export.
3. **Non-Destructive by Design:** Source files are never modified. Timeline edits, cuts, transitions, effects, and color grades are stored as structured project metadata with instant, limitless Undo/Redo.
4. **Hardware Acceleration & Battery Efficiency:** Leverage Android's hardware video decoders and encoders via `MediaCodec`, zero-copy GPU textures (`SurfaceTexture`), and OpenGL ES 3.0 shaders without draining battery through unnecessary software pixel copying.
5. **Rock-Solid Persistence & Crash Recovery:** Transaction-based state management backed by Room ensures projects are continuously auto-saved. If the app is interrupted or killed by the OS, the project resumes in the exact same state.

---

## 3. User Personas & Core Use Cases
- **Mobile Content Creators (Reels, TikTok, Shorts):** Quick import, vertical (9:16) and landscape (16:9) aspect ratios, beat-snapping, trending GPU transitions, text styling, and instant high-bitrate export.
- **Vloggers & Storytellers:** Multi-track editing with A-roll video, B-roll overlays, voiceover recording, background music ducking, and auto-captions.
- **Mobile Filmmakers:** Precise frame-accurate trimming, multi-layer compositing, keyframe animation (position, scale, rotation, opacity), color grading wheels/curves/LUTs, and chroma keying.

---

## 4. Feature Inventory & Scope

### 4.1 Home & Project Management
- **Project Catalog:** Grid/list of existing projects with auto-generated preview thumbnails, creation date, duration, and resolution badge.
- **New Project Flow:** Aspect ratio selection (16:9, 9:16, 1:1, 4:5, 21:9), project name, custom frame rates (24, 30, 60 fps).
- **Project Duplication & Deletion:** Safe management with deletion confirmation.
- **Crash & Interruption Recovery:** Automatic detection of unfinished sessions with one-tap restore.

### 4.2 Media Import & Asset Bin
- **Safe Media Access:** Modern Android Photo Picker and Storage Access Framework (SAF) integration with persistent URI permissions.
- **Multi-Format Support:** MP4, MOV, MKV, WebM video; PNG, JPEG, WebP images; MP3, WAV, AAC, M4A audio.
- **Metadata Extraction:** Resolution, duration, codec, bitrate, frame rate, audio channels/sample rate.
- **Local Asset Cache:** Thumbnail generation and caching for instant timeline filmstrip rendering.

### 4.3 Timeline & Editing Workspace
- **Multi-Track Architecture:**
  - Multiple video/visual tracks (V1, V2, etc. with layer ordering: higher tracks composite over lower tracks).
  - Multiple audio tracks (A1, A2, etc. for dialogue, sound effects, background music).
  - Dedicated text and overlay tracks.
- **Touch-Based Interactions:**
  - Horizontal drag to scrub playhead.
  - Two-finger pinch to zoom timeline in/out (from individual frames to multi-minute overviews).
  - Clip selection with visual bounding highlight.
  - Clip trimming via left and right drag handles with edge feedback.
  - Long-press to drag and reorder clips between tracks.
  - Magnetic snapping to playhead, clip boundaries, and marker points.
- **Core Operations:**
  - Split at Playhead (instant cut).
  - Trim In / Trim Out.
  - Ripple Delete (closing gaps automatically) and Standard Delete.
  - Duplicate Clip.
  - Change Playback Speed (0.1x to 10.0x with pitch correction).
  - Reverse Clip.
  - Freeze Frame generation.
  - Track-level controls: Mute, Solo, Hide, Lock.
- **Undo/Redo System:** Command-pattern transaction stack allowing infinite undo/redo without memory ballooning.

### 4.4 GPU Preview & Rendering Engine
- **OpenGL ES 3.0 Compositor:** Real-time multi-layer composition pipeline.
- **Transformations:** Scale (uniform and non-uniform), Translation (X, Y), Rotation (degrees), Opacity (0.0 to 1.0).
- **Blend Modes:** Normal, Add, Multiply, Screen, Overlay, Darken, Lighten, Color Dodge.
- **GPU Effects Pipeline:**
  - Color adjustments: Brightness, Contrast, Saturation, Exposure, Temperature, Tint, Vibrance.
  - Stylize: Vignette, Film Grain, Sharpen, Gaussian Blur, Box Blur, Chromatic Aberration, Grayscale, Sepia, Invert.
  - Multi-pass shaders with offscreen Framebuffer Objects (FBOs).
- **GPU Transitions:**
  - Cross Dissolve, Dip to Black, Dip to White.
  - Wipe (Left, Right, Up, Down).
  - Push / Slide.
  - Zoom In / Zoom Out.
  - Adjustable transition duration (0.1s to 5.0s) and transition easing curves.
- **Chroma Key (Green/Blue Screen):**
  - Custom key color picker.
  - Similarity, smoothness, and spill suppression sliders.
  - Real-time alpha masking.

### 4.5 Keyframe Animation System
- **Keyframeable Properties:** Position X/Y, Scale X/Y, Rotation, Opacity, Volume, and Effect Parameters.
- **Interpolation Engines:**
  - Linear.
  - Ease-In, Ease-Out, Ease-In-Out (Quad, Cubic, Quartic).
  - Elastic and Bounce easing curves.
  - Cubic Bezier curves with adjustable control points `(P1x, P1y, P2x, P2y)`.

### 4.6 Audio Engine & Mixing
- **Multi-Track Mixing:** Simultaneous playback and mix of video audio and auxiliary audio tracks.
- **Volume & Gain:** -60 dB to +12 dB with smooth volume envelope interpolation.
- **Fade Transitions:** Automatic or custom Fade In and Fade Out durations.
- **Waveform Rendering:** Cached audio peak waveforms for precise beat-matching and dialogue cuts.
- **Audio Ducking:** Automatic attenuation of background music tracks when speech/voiceover is detected.

### 4.7 Text, Titles & Subtitles
- **Vector Text Rendering:** Dynamic GPU texture generation with custom fonts, colors, stroke widths, drop shadows, and background bounding boxes.
- **Subtitle System:** Subtitle track with time-coded cues, SRT file import and export.
- **Text Animations:** Fade, slide, typewriter, and scale entrance/exit animations.

### 4.8 Export & Delivery
- **Resolutions:** 720p (HD), 1080p (Full HD), 1440p (2K), 2160p (4K UHD, where device hardware supports).
- **Aspect Ratios:** 16:9, 9:16, 1:1, 4:5, 21:9 with letterboxing or pillarboxing.
- **Frame Rates:** 24 fps, 30 fps, 60 fps.
- **Encoding Formats:** H.264 (AVC) and H.265 (HEVC) MP4 with AAC audio.
- **Bitrate Control:** Variable Bitrate (VBR) with presets (Low, Standard, High, Ultra).
- **Export UX:** Real-time progress bar, estimated time remaining, background execution safety, cancel button, direct Android Share Sheet integration.

---

## 5. Non-Functional Requirements
- **Performance:** 
  - Preview playback at stable 30/60 fps without dropping frames on mid-range ARM64 devices.
  - Zero UI thread blocking (all decoding, shader compilation, and database I/O run on background dispatchers).
  - Peak memory usage constrained under 350 MB even with 4K assets loaded on the timeline.
- **Reliability:**
  - Zero data loss on unexpected process kill or app backgrounding.
  - Graceful handling of OpenGL context loss (EGL lifecycle).
  - Fallback logic for unsupported hardware codecs.
- **Compatibility:**
  - Min SDK: Android 8.0 (API level 26).
  - Target SDK: Android 15 (API level 35).
  - Architectures: `arm64-v8a` (primary target), `armeabi-v7a`, `x86_64` (emulator).
