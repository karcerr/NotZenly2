package tagme

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.util.Log
import android.view.MotionEvent
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import com.example.tagme.R
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class CustomIconOverlay(
    private val context: Context,
    private var location: GeoPoint,
    private var speed: Float?,
    private var drawable: Drawable,
    private var name: String?,
    private val userId: Int,
    private val storyId: Int,
    private val fontResId: Int,
    private val clickListener: ((CustomIconOverlay) -> Unit)?
) : Overlay(context) {
    private var intersectedOverlays: MutableList<Pair<Int, Int>> = mutableListOf()
    private var visible = true
    private var closestVisibleOverlay : CustomIconOverlay? = null

    private var overlayScale = calculateScaleFactor(drawable)
    var mapView: MapView? = null
    private val textPaintBlack = Paint().apply {
        color = Color.BLACK
        textSize = 48f
        isAntiAlias = true
        typeface = ResourcesCompat.getFont(context, fontResId)
        strokeWidth = 2f
        style = Paint.Style.FILL_AND_STROKE
    }

    private val executor: Executor = Executors.newSingleThreadExecutor()
    init {
        if (storyId != 0) {
            blurDrawable(drawable, 25f, executor) { blurredDrawable ->
                drawable = blurredDrawable
                mapView?.invalidate()
            }
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent?, mapView: MapView?): Boolean {
        if (!visible) return super.onSingleTapConfirmed(e, mapView)
        val projection = mapView?.projection
        val point = projection?.toPixels(location, null)

        point?.let {
            val x = e?.x ?: 0f
            val y = e?.y ?: 0f

            val distance = sqrt((x - it.x).toDouble().pow(2.0) + (y - it.y).toDouble().pow(2.0))

            val iconRadius = (drawable.intrinsicWidth * overlayScale / 2)

            if (distance <= iconRadius) {
                Log.d("Tagme_icons", intersectedOverlays.toString())
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
        val intersectCircleRadius = 40f
        point?.let { currentPoint ->
            var intersected = false
            var minDistanceToVisible = Double.MAX_VALUE
            var closestVisibleOverlayTemp: CustomIconOverlay? = null

            mapView.overlays?.forEach { overlay ->
                if (overlay is CustomIconOverlay && overlay.visible && doOverlaysIntersect(this, overlay, currentPoint)) {
                    val overlayPoint = projection.toPixels(overlay.location, null)
                    val distance = sqrt(
                        (currentPoint.x - overlayPoint.x).toDouble().pow(2.0) +
                                (currentPoint.y - overlayPoint.y).toDouble().pow(2.0)
                    )
                    if (distance < minDistanceToVisible) {
                        minDistanceToVisible = distance
                        closestVisibleOverlayTemp = overlay
                    }
                    intersected = true
                }
            }

            visible = !intersected
            if (closestVisibleOverlay != null) {
                closestVisibleOverlay!!.intersectedOverlays.remove(userId to storyId)
                closestVisibleOverlay = null
            }
            if (intersected) {
                closestVisibleOverlay = closestVisibleOverlayTemp
                closestVisibleOverlay!!.intersectedOverlays.add(userId to storyId)
            }
        }

        if(!visible) return
        point?.let {
            canvas.save()
            canvas.rotate(rotation, it.x.toFloat(), it.y.toFloat())
            overlayScale = calculateScaleFactor(drawable)
            val scaledWidth = (drawable.intrinsicWidth * overlayScale).toInt()
            val scaledHeight = (drawable.intrinsicHeight * overlayScale).toInt()
            val x0 = (it.x - scaledWidth / 2).toFloat()
            var y0 = (it.y - scaledHeight / 2).toFloat()
            val x1 = (it.x + scaledWidth / 2).toFloat()
            val y1 = (it.y + scaledHeight / 2).toFloat()
            val gradientStartColor = Color.parseColor("#3CB583")
            val gradientEndColor = Color.parseColor("#34C1E0")
            val fillGradient = LinearGradient(
                x0, y0, x1, y1,
                gradientStartColor, gradientEndColor,
                Shader.TileMode.CLAMP
            )
            var intersectCircleY = y1
            if (storyId != 0) {
                y0 += scaledHeight / 4
                val circleRadius = 80f
                intersectCircleY = y0  +  circleRadius
                val circleY = y0 + (scaledHeight - 2 * circleRadius)
                // flipped triangle
                val trianglePath = Path().apply {
                    moveTo(it.x.toFloat() - 80, circleY)
                    lineTo(it.x.toFloat(), y1)
                    lineTo(it.x.toFloat() + 80, circleY)
                    close()
                }
                val trianglePaint = Paint().apply {
                    style = Paint.Style.FILL
                    color = gradientEndColor
                }
                canvas.drawPath(trianglePath, trianglePaint)
                val circleX = it.x.toFloat()
                val circlePaint = Paint().apply {
                    shader = RadialGradient(
                        circleX, circleY, circleRadius,
                        gradientStartColor, gradientEndColor,
                        Shader.TileMode.CLAMP
                    )

                    style = Paint.Style.FILL
                }

                canvas.drawCircle(circleX, circleY, circleRadius, circlePaint)
                drawable.setBounds(it.x - 80, circleY.toInt() - 80, it.x + 80, circleY.toInt() + 80)
                val circlePath = Path().apply {
                    addCircle(circleX, circleY, circleRadius, Path.Direction.CW)
                    close()
                }
                val circleIntersectPath = Path().apply {
                    addCircle(x1, intersectCircleY, intersectCircleRadius, Path.Direction.CW)
                    close()
                }
                val combinedPath = Path().apply {
                    op(trianglePath, circlePath, Path.Op.UNION)
                }
                if (intersectedOverlays.isNotEmpty())
                    combinedPath.apply {
                        op(this, circleIntersectPath, Path.Op.UNION)
                    }


                val strokePaint = Paint().apply {
                    shader = fillGradient
                    style = Paint.Style.STROKE
                    strokeWidth = 15f
                }
                canvas.clipPath(combinedPath)
                drawable.draw(canvas)
                canvas.drawCircle(circleX, circleY, circleRadius, strokePaint)
            } else {
                drawable.setBounds(x0.toInt(), y0.toInt(), x1.toInt(), y1.toInt())
                val strokePaint = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 15.0F
                    shader = fillGradient
                }
                val strokeRect = RectF(x0, y0, x1, y1)
                canvas.drawRoundRect(strokeRect, scaledWidth * 0.25f, scaledWidth * 0.25f, strokePaint)
                val cornerSize = scaledWidth * 0.25f

                val roundedRectPath = Path().apply {
                    addRoundRect(strokeRect, cornerSize, cornerSize, Path.Direction.CW)
                    close()
                }
                val circlePath = Path().apply {
                    addCircle(x1, y1, intersectCircleRadius, Path.Direction.CW)
                    close()
                }
                val combinedPath = Path().apply {
                    op(roundedRectPath, circlePath, Path.Op.UNION)
                }
                if (intersectedOverlays.isNotEmpty())
                    canvas.clipPath(combinedPath)
                else
                    canvas.clipPath(roundedRectPath)

                drawable.draw(canvas)
            }
            if (intersectedOverlays.isNotEmpty()) {
                val circleGradientStartColor = Color.parseColor("#9D51FF")
                val circleGradientEndColor = Color.parseColor("#4EEAC5")
                val circleGradient = LinearGradient(
                    x1 - intersectCircleRadius, intersectCircleY, x1 + intersectCircleRadius, intersectCircleY + intersectCircleRadius,
                    circleGradientStartColor, circleGradientEndColor,
                    Shader.TileMode.CLAMP
                )

                val circleStrokePaint = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 10.0F
                    shader = fillGradient
                }

                val circleFillPaint = Paint().apply {
                    style = Paint.Style.FILL
                    shader = circleGradient
                }

                canvas.drawCircle(x1, intersectCircleY, intersectCircleRadius, circleFillPaint)
                canvas.drawCircle(x1, intersectCircleY, intersectCircleRadius, circleStrokePaint)
            }
            canvas.restore()

            canvas.save()
            canvas.rotate(rotation, it.x.toFloat(), it.y.toFloat())
            val simplifiedIntersectCount = min(intersectedOverlays.count(), 99)
            val intersectCountText = "+$simplifiedIntersectCount"
            if (storyId == 0) {
                val nameText = name
                canvas.drawText(
                    nameText!!,
                    it.x.toFloat() - textPaintBlack.measureText(nameText) / 2,
                    (it.y - scaledHeight / 2 - 10).toFloat(),
                    textPaintBlack
                )
                if (speed != null && speed!!.toInt() != 0) {
                    val speedText = context.getString(R.string.speed_format, (speed!! * 3.6).toInt())
                    canvas.drawText(
                        speedText,
                        it.x.toFloat(),
                        (it.y + scaledHeight / 2 + 65).toFloat(),
                        textPaintBlack
                    )
                }
            }

            if (intersectedOverlays.isNotEmpty()) {
                val greaterThanTen = (intersectedOverlays.count() >= 10).toInt()
                val textPaintWhite = Paint().apply {
                    color = Color.WHITE
                    textSize =  32f - (6f * greaterThanTen)
                    isAntiAlias = true
                    typeface = ResourcesCompat.getFont(context, fontResId)
                    strokeWidth = 2f
                    style = Paint.Style.FILL_AND_STROKE
                }
                canvas.drawText(
                    intersectCountText,
                    x1 - (intersectCircleRadius / 2 - 5 + (5 * greaterThanTen)),
                    intersectCircleY + (intersectCircleRadius / 2 - 7 - (3 * greaterThanTen)),
                    textPaintWhite
                )
            }
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
    private fun doOverlaysIntersect(overlay1: CustomIconOverlay, overlay2: CustomIconOverlay, currentPoint: Point): Boolean {
        if (overlay2 != overlay1) {
            val otherProjection = mapView!!.projection
            val otherPoint = otherProjection?.toPixels(overlay2.location, null)!!
            val distance = sqrt(
                (currentPoint.x - otherPoint.x).toDouble().pow(2.0) +
                        (currentPoint.y - otherPoint.y).toDouble().pow(2.0)
            )
            val iconRadius = (drawable.intrinsicWidth * overlayScale / 2)
            val otherIconRadius = (overlay2.drawable.intrinsicWidth * calculateScaleFactor(overlay2.drawable) / 2)
            return (distance <= iconRadius + otherIconRadius)
        }
        return false
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
    fun getStoryId(): Int {
        return storyId
    }
    fun getIntersectedIds(): MutableList<Pair<Int, Int>> {
        return intersectedOverlays
    }

    fun updateDrawable(newDrawable: Drawable) {
        if (storyId != 0) {
            blurDrawable(newDrawable, 25f, executor) { blurredDrawable ->
                drawable = blurredDrawable
                mapView?.invalidate()
            }
        } else {
            drawable = newDrawable
            mapView?.invalidate()
        }
    }


    private fun blurDrawable(drawable: Drawable, blurRadius: Float, executor: Executor, callback: (Drawable) -> Unit) {
        val bitmap = drawable.toBitmap()

        val imageReader = ImageReader.newInstance(
            bitmap.width, bitmap.height,
            PixelFormat.RGBA_8888, 1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        )

        val renderNode = RenderNode("BlurEffect")

        val hardwareRenderer = HardwareRenderer()

        hardwareRenderer.setSurface(imageReader.surface)
        hardwareRenderer.setContentRoot(renderNode)

        renderNode.setPosition(0, 0, bitmap.width, bitmap.height)

        val blurRenderEffect = RenderEffect.createBlurEffect(
            blurRadius, blurRadius,
            Shader.TileMode.MIRROR
        )

        renderNode.setRenderEffect(blurRenderEffect)

        executor.execute {
            val renderCanvas = renderNode.beginRecording()
            renderCanvas.drawBitmap(bitmap, 0f, 0f, null)
            renderNode.endRecording()

            hardwareRenderer.createRenderRequest()
                .setWaitForPresent(true)
                .syncAndDraw()

            val image = imageReader.acquireNextImage() ?: throw RuntimeException("No Image")
            val hardwareBuffer = image.hardwareBuffer ?: throw RuntimeException("No HardwareBuffer")

            val blurredBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, null)
                ?: throw RuntimeException("Create Bitmap Failed")

            val blurredDrawable = BitmapDrawable(context.resources, blurredBitmap)

            hardwareBuffer.close()
            image.close()
            imageReader.close()
            renderNode.discardDisplayList()
            hardwareRenderer.destroy()

            callback(blurredDrawable)
        }
    }
}
fun Boolean.toInt() = if (this) 1 else 0