package com.emirkuljanin.hnfeed.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.emirkuljanin.hnfeed.core.data.repository.TopStoriesRepository
import com.emirkuljanin.hnfeed.core.model.Story
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

@HiltViewModel
class FeedViewModel @Inject constructor(repository: TopStoriesRepository) : ViewModel() {

    /** Cached in the ViewModel so loaded pages survive configuration changes and recomposition. */
    val stories: Flow<PagingData<Story>> = repository.topStories().cachedIn(viewModelScope)
}
