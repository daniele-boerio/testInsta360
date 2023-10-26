package com.example.testapp.activity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.example.testapp.databinding.ActivityTestBinding

import com.example.testapp.observer.SimpleSdpObserver
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
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
    private val TAG = "com.example.testapp." + this::class.simpleName
    private val tag = "com.example.testapp." + this::class.simpleName

    private val requestCode = 111
    private val VIDEO_TRACK_ID = "ARDAMSv0"
    private val VIDEO_RESOLUTION_WIDTH = 1280
    private val VIDEO_RESOLUTION_HEIGHT = 720
    private val FPS = 30

    private lateinit var binding: ActivityTestBinding

    private var socket: Socket? = null
    private var isInitiator = false
    private var isChannelReady : Boolean = false
    private var isStarted : Boolean = false


    lateinit var audioConstraints: MediaConstraints
    lateinit var videoConstraints: MediaConstraints
    lateinit var sdpConstraints: MediaConstraints
    lateinit var videoSource: VideoSource
    lateinit var localVideoTrack: VideoTrack
    lateinit var audioSource: AudioSource
    lateinit var localAudioTrack: AudioTrack
    lateinit var surfaceTextureHelper: SurfaceTextureHelper

    private lateinit var peerConnection: PeerConnection
    private lateinit var rootEglBase: EglBase
    private lateinit var factory: PeerConnectionFactory
    private lateinit var videoTrackFromCamera: VideoTrack

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestBinding.inflate(layoutInflater)
        start()
    }

    override fun onDestroy() {
        if (socket != null) {
            socket!!.disconnect()
        }
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
            // For me this was "http://192.168.1.220:3000";
            // $ hostname -I
            val URL = "http://172.28.158.160:3030/" // "https://calm-badlands-59575.herokuapp.com/";
            Log.e(TAG, "IO Socket: $URL")
            socket = IO.socket(URL)
            socket!!.on(Socket.EVENT_CONNECT) {
                Log.d(
                    TAG,
                    "connectToSignallingServer: connect"
                )
                socket!!.emit("create or join", "cuarto")
            }.on(
                "ipaddr"
            ) { args: Array<Any?>? ->
                Log.d(
                    TAG,
                    "connectToSignallingServer: ipaddr"
                )
            }.on(
                "created"
            ) { args: Array<Any?>? ->
                Log.d(
                    TAG,
                    "connectToSignallingServer: created"
                )
                isInitiator = true
            }.on(
                "full"
            ) { args: Array<Any?>? ->
                Log.d(
                    TAG,
                    "connectToSignallingServer: full"
                )
            }.on(
                "join"
            ) { args: Array<Any?>? ->
                Log.d(
                    TAG,
                    "connectToSignallingServer: join"
                )
                Log.d(
                    TAG,
                    "connectToSignallingServer: Another peer made a request to join room"
                )
                Log.d(
                    TAG,
                    "connectToSignallingServer: This peer is the initiator of room"
                )
                isChannelReady = true
            }.on("joined") { args: Array<Any?>? ->
                Log.d(
                    TAG,
                    "connectToSignallingServer: joined"
                )
                isChannelReady = true
            }.on(
                "log"
            ) { args: Array<Any> ->
                for (arg in args) {
                    Log.d(
                        TAG,
                        "connectToSignallingServer: $arg"
                    )
                }
            }.on(
                "message"
            ) { args: Array<Any?>? ->
                Log.d(
                    TAG,
                    "connectToSignallingServer: got a message"
                )
            }.on(
                "message"
            ) { args: Array<Any> ->
                try {
                    if (args[0] is String) {
                        val message = args[0] as String
                        if (message == "got user media") {
                            maybeStart()
                        }
                    } else {
                        val message = args[0] as JSONObject
                        Log.d(
                            TAG,
                            "connectToSignallingServer: got message $message"
                        )
                        if (message.getString("type") == "offer") {
                            Log.d(
                                TAG,
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
                                TAG,
                                "connectToSignallingServer: receiving candidates"
                            )
                            val candidate = IceCandidate(
                                message.getString("id"),
                                message.getInt("label"),
                                message.getString("candidate")
                            )
                            peerConnection.addIceCandidate(candidate)
                        }
                        /*else if (message === 'bye' && isStarted) {
        handleRemoteHangup();
    }*/
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }.on(
                Socket.EVENT_DISCONNECT
            ) { args: Array<Any?>? ->
                Log.d(
                    TAG,
                    "connectToSignallingServer: disconnect"
                )
            }
            socket!!.connect()
        } catch (e: URISyntaxException) {
            e.printStackTrace()
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
                    message.put("sdp", sessionDescription.description)
                    sendMessage(message)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }, MediaConstraints())
    }

    private fun maybeStart() {
        Log.d(TAG, "maybeStart: $isStarted $isChannelReady")
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
            org.webrtc.MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")
        )
        sdpMediaConstraints.mandatory.add(
            org.webrtc.MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true")
        )
        peerConnection.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                Log.d(TAG, "onCreateSuccess: ")
                peerConnection.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                val message = JSONObject()
                try {
                    message.put("type", "offer")
                    message.put("sdp", sessionDescription.description)
                    sendMessage(message)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }, sdpMediaConstraints)
    }

    private fun sendMessage(message: Any) {
        socket!!.emit("message", message)
    }

    private fun initializeSurfaceViews() {
        rootEglBase = EglBase.create()
        binding.surfaceView.init(rootEglBase.getEglBaseContext(), null)
        binding.surfaceView.setEnableHardwareScaler(true)
        binding.surfaceView.setMirror(true)
        binding.surfaceView2.init(rootEglBase.getEglBaseContext(), null)
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
        val videoSource: org.webrtc.VideoSource = factory.createVideoSource(videoCapturer)
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
        sendMessage("got user media")
    }

    private fun createPeerConnection(factory: PeerConnectionFactory?): PeerConnection? {
        val iceServers: ArrayList<PeerConnection.IceServer> = ArrayList<PeerConnection.IceServer>()
        val URL = "stun:stun.l.google.com:19302"
        iceServers.add(PeerConnection.IceServer(URL))
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        val pcConstraints = MediaConstraints()
        val pcObserver: PeerConnection.Observer =
            object : PeerConnection.Observer {
                override fun onSignalingChange(signalingState: PeerConnection.SignalingState) {
                    Log.d(TAG, "onSignalingChange: ")
                }

                override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState) {
                    Log.d(TAG, "onIceConnectionChange: ")
                }

                override fun onIceConnectionReceivingChange(b: Boolean) {
                    Log.d(TAG, "onIceConnectionReceivingChange: ")
                }

                override fun onIceGatheringChange(iceGatheringState: PeerConnection.IceGatheringState) {
                    Log.d(TAG, "onIceGatheringChange: ")
                }

                override fun onIceCandidate(iceCandidate: IceCandidate) {
                    Log.d(TAG, "onIceCandidate: ")
                    val message = JSONObject()
                    try {
                        message.put("type", "candidate")
                        message.put("label", iceCandidate.sdpMLineIndex)
                        message.put("id", iceCandidate.sdpMid)
                        message.put("candidate", iceCandidate.sdp)
                        Log.d(
                            TAG,
                            "onIceCandidate: sending candidate $message"
                        )
                        sendMessage(message)
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }

                override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
                    Log.d(TAG, "onIceCandidatesRemoved: ")
                }

                override fun onAddStream(mediaStream: MediaStream) {
                    Log.d(TAG, "onAddStream: " + mediaStream.videoTracks.size)
                    val remoteVideoTrack: VideoTrack = mediaStream.videoTracks.get(0)
                    val remoteAudioTrack: org.webrtc.AudioTrack = mediaStream.audioTracks.get(0)
                    remoteAudioTrack.setEnabled(true)
                    remoteVideoTrack.setEnabled(true)
                    remoteVideoTrack.addRenderer(VideoRenderer(binding.surfaceView2))
                }

                override fun onRemoveStream(mediaStream: MediaStream) {
                    Log.d(TAG, "onRemoveStream: ")
                }

                override fun onDataChannel(dataChannel: DataChannel) {
                    Log.d(TAG, "onDataChannel: ")
                }

                override fun onRenegotiationNeeded() {
                    Log.d(TAG, "onRenegotiationNeeded: ")
                }
            }
        return factory!!.createPeerConnection(rtcConfig, pcConstraints, pcObserver)
    }

    private fun createVideoCapturer(): VideoCapturer? {
        val videoCapturer: VideoCapturer?
        videoCapturer = if (useCamera2()) {
            createCameraCapturer(Camera2Enumerator(this))
        } else {
            createCameraCapturer(Camera1Enumerator(true))
        }
        return videoCapturer
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames: Array<String> = enumerator.getDeviceNames()
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val videoCapturer: VideoCapturer = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val videoCapturer: VideoCapturer = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
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


        // Return true only if both permissions are granted
        return cameraPermission == PackageManager.PERMISSION_GRANTED &&
                audioPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
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
        if(!grantResults.contains(PackageManager.PERMISSION_DENIED)){
            //permission granted
            start()
        }else{
            //permission denied
            Toast.makeText(this, "Permission are required" , Toast.LENGTH_SHORT).show()
        }
    }
}
