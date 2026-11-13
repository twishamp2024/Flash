# System Architecture Document
## DriftMobile: Mobile-First Android Video Editing Engine

---

## 1. Architectural Overview & Design Philosophy

DriftMobile follows **Clean Architecture** principles combined with **Unidirectional Data Flow (UDF)** and a **modular, engine-driven subsystem design**.

```
+--------------------------------------------------------------------------+
|                          UI Layer (Jetpack Compose)                      |
|   HomeScreen  |  EditorScreen  |  Timeline  |  PreviewView  |  ExportSheet|
+--------------------------------------------------------------------------+
                                     |
                                StateFlow / Events
                                     v
+--------------------------------------------------------------------------+
|                           ViewModel Layer (MVI)                          |
|   EditorViewModel  |  TimelineViewModel  |  PreviewViewModel | ExportVM  |
+--------------------------------------------------------------------------+
                                     |
                                UseCases / Commands
                                     v
+--------------------------------------------------------------------------+
|                     Domain & Timeline Engine Layer                       |
|   TimelineEngine   |  CommandManager (Undo/Redo)  |  ProjectRepository   |
|   Timeline Clock   |  KeyframeInterpolator        |  TransitionEvaluator |
+--------------------------------------------------------------------------+
                                     |
                +--------------------+--------------------+
                v                                         v
+-------------------------------+         +--------------------------------+
|      Preview Rendering        |         |        Export Processing       |
|  - OpenGL ES 3.0 Compositor   |         |  - Media3 Transformer /        |
|  - SurfaceView / TextureView  |         |    MediaCodec Hardware Pipeline|
|  - Multi-pass GLSL Shaders    |         |  - Shared OpenGL ES Shaders    |
|  - SurfaceTexture Video Feeds |         |  - MediaMuxer MP4 Output       |
+-------------------------------+         +--------------------------------+
                |                                         |
                +--------------------+--------------------+
                                     v
+--------------------------------------------------------------------------+
|                       Media & Platform Layer                             |
|  - AndroidX Media3 (ExoPlayer)        - Room Database (Autosave/Recovery)|
|  - Android MediaCodec / MediaExtractor- Android SAF (Scoped Storage)     |
|  - EGL Context & GL Resource Manager  - Coroutines & Kotlin Flow         |
+--------------------------------------------------------------------------+
```

### Key Architectural Tenets:
1. **Separation of Concerns:** The timeline composition engine has zero dependencies on Android UI / Compose. It operates purely on immutable domain models and high-precision time calculations (`Long` microseconds).
2. **Single Deterministic Source of Truth:** The `Project` model represents the complete non-destructive edit state. Preview rendering and export rendering are both purely functional evaluations of `Project` at timestamp $t$.
3. **Shared GPU Shader Pipeline:** The same GLSL vertex and fragment shaders are compiled and executed for both the on-screen preview viewport and off-screen export frame buffers.

---

## 2. Package & Modular Structure

The codebase is organized into clean, focused layers:

```
f:\DriftMobile\
├── app/                               # Main application entry point & DI configuration
│   └── src/main/java/org/cutwire/drift/
│       ├── DriftApplication.kt       # Application class & initialization
│       ├── MainActivity.kt           # Single Activity host
│       └── di/                       # Hilt dependency injection modules
│
├── core/
│   ├── common/                       # Result monads, Dispatcher providers, Logger
│   ├── model/                        # Core domain models (Project, Track, Clip, Effect, Keyframe)
│   ├── database/                     # Room database, DAO, entities, converters
│   ├── storage/                      # SAF URI management, cache directories, file I/O
│   ├── media/                        # Metadata extractor, thumbnail generator, waveform extractor
│   └── designsystem/                 # Compose theme, colors, typography, icons, buttons
│
├── engine/
│   ├── timeline/                     # TimelineEngine, TrackEngine, ClipOperations, Snapping
│   ├── command/                      # Command pattern undo/redo engine
│   ├── composition/                  # Layer composition, spatial transforms, blend modes
│   ├── preview/                      # OpenGL ES 3.0 SurfaceView/TextureView preview pipeline
│   ├── rendering/                    # GLSL shaders, FBO ping-pong, textures, EGL context
│   ├── audio/                        # Audio mixing, volume envelopes, ducking
│   ├── animation/                    # Keyframe interpolation (Linear, Bezier, Easing)
│   └── export/                       # Media3 Transformer / MediaCodec hardware export engine
│
└── feature/
    ├── home/                         # Recent projects, create project dialog
    ├── editor/                       # Main editor workspace, toolbar, inspector
    ├── timeline/                     # Multi-track touch timeline, zoom/pan gestures, trim handles
    ├── preview/                      # Interactive video preview player & overlay controls
    ├── effects/                      # Filter selection, parameter sliders, LUT picker
    ├── transitions/                  # Transition browser, duration & easing selectors
    ├── text/                         # Title and subtitle editor, styling, typography
    ├── audio/                        # Audio track controls, volume sliders, fades
    └── export/                       # Export dialog, resolution/bitrate presets, progress
```

---

## 3. Core Domain & Timeline Data Model

All time values are stored as **integer 64-bit microseconds (`Long Us`)**, avoiding floating-point rounding errors and drift over long compositions.

