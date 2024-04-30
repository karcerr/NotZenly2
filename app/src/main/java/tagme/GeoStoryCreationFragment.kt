package tagme

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.tagme.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class GeoStoryCreationFragment : Fragment() {
    private lateinit var api: API
    private lateinit var geoStoryPreview: ImageView
    private lateinit var geoStoryPreviewIcon: ImageView
    private lateinit var compressingStatus: LinearLayout
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var imageBitmap: Bitmap
    private lateinit var outputStream: ByteArrayOutputStream
    private var imageCompressed: Boolean = false
    companion object{
        val MAX_SIZE_BEFORE_ENCODING = 150 * 1024
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
                    val byt = outputStream.size()
                    Log.d("Tagme_PIC", byt.toString())
                    api.insertPictureIntoWS(outputStream)
                }
            }
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
                val compressedBitmap = compressBitmap(originalBitmap)
                originalBitmap.recycle()
                requireActivity().runOnUiThread {
                    compressingStatus.visibility = View.GONE
                    imageBitmap = compressedBitmap
                    imageCompressed = true
                    geoStoryPreview.setImageBitmap(imageBitmap)
                }
            }
        }
    }
    private fun compressBitmap(bitmap: Bitmap): Bitmap {
        val desiredHeight = 800
        val aspectRatio = 9f / 16f
        val desiredWidth = (desiredHeight * aspectRatio).toInt()
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, desiredWidth, desiredHeight, true)
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
}