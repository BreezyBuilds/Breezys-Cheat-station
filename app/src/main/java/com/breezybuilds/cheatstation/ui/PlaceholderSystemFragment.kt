package com.breezybuilds.cheatstation.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class PlaceholderSystemFragment : Fragment() {

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()

        val root = Ui.vbox(ctx, 20).apply {
            gravity = Gravity.CENTER
        }

        val title = requireArguments().getString("title") ?: "System"

        root.addView(
            Ui.tv(ctx, title, 24f, bold = true),
            Ui.lp()
        )

        root.addView(
            Ui.tv(
                ctx,
                "Cheat management for this system is coming soon.",
                15f,
                secondary = true
            ),
            Ui.lp()
        )

        return root
    }

    companion object {
        fun newInstance(title: String): PlaceholderSystemFragment {
            return PlaceholderSystemFragment().apply {
                arguments = Bundle().apply {
                    putString("title", title)
                }
            }
        }
    }
}
