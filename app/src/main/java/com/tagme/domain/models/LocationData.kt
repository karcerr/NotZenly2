package com.tagme.domain.models

import java.sql.Timestamp

data class LocationData(
    var latitude: Double,
    var longitude: Double,
    var accuracy: Double,
    var speed: Float,
    var timestamp: Timestamp?
)