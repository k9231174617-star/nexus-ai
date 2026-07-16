package com.nexus.agent.ui.universal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.nexus.agent.R
import com.nexus.agent.databinding.FragmentUniversalAgentBinding

/**
 * Universal Agent Fragment — the main hub for all media-related AI operations.
 * Provides a tabbed interface for Image Editing, Video Creation, and Document Viewing.
 */
class UniversalAgentFragment : Fragment() {

    private var _binding: FragmentUniversalAgentBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUniversalAgentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViewPager()
        setupTabLayout()
        setupMediaToolbar()
    }

    private fun setupViewPager() {
        viewPager = binding.universalViewPager
        val adapter = UniversalPagerAdapter(this)
        viewPager.adapter = adapter
        viewPager.offscreenPageLimit = 3
    }

    private fun setupTabLayout() {
        tabLayout = binding.universalTabLayout
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_image)
                1 -> getString(R.string.tab_video)
                2 -> getString(R.string.tab_document)
                else -> ""
            }
            tab.icon = when (position) {
                0 -> requireContext().getDrawable(R.drawable.ic_universal)
                1 -> requireContext().getDrawable(R.drawable.ic_universal)
                2 -> requireContext().getDrawable(R.drawable.ic_universal)
                else -> null
            }
        }.attach()
    }

    private fun setupMediaToolbar() {
        binding.mediaToolbar.setOnActionListener { action ->
            when (action) {
                MediaToolbar.Action.IMPORT -> handleImport()
                MediaToolbar.Action.CAMERA -> handleCamera()
                MediaToolbar.Action.AI_GENERATE -> handleAIGenerate()
                MediaToolbar.Action.SHARE -> handleShare()
                MediaToolbar.Action.EXPORT -> handleExport()
            }
        }
    }

    private fun handleImport() {
        val currentFragment = childFragmentManager.findFragmentByTag("f${viewPager.currentItem}")
        when (currentFragment) {
            is ImageEditor -> currentFragment.showImportDialog()
            is VideoCreator -> currentFragment.showImportDialog()
            is DocumentViewer -> currentFragment.showImportDialog()
        }
    }

    private fun handleCamera() {
        val currentFragment = childFragmentManager.findFragmentByTag("f${viewPager.currentItem}")
        when (currentFragment) {
            is ImageEditor -> currentFragment.launchCamera()
            is VideoCreator -> currentFragment.launchCamera()
        }
    }

    private fun handleAIGenerate() {
        val currentFragment = childFragmentManager.findFragmentByTag("f${viewPager.currentItem}")
        when (currentFragment) {
            is ImageEditor -> currentFragment.showAIGenerateDialog()
            is VideoCreator -> currentFragment.showAIGenerateDialog()
        }
    }

    private fun handleShare() {
        val currentFragment = childFragmentManager.findFragmentByTag("f${viewPager.currentItem}")
        when (currentFragment) {
            is ImageEditor -> currentFragment.shareCurrentMedia()
            is VideoCreator -> currentFragment.shareCurrentMedia()
            is DocumentViewer -> currentFragment.shareCurrentDocument()
        }
    }

    private fun handleExport() {
        val currentFragment = childFragmentManager.findFragmentByTag("f${viewPager.currentItem}")
        when (currentFragment) {
            is ImageEditor -> currentFragment.exportImage()
            is VideoCreator -> currentFragment.exportVideo()
            is DocumentViewer -> currentFragment.exportDocument()
        }
    }

    fun getMediaToolbar(): MediaToolbar = binding.mediaToolbar

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
