# Technical Decisions Record (ADR)
## DriftMobile: Architectural Decisions & Trade-Offs

---

## ADR 001: Native Kotlin & Jetpack Compose vs Qt/QML Desktop Port

### Context
The reference repository (Drift) is written in C++ using Qt 6 and QML. While Qt supports Android deployment via its Android package wrapper, deploying Qt/QML on mobile introduces substantial friction:
- Massive binary size (>80 MB uncompressed Qt runtime libraries).
- Non-native touch gestures and lack of standard Android gesture physics (overscroll, fling velocity, pinch-to-zoom).
- Poor integration with Android system lifecycle, configuration changes, edge-to-edge system bars, and Material 3 design tokens.
- Complicated integration with modern Android media APIs like Media3 and Jetpack components.

### Decision
DriftMobile is implemented as a 100% native Android application using **Kotlin**, **Jetpack Compose**, and **Material 3**. 

### Consequences
- **Positive:** True Android look-and-feel, seamless gesture physics, automatic support for system dark mode, dynamic colors, edge-to-edge layouts, and direct interop with AndroidX libraries.
- **Negative:** Requires an independent rewrite of UI and engine orchestrations rather than copying Drift C++ QML code directly. (This also satisfies the strict clean-room IP requirement).

---

## ADR 002: AndroidX Media3 & MediaCodec vs Custom FFmpeg Compilation

### Context
Video editing requires decoding multiple video formats, scrubbing frame-by-frame, and encoding final compositions. FFmpeg offers vast format support, but compiling full FFmpeg for multiple Android ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`) adds 40+ MB of native libraries, requires complex JNI bridging, and often defaults to software decoding/encoding unless configured for MediaCodec NDK (`AMediaCodec`), which is notoriously device-finicky.

### Decision
Adopt **AndroidX Media3** (ExoPlayer, Transformer, Effect APIs) backed by Android's hardware `MediaCodec`, `MediaExtractor`, and `MediaMuxer`.

### Consequences
- **Positive:** Zero native library bloat for decoding/encoding, optimal battery efficiency, direct GPU rendering via `Surface` / `SurfaceTexture`, and long-term Google platform maintenance.
- **Negative:** Hardware decoders on some budget Android devices are limited to 2-4 concurrent instances. Our engine must manage decoder lifecycle and virtual track pooling to prevent resource exhaustion.

---

## ADR 003: Microsecond Integer Time Representation (`Long Us`)

### Context
Using floating-point numbers (`Float` or `Double` seconds) for timeline time representation causes compounding floating-point rounding errors over long edits. Two clips placed side by side may develop invisible 1-millisecond gaps or overlaps, causing stuttering or audio clicks during playback and export.

### Decision
Store all timeline timestamps, durations, and in/out points as **64-bit integer microseconds (`Long Us`)**. 1 second = `1_000_000L Us`.

### Consequences
- **Positive:** Exact frame boundary arithmetic, integer equality checks, zero drift, and direct 1:1 compatibility with Android `MediaCodec` presentation timestamps (PTS in microseconds).
- **Negative:** Requires simple converter utility functions when formatting times for UI display (e.g., `00:01:23.14`).

---

## ADR 004: OpenGL ES 3.0 vs Vulkan for Video Compositing & Effects

### Context
Vulkan offers low overhead and explicit multi-threading, but Vulkan driver stability across older or mid-range Android devices (especially API 26-28) varies widely. OpenGL ES 3.0 is universally supported across virtually 100% of active Android devices and integrates smoothly with `SurfaceTexture`, `EGL14`, and AndroidX Media3 `GlEffect`.

### Decision
Standardize on **OpenGL ES 3.0** with GLSL 300 es shaders, with fallback compatibility for OpenGL ES 2.0 / GLSL 100 where needed.

### Consequences
- **Positive:** Universal hardware support across almost every Android device in the market, mature shader documentation, and effortless binding to Media3 Transformer's GL pipeline.
- **Negative:** Single-threaded OpenGL context constraint, managed cleanly by hosting OpenGL operations on a dedicated `GLThread`.

---

## ADR 005: Command Pattern for Undo / Redo vs Full Project Snapshots

### Context
A video editing project may contain dozens of clips, keyframes, transitions, and effect parameters. Storing a complete clone of the `Project` object for every micro-adjustment (such as dragging a slider) wastes memory and triggers frequent GC pauses on mobile devices.

### Decision
Implement the **Command Pattern (`EditCommand`)** with an undo and redo stack. Each command encapsulates the minimum delta required to execute and invert an operation.

### Consequences
- **Positive:** Extremely small memory footprint, instantaneous undo/redo execution, and effortless transaction logging for autosave and telemetry.
- **Negative:** Every new editing capability must define its corresponding `EditCommand` pair (`execute()` and `undo()`).

---

## ADR 006: Room Database for Project Persistence vs Raw JSON Files

### Context
Persisting projects purely as monolithic JSON files on internal storage requires reading and rewriting the entire file on every autosave, which is prone to write truncation if the app is abruptly killed during I/O.

### Decision
Use **Room (SQLite)** as the primary persistence layer for projects, tracks, and clips, with an automated serialization bridge for project export/import.

### Consequences
- **Positive:** Atomic transactions, schema migrations, fast querying of project catalogs and recent lists, and zero risk of file corruption during unexpected termination.
- **Negative:** Requires mapping between relational Room entities and domain data classes (handled cleanly via Repository and TypeConverters).

---

## ADR 007: Shared Preview and Export Shader Execution Guarantee

### Context
A classic flaw in video editors is preview divergence: an effect looks one way during playback in the preview window, but looks slightly different or behaves differently in the exported MP4.

### Decision
Preview and Export share the **exact same GLSL shader files and parameter mapping logic**.
- The preview engine uses an offscreen FBO pipeline and renders to an on-screen `SurfaceView`.
- The export engine uses the exact same shader classes wrapped in Media3 `GlEffect` / `GlShaderProgram` reading from decoders and writing to the hardware encoder input surface.
- Transition shaders are pure mathematical functions of `(uFromTexture, uToTexture, uProgress, uParameters)` with no hidden frame-to-frame mutable state.

### Consequences
- **Positive:** Complete visual fidelity: "What You See Is What You Get" (WYSIWYG).
- **Negative:** Shaders cannot rely on accumulated historical frame state across renders unless explicitly modeled as multi-pass ping-pong buffers.
