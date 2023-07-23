package com.example.testapp.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.arashivision.sdkcamera.InstaCameraSDK
import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.example.testapp.databinding.ActivityMainBinding

class MainActivity : ObserveCameraActivity() {

    private lateinit var binding : ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        // Init SDK
        InstaCameraSDK.init(this.application)

        binding.wifiConnect.setOnClickListener {
            InstaCameraManager.getInstance().openCamera(InstaCameraManager.CONNECT_TYPE_WIFI)
        }
        binding.usbConnect.setOnClickListener {
            InstaCameraManager.getInstance().openCamera(InstaCameraManager.CONNECT_TYPE_USB)
        }
        binding.startLive.setOnClickListener {
            startActivity(Intent(this@MainActivity, LiveActivity::class.java))
        }

    }
    override fun onCameraStatusChanged(enabled: Boolean) {
        super.onCameraStatusChanged(enabled)
        if (enabled) {
            Toast.makeText(this, "camera connected", Toast.LENGTH_SHORT).show()
            binding.startLive.visibility = View.VISIBLE
        } else {
            Toast.makeText(this, "camera disconnected", Toast.LENGTH_SHORT).show()
            binding.startLive.visibility = View.INVISIBLE
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

}