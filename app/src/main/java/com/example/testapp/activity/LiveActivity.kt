package com.example.testapp.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
import com.arashivision.sdkcamera.camera.callback.ILiveStatusListener
import com.arashivision.sdkcamera.camera.callback.IPreviewStatusListener
import com.arashivision.sdkcamera.camera.live.LiveParamsBuilder
import com.arashivision.sdkcamera.camera.preview.PreviewParamsBuilder
import com.arashivision.sdkcamera.camera.preview.VideoData
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices


class LiveActivity : ObserveCameraActivity(), IPreviewStatusListener, ILiveStatusListener {
    private val tag = "com.example.testapp." + this::class.simpleName
    private lateinit var binding : ActivityLiveBinding
    private lateinit var mTvLiveStatus : TextView
    private lateinit var mTvVelocity : TextView
    private lateinit var mBtnSwitchLive: ToggleButton
    private lateinit var mCapturePlayerView: InstaCapturePlayerView
    private var mCurrentResolution: PreviewStreamResolution? = null
    private lateinit var userid : String
    private lateinit var ipaddress : String
    private lateinit var port : String
    private val requestCode = 123 // You can use any integer value
    private lateinit var wifiConnectionDialog: WIFIConnectionDialog
    private lateinit var ssid : String
    private lateinit var fusedLocationClient: FusedLocationProviderClient


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

        // Initialize the FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)


        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        ssid = wifiInfo.ssid.replace("\"", "")

        if(!hasPermissions()){
            requestPermissions()
        }else{
            if(ssid.startsWith("X3 ")){
                InstaCameraManager.getInstance().openCamera(InstaCameraManager.CONNECT_TYPE_WIFI)
            }else{
                wifiConnectionDialog.show()
            }
            bindViews()
        }
    }

    private fun bindViews() {
        mCapturePlayerView = binding.playerCapture
        mCapturePlayerView.setLifecycle(lifecycle)
        mBtnSwitchLive = binding.btnSwitchLive
        mTvLiveStatus = binding.tvLiveStatus
        mTvVelocity = binding.velocity

        //until preview is running the live button is disabled
        mBtnSwitchLive.isEnabled = false

        mBtnSwitchLive.setOnClickListener{
            if(mBtnSwitchLive.isChecked){
                mBtnSwitchLive.isChecked = true   //set to stop

                //use mobile network for streaming
                if (NetworkManager.getInstance().isBindingMobileNetwork()){
                    checkToStartLive()
                }else{
                    exchangeNetToMobileInsta(this.applicationContext)
                }
            }else{
                mBtnSwitchLive.isChecked = false  //set to resume
                stopLive()
                stopLocationUpdates()
                mTvLiveStatus.setText(R.string.live_push_finished)
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
        val rtmp = "rtmp://develop.ewlab.di.unimi.it:3027/serverRTMP/mystream"      //"rtmp://$ipaddress:$port/serverRTMP/mystream" //rtmp://develop.ewlab.di.unimi.it:3027/serverRTMP/mystream
        Log.d(tag, rtmp)
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
            .setBitrate(4800000)
            .setPanorama(true)
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
                exchangeNetToMobileInsta(applicationContext)
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
            .setGestureEnabled(false)
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
        startLocationUpdates()
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
            mCurrentResolution = resolutionList[resolutionList.size-2]
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
            if(ssid.startsWith("X3 ")){
                InstaCameraManager.getInstance().openCamera(InstaCameraManager.CONNECT_TYPE_WIFI)
            }else{
                wifiConnectionDialog.show()
            }
            bindViews()
        }else{
            //permission denied
            Toast.makeText(this, "Permission are required" , Toast.LENGTH_SHORT).show()
        }
    }

    // Bind Mobile Network
    private fun exchangeNetToMobileInsta(context: Context) {
        if (NetworkManager.getInstance().isBindingMobileNetwork()) {
            return
        }
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networks = connManager.allNetworks
        for (network in networks) {
            val networkInfo = connManager.getNetworkInfo(network)
            if (networkInfo != null && networkInfo.type == ConnectivityManager.TYPE_WIFI) {
                // Need to set network Id of current wifi to camera
                InstaCameraManager.getInstance().setNetIdToCamera(NetworkManager.getInstance().getNetworkId(network!!))
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        NetworkManager.getInstance().mNetworkCallback = object : ConnectivityManager.NetworkCallback() {
            @SuppressLint("SuspiciousIndentation")
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                val bindSuccessful: Boolean =
                    connManager.bindProcessToNetwork(null)
                connManager.bindProcessToNetwork(network)
                // Record the bound mobile network ID
                NetworkManager.getInstance().mMobileNetId = NetworkManager.getInstance().getNetworkId(network)
                if (bindSuccessful) {
                    Log.d(tag, context.getString(R.string.live_toast_bind_mobile_network_successful))
                    //binding is successful so check to start the live
                    checkToStartLive()

                } else {
                    Log.d(tag, context.getString(R.string.live_toast_bind_mobile_network_failed))

                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                // The mobile network is suddenly unavailable, need to temporarily unbind and wait for the network to recover again
                connManager.bindProcessToNetwork(null)
                Log.d(tag, context.getString(R.string.live_toast_unbind_mobile_network_when_lost))
            }
        }
        connManager.requestNetwork(request, NetworkManager.getInstance().mNetworkCallback as ConnectivityManager.NetworkCallback)
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

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        // Create location request with desired accuracy and update interval
        val locationRequest = LocationRequest.create()
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setInterval(5000) // Update interval in milliseconds

        // Register for location updates
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                // Use location.speed to get the current velocity in meters per second
                val velocity = location.speed

                mTvVelocity.text = velocity.toString()

            }
        }
    }

    override fun onVideoData(videoData: VideoData) {
        videoData.data
    }
}
