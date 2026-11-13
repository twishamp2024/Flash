package org.cutwire.drift.engine.preview

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(UnstableApi::class)
class VideoPlayerEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionTickerJob: Job? = null

    private var player: ExoPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackPositionUs = MutableStateFlow(0L)
    val playbackPositionUs: StateFlow<Long> = _playbackPositionUs.asStateFlow()

    private val _durationUs = MutableStateFlow(0L)
    val durationUs: StateFlow<Long> = _durationUs.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private var currentUri: Uri? = null
    private var inPointUs: Long = 0L
    private var outPointUs: Long = C.TIME_END_OF_SOURCE
    private var isImageSource: Boolean = false
    private var imageTickerJob: Job? = null

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        if (player != null) return

        player = ExoPlayer.Builder(context)
            .setSeekParameters(SeekParameters.EXACT)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = false

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_READY -> {
                                _isReady.value = true
                                _isBuffering.value = false
                                val durationMs = duration
                                if (durationMs != C.TIME_UNSET) {
                                    _durationUs.value = durationMs * 1_000L
                                }
                            }
                            Player.STATE_BUFFERING -> {
                                _isBuffering.value = true
                            }
                            Player.STATE_ENDED -> {
                                _isPlaying.value = false
                                stopPositionTicker()
                                // When ended, loop back to inPoint if editing
                                seekToUs(inPointUs)
                            }
                            Player.STATE_IDLE -> {
                                _isReady.value = false
                                _isBuffering.value = false
                            }
                        }
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                        if (playing) {
                            startPositionTicker()
                        } else {
                            stopPositionTicker()
                            updateCurrentPosition()
                        }
                    }
                })
            }
    }

    fun attachPlayerView(playerView: PlayerView) {
        playerView.player = player
        playerView.useController = false
    }

    fun detachPlayerView(playerView: PlayerView) {
        if (playerView.player == player) {
            playerView.player = null
        }
    }

    /**
     * Set a media item with optional in/out trimming boundaries in microseconds.
     */
    fun setSource(
        uri: Uri,
        clipInUs: Long = 0L,
        clipOutUs: Long = 0L,
        autoPlay: Boolean = false
    ) {
        currentUri = uri
        inPointUs = clipInUs
        outPointUs = if (clipOutUs > clipInUs) clipOutUs else C.TIME_END_OF_SOURCE

        val uriStr = uri.toString().lowercase()
        val isImg = uriStr.endsWith(".png") || uriStr.endsWith(".jpg") || uriStr.endsWith(".jpeg") || uriStr.endsWith(".webp")
        isImageSource = isImg

        if (isImg) {
            val duration = if (clipOutUs > clipInUs) clipOutUs - clipInUs else 3_000_000L
            _durationUs.value = duration
            _playbackPositionUs.value = 0L
            _isReady.value = true
            _isBuffering.value = false
            if (autoPlay) {
                play()
            }
        } else {
            val clippingConfig = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionUs(inPointUs)
                .apply {
                    if (outPointUs != C.TIME_END_OF_SOURCE) {
                        setEndPositionUs(outPointUs)
                    }
                }
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setClippingConfiguration(clippingConfig)
                .build()

            player?.apply {
                setMediaItem(mediaItem)
                prepare()
                seekTo(0L)
                playWhenReady = autoPlay
            }

            _playbackPositionUs.value = 0L
        }
    }

    /**
     * Update trimming boundaries without reloading media if source is identical.
     */
    fun updateTrimBounds(clipInUs: Long, clipOutUs: Long) {
        inPointUs = clipInUs
        outPointUs = if (clipOutUs > clipInUs) clipOutUs else C.TIME_END_OF_SOURCE
        currentUri?.let { uri ->
            val wasPlaying = isPlaying.value
            val currentPos = _playbackPositionUs.value
            setSource(uri, inPointUs, outPointUs, autoPlay = wasPlaying)
            seekToUs(currentPos.coerceIn(0L, (outPointUs - inPointUs).coerceAtLeast(0L)))
        }
    }

    fun play() {
        if (isImageSource) {
            _isPlaying.value = true
            startImageTicker()
        } else {
            player?.play()
        }
    }

    fun pause() {
        if (isImageSource) {
            _isPlaying.value = false
            stopImageTicker()
        } else {
            player?.pause()
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            play()
        }
    }

    /**
     * Seek to exact position in microseconds relative to the clip / timeline start.
     */
    fun seekToUs(positionUs: Long) {
        if (isImageSource) {
            val maxDur = _durationUs.value.coerceAtLeast(1_000_000L)
            _playbackPositionUs.value = positionUs.coerceIn(0L, maxDur)
        } else {
            val positionMs = (positionUs / 1_000L).coerceAtLeast(0L)
            player?.seekTo(positionMs)
            _playbackPositionUs.value = positionUs
        }
    }

    /**
     * Apply a list of OpenGL ES video effects directly to the ExoPlayer rendering pipeline.
     */
    fun setEffects(effects: List<androidx.media3.common.Effect>) {
        player?.setVideoEffects(effects)
    }

    private fun startImageTicker() {
        stopImageTicker()
        imageTickerJob = scope.launch {
            val startWallClock = System.currentTimeMillis()
            val startOffsetUs = _playbackPositionUs.value
            val totalUs = _durationUs.value.coerceAtLeast(1_000_000L)
            while (isActive && _isPlaying.value) {
                val elapsedMs = System.currentTimeMillis() - startWallClock
                val currentUs = startOffsetUs + elapsedMs * 1_000L
                if (currentUs >= totalUs) {
                    _playbackPositionUs.value = 0L
                    _isPlaying.value = false
                    break
                } else {
                    _playbackPositionUs.value = currentUs
                }
                delay(16L) // ~60fps
            }
        }
    }

    private fun stopImageTicker() {
        imageTickerJob?.cancel()
        imageTickerJob = null
    }

    /**
     * Step forward or backward by a specific microsecond offset (e.g. 1 frame at 30fps = 33333 Us).
     */
    fun stepUs(offsetUs: Long) {
        val newPosUs = (_playbackPositionUs.value + offsetUs).coerceAtLeast(0L)
        seekToUs(newPosUs)
    }

    fun setSpeed(speed: Float) {
        player?.playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 4.0f))
    }

    fun setVolume(volume: Float) {
        player?.volume = volume.coerceIn(0.0f, 2.0f)
    }

    fun setMuted(muted: Boolean) {
        player?.volume = if (muted) 0.0f else 1.0f
    }

    private fun startPositionTicker() {
        stopPositionTicker()
        positionTickerJob = scope.launch {
            while (isActive) {
                updateCurrentPosition()
                delay(16L) // ~60 FPS position updates for smooth playhead scrubbing
            }
        }
    }

    private fun stopPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = null
    }

    private fun updateCurrentPosition() {
        player?.let { p ->
            val posMs = p.currentPosition
            _playbackPositionUs.value = posMs * 1_000L
        }
    }

    fun release() {
        stopPositionTicker()
        scope.cancel()
        player?.release()
        player = null
    }
}
