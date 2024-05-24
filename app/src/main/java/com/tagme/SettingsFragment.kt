package com.tagme

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
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
        val darkOverlay = view.findViewById<View>(R.id.dark_overlay)
        val are_you_sure_layout = view.findViewById<LinearLayout>(R.id.are_you_sure_layout)
        val yesButton = view.findViewById<Button>(R.id.yes_button)
        val noButton = view.findViewById<Button>(R.id.no_button)
        val mapActivity = requireActivity() as MapActivity
        api = mapActivity.api

        backButton.setOnClickListener{
            mapActivity.onBackPressedDispatcher.onBackPressed()
        }

        exitLayout.setOnClickListener {
            darkOverlay.visibility = View.VISIBLE
            are_you_sure_layout.visibility = View.VISIBLE
        }
        darkOverlay.setOnClickListener {
            darkOverlay.visibility = View.GONE
            are_you_sure_layout.visibility = View.GONE
        }
        noButton.setOnClickListener {
            darkOverlay.visibility = View.GONE
            are_you_sure_layout.visibility = View.GONE
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }
}
