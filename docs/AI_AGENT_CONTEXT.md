# FLASH APP — AI AGENT CONTEXT & VIBECODING SPEC
`VERSION: 1.0.0` | `STACK: Kotlin 2.0 / Compose / Media3 1.5 / Room / OpenGL ES 3.0` | `TARGET: API 34` | `MIN: API 26`

## 1. INVIOLABLE SYSTEM INVARIANTS (DO NOT BREAK)
1. **Clock Unit:** Microseconds (`Long Us`). NEVER use floating `Float`/`Double` seconds for timeline math.
   $$\text{durationUs} = \lfloor(\text{sourceEndUs} - \text{sourceStartUs}) / \text{speed}\rfloor$$
2. **State Mutability:** `Project`, `Track`, `Clip`, `Effect`, `TextOverlay` are IMMUTABLE data classes.
3. **Edit Mutations:** EVERY edit MUST pass through `CommandManager.execute(cmd, project)`. NEVER mutate state directly.
4. **Hilt Rule:** `@Inject constructor(...)` classes CANNOT have default parameter values (Dagger produces duplicate constructors).
5. **Media3 Overlay Rule:** `OverlayEffect` requires `List<TextureOverlay>` in Kotlin. Pass `listOf<TextureOverlay>(...)`, NOT Guava `ImmutableList`.
6. **WYSIWYG Rule:** Effects added to `VideoPlayerEngine` preview MUST also be mapped in `ExportEngine` via `EffectShaderConverter`.

---

## 2. SYMBOL MAP & ARCHITECTURE INDEX

| Path | Primary Class | Key API / Signature | Purpose |
| :--- | :--- | :--- | :--- |
| `core/model/TimeExt.kt` | Extensions | `Long.usToMs`, `Long.msToUs`, `Double.secToUs` | Microsecond conversions |
| `core/model/Project.kt` | `Project` | `id, name, width, height, tracks: List<Track>, assets` | Root project tree |
| `core/model/Track.kt` | `Track` | `id, type: TrackType, order: Int, muted, locked, clips` | Timeline lane |
| `core/model/Clip.kt` | `Clip` | `timelineStartUs, sourceStartUs, sourceEndUs, durationUs, speed, effects, textOverlay` | Unit of media on timeline |
| `core/model/Effect.kt` | `Effect` | `id, type: EffectType, parameters: Map<String, Float>, enabled` | GPU shader parameters |
| `core/model/TextOverlay.kt` | `TextOverlay`| `text, fontSizeSp, textColor, strokeColor, alignment` | Dynamic text metadata |
| `engine/command/CommandManager.kt`| `CommandManager`| `execute(cmd, proj): Project`, `undo(proj): Project`, `redo(proj): Project` | Undo/Redo stack manager |
| `engine/command/EditCommand.kt` | `EditCommand` | `execute(proj): Project`, `undo(proj): Project` | Atomic command interfaces |
| `engine/render/EffectShaderConverter.kt`| `EffectShaderConverter`| `createGlEffect(effect): GlEffect`, `createTextOverlayEffect(overlay): GlEffect` | OpenGL ES 3.0 / GLSL bridge |
| `engine/render/TextBitmapRenderer.kt` | `TextBitmapRenderer`| `renderToBitmap(overlay, w, h): Bitmap` | Canvas ARGB_8888 rasterizer |
| `engine/audio/AudioWaveformExtractor.kt`| `AudioWaveformExtractor`| `getWaveform(assetId, uri, samples=120): List<Float>` | RMS amplitude sampling |
| `engine/ai/SmartAutoCutEngine.kt` | `SmartAutoCutEngine` | `detectSilence(peaks, durUs, threshold=0.05f): List<SilenceInterval>` | Silence jump-cut generator |
| `engine/ai/AutoSubtitleEngine.kt` | `AutoSubtitleEngine` | `generateSubtitlesFromPeaks(peaks, durUs): List<SubtitleSegment>` | Timed subtitle synthesis |
| `engine/preview/VideoPlayerEngine.kt` | `VideoPlayerEngine` | `initialize(surfaceView)`, `loadProject(proj)`, `seekTo(timeUs)` | Media3 ExoPlayer controller |
| `engine/export/ExportEngine.kt` | `ExportEngine` | `startExport(proj, settings, onProgress, onComplete, onError)` | Media3 Transformer exporter |
| `feature/editor/EditorViewModel.kt` | `EditorViewModel` | `splitClipAtPlayhead()`, `trimSelectedClip()`, `addTextOverlay()`, `applyAiSmartAutoCut()` | Presentation coordinator |
| `feature/timeline/MultiTrackTimeline.kt` | Composable | `MultiTrackTimeline(project, playheadUs, onSeek, onSelectClip)` | Interactive touch canvas |

