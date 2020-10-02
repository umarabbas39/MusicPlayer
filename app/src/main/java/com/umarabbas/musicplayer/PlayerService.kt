package com.umarabbas.musicplayer

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.session.MediaButtonReceiver
import java.io.File
import java.util.*

class PlayerService : MediaBrowserServiceCompat() {

    private val MY_MEDIA_ROOT_ID = "media_root_id"
    private val MY_EMPTY_MEDIA_ROOT_ID = "empty_root_id"
    private val LOG = "player_session"
    private val id = 98
    private var currentSong = 0
    private var mMediaSession: MediaSessionCompat? = null
    private lateinit var stateBuilder: PlaybackStateCompat.Builder
    private var mediaPlayer: MediaPlayer? = null
    private var timer: Timer? = null
    private var stopped = false
    private var songList = mutableListOf<Song>()

    override fun onCreate() {
        super.onCreate()
        songList = SongProvider.getAllDeviceSongs(baseContext)
        timer = Timer()
        mMediaSession = MediaSessionCompat(baseContext, LOG).apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                        or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY
                            or PlaybackStateCompat.ACTION_PLAY_PAUSE
                            or PlaybackStateCompat.ACTION_PAUSE
                            or PlaybackStateCompat.ACTION_SEEK_TO
                            or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                            or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
            setPlaybackState(stateBuilder.build())
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlayFromUri(uri: Uri?, extras: Bundle?) {
                    super.onPlayFromUri(uri, extras)
                    restartServiceIfNeeded()
                    mMediaSession?.isActive = true
                    mMediaSession?.setPlaybackState(
                        PlaybackStateCompat.Builder()
                            .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f).build()
                    )
                    Log.d("shani-main", "this is called from play uri")
                    if (mediaPlayer == null) {
                        initializePlayer(uri)
                    } else {
                        mediaPlayer?.pause()
                        mediaPlayer?.reset()
                        mediaPlayer?.apply {
                            setDataSource(applicationContext, uri!!)
                            prepare()
                            start()
                        }
                    }
                    songList.forEachIndexed { index, song ->
                        if (Uri.fromFile(File(song.path)) == uri) {
                            currentSong = index
                            return@forEachIndexed
                        }
                    }
                    startTimer()
                    updateMetadata()
                    buildAndDeployNot()
                }

                override fun onPlay() {
                    super.onPlay()
                    restartServiceIfNeeded()
                    if (mediaPlayer == null) {
                        initializePlayer(null)
                    }
                    mMediaSession?.isActive = true
                    changeState(
                        mediaPlayer?.currentPosition?.toLong()!!,
                        PlaybackStateCompat.STATE_PLAYING
                    )
                    mediaPlayer?.start()
                    startTimer()
                    buildAndDeployNot()
                }

