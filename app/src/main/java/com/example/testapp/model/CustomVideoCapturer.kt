package com.example.testapp.model

import android.content.Context
import org.webrtc.*

class CustomVideoCapturer() : VideoCapturer {

    private var surfaceTextureHelper : SurfaceTextureHelper? = null
    private var capturerObserver: CapturerObserver? = null
    private var context: Context? = null

    private var frameWidth: Int = 0
    private var frameHeight: Int = 0


    fun init (width: Int, height: Int){
        frameWidth = width
        frameHeight = height
    }


    override fun initialize(surfaceTextureHelper: SurfaceTextureHelper?, context: Context?, capturerObserver: CapturerObserver?) {
        this.surfaceTextureHelper = surfaceTextureHelper
        this.context = context
        this.capturerObserver = capturerObserver
    }
    fun addVideoData(timestamp: Long, rawVideoData: ByteArray) {

        // Your NV21 video frame data as a ByteArray
        val nv21Data: ByteArray = rawVideoData

        // Video frame dimensions (width and height)
        val width: Int = frameWidth
        val height: Int = frameHeight

        // Create an NV21Buffer from the nv21Data
        val buffer = NV21Buffer(nv21Data, width, height, null)

        // Create the VideoFrame
        val videoFrame = VideoFrame(buffer,0, timestamp)

        capturerObserver?.onFrameCaptured(videoFrame)

    }
    override fun startCapture(width: Int, height: Int, framerate: Int) {}
    override fun stopCapture() {}
    override fun changeCaptureFormat(width: Int, height: Int, framerate: Int) {
        frameWidth = width
        frameHeight = height
    }
    override fun isCapturing(): Boolean { return false }
    override fun dispose() {}
    override fun isScreencast(): Boolean { return false }

}
