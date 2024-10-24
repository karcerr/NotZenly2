package com.tagme.domain.models

import java.sql.Timestamp

data class GeoStoryData(
    val geoStoryId: Int,
    var creatorData: UserData,
    val pictureId: Int,
    var privacy: String?,
    var views: Int?,
    val latitude: Double,
    val longitude: Double,
    var timestamp: Timestamp,
    var viewed: Boolean
)