package com.example.testapp.activity

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.testapp.R
import com.example.testapp.databinding.ActivityLoginBinding
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

class LoginActivity : AppCompatActivity() {

    private val tag = "com.example.testapp." + this::class.simpleName
    private lateinit var binding : ActivityLoginBinding
    private lateinit var btnLogin : Button
    private lateinit var etUsername : EditText
    private lateinit var etPassword : EditText
    private val url = "https://develop.ewlab.di.unimi.it/telecyclette/api/connections"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        btnLogin = binding.loginButton
        etUsername = binding.username
        etPassword = binding.password

        //check if internet is available to perform the API request
        if (!isInternetAvailable(this)) {
            //use mobile network for api request
            NetworkManager.getInstance().exchangeNetToMobile(this.applicationContext)
        }

        //disable and re-enable the button if username and password edit texts are empty
        val textWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val username = etUsername.text.toString().trim()
                val password = etPassword.text.toString().trim()

                // Enable the button only if both username and password are not empty
                btnLogin.isEnabled = username.isNotEmpty() && password.isNotEmpty()
            }
        }
        etUsername.addTextChangedListener(textWatcher)
        etPassword.addTextChangedListener(textWatcher)

        btnLogin.setOnClickListener{
            if (btnLogin.isEnabled) {
                val username = etUsername.text.toString().trim()
                val password = etPassword.text.toString().trim()

                if (isInternetAvailable(this)){
                    //check user and password with webAPI
                    makeBasicAuthRequest(username,password)
                }else{
                    Toast.makeText(this, getString(R.string.no_connection), Toast.LENGTH_SHORT).show()
                }

            }

        }
    }

    private fun makeBasicAuthRequest(username: String, password: String) {

        val client = OkHttpClient()
        val credential = Credentials.basic(username, password)
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", credential)         //base auth
            .build()

        client.newCall(request).enqueue(object : Callback{
            override fun onFailure(call: Call, e: IOException) {
                Log.d(tag,"Request failed with code $e")
            }

            override fun onResponse(call: Call, response: Response) {

                if(response.code == 204){
                    Log.d(tag,"Request code ${response.code}: ${getString(R.string.code204)}")
                    runOnUiThread {
                        Toast.makeText(applicationContext, getString(R.string.code204), Toast.LENGTH_SHORT).show()
                    }
                }else{

                    when (response.code) {
                        200 -> {    //result returned correctly
                            Log.d(tag,"Request code ${response.code}: ${getString(R.string.code200)}")

                            val responseBody = response.body!!.string()
                            // Parse the JSON response into your User data model
                            val user = parseJsonToUserData(responseBody)
                            Log.d(tag, user.toString())

                            // Save the session token upon successful login
                            val sharedPreferences = getSharedPreferences("LoginPreferences", Context.MODE_PRIVATE)
                            val editor = sharedPreferences.edit()
                            editor.putString("username", username)
                            editor.putString("password", password)
                            editor.apply()

                            //create the intent passing users list
                            val intent = Intent(this@LoginActivity, UserActivity::class.java)
                            intent.putExtra("user", user)
                            startActivity(intent)
                        }
                        401 -> {    //verify username and password
                            Log.d(tag,"Request code ${response.code}: ${getString(R.string.code401)}")
                            runOnUiThread {
                                Toast.makeText(applicationContext, getString(R.string.code401), Toast.LENGTH_SHORT).show()
                            }
                            //delete shared preferences if an error occurred
                            val sharedPreferences = getSharedPreferences("LoginPreferences", Context.MODE_PRIVATE)
                            val editor = sharedPreferences.edit()
                            editor.remove("username")
                            editor.remove("password")
                            editor.apply()
                        }
                        403 -> {    //client not authorized
                            Log.d(tag,"Request code ${response.code}: ${getString(R.string.code403)}")
                            runOnUiThread {
                                Toast.makeText(applicationContext, getString(R.string.code403), Toast.LENGTH_SHORT).show()
                            }
                            //delete shared preferences if an error occurred
                            val sharedPreferences = getSharedPreferences("LoginPreferences", Context.MODE_PRIVATE)
                            val editor = sharedPreferences.edit()
                            editor.remove("username")
                            editor.remove("password")
                            editor.apply()
                        }
                        422 -> {    //missing data (credential)
                            Log.d(tag,"Request code ${response.code}: ${getString(R.string.code422)}")
                            runOnUiThread {
                                Toast.makeText(applicationContext, getString(R.string.code422), Toast.LENGTH_SHORT).show()
                            }
                            //delete shared preferences if an error occurred
                            val sharedPreferences = getSharedPreferences("LoginPreferences", Context.MODE_PRIVATE)
                            val editor = sharedPreferences.edit()
                            editor.remove("username")
                            editor.remove("password")
                            editor.apply()
                        }
                        else -> {
                            Log.d(tag,"Request code ${response.code}: ${response.message}")
                            runOnUiThread {
                                Toast.makeText(applicationContext, response.message, Toast.LENGTH_SHORT).show()
                            }
                            //delete shared preferences if an error occurred
                            val sharedPreferences = getSharedPreferences("LoginPreferences", Context.MODE_PRIVATE)
                            val editor = sharedPreferences.edit()
                            editor.remove("username")
                            editor.remove("password")
                            editor.apply()
                        }
                    }
                }
            }
        })
    }
    fun parseJsonToUserData(json: String): User {
        // Implement JSON parsing logic here and return a User object
        val jsonObject = JSONObject(json)
        Log.d(tag, jsonObject.toString())
        return User(
            jsonObject.getString("retirementHomeID"),
            jsonObject.getString("ipAddress"),
            jsonObject.getString("port")
        )
    }

    private fun isInternetAvailable(context: Context): Boolean {
        var isOnline = false
        try {
            val manager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) // active network is always the wifi if connected
            val bounderNetwork = manager.getNetworkCapabilities(manager.boundNetworkForProcess) // bounded network could be the mobile network if Binded or could be null
            isOnline = (capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) || (bounderNetwork != null && bounderNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return isOnline
    }

}