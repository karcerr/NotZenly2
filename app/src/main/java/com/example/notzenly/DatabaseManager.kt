package com.example.notzenly
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.LocalDateTime

object DatabaseManager {
    private const val DATABASE_URL = "jdbc:postgresql://141.8.193.201:5432/db_vegetable"
    private const val DATABASE_USER = "vegetable"
    private const val DATABASE_PASSWORD = "2kn39fjs"

    fun addUserTracking(userId: Int, startTime: LocalDateTime, endTime: LocalDateTime, trackedRouteLength: Double, averageRouteSpeed: Double, trackColor: String) {
        var connection: Connection? = null
        try {
            connection = DriverManager.getConnection(DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD)
            val statement = connection.prepareStatement("INSERT INTO user_tracking (user_id, start_time, end_time, tracked_route_length, average_route_speed, track_color) VALUES (?, ?, ?, ?, ?, ?)")
            statement.setInt(1, userId)
            statement.setTimestamp(2, Timestamp.valueOf(startTime.toString()))
            statement.setTimestamp(3, Timestamp.valueOf(endTime.toString()))
            statement.setDouble(4, trackedRouteLength)
            statement.setDouble(5, averageRouteSpeed)
            statement.setString(6, trackColor)
            statement.executeUpdate()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connection?.close()
        }
    }
}