package com.emirkuljanin.hnfeed.core.data.thumbnail

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpgradePauseTest {

    @Test
    fun isUnsetAtFirst_andSurvivesANewInstance() = runTest {
        assertNull(pause().resumesAt())

        pause().pauseUntil(Instant.parse("2026-10-24T00:05:00Z"))

        assertEquals(Instant.parse("2026-10-24T00:05:00Z"), pause().resumesAt())
    }

    @Test
    fun theQuotaResetsJustAfterTheNextMidnightUtc() {
        assertEquals(Instant.parse("2026-10-24T00:05:00Z"), nextQuotaReset(Instant.parse("2026-10-23T12:00:00Z")))
        assertEquals(Instant.parse("2026-10-24T00:05:00Z"), nextQuotaReset(Instant.parse("2026-10-23T00:00:00Z")))
        assertEquals(Instant.parse("2026-10-25T00:05:00Z"), nextQuotaReset(Instant.parse("2026-10-24T00:01:00Z")))
    }

    private fun TestScope.pause() = SharedPreferencesUpgradePause(
        ApplicationProvider.getApplicationContext<Context>(),
        StandardTestDispatcher(testScheduler),
    )
}
