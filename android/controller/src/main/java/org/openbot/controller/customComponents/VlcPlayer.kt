/*
 * Developed for the OpenBot project (https://openbot.org) by:
 *
 * Ivo Zivkov
 * izivkov@gmail.com
 *
 * Date: 2020-12-27, 10:56 p.m.
 */

package org.openbot.controller.customComponents

import android.content.Context
import android.net.Uri
import android.util.Log
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.*

data class VlcPlayer(
        val context: Context, val layout: VLCVideoLayout
) : IVideoPlayer {
    private val TAG: String = "VlcView"

    private var mLibVLC: LibVLC? = null
    private var mMediaPlayer: MediaPlayer? = null
    private var mVideoLayout: VLCVideoLayout? = null

    init {
    }

    override fun init() {
        val args = ArrayList<String>()
        // args.add("-vvv")
        args.add("--file-caching=2000");

        mLibVLC = LibVLC(this.context, args)
        mMediaPlayer = MediaPlayer(mLibVLC)

        mVideoLayout = this.layout
        mMediaPlayer!!.detachViews()
        mMediaPlayer!!.attachViews(mVideoLayout!!, null, true, true)
    }

    override fun start(url: String) {
        Log.i(null, "Stated the player..., playState: " + mMediaPlayer!!.playerState)

        val media = Media(mLibVLC, Uri.parse(url))
        media.setHWDecoderEnabled(true, false)
        media.addOption(":network-caching=0")
        media.addOption(":clock-jitter=0")
        media.addOption(":clock-synchro=0")
        Log.i(TAG, "media.state: " + media.state)

        mMediaPlayer!!.media = media
        media.release()

        mMediaPlayer!!.play()
    }

    override fun stop() {
        mMediaPlayer!!.stop()
    }
}
