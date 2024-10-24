package com.tagme.domain.models

import com.tagme.presentation.views.CustomIconOverlay

data class CustomIconOverlayEvent(
    val overlay: CustomIconOverlay,
    val id: Int,
    val isCenterTargetUser: Boolean,
    val withZoom: Boolean
)
