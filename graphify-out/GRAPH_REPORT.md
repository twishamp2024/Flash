# Graph Report - main  (2026-11-13)

## Corpus Check
- Corpus is ~22,582 words - fits in a single context window. You may not need a graph.

## Summary
- 697 nodes · 1404 edges · 31 communities (18 shown, 13 thin omitted)
- Extraction: 99% EXTRACTED · 1% INFERRED · 0% AMBIGUOUS · INFERRED: 9 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- Command & Undo Engine
- Command & Undo Engine
- GPU Shaders & Video Rendering
- Command & Undo Engine
- Command & Undo Engine
- On-Device AI Engine
- On-Device AI Engine
- GPU Shaders & Video Rendering
- Media3 Video Export
- Command & Undo Engine
- GPU Shaders & Video Rendering
- Command & Undo Engine
- Multi-Track Timeline UI
- Audio Mixing & Waveforms
- Multi-Track Timeline UI
- Command & Undo Engine
- On-Device AI Engine
- Multi-Track Timeline UI
- Command & Undo Engine
- Command & Undo Engine
- Command & Undo Engine
- On-Device AI Engine
- Command & Undo Engine
- Command & Undo Engine
- On-Device AI Engine
- On-Device AI Engine
- Command & Undo Engine

## God Nodes (most connected - your core abstractions)
1. `Project` - 79 edges
2. `EditorViewModel` - 75 edges
3. `VideoPlayerEngine` - 31 edges
4. `Clip` - 30 edges
5. `EditCommand` - 23 edges
6. `AspectRatio` - 22 edges
7. `Track` - 20 edges
8. `EffectType` - 19 edges
9. `HomeViewModel` - 19 edges
10. `TextOverlay` - 16 edges

## Surprising Connections (you probably didn't know these)
- `DriftDatabase` --references--> `Converters`  [EXTRACTED]
  java/org/cutwire/drift/core/database/DriftDatabase.kt → java/org/cutwire/drift/core/database/converter/Converters.kt
- `ProjectRepositoryImpl` --references--> `ProjectDao`  [EXTRACTED]
  java/org/cutwire/drift/core/storage/ProjectRepository.kt → java/org/cutwire/drift/core/database/dao/ProjectDao.kt
- `ProjectEntity` --references--> `AspectRatio`  [EXTRACTED]
  java/org/cutwire/drift/core/database/entity/ProjectEntity.kt → java/org/cutwire/drift/core/model/AspectRatio.kt
- `EditorViewModel` --references--> `MediaMetadataExtractor`  [EXTRACTED]
  java/org/cutwire/drift/feature/editor/EditorViewModel.kt → java/org/cutwire/drift/core/media/MediaMetadataExtractor.kt
- `EditorViewModel` --references--> `ThumbnailGenerator`  [EXTRACTED]
  java/org/cutwire/drift/feature/editor/EditorViewModel.kt → java/org/cutwire/drift/core/media/ThumbnailGenerator.kt

## Import Cycles
- None detected.

## Communities (31 total, 13 thin omitted)

### Community 0 - "Command & Undo Engine"
Cohesion: 0.06
Nodes (13): Cancelled, Error, ExportConfiguration, ExportEngine, Listener, ExportStatus, Idle, Progress (+5 more)

### Community 1 - "Command & Undo Engine"
Cohesion: 0.08
Nodes (14): AiAutoCutDialog(), AudioBottomSheet(), ClipTrimControls(), EditorBottomToolbar(), EditorPlaybackTransport(), EditorScreen(), EditorTopBar(), EffectsBottomSheet() (+6 more)

### Community 4 - "Command & Undo Engine"
Cohesion: 0.08
Nodes (11): Converters, AspectRatio, RATIO_16_9, RATIO_1_1, RATIO_21_9, RATIO_4_5, RATIO_9_16, HomeUiState (+3 more)

### Community 5 - "Command & Undo Engine"
Cohesion: 0.08
Nodes (11): ExportSettings, Project, AddTrackCommand, DeleteTrackCommand, DuplicateClipCommand, EditCommand, MoveClipCommand, ToggleClipMuteCommand (+3 more)

### Community 6 - "On-Device AI Engine"
Cohesion: 0.11
Nodes (4): ProjectDao, DriftDatabase, ProjectEntity, DatabaseModule

### Community 7 - "On-Device AI Engine"
Cohesion: 0.09
Nodes (4): ProjectRepository, ProjectRepositoryImpl, ProjectSerializer, RepositoryModule

### Community 8 - "GPU Shaders & Video Rendering"
Cohesion: 0.07
Nodes (17): TextAlignment, CENTER, LEFT, RIGHT, TextAnimation, FADE, NONE, POP (+9 more)

### Community 10 - "Command & Undo Engine"
Cohesion: 0.09
Nodes (6): DriftTheme(), MainActivity, DriftNavHost(), Editor, Home, Screen

