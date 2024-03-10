package com.example.notzenly


import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


class LogInActivity : AppCompatActivity() {
    private val REQUEST_PERMISSIONS_REQUEST_CODE = 1
    private val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    private var hasPermission = false
    private var isLoggedIn = false //это надо будет поменять (хранить в кеше sessID?)

    lateinit var usernameInput : EditText
    lateinit var passwordInput : EditText
    lateinit var loginBtn : Button
    lateinit var RegisterBtn : Button
    lateinit var loginLayout : LinearLayout
    lateinit var enableGpsLayout : LinearLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login_activity)
        loginLayout = findViewById(R.id.login_layout)
        enableGpsLayout = findViewById(R.id.enable_gps_layout)
        if(!isLoggedIn) {
            usernameInput = findViewById(R.id.username_input)
            passwordInput = findViewById(R.id.password_input)
            loginBtn = findViewById(R.id.login_btn)
            RegisterBtn = findViewById(R.id.register_btn)


            loginBtn.setOnClickListener {
                val username = usernameInput.text.toString()
                val password = usernameInput.text.toString()
                //Тут будет логика входа через REST API
                //...
                //Если удача, то:
                hideLoginShowGpsOverlay()
            }
            RegisterBtn.setOnClickListener {
                val username = usernameInput.text.toString()
                val password = usernameInput.text.toString()
                //Тут будет логика регистрации через REST API
                //...
                //Если удача, то:
                hideLoginShowGpsOverlay()
            }
        } else {
            hideLoginShowGpsOverlay()
        }
    }


    private fun requestLocation() {
        requestPermissionsIfNecessary(permissions)
        // If permissions granted, check location status
        if (hasPermission) {
            if (isLocationEnabled()) {
                startActivity(Intent(this, MapActivity::class.java))
                finish()
            } else {
                showLocationSettings()
            }
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    private fun showLocationSettings() {
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        Log.d("com.example.notzenly", "clicked?")
    }

    private fun requestPermissionsIfNecessary(permissions: Array<String>) {
        val permissionsToRequest = ArrayList<String>();
        permissions.forEach { permission ->
            if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED) {
                // Permission is not granted
                permissionsToRequest.add(permission);
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_PERMISSIONS_REQUEST_CODE);
        } else{
            hasPermission = true
        }
    }
    private fun hideLoginShowGpsOverlay(){
        loginLayout.visibility = View.GONE
        enableGpsLayout.visibility = View.VISIBLE

        //Handling Permissions:
        requestPermissionsIfNecessary(permissions)

        if (!isLocationEnabled() or !hasPermission) {
            findViewById<Button>(R.id.EnableLocationbtn).setOnClickListener {
                requestLocation()
            }
        } else {
            startActivity(Intent(this, MapActivity::class.java))
            finish()
        }
    }
}
