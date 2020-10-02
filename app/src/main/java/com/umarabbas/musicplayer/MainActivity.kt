package com.umarabbas.musicplayer

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.umarabbas.musicplayer.databinding.ActivityMainBinding
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.lang.Exception
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    var listOfSongs = mutableListOf<Song>()
    lateinit var adapter: RVAdapter
    lateinit var controllerCallback: MediaControllerCompat.Callback
    private lateinit var mediaBrowser: MediaBrowserCompat
    private var currentSong: Song? = null
    private var shouldPlay = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        requestPermission()
        adapter = RVAdapter(this) {
            if (!mediaBrowser.isConnected) {
                startService(Intent(this@MainActivity,PlayerService::class.java))
                mediaBrowser.connect()
                currentSong = it
                shouldPlay = true
            } else {
                playSong(it)
            }
        }
        binding.recyclerView.adapter = adapter
        CallBackHandler.onChange = {
            setSong(it)
        }
        CallBackHandler.seekListener = {
            val currentTime = formatTime(it)
            runOnUiThread {
                binding.seekBar.progress = it.toInt()
                binding.currentPosition.text = currentTime
            }
        }
        binding.buttonNext.setOnClickListener {
            mediaController.transportControls.skipToNext()
        }
        binding.buttonPrevious.setOnClickListener {
            mediaController.transportControls.skipToPrevious()
        }
        binding.seekBar.isEnabled = false
        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {}

            override fun onStartTrackingTouch(p0: SeekBar?) {}

            override fun onStopTrackingTouch(p0: SeekBar?) {
                p0?.let {
                    mediaController.transportControls.seekTo(it.progress.toLong())
                    binding.currentPosition.text = formatTime(it.progress.toLong())
                }
            }

        })
        controllerCallback = object : MediaControllerCompat.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
                super.onMetadataChanged(metadata)
                Log.d("shani-activity", "this is onMetaDataChanged called from media controller")
            }

            override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                super.onPlaybackStateChanged(state)
                if (mediaController.playbackState?.state == PlaybackStateCompat.STATE_PLAYING) {
                    runOnUiThread { binding.buttonPlayPause.setImageResource(R.drawable.ic_baseline_pause_24) }
                } else {
                    runOnUiThread { binding.buttonPlayPause.setImageResource(R.drawable.ic_baseline_play_arrow_24) }
                }
                Log.d("shani-activity", "this is onPlayBackChanged called from media controller")
            }
        }
        mediaBrowser = MediaBrowserCompat(
            this,
            ComponentName(this, PlayerService::class.java),
            object : MediaBrowserCompat.ConnectionCallback() {
                override fun onConnected() {
                    super.onConnected()
                    mediaBrowser.sessionToken.also {
                        Log.d("shani-activity", "getting the session from mediaBrowser")
                        val mediaController = MediaControllerCompat(this@MainActivity, it)
                        MediaControllerCompat.setMediaController(this@MainActivity, mediaController)
                        initViews()
                        if (shouldPlay) {
                            currentSong?.let { playSong(it) }
                            shouldPlay = false
                        }
                    }
                }
            },
            null
        )
    }

    fun initViews() {
        val mediaController = MediaControllerCompat.getMediaController(this)
        mediaController?.registerCallback(controllerCallback)
        binding.buttonPlayPause.setOnClickListener {
            if (mediaController.playbackState.state == PlaybackStateCompat.STATE_PLAYING) {
                mediaController.transportControls?.pause()
            } else {
                mediaController.transportControls?.play()
            }
        }
    }

//    override fun onStart() {
//        super.onStart()
//        if (!mediaBrowser.isConnected)
//            mediaBrowser.connect()
//    }

    private fun requestPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ), 1
            )
        } else {
            getSongs()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_DENIED) {
            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            getSongs()
        }
    }

    public override fun onStop() {
        super.onStop()
        // (see "stay in sync with the MediaSession")
        MediaControllerCompat.getMediaController(this)?.unregisterCallback(controllerCallback)
        mediaBrowser.disconnect()
    }


    override fun onResume() {
        super.onResume()
        volumeControlStream = AudioManager.STREAM_MUSIC
    }

    fun getSongs() {
        GlobalScope.launch {
            listOfSongs = SongProvider.getAllDeviceSongs(this@MainActivity)
            runOnUiThread {
                adapter.setSongs(listOfSongs)
            }
        }
    }

    fun playSong(song: Song) {
        binding.songTitle.text = song.title
        binding.maxDuration.text = formatTime(song.duration.toLong())
        binding.buttonPlayPause.setImageResource(R.drawable.ic_baseline_pause_24)
        binding.seekBar.max = song.duration
        binding.seekBar.isEnabled = true
        Log.d("shani-main", "this is from playsong")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            mediaController?.transportControls?.playFromUri(Uri.fromFile(File(song.path)), null)
        }
    }

    fun setSong(song: Song) {
        runOnUiThread {
            binding.songTitle.text = song.title
            binding.maxDuration.text = formatTime(song.duration.toLong())
            binding.buttonPlayPause.setImageResource(R.drawable.ic_baseline_pause_24)
            binding.seekBar.max = song.duration
            binding.seekBar.isEnabled = true
        }
    }

    fun formatTime(millis: Long): String {
        return String.format(
            "%02d:%02d:%02d",
            TimeUnit.MILLISECONDS.toHours(millis),
            TimeUnit.MILLISECONDS.toMinutes(millis) -
                    TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(millis)), // The change is in this line
            TimeUnit.MILLISECONDS.toSeconds(millis) -
                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
        )
    }
}