### Community 11 - "GPU Shaders & Video Rendering"
Cohesion: 0.08
Nodes (16): EffectParameter, EffectType, BLUR, BRIGHTNESS, CHROMA_KEY, CONTRAST, EXPOSURE, FILM_GRAIN (+8 more)

### Community 13 - "Multi-Track Timeline UI"
Cohesion: 0.09
Nodes (21): KeyframePoint, KeyframeTrack, EasingCurve, BOUNCE, EASE_IN, EASE_IN_OUT, EASE_OUT, ELASTIC (+13 more)

### Community 15 - "Audio Mixing & Waveforms"
Cohesion: 0.20
Nodes (7): ExtractedMediaMetadata, MediaMetadataExtractor, MediaAsset, MediaType, AUDIO, IMAGE, VIDEO

### Community 16 - "Multi-Track Timeline UI"
Cohesion: 0.17
Nodes (3): AudioWaveformExtractor, SnapHelper, SnapResult

### Community 19 - "Multi-Track Timeline UI"
Cohesion: 0.31
Nodes (6): MultiTrackTimeline(), TimelineClipItem(), TimelineRuler(), TrackHeaderItem(), TrackLane(), TimelineScaleHelper

### Community 20 - "Command & Undo Engine"
Cohesion: 0.19
Nodes (4): SilenceInterval, SmartAutoCutEngine, DeleteClipCommand, SplitClipCommand

### Community 21 - "Command & Undo Engine"
Cohesion: 0.27
Nodes (6): Clip, ClipTransform, Track, TransitionInstance, DetachAudioCommand, EditorUiState

### Community 23 - "On-Device AI Engine"
Cohesion: 0.33
Nodes (8): AboutDialog(), AspectRatioCard(), EmptyProjectsPlaceholder(), HomeScreen(), HomeTopBar(), NewProjectBottomSheet(), ProjectCard(), ProjectsGrid()

### Community 24 - "Command & Undo Engine"
Cohesion: 0.22
Nodes (3): EffectInstance, AddEffectCommand, RemoveEffectCommand

### Community 25 - "Command & Undo Engine"
Cohesion: 0.33
Nodes (3): TextOverlay, AddTextClipCommand, CompoundCommand

## Knowledge Gaps
- **53 isolated node(s):** `RATIO_16_9`, `RATIO_9_16`, `RATIO_1_1`, `RATIO_4_5`, `RATIO_21_9` (+48 more)
  These have ≤1 connection - possible missing edges. (Counts symbols only; 223 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **13 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Project` connect `Command & Undo Engine` to `Multi-Track Timeline UI`, `Command & Undo Engine`, `On-Device AI Engine`, `GPU Shaders & Video Rendering`, `Command & Undo Engine`, `On-Device AI Engine`, `Audio Mixing & Waveforms`, `Multi-Track Timeline UI`, `Command & Undo Engine`, `Multi-Track Timeline UI`, `Command & Undo Engine`, `Command & Undo Engine`, `Command & Undo Engine`, `On-Device AI Engine`, `Command & Undo Engine`, `Command & Undo Engine`, `Command & Undo Engine`, `Command & Undo Engine`?**
  _High betweenness centrality (0.243) - this node is a cross-community bridge._
- **What connects `RATIO_16_9`, `RATIO_9_16`, `RATIO_1_1` to the rest of the system?**
  _53 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Command & Undo Engine` be split into smaller, more focused modules?**
  _Cohesion score 0.05877551020408163 - nodes in this community are weakly interconnected._
- **Why does `EditorViewModel` connect `Command & Undo Engine` to `Command & Undo Engine`, `Command & Undo Engine`, `GPU Shaders & Video Rendering`, `Command & Undo Engine`, `On-Device AI Engine`, `GPU Shaders & Video Rendering`, `Media3 Video Export`, `Command & Undo Engine`, `GPU Shaders & Video Rendering`, `Audio Mixing & Waveforms`, `Multi-Track Timeline UI`, `Command & Undo Engine`, `Command & Undo Engine`, `Command & Undo Engine`, `Command & Undo Engine`, `Command & Undo Engine`, `Command & Undo Engine`, `Command & Undo Engine`, `Command & Undo Engine`?**
  _High betweenness centrality (0.186) - this node is a cross-community bridge._
- **Should `Command & Undo Engine` be split into smaller, more focused modules?**
  _Cohesion score 0.0824524312896406 - nodes in this community are weakly interconnected._
- **Why does `VideoPlayerEngine` connect `GPU Shaders & Video Rendering` to `Command & Undo Engine`, `Command & Undo Engine`?**
  _High betweenness centrality (0.087) - this node is a cross-community bridge._
- **Should `GPU Shaders & Video Rendering` be split into smaller, more focused modules?**
  _Cohesion score 0.08013937282229965 - nodes in this community are weakly interconnected._