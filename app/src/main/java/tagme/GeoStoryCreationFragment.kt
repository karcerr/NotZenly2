package tagme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.example.tagme.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class GeoStoryCreationFragment : Fragment() {
    private lateinit var api: API
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
        val geoStoryPreview = view.findViewById<ImageView>(R.id.geo_story_preview)
        val backButton = view.findViewById<ImageButton>(R.id.back_arrow_button)
        api = (requireActivity() as MapActivity).api
        val publishButton = view.findViewById<ImageButton>(R.id.publish_geo_story_button)




        backButton.setOnClickListener{
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        return view
    }
}