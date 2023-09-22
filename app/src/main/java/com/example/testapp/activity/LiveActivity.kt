package com.example.testapp.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.arashivision.sdkcamera.InstaCameraSDK
import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.arashivision.sdkcamera.camera.InstaCameraManager.CONNECT_TYPE_WIFI
import com.arashivision.sdkcamera.camera.callback.ILiveStatusListener
import com.arashivision.sdkcamera.camera.callback.IPreviewStatusListener
import com.arashivision.sdkcamera.camera.live.LiveParamsBuilder
import com.arashivision.sdkcamera.camera.preview.PreviewParamsBuilder
import com.arashivision.sdkcamera.camera.resolution.PreviewStreamResolution
import com.arashivision.sdkmedia.InstaMediaSDK
import com.arashivision.sdkmedia.player.capture.CaptureParamsBuilder
import com.arashivision.sdkmedia.player.capture.InstaCapturePlayerView
import com.arashivision.sdkmedia.player.config.InstaStabType
import com.arashivision.sdkmedia.player.listener.PlayerViewListener
import com.example.testapp.R
import com.example.testapp.databinding.ActivityLiveBinding
import com.example.testapp.dialog.WIFIConnectionDialog
import com.example.testapp.observer.ObserveCameraActivity
import com.example.testapp.utils.NetworkManager

class LiveActivity : ObserveCameraActivity(), IPreviewStatusListener, ILiveStatusListener {
    private val tag = "com.example.testapp." + this::class.simpleName
    private lateinit var binding : ActivityLiveBinding
    private lateinit var mTvLiveStatus : TextView
    private lateinit var mBtnSwitchLive: ToggleButton
    private lateinit var mCapturePlayerView: InstaCapturePlayerView
    private var mCurrentResolution: PreviewStreamResolution? = null
    private lateinit var userid : String
    private lateinit var ipaddress : String
    private lateinit var port : String
    private val requestCode = 123 // You can use any integer value
    private lateinit var wifiConnectionDialog: WIFIConnectionDialog
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        wifiConnectionDialog = WIFIConnectionDialog(this)

        val intent = intent

        userid = intent.getStringExtra("retirementHomeID")!!
        ipaddress = intent.getStringExtra("ipaddress")!!
        port = intent.getStringExtra("port")!!

        //init InstaCameraSDK for camera connection
        InstaCameraSDK.init(this.application)
        //init InstaMediaSDK for preview
        InstaMediaSDK.init(this.application)

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val ssid = wifiInfo.ssid.replace("\"", "")

        InstaCameraManager.getInstance().openCamera(InstaCameraManager.CONNECT_TYPE_USB)

