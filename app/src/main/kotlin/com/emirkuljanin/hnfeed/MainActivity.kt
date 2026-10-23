package com.emirkuljanin.hnfeed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.emirkuljanin.hnfeed.core.designsystem.theme.HnFeedTheme
import com.emirkuljanin.hnfeed.core.designsystem.time.LocalClock
import com.emirkuljanin.hnfeed.feature.feed.FeedRoute
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** The same clock the data layer uses, so the UI and the database agree on "now". */
    @Inject
    lateinit var clock: Clock

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalClock provides clock) {
                HnFeedTheme {
                    FeedRoute()
                }
            }
        }
    }
}
