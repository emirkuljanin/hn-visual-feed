package com.emirkuljanin.hnfeed.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.emirkuljanin.hnfeed.core.data.repository.TopStoriesRepository
import com.emirkuljanin.hnfeed.core.data.util.NetworkMonitor
import com.emirkuljanin.hnfeed.core.model.Story
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the feed shows besides the list itself, whose load states come from Paging. */
data class FeedUiState(val isOffline: Boolean = false)

@HiltViewModel
class FeedViewModel @Inject constructor(repository: TopStoriesRepository, networkMonitor: NetworkMonitor) :
    ViewModel() {

    /** Cached in the ViewModel so loaded pages survive configuration changes and recomposition. */
    val stories: Flow<PagingData<Story>> = repository.topStories().cachedIn(viewModelScope)

    val uiState: StateFlow<FeedUiState> = networkMonitor.isOnline
        .map { online -> FeedUiState(isOffline = !online) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), FeedUiState())

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