        if(ssid.startsWith("X3 ")){
            InstaCameraManager.getInstance().openCamera(InstaCameraManager.CONNECT_TYPE_WIFI)
        }else{
            wifiConnectionDialog.show()
        }
        bindViews()
    }

    private fun bindViews() {
        mCapturePlayerView = binding.playerCapture
        mCapturePlayerView.setLifecycle(lifecycle)
        mBtnSwitchLive = binding.btnSwitchLive
        mTvLiveStatus = binding.tvLiveStatus

        //until preview is running the live button is disabled
        mBtnSwitchLive.isEnabled = false

        mBtnSwitchLive.setOnClickListener{
            if(mBtnSwitchLive.isChecked){
                mBtnSwitchLive.isChecked = true   //set to stop
                if (InstaCameraManager.getInstance().cameraConnectedType == CONNECT_TYPE_WIFI){
                    NetworkManager.getInstance().exchangeNetToMobile(this.applicationContext)
                }
                checkToStartLive()
            }else{
                mBtnSwitchLive.isChecked = false  //set to resume
                stopLive()
            }
        }
    }

    private fun restartPreview() {
        val builder = PreviewParamsBuilder()
            .setStreamResolution(mCurrentResolution)
            .setPreviewType(InstaCameraManager.PREVIEW_TYPE_LIVE)
            .setAudioEnabled(true)
        InstaCameraManager.getInstance().closePreviewStream()
        InstaCameraManager.getInstance().startPreviewStream(builder)
    }

    private fun checkToStartLive(): Boolean {
        //todo
        val rtmp = "rtmp://$ipaddress/$port"
        val width = mCurrentResolution!!.width
        val height = mCurrentResolution!!.height
        val fps = mCurrentResolution!!.fps
        mCapturePlayerView.setLiveType(InstaCapturePlayerView.LIVE_TYPE_PANORAMA)
        val builder = LiveParamsBuilder()
            .setRtmp(rtmp)
            .setWidth(width)
            .setHeight(height)
            .setFps(fps)
            //.setBitrate(bitrate.toInt() * 1024 * 1024)
            .setBitrate(480000)
            .setPanorama(true) // 设置网络ID即可在使用WIFI连接相机时使用4G网络推流
            // set NetId to use 4G to push live streaming when connecting camera by WIFI
            .setNetId(NetworkManager.getInstance().getMobileNetId())
        InstaCameraManager.getInstance().startLive(builder, this)
        return true
    }

    private fun stopLive() {
        InstaCameraManager.getInstance().stopLive()
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            // Auto close preview after page loses focus
            InstaCameraManager.getInstance().stopLive()
            InstaCameraManager.getInstance().closePreviewStream()
            InstaCameraManager.getInstance().setPreviewStatusChangedListener(null)
            mCapturePlayerView.destroy()
            NetworkManager.getInstance().clearBindProcess(this.applicationContext)
        }
    }

    override fun onOpened() {
        // Preview stream is on and can be played
        InstaCameraManager.getInstance().setStreamEncode()
        mCapturePlayerView.setPlayerViewListener(object : PlayerViewListener {
            override fun onLoadingFinish() {
                mBtnSwitchLive.isEnabled = true
                mBtnSwitchLive.isChecked = true //set to Stop
                InstaCameraManager.getInstance().setPipeline(mCapturePlayerView.pipeline)
                if (InstaCameraManager.getInstance().cameraConnectedType == CONNECT_TYPE_WIFI){
                    NetworkManager.getInstance().exchangeNetToMobile(applicationContext)
                }
                checkToStartLive()
            }

            override fun onReleaseCameraPipeline() {
                InstaCameraManager.getInstance().setPipeline(null)
            }
        })
        mCapturePlayerView.prepare(createParams())
        mCapturePlayerView.play()
        mCapturePlayerView.keepScreenOn = true
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
            .setStabType(InstaStabType.STAB_TYPE_AUTO)
            .setStabEnabled(true)
            .setLive(true)
            .setResolutionParams(
                mCurrentResolution!!.width,
                mCurrentResolution!!.height,
                mCurrentResolution!!.fps
            )
    }
    override fun onIdle() {
        // Preview Stopped
        mBtnSwitchLive.isEnabled = false
        mCapturePlayerView.destroy()
        mCapturePlayerView.keepScreenOn = false
    }

    override fun onLivePushStarted() {
        mTvLiveStatus.setText(R.string.live_push_started)
    }

    override fun onLivePushFinished() {
        mBtnSwitchLive.isChecked = false    //resume
        mTvLiveStatus.setText(R.string.live_push_finished)
    }

    @SuppressLint("SetTextI18n")
    override fun onLivePushError(error : Int, desc : String) {
        mBtnSwitchLive.isChecked = false    //resume
        mTvLiveStatus.text = getString(R.string.live_push_error) + " ($error : $desc)"
        Log.d(tag, getString(R.string.live_push_error) + " ($error : $desc)")
    }

    override fun onLiveFpsUpdate(fps: Int) {
        mTvLiveStatus.text = getString(R.string.live_fps_update, fps)
    }

    override fun onCameraStatusChanged(enabled: Boolean) {
        super.onCameraStatusChanged(enabled)
        if (!enabled) {
            mBtnSwitchLive.isChecked = false    //resume
            mBtnSwitchLive.isEnabled = false
        }else{
            if (wifiConnectionDialog.isShowing){
                wifiConnectionDialog.dismiss()
            }
            val resolutionList = InstaCameraManager.getInstance().getSupportedPreviewStreamResolution(InstaCameraManager.PREVIEW_TYPE_LIVE)
            Log.d(tag, resolutionList.toString())
            mCurrentResolution = resolutionList[resolutionList.size-1]
            Log.d(tag, mCurrentResolution.toString())
            InstaCameraManager.getInstance().setPreviewStatusChangedListener(this)
            restartPreview()
        }
    }

    override fun onCameraConnectError(errorCode: Int) {
        super.onCameraConnectError(errorCode)
        Toast.makeText(
            this,
            "Communication error:$errorCode",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onCameraSDCardStateChanged(enabled: Boolean) {
        super.onCameraSDCardStateChanged(enabled)
        if (enabled) {
            Toast.makeText(this, "SD card enabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "SD card disabled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasPermissions(): Boolean {
        val internetPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.INTERNET
        )
        val accessNetworkStatePermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_NETWORK_STATE
        )
        val changeNetworkStatePermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CHANGE_NETWORK_STATE
        )
        val accessFineLocationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        )
        val accessCoarseLocationPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val changeWifiStatePermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CHANGE_WIFI_STATE
        )
        val accessWifiStatePermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_WIFI_STATE
        )

        // Return true only if both permissions are granted
        return internetPermission == PackageManager.PERMISSION_GRANTED &&
                accessNetworkStatePermission == PackageManager.PERMISSION_GRANTED &&
                changeNetworkStatePermission == PackageManager.PERMISSION_GRANTED &&
                accessFineLocationPermission == PackageManager.PERMISSION_GRANTED &&
                accessCoarseLocationPermission == PackageManager.PERMISSION_GRANTED &&
                changeWifiStatePermission == PackageManager.PERMISSION_GRANTED &&
                accessWifiStatePermission == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.CHANGE_NETWORK_STATE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_WIFI_STATE
            ),
            requestCode
        )
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == this.requestCode) {
            for (i in permissions.indices) {
                val permission = permissions[i]
                val grantResult = grantResults[i]

                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted
                    Log.d(tag, "Permission granted: $permission")

                } else {
                    // Permission denied
                    Log.d(tag, "Permission denied: $permission")
                    // Handle the denied permission (e.g., show a message to the user)

                }
            }
        }
        if(!grantResults.contains(PackageManager.PERMISSION_DENIED)){
            //permission granted
        }else{
            //permission denied
            Toast.makeText(this, "Permission is required" , Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val ssid = wifiInfo.ssid.replace("\"", "")

        if(ssid.startsWith("X3")){
            InstaCameraManager.getInstance().openCamera(InstaCameraManager.CONNECT_TYPE_WIFI)
            if (wifiConnectionDialog.isShowing){
                wifiConnectionDialog.dismiss()
            }
        }
    }
}
