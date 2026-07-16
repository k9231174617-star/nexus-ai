package com.nexus.agent

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.nexus.agent.databinding.ActivityMainBinding
import com.nexus.agent.ui.common.ToastManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        setupDrawer()
        setupStatusBar()
    }

    private fun setupNavigation() {
        val navHostFrag = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFrag.navController

        binding.navigationView.setNavigationItemSelectedListener { item ->
            val destId = when (item.itemId) {
                R.id.nav_main_agent    -> R.id.mainAgentFragment
                R.id.nav_code_agent    -> R.id.codeAgentFragment
                R.id.nav_universal     -> R.id.universalAgentFragment
                R.id.nav_cli           -> R.id.cliFragment
                R.id.nav_files         -> R.id.filesFragment
                R.id.nav_memory        -> R.id.memoryFragment
                R.id.nav_planner       -> R.id.plannerFragment
                R.id.nav_sandbox       -> R.id.sandboxFragment
                R.id.nav_browser       -> R.id.browserFragment
                R.id.nav_rag           -> R.id.ragFragment
                R.id.nav_graph         -> R.id.graphFragment
                R.id.nav_observability -> R.id.observabilityFragment
                R.id.nav_settings      -> R.id.settingsFragment
                else -> return@setNavigationItemSelectedListener false
            }
            navController.navigate(destId)
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.toolbar.title = destination.label
            updateDrawerSelection(destination.id)
        }
    }

    private fun setupDrawer() {
        drawerLayout = binding.drawerLayout
        binding.toolbar.setNavigationOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }
    }

    private fun setupStatusBar() {
        window.statusBarColor = android.graphics.Color.parseColor("#09090F")
        window.navigationBarColor = android.graphics.Color.parseColor("#080810")
    }

    private fun updateDrawerSelection(destinationId: Int) {
        val menuItemId = when (destinationId) {
            R.id.mainAgentFragment      -> R.id.nav_main_agent
            R.id.codeAgentFragment      -> R.id.nav_code_agent
            R.id.universalAgentFragment -> R.id.nav_universal
            R.id.cliFragment            -> R.id.nav_cli
            R.id.filesFragment          -> R.id.nav_files
            R.id.memoryFragment         -> R.id.nav_memory
            R.id.plannerFragment        -> R.id.nav_planner
            R.id.sandboxFragment        -> R.id.nav_sandbox
            R.id.browserFragment        -> R.id.nav_browser
            R.id.ragFragment            -> R.id.nav_rag
            R.id.graphFragment          -> R.id.nav_graph
            R.id.observabilityFragment  -> R.id.nav_observability
            R.id.settingsFragment       -> R.id.nav_settings
            else -> return
        }
        binding.navigationView.setCheckedItem(menuItemId)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
