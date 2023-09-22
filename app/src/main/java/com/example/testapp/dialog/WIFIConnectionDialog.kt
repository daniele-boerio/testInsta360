package com.example.testapp.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.example.testapp.R

@SuppressLint("InflateParams")
class WIFIConnectionDialog (context: Context) : AlertDialog(context) {
    private val tag = "com.example.testapp." + this::class.simpleName
    init {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val contentView = inflater.inflate(R.layout.dialog_wifi_connection, null, false)
        setView(contentView)

        //disable dismiss with back and clicking outside
        this.setCancelable(false)
        this.setCanceledOnTouchOutside(false)
    }
}