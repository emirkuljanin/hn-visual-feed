package com.emirkuljanin.hnfeed.core.database.util

import androidx.room3.ColumnTypeConverter
import java.time.Instant

/** Stores [Instant]s as epoch milliseconds. */
internal class InstantConverter {

    @ColumnTypeConverter
    fun fromEpochMillis(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @ColumnTypeConverter
    fun toEpochMillis(instant: Instant?): Long? = instant?.toEpochMilli()
}