---

## 3. COMMAND PATTERN CATALOG

```
EditCommand (Interface)
├── SplitClipCommand(clipId, splitTimeUs)
├── TrimClipCommand(clipId, newSourceStartUs, newSourceEndUs)
├── MoveClipCommand(clipId, targetTrackId, newTimelineStartUs)
├── DuplicateClipCommand(clipId, newClipId)
├── DeleteClipCommand(clipId)
├── UpdateClipVolumeCommand(clipId, volume: 0.0..2.0)
├── ToggleClipMuteCommand(clipId)
├── UpdateClipSpeedCommand(clipId, speed: 0.25..3.0)
├── DetachAudioCommand(videoClipId)
├── AddTextClipCommand(textOverlay, timelineStartUs, durationUs)
└── CompoundCommand(commands: List<EditCommand>, description: String)
```
*Note:* CompoundCommand enables multi-step actions (e.g. AI Jump-Cut with 12 split/deletes) to undo in a single step.

---

## 4. SHADER EFFECT MATRIX (`EffectType`)

| Enum | Parameters (`key: range`) | GLSL Operation |
| :--- | :--- | :--- |
| `BRIGHTNESS` | `intensity: -1.0..1.0` | `rgb + intensity` |
| `CONTRAST` | `intensity: 0.0..2.0` | `(rgb - 0.5) * intensity + 0.5` |
| `SATURATION` | `intensity: 0.0..2.0` | `mix(vec3(dot(rgb, LUMA)), rgb, intensity)` |
| `VIGNETTE` | `intensity: 0.0..1.0` | `rgb * smoothstep(0.8, 0.2, distance(uv, vec2(0.5)))` |
| `HUE_ADJUST` | `intensity: -180.0..180.0` | RGB $\to$ HSV $\to$ rotate hue $\to$ RGB |
| `GRAYSCALE` | None | `vec3(dot(rgb, vec3(0.299, 0.587, 0.114)))` |
| `INVERT` | None | `1.0 - rgb` |
| `SEPIA` | None | Matrix multiply warm tint: `rgb * mat3(...)` |

---

## 5. EXTENSION RECIPES FOR AGENTS

### Recipe A: Add New Shader Filter
1. In `core/model/Effect.kt`: Add entry to `enum class EffectType`.
2. In `engine/render/EffectShaderConverter.kt`:
   - Add GLSL shader block in `createGlEffect()`.
   - Bind uniform float variables using `parameters[paramKey]`.
3. In `feature/editor/EditorScreen.kt`: Add button & slider to `EffectsBottomSheet`.

### Recipe B: Add New Edit Command
1. In `engine/command/EditCommand.kt`:
   - Implement `EditCommand`: Define `execute(Project): Project` and `undo(Project): Project`.
   - Store required state in constructor to restore prior state in `undo`.
2. In `feature/editor/EditorViewModel.kt`:
   - Execute via `val updated = commandManager.execute(cmd, currentProject)`.
   - Call `updateProjectAfterCommand(updated)`.
3. In `app/src/test/java/org/cutwire/drift/CommandManagerTest.kt`: Add unit test validating execute & undo symmetry.

### Recipe C: Connect On-Device ML/TFLite/ONNX
1. Put `.tflite` or `.onnx` model inside `app/src/main/assets/`.
2. Create `@Singleton class CustomMlEngine @Inject constructor(@ApplicationContext context: Context)`.
3. Feed normalized PCM audio from `AudioWaveformExtractor` or video bitmap from `TextBitmapRenderer`.
4. Wrap output modifications in `CompoundCommand` and dispatch through `EditorViewModel`.

---

## 6. KNOWN PITFALLS & GOTCHAS
- **Gradle Daemon Sockets on Windows:** Always run `./gradlew.bat <task> --no-daemon` in headless agent shells.
- **Dagger Double-Constructor Bug:** Never write `class Foo @Inject constructor(val bar: Bar = Bar())`.
- **MediaCodec Scoped Storage:** On Android 10+, write exports to internal cache first (`context.cacheDir/exports/`), then stream-copy to MediaStore `Environment.DIRECTORY_MOVIES/Flash`.
- **ExoPlayer Surface Attachment:** Do not detach `SurfaceView` while ExoPlayer is actively decoding; call `player.clearVideoSurface()` prior to disposal.
