package com.tagme

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {
    private lateinit var api: API
    companion object {
        fun newInstance(): SettingsFragment {
            val fragment = SettingsFragment()
            return fragment
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.settings, container, false)
        val backButton = view.findViewById<ImageButton>(R.id.back_button)
        val exitLayout = view.findViewById<LinearLayout>(R.id.exit_layout)
        val notificationsLayoutButton = view.findViewById<LinearLayout>(R.id.notifications_layout_button)
        val storageLayoutButton = view.findViewById<LinearLayout>(R.id.storage_layout_button)
        val storageLayout = view.findViewById<LinearLayout>(R.id.storage_layout)
        val imageSizeTextView = view.findViewById<TextView>(R.id.image_size_format)
        val clearCacheButton = view.findViewById<Button>(R.id.clear_cache_button)

        val notificationsLayout = view.findViewById<LinearLayout>(R.id.notifications_layout)

        val friendRequestSwitch = view.findViewById<SwitchCompat>(R.id.friend_requests_switch)
        val messagesSwitch = view.findViewById<SwitchCompat>(R.id.messages_switch)
        val darkOverlay = view.findViewById<View>(R.id.dark_overlay)
        val areYouSureLayout = view.findViewById<LinearLayout>(R.id.are_you_sure_layout)
        val yesButton = view.findViewById<Button>(R.id.yes_button)
        val noButton = view.findViewById<Button>(R.id.no_button)
        val headerTextView = view.findViewById<TextView>(R.id.header_text)
        val mainLayout = view.findViewById<LinearLayout>(R.id.main_layout)

        val mapActivity = requireActivity() as MapActivity
        api = mapActivity.api

        backButton.setOnClickListener{
            mapActivity.onBackPressedDispatcher.onBackPressed()
        }

        exitLayout.setOnClickListener {
            darkOverlay.visibility = View.VISIBLE
            areYouSureLayout.visibility = View.VISIBLE
        }
        darkOverlay.setOnClickListener {
            darkOverlay.visibility = View.GONE
            areYouSureLayout.visibility = View.GONE
        }
        noButton.setOnClickListener {
            darkOverlay.visibility = View.GONE
            areYouSureLayout.visibility = View.GONE
        }
        yesButton.setOnClickListener {
            api.myToken = null
            startActivity(Intent(mapActivity, LogInActivity::class.java))
            mapActivity.finish()
        }
        notificationsLayoutButton.setOnClickListener {
            mainLayout.visibility = View.GONE
            notificationsLayout.visibility = View.VISIBLE
            headerTextView.text = getString(R.string.notifications)
            backButton.setOnClickListener{
                notificationsLayout.visibility = View.GONE
                mainLayout.visibility = View.VISIBLE
                headerTextView.text = getString(R.string.settings)
                backButton.setOnClickListener{
                    mapActivity.onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        storageLayoutButton.setOnClickListener {
            mainLayout.visibility = View.GONE
            storageLayout.visibility = View.VISIBLE
            headerTextView.text = getString(R.string.storage_settings)
            backButton.setOnClickListener{
                storageLayout.visibility = View.GONE
                mainLayout.visibility = View.VISIBLE
                headerTextView.text = getString(R.string.settings)
                backButton.setOnClickListener{
                    mapActivity.onBackPressedDispatcher.onBackPressed()
                }
            }
            imageSizeTextView.text = api.getCacheSize(mapActivity)
        }
        clearCacheButton.setOnClickListener {
            imageSizeTextView.text = getString(R.string.calculating_size)
            api.clearImageCache(mapActivity)
            imageSizeTextView.text = api.getCacheSize(mapActivity)
        }
        friendRequestSwitch.isChecked = api.friendRequestsNotificationsEnabled
        messagesSwitch.isChecked = api.messagesNotificationsEnabled
        friendRequestSwitch.setOnCheckedChangeListener { _, isChecked ->
            api.friendRequestsNotificationsEnabled = isChecked
        }
        messagesSwitch.setOnCheckedChangeListener { _, isChecked ->
            api.messagesNotificationsEnabled = isChecked
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }
}
