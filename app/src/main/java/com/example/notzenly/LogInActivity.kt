package com.example.notzenly


import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

//import com.example.notzenly.RestAPI

class LogInActivity : AppCompatActivity() {
    private val REQUEST_PERMISSIONS_REQUEST_CODE = 1
    private val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    private var hasPermission = false
    private var isLoggedIn = false //это надо будет поменять (хранить в кеше sessID?)

    lateinit var usernameInput : EditText
    lateinit var passwordInput : EditText
    lateinit var errorText : TextView
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
            errorText = findViewById(R.id.error_message)

            val restApi = RestAPI()
            loginBtn.setOnClickListener {
                hideLoginShowGpsOverlay() // удалить это!!
                val username = usernameInput.text.toString()
                val password = passwordInput.text.toString()
                //Проверка, пусты ли поля:
                if (username == "" || password == "") {
                    errorText.text = "Введите логин и пароль"
                    errorText.visibility = View.VISIBLE
                    if (password == "") {
                        passwordInput.setHintTextColor(Color.RED)
                    }
                    if (username == "") {
                        usernameInput.setHintTextColor(Color.RED)
                    }
                } else {
                    //Тут - логика входа через REST API
                    CoroutineScope(Dispatchers.Main).launch {
                        val result = restApi.loginUser(username, password)
                        // Handle the result here
                        if (result != null) { //null = login succesfull
                            Log.d("com.example.notzenly", result)
                        } else {
                            //Если удача, то:
                            hideLoginShowGpsOverlay()
                        }
                    }
                }
            }
            RegisterBtn.setOnClickListener {
                val username = usernameInput.text.toString()
                val password = passwordInput.text.toString()
                //Проверка, пусты ли поля:
                if (username == "" || password == "") {
                    errorText.text = "Введите логин и пароль"
                    errorText.visibility = View.VISIBLE
                    if (password == "") {
                        passwordInput.setHintTextColor(Color.RED)
                    }
                    if (username == "") {
                        usernameInput.setHintTextColor(Color.RED)
                    }
                } else {
                    //Тут - логика входа через REST API
                    CoroutineScope(Dispatchers.Main).launch {
                        val result = restApi.registerUser(username, password)
                        // Handle the result here
                        if (result != null) { //null = registration succesfull
                            Log.d("com.example.notzenly", result)
                        } else {
                            //Если удача, то:
                            hideLoginShowGpsOverlay()
                        }
                    }
                }
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
    }

    private fun requestPermissionsIfNecessary(permissions: Array<String>) {
        val permissionsToRequest = ArrayList<String>()
        permissions.forEach { permission ->
            if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED) {
                // Permission is not granted
                permissionsToRequest.add(permission)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_PERMISSIONS_REQUEST_CODE)
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
