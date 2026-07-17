package com.nexus.agent.ui.trading

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class TradingFragment : Fragment() {

    private val viewModel: TradingViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // In full implementation: return inflater.inflate(R.layout.fragment_trading, container, false)
        return inflater.inflate(android.R.layout.simple_list_item_1, container, false)
    }
}
