package com.nexus.agent.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.nexus.agent.R
import com.nexus.agent.ui.code.CodeAgentFragment
import com.nexus.agent.ui.universal.UniversalAgentFragment

/**
 * Корневой фрагмент главного агента.
 * Управляет ViewPager2 с вкладками: Main Chat, Code Agent, Universal Agent.
 * Также управляет CLIOverlay для быстрого доступа к терминалу.
 */
class MainAgentFragment : Fragment() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var btnOpenCli: ImageButton
    private lateinit var btnSettings: ImageButton
    private var cliOverlay: CLIOverlay? = null

    companion object {
        fun newInstance() = MainAgentFragment()
        private val TAB_TITLES = listOf("Chat", "Code", "Universal")
        private val TAB_ICONS = listOf(
            R.drawable.ic_main_agent,
            R.drawable.ic_code_agent,
            R.drawable.ic_universal
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_main_agent, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewPager = view.findViewById(R.id.viewPager)
        tabLayout = view.findViewById(R.id.tabLayout)
        btnOpenCli = view.findViewById(R.id.btnOpenCli)
        btnSettings = view.findViewById(R.id.btnSettings)

        setupViewPager()
        setupTabs()
        setupButtons()
        setupStatsBar(view)
    }

    private fun setupViewPager() {
        val adapter = MainAgentPagerAdapter(this)
        viewPager.adapter = adapter
        viewPager.offscreenPageLimit = 2
    }

    private fun setupTabs() {
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = TAB_TITLES[position]
            tab.setIcon(TAB_ICONS[position])
        }.attach()
    }

    private fun setupButtons() {
        btnOpenCli.setOnClickListener {
            toggleCliOverlay()
        }

        btnSettings.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.container, com.nexus.agent.ui.settings.SettingsFragment::newInstance())
                .addToBackStack("settings")
                .commit()
        }
    }

    private fun setupStatsBar(view: View) {
        val statsBar = view.findViewById<StatsBarView>(R.id.statsBar)
        statsBar?.let { bar ->
            // Подписка на обновления статистики из ViewModel
            // В реальном приложении здесь будет observe на LiveData/StateFlow
            bar.updateStats(
                modelName = "GPT-4o",
                tokensUsed = 0,
                latencyMs = 0L,
                status = StatsBarView.Status.IDLE
            )
        }
    }

    private fun toggleCliOverlay() {
        if (cliOverlay == null) {
            cliOverlay = CLIOverlay(requireContext()).apply {
                onClose = { removeCliOverlay() }
                anchorToView(btnOpenCli)
            }
            (view as? ViewGroup)?.addView(cliOverlay)
        } else {
            removeCliOverlay()
        }
    }

    private fun removeCliOverlay() {
        cliOverlay?.let {
            (view as? ViewGroup)?.removeView(it)
            cliOverlay = null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cliOverlay = null
    }

    /**
     * Адаптер для ViewPager2 с фрагментами агентов.
     */
    private inner class MainAgentPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> MainChatFragment.newInstance()
                1 -> CodeAgentFragment.newInstance()
                2 -> UniversalAgentFragment.newInstance()
                else -> throw IllegalArgumentException("Invalid position: $position")
            }
        }
    }
}
