package com.tagme


import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.os.Build
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
import com.vk.id.onetap.xml.OneTap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch

class LogInActivity : AppCompatActivity() {

    private val permissions = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
    else
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    private var hasPermission = false
    private var isAuthorized = false

    private lateinit var usernameInput : EditText
    private lateinit var passwordInput : EditText
    private lateinit var errorText : TextView
    private lateinit var loginBtn : Button
    private lateinit var registerBtn : Button
    private lateinit var vkidButton : OneTap
    private lateinit var loginLayout : LinearLayout
    private lateinit var loadingLayout : LinearLayout
    private lateinit var enableGpsLayout : LinearLayout
    private lateinit var loadingState : TextView
    companion object{
        private const val REQUEST_PERMISSIONS_REQUEST_CODE = 1
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login_activity)
        loginLayout = findViewById(R.id.login_layout)
        loadingLayout = findViewById(R.id.loading_layout)
        enableGpsLayout = findViewById(R.id.enable_gps_layout)
        usernameInput = findViewById(R.id.username_input)
        passwordInput = findViewById(R.id.password_input)
        loginBtn = findViewById(R.id.login_btn)
        registerBtn = findViewById(R.id.register_btn)
        vkidButton = findViewById(R.id.vkidButton)
        errorText = findViewById(R.id.error_message)
        loadingState = findViewById(R.id.current_loading_state)

        val api = API.getInstance(applicationContext)
        CoroutineScope(Dispatchers.Main).launch {
            connectToServer(api)
        }

        vkidButton.setCallbacks(
            onAuth = { accessToken ->
                val token = accessToken.token
                Log.d("Tagme_VK", "Access Token: $token")
                CoroutineScope(Dispatchers.Main).launch {
                    loadingLayout.visibility = View.VISIBLE
                    loginLayout.visibility = View.GONE
                    errorText.visibility = View.GONE
                    val answer = api.authVK(token)
                    if (answer != null) {
                        if (answer.getString("status") == "success") {
                            api.getMyDataFromWS()
                            hideLoginShowGpsOverlay()
                            isAuthorized = true
                        } else {
                            loadingLayout.visibility = View.GONE
                            loginLayout.visibility = View.VISIBLE
                            errorText.visibility = View.VISIBLE
                            errorText.text = answer.getString("message")
                        }
                    }
                }
            }
        )

        loginBtn.setOnClickListener {
            val username = usernameInput.text.toString()
            val password = passwordInput.text.toString()
            if (username == "" || password == "") {
                errorText.text = getString(R.string.empty_login_password)
                errorText.visibility = View.VISIBLE
                if (password == "") {
                    passwordInput.setHintTextColor(Color.RED)
                }
                if (username == "") {
                    usernameInput.setHintTextColor(Color.RED)
                }
            } else {
                CoroutineScope(Dispatchers.Main).launch {
                    val answer = api.loginUser(username, password)
                    if (answer != null) {
                        if (answer.getString("status") == "success") {
                            api.getMyDataFromWS()
                            hideLoginShowGpsOverlay()
                            isAuthorized = true
                        } else {

                            errorText.visibility = View.VISIBLE
                            errorText.text = getString(R.string.error_incorrect_login_or_pass)
                        }

                    }
                }
            }
        }
        registerBtn.setOnClickListener {
            val username = usernameInput.text.toString()
            val password = passwordInput.text.toString()
            if (username == "" || password == "") {
                errorText.text = getString(R.string.empty_login_password)
                errorText.visibility = View.VISIBLE
                if (password == "") {
                    passwordInput.setHintTextColor(Color.RED)
                }
                if (username == "") {
                    usernameInput.setHintTextColor(Color.RED)
                }
            } else {
                CoroutineScope(Dispatchers.Main).launch {
                    val answer = api.registerUser(username, password)
                    if (answer != null) {
                        if (answer.getString("status") == "success") {
                            api.getMyDataFromWS()
                            hideLoginShowGpsOverlay()
                            isAuthorized = true
                        } else {
                            errorText.visibility = View.VISIBLE
                            val errorMessages = getLocalizedErrorMessages(answer.getString("message"))
                            Log.d("Tagme_login", errorMessages.toString())
                            val combinedErrorMessage = errorMessages.joinToString("\n")
                            errorText.text = combinedErrorMessage
                        }
                    }
                }
            }
        }
    }

    private fun requestLocation() {
        requestPermissionsIfNecessary(permissions)
        if (hasPermission) {
            if (isLocationEnabled()) {
                startActivity(Intent(this, MapActivity::class.java))
                finish()
            } else {
                showLocationSettings()
            }
        }
    }
    private fun checkForPermAndLocation() {
        if (hasPermission and isLocationEnabled()) {
            startActivity(Intent(this, MapActivity::class.java))
            finish()
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
                permissionsToRequest.add(permission)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_PERMISSIONS_REQUEST_CODE
            )
        } else{
            hasPermission = true
        }
    }
    private fun hideLoginShowGpsOverlay(){
        loadingState.text = getString(R.string.loading_map)
        requestPermissionsIfNecessary(permissions)

        if (!isLocationEnabled() or !hasPermission) {
            loginLayout.visibility = View.GONE
            loadingLayout.visibility = View.GONE
            enableGpsLayout.visibility = View.VISIBLE
            findViewById<Button>(R.id.EnableLocationbtn).setOnClickListener {
                requestLocation()
            }
        } else {
            startActivity(Intent(this, MapActivity::class.java))
            finish()
        }
    }
    private fun getLocalizedErrorMessages(inputError: String): List<String> {
        val errorMessages = mutableListOf<String>()
        val errors = inputError.replace("input_error:", "").trim().split(", ")

        errors.forEach { error ->
            when (error) {
                "too short" -> errorMessages.add(getString(R.string.error_too_short))
                "no capital" -> errorMessages.add(getString(R.string.error_no_capital))
                "no digits" -> errorMessages.add(getString(R.string.error_no_digits))
                "login too long" -> errorMessages.add(getString(R.string.error_login_too_long))
                "password too long" -> errorMessages.add(getString(R.string.error_password_too_long))
                "username already exists" -> errorMessages.add(getString(R.string.error_user_already_exists))
            }
        }
        return errorMessages
    }

    override fun onResume() {
        super.onResume()
        if (isAuthorized) {
            checkForPermAndLocation()
        }
    }
    private suspend fun connectToServer(api: API) {
        while (true) {
            loadingState.text = getString(R.string.loading_server_connect)
            val future = api.connectToServer(applicationContext)
            val connected = future.await()
            if (connected) {
                val myToken = api.myToken
                if (myToken != null) {
                    val answer = api.loginToken()
                    if (answer != null) {
                        if (answer.getString("status") == "success") {
                            api.getMyDataFromWS()
                            hideLoginShowGpsOverlay()
                            isAuthorized = true
                            break
                        } else {
                            loadingLayout.visibility = View.GONE
                            loginLayout.visibility = View.VISIBLE
                            break
                        }
                    }
                } else {
                    loadingLayout.visibility = View.GONE
                    loginLayout.visibility = View.VISIBLE
                    break
                }
            } else {
                loadingState.text = getString(R.string.loading_server_reconnect)
                Log.d("Tagme_", "Failed to connect to the server")
                delay(2000)
            }
        }
    }
}
