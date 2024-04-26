package tagme
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import androidx.core.content.res.ResourcesCompat
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.pow
import kotlin.math.sqrt

class CustomIconOverlay(
    private val context: Context,
    private var location: GeoPoint,
    private var speed: Float,
    private val drawable: Drawable,
    private var name: String,
    private var userId: Int,
    private val fontResId: Int,
    private val clickListener: ((CustomIconOverlay) -> Unit)?

) : Overlay(context) {
    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 48f
        isAntiAlias = true
        typeface = ResourcesCompat.getFont(context, fontResId)
        strokeWidth = 2f
        style = Paint.Style.FILL_AND_STROKE
    }


    override fun onSingleTapConfirmed(e: MotionEvent?, mapView: MapView?): Boolean {
        val projection = mapView?.projection
        val point = projection?.toPixels(location, null)

        point?.let {
            val x = e?.x ?: 0f
            val y = e?.y ?: 0f

            val distance = sqrt((x - it.x).toDouble().pow(2.0) + (y - it.y).toDouble().pow(2.0))

            val iconRadius = (drawable.intrinsicWidth * calculateScaleFactor(drawable) / 2).toFloat()

            if (distance <= iconRadius) {
                clickListener?.invoke(this)
                return true
            }
        }

        return super.onSingleTapConfirmed(e, mapView)
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
            val strokePaint = Paint().apply {
                style = Paint.Style.STROKE
                color = Color.BLACK
                strokeWidth = 6.0F
            }
            val strokeRect = RectF(
                (it.x - scaledWidth / 2).toFloat(),
                (it.y - scaledHeight / 2).toFloat(),
                (it.x + scaledWidth / 2).toFloat(),
                (it.y + scaledHeight / 2).toFloat()
            )
            canvas.drawRoundRect(strokeRect, scaledWidth * 0.25f, scaledWidth * 0.25f, strokePaint)

            val cornerSize = scaledWidth * 0.25f
            val path = Path().apply {
                addRoundRect(
                    RectF(it.x.toFloat() - scaledWidth / 2, it.y.toFloat() - scaledHeight / 2,
                        it.x.toFloat() + scaledWidth / 2, it.y.toFloat() + scaledHeight / 2),
                    cornerSize, cornerSize, Path.Direction.CW
                )
                close()
            }

            canvas.clipPath(path)

            drawable.draw(canvas)

            canvas.restore()

            canvas.save()
            canvas.rotate(rotation, it.x.toFloat(), it.y.toFloat())

            val speedText = "${speed.toInt()}m/s"
            val nameText = name

            if (speed.toInt() != 0)
                canvas.drawText(speedText, it.x.toFloat(), (it.y + scaledHeight / 2 + 45).toFloat(), textPaint)
            canvas.drawText(nameText, it.x.toFloat() - textPaint.measureText(nameText) / 2, (it.y - scaledHeight / 2 - 10).toFloat(), textPaint)

            canvas.restore()
        }
    }

    private fun calculateScaleFactor(drawable: Drawable): Float {
        val placeholderWidth = dpToPx(64f)
        val placeholderHeight = dpToPx(64f)

        return if (placeholderWidth != 0 && placeholderHeight != 0) {
            (placeholderWidth.toFloat() / drawable.intrinsicWidth).coerceAtMost(placeholderHeight.toFloat() / drawable.intrinsicHeight)
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
    fun getLocation(): GeoPoint {
        return location
    }
    fun getUserId(): Int {
        return userId
    }
}
