package com.example.testapp.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.testapp.R
import com.example.testapp.databinding.ActivityUserBinding
import com.example.testapp.model.User
import com.example.testapp.utils.NetworkManager
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import org.json.JSONObject

class UserActivity : AppCompatActivity() {
    private val tag = "com.example.testapp." + this::class.simpleName
    private lateinit var binding : ActivityUserBinding
    private lateinit var retirementHome : TextView
    private lateinit var connectivityStatus : TextView
    private lateinit var connectButton : Button
    private var user : User? = null
    private val url = "https://develop.ewlab.di.unimi.it/telecyclette/api/connections"
    private var username : String? = null
    private var password : String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        retirementHome = binding.retirementHome
        connectivityStatus = binding.connectivityStatus
        connectButton = binding.connectButton

        //get user list from loginActivity
        val intent = intent
        user = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("user", User::class.java)
        }else{
            intent.getParcelableExtra("user")
        }

        //get user and password from shared
        val sharedPreferences = getSharedPreferences("LoginPreferences", Context.MODE_PRIVATE)
        username = sharedPreferences.getString("username", null)
        password = sharedPreferences.getString("password", null)

        //check if internet is available to perform the API request
        if (!isInternetAvailable(this)) {
            //use mobile network for api request
            exchangeNetToMobile(this.applicationContext)

        }else{
            //if data aren't collected from LoginActivity (case empty List) or opened app in UserActivity
            if(user == null){

                //call API Request
                makeBasicAuthRequest(username!!,password!!)

            }else{
                //populate the view with the user
                retirementHome.text = user!!.retirementHomeID

                //if the user has ip and port then is connectable
                if (isConnectable(user!!)) {
                    connectivityStatus.text = resources.getString(R.string.ready)
                    connectivityStatus.setTextColor(ContextCompat.getColor(this, R.color.green))
                    connectButton.isEnabled = true
                } else {
                    connectivityStatus.text = Resources.getSystem().getString(R.string.not_ready)
                    connectivityStatus.setTextColor(ContextCompat.getColor(this, R.color.red))
                    connectButton.isEnabled = false
                }
            }
        }

        connectButton.setOnClickListener{
            //setup the alert dialog
            val alertDialogBuilder = AlertDialog.Builder(this)
            alertDialogBuilder.setTitle(getString(R.string.dialog_user_title))
            alertDialogBuilder.setMessage(getString(R.string.dialog_user_message))
            alertDialogBuilder.setPositiveButton(getString(R.string.dialog_user_confirm)) { _, _ ->

                //create the intent passing users info
                val i = Intent(this@UserActivity, LiveActivity::class.java)
                i.putExtra("retirementHomeID", user!!.retirementHomeID)
                i.putExtra("ipaddress", user!!.ipaddress)
                i.putExtra("port", user!!.port)
                startActivity(i)
            }

            alertDialogBuilder.setNegativeButton(getString(R.string.dialog_user_cancel)) { _, _ ->
                // Handle negative button click here (if needed)
            }

            val alertDialog = alertDialogBuilder.create()
            alertDialog.show()
        }
    }

    private fun isConnectable(user : User): Boolean {   //method where i check if the user is connectable
        return user.ipaddress != ""
    }

    private fun makeBasicAuthRequest(username: String, password: String) {  //api request

        val client = OkHttpClient()
        val credential = Credentials.basic(username, password)
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", credential)         //base auth
            .build()

        client.newCall(request).enqueue(object : Callback{
            override fun onFailure(call: Call, e: IOException) {
                Log.d(tag,"Request failed with error: $e")
            }

            override fun onResponse(call: Call, response: Response) {

                if(response.code == 204){
                    Log.d(tag,"Request code ${response.code}: ${getString(R.string.code204)}")
                    //function that permit to update UI on a function
                    runOnUiThread {
                        Toast.makeText(applicationContext, getString(R.string.code204), Toast.LENGTH_SHORT).show()
                    }
                    //delete shared preferences if an error occurred
                    val sharedPreferences = getSharedPreferences("LoginPreferences", Context.MODE_PRIVATE)
                    val editor = sharedPreferences.edit()
                    editor.remove("username")
                    editor.remove("password")
                    editor.apply()
                    //an error is occurred during login return on LoginActivity
                    startActivity(Intent(this@UserActivity, LoginActivity::class.java))
                }else{
                    when (response.code) {
                        200 -> {    //result returned correctly
                            Log.d(tag,"Request code ${response.code}: ${getString(R.string.code200)}")

                            val responseBody = response.body!!.string()
                            // Parse the JSON response into your User data model
                            user = parseJsonToUserData(responseBody)

                            //function that permit to update UI on a function
                            runOnUiThread {
                                updateUI(user!!)
                            }
                        }
                        401 -> {    //verify username and password
                            Log.d(tag,"Request code ${response.code}: ${getString(R.string.code401)}")
                            //function that permit to update UI on a function
                            runOnUiThread {
                                Toast.makeText(applicationContext, getString(R.string.code401), Toast.LENGTH_SHORT).show()
                            }
                            //delete shared preferences if an error occurred
                            val sharedPreferences = getSharedPreferences("LoginPreferences", Context.MODE_PRIVATE)
                            val editor = sharedPreferences.edit()
                            editor.remove("username")
                            editor.remove("password")
                            editor.apply()
                            //an error is occurred during login return on LoginActivity
                            startActivity(Intent(this@UserActivity, LoginActivity::class.java))
                        }
                        403 -> {    //client not authorized
                            Log.d(tag,"Request code ${response.code}: ${getString(R.string.code403)}")
                            //function that permit to update UI on a function
                            runOnUiThread {
                                Toast.makeText(applicationContext, getString(R.string.code403), Toast.LENGTH_SHORT).show()
                            }
                            //delete shared preferences if an error occurred
                            val sharedPreferences = getSharedPreferences("LoginPreferences", Context.MODE_PRIVATE)
                            val editor = sharedPreferences.edit()
                            editor.remove("username")
                            editor.remove("password")
                            editor.apply()
                            //an error is occurred during login return on LoginActivity
                            startActivity(Intent(this@UserActivity, LoginActivity::class.java))
                        }
                        422 -> {    //missing data (credential)
                            Log.d(tag,"Request code ${response.code}: ${getString(R.string.code422)}")
                            //function that permit to update UI on a function
                            runOnUiThread {
                                Toast.makeText(applicationContext, getString(R.string.code422), Toast.LENGTH_SHORT).show()
                            }
                            //delete shared preferences if an error occurred
                            val sharedPreferences = getSharedPreferences("LoginPreferences", Context.MODE_PRIVATE)
                            val editor = sharedPreferences.edit()
                            editor.remove("username")
                            editor.remove("password")
                            editor.apply()
                            //an error is occurred during login return on LoginActivity
                            startActivity(Intent(this@UserActivity, LoginActivity::class.java))
                        }
                        else -> {
                            Log.d(tag,"Request code ${response.code}: ${response.message}")
                            //function that permit to update UI on a function
                            runOnUiThread {
                                Toast.makeText(applicationContext, response.message, Toast.LENGTH_SHORT).show()
                            }
                            //delete shared preferences if an error occurred
                            val sharedPreferences = getSharedPreferences("LoginPreferences", Context.MODE_PRIVATE)
                            val editor = sharedPreferences.edit()
                            editor.remove("username")
                            editor.remove("password")
                            editor.apply()
                            //if an error is occurred during login return on LoginActivity
                            startActivity(Intent(this@UserActivity, LoginActivity::class.java))
                        }
                    }
                }
            }
        })
    }

    fun updateUI(apiUser : User){

        runOnUiThread {
            //change the view if the user is connectable or not
            retirementHome.text = apiUser.retirementHomeID
            if (isConnectable(apiUser)) {
                connectivityStatus.text = resources.getString(R.string.ready)
                connectivityStatus.setTextColor(ContextCompat.getColor(this, R.color.green))
                connectButton.isEnabled = true
            } else {
                connectivityStatus.text = Resources.getSystem().getString(R.string.not_ready)
                connectivityStatus.setTextColor(ContextCompat.getColor(this, R.color.red))
                connectButton.isEnabled = false
            }
        }

    }

    // Function to parse JSON response into UserData
    fun parseJsonToUserData(json: String): User {
        // Implement JSON parsing logic here and return a UserData object
        // You can use libraries like Gson to simplify JSON parsing
        // For simplicity, here's a basic example:
        val jsonObject = JSONObject(json)
        return User(
            jsonObject.getString("retirementHomeID"),
            jsonObject.getString("ipAddress"),
            jsonObject.getString("port")
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.user_list_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_refresh -> {
                // Handle the refresh action here
                if (isInternetAvailable(this)) {
                    // Internet connection is available, perform network-related tasks

                    Toast.makeText(applicationContext, getString(R.string.refreshing_data), Toast.LENGTH_SHORT).show()

                    //call API Request
                    makeBasicAuthRequest(username!!,password!!)

                } else {
                    // No internet connection, handle the absence of internet access
                    Toast.makeText(this, getString(R.string.no_connection), Toast.LENGTH_SHORT).show()
                }
                return true
            }
            // Handle other menu items if needed
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun isInternetAvailable(context: Context): Boolean {
        var isOnline = false
        try {
            val manager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) // need ACCESS_NETWORK_STATE permission
            val bounderNetwork = manager.getNetworkCapabilities(manager.boundNetworkForProcess) // need ACCESS_NETWORK_STATE permission
            isOnline = (capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) || (bounderNetwork != null && bounderNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return isOnline
    }

    private fun exchangeNetToMobile(context : Context){     //recreated the function because i need to call API after the success of Bind
        if (NetworkManager.getInstance().isBindingMobileNetwork()) {
            return
        }
        val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        NetworkManager.getInstance().mNetworkCallback = object : ConnectivityManager.NetworkCallback() {
            @SuppressLint("SuspiciousIndentation")
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                val bindSuccessful: Boolean = connManager.bindProcessToNetwork(null)
                connManager.bindProcessToNetwork(network)
                // Record the bound mobile network ID
                NetworkManager.getInstance().mMobileNetId = NetworkManager.getInstance().getNetworkId(network)
                if (bindSuccessful) {
                    Log.d(tag, context.getString(R.string.live_toast_bind_mobile_network_successful))
                    //call API
                    makeBasicAuthRequest(username!!,password!!)
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

    override fun onPause() {
        super.onPause()
        Log.d(tag, getString(R.string.live_toast_unbind_mobile_network))
        NetworkManager.getInstance().clearBindProcess(this)
    }

    override fun onResume() {
        super.onResume()
        //check if internet is available to perform the API request
        if (!isInternetAvailable(this)) {
            //use mobile network for api request
            exchangeNetToMobile(this.applicationContext)
        }else{
            makeBasicAuthRequest(username!!,password!!)
        }
    }
}