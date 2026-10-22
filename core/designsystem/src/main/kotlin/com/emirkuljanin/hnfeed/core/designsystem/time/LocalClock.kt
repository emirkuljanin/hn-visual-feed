package com.emirkuljanin.hnfeed.core.designsystem.time

import androidx.compose.runtime.staticCompositionLocalOf
import java.time.Clock

/**
 * The clock UI code reads "now" from (e.g. for "3 hours ago"), instead of calling `Instant.now()`. Screenshot and
 * UI tests provide a fixed clock so their output does not change over time.
 */
val LocalClock = staticCompositionLocalOf<Clock> { Clock.systemDefaultZone() }
