package com.example.notzenly

import android.os.Bundle
import kotlinx.coroutines.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.notzenly.databinding.ActivityMainBinding
import java.time.LocalDateTime

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        CoroutineScope(Dispatchers.Main).launch {
            addUserTracking()
        }
    }

    private suspend fun addUserTracking() {
        // Perform database operation in a background thread
        withContext(Dispatchers.IO) {
            val userId = 1
            val startTime = LocalDateTime.now()
            val endTime = startTime.plusHours(1) // Adding an hour to the start time
            val trackedRouteLength = 10.5 // in kilometers
            val averageRouteSpeed = 30.0 // in km/h
            val trackColor = "#FF0000" // Example color code

            DatabaseManager.addUserTracking(userId, startTime, endTime, trackedRouteLength, averageRouteSpeed, trackColor)
        }
    }
}