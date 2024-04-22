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
    private val context: Context,
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

        val rotation = -(mapView?.mapOrientation ?: 0f)

        point?.let {
            canvas.save()
            canvas.rotate(rotation, it.x.toFloat(), it.y.toFloat())

            val scaleFactor = calculateScaleFactor(drawable)

            val scaledWidth = (drawable.intrinsicWidth * scaleFactor).toInt()
            val scaledHeight = (drawable.intrinsicHeight * scaleFactor).toInt()
            drawable.setBounds(it.x - scaledWidth / 2, it.y - scaledHeight / 2,
                it.x + scaledWidth / 2, it.y + scaledHeight / 2)
            drawable.draw(canvas)

            val speedText = "${speed.toInt()}m/s"
            val nameText = name


            canvas.drawText(speedText, it.x.toFloat(), (it.y + scaledHeight / 2 + 45).toFloat(), textPaint)
            canvas.drawText(nameText, it.x.toFloat() - textPaint.measureText(nameText) / 2, (it.y - scaledHeight / 2 - 10).toFloat(), textPaint)


            canvas.restore()
        }

    }
    private fun calculateScaleFactor(drawable: Drawable): Float {
        val placeholderWidth = dpToPx(64f)
        val placeholderHeight = dpToPx(64f)

        return if (placeholderWidth != 0 && placeholderHeight != 0) {
            Math.min(placeholderWidth.toFloat() / drawable.intrinsicWidth, placeholderHeight.toFloat() / drawable.intrinsicHeight)
        } else {
            1f
        }
    }

    private fun dpToPx(dp: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }
    fun setLocation(newLocation: GeoPoint) {
        location = newLocation
    }
    fun setSpeed(newSpeed: Float) {
        speed = newSpeed
    }

}
