package org.cutwire.drift.feature.editor

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import org.cutwire.drift.core.designsystem.theme.*
import org.cutwire.drift.core.model.Clip
import org.cutwire.drift.core.model.EffectType
import org.cutwire.drift.core.model.TextAlignment
import org.cutwire.drift.core.model.TextOverlay
import org.cutwire.drift.core.model.TrackType
import org.cutwire.drift.core.model.formatTimecode
import org.cutwire.drift.feature.timeline.MultiTrackTimeline

@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Media Picker Launcher for videos and images
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        android.util.Log.d("DriftMedia", "Picked ${uris.size} uris: $uris")
        if (uris.isNotEmpty()) {
            viewModel.importMedia(uris)
        }
    }

    // Export Modal Bottom Sheet
    if (uiState.showExportSheet) {
        ExportBottomSheet(
            isExporting = uiState.isExporting,
            progress = uiState.exportProgress,
            statusMessage = uiState.exportStatusMessage,
            errorMessage = uiState.errorMessage,
            exportedFileUri = uiState.exportedFileUri,
            onDismiss = { viewModel.closeExportSheet() },
            onStartExport = { res, fps -> viewModel.startExport(res, fps) },
            onCancelExport = { viewModel.cancelExport() },
            onShareExport = { uri ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
            }
        )
    }

    // Effects Modal Bottom Sheet
    if (uiState.showEffectsSheet) {
        EffectsBottomSheet(
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { viewModel.toggleEffectsSheet() }
        )
    }

    // Audio Modal Bottom Sheet
    if (uiState.showAudioSheet) {
        AudioBottomSheet(
            uiState = uiState,
            viewModel = viewModel,
            onDismiss = { viewModel.toggleAudioSheet() }
        )
    }

    // Text Modal Bottom Sheet
    if (uiState.showTextSheet) {
        TextOverlayBottomSheet(
            onDismiss = { viewModel.toggleTextSheet() },
            onAddText = { text, color, fontSize ->
                viewModel.addTextOverlay(text, color, fontSize)
                viewModel.toggleTextSheet()
            }
        )
    }

    // AI Smart Auto-Cut Dialog
    if (uiState.showAiAutoCutDialog) {
        AiAutoCutDialog(
            silenceCount = uiState.detectedSilencesCount,
            onDismiss = { viewModel.dismissAiAutoCutDialog() },
            onApply = { viewModel.applyAiSmartAutoCut() }
        )
    }

    Scaffold(
        containerColor = DriftBackground,
        topBar = {
            EditorTopBar(
                projectName = uiState.project?.name ?: "Editor",
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                onBackClick = onNavigateBack,
                onUndoClick = { viewModel.undo() },
                onRedoClick = { viewModel.redo() },
                onExportClick = { viewModel.openExportSheet() }
            )
        },
        bottomBar = {
            EditorBottomToolbar(
                onAddMediaClick = {
                    mediaPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                },
                onTextClick = { viewModel.toggleTextSheet() },
                onTrimClick = { viewModel.toggleTrimControls() },
                onSplitClick = { viewModel.splitClipAtPlayhead() },
                onEffectsClick = { viewModel.toggleEffectsSheet() },
                onAudioClick = { viewModel.toggleAudioSheet() },
                onAiCutClick = { viewModel.openAiAutoCutDialog() },
                onSubtitlesClick = { viewModel.applyAutoSubtitles() }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Error banner if any
            uiState.errorMessage?.let { error ->
                Surface(
                    color = DriftError.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error,
                        color = DriftError,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }

            // 1. Preview Viewport (PlayerView or Placeholder)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.0f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val ratio = uiState.project?.aspectRatio?.floatRatio ?: (16f / 9f)

                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.96f)
                        .aspectRatio(ratio)
                        .background(DriftSurfaceVariant)
                        .border(1.dp, DriftBorder),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.activeAssetUri != null) {
                        VideoPlayerViewport(
                            viewModel = viewModel,
                            activeUri = uiState.activeAssetUri,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Empty State Placeholder
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = DriftTextTertiary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No Media Loaded",
                                color = DriftTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap '+ Add Media' below to import your video",
                                color = DriftTextSecondary,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Render Active Text Overlays in Real-Time Preview
                    val activeTextClips = remember(uiState.project, uiState.currentPositionUs) {
                        uiState.project?.tracks
                            ?.filter { it.type == TrackType.TEXT && !it.isHidden }
                            ?.flatMap { it.clips }
                            ?.filter { clip ->
                                uiState.currentPositionUs in clip.timelineStartUs until (clip.timelineStartUs + clip.durationUs)
                            } ?: emptyList()
                    }
                    for (textClip in activeTextClips) {
                        val overlay = textClip.textOverlay
                        if (overlay != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp),
                                contentAlignment = when (overlay.alignment) {
                                    TextAlignment.LEFT -> Alignment.CenterStart
                                    TextAlignment.CENTER -> Alignment.Center
                                    TextAlignment.RIGHT -> Alignment.CenterEnd
                                }
                            ) {
                                Text(
                                    text = overlay.text,
                                    color = Color(overlay.textColor),
                                    fontSize = overlay.fontSizeSp.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = when (overlay.alignment) {
                                        TextAlignment.LEFT -> TextAlign.Left
                                        TextAlignment.CENTER -> TextAlign.Center
                                        TextAlignment.RIGHT -> TextAlign.Right
                                    },
                                    modifier = Modifier
                                        .then(
                                            if (overlay.backgroundColor != 0L) {
                                                Modifier
                                                    .background(Color(overlay.backgroundColor), RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                            } else Modifier
                                        )
                                )
                            }
                        }
                    }

                    // Floating Timecode Badge
                    val frameRate = uiState.project?.frameRate ?: 30
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = "${uiState.currentPositionUs.formatTimecode(frameRate)} / ${uiState.durationUs.formatTimecode(frameRate)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = DriftTextPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 2. Playback Transport Bar
            EditorPlaybackTransport(
                isPlaying = uiState.isPlaying,
                onPlayPauseClick = { viewModel.togglePlayPause() },
                onStepBackward = { viewModel.stepUs(-1_000_000L) },
                onStepForward = { viewModel.stepUs(1_000_000L) },
                onRewindClick = { viewModel.seekTo(0L) }
            )

            // 3. Trimming Quick Controls (when clip selected and toggled)
            if (uiState.showTrimControls && uiState.selectedClip != null) {
                ClipTrimControls(
                    clip = uiState.selectedClip!!,
                    currentPosUs = uiState.currentPositionUs,
                    frameRate = uiState.project?.frameRate ?: 30,
                    onSetIn = { viewModel.setInPointAtPlayhead() },
                    onSetOut = { viewModel.setOutPointAtPlayhead() },
                    onResetTrim = { viewModel.resetTrim() }
                )
            }

            // 4. Interactive Timeline View
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.1f)
                    .background(DriftSurface)
                    .border(androidx.compose.foundation.BorderStroke(1.dp, DriftBorder))
            ) {
                val hasClips = uiState.project?.tracks?.any { it.clips.isNotEmpty() } == true
                if (hasClips) {
                    MultiTrackTimeline(
                        project = uiState.project,
                        currentPositionUs = uiState.currentPositionUs,
                        selectedClip = uiState.selectedClip,
                        filmstripThumbnails = uiState.filmstripThumbnails,
                        onSeek = { viewModel.seekTo(it) },
                        onSelectClip = { viewModel.selectClip(it) },
                        onTrimClip = { clipId, inUs, outUs, startUs -> viewModel.trimClip(clipId, inUs, outUs, startUs) },
                        onMoveClip = { clipId, trackId, startUs -> viewModel.moveClip(clipId, trackId, startUs) },
                        onSplitClip = { _, _ -> viewModel.splitClipAtPlayhead() },
                        onToggleTrackMute = { viewModel.toggleTrackMute(it) },
                        onToggleTrackLock = { viewModel.toggleTrackLock(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = DriftAccent,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Multi-Track Timeline",
                            fontWeight = FontWeight.Bold,
                            color = DriftTextPrimary,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Tap '+ Add Media' to import video clips",
                            color = DriftTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerViewport(
    viewModel: EditorViewModel,
    activeUri: Uri?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriStr = activeUri?.toString()?.lowercase() ?: ""
    val isImage = uriStr.endsWith(".png") || uriStr.endsWith(".jpg") || uriStr.endsWith(".jpeg") || uriStr.endsWith(".webp")

    if (isImage && activeUri != null) {
        val bitmap = remember(activeUri) {
            try {
                if (activeUri.scheme == "file") {
                    android.graphics.BitmapFactory.decodeFile(activeUri.path)
                } else {
                    context.contentResolver.openInputStream(activeUri)?.use {
                        android.graphics.BitmapFactory.decodeStream(it)
                    }
                }
            } catch (_: Exception) {
                null
            }
        }

        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = modifier.fillMaxSize()
            )
        } else {
            Box(modifier = modifier.fillMaxSize().background(Color.DarkGray))
        }
    } else {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                    viewModel.playerEngine.attachPlayerView(this)
                }
            },
            update = { playerView ->
                viewModel.playerEngine.attachPlayerView(playerView)
            },
            modifier = modifier
        )
    }
}

@Composable
private fun TimelineClipView(
    clip: Clip,
    filmstrip: List<Bitmap>,
    currentPositionUs: Long,
    durationUs: Long,
    frameRate: Int,
    onSeekTo: (Long) -> Unit,
    onTrimChanged: (Long, Long) -> Unit
) {
    val totalTimeUs = if (durationUs > 0L) durationUs else clip.durationUs
    val progress = if (totalTimeUs > 0L) (currentPositionUs.toFloat() / totalTimeUs.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Track Header & Time Info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = DriftAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Track 1: Main Video",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = DriftTextPrimary
                )
            }
            Text(
                text = "Clip Duration: ${clip.durationUs.formatTimecode(frameRate)}",
                fontSize = 11.sp,
                color = DriftTextSecondary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Interactive Clip Strip with Filmstrip Bitmaps & Playhead
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DriftSurfaceVariant)
                .border(2.dp, DriftAccent, RoundedCornerShape(8.dp))
                .pointerInput(totalTimeUs) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val newProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        val seekUs = (newProgress * totalTimeUs).toLong()
                        onSeekTo(seekUs)
                    }
                }
        ) {
            // Filmstrip Thumbnails Row
            if (filmstrip.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    filmstrip.forEach { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading filmstrip frames...",
                        color = DriftTextTertiary,
                        fontSize = 11.sp
                    )
                }
            }

            // Left In-point Handle Accent
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(10.dp)
                    .background(DriftAccent)
            )

            // Right Out-point Handle Accent
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(10.dp)
                    .background(DriftAccent)
            )

            // Playhead Needle
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val needleOffset = maxWidth * progress
                Box(
                    modifier = Modifier
                        .offset(x = needleOffset)
                        .width(2.5.dp)
                        .fillMaxHeight()
                        .background(Color.White)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Timecode ruler hint
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("00:00:00", fontSize = 10.sp, color = DriftTextTertiary)
            Text(totalTimeUs.formatTimecode(frameRate), fontSize = 10.sp, color = DriftTextTertiary)
        }
    }
}

