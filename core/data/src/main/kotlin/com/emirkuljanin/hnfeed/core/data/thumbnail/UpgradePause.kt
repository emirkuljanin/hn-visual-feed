package com.emirkuljanin.hnfeed.core.data.thumbnail

import android.content.Context
import androidx.core.content.edit
import com.emirkuljanin.hnfeed.core.common.dispatchers.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * When cloud upgrades may run again. After the provider's daily quota ran out: a time, so upgrade runs started during
 * the pause (every app start and page load starts one) end at once instead of asking the provider again. After runs
 * that stopped at a transient failure: since when they have been failing, which sets the delay before the next
 * retry ([upgradeRetryDelay]). Persisted, so neither is forgotten when the process dies between runs.
 */
internal interface UpgradePause {

    /** `null` if upgrades were never paused. */
    suspend fun resumesAt(): Instant?

    suspend fun pauseUntil(resumeAt: Instant)

    /**
     * Records a run that stopped at a transient failure at [now]; returns when this streak of failures began. A
     * stored start later than [now] (the clock was set back) starts the streak over at [now].
     */
    suspend fun recordFailure(now: Instant): Instant

    /**
     * A run got through: it ended other than at a transient failure, or the provider answered for a story (an image
     * or a refusal) before it stopped. The next failure starts a new streak.
     */
    suspend fun clearFailures()
}

/**
 * Two values, so SharedPreferences rather than a database table. Main-safe. Upgrade runs are one unique WorkManager
 * chain, so the read-modify-write of the streak never races another run.
 */
internal class SharedPreferencesUpgradePause @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : UpgradePause {

    private val preferences by lazy { context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE) }

    override suspend fun resumesAt(): Instant? = withContext(ioDispatcher) {
        preferences.getLong(KEY_RESUME_AT, 0L).takeIf { it > 0L }?.let(Instant::ofEpochMilli)
    }

    override suspend fun pauseUntil(resumeAt: Instant) {
        withContext(ioDispatcher) {
            preferences.edit(commit = true) { putLong(KEY_RESUME_AT, resumeAt.toEpochMilli()) }
        }
    }

    override suspend fun recordFailure(now: Instant): Instant = withContext(ioDispatcher) {
        preferences.getLong(KEY_FAILING_SINCE, 0L).takeIf { it > 0L }?.let(Instant::ofEpochMilli)?.takeIf { it <= now }
            ?: now.also { preferences.edit(commit = true) { putLong(KEY_FAILING_SINCE, it.toEpochMilli()) } }
    }

    override suspend fun clearFailures() {
        withContext(ioDispatcher) {
            if (preferences.contains(KEY_FAILING_SINCE)) preferences.edit(commit = true) { remove(KEY_FAILING_SINCE) }
        }
    }

    private companion object {
        const val FILE_NAME = "thumbnail_upgrades"
        const val KEY_RESUME_AT = "resume_at"
        const val KEY_FAILING_SINCE = "failing_since"
    }
}

/** Shortly after the next midnight UTC, when Cloudflare's free daily allocation resets. */
internal fun nextQuotaReset(now: Instant): Instant =
    now.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        .plus(QUOTA_RESET_MARGIN)

/** A little slack so the first request after the reset is not counted against the old day. */
private val QUOTA_RESET_MARGIN: Duration = Duration.ofMinutes(5)

/**
 * The delay before upgrades are retried after a transient failure: as long as the failures have been going on
 * ([streak]), at least 15 minutes and at most 4 hours. Failures 0, 15, 30, 60 and 120 minutes into an outage retry
 * after 15, 15, 30, 60 and 120 minutes, so the gaps roughly double. Short at first, because most outages (a captive
 * portal, a busy provider) are brief; growing, so a provider that stays unreachable from this network costs about 6
 * background runs a day once capped instead of one every 15 minutes forever. The cap stays below WorkManager's 5 h
 * backoff limit, the old worst case. Based on time rather than on the number of failed runs, because every page load
 * and app start also starts a run: a short outage while the user scrolls must not push the next retry to hours.
 */
internal fun upgradeRetryDelay(streak: Duration): Duration = streak.coerceIn(FIRST_RETRY_DELAY, MAX_RETRY_DELAY)

private val FIRST_RETRY_DELAY: Duration = Duration.ofMinutes(15)
private val MAX_RETRY_DELAY: Duration = Duration.ofHours(4)
