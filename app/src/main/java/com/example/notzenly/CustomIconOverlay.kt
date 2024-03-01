package com.example.notzenly
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class CustomIconOverlay(context: Context, private val location: GeoPoint, private val drawable: Drawable) : Overlay(context) {
    override fun draw(canvas: Canvas, mapView: MapView?, shadow: Boolean) {
        super.draw(canvas, mapView, shadow)

        // Convert the geographic coordinates to screen coordinates
        val projection = mapView?.projection
        val point = projection?.toPixels(location, null)

        // Calculate the current rotation angle of the map view
        val rotation = -(mapView?.mapOrientation ?: 0f) // Negative sign because osmdroid uses clockwise rotation

        // Draw the drawable at the specified location
        point?.let {
            canvas.save()
            canvas.rotate(rotation, it.x.toFloat(), it.y.toFloat())
            drawable.setBounds(it.x - drawable.intrinsicWidth / 2, it.y - drawable.intrinsicHeight / 2,
                it.x + drawable.intrinsicWidth / 2, it.y + drawable.intrinsicHeight / 2)
            drawable.draw(canvas)
            canvas.restore()
        }
    }
}
