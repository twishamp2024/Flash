package org.cutwire.drift.feature.timeline

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import org.cutwire.drift.core.model.Clip
import org.cutwire.drift.core.model.Project
import org.cutwire.drift.core.model.Track
import org.cutwire.drift.core.model.TrackType
import org.cutwire.drift.core.designsystem.theme.*
import org.cutwire.drift.core.model.formatTimecode

private val TRACK_HEADER_WIDTH = 56.dp
private val TRACK_HEIGHT = 64.dp
private val RULER_HEIGHT = 28.dp

@Composable
fun MultiTrackTimeline(
    project: Project?,
    currentPositionUs: Long,
    selectedClip: Clip?,
    filmstripThumbnails: List<Bitmap>,
    onSeek: (Long) -> Unit,
    onSelectClip: (Clip?) -> Unit,
    onTrimClip: (clipId: String, newSourceStartUs: Long, newSourceEndUs: Long, newTimelineStartUs: Long) -> Unit,
    onMoveClip: (clipId: String, targetTrackId: String, newTimelineStartUs: Long) -> Unit,
    onSplitClip: (clipId: String, splitTimeUs: Long) -> Unit,
    onToggleTrackMute: (trackId: String) -> Unit,
    onToggleTrackLock: (trackId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var usPerDp by remember { mutableLongStateOf(TimelineScaleHelper.DEFAULT_US_PER_DP) }
    val timelineDurationUs = project?.durationUs?.coerceAtLeast(10_000_000L) ?: 10_000_000L
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    val totalWidthDp = TimelineScaleHelper.timeUsToDp(timelineDurationUs, usPerDp) + 300.dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DriftBackground)
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom != 1.0f) {
                        val newUsPerDp = (usPerDp / zoom).toLong().coerceIn(
                            TimelineScaleHelper.MIN_US_PER_DP,
                            TimelineScaleHelper.MAX_US_PER_DP
                        )
                        usPerDp = newUsPerDp
                    }
                }
            }
    ) {
        // Track Controls + Timeline View
        Row(modifier = Modifier.fillMaxSize()) {
            // Left fixed track headers column
            Column(
                modifier = Modifier
                    .width(TRACK_HEADER_WIDTH)
                    .fillMaxHeight()
                    .background(DriftSurface)
                    .border(width = 1.dp, color = DriftBorder)
                    .zIndex(2f)
            ) {
                // Header spacer matching ruler height
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RULER_HEIGHT)
                        .background(DriftSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tracks",
                        fontSize = 10.sp,
                        color = DriftTextMuted,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Track header pills
                val tracks = project?.tracks.orEmpty()
                tracks.forEach { track ->
                    TrackHeaderItem(
                        track = track,
                        onToggleMute = { onToggleTrackMute(track.id) },
                        onToggleLock = { onToggleTrackLock(track.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(TRACK_HEIGHT)
                    )
                }
            }

            // Right scrollable timeline body
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
            ) {
                Column(modifier = Modifier.width(totalWidthDp)) {
                    // 1. Time Ruler
                    TimelineRuler(
                        durationUs = timelineDurationUs,
                        usPerDp = usPerDp,
                        onSeek = { targetUs ->
                            val snapped = SnapHelper.findSnap(
                                targetTimeUs = targetUs,
                                project = project,
                                playheadPositionUs = currentPositionUs
                            )
                            onSeek(snapped.snappedTimeUs)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(RULER_HEIGHT)
                    )

                    // 2. Track Lanes
                    val tracks = project?.tracks.orEmpty()
                    tracks.forEach { track ->
                        TrackLane(
                            track = track,
                            usPerDp = usPerDp,
                            selectedClipId = selectedClip?.id,
                            filmstripThumbnails = filmstripThumbnails,
                            onClipClick = { clip -> onSelectClip(clip) },
                            onTrimClip = onTrimClip,
                            onMoveClip = { clipId, newStartUs ->
                                val snapped = SnapHelper.findSnap(
                                    targetTimeUs = newStartUs,
                                    project = project,
                                    playheadPositionUs = currentPositionUs,
                                    excludeClipId = clipId
                                )
                                onMoveClip(clipId, track.id, snapped.snappedTimeUs)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(TRACK_HEIGHT)
                        )
                    }
                }

                // 3. Vertical Playhead Line spanning all tracks
                val playheadOffsetDp = TimelineScaleHelper.timeUsToDp(currentPositionUs, usPerDp)
                Box(
                    modifier = Modifier
                        .offset(x = playheadOffsetDp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(DriftPlayhead)
                        .zIndex(5f)
                ) {
                    // Playhead diamond handle at ruler top
                    Box(
                        modifier = Modifier
                            .offset(x = (-5).dp, y = 0.dp)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(DriftPlayhead)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackHeaderItem(
    track: Track,
    onToggleMute: () -> Unit,
    onToggleLock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trackIcon = when (track.type) {
        TrackType.VIDEO -> Icons.Default.Videocam
        TrackType.AUDIO -> Icons.Default.Audiotrack
        TrackType.TEXT -> Icons.Default.TextFields
        TrackType.OVERLAY -> Icons.Default.Add
    }

    Column(
        modifier = modifier
            .border(width = 0.5.dp, color = DriftBorder)
            .background(if (track.isLocked) DriftSurfaceVariant else DriftSurface)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Icon(
            imageVector = trackIcon,
            contentDescription = null,
            tint = if (track.isMuted) DriftTextMuted else DriftAccent,
            modifier = Modifier.size(16.dp)
        )

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (track.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "Mute",
                tint = if (track.isMuted) DriftPlayhead else DriftTextMuted,
                modifier = Modifier
                    .size(14.dp)
                    .clickable { onToggleMute() }
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (track.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = "Lock",
                tint = if (track.isLocked) DriftAccent else DriftTextMuted,
                modifier = Modifier
                    .size(14.dp)
                    .clickable { onToggleLock() }
            )
        }
    }
}

@Composable
private fun TimelineRuler(
    durationUs: Long,
    usPerDp: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val stepUs = TimelineScaleHelper.calculateRulerTickStepUs(usPerDp)
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .background(DriftSurfaceVariant)
            .border(width = 0.5.dp, color = DriftBorder)
            .pointerInput(usPerDp) {
                detectTapGestures { offset ->
                    val dpValue = with(density) { offset.x.toDp().value }
                    val seekTimeUs = TimelineScaleHelper.dpToTimeUs(dpValue, usPerDp)
                    onSeek(seekTimeUs)
                }
            }
            .pointerInput(usPerDp) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val dpValue = with(density) { change.position.x.toDp().value }
                    val seekTimeUs = TimelineScaleHelper.dpToTimeUs(dpValue, usPerDp)
                    onSeek(seekTimeUs)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalTicks = (durationUs / stepUs).toInt() + 1
            for (i in 0..totalTicks) {
                val tickTimeUs = i * stepUs
                val xDp = (tickTimeUs.toFloat() / usPerDp)
                val xPx = xDp * density.density

                // Major tick line
                drawLine(
                    color = Color(0xFF6B7280),
                    start = Offset(xPx, size.height - 8.dp.toPx()),
                    end = Offset(xPx, size.height),
                    strokeWidth = 1.dp.toPx()
                )

                // Sub-ticks
                val subStepPx = (stepUs.toFloat() / usPerDp) * density.density / 4
                for (j in 1..3) {
                    val subXPx = xPx + j * subStepPx
                    drawLine(
                        color = Color(0xFF4B5563),
                        start = Offset(subXPx, size.height - 4.dp.toPx()),
                        end = Offset(subXPx, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
        }

        // Timestamp text labels
        val totalTicks = (durationUs / stepUs).toInt() + 1
        for (i in 0..totalTicks) {
            val tickTimeUs = i * stepUs
            val xDp = TimelineScaleHelper.timeUsToDp(tickTimeUs, usPerDp)
            Text(
                text = tickTimeUs.formatTimecode(30).take(5),
                color = DriftTextMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.offset(x = xDp + 2.dp, y = 2.dp)
            )
        }
    }
}

@Composable
private fun TrackLane(
    track: Track,
    usPerDp: Long,
    selectedClipId: String?,
    filmstripThumbnails: List<Bitmap>,
    onClipClick: (Clip) -> Unit,
    onTrimClip: (clipId: String, newSourceStartUs: Long, newSourceEndUs: Long, newTimelineStartUs: Long) -> Unit,
    onMoveClip: (clipId: String, newTimelineStartUs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .border(width = 0.5.dp, color = DriftBorder)
            .background(DriftBackground)
    ) {
        track.clips.forEach { clip ->
            val isSelected = clip.id == selectedClipId
            TimelineClipItem(
                clip = clip,
                trackType = track.type,
                usPerDp = usPerDp,
                isSelected = isSelected,
                isTrackLocked = track.isLocked,
                thumbnails = if (isSelected) filmstripThumbnails else emptyList(),
                onClick = { onClipClick(clip) },
                onTrim = onTrimClip,
                onMove = onMoveClip
            )
        }
    }
}

@Composable
private fun TimelineClipItem(
    clip: Clip,
    trackType: TrackType = TrackType.VIDEO,
    usPerDp: Long,
    isSelected: Boolean,
    isTrackLocked: Boolean,
    thumbnails: List<Bitmap>,
    onClick: () -> Unit,
    onTrim: (clipId: String, newSourceStartUs: Long, newSourceEndUs: Long, newTimelineStartUs: Long) -> Unit,
    onMove: (clipId: String, newTimelineStartUs: Long) -> Unit
) {
    val density = LocalDensity.current
    val startOffsetDp = TimelineScaleHelper.timeUsToDp(clip.timelineStartUs, usPerDp)
    val clipWidthDp = TimelineScaleHelper.timeUsToDp(clip.durationUs, usPerDp).coerceAtLeast(36.dp)

    var dragOffsetPx by remember { mutableFloatStateOf(0f) }

    val clipBgColor = when (trackType) {
        TrackType.AUDIO -> if (isSelected) Color(0xFF064E3B) else Color(0xFF042F2E)
        TrackType.TEXT, TrackType.OVERLAY -> if (isSelected) Color(0xFF312E81) else Color(0xFF1E1B4B)
        else -> if (isSelected) DriftSurfaceVariant else DriftCard
    }

    val clipBorderColor = when (trackType) {
        TrackType.AUDIO -> if (isSelected) Color(0xFF10B981) else Color(0xFF047857)
        TrackType.TEXT, TrackType.OVERLAY -> if (isSelected) Color(0xFF818CF8) else Color(0xFF4338CA)
        else -> if (isSelected) DriftAccent else DriftBorder
    }

    Box(
        modifier = Modifier
            .offset(x = startOffsetDp + with(density) { dragOffsetPx.toDp() })
            .width(clipWidthDp)
            .fillMaxHeight()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(clipBgColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = clipBorderColor,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(enabled = !isTrackLocked) { onClick() }
            .pointerInput(clip.id, isTrackLocked) {
                if (!isTrackLocked) {
                    detectDragGestures(
                        onDragEnd = {
                            val deltaUs = TimelineScaleHelper.dpToTimeUs(with(density) { dragOffsetPx.toDp().value }, usPerDp)
                            val finalStartUs = (clip.timelineStartUs + deltaUs).coerceAtLeast(0L)
                            dragOffsetPx = 0f
                            onMove(clip.id, finalStartUs)
                        },
                        onDragCancel = {
                            dragOffsetPx = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetPx += dragAmount.x
                        }
                    )
                }
            }
    ) {
        // Filmstrip background inside clip (for video)
        if (thumbnails.isNotEmpty() && trackType == TrackType.VIDEO) {
            Row(modifier = Modifier.fillMaxSize()) {
                thumbnails.forEach { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                    )
                }
            }
        }

        // Audio Waveform Visualization (for audio tracks)
        if (trackType == TrackType.AUDIO) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                val stepX = 4.dp.toPx()
                val barWidth = 2.dp.toPx()
                val barCount = (size.width / stepX).toInt().coerceAtLeast(1)
                val midY = size.height / 2f
                val waveColor = if (clip.isMuted) Color(0xFF6B7280) else Color(0xFF10B981)

                for (i in 0 until barCount) {
                    val x = i * stepX
                    val ampFactor = 0.25f + 0.65f * kotlin.math.abs(kotlin.math.sin(i * 0.35f) * kotlin.math.sin(i * 0.12f))
                    val barHeight = (size.height * 0.42f) * ampFactor * clip.volume.coerceIn(0.2f, 1.5f)

                    drawLine(
                        color = waveColor,
                        start = androidx.compose.ui.geometry.Offset(x, midY - barHeight),
                        end = androidx.compose.ui.geometry.Offset(x, midY + barHeight),
                        strokeWidth = barWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
        }

        // Overlay with clip duration & name
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = if (thumbnails.isNotEmpty()) 0.35f else 0f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            if (trackType == TrackType.TEXT) {
                Text(
                    text = clip.textOverlay?.text ?: "Text",
                    color = DriftTextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 2.dp)
                )
            }
            Row(
                modifier = Modifier.align(Alignment.BottomStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (clip.isMuted) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = "Muted",
                        tint = DriftPlayhead,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                }
                Text(
                    text = clip.durationUs.formatTimecode(30).take(5),
                    color = DriftTextPrimary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                if (clip.playbackSpeed != 1.0f) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${clip.playbackSpeed}x",
                        color = DriftAccent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Left trim handle (when selected)
        if (isSelected && !isTrackLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(14.dp)
                    .fillMaxHeight()
                    .background(DriftAccent)
                    .pointerInput(clip.id) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaUs = TimelineScaleHelper.dpToTimeUs(with(density) { dragAmount.toDp().value }, usPerDp)
                            val newSourceStartUs = (clip.sourceStartUs + deltaUs).coerceIn(0L, clip.sourceEndUs - 100_000L)
                            val newTimelineStartUs = (clip.timelineStartUs + deltaUs).coerceAtLeast(0L)
                            onTrim(clip.id, newSourceStartUs, clip.sourceEndUs, newTimelineStartUs)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(16.dp)
                        .background(Color.Black)
                )
            }

            // Right trim handle (when selected)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(14.dp)
                    .fillMaxHeight()
                    .background(DriftAccent)
                    .pointerInput(clip.id) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaUs = TimelineScaleHelper.dpToTimeUs(with(density) { dragAmount.toDp().value }, usPerDp)
                            val newSourceEndUs = (clip.sourceEndUs + deltaUs).coerceAtLeast(clip.sourceStartUs + 100_000L)
                            onTrim(clip.id, clip.sourceStartUs, newSourceEndUs, clip.timelineStartUs)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(16.dp)
                        .background(Color.Black)
                )
            }
        }
    }
}
