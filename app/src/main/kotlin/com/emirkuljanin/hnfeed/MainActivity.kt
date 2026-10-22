package com.emirkuljanin.hnfeed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.emirkuljanin.hnfeed.core.designsystem.theme.HnFeedTheme
import com.emirkuljanin.hnfeed.feature.feed.FeedScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HnFeedTheme {
                FeedScreen()
            }
        }
    }
}