```kotlin
data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val width: Int = 1920,
    val height: Int = 1080,
    val frameRate: Int = 30,
    val durationUs: Long = 0L,
    val tracks: List<Track> = emptyList(),
    val assets: List<MediaAsset> = emptyList(),
    val exportSettings: ExportSettings = ExportSettings(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val schemaVersion: Int = 1
)

enum class TrackType {
    VIDEO,
    AUDIO,
    TEXT,
    OVERLAY
}

data class Track(
    val id: String = UUID.randomUUID().toString(),
    val type: TrackType,
    val order: Int,
    val name: String = "",
    val isMuted: Boolean = false,
    val isHidden: Boolean = false,
    val isLocked: Boolean = false,
    val volume: Float = 1.0f,
    val clips: List<Clip> = emptyList()
)

data class Clip(
    val id: String = UUID.randomUUID().toString(),
    val assetId: String,
    val timelineStartUs: Long,
    val sourceStartUs: Long,
    val sourceEndUs: Long,
    val durationUs: Long,
    val playbackSpeed: Float = 1.0f,
    val transform: ClipTransform = ClipTransform(),
    val opacity: Float = 1.0f,
    val volume: Float = 1.0f,
    val effects: List<EffectInstance> = emptyList(),
    val keyframes: List<KeyframeTrack> = emptyList(),
    val transitionIn: TransitionInstance? = null,
    val transitionOut: TransitionInstance? = null
)

data class ClipTransform(
    val positionX: Float = 0.0f,   // Normalized (-1.0 to 1.0)
    val positionY: Float = 0.0f,
    val scaleX: Float = 1.0f,
    val scaleY: Float = 1.0f,
    val rotation: Float = 0.0f,    // Degrees (0 to 360)
    val anchorX: Float = 0.5f,     // Normalized pivot
    val anchorY: Float = 0.5f
)
```

---

## 4. Command-Based Undo / Redo Engine

To ensure predictable non-destructive editing and low memory usage, all timeline modifications are executed through explicit `EditCommand` instances:

```kotlin
interface EditCommand {
    val description: String
    fun execute(project: Project): Project
    fun undo(project: Project): Project
}
```

The `CommandManager` holds an undo stack and a redo stack (bounded to e.g. 50 actions) and emits project state changes to the repository and UI. Examples:
- `SplitClipCommand(clipId, splitTimelineUs)`
- `TrimClipCommand(clipId, newSourceStartUs, newSourceEndUs, newTimelineStartUs)`
- `MoveClipCommand(clipId, targetTrackId, newTimelineStartUs)`
- `AddEffectCommand(clipId, effectInstance)`
- `UpdateClipTransformCommand(clipId, newTransform)`

---

## 5. Preview & Rendering Architecture

### 5.1 Deterministic GPU Pipeline
```
               [ Video Frame (MediaCodec / SurfaceTexture) ]
                                    |
                                    v
                    [ Vertex Transform Matrix ]
                 (Position, Scale, Rotation, Pivot)
                                    |
                                    v
                         [ Base Frame Sampling ]
                                    |
                                    v
                  [ Effect Pass 1 (e.g. Color Grade) ]
                                    |  (Offscreen FBO)
                                    v
                   [ Effect Pass 2 (e.g. Vignette) ]
                                    |  (Offscreen FBO)
                                    v
                    [ Transition Blend with Layer B ]
                                    |
                                    v
                       [ Final Canvas Framebuffer ]
                    (On-Screen Viewport or Video Encoder)
```

### 5.2 Threading Model
- **UI Thread:** Jetpack Compose rendering, touch gesture handling, high-level user events.
- **Timeline Engine Thread:** Timeline arithmetic, state validation, command execution.
- **Render Thread (GLThread):** Dedicated HandlerThread with an active EGL context. Handles texture uploads, shader drawing, and frame swapping without stalling the UI.
- **Media Decoding Thread:** MediaCodec / ExoPlayer decoders pushing YUV/OES frames directly into SurfaceTextures.
- **I/O Thread:** Room database writes, thumbnail extraction, and file management.

---

## 6. Persistence & Crash Recovery Strategy

1. **Room Database:** Stores project metadata, tracks, clips, effects, and asset references in relational tables.
2. **Auto-Save Pipeline:**
   - Every executed command automatically updates an in-memory `StateFlow<Project>`.
   - A debounced (500ms) background worker persists the serialized project graph to Room.
   - A dedicated `AppSession` entity records the currently active project and timeline playhead.
3. **Session Recovery:**
   - On cold launch, `ProjectRecoveryManager` checks if an active project was abruptly interrupted.
   - If found, the user is offered instant one-tap recovery restoring exact track states, playhead position, and zoom level.

---

## 7. Media3 & Hardware Video Export Architecture

Exporting uses **AndroidX Media3 Transformer** combined with a custom **GlEffect / GlShaderProgram** pipeline:
- **Zero Transcode When Possible:** Video frames undergo hardware decoding -> EGL texture -> custom GLSL effect pipeline -> hardware encoding via `MediaCodec` -> `MediaMuxer`.
- **Background Execution:** Long renders run within a Foreground Service / WorkManager with a persistent notification and progress bar, preventing OS kills when the user switches apps.
- **Failure Recovery:** If encoding fails due to hardware limitations (e.g., unsupported resolution/profile), the exporter automatically falls back to standard compatible profiles (e.g., Baseline H.264 @ 1080p).