@Composable
private fun ClipTrimControls(
    clip: Clip,
    currentPosUs: Long,
    frameRate: Int,
    onSetIn: () -> Unit,
    onSetOut: () -> Unit,
    onResetTrim: () -> Unit
) {
    Surface(
        color = DriftSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Set In Button
            Button(
                onClick = onSetIn,
                colors = ButtonDefaults.buttonColors(containerColor = DriftSurface),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.West,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = DriftAccent
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Set In", fontSize = 11.sp, color = DriftTextPrimary)
            }

            // In & Out Timecode Info
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "In: ${clip.sourceStartUs.formatTimecode(frameRate)}  •  Out: ${clip.sourceEndUs.formatTimecode(frameRate)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = DriftAccent
                )
                Text(
                    text = "Trimmed: ${clip.durationUs.formatTimecode(frameRate)}",
                    fontSize = 10.sp,
                    color = DriftTextSecondary
                )
            }

            // Set Out Button
            Button(
                onClick = onSetOut,
                colors = ButtonDefaults.buttonColors(containerColor = DriftSurface),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Set Out", fontSize = 11.sp, color = DriftTextPrimary)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.East,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = DriftAccent
                )
            }

            // Reset Trim
            IconButton(
                onClick = onResetTrim,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset Trim",
                    tint = DriftTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EditorPlaybackTransport(
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onStepBackward: () -> Unit,
    onStepForward: () -> Unit,
    onRewindClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DriftBackground)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onRewindClick) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Rewind to Start",
                tint = DriftTextPrimary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onStepBackward) {
            Icon(
                imageVector = Icons.Default.Replay,
                contentDescription = "Step Back 1s",
                tint = DriftTextSecondary
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(DriftAccent)
                .clickable(onClick = onPlayPauseClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = DriftOnAccent,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        IconButton(onClick = onStepForward) {
            Icon(
                imageVector = Icons.Default.Forward10,
                contentDescription = "Step Forward 1s",
                tint = DriftTextSecondary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
    }
}

@Composable
private fun EditorTopBar(
    projectName: String,
    canUndo: Boolean,
    canRedo: Boolean,
    onBackClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onExportClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = DriftTextPrimary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = projectName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = DriftTextPrimary,
                maxLines = 1
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onUndoClick,
                enabled = canUndo
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) DriftTextPrimary else DriftTextTertiary
                )
            }
            IconButton(
                onClick = onRedoClick,
                enabled = canRedo
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) DriftTextPrimary else DriftTextTertiary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onExportClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = DriftAccent,
                    contentColor = DriftOnAccent
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun EditorBottomToolbar(
    onAddMediaClick: () -> Unit,
    onTextClick: () -> Unit,
    onTrimClick: () -> Unit,
    onSplitClick: () -> Unit,
    onEffectsClick: () -> Unit,
    onAudioClick: () -> Unit,
    onAiCutClick: () -> Unit,
    onSubtitlesClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DriftSurface)
            .navigationBarsPadding()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolbarActionItem(icon = Icons.Default.AddPhotoAlternate, label = "Add Media", onClick = onAddMediaClick)
        ToolbarActionItem(icon = Icons.Default.TextFields, label = "Text", onClick = onTextClick)
        ToolbarActionItem(icon = Icons.Default.ContentCut, label = "Trim", onClick = onTrimClick)
        ToolbarActionItem(icon = Icons.AutoMirrored.Filled.CallSplit, label = "Split", onClick = onSplitClick)
        ToolbarActionItem(icon = Icons.Default.AutoFixHigh, label = "Effects", onClick = onEffectsClick)
        ToolbarActionItem(icon = Icons.Default.GraphicEq, label = "Audio", onClick = onAudioClick)
        ToolbarActionItem(icon = Icons.Default.AutoAwesome, label = "AI Cut", onClick = onAiCutClick)
        ToolbarActionItem(icon = Icons.Default.Subtitles, label = "Subtitles", onClick = onSubtitlesClick)
    }
}

@Composable
private fun ToolbarActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = DriftTextPrimary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = DriftTextSecondary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportBottomSheet(
    isExporting: Boolean,
    progress: Int,
    statusMessage: String?,
    errorMessage: String?,
    exportedFileUri: Uri?,
    onDismiss: () -> Unit,
    onStartExport: (ExportResolution, Int) -> Unit,
    onCancelExport: () -> Unit,
    onShareExport: (Uri) -> Unit
) {
    var selectedResolution by remember { mutableStateOf(ExportResolution.FHD_1080P) }
    var selectedFps by remember { mutableIntStateOf(30) }

    ModalBottomSheet(
        onDismissRequest = {
            if (!isExporting) onDismiss()
        },
        containerColor = DriftSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = DriftTextTertiary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Export Project",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = DriftTextPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (exportedFileUri != null) {
                // Export Complete State
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = DriftSuccess,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Export Complete!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = DriftTextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Saved to Movies/Flash in device gallery",
                        fontSize = 13.sp,
                        color = DriftTextSecondary
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Done", color = DriftTextPrimary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = { onShareExport(exportedFileUri) },
                            colors = ButtonDefaults.buttonColors(containerColor = DriftAccent),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = DriftOnAccent)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share", color = DriftOnAccent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (isExporting) {
                // Active Export Progress State
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = statusMessage ?: "Exporting video...",
                        fontSize = 14.sp,
                        color = DriftTextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = DriftAccent,
                        trackColor = DriftSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$progress%",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = DriftAccent
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = onCancelExport,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DriftError)
                    ) {
                        Text("Cancel Export")
                    }
                }
            } else {
                // Configuration State
                Text(
                    text = "Resolution",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = DriftTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExportResolution.entries.forEach { res ->
                        FilterChip(
                            selected = (selectedResolution == res),
                            onClick = { selectedResolution = res },
                            label = { Text(res.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DriftAccent,
                                selectedLabelColor = DriftOnAccent
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Frame Rate",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = DriftTextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(24, 30, 60).forEach { fps ->
                        FilterChip(
                            selected = (selectedFps == fps),
                            onClick = { selectedFps = fps },
                            label = { Text("$fps fps", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DriftAccent,
                                selectedLabelColor = DriftOnAccent
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { onStartExport(selectedResolution, selectedFps) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DriftAccent,
                        contentColor = DriftOnAccent
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.FileDownload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Export", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EffectsBottomSheet(
    uiState: EditorUiState,
    viewModel: EditorViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedClip = uiState.selectedClip

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DriftSurface,
        scrimColor = Color.Black.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Video Effects & Color Grading",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DriftTextPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = DriftTextSecondary)
                }
            }

            if (selectedClip == null) {
                Text(
                    text = "Select a clip on the timeline to apply effects",
                    color = DriftTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                Text(
                    text = "Add Effect",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DriftAccent,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )

                // Available Effects Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val availableTypes = listOf(
                        EffectType.BRIGHTNESS,
                        EffectType.CONTRAST,
                        EffectType.SATURATION,
                        EffectType.EXPOSURE,
                        EffectType.TEMPERATURE,
                        EffectType.TINT,
                        EffectType.GRAYSCALE,
                        EffectType.SEPIA
                    )
                    availableTypes.forEach { type ->
                        val isApplied = selectedClip.effects.any { it.type == type }
                        AssistChip(
                            onClick = { if (!isApplied) viewModel.addEffect(type) },
                            label = { Text(type.displayName, fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isApplied) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isApplied) DriftAccent else DriftTextSecondary
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isApplied) DriftSurfaceVariant else DriftCard,
                                labelColor = if (isApplied) DriftAccent else DriftTextPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Active Effects Adjustment Sliders
                if (selectedClip.effects.isNotEmpty()) {
                    Text(
                        text = "Active Effects",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DriftTextPrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    selectedClip.effects.forEach { effect ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .background(DriftCard, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = effect.type.displayName,
                                    fontWeight = FontWeight.Medium,
                                    color = DriftTextPrimary,
                                    fontSize = 13.sp
                                )
                                IconButton(
                                    onClick = { viewModel.removeEffect(effect.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove",
                                        tint = DriftError,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Sliders for effect parameters
                            when (effect.type) {
                                EffectType.BRIGHTNESS -> {
                                    val b = effect.parameters["brightness"] ?: 0.0f
                                    EffectSlider(
                                        label = "Brightness",
                                        value = b,
                                        range = -1f..1f,
                                        onValueChange = { viewModel.updateEffectParameter(effect.id, "brightness", it) }
                                    )
                                }
                                EffectType.CONTRAST -> {
                                    val c = effect.parameters["contrast"] ?: 0.0f
                                    EffectSlider(
                                        label = "Contrast",
                                        value = c,
                                        range = -1f..1f,
                                        onValueChange = { viewModel.updateEffectParameter(effect.id, "contrast", it) }
                                    )
                                }
                                EffectType.SATURATION -> {
                                    val s = effect.parameters["saturation"] ?: 1.0f
                                    EffectSlider(
                                        label = "Saturation",
                                        value = s,
                                        range = 0f..2f,
                                        onValueChange = { viewModel.updateEffectParameter(effect.id, "saturation", it) }
                                    )
                                }
                                EffectType.EXPOSURE -> {
                                    val exp = effect.parameters["exposure"] ?: 0.0f
                                    EffectSlider(
                                        label = "Exposure",
                                        value = exp,
                                        range = -2f..2f,
                                        onValueChange = { viewModel.updateEffectParameter(effect.id, "exposure", it) }
                                    )
                                }
                                EffectType.TEMPERATURE -> {
                                    val temp = effect.parameters["temperature"] ?: 0.0f
                                    EffectSlider(
                                        label = "Warmth",
                                        value = temp,
                                        range = -1f..1f,
                                        onValueChange = { viewModel.updateEffectParameter(effect.id, "temperature", it) }
                                    )
                                }
                                EffectType.TINT -> {
                                    val tint = effect.parameters["tint"] ?: 0.0f
                                    EffectSlider(
                                        label = "Tint",
                                        value = tint,
                                        range = -1f..1f,
                                        onValueChange = { viewModel.updateEffectParameter(effect.id, "tint", it) }
                                    )
                                }
                                else -> {}
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun EffectSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 11.sp, color = DriftTextSecondary)
            Text(text = "%.2f".format(value), fontSize = 11.sp, color = DriftAccent, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = DriftAccent,
                activeTrackColor = DriftAccent,
                inactiveTrackColor = DriftBorder
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioBottomSheet(
    uiState: EditorUiState,
    viewModel: EditorViewModel,
    onDismiss: () -> Unit
) {
    val selectedClip = uiState.selectedClip

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DriftSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = DriftBorder) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Audio & Sound Mixing",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = DriftTextPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = DriftTextSecondary)
                }
            }

            if (selectedClip == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Select a clip on the timeline to adjust audio",
                        color = DriftTextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))

                // 1. Volume & Mute Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Clip Volume: ${(selectedClip.volume * 100).toInt()}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = DriftTextPrimary
                    )

                    Button(
                        onClick = { viewModel.toggleClipMute() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedClip.isMuted) DriftPlayhead else DriftSurfaceVariant,
                            contentColor = DriftTextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (selectedClip.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (selectedClip.isMuted) "Unmute" else "Mute", fontSize = 12.sp)
                    }
                }

                Slider(
                    value = selectedClip.volume,
                    onValueChange = { viewModel.updateClipVolume(it) },
                    valueRange = 0.0f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = DriftAccent,
                        activeTrackColor = DriftAccent,
                        inactiveTrackColor = DriftBorder
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Playback Speed Presets & Slider
                Text(
                    text = "Playback Speed: ${String.format("%.2f", selectedClip.playbackSpeed)}x",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = DriftTextPrimary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf(0.5f, 0.75f, 1.0f, 1.5f, 2.0f)
                    presets.forEach { speed ->
                        val isSelected = kotlin.math.abs(selectedClip.playbackSpeed - speed) < 0.05f
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.updateClipSpeed(speed) },
                            label = { Text("${speed}x", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DriftAccent,
                                selectedLabelColor = DriftOnAccent,
                                containerColor = DriftSurfaceVariant,
                                labelColor = DriftTextSecondary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Audio Fade Controls
                val fadeInSec = selectedClip.audioFadeInUs / 1_000_000f
                val fadeOutSec = selectedClip.audioFadeOutUs / 1_000_000f

                Text(
                    text = "Fade In: ${String.format("%.1f", fadeInSec)}s",
                    fontSize = 13.sp,
                    color = DriftTextSecondary
                )
                Slider(
                    value = fadeInSec,
                    onValueChange = { sec ->
                        viewModel.updateClipFade((sec * 1_000_000L).toLong(), selectedClip.audioFadeOutUs)
                    },
                    valueRange = 0.0f..3.0f,
                    colors = SliderDefaults.colors(thumbColor = DriftAccent, activeTrackColor = DriftAccent)
                )

                Text(
                    text = "Fade Out: ${String.format("%.1f", fadeOutSec)}s",
                    fontSize = 13.sp,
                    color = DriftTextSecondary
                )
                Slider(
                    value = fadeOutSec,
                    onValueChange = { sec ->
                        viewModel.updateClipFade(selectedClip.audioFadeInUs, (sec * 1_000_000L).toLong())
                    },
                    valueRange = 0.0f..3.0f,
                    colors = SliderDefaults.colors(thumbColor = DriftAccent, activeTrackColor = DriftAccent)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 4. Detach Audio Action
                Button(
                    onClick = {
                        viewModel.detachAudioFromSelectedClip()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DriftSurfaceVariant,
                        contentColor = DriftAccent
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Detach Audio to Separate Track", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextOverlayBottomSheet(
    onDismiss: () -> Unit,
    onAddText: (String, Long, Float) -> Unit
) {
    var textInput by remember { mutableStateOf("Drift Video") }
    var selectedColor by remember { mutableStateOf(0xFFFFFFFFL) }
    var fontSize by remember { mutableFloatStateOf(28f) }

    val colorOptions = listOf(
        0xFFFFFFFFL to "White",
        0xFFFACC15L to "Yellow",
        0xFFEF4444L to "Red",
        0xFF06B6D4L to "Cyan",
        0xFF10B981L to "Green",
        0xFFA855F7L to "Purple"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DriftSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = DriftTextTertiary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "Add Text Overlay",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = DriftTextPrimary
            )
            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                label = { Text("Overlay Text") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = DriftTextPrimary,
                    unfocusedTextColor = DriftTextPrimary,
                    focusedBorderColor = DriftAccent,
                    unfocusedBorderColor = DriftBorder
                )
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Color", fontSize = 13.sp, color = DriftTextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                colorOptions.forEach { (colorVal, _) ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(colorVal))
                            .border(
                                width = if (selectedColor == colorVal) 3.dp else 1.dp,
                                color = if (selectedColor == colorVal) DriftAccent else Color.Gray,
                                shape = CircleShape
                            )
                            .clickable { selectedColor = colorVal }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Font Size", fontSize = 13.sp, color = DriftTextSecondary)
                Text(text = "${fontSize.toInt()} sp", fontSize = 13.sp, color = DriftAccent, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = fontSize,
                onValueChange = { fontSize = it },
                valueRange = 16f..56f,
                colors = SliderDefaults.colors(
                    thumbColor = DriftAccent,
                    activeTrackColor = DriftAccent,
                    inactiveTrackColor = DriftSurfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    if (textInput.isNotBlank()) {
                        onAddText(textInput, selectedColor, fontSize)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = DriftAccent)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add to Timeline", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AiAutoCutDialog(
    silenceCount: Int,
    onDismiss: () -> Unit,
    onApply: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = DriftAccent,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "AI Smart Auto-Cut",
                color = DriftTextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "On-device audio analysis detected $silenceCount silent pauses.",
                    color = DriftTextSecondary,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (silenceCount > 0)
                        "Would you like to automatically cut out silent intervals with atomic jump-cut edits?"
                    else
                        "No significant silence pauses detected in the current clip.",
                    color = DriftTextSecondary,
                    fontSize = 13.sp
                )
            }
        },
        confirmButton = {
            if (silenceCount > 0) {
                Button(
                    onClick = onApply,
                    colors = ButtonDefaults.buttonColors(containerColor = DriftAccent)
                ) {
                    Text("Apply Jump-Cuts", color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = DriftTextSecondary)
            }
        },
        containerColor = DriftSurface
    )
}