                override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                    var s =
                        mediaButtonEvent?.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)?.keyCode
                    when (s) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            if (mMediaSession?.controller?.playbackState?.state == PlaybackStateCompat.STATE_PLAYING) {
                                mMediaSession?.controller?.transportControls?.pause()
                            } else {
                                mMediaSession?.controller?.transportControls?.play()
                            }
                        }
                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            mMediaSession?.controller?.transportControls?.skipToNext()
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            mMediaSession?.controller?.transportControls?.skipToPrevious()
                        }
                        KeyEvent.KEYCODE_MEDIA_STOP -> {
                            mMediaSession?.controller?.transportControls?.stop()
                        }
                    }
                    return true
                }

                override fun onSeekTo(pos: Long) {
                    super.onSeekTo(pos)
                    restartServiceIfNeeded()
                    mediaPlayer?.seekTo(pos.toInt())
                    changeState(
                        mediaPlayer?.currentPosition?.toLong()!!,
                        PlaybackStateCompat.STATE_FAST_FORWARDING
                    )
                    changeState(
                        mediaPlayer?.currentPosition?.toLong()!!,
                        PlaybackStateCompat.STATE_PLAYING
                    )
                }

                override fun onPause() {
                    super.onPause()
                    changeState(
                        mediaPlayer?.currentPosition?.toLong()!!,
                        PlaybackStateCompat.STATE_PAUSED
                    )
                    timer?.purge()
                    mediaPlayer?.pause()
                    buildAndDeployNot()
                    stopServiceIf()
                }

                override fun onStop() {
                    super.onStop()
                    timer?.purge()
                    mediaPlayer?.stop()
                    mediaPlayer?.release()
                    mediaPlayer = null
                    stopServiceIf()
                }

                override fun onSkipToNext() {
                    super.onSkipToNext()
                    restartServiceIfNeeded()
                    playNextSong()
                    startTimer()
                    updateMetadata()
                    buildAndDeployNot()
                }

                override fun onSkipToPrevious() {
                    super.onSkipToPrevious()
                    restartServiceIfNeeded()
                    playPreviousSong()
                    startTimer()
                    updateMetadata()
                    buildAndDeployNot()
                }
            })
            setSessionToken(sessionToken)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                CallBackHandler.provideNotificationChannel()
            )
        }
        startForeground(id, buildNotification())
    }

    private fun initializePlayer(uri: Uri?) {
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setDataSource(
                applicationContext,
                uri ?: Uri.fromFile(File(songList[currentPosition].path))
            )
            setOnCompletionListener {
                playNextSong()
                updateMetadata()
                buildAndDeployNot()
            }
            prepare()
            start()
        }
    }

    private fun stopServiceIf() {
        if (!stopped) {
            stopped = true
            stopForeground(false)
        }
    }

    private fun restartServiceIfNeeded() {
        if (stopped) {
            startForeground(id, buildNotification())
            stopped = false
        }
    }

    private fun getPausePlay(): Int {
        return if (mMediaSession?.controller?.playbackState?.state == PlaybackStateCompat.STATE_PLAYING) {
            R.drawable.ic_baseline_pause_24
        } else {
            R.drawable.ic_baseline_play_arrow_24
        }
    }

    private fun buildAndDeployNot() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
            id,
            buildNotification()
        )
    }

    private fun buildNotification(): Notification {
        val controller = mMediaSession?.controller
        val mediaMetadata = controller?.metadata
        val description = mediaMetadata?.description
        val builder =
            NotificationCompat.Builder(this, CallBackHandler.NOTIFICATION_CHANNEL_ID).apply {
                setContentTitle(description?.title)
                setContentText(description?.description)
                setSubText(description?.subtitle)
                setLargeIcon(
                    drawableToBitmap(
                        ContextCompat.getDrawable(
                            baseContext,
                            R.drawable.ic_launcher_foreground
                        )!!
                    )
                )
                setSound(null)
                setOnlyAlertOnce(true)
                setContentIntent(controller?.sessionActivity)
                setDeleteIntent(
                    MediaButtonReceiver.buildMediaButtonPendingIntent(
                        baseContext,
                        PlaybackStateCompat.ACTION_STOP
                    )
                )
                setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                setSmallIcon(R.drawable.ic_launcher_foreground)
                addAction(
                    NotificationCompat.Action(
                        R.drawable.ic_baseline_skip_previous_24,
                        "pause",
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                            baseContext,
                            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                        )
                    )
                )
                addAction(
                    NotificationCompat.Action(
                        getPausePlay(),
                        "pause",
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                            baseContext,
                            PlaybackStateCompat.ACTION_PLAY_PAUSE
                        )
                    )
                )
                addAction(
                    NotificationCompat.Action(
                        R.drawable.ic_baseline_skip_next_24,
                        "pause",
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                            baseContext,
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                        )
                    )
                )
                setStyle(
                    androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mMediaSession?.sessionToken)
                        .setShowActionsInCompactView(0, 1, 2)
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(
                            MediaButtonReceiver.buildMediaButtonPendingIntent(
                                baseContext,
                                PlaybackStateCompat.ACTION_STOP
                            )
                        )
                )
            }
        return builder.build()
    }

    fun drawableToBitmap(drawable: Drawable): Bitmap? {
        var bitmap: Bitmap? = null
        if (drawable is BitmapDrawable) {
            val bitmapDrawable: BitmapDrawable = drawable
            if (bitmapDrawable.bitmap != null) {
                return bitmapDrawable.bitmap
            }
        }
        bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
            Bitmap.createBitmap(
                1,
                1,
                Bitmap.Config.ARGB_8888
            ) // Single color bitmap will be created of 1x1 pixel
        } else {
            Bitmap.createBitmap(
                drawable.intrinsicWidth,
                drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
        }
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mMediaSession, intent)
        return START_NOT_STICKY
    }

    private fun updateMetadata() {
        var temp = songList.get(currentSong)
        var s = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, temp.title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, temp.artistName)
            .apply {
                mediaPlayer?.let {
                    putLong(MediaMetadataCompat.METADATA_KEY_DURATION, it.duration.toLong())
                }
            }
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, temp.albumName)
            .build()
        mMediaSession?.setMetadata(s)
    }

    private fun playNextSong() {
        ++currentSong
        if (currentSong >= songList.size)
            currentSong = 0
        mediaPlayer?.pause()
        mediaPlayer?.reset()
        mediaPlayer?.apply {
            setDataSource(
                applicationContext,
                Uri.fromFile(File(songList.get(currentSong).path))
            )
            prepare()
            start()
        }
        CallBackHandler.onChange?.invoke(songList[currentSong])
        changeState(0, PlaybackStateCompat.STATE_PLAYING)
    }

    private fun changeState(position: Long, state: Int) {
        mMediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(
                    state,
                    position,
                    1.0f
                ).build()
        )
    }

    private fun playPreviousSong() {
        --currentSong
        if (currentSong < 0)
            currentSong = songList.size - 1
        mediaPlayer?.pause()
        mediaPlayer?.reset()
        mediaPlayer?.apply {
            setDataSource(
                applicationContext,
                Uri.fromFile(File(songList.get(currentSong).path))
            )
            prepare()
            start()
        }
        CallBackHandler.onChange?.invoke(songList[currentSong])
        changeState(0, PlaybackStateCompat.STATE_PLAYING)
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        return BrowserRoot(MY_MEDIA_ROOT_ID, null)
    }

    private fun startTimer() {
        timer?.purge()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                mediaPlayer?.currentPosition?.let {
                    CallBackHandler.informSeekbar(it.toLong())
                }
            }
        }, 0, 1000)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        if (MY_EMPTY_MEDIA_ROOT_ID == parentId) {
            result.sendResult(null)
            return
        }
        val mediaItems = emptyList<MediaBrowserCompat.MediaItem>()
        if (MY_MEDIA_ROOT_ID == parentId) {

        } else {

        }
        result.sendResult(mediaItems.toMutableList())
    }
}