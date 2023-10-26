package com.example.testapp.activity

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class StartActivity : AppCompatActivity() {

    private val tag = "com.example.testapp." + this::class.simpleName
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPreferences = getSharedPreferences("LoginPreferences", Context.MODE_PRIVATE)
        val sharedUser = sharedPreferences.getString("username", null)

        //if sharedUser is checked then we can go directly to ServerListActivity
        if (sharedUser != null) {
            // User is logged in, navigate to the desired activity
            val intent = Intent(this, UserActivity::class.java)
            startActivity(intent)
            finish()
        }else {
            // User is not logged in, proceed with the login/registration flow
            //val intent = Intent(this, LoginActivity::class.java)
            val intent = Intent(this, TestActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}