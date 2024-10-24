package com.tagme.presentation.views.activities

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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tagme.R
import com.tagme.presentation.viewmodels.LoginViewModel
import com.vk.id.onetap.xml.OneTap
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONObject

@AndroidEntryPoint
class LogInActivity : AppCompatActivity() {
    private val loginViewModel: LoginViewModel by viewModels()

    private val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
    else
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private var hasPermission = false
    private var isAuthorized = false

    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var errorText: TextView
    private lateinit var loginBtn: Button
    private lateinit var registerBtn: Button
    private lateinit var vkidButton: OneTap
    private lateinit var loginLayout: LinearLayout
    private lateinit var loadingLayout: LinearLayout
    private lateinit var enableGpsLayout: LinearLayout
    private lateinit var loadingState: TextView

    companion object {
        private const val REQUEST_PERMISSIONS_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login_activity)

        initViews()

        checkForPermissions(permissions)

        observeViewModel()

        loginViewModel.connectToServer()

        vkidButton.setCallbacks(
            onAuth = { accessToken ->
                val token = accessToken.token
                Log.d("Tagme_VK", "Access Token: $token")
                loginViewModel.authVK(token)
            }
        )

        loginBtn.setOnClickListener {
            handleLoginClick()
        }

        registerBtn.setOnClickListener {
            handleRegisterClick()
        }
    }

    private fun initViews() {
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
    }

    private fun observeViewModel() {
        loginViewModel.loginResult.observe(this) { result ->
            handleLoginResult(result)
        }

        loginViewModel.loginTokenResult.observe(this) { result ->
            handleLoginTokenResult(result)
        }

        loginViewModel.registerResult.observe(this) { result ->
            handleRegisterResult(result)
        }

        loginViewModel.vkAuthResult.observe(this) { result ->
            handleVkAuthResult(result)
        }

        loginViewModel.connectionStatus.observe(this) { result ->
            handleConnectionResult(result)
        }
    }

    private fun handleLoginClick() {
        val username = usernameInput.text.toString()
        val password = passwordInput.text.toString()

        if (username.isEmpty() || password.isEmpty()) {
            displayError(getString(R.string.empty_login_password))
            if (password.isEmpty()) passwordInput.setHintTextColor(Color.RED)
            if (username.isEmpty()) usernameInput.setHintTextColor(Color.RED)
        } else {
            showLoading(getString(R.string.loading_server_await))
            loginViewModel.login(username, password)
        }
    }

    private fun handleRegisterClick() {
        val username = usernameInput.text.toString()
        val password = passwordInput.text.toString()

        if (username.isEmpty() || password.isEmpty()) {
            displayError(getString(R.string.empty_login_password))
            if (password.isEmpty()) passwordInput.setHintTextColor(Color.RED)
            if (username.isEmpty()) usernameInput.setHintTextColor(Color.RED)
        } else {
            showLoading(getString(R.string.loading_server_await))
            loginViewModel.register(username, password)
        }
    }

    private fun handleLoginResult(result: JSONObject?) {
        if (result?.getString("status") == "success") {
            hideLoginShowGpsOverlay()
            isAuthorized = true
        } else {
            displayError(result?.getString("message") ?: getString(R.string.error_incorrect_login_or_pass))
        }
    }
    private fun handleLoginTokenResult(result: JSONObject?) {
        if (result?.getString("status") == "success") {
            hideLoginShowGpsOverlay()
            isAuthorized = true
        } else {
            hideLoadingShowAuth()
        }
    }

    private fun handleRegisterResult(result: JSONObject?) {
        if (result?.getString("status") == "success") {
            hideLoginShowGpsOverlay()
            isAuthorized = true
        } else {
            val errorMessages = result?.let { getLocalizedErrorMessages(it.getString("message")) }
            val combinedErrorMessage = errorMessages?.joinToString("\n")
            displayError(combinedErrorMessage?: getString(R.string.something_went_wrong))
        }
    }

    private fun handleVkAuthResult(result: JSONObject?) {
        if (result?.getString("status") == "success") {
            hideLoginShowGpsOverlay()
            isAuthorized = true
        } else {
            displayError(result?.getString("message") ?: getString(R.string.vk_error))
        }
    }
    private fun handleConnectionResult(result: Boolean) {
        if (result) {
            loginViewModel.loginToken()
        } else {
            showLoading(getString(R.string.loading_server_reconnect))
            loginViewModel.connectToServer()
        }
    }

    private fun displayError(message: String) {
        loadingLayout.visibility = View.GONE
        loginLayout.visibility = View.VISIBLE
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    private fun showLoading(message: String) {
        loadingState.text = message
        loginLayout.visibility = View.GONE
        loadingLayout.visibility = View.VISIBLE
    }
    private fun hideLoadingShowAuth() {
        loginLayout.visibility = View.VISIBLE
        loadingLayout.visibility = View.GONE
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
    private fun hideLoginShowGpsOverlay() {
        loginViewModel.getMyDataWS()
        loadingLayout.visibility = View.GONE
        if (!isLocationEnabled() || !hasPermission) {
            enableGpsLayout.visibility = View.VISIBLE
            findViewById<Button>(R.id.EnableLocationbtn).setOnClickListener {
                requestLocation()
            }
        } else {
            loadingLayout.visibility = View.VISIBLE
            loadingState.text = getString(R.string.loading_map)
            startActivity(Intent(this, MapActivity::class.java))
            finish()
        }
    }
    private fun checkForPermissions(permissions: Array<String>) {
        val permissionsToRequest = ArrayList<String>()
        permissions.forEach { permission ->
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }
        hasPermission = permissionsToRequest.isEmpty()
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_PERMISSIONS_REQUEST_CODE
            )
        }
    }

    private fun requestLocation() {
        requestPermissionsIfNecessary(permissions)
        if (hasPermission) {
            if (isLocationEnabled()) {
                enableGpsLayout.visibility = View.GONE
                loadingState.text = getString(R.string.loading_map)
                loadingLayout.visibility = View.VISIBLE
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
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_PERMISSIONS_REQUEST_CODE
            )
        } else {
            hasPermission = true
        }
    }
    override fun onResume() {
        super.onResume()
        if (isAuthorized) {
            hideLoginShowGpsOverlay()
        }
    }
}
