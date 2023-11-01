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
import com.arashivision.sdkcamera.camera.callback.IPreviewStatusListener
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
import com.example.testapp.model.CustomVideoCapturer
import com.example.testapp.observer.ObserveCameraActivity
import com.example.testapp.observer.SimpleSdpObserver
import com.example.testapp.utils.NetworkManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.net.URISyntaxException


class LiveActivity : ObserveCameraActivity(), IPreviewStatusListener {
    private val tag = "com.example.testapp." + this::class.simpleName

    private val usb = false //test connection with usb

    private val urlSignalingServer = "https://develop.ewlab.di.unimi.it/"

    private val urlStunServer = "stun:stun.l.google.com:19302"

    private val authCode = "tokenDiAuth"    //todo
    private val peerID = android.os.Build.MODEL //todo
    private val room = "STANZA" //todo

    private val VIDEO_TRACK_ID = "ARDAMSv0"
    private val AUDIO_TRACK_ID = "ARDAMSa0"

    private lateinit var mTvLiveStatus: TextView
    private lateinit var mTvVelocity: TextView
    private lateinit var mBtnSwitchLive: ToggleButton
    private lateinit var mCapturePlayerView: InstaCapturePlayerView

    private var mCurrentResolution: PreviewStreamResolution? = null

    private lateinit var userid: String
    private lateinit var ipaddress: String
    private lateinit var port: String

    private lateinit var wifiConnectionDialog: WIFIConnectionDialog
    private lateinit var ssid: String
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val requestCode = 123 // You can use any integer value

    private lateinit var binding: ActivityLiveBinding

    //webrtc variables
    private lateinit var socket: Socket
    private lateinit var options : IO.Options

    private var isInitiator = false
    private var isChannelReady : Boolean = false
    private var isStarted : Boolean = false

    private lateinit var audioConstraints: MediaConstraints
    private lateinit var videoSource: VideoSource
    private lateinit var audioSource: AudioSource
    private lateinit var localAudioTrack: AudioTrack
    private lateinit var surfaceTextureHelper: SurfaceTextureHelper

    private lateinit var peerConnection: PeerConnection
    private lateinit var rootEglBase: EglBase
    private lateinit var factory: PeerConnectionFactory
    private lateinit var videoTrackFromCamera360: VideoTrack

    private lateinit var customVideoCapturer : CustomVideoCapturer



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

        if (!hasPermissions()) {
            requestPermissions()
        } else {
            if (usb) {
                InstaCameraManager.getInstance().openCamera(InstaCameraManager.CONNECT_TYPE_USB)
            } else if (ssid.startsWith("X3 ")) {
                InstaCameraManager.getInstance().openCamera(InstaCameraManager.CONNECT_TYPE_WIFI)
            } else {
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

        mBtnSwitchLive.setOnClickListener {
            if (mBtnSwitchLive.isChecked) {
                mBtnSwitchLive.isChecked = true   //set to stop

                //use mobile network for streaming
                if (NetworkManager.getInstance().isBindingMobileNetwork()) {
                    checkToStartLive()
                } else {
                    exchangeNetToMobileInsta(this.applicationContext)
                }
            } else {
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
            .setPreviewType(InstaCameraManager.PREVIEW_TYPE_NORMAL)
            .setAudioEnabled(true)
        InstaCameraManager.getInstance().closePreviewStream()
        InstaCameraManager.getInstance().startPreviewStream(builder)
    }


    private fun checkToStartLive() {
        connectToSignallingServer()
        createVideoTrackFromInsta360()
        initializePeerConnections()
    }

    private fun stopLive() {
        //stop rtc live
        socket.disconnect()
        peerConnection.dispose()
        factory.dispose()
        customVideoCapturer.stopCapture()
    }

    override fun onVideoData(videoData: VideoData?) {
        // Callback frequency 500Hz
        // videoData.timestamp: The time since the camera was turned on
        // videoData.data: Preview raw stream data every frame
        // videoData.size: videoData.data.length

        if (videoData != null) {
            customVideoCapturer.addVideoData(videoData.timestamp, videoData.data)
        }
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing) {
            // Auto close preview after page loses focus
            stopLive()
            stopLocationUpdates()
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
                startLocationUpdates()
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
            .setStabEnabled(true)
            .setResolutionParams(
                mCurrentResolution!!.width,
                mCurrentResolution!!.height,
                mCurrentResolution!!.fps
            )
    }

    override fun onCameraStatusChanged(enabled: Boolean) {
        super.onCameraStatusChanged(enabled)
        if (!enabled) {
            mBtnSwitchLive.isChecked = false    //resume
            mBtnSwitchLive.isEnabled = false
        } else {
            if (wifiConnectionDialog.isShowing) {
                wifiConnectionDialog.dismiss()
            }
            val resolutionList = InstaCameraManager.getInstance()
                .getSupportedPreviewStreamResolution(InstaCameraManager.PREVIEW_TYPE_NORMAL)
            Log.d(tag, resolutionList.toString())
            mCurrentResolution = resolutionList[resolutionList.size - 2]
            Log.d(tag, mCurrentResolution.toString())

            initializewebrtc()

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
        val cameraPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        )
        val audioPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        )

        // Return true only if both permissions are granted
        return internetPermission == PackageManager.PERMISSION_GRANTED &&
                accessNetworkStatePermission == PackageManager.PERMISSION_GRANTED &&
                changeNetworkStatePermission == PackageManager.PERMISSION_GRANTED &&
                accessFineLocationPermission == PackageManager.PERMISSION_GRANTED &&
                accessCoarseLocationPermission == PackageManager.PERMISSION_GRANTED &&
                changeWifiStatePermission == PackageManager.PERMISSION_GRANTED &&
                accessWifiStatePermission == PackageManager.PERMISSION_GRANTED &&
                cameraPermission == PackageManager.PERMISSION_GRANTED &&
                audioPermission == PackageManager.PERMISSION_GRANTED
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
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
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
        if (!grantResults.contains(PackageManager.PERMISSION_DENIED)) {
            //permission granted
            if (usb) {
                InstaCameraManager.getInstance().openCamera(InstaCameraManager.CONNECT_TYPE_USB)
            } else if (ssid.startsWith("X3 ")) {
                InstaCameraManager.getInstance().openCamera(InstaCameraManager.CONNECT_TYPE_WIFI)
            } else {
                wifiConnectionDialog.show()
            }
            bindViews()
        } else {
            //permission denied
            Toast.makeText(this, "Permission are required", Toast.LENGTH_SHORT).show()
        }
    }

    // Bind Mobile Network
    private fun exchangeNetToMobileInsta(context: Context) {
        if (NetworkManager.getInstance().isBindingMobileNetwork()) {
            return
        }
        val connManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networks = connManager.allNetworks
        for (network in networks) {
            val networkInfo = connManager.getNetworkInfo(network)
            if (networkInfo != null && networkInfo.type == ConnectivityManager.TYPE_WIFI) {
                // Need to set network Id of current wifi to camera
                InstaCameraManager.getInstance()
                    .setNetIdToCamera(NetworkManager.getInstance().getNetworkId(network!!))
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        NetworkManager.getInstance().mNetworkCallback =
            object : ConnectivityManager.NetworkCallback() {
                @SuppressLint("SuspiciousIndentation")
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    val bindSuccessful: Boolean =
                        connManager.bindProcessToNetwork(null)
                    connManager.bindProcessToNetwork(network)
                    // Record the bound mobile network ID
                    NetworkManager.getInstance().mMobileNetId =
                        NetworkManager.getInstance().getNetworkId(network)
                    if (bindSuccessful) {
                        Log.d(
                            tag,
                            context.getString(R.string.live_toast_bind_mobile_network_successful)
                        )
                        //binding is successful so check to start the live
                        checkToStartLive()

                    } else {
                        Log.d(
                            tag,
                            context.getString(R.string.live_toast_bind_mobile_network_failed)
                        )

                    }
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    // The mobile network is suddenly unavailable, need to temporarily unbind and wait for the network to recover again
                    connManager.bindProcessToNetwork(null)
                    Log.d(
                        tag,
                        context.getString(R.string.live_toast_unbind_mobile_network_when_lost)
                    )
                }
            }
        connManager.requestNetwork(
            request,
            NetworkManager.getInstance().mNetworkCallback as ConnectivityManager.NetworkCallback
        )
    }

    override fun onResume() {
        super.onResume()
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val ssid = wifiInfo.ssid.replace("\"", "")

        if (usb) {
            InstaCameraManager.getInstance().openCamera(InstaCameraManager.CONNECT_TYPE_USB)
            if (wifiConnectionDialog.isShowing) {
                wifiConnectionDialog.dismiss()
            }
        } else if (ssid.startsWith("X3")) {
            InstaCameraManager.getInstance().openCamera(InstaCameraManager.CONNECT_TYPE_WIFI)
            if (wifiConnectionDialog.isShowing) {
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
                val velocity = location.speed.toInt()

                mTvVelocity.text = velocity.toString()

            }
        }
    }


    //webrtc
    private fun connectToSignallingServer() {
        try {
            options = IO.Options.builder()
                .setPath("/telecyclette/socket.io/")
                .setReconnection(true)
                .setAuth(mapOf("token" to authCode))
                .setQuery("peerID=$peerID")
                .build()
            socket = IO.socket(urlSignalingServer, options)

            Log.e(tag, "IO Socket: $urlSignalingServer")
            Log.d(tag, "PeerID: $peerID")



            socket.on(Socket.EVENT_CONNECT) {

                Log.d(tag,"connectToSignallingServer: connect")
                socket.emit("create or join", room)

            }.on("created") {

                Log.d(tag,"connectToSignallingServer: created")
                isInitiator = true

            }.on("full") {

                Log.d(tag,"connectToSignallingServer: full")

            }.on("join") {

                Log.d(tag,"connectToSignallingServer: join")
                Log.d(tag,"connectToSignallingServer: Another peer made a request to join room")
                Log.d(tag,"connectToSignallingServer: This peer is the initiator of room")
                isChannelReady = true
                startStreamingVideo()

            }.on("joined") {

                Log.d(tag,"connectToSignallingServer: joined")
                isChannelReady = true

            }.on("log") { args: Array<Any> ->

                for (arg in args) {
                    Log.d(tag,"connectToSignallingServer: $arg")
                }

            }.on("message") { args: Array<Any> ->

                try {
                    val message = args[0] as JSONObject
                    Log.d(
                        tag,
                        "connectToSignallingServer: got message $message"
                    )
                    if (message.getString("type") == "got user media") {
                        maybeStart()
                    }
                    else if (message.getString("type") == "offer") {
                        Log.d(
                            tag,
                            "connectToSignallingServer: received an offer $isInitiator $isStarted"
                        )
                        if (!isInitiator && !isStarted) {
                            maybeStart()
                        }
                        peerConnection.setRemoteDescription(
                            SimpleSdpObserver(),
                            SessionDescription(
                                SessionDescription.Type.OFFER,
                                message.getString("sdp")
                            )
                        )
                        doAnswer()
                    } else if (message.getString("type") == "answer" && isStarted) {
                        peerConnection.setRemoteDescription(
                            SimpleSdpObserver(),
                            SessionDescription(
                                SessionDescription.Type.ANSWER,
                                message.getString("sdp")
                            )
                        )
                    } else if (message.getString("type") == "candidate" && isStarted) {
                        Log.d(
                            tag,
                            "connectToSignallingServer: receiving candidates"
                        )
                        val candidate = IceCandidate(
                            message.getString("id"),
                            message.getInt("label"),
                            message.getString("candidate")
                        )
                        peerConnection.addIceCandidate(candidate)
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                    Log.e(tag, e.toString())
                }
            }.on(
                Socket.EVENT_DISCONNECT
            ) {
                Log.d(
                    tag,
                    "connectToSignallingServer: disconnect"
                )
            }
            socket.connect()
        } catch (e: URISyntaxException) {
            e.printStackTrace()
            Log.e(tag, e.toString())
        }
    }

    private fun doAnswer() {
        peerConnection.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                peerConnection.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                val message = JSONObject()
                try {
                    message.put("type", "answer")
                    message.put("room", room)
                    message.put("sdp", sessionDescription.description)
                    sendMessage(message)
                } catch (e: JSONException) {
                    e.printStackTrace()
                    Log.e(tag, e.toString())
                }
            }
        }, MediaConstraints())
    }

    private fun maybeStart() {
        Log.d(tag, "maybeStart: $isStarted $isChannelReady")
        if (!isStarted && isChannelReady) {
            isStarted = true
            if (isInitiator) {
                doCall()
            }
        }
    }

    private fun doCall() {
        val sdpMediaConstraints = MediaConstraints()
        sdpMediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")
        )
        /*  video from peer */
        sdpMediaConstraints.mandatory.add(
            MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true")
        )
        peerConnection.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.d(tag, "onCreateSuccess: ")
                peerConnection.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                val message = JSONObject()
                try {
                    message.put("type", "offer")
                    message.put("room", room)
                    message.put("sdp", sessionDescription.description)
                    sendMessage(message)
                } catch (e: JSONException) {
                    e.printStackTrace()
                    Log.e(tag, e.toString())
                }
            }
        }, sdpMediaConstraints)
    }

    private fun sendMessage(message: Any) {
        socket.emit("message", message)
    }

    private fun initializePeerConnectionFactory() {
        rootEglBase = EglBase.create()
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(
            applicationContext
        ).createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)
        val options = PeerConnectionFactory.Options()
        val defaultVideoEncoderFactory = DefaultVideoEncoderFactory(
            rootEglBase.eglBaseContext, true, true)
        val defaultVideoDecoderFactory = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)

        factory = PeerConnectionFactory.builder().setOptions(options)
            .setVideoEncoderFactory(defaultVideoEncoderFactory)
            .setVideoDecoderFactory(defaultVideoDecoderFactory)
            .createPeerConnectionFactory()
    }

    private fun initializewebrtc(){
        // Initialize the customVideoCapturer
        customVideoCapturer = CustomVideoCapturer()
        customVideoCapturer.init(mCurrentResolution!!.width, mCurrentResolution!!.height)
        // Initialize factory
        initializePeerConnectionFactory()
        // Initialize the video source
        videoSource = factory.createVideoSource(false)
        // Initialize the surfaceTextureHelper
        surfaceTextureHelper = SurfaceTextureHelper.create("VideoCapturerThread", rootEglBase.eglBaseContext)
        // Initialize the video capturer
        customVideoCapturer.initialize(surfaceTextureHelper, applicationContext, videoSource.capturerObserver)
    }

    private fun createVideoTrackFromInsta360() {
        videoTrackFromCamera360 = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource)
        videoTrackFromCamera360.setEnabled(true)

        // Create an AudioSource instance
        audioConstraints = MediaConstraints()
        audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack(AUDIO_TRACK_ID, audioSource)

    }

    private fun initializePeerConnections() {
        peerConnection = createPeerConnection(factory)!!
    }

    private fun startStreamingVideo() {
        val mediaStream: MediaStream = factory.createLocalMediaStream("ARDAMS")
        mediaStream.addTrack(videoTrackFromCamera360) // Assuming videoTrackFromCamera is a MediaStreamTrack
        mediaStream.addTrack(localAudioTrack) // Assuming localAudioTrack is a MediaStreamTrack
        peerConnection.addTrack(videoTrackFromCamera360) // Add the video track
        peerConnection.addTrack(localAudioTrack) // Add the audio track

        val message = JSONObject()
        try {
            message.put("type", "got user media")
            message.put("room", room)
            Log.d(
                tag,
                "startStreamingVideo: sending data $message"
            )
            sendMessage(message)
        } catch (e: JSONException) {
            e.printStackTrace()
            Log.e(tag, e.toString())
        }
    }

    private fun createPeerConnection(factory: PeerConnectionFactory?): PeerConnection? {
        val iceServers = mutableListOf<PeerConnection.IceServer>()
        iceServers.add(PeerConnection.IceServer.builder(urlStunServer).createIceServer())
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        val pcConstraints = MediaConstraints()
        val pcObserver: PeerConnection.Observer =
            object : PeerConnection.Observer {
                override fun onSignalingChange(signalingState: PeerConnection.SignalingState) {
                    Log.d(tag, "onSignalingChange: ")
                }

                override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState) {
                    Log.d(tag, "onIceConnectionChange: ")
                }

                override fun onIceConnectionReceivingChange(b: Boolean) {
                    Log.d(tag, "onIceConnectionReceivingChange: ")
                }

                override fun onIceGatheringChange(iceGatheringState: PeerConnection.IceGatheringState) {
                    Log.d(tag, "onIceGatheringChange: ")
                }

                override fun onIceCandidate(iceCandidate: IceCandidate) {
                    Log.d(tag, "onIceCandidate: ")
                    val message = JSONObject()
                    try {
                        message.put("type", "candidate")
                        message.put("room", room)
                        message.put("label", iceCandidate.sdpMLineIndex)
                        message.put("id", iceCandidate.sdpMid)
                        message.put("candidate", iceCandidate.sdp)
                        Log.d(
                            tag,
                            "onIceCandidate: sending candidate $message"
                        )
                        sendMessage(message)
                    } catch (e: JSONException) {
                        e.printStackTrace()
                        Log.e(tag, e.toString())
                    }
                }

                override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
                    Log.d(tag, "onIceCandidatesRemoved: ")
                }

                override fun onAddStream(p0: MediaStream?) {
                    TODO("Not yet implemented")
                }

                override fun onTrack(transceiver: RtpTransceiver?) {
                    super.onTrack(transceiver)

                    if (transceiver != null && transceiver.receiver != null && transceiver.receiver.track() != null) {
                        val track = transceiver.receiver.track()
                        if (track is AudioTrack) {
                            // Handle incoming audio track
                            handleIncomingAudioTrack(track)
                        }
                    }
                }

                override fun onRemoveStream(mediaStream: MediaStream) {
                    Log.d(tag, "onRemoveStream: ")
                }

                override fun onDataChannel(dataChannel: DataChannel) {
                    Log.d(tag, "onDataChannel: ")
                }

                override fun onRenegotiationNeeded() {
                    Log.d(tag, "onRenegotiationNeeded: ")
                }
            }
        return factory!!.createPeerConnection(rtcConfig, pcConstraints, pcObserver)
    }

    private fun handleIncomingAudioTrack(audioTrack: AudioTrack) {
        audioTrack.setEnabled(true)
    }
}
