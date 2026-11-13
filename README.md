# Flash — Professional Mobile Video Editor for Android

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" height="96" alt="Flash Icon" />
</p>

<p align="center">
  <b>A mobile-first, high-performance, non-destructive video editing workstation for Android.</b>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License: MIT"></a>
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-green.svg" alt="Platform">
  <img src="https://img.shields.io/badge/Language-Kotlin%202.0-purple.svg" alt="Kotlin">
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20%2F%20Material%203-blueviolet.svg" alt="Jetpack Compose">
  <img src="https://img.shields.io/badge/Media-AndroidX%20Media3%201.5-red.svg" alt="Media3">
  <a href="https://github.com/twishamp2024/Flash/releases/tag/v1.0.0"><img src="https://img.shields.io/badge/Release-v1.0.0-orange.svg" alt="Release"></a>
</p>

<p align="center">
  <a href="docs/HUMAN_ARCHITECTURE_GUIDE.md"><b>📖 Human Architecture Guide</b></a> •
  <a href="docs/AI_AGENT_CONTEXT.md"><b>🤖 AI Agent Vibecoding Spec</b></a> •
  <a href="graphify-out/GRAPH_REPORT.md"><b>🕸️ Knowledge Graph Report</b></a> •
  <a href="https://github.com/twishamp2024/Flash/releases/download/v1.0.0/Flash.apk"><b>⚡ Download Flash.apk</b></a>
</p>

---

## ⚡ About Flash

**Flash** is a genuine native Android video editor engineered specifically for touch ergonomics on smartphones and tablets. It is **not a desktop port**, **not a WebView wrapper**, and **not a prototype**. 

Flash provides a mobile-native editing experience comparable to professional mobile editors like CapCut and VN, while maintaining complete privacy and freedom:
- 🔒 **100% Offline-First:** Operates entirely on-device with zero server dependencies.
- 👤 **No Account Required:** Start editing immediately without signing in or registering.
- 🚫 **No Forced Watermarks:** Never stamps logos or watermarks onto your exported videos.
- 💎 **No Subscriptions or Paywalls:** All features and export presets are permanently free and open.
- ⚖️ **Permissive MIT License:** Clean-room independent architecture.

---

## 📚 Technical Documentation & Knowledge Graph

For engineers and AI agents wishing to extend Flash or explore its inner workings:

| Document | Target Audience | Focus & Contents |
| :--- | :--- | :--- |
| [**Human Architecture Guide**](docs/HUMAN_ARCHITECTURE_GUIDE.md) | Human Engineers & Architects | Exhaustive deep-dive into every subsystem: microsecond timeline clock math, command pattern rollback, OpenGL ES 3.0 GLSL shaders, Media3 pipeline, typography engine, on-device AI algorithms, and file-by-file directory guide. |
| [**AI Agent Vibecoding Spec**](docs/AI_AGENT_CONTEXT.md) | AI Coding Assistants & LLMs | **Token-optimized specification** with high semantic density. Contains symbol tables, API signatures, system invariants, shader matrices, extension recipes, and critical gotchas. |
| [**Knowledge Graph Report**](graphify-out/GRAPH_REPORT.md) | System Analysts & Contributors | Structural AST analysis of the Android source code: 697 nodes, 1,404 relationships, god nodes, cluster analysis, and cross-module bridges. |
| [**Interactive Graph Visualization**](graphify-out/graph.html) | Visual Exploration | Standalone interactive HTML graph (open in browser) to explore code relationships, call hierarchies, and architectural modules. |

---

## ✨ Features

### 🎬 Multi-Track Non-Destructive Timeline
- **Multi-Layer Composition:** Dedicated, independent tracks for Video, Audio, and Text/Graphics overlays.
- **Microsecond Precision Clock (`Long Us`):** All timeline math, clipping, splits, and effect timings are calculated with integer microsecond precision to eliminate IEEE-754 floating-point drift.
- **Atomic Undo / Redo:** Full transactional command history (`SplitClipCommand`, `TrimClipCommand`, `MoveClipCommand`, `DuplicateClipCommand`, `DeleteClipCommand`, `UpdateClipVolumeCommand`, `UpdateClipSpeedCommand`, etc.).
- **Interactive Gestures:** Pinch-to-zoom timeline scaling, horizontal scroll scrubbing, drag handles for precise trimming, and track lock/mute toggles.

