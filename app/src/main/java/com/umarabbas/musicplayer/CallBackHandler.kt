package com.umarabbas.musicplayer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import androidx.annotation.RequiresApi

class CallBackHandler {
    companion object {
        var onChange: ((Song) -> Unit)? = null
        var seekListener: ((Long) -> Unit)? = null
        val NOTIFICATION_CHANNEL_ID = "my_downloader_555"
        val NOTIFICATION_CHANNEL_NAME = "VideoDownloader"

        fun informSeekbar(position: Long) {
            seekListener?.invoke(position)
        }

        @RequiresApi(Build.VERSION_CODES.O)
        fun provideNotificationChannel(): NotificationChannel {
            return NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                enableLights(true)
                lightColor = Color.YELLOW
                setSound(null,null)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes
                        .Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
                description = "This service is used to show download notifications"
            }
        }
    }
}