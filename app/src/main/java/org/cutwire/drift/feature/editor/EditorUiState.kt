package org.cutwire.drift.feature.editor

import android.graphics.Bitmap
import android.net.Uri
import org.cutwire.drift.core.model.Clip
import org.cutwire.drift.core.model.Project
import org.cutwire.drift.core.model.Track

enum class ExportResolution(val label: String, val width: Int, val height: Int) {
    HD_720P("720p HD", 1280, 720),
    FHD_1080P("1080p Full HD", 1920, 1080),
    UHD_4K("4K Ultra HD", 3840, 2160)
}

data class EditorUiState(
    val project: Project? = null,
    val isLoading: Boolean = true,
    val isPlaying: Boolean = false,
    val currentPositionUs: Long = 0L,
    val durationUs: Long = 0L,
    val selectedClip: Clip? = null,
    val selectedTrack: Track? = null,
    val activeAssetUri: Uri? = null,
    val filmstripThumbnails: List<Bitmap> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isExporting: Boolean = false,
    val exportProgress: Int = 0,
    val exportStatusMessage: String? = null,
    val exportedFileUri: Uri? = null,
    val exportedFilePath: String? = null,
    val showExportSheet: Boolean = false,
    val showTrimControls: Boolean = false,
    val showEffectsSheet: Boolean = false,
    val showAudioSheet: Boolean = false,
    val showTextSheet: Boolean = false,
    val showAiAutoCutDialog: Boolean = false,
    val detectedSilencesCount: Int = 0,
    val errorMessage: String? = null
)