### 🎨 GPU Shader Pipeline & Color Grading
- **Real-Time OpenGL ES 3.0 Processing:** Shaders running on GPU textures via AndroidX Media3 `GlEffect` pipeline.
- **Built-in Filter & Adjustment Presets:**
  - Brightness & Contrast
  - Saturation & Hue Adjustment
  - Vignette with smooth falloff
  - Grayscale, Color Inversion, and Warm Sepia
- **WYSIWYG Guarantee:** The exact shader pipeline evaluated during preview playback is replicated during hardware export.

### 🎵 Audio Engine & Waveform Analysis
- **Fine-Grained Volume Control:** 0% to 200% gain adjustment per clip with instant mute.
- **Speed Ramping:** Smooth playback speed adjustment (0.25x slow-motion up to 3.0x fast-forward) with synchronized timeline duration recalculation.
- **Audio Detachment:** Extract embedded audio from video clips into dedicated audio tracks.
- **Visual Waveforms:** Background amplitude peak analysis providing visual audio waveforms on the timeline.

### ✍️ Dynamic Text Overlays
- **ARGB_8888 Canvas Engine:** High-DPI text bitmap rendering using Android Canvas and `StaticLayout`.
- **Custom Styling:** Rich color palette presets, adjustable font sizing (sp), drop shadows, and stroke outlines.
- **GPU Texture Mapping:** Projected seamlessly into video frames using Media3 `OverlayEffect`.

### 🤖 On-Device AI Features
- **Smart Auto-Cut:** Audio amplitude profiling automatically detects silence intervals and generates atomic jump-cuts to tighten talking-head clips.
- **Auto Subtitles:** On-device speech cadence analyzer generates synchronized text subtitle clips directly on the timeline.

### 🚀 Hardware-Accelerated Video Export
- **AndroidX Media3 Transformer Engine:** Hardware MediaCodec encoding for fast rendering.
- **Quality Presets:** 720p HD, 1080p Full HD, and 4K UHD.
- **Custom Frame Rates & Bitrates:** 24 FPS, 30 FPS, and 60 FPS.
- **Public Gallery Integration:** Automatically registers exported videos in `Movies/Flash` via Android MediaStore.
- **System Share Sheet:** Instant one-tap sharing to messaging and social media apps.

---

## 🏗️ Architecture

Flash follows **Clean Architecture** and feature-based modularization:

```
app/src/main/java/org/cutwire/drift/
├── core/
│   ├── database/        # Room Database (ProjectEntity, AssetEntity, TypeConverters)
│   ├── designsystem/    # Material 3 Dark Pro Theme (Colors, Typography, Shapes)
│   ├── model/           # Immutable Domain Models (Project, Track, Clip, Effect, TextOverlay)
│   └── storage/         # SAF Media Ingestion, Project Serializer & Cache Management
├── engine/
│   ├── ai/              # On-Device SmartAutoCutEngine & AutoSubtitleEngine
│   ├── audio/           # AudioWaveformExtractor & Audio Mixing
│   ├── command/         # Command Pattern Engine (CommandManager, EditCommand, Rollback)
│   ├── export/          # Media3 Transformer Video Export Engine
│   ├── player/          # Media3 ExoPlayer Composition & Playback Engine
│   └── render/          # OpenGL ES 3.0 GLSL Shaders & TextBitmapRenderer
└── feature/
    ├── editor/          # EditorScreen, EditorViewModel, Timeline UI, Property Sheets
    ├── home/            # HomeScreen, HomeViewModel, Project Creation & About Dialog
    └── timeline/        # MultiTrackTimeline, Ruler, Playhead & Filmstrip Strip
```

---

## 📱 Download & Install

Download the latest ready-to-install APK from the [Releases](https://github.com/twishamp2024/Flash/releases) page:
- **Download:** [`Flash.apk`](https://github.com/twishamp2024/Flash/releases/latest/download/Flash.apk)
- **Minimum Requirement:** Android 8.0 (API Level 26) or higher.
- **Target SDK:** Android 14 (API Level 34).

---

## 🛠️ Building from Source

### Prerequisites
- Android Studio Ladybug / Meerkat or later
- JDK 17 or JDK 21 (Eclipse Adoptium Recommended)
- Android SDK 34 with Build Tools 34.0.0

### Build Commands
Clone the repository:
```bash
git clone https://github.com/twishamp2024/Flash.git
cd Flash
```

Build the debug APK:
```bash
./gradlew assembleDebug --no-daemon
```
The output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

Run all unit tests:
```bash
./gradlew testDebugUnitTest --no-daemon
```

Install directly onto a connected device or emulator:
```bash
./gradlew installDebug --no-daemon
```

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.
