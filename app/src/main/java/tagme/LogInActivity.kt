package tagme


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
import com.example.tagme.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LogInActivity : AppCompatActivity() {
    private val REQUEST_PERMISSIONS_REQUEST_CODE = 1
    private val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    private var hasPermission = false

    private lateinit var usernameInput : EditText
    private lateinit var passwordInput : EditText
    private lateinit var errorText : TextView
    private lateinit var loginBtn : Button
    private lateinit var registerBtn : Button
    private lateinit var loginLayout : LinearLayout
    private lateinit var enableGpsLayout : LinearLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login_activity)
        loginLayout = findViewById(R.id.login_layout)
        enableGpsLayout = findViewById(R.id.enable_gps_layout)
        usernameInput = findViewById(R.id.username_input)
        passwordInput = findViewById(R.id.password_input)
        loginBtn = findViewById(R.id.login_btn)
        registerBtn = findViewById(R.id.register_btn)
        errorText = findViewById(R.id.error_message)

        val api = API.getInstance(applicationContext)
        CoroutineScope(Dispatchers.Main).launch {
            val connected = api.connectToServer()
            if (connected) {
                Log.d("Tagme_custom_log", "Connected to the server")
                if (api.token != null) {
                    CoroutineScope(Dispatchers.Main).launch {
                        val answer = api.loginToken()
                        if (answer != null) {
                            if (answer.getString("status") == "success") {
                                hideLoginShowGpsOverlay()
                                Log.d("Tagme_custom_log", "Logged in via token")
                            }
                        }
                    }
                }
            } else {
                Log.d("Tagme_custom_log", "Failed to connect to the server")
            }
        }
        loginBtn.setOnClickListener {
            val username = usernameInput.text.toString()
            val password = passwordInput.text.toString()
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
                CoroutineScope(Dispatchers.Main).launch {
                    val answer = api.loginUser(username, password)
                    if (answer != null) {
                        if (answer.getString("status") == "success") {
                            hideLoginShowGpsOverlay()
                        } else {
                            errorText.visibility = View.VISIBLE
                            errorText.text = answer.getString("message")
                        }

                    }
                }
            }
        }
        registerBtn.setOnClickListener {
            val username = usernameInput.text.toString()
            val password = passwordInput.text.toString()
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
                CoroutineScope(Dispatchers.Main).launch {
                    val answer = api.registerUser(username, password)
                    if (answer != null) {
                        if (answer.getString("status") == "success") {
                            hideLoginShowGpsOverlay()
                        } else {
                            errorText.visibility = View.VISIBLE
                            errorText.text = answer.getString("message")
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
                REQUEST_PERMISSIONS_REQUEST_CODE)
        } else{
            hasPermission = true
        }
    }
    private fun hideLoginShowGpsOverlay(){
        requestPermissionsIfNecessary(permissions)

        if (!isLocationEnabled() or !hasPermission) {
            loginLayout.visibility = View.GONE
            enableGpsLayout.visibility = View.VISIBLE
            findViewById<Button>(R.id.EnableLocationbtn).setOnClickListener {
                requestLocation()
            }
        } else {
            startActivity(Intent(this, MapActivity::class.java))
            finish()
        }
    }
}
