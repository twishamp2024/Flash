package org.cutwire.drift.feature.editor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.cutwire.drift.core.media.MediaMetadataExtractor
import org.cutwire.drift.core.media.ThumbnailGenerator
import org.cutwire.drift.core.model.Clip
import org.cutwire.drift.core.model.Project
import org.cutwire.drift.core.model.Track
import org.cutwire.drift.core.model.TrackType
import org.cutwire.drift.core.storage.MediaStorageManager
import org.cutwire.drift.core.storage.ProjectRepository
import org.cutwire.drift.engine.command.CommandManager
import org.cutwire.drift.engine.command.DeleteClipCommand
import org.cutwire.drift.engine.command.DuplicateClipCommand
import org.cutwire.drift.engine.command.MoveClipCommand
import org.cutwire.drift.engine.command.SplitClipCommand
import org.cutwire.drift.engine.command.TrimClipCommand
import org.cutwire.drift.engine.export.ExportConfiguration
import org.cutwire.drift.engine.export.ExportEngine
import org.cutwire.drift.engine.export.ExportStatus
import org.cutwire.drift.engine.preview.VideoPlayerEngine
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import org.cutwire.drift.core.model.EffectInstance
import org.cutwire.drift.core.model.EffectType
import org.cutwire.drift.core.model.TextOverlay
import org.cutwire.drift.engine.ai.AutoSubtitleEngine
import org.cutwire.drift.engine.ai.SilenceInterval
import org.cutwire.drift.engine.ai.SmartAutoCutEngine
import org.cutwire.drift.engine.audio.AudioWaveformExtractor
import org.cutwire.drift.engine.command.AddEffectCommand
import org.cutwire.drift.engine.command.AddTextClipCommand
import org.cutwire.drift.engine.command.CompoundCommand
import org.cutwire.drift.engine.command.DetachAudioCommand
import org.cutwire.drift.engine.command.RemoveEffectCommand
import org.cutwire.drift.engine.command.ToggleClipMuteCommand
import org.cutwire.drift.engine.command.UpdateClipFadeCommand
import org.cutwire.drift.engine.command.UpdateClipSpeedCommand
import org.cutwire.drift.engine.command.UpdateClipVolumeCommand
import org.cutwire.drift.engine.command.UpdateEffectCommand
import org.cutwire.drift.engine.render.EffectShaderConverter
import javax.inject.Inject

@androidx.media3.common.util.UnstableApi
@OptIn(FlowPreview::class)
@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    private val mediaMetadataExtractor: MediaMetadataExtractor,
    private val thumbnailGenerator: ThumbnailGenerator,
    private val mediaStorageManager: MediaStorageManager,
    val playerEngine: VideoPlayerEngine,
    private val exportEngine: ExportEngine,
    private val commandManager: CommandManager,
    private val effectShaderConverter: EffectShaderConverter,
    private val smartAutoCutEngine: SmartAutoCutEngine,
    private val autoSubtitleEngine: AutoSubtitleEngine,
    private val audioWaveformExtractor: AudioWaveformExtractor
) : ViewModel() {

    private val projectId: String = checkNotNull(savedStateHandle["projectId"])

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val autosaveChannel = Channel<Project>(Channel.CONFLATED)

    private var exportJob: Job? = null
    private var filmstripJob: Job? = null
    private var detectedSilences: List<SilenceInterval> = emptyList()

    init {
        loadProject()
        observePlayerState()
        observeCommandState()
        observeAutosave()
    }

    private fun observeCommandState() {
        viewModelScope.launch {
            commandManager.canUndo.collect { canUndo ->
                _uiState.update { it.copy(canUndo = canUndo) }
            }
        }
        viewModelScope.launch {
            commandManager.canRedo.collect { canRedo ->
                _uiState.update { it.copy(canRedo = canRedo) }
            }
        }
    }

    private fun observeAutosave() {
        viewModelScope.launch {
            autosaveChannel.consumeAsFlow()
                .debounce(500L)
                .collect { projToSave ->
                    projectRepository.saveProject(projToSave)
                }
        }
    }

    private fun observePlayerState() {
        viewModelScope.launch {
            playerEngine.isPlaying.collect { playing ->
                _uiState.update { it.copy(isPlaying = playing) }
            }
        }
        viewModelScope.launch {
            playerEngine.playbackPositionUs.collect { posUs ->
                _uiState.update { it.copy(currentPositionUs = posUs) }
            }
        }
        viewModelScope.launch {
            playerEngine.durationUs.collect { durUs ->
                if (durUs > 0L) {
                    _uiState.update { it.copy(durationUs = durUs) }
                }
            }
        }
    }

    private fun loadProject() {
        viewModelScope.launch {
            val project = projectRepository.getProject(projectId)
            if (project != null) {
                _uiState.update {
                    it.copy(
                        project = project,
                        durationUs = project.durationUs,
                        isLoading = false
                    )
                }

                // If project has clips, load first clip
                val firstClip = project.tracks.flatMap { it.clips }.firstOrNull()
                if (firstClip != null) {
                    selectClip(firstClip)
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Project not found"
                    )
                }
            }
        }
    }

    fun importMedia(uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            var currentProj = _uiState.value.project ?: return@launch

            for (incomingUri in uris) {
                mediaStorageManager.persistUriPermission(incomingUri)
                val displayName = queryDisplayName(incomingUri) ?: "Media_${System.currentTimeMillis()}"

                // Copy to internal project storage for bulletproof reliability and offline persistence
                val localFile = try {
                    mediaStorageManager.copyMediaToProjectStorage(incomingUri, displayName)
                } catch (e: Exception) {
                    android.util.Log.e("DriftMedia", "Failed to copy media file to internal storage", e)
                    null
                }

                val finalUri = if (localFile != null && localFile.exists()) {
                    Uri.fromFile(localFile)
                } else {
                    incomingUri
                }

                val metadataResult = mediaMetadataExtractor.extractMetadata(finalUri)
                if (metadataResult.isFailure) {
                    android.util.Log.e("DriftMedia", "Metadata extraction failed: ${metadataResult.exceptionOrNull()}")
                    continue
                }
                val metadata = metadataResult.getOrThrow()
                if (metadata.durationUs <= 0) {
                    android.util.Log.w("DriftMedia", "Skipping media with 0 or invalid duration: $finalUri")
                    continue
                }

                val assetId = java.util.UUID.randomUUID().toString()
                val thumbPath = thumbnailGenerator.generateAssetThumbnail(finalUri, assetId)
                val mediaAsset = mediaMetadataExtractor.buildMediaAsset(finalUri, displayName, metadata, thumbPath)

                // Locate or create video track (Track 0)
                var videoTrack = currentProj.tracks.firstOrNull { it.type == TrackType.VIDEO }
                if (videoTrack == null) {
                    videoTrack = Track(type = TrackType.VIDEO, order = 0)
                    currentProj = currentProj.copy(tracks = currentProj.tracks + videoTrack)
                }

                val timelineStartUs = videoTrack.clips.maxOfOrNull { it.timelineEndUs } ?: 0L
                val clip = Clip(
                    assetId = mediaAsset.id,
                    timelineStartUs = timelineStartUs,
                    sourceStartUs = 0L,
                    sourceEndUs = metadata.durationUs,
                    durationUs = metadata.durationUs
                )

                val updatedClips = videoTrack.clips + clip
                val updatedTrack = videoTrack.copy(clips = updatedClips)
                val updatedTracks = currentProj.tracks.map {
                    if (it.id == updatedTrack.id) updatedTrack else it
                }

                currentProj = currentProj.copy(
                    tracks = updatedTracks,
                    assets = currentProj.assets + mediaAsset,
                    updatedAt = System.currentTimeMillis()
                )

                // Select this new clip and load it
                selectClip(clip, mediaAsset, currentProj)
            }

            projectRepository.saveProject(currentProj)
            _uiState.update {
                it.copy(
                    project = currentProj,
                    durationUs = currentProj.durationUs,
                    isLoading = false
                )
            }
        }
    }

    fun selectClip(clip: Clip?) {
        val proj = _uiState.value.project ?: return
        if (clip == null) {
            _uiState.update { it.copy(selectedClip = null, filmstripThumbnails = emptyList()) }
            return
        }
        val asset = proj.assets.firstOrNull { it.id == clip.assetId }
        selectClip(clip, asset, proj)
    }

    private fun selectClip(clip: Clip, asset: org.cutwire.drift.core.model.MediaAsset?, project: Project) {
        val uri = asset?.let { Uri.parse(it.uriString) }
        _uiState.update {
            it.copy(
                selectedClip = clip,
                activeAssetUri = uri,
                showTrimControls = true
            )
        }

        if (uri != null) {
            playerEngine.setSource(
                uri = uri,
                clipInUs = clip.sourceStartUs,
                clipOutUs = clip.sourceEndUs,
                autoPlay = false
            )

            // Generate filmstrip thumbnails for timeline
            loadFilmstrip(uri, clip.sourceStartUs, clip.sourceEndUs)
        }
    }

    private fun loadFilmstrip(uri: Uri, startUs: Long, endUs: Long) {
        filmstripJob?.cancel()
        filmstripJob = viewModelScope.launch {
            val thumbnails = thumbnailGenerator.generateFilmstrip(
                uri = uri,
                startUs = startUs,
                endUs = endUs,
                count = 8
            )
            _uiState.update { it.copy(filmstripThumbnails = thumbnails) }
        }
    }

    fun updateClipTrim(newInUs: Long, newOutUs: Long) {
        val currentProj = _uiState.value.project ?: return
        val currentClip = _uiState.value.selectedClip ?: return
        val asset = currentProj.assets.firstOrNull { it.id == currentClip.assetId } ?: return

        val minDurationUs = 100_000L.coerceAtMost(asset.durationUs) // 100ms minimum clip duration
        val maxInUs = (asset.durationUs - minDurationUs).coerceAtLeast(0L)
        val clampedInUs = newInUs.coerceIn(0L, maxInUs)
        val minOutUs = (clampedInUs + minDurationUs).coerceAtMost(asset.durationUs)
        val clampedOutUs = newOutUs.coerceIn(minOutUs, asset.durationUs.coerceAtLeast(minOutUs))
        val newDurationUs = (clampedOutUs - clampedInUs).coerceAtLeast(0L)

        val updatedClip = currentClip.copy(
            sourceStartUs = clampedInUs,
            sourceEndUs = clampedOutUs,
            durationUs = newDurationUs
        )

        // Update tracks and ripple following clips
        val updatedTracks = currentProj.tracks.map { track ->
            if (track.clips.any { it.id == currentClip.id }) {
                var currentTimelineStart = 0L
                val newClips = track.clips.map { c ->
                    if (c.id == currentClip.id) {
                        val cUpdated = updatedClip.copy(timelineStartUs = currentTimelineStart)
                        currentTimelineStart += cUpdated.durationUs
                        cUpdated
                    } else {
                        val cUpdated = c.copy(timelineStartUs = currentTimelineStart)
                        currentTimelineStart += cUpdated.durationUs
                        cUpdated
                    }
                }
                track.copy(clips = newClips)
            } else {
                track
            }
        }

        val totalDurationUs = updatedTracks.maxOfOrNull { track ->
            track.clips.maxOfOrNull { it.timelineEndUs } ?: 0L
        } ?: 0L

        val updatedProj = currentProj.copy(
            tracks = updatedTracks,
            updatedAt = System.currentTimeMillis()
        )

        _uiState.update {
            it.copy(
                project = updatedProj,
                selectedClip = updatedClip,
                durationUs = totalDurationUs
            )
        }

        playerEngine.updateTrimBounds(clampedInUs, clampedOutUs)
        viewModelScope.launch {
            projectRepository.saveProject(updatedProj)
        }
    }

    fun setInPointAtPlayhead() {
        val clip = _uiState.value.selectedClip ?: return
        val posUs = _uiState.value.currentPositionUs
        val minDurationUs = 100_000L
        val maxIn = (clip.sourceEndUs - minDurationUs).coerceAtLeast(0L)
        val newInUs = (clip.sourceStartUs + posUs).coerceIn(0L, maxIn)
        updateClipTrim(newInUs, clip.sourceEndUs)
    }

    fun setOutPointAtPlayhead() {
        val clip = _uiState.value.selectedClip ?: return
        val posUs = _uiState.value.currentPositionUs
        val minDurationUs = 100_000L
        val minOut = clip.sourceStartUs + minDurationUs
        val newOutUs = (clip.sourceStartUs + posUs).coerceAtLeast(minOut)
        updateClipTrim(clip.sourceStartUs, newOutUs)
    }

    fun resetTrim() {
        val clip = _uiState.value.selectedClip ?: return
        val proj = _uiState.value.project ?: return
        val asset = proj.assets.firstOrNull { it.id == clip.assetId } ?: return
        updateClipTrim(0L, asset.durationUs)
    }

    // --- Phase 3: Command-Pattern Editing & Undo/Redo ---

    fun undo() {
        val proj = _uiState.value.project ?: return
        val restored = commandManager.undo(proj)
        updateProjectAfterCommand(restored)
    }

    fun redo() {
        val proj = _uiState.value.project ?: return
        val reApplied = commandManager.redo(proj)
        updateProjectAfterCommand(reApplied)
    }

    fun splitClipAtPlayhead() {
        val proj = _uiState.value.project ?: return
        val currentPosUs = _uiState.value.currentPositionUs

        // Target currently selected clip or the clip under the playhead
        val targetClip = _uiState.value.selectedClip
            ?.takeIf { currentPosUs > it.timelineStartUs && currentPosUs < it.timelineEndUs }
            ?: proj.tracks.flatMap { it.clips }.firstOrNull {
                currentPosUs > it.timelineStartUs && currentPosUs < it.timelineEndUs
            }
            ?: return

        val command = SplitClipCommand(targetClip.id, currentPosUs)
        val updatedProj = commandManager.execute(command, proj)
        updateProjectAfterCommand(updatedProj)
    }

    fun trimClip(
        clipId: String,
        newSourceStartUs: Long,
        newSourceEndUs: Long,
        newTimelineStartUs: Long,
        ripple: Boolean = false
    ) {
        val proj = _uiState.value.project ?: return
        val command = TrimClipCommand(clipId, newSourceStartUs, newSourceEndUs, newTimelineStartUs, ripple)
        val updatedProj = commandManager.execute(command, proj)
        updateProjectAfterCommand(updatedProj)
    }

    fun moveClip(clipId: String, targetTrackId: String, newTimelineStartUs: Long) {
        val proj = _uiState.value.project ?: return
        val command = MoveClipCommand(clipId, targetTrackId, newTimelineStartUs)
        val updatedProj = commandManager.execute(command, proj)
        updateProjectAfterCommand(updatedProj)
    }

    fun deleteSelectedClip() {
        val clip = _uiState.value.selectedClip ?: return
        val proj = _uiState.value.project ?: return
        val command = DeleteClipCommand(clip.id)
        val updatedProj = commandManager.execute(command, proj)
        _uiState.update { it.copy(selectedClip = null) }
        updateProjectAfterCommand(updatedProj)
    }

    fun duplicateSelectedClip() {
        val clip = _uiState.value.selectedClip ?: return
        val proj = _uiState.value.project ?: return
        val command = DuplicateClipCommand(clip.id)
        val updatedProj = commandManager.execute(command, proj)
        updateProjectAfterCommand(updatedProj)
    }

    fun toggleTrackMute(trackId: String) {
        val proj = _uiState.value.project ?: return
        val updatedTracks = proj.tracks.map {
            if (it.id == trackId) it.copy(isMuted = !it.isMuted) else it
        }
        val updatedProj = proj.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
        updateProjectAfterCommand(updatedProj)
    }

    fun toggleTrackLock(trackId: String) {
        val proj = _uiState.value.project ?: return
        val updatedTracks = proj.tracks.map {
            if (it.id == trackId) it.copy(isLocked = !it.isLocked) else it
        }
        val updatedProj = proj.copy(tracks = updatedTracks, updatedAt = System.currentTimeMillis())
        updateProjectAfterCommand(updatedProj)
    }

    private fun updateProjectAfterCommand(newProject: Project) {
        val currentSelectedId = _uiState.value.selectedClip?.id
        val updatedSelectedClip = currentSelectedId?.let { id ->
            newProject.tracks.flatMap { it.clips }.firstOrNull { it.id == id }
        }

        _uiState.update {
            it.copy(
                project = newProject,
                selectedClip = updatedSelectedClip,
                durationUs = newProject.durationUs
            )
        }

        // Trigger debounced autosave
        autosaveChannel.trySend(newProject)

        // If active clip was updated, reload in player engine & apply GPU shaders
        if (updatedSelectedClip != null) {
            val asset = newProject.assets.firstOrNull { it.id == updatedSelectedClip.assetId }
            val uri = asset?.let { Uri.parse(it.uriString) }
            if (uri != null) {
                playerEngine.setSource(uri, updatedSelectedClip.sourceStartUs, updatedSelectedClip.sourceEndUs)
            }

            val glEffects = effectShaderConverter.convertEffects(
                transform = updatedSelectedClip.transform,
                opacity = updatedSelectedClip.opacity,
                effects = updatedSelectedClip.effects
            )
            playerEngine.setEffects(glEffects)
        }
    }

    // --- Phase 4: GPU Shader & Effect Pipeline ---

    fun toggleEffectsSheet() {
        _uiState.update { it.copy(showEffectsSheet = !it.showEffectsSheet) }
    }

    fun addEffect(effectType: EffectType) {
        val clip = _uiState.value.selectedClip ?: return
        val proj = _uiState.value.project ?: return

        // Prevent duplicate effects of the same type on the same clip
        if (clip.effects.any { it.type == effectType }) return

        val initialParameters = when (effectType) {
            EffectType.BRIGHTNESS -> mapOf("brightness" to 0.2f)
            EffectType.CONTRAST -> mapOf("contrast" to 0.3f)
            EffectType.SATURATION -> mapOf("saturation" to 1.4f)
            EffectType.EXPOSURE -> mapOf("exposure" to 0.3f)
            EffectType.TEMPERATURE -> mapOf("temperature" to 0.3f)
            EffectType.TINT -> mapOf("tint" to 0.2f)
            EffectType.SEPIA -> mapOf()
            EffectType.GRAYSCALE -> mapOf()
            else -> mapOf()
        }

        val newEffect = EffectInstance(type = effectType, parameters = initialParameters)
        val command = AddEffectCommand(clip.id, newEffect)
        val updatedProj = commandManager.execute(command, proj)
        updateProjectAfterCommand(updatedProj)
    }

    fun updateEffectParameter(effectId: String, paramName: String, value: Float) {
        val clip = _uiState.value.selectedClip ?: return
        val proj = _uiState.value.project ?: return
        val effect = clip.effects.firstOrNull { it.id == effectId } ?: return
        val newParams = effect.parameters + (paramName to value)
        val command = UpdateEffectCommand(clip.id, effectId, newParams)
        val updatedProj = commandManager.execute(command, proj)
        updateProjectAfterCommand(updatedProj)
    }

    fun removeEffect(effectId: String) {
        val clip = _uiState.value.selectedClip ?: return
        val proj = _uiState.value.project ?: return
        val command = RemoveEffectCommand(clip.id, effectId)
        val updatedProj = commandManager.execute(command, proj)
        updateProjectAfterCommand(updatedProj)
    }

    // --- Phase 5: Audio Mixing & Processing ---

    fun toggleAudioSheet() {
        _uiState.update { it.copy(showAudioSheet = !it.showAudioSheet) }
    }

    fun updateClipVolume(volume: Float) {
        val clip = _uiState.value.selectedClip ?: return
        val proj = _uiState.value.project ?: return
        val command = UpdateClipVolumeCommand(clip.id, volume)
        val updatedProj = commandManager.execute(command, proj)
        playerEngine.setVolume(if (clip.isMuted) 0f else volume)
        updateProjectAfterCommand(updatedProj)
    }

    fun toggleClipMute() {
        val clip = _uiState.value.selectedClip ?: return
        val proj = _uiState.value.project ?: return
        val command = ToggleClipMuteCommand(clip.id)
        val updatedProj = commandManager.execute(command, proj)
        val newMute = !clip.isMuted
        playerEngine.setMuted(newMute)
        updateProjectAfterCommand(updatedProj)
    }

    fun updateClipSpeed(speed: Float) {
        val clip = _uiState.value.selectedClip ?: return
        val proj = _uiState.value.project ?: return
        val command = UpdateClipSpeedCommand(clip.id, speed)
        val updatedProj = commandManager.execute(command, proj)
        playerEngine.setSpeed(speed)
        updateProjectAfterCommand(updatedProj)
    }

    fun updateClipFade(fadeInUs: Long, fadeOutUs: Long) {
        val clip = _uiState.value.selectedClip ?: return
        val proj = _uiState.value.project ?: return
        val command = UpdateClipFadeCommand(clip.id, fadeInUs, fadeOutUs)
        val updatedProj = commandManager.execute(command, proj)
        updateProjectAfterCommand(updatedProj)
    }

    fun detachAudioFromSelectedClip() {
        val clip = _uiState.value.selectedClip ?: return
        val proj = _uiState.value.project ?: return
        val command = DetachAudioCommand(clip.id)
        val updatedProj = commandManager.execute(command, proj)
        updateProjectAfterCommand(updatedProj)
    }

    fun toggleTextSheet() {
        _uiState.update { it.copy(showTextSheet = !it.showTextSheet) }
    }

    fun addTextOverlay(text: String, color: Long = 0xFFFFFFFF, fontSize: Float = 28f) {
        val proj = _uiState.value.project ?: return
        val currentPos = _uiState.value.currentPositionUs
        val overlay = TextOverlay(text = text, textColor = color, fontSizeSp = fontSize)
        val command = AddTextClipCommand(textOverlay = overlay, timelineStartUs = currentPos, durationUs = 3_000_000L)
        val updatedProj = commandManager.execute(command, proj)
        updateProjectAfterCommand(updatedProj)
    }

    fun openAiAutoCutDialog() {
        val clip = _uiState.value.selectedClip ?: _uiState.value.project?.tracks?.flatMap { it.clips }?.firstOrNull()
        val proj = _uiState.value.project
        if (clip == null || proj == null) return
        val asset = proj.assets.firstOrNull { it.id == clip.assetId }
        val uri = asset?.let { Uri.parse(it.uriString) } ?: return

        viewModelScope.launch {
            val peaks = audioWaveformExtractor.getWaveform(clip.assetId, uri, 120)
            val silences = smartAutoCutEngine.detectSilences(peaks, clip.durationUs)
            detectedSilences = silences
            _uiState.update { it.copy(showAiAutoCutDialog = true, detectedSilencesCount = silences.size) }
        }
    }

    fun dismissAiAutoCutDialog() {
        _uiState.update { it.copy(showAiAutoCutDialog = false) }
    }

    fun applyAiSmartAutoCut() {
        val clip = _uiState.value.selectedClip ?: _uiState.value.project?.tracks?.flatMap { it.clips }?.firstOrNull()
        val proj = _uiState.value.project
        if (clip == null || proj == null || detectedSilences.isEmpty()) {
            dismissAiAutoCutDialog()
            return
        }
        val command = smartAutoCutEngine.createAutoCutCommand(clip, detectedSilences)
        if (command != null) {
            val updatedProj = commandManager.execute(command, proj)
            updateProjectAfterCommand(updatedProj)
        }
        dismissAiAutoCutDialog()
    }

    fun applyAutoSubtitles() {
        val clip = _uiState.value.selectedClip ?: _uiState.value.project?.tracks?.flatMap { it.clips }?.firstOrNull()
        val proj = _uiState.value.project
        if (clip == null || proj == null) return
        val asset = proj.assets.firstOrNull { it.id == clip.assetId }
        val uri = asset?.let { Uri.parse(it.uriString) } ?: return

        viewModelScope.launch {
            val peaks = audioWaveformExtractor.getWaveform(clip.assetId, uri, 120)
            val subtitles = autoSubtitleEngine.generateSubtitlesFromPeaks(peaks, clip.durationUs, clip.timelineStartUs)
            if (subtitles.isEmpty()) return@launch

            val textCommands = subtitles.map { sub ->
                val overlay = TextOverlay(text = sub.suggestedText)
                AddTextClipCommand(textOverlay = overlay, timelineStartUs = sub.startUs, durationUs = sub.endUs - sub.startUs)
            }
            val compound = CompoundCommand(textCommands, "AI Auto Subtitles")
            val updatedProj = commandManager.execute(compound, proj)
            updateProjectAfterCommand(updatedProj)
        }
    }

    fun togglePlayPause() {
        playerEngine.togglePlayPause()
    }

    fun seekTo(positionUs: Long) {
        playerEngine.seekToUs(positionUs)
    }

    fun stepUs(offsetUs: Long) {
        playerEngine.stepUs(offsetUs)
    }

    fun openExportSheet() {
        _uiState.update { it.copy(showExportSheet = true, errorMessage = null) }
    }

    fun closeExportSheet() {
        _uiState.update { it.copy(showExportSheet = false) }
    }

    fun toggleTrimControls() {
        _uiState.update { it.copy(showTrimControls = !it.showTrimControls) }
    }

    fun startExport(
        resolution: ExportResolution = ExportResolution.FHD_1080P,
        frameRate: Int = 30
    ) {
        val clip = _uiState.value.selectedClip ?: _uiState.value.project?.tracks?.flatMap { it.clips }?.firstOrNull()
        val proj = _uiState.value.project

        if (clip == null || proj == null) {
            _uiState.update { it.copy(errorMessage = "No video clip available to export") }
            return
        }

        val asset = proj.assets.firstOrNull { it.id == clip.assetId }
        if (asset == null) {
            _uiState.update { it.copy(errorMessage = "Associated media asset missing") }
            return
        }

        val sourceUri = Uri.parse(asset.uriString)
        val config = ExportConfiguration(
            width = resolution.width,
            height = resolution.height,
            frameRate = frameRate
        )

        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isExporting = true,
                    exportProgress = 0,
                    exportStatusMessage = "Preparing export...",
                    errorMessage = null
                )
            }

            val additionalEffects = effectShaderConverter.convertEffects(
                transform = clip.transform,
                opacity = clip.opacity,
                effects = clip.effects
            ).toMutableList()

            // Include any active text overlays into GPU export pipeline
            val textClips = proj.tracks
                .filter { it.type == TrackType.TEXT && !it.isHidden }
                .flatMap { it.clips }
            for (tc in textClips) {
                val overlay = tc.textOverlay
                if (overlay != null) {
                    val overlayEffect = effectShaderConverter.createTextOverlayEffect(
                        overlay,
                        resolution.width,
                        resolution.height
                    )
                    if (overlayEffect != null) {
                        additionalEffects.add(overlayEffect)
                    }
                }
            }

            exportEngine.exportClip(
                sourceUri = sourceUri,
                inPointUs = clip.sourceStartUs,
                outPointUs = clip.sourceEndUs,
                config = config,
                additionalEffects = additionalEffects
            ).collect { status ->
                when (status) {
                    is ExportStatus.Progress -> {
                        _uiState.update {
                            it.copy(
                                isExporting = true,
                                exportProgress = status.percentage,
                                exportStatusMessage = "Exporting: ${status.percentage}%"
                            )
                        }
                    }
                    is ExportStatus.Success -> {
                        _uiState.update {
                            it.copy(
                                isExporting = false,
                                exportProgress = 100,
                                exportStatusMessage = "Export complete! Saved to Movies/Flash",
                                exportedFileUri = status.mediaStoreUri,
                                exportedFilePath = status.outputFile.absolutePath
                            )
                        }
                    }
                    is ExportStatus.Error -> {
                        _uiState.update {
                            it.copy(
                                isExporting = false,
                                exportStatusMessage = null,
                                errorMessage = "Export error: ${status.message}"
                            )
                        }
                    }
                    ExportStatus.Cancelled -> {
                        _uiState.update {
                            it.copy(
                                isExporting = false,
                                exportStatusMessage = "Export cancelled"
                            )
                        }
                    }
                    ExportStatus.Idle -> {}
                }
            }
        }
    }

    fun cancelExport() {
        exportEngine.cancelExport()
        exportJob?.cancel()
        _uiState.update {
            it.copy(
                isExporting = false,
                exportStatusMessage = "Export cancelled"
            )
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        name = cursor.getString(nameIdx)
                    }
                }
            }
        }
        return name ?: uri.lastPathSegment
    }

    override fun onCleared() {
        super.onCleared()
        playerEngine.release()
        exportEngine.cancelExport()
        thumbnailGenerator.clearCache()
    }
}
