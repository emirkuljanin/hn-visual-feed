package com.emirkuljanin.hnfeed.core.data.util

import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(RobolectricTestRunner::class)
class ConnectivityManagerNetworkMonitorTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    @Test
    @OptIn(ExperimentalCoroutinesApi::class) // UnconfinedTestDispatcher
    fun isOffline_onlyWhenNoNetworkIsLeft_andStopsListeningWhenNotCollected() = runTest {
        // Start offline: the active network has no internet capability.
        shadowOf(connectivityManager).setNetworkCapabilities(
            connectivityManager.activeNetwork,
            ShadowNetworkCapabilities.newInstance(),
        )
        val monitor = ConnectivityManagerNetworkMonitor(context, UnconfinedTestDispatcher(testScheduler))
        val wifi = ShadowNetwork.newInstance(1)
        val mobile = ShadowNetwork.newInstance(2)

        monitor.isOnline.test {
            assertFalse(awaitItem())
            val callback = shadowOf(connectivityManager).networkCallbacks.single()

            callback.onAvailable(wifi)
            assertEquals(true, awaitItem())
            // Handing over from Wi-Fi to mobile data: still online throughout.
            callback.onAvailable(mobile)
            callback.onLost(wifi)
            expectNoEvents()
            callback.onLost(mobile)
            assertEquals(false, awaitItem())
        }

        assertEquals(0, shadowOf(connectivityManager).networkCallbacks.size)
    }
}
