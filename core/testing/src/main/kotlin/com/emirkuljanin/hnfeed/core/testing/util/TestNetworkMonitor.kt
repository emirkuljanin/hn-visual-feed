package com.emirkuljanin.hnfeed.core.testing.util

import com.emirkuljanin.hnfeed.core.data.util.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow

/** A [NetworkMonitor] tests switch by hand. */
class TestNetworkMonitor(online: Boolean = true) : NetworkMonitor {
    override val isOnline = MutableStateFlow(online)

    fun setOnline(online: Boolean) {
        isOnline.value = online
    }
}
