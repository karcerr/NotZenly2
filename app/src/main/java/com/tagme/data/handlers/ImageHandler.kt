package com.tagme.data.handlers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.palette.graphics.Palette
import com.tagme.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class ImageHandler(
    private val context: Context,
    private val imageView: ImageView,
    private val addImageViewIcon: ImageView,
    private val compressingStatus: LinearLayout,
    private val applyOverlayGradient: Boolean,
    private val maxWidth: Int,
    private val maxHeight: Int,
    private val cornerRadius: Float
) {
    private lateinit var outputStream: ByteArrayOutputStream
    private var imageCompressed: Boolean = false
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>

    companion object {
        const val MAX_SIZE_BEFORE_ENCODING = 100 * 1024
    }

    fun initImagePickerLauncher(fragment: Fragment) {
        imageCompressed = false
        imagePickerLauncher = fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                data?.data?.let { uri ->
                    compressImage(uri)
                }
            }
        }
    }

    fun pickImageGallery() {
        imageCompressed = false
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        imagePickerLauncher.launch(intent)
    }

    private fun compressImage(uri: Uri) {
        GlobalScope.launch(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val originalBitmap = applyExifOrientation(uri, BitmapFactory.decodeStream(stream))
                val compressedBitmap = if(applyOverlayGradient)
                    compressBitmap(overlayGradient(compressBitmap(originalBitmap)))
                else compressBitmap(originalBitmap)
                originalBitmap.recycle()
                (context as Activity).runOnUiThread {
                    compressingStatus.visibility = View.GONE
                    val roundedImageBitmap = applyRoundedCorners(compressedBitmap, cornerRadius)
                    imageCompressed = true
                    imageView.setImageBitmap(roundedImageBitmap)
                }
            }
        }
    }

    private fun compressBitmap(bitmap: Bitmap): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height

        val aspectRatio = originalWidth.toFloat() / originalHeight.toFloat()
        val targetWidth: Int
        val targetHeight: Int
        if (aspectRatio > maxWidth.toFloat() / maxHeight.toFloat()) {
            targetWidth = maxWidth
            targetHeight = (maxWidth.toFloat() / aspectRatio).toInt()
        } else {
            targetWidth = (maxHeight.toFloat() * aspectRatio).toInt()
            targetHeight = maxHeight
        }

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        val initialByteArray = outputStream.toByteArray()
        (context as Activity).runOnUiThread {
            imageView.setImageResource(R.drawable.photo_bg)
            addImageViewIcon.visibility = View.GONE
            compressingStatus.visibility = View.VISIBLE
        }
        if (initialByteArray.size <= MAX_SIZE_BEFORE_ENCODING) {
            Log.d("Tagme_PIC", "Image size is already within the desired range.")
            return BitmapFactory.decodeByteArray(initialByteArray, 0, initialByteArray.size)
        }
        var quality = 100
        var byteArray = initialByteArray
        Log.d("Tagme_PIC", "Before compressing: ${byteArray.size}")
        while (byteArray.size > MAX_SIZE_BEFORE_ENCODING && quality > 0) {
            outputStream.reset()
            quality -= if (quality <= 20) 5 else 10
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            byteArray = outputStream.toByteArray()
            Log.d("Tagme_PIC", "Compressing: $quality ${byteArray.size}")
        }

        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    }
    private fun applyExifOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        val inputStream = context.contentResolver.openInputStream(uri)
        val exif = inputStream?.use { ExifInterface(it) }
        val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun overlayGradient(bitmap: Bitmap): Bitmap {
        val dominantColors = calculateDominantColors(bitmap)

        val gradientBitmap = createGradientBitmap(maxWidth, maxHeight, dominantColors.first, dominantColors.second)

        val scaledGradientBitmap = Bitmap.createScaledBitmap(gradientBitmap, maxWidth, maxHeight, true)
        val combinedBitmap = Bitmap.createBitmap(maxWidth, maxHeight, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(combinedBitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(scaledGradientBitmap, 0f, 0f, paint)
        val startX = (scaledGradientBitmap.width - bitmap.width) / 2f
        val startY = (scaledGradientBitmap.height - bitmap.height) / 2f
        canvas.drawBitmap(bitmap, startX, startY, paint)

        return combinedBitmap
    }

    private fun createGradientBitmap(width: Int, height: Int, colorStart: Int, colorEnd: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val gradient = LinearGradient(0f, 0f, 0f, height.toFloat(), colorStart, colorEnd, Shader.TileMode.CLAMP)
        val paint = Paint()
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }

    private fun calculateDominantColors(bitmap: Bitmap): Pair<Int, Int> {
        val palette = Palette.from(bitmap).generate()
        if (palette.swatches.isNullOrEmpty()) {
            return Pair(Color.WHITE, Color.BLACK)
        }
        val dominantColors = mutableListOf<Int>()
        palette.swatches.sortedByDescending { it.population }.take(2).forEach { swatch ->
            dominantColors.add(swatch.rgb)
        }

        if (dominantColors.size == 1) {
            dominantColors.add(dominantColors[0])
        }

        return Pair(dominantColors[0], dominantColors[1])
    }

    private fun applyRoundedCorners(bitmap: Bitmap, radius: Float): Bitmap {
        val roundedBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(roundedBitmap)
        val paint = Paint()
        paint.isAntiAlias = true
        val rectF = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val path = Path()
        path.addRoundRect(rectF, radius, radius, Path.Direction.CW)
        canvas.drawPath(path, paint)
        paint.shader = shader
        canvas.drawPath(path, paint)
        return roundedBitmap
    }

    fun isImageCompressed(): Boolean {
        return imageCompressed
    }

    fun getOutputStream(): ByteArrayOutputStream {
        return outputStream
    }
}
