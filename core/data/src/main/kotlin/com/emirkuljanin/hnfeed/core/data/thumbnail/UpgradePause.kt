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
 * When cloud upgrades may resume after the provider's daily quota ran out. Persisted, so upgrade runs started during
 * the pause (every app start and page load starts one) end at once instead of asking the provider again.
 */
internal interface UpgradePause {

    /** `null` if upgrades were never paused. */
    suspend fun resumesAt(): Instant?

    suspend fun pauseUntil(resumeAt: Instant)
}

/** A single value, so SharedPreferences rather than a database table. Main-safe. */
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

    private companion object {
        const val FILE_NAME = "thumbnail_upgrades"
        const val KEY_RESUME_AT = "resume_at"
    }
}

/** Shortly after the next midnight UTC, when Cloudflare's free daily allocation resets. */
internal fun nextQuotaReset(now: Instant): Instant =
    now.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        .plus(QUOTA_RESET_MARGIN)

/** A little slack so the first request after the reset is not counted against the old day. */
private val QUOTA_RESET_MARGIN: Duration = Duration.ofMinutes(5)
