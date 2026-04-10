package com.vocalize.app.util

import android.content.Context
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPosition: Int = 0,
    val duration: Int = 0,
    val currentMemoId: String? = null,
    val playbackSpeed: Float = 1.0f
)

@Singleton
class AudioPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null
    private var crossfadePlayer: MediaPlayer? = null
    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    var onPositionSave: ((String, Long) -> Unit)? = null
    var onTrackCompleted: (() -> Unit)? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    companion object {
        private const val CROSSFADE_DURATION_MS = 1500L
        private const val CROSSFADE_STEPS = 30
    }

    init {
        setupMediaSession()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(context, "VocalizeSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { togglePlayPause() }
                override fun onPause() { togglePlayPause() }
                override fun onStop() { release() }
                override fun onSeekTo(pos: Long) { seekTo(pos.toInt()) }
                override fun onSkipToNext() { onTrackCompleted?.invoke() }
                override fun onSkipToPrevious() {}
            })
            isActive = true
        }
    }

    fun prepareAndPlay(filePath: String, memoId: String, startPositionMs: Long = 0L) {
        release()
        val file = File(filePath)
        if (!file.exists()) return

        mediaPlayer = MediaPlayer().apply {
            setDataSource(filePath)
            prepare()
            if (startPositionMs > 0) seekTo(startPositionMs.toInt())
            start()
            setOnCompletionListener {
                onPositionSave?.invoke(memoId, 0L)
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = false,
                    currentPosition = 0,
                    currentMemoId = null
                )
                updateMediaSessionState(false)
                onTrackCompleted?.invoke()
            }
            _playbackState.value = PlaybackState(
                isPlaying = true,
                currentPosition = startPositionMs.toInt(),
                duration = this.duration,
                currentMemoId = memoId,
                playbackSpeed = _playbackState.value.playbackSpeed
            )
        }
        applySpeed(_playbackState.value.playbackSpeed)
        updateMediaSessionState(true)
    }

    /**
     * Crossfade: smoothly transitions from the current track to the next.
     * The current player's volume fades to 0 over [CROSSFADE_DURATION_MS],
     * while the new player fades from 0 to 1 simultaneously.
     */
    fun prepareAndPlayWithCrossfade(filePath: String, memoId: String) {
        val file = File(filePath)
        if (!file.exists()) {
            prepareAndPlay(filePath, memoId)
            return
        }

        val oldPlayer = mediaPlayer
        val oldMemoId = _playbackState.value.currentMemoId

        // Prepare the next player silently
        crossfadePlayer = MediaPlayer().apply {
            setDataSource(filePath)
            prepare()
            setVolume(0f, 0f)
            start()
            setOnCompletionListener {
                onPositionSave?.invoke(memoId, 0L)
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = false,
                    currentPosition = 0,
                    currentMemoId = null
                )
                updateMediaSessionState(false)
                onTrackCompleted?.invoke()
            }
        }

        val newDuration = crossfadePlayer?.duration ?: 0
        _playbackState.value = PlaybackState(
            isPlaying = true,
            currentPosition = 0,
            duration = newDuration,
            currentMemoId = memoId,
            playbackSpeed = _playbackState.value.playbackSpeed
        )
        applySpeedTo(crossfadePlayer, _playbackState.value.playbackSpeed)
        updateMediaSessionState(true)

        // Crossfade coroutine: fade old out, new in simultaneously
        scope.launch {
            val stepMs = CROSSFADE_DURATION_MS / CROSSFADE_STEPS
            for (step in 0..CROSSFADE_STEPS) {
                val progress = step.toFloat() / CROSSFADE_STEPS
                val oldVol = 1f - progress
                val newVol = progress
                oldPlayer?.setVolume(oldVol, oldVol)
                crossfadePlayer?.setVolume(newVol, newVol)
                delay(stepMs)
            }
            // Save old position and release old player
            oldMemoId?.let { id -> onPositionSave?.invoke(id, 0L) }
            oldPlayer?.stop()
            oldPlayer?.release()

            // Promote crossfade player to main
            mediaPlayer = crossfadePlayer
            crossfadePlayer = null
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                _playbackState.value = _playbackState.value.copy(isPlaying = false)
                updateMediaSessionState(false)
                _playbackState.value.currentMemoId?.let { id ->
                    onPositionSave?.invoke(id, player.currentPosition.toLong())
                }
            } else {
                player.start()
                _playbackState.value = _playbackState.value.copy(isPlaying = true)
                updateMediaSessionState(true)
            }
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
        _playbackState.value = _playbackState.value.copy(currentPosition = positionMs)
    }

    fun setSpeed(speed: Float) {
        _playbackState.value = _playbackState.value.copy(playbackSpeed = speed)
        applySpeed(speed)
    }

    private fun applySpeed(speed: Float) {
        applySpeedTo(mediaPlayer, speed)
    }

    private fun applySpeedTo(player: MediaPlayer?, speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                player?.let {
                    val params = it.playbackParams.setSpeed(speed)
                    it.playbackParams = params
                }
            } catch (_: Exception) {}
        }
    }

    private fun updateMediaSessionState(playing: Boolean) {
        val state = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_STOP or
                PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(
                if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                _playbackState.value.currentPosition.toLong(),
                _playbackState.value.playbackSpeed
            )
            .build()
        mediaSession?.setPlaybackState(state)
    }

    fun getMediaSessionToken(): MediaSession.Token? = mediaSession?.sessionToken

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0

    fun getDuration(): Int = mediaPlayer?.duration ?: 0

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false

    fun release() {
        scope.coroutineContext.cancelChildren()
        mediaPlayer?.let { player ->
            _playbackState.value.currentMemoId?.let { id ->
                onPositionSave?.invoke(id, player.currentPosition.toLong())
            }
            if (player.isPlaying) player.stop()
            player.release()
        }
        crossfadePlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
        crossfadePlayer = null
        _playbackState.value = PlaybackState()
        updateMediaSessionState(false)
    }

    fun updatePosition() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                _playbackState.value = _playbackState.value.copy(
                    currentPosition = player.currentPosition
                )
            }
        }
    }

    fun destroy() {
        release()
        scope.cancel()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
    }
}
