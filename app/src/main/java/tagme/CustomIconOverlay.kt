package tagme
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class CustomIconOverlay(
    context: Context,
    private var location: GeoPoint,
    private var speed: Float,
    private val drawable: Drawable,
    private var name: String,
    private val fontResId: Int
) : Overlay(context) {
    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 48f
        isAntiAlias = true
        typeface = ResourcesCompat.getFont(context, fontResId)
        strokeWidth = 2f
        style = Paint.Style.FILL_AND_STROKE
    }
    override fun draw(canvas: Canvas, mapView: MapView?, shadow: Boolean) {
        super.draw(canvas, mapView, shadow)

        val projection = mapView?.projection
        val point = projection?.toPixels(location, null)

        val rotation = -(mapView?.mapOrientation ?: 0f) // Negative sign because osmdroid uses clockwise rotation

        point?.let {
            canvas.save()
            canvas.rotate(rotation, it.x.toFloat(), it.y.toFloat())
            drawable.setBounds(it.x - drawable.intrinsicWidth / 2, it.y - drawable.intrinsicHeight / 2,
                it.x + drawable.intrinsicWidth / 2, it.y + drawable.intrinsicHeight / 2)
            drawable.draw(canvas)

            // Drawing speed and name texts next to the drawable
            val speedText = "${speed.toInt()}m/s"
            val nameText = name


            canvas.drawText(speedText, it.x.toFloat(), (it.y + drawable.intrinsicHeight / 2 + 25).toFloat(), textPaint)
            canvas.drawText(nameText, it.x.toFloat() - textPaint.measureText(nameText) / 2, (it.y - drawable.intrinsicHeight / 2 + 5).toFloat(), textPaint)

            canvas.restore()
        }
    }

    fun setLocation(newLocation: GeoPoint) {
        location = newLocation
    }
    fun setSpeed(newSpeed: Float) {
        speed = newSpeed
    }
}
