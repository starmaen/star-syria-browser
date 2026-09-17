package com.starsyria.browser

import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** مشغل فيديو/ميديا مدمج، يُستدعى عند تنزيل أو فتح ملف فيديو داخل المتصفح. */
class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val playerView = PlayerView(this)
        setContentView(playerView)

        val videoUrl = intent.getStringExtra("video_url") ?: return
        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        player.setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
        player.prepare()
        player.play()
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}
