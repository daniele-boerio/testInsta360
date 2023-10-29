package com.example.testapp.activity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.testapp.databinding.ActivityTestBinding
import com.example.testapp.observer.SimpleSdpObserver
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.net.URISyntaxException


class TestActivity : AppCompatActivity() {
    private val tag = "com.example.testapp." + this::class.simpleName

    private val requestCode = 111
    private val VIDEO_TRACK_ID = "ARDAMSv0"
    private val VIDEO_RESOLUTION_WIDTH = 1280
    private val VIDEO_RESOLUTION_HEIGHT = 720
    private val FPS = 30

    private lateinit var binding: ActivityTestBinding

    private lateinit var socket: Socket
    private lateinit var options : IO.Options
    private val authCode = "tokenDiAuth"
    private val peerID = android.os.Build.MODEL //todo
    private val room = "STANZA"

    private var isInitiator = false
    private var isChannelReady : Boolean = false
    private var isStarted : Boolean = false


    private lateinit var audioConstraints: MediaConstraints
    private lateinit var videoConstraints: MediaConstraints
    private lateinit var sdpConstraints: MediaConstraints
    private lateinit var videoSource: VideoSource
    private lateinit var localVideoTrack: VideoTrack
    private lateinit var audioSource: AudioSource
    private lateinit var localAudioTrack: AudioTrack
    private lateinit var surfaceTextureHelper: SurfaceTextureHelper

    private lateinit var peerConnection: PeerConnection
    private lateinit var rootEglBase: EglBase
    private lateinit var factory: PeerConnectionFactory
    private lateinit var videoTrackFromCamera: VideoTrack

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestBinding.inflate(layoutInflater)
        setSupportActionBar(binding.toolbar)
        start()
    }

    override fun onDestroy() {
        socket.disconnect()
        super.onDestroy()
    }
    private fun start() {
        if(hasPermissions()){
            connectToSignallingServer()
            initializeSurfaceViews()
            initializePeerConnectionFactory()
            createVideoTrackFromCameraAndShowIt()
            initializePeerConnections()
            startStreamingVideo()
        }else{
            requestPermissions()
        }
    }

    private fun connectToSignallingServer() {
        try {
            val URL = "http://192.168.1.92:3030"

            socket = IO.socket(URL)
            options = IO.Options.builder()
                .build()

            /*val URL = "https://develop.ewlab.di.unimi.it/"

            options = IO.Options.builder()
                .setPath("/telecyclette/socket.io/")
                .setReconnection(true)
                .setAuth(mapOf("token" to authCode))
                .setQuery("peerID=$peerID")
                .build()*/
            socket = IO.socket(URL, options)

            Log.e(tag, "IO Socket: $URL")
            Log.d(tag, "PeerID: $peerID")



            socket.on(Socket.EVENT_CONNECT) {

                Log.d(tag,"connectToSignallingServer: connect")
                socket.emit("create or join", room) //todo

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

            }.on("joined") {

                Log.d(tag,"connectToSignallingServer: joined")
                isChannelReady = true

            }.on("log") { args: Array<Any> ->

                for (arg in args) {
                    Log.d(tag,"connectToSignallingServer: $arg")
                }

            }.on("message") {

                Log.d(tag,"connectToSignallingServer: got a message")

            }.on("message") { args: Array<Any> ->

                try {
                    val message = args[0] as JSONObject
                    Log.d(
                        tag,
                        "connectToSignallingServer: got message $message"
                    )
                    if (message.getString("type") == "media") {
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

    //MirtDPM4
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

    private fun initializeSurfaceViews() {
        rootEglBase = EglBase.create()
        binding.surfaceView.init(rootEglBase.eglBaseContext, null)
        binding.surfaceView.setEnableHardwareScaler(true)
        binding.surfaceView.setMirror(true)
        binding.surfaceView2.init(rootEglBase.eglBaseContext, null)
        binding.surfaceView2.setEnableHardwareScaler(true)
        binding.surfaceView2.setMirror(true)

        //add one more
    }

    private fun initializePeerConnectionFactory() {

        PeerConnectionFactory.initializeAndroidGlobals(this, true, true, true)
        factory = PeerConnectionFactory(null)
        factory.setVideoHwAccelerationOptions(
            rootEglBase.eglBaseContext,
            rootEglBase.eglBaseContext
        )
    }

    private fun createVideoTrackFromCameraAndShowIt() {
        audioConstraints = MediaConstraints()
        val videoCapturer: VideoCapturer? = createVideoCapturer()
        val videoSource: VideoSource = factory.createVideoSource(videoCapturer)
        videoCapturer!!.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS)
        videoTrackFromCamera = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource)
        videoTrackFromCamera.setEnabled(true)
        videoTrackFromCamera.addRenderer(VideoRenderer(binding.surfaceView))

        //create an AudioSource instance
        audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("101", audioSource)
    }

    private fun initializePeerConnections() {
        peerConnection = createPeerConnection(factory)!!
    }

    private fun startStreamingVideo() {
        val mediaStream: MediaStream = factory.createLocalMediaStream("ARDAMS")
        mediaStream.addTrack(videoTrackFromCamera)
        mediaStream.addTrack(localAudioTrack)
        peerConnection.addStream(mediaStream)
        val message = JSONObject()
        try {
            message.put("type", "media")
            message.put("room", room)
            Log.d(
                tag,
                "startStreamingVideo: sending data $message"
            )
            sendMessage(message)
        }catch (e: JSONException) {
            e.printStackTrace()
            Log.e(tag, e.toString())
        }
    }

    private fun createPeerConnection(factory: PeerConnectionFactory?): PeerConnection? {
        val iceServers: ArrayList<PeerConnection.IceServer> = ArrayList()
        val URL = "stun:stun.l.google.com:19302"
        iceServers.add(PeerConnection.IceServer(URL))
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

                override fun onAddStream(mediaStream: MediaStream) {
                    Log.d(tag, "onAddStream: " + mediaStream.videoTracks.size)
                    val remoteVideoTrack: VideoTrack = mediaStream.videoTracks[0]
                    val remoteAudioTrack: org.webrtc.AudioTrack = mediaStream.audioTracks[0]
                    remoteAudioTrack.setEnabled(true)
                    remoteVideoTrack.setEnabled(true)
                    remoteVideoTrack.addRenderer(VideoRenderer(binding.surfaceView2))
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

    private fun createVideoCapturer(): VideoCapturer? {
        val videoCapturer: VideoCapturer? = if (useCamera2()) {
            createCameraCapturer(Camera2Enumerator(this))
        } else {
            createCameraCapturer(Camera1Enumerator(true))
        }
        return videoCapturer
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames: Array<String> = enumerator.deviceNames
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        return null
    }

    private fun useCamera2(): Boolean {
        return Camera2Enumerator.isSupported(this)
    }

    private fun hasPermissions(): Boolean {
        val cameraPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        )
        val audioPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        )

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
        return cameraPermission == PackageManager.PERMISSION_GRANTED &&
               audioPermission == PackageManager.PERMISSION_GRANTED &&
                internetPermission == PackageManager.PERMISSION_GRANTED &&
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
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
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
            start()
        }else{
            //permission denied
            Toast.makeText(this, "Permission are required" , Toast.LENGTH_SHORT).show()
        }
    }
}
