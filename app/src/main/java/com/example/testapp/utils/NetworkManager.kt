package com.example.testapp.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
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

    var mMobileNetId: Long = -1

    var mNetworkCallback: NetworkCallback? = null

    open fun exchangeNetToMobile(context : Context){
        if (isBindingMobileNetwork()) {
            return
        }
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        mNetworkCallback = object : NetworkCallback() {
            @SuppressLint("SuspiciousIndentation")
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                val bindSuccessful: Boolean = connManager.bindProcessToNetwork(null)
                connManager.bindProcessToNetwork(network)
                // Record the bound mobile network ID
                mMobileNetId = getNetworkId(network)
                if (bindSuccessful) {
                    Log.d(tag, context.getString(R.string.live_toast_bind_mobile_network_successful))
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
        connManager.requestNetwork(request, mNetworkCallback as NetworkCallback)
    }
    fun getNetworkId(network: Network): Long {
        return network.networkHandle
    }

    // Unbind Mobile Network
    open fun clearBindProcess(context: Context) {
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connManager.bindProcessToNetwork(null)
        if (mNetworkCallback != null) {
            // 注销callback，彻底解除绑定
            // Unregister Callback, Unbind completely
            connManager.unregisterNetworkCallback(mNetworkCallback!!)

            Log.d(tag, context.getString(R.string.live_toast_unbind_mobile_network))
        }
        mNetworkCallback = null
        mMobileNetId = -1
    }

    fun getMobileNetId(): Long {
        return mMobileNetId
    }

    fun isBindingMobileNetwork(): Boolean {
        return mNetworkCallback != null
    }

}