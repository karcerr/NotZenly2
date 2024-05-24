package com.tagme

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.palette.graphics.Palette
import com.tagme.R
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

class GeoStoryCreationFragment : Fragment() {
    private lateinit var api: API
    private lateinit var geoStoryPreview: ImageView
    private lateinit var geoStoryPreviewIcon: ImageView
    private lateinit var compressingStatus: LinearLayout
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var outputStream: ByteArrayOutputStream
    private var imageCompressed: Boolean = false
    companion object{
        val MAX_SIZE_BEFORE_ENCODING = 100 * 1024
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.geo_story_creation, container, false)
        val coroutineScope = CoroutineScope(Dispatchers.Main)
        val privacyGlobal = view.findViewById<ImageButton>(R.id.global_privacy)
        val privacyFriendsOnly = view.findViewById<ImageButton>(R.id.friend_only_privacy)
        var privacy = "friends only"
        val selectedColor = Color.parseColor("#5EFF77")
        val unselectedColor = Color.parseColor("#C6C6C6")
        val geoStoryFrameLayout = view.findViewById<FrameLayout>(R.id.geo_story_frame)
        geoStoryPreview = view.findViewById(R.id.geo_story_preview)
        geoStoryPreviewIcon = view.findViewById(R.id.geo_story_preview_icon)
        compressingStatus = view.findViewById(R.id.compressing_status)
        val backButton = view.findViewById<ImageButton>(R.id.back_arrow_button)
        api = (requireActivity() as MapActivity).api
        val publishButton = view.findViewById<ImageButton>(R.id.publish_geo_story_button)

        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data: Intent? = result.data
                data?.data?.let { uri ->
                    compressImage(uri)
                }
            }
        }

        geoStoryFrameLayout.setOnClickListener {
            imageCompressed = false
            pickImageGallery()
        }
        backButton.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        publishButton.setOnClickListener{
            coroutineScope.launch {
                if (imageCompressed) {
                    api.insertPictureIntoWS(outputStream)
                    if (api.lastInsertedPicId != 0) {
                        val latitude = (requireActivity() as MapActivity).myLatitude
                        val longitude = (requireActivity() as MapActivity).myLongitute
                        val result = api.createGeoStory(
                            api.lastInsertedPicId,
                            privacy,
                            latitude,
                            longitude
                        )
                        val message = result?.getString("message")
                        if (message == "success") {
                            requireActivity().onBackPressedDispatcher.onBackPressed()
                            delay(500)
                            geoStoryPreview.setImageResource(R.drawable.photo_bg)
                            geoStoryPreviewIcon.visibility = View.VISIBLE
                            compressingStatus.visibility = View.GONE
                            Toast.makeText(requireActivity(), getString(R.string.geo_story_created), Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(requireActivity(), getString(R.string.something_went_wrong), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        privacyGlobal.setOnClickListener {
            privacyGlobal.setColorFilter(selectedColor)
            privacyFriendsOnly.setColorFilter(unselectedColor)
            privacy = "global"

        }
        privacyFriendsOnly.setOnClickListener {
            privacyGlobal.setColorFilter(unselectedColor)
            privacyFriendsOnly.setColorFilter(selectedColor)
            privacy = "friends only"
        }
        return view
    }
    private fun pickImageGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        imagePickerLauncher.launch(intent)
    }
    private fun compressImage(uri: Uri) {
        GlobalScope.launch(Dispatchers.IO) {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                val originalBitmap = BitmapFactory.decodeStream(stream)
                val compressedBitmap = compressBitmap(overlayGradient(compressBitmap(originalBitmap)))
                originalBitmap.recycle()
                requireActivity().runOnUiThread {
                    compressingStatus.visibility = View.GONE
                    val roundedImageBitmap = applyRoundedCorners(compressedBitmap, 20f)
                    imageCompressed = true
                    geoStoryPreview.setImageBitmap(roundedImageBitmap)
                }
            }
        }
    }
    private fun compressBitmap(bitmap: Bitmap): Bitmap {
        val maxWidth = 450
        val maxHeight = 800

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
        requireActivity().runOnUiThread {
            geoStoryPreview.setImageResource(R.drawable.photo_bg)
            geoStoryPreviewIcon.visibility = View.GONE
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
    private fun overlayGradient(bitmap: Bitmap): Bitmap {
        val dominantColors = calculateDominantColors(bitmap)

        val gradientBitmap = createGradientBitmap(450, 800, dominantColors.first, dominantColors.second)

        val scaledGradientBitmap = Bitmap.createScaledBitmap(gradientBitmap, 450, 800, true)
        val combinedBitmap = Bitmap.createBitmap(450, 800, Bitmap.Config.ARGB_8888)

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
}