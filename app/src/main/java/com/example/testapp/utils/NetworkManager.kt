package com.example.testapp.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import android.widget.Toast
import com.arashivision.sdkcamera.camera.InstaCameraManager
import com.example.testapp.R


// Use mobile data to access the network when connected to the camera wifi

open class NetworkManager {
    private val tag = "com.example.testapp." + this::class.simpleName

    companion object{
        fun getInstance(): NetworkManager {
            return NetworkHolder.instance
        }
    }

    private object NetworkHolder {
        val instance = NetworkManager()
    }

    private var mMobileNetId: Long = -1

    private var mNetworkCallback: NetworkCallback? = null

    // 绑定移动网络
    // Bind Mobile Network
    open fun exchangeNetToMobile(context: Context) {
        if (isBindingMobileNetwork()) {
            return
        }
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networks = connManager.allNetworks
        for (network in networks) {
            val networkInfo = connManager.getNetworkInfo(network)
            if (networkInfo != null && networkInfo.type == ConnectivityManager.TYPE_WIFI) {
                // 需将WIFI的网络ID设置给相机
                // Need to set network Id of current wifi to camera
                InstaCameraManager.getInstance().setNetIdToCamera(getNetworkId(network!!))
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        mNetworkCallback = object : NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                val bindSuccessful: Boolean =
                    connManager.bindProcessToNetwork(null)
                    connManager.bindProcessToNetwork(network)
                // 记录绑定的移动网络ID
                // Record the bound mobile network ID
                mMobileNetId = getNetworkId(network)
                if (bindSuccessful) {
                    Toast.makeText(
                        context,
                        R.string.live_toast_bind_mobile_network_successful,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        R.string.live_toast_bind_mobile_network_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                // The mobile network is suddenly unavailable, need to temporarily unbind and wait for the network to recover again
                connManager.bindProcessToNetwork(null)
                Toast.makeText(
                    context,
                    R.string.live_toast_unbind_mobile_network_when_lost,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        connManager.requestNetwork(request, mNetworkCallback as NetworkCallback)
    }
    private fun getNetworkId(network: Network): Long {
        return network.networkHandle
    }

    // 解除网络绑定
    // Unbind Mobile Network
    open fun clearBindProcess(context: Context) {
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connManager.bindProcessToNetwork(null)
        if (mNetworkCallback != null) {
            // 注销callback，彻底解除绑定
            // Unregister Callback, Unbind completely
            connManager.unregisterNetworkCallback(mNetworkCallback!!)
            Toast.makeText(
                context,
                R.string.live_toast_unbind_mobile_network,
                Toast.LENGTH_SHORT
            ).show()
        }
        mNetworkCallback = null
        mMobileNetId = -1
        // 重置相机网络
        // Reset Camera Net Id
        InstaCameraManager.getInstance().setNetIdToCamera(-1)
    }

    fun getMobileNetId(): Long {
        return mMobileNetId
    }

    fun isBindingMobileNetwork(): Boolean {
        return mNetworkCallback != null
    }

    private fun typeNetwork(context : Context) : String
    {
        val connectivityManager = context
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network =
            connectivityManager.activeNetwork // network is currently in a high power state for performing data transmission.
        Log.d("Network", "active network $network")
        network ?: return ""  // return false if network is null
        val actNetwork = connectivityManager.getNetworkCapabilities(network)
            ?: return "" // return false if Network Capabilities is null
        return when {
            actNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> { // check if wifi is connected
                Log.d("Network", "wifi connected")
                "WIFI"
            }
            actNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> { // check if mobile dats is connected
                Log.d("Network", "cellular network connected")
                "CELLULAR"
            }
            else -> {
                Log.d("Network", "internet not connected")
                ""
            }
        }
    }

}