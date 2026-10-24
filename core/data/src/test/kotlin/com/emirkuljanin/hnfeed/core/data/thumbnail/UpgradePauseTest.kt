package com.emirkuljanin.hnfeed.core.data.thumbnail

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
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
    fun aFailureStreak_keepsItsStart_acrossInstances_untilCleared() = runTest {
        val start = Instant.parse("2026-10-24T10:00:00Z")
        assertEquals(start, pause().recordFailure(start))
        assertEquals(start, pause().recordFailure(start.plusSeconds(3600)))

        pause().clearFailures()

        assertEquals(start.plusSeconds(7200), pause().recordFailure(start.plusSeconds(7200)))
    }

    /** The device clock was ahead when the streak began, then set back: the streak starts over instead of freezing. */
    @Test
    fun aFailureStreakStartingInTheFuture_startsOverNow() = runTest {
        val now = Instant.parse("2026-10-24T10:00:00Z")
        pause().recordFailure(now.plusSeconds(86_400))

        assertEquals(now, pause().recordFailure(now))
    }

    @Test
    fun theRetryDelay_isAsLongAsTheStreak_from15MinutesUpTo4Hours() {
        val minutes = listOf(0L, 10L, 15L, 30L, 60L, 120L, 600L).map {
            upgradeRetryDelay(streak = Duration.ofMinutes(it)).toMinutes()
        }

        assertEquals(listOf(15L, 15L, 15L, 30L, 60L, 120L, 240L), minutes)
        assertEquals(Duration.ofMinutes(15), upgradeRetryDelay(streak = Duration.ofMinutes(-5)), "Clock set back")
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
