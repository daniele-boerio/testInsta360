package com.example.testapp.activity

import android.os.Bundle
import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.arashivision.sdkcamera.camera.callback.ILiveStatusListener
import com.arashivision.sdkcamera.camera.callback.IPreviewStatusListener
import com.arashivision.sdkmedia.player.capture.CaptureParamsBuilder
import com.arashivision.sdkmedia.player.listener.PlayerViewListener
import com.example.testapp.databinding.ActivityLiveBinding


class LiveActivity : ObserveCameraActivity() , IPreviewStatusListener, ILiveStatusListener {

    private lateinit var binding : ActivityLiveBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        binding.playerCapture.setLifecycle(lifecycle)
        //InstaCameraManager.getInstance().startPreviewStream()
    }

    override fun onOpened() {
        InstaCameraManager.getInstance().setStreamEncode()
        binding.playerCapture.setPlayerViewListener(object : PlayerViewListener {
            override fun onLoadingFinish() {
                // Must do this
                val pipeline: Any = binding.playerCapture.getPipeline()
                InstaCameraManager.getInstance().setPipeline(pipeline)
            }

            override fun onLoadingStatusChanged(isLoading: Boolean) {}
            //fun onFail(desc: String?) {}
        })
        binding.playerCapture.prepare(createParams())
        binding.playerCapture.play()
    }

    private fun createParams(): CaptureParamsBuilder? {
        return CaptureParamsBuilder()
            .setCameraType(InstaCameraManager.getInstance().cameraType)
            .setMediaOffset(InstaCameraManager.getInstance().mediaOffset)
            .setMediaOffsetV2(InstaCameraManager.getInstance().mediaOffsetV2)
            .setMediaOffsetV3(InstaCameraManager.getInstance().mediaOffsetV3)
            .setCameraSelfie(InstaCameraManager.getInstance().isCameraSelfie)
            .setGyroTimeStamp(InstaCameraManager.getInstance().gyroTimeStamp)
            .setBatteryType(InstaCameraManager.getInstance().batteryType)
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            // Auto close preview after page loses focus
            InstaCameraManager.getInstance().setPreviewStatusChangedListener(null)
            InstaCameraManager.getInstance().closePreviewStream()
            binding.playerCapture.destroy()
        }
    }

    override fun onIdle() {
        // Preview Stopped
        binding.playerCapture.destroy()
    }
}