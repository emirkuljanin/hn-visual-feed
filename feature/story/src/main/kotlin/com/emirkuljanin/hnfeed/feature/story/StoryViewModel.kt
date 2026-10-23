package com.emirkuljanin.hnfeed.feature.story

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emirkuljanin.hnfeed.core.data.repository.StoryDetailsRepository
import com.emirkuljanin.hnfeed.core.model.Story
import com.emirkuljanin.hnfeed.core.model.StoryComments
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StoryUiState(
    /** `null` until the story is cached (first open, while the refresh runs or after it failed). */
    val story: Story? = null,
    val comments: List<CommentListItem> = emptyList(),
    val isRefreshing: Boolean = true,
    val refreshFailed: Boolean = false,
)

/**
 * The story screen: refreshes the story and its first comments when opened, then loads more comments as the UI
 * asks. Which comments have their replies expanded is UI state, kept across process death; loaded comments are in
 * the database.
 */
@HiltViewModel(assistedFactory = StoryViewModel.Factory::class)
class StoryViewModel @AssistedInject constructor(
    @Assisted private val storyId: Long,
    private val repository: StoryDetailsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val expanded = savedStateHandle.getMutableStateFlow(KEY_EXPANDED, longArrayOf())
    private val loading = MutableStateFlow(emptySet<Long>())
    private val failed = MutableStateFlow(emptySet<Long>())

    /** Starts as running: the refresh is launched from `init`, so the first frame already shows it. */
    private val refresh = MutableStateFlow(RefreshState.RUNNING)
    private var refreshJob: Job? = null

    /** Also read directly, to know whether an expanded comment needs its replies loaded. */
    private val comments: StateFlow<StoryComments?> =
        repository.comments(storyId).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<StoryUiState> = combine(
        repository.story(storyId),
        comments,
        expanded,
        combine(loading, failed, ::Pair),
        refresh,
    ) { story, comments, expanded, (loading, failed), refresh ->
        StoryUiState(
            story = story,
            comments = if (story == null || comments == null) {
                emptyList()
            } else {
                buildCommentList(
                    storyId = storyId,
                    storyAuthor = story.author,
                    comments = comments,
                    expanded = expanded.toSet(),
                    loading = loading,
                    failed = failed,
                    isRefreshing = refresh == RefreshState.RUNNING,
                )
            },
            isRefreshing = refresh == RefreshState.RUNNING,
            refreshFailed = refresh == RefreshState.FAILED,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), StoryUiState())

    init {
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refresh.value = RefreshState.RUNNING
        refreshJob = viewModelScope.launch {
            refresh.value = try {
                repository.refresh(storyId)
                // A refresh replaces the first page of every level, so earlier "load more" failures are moot.
                failed.value = emptySet()
                RefreshState.DONE
            } catch (e: IOException) {
                RefreshState.FAILED
            }
        }
    }

    /** Shows or hides the replies of [commentId], loading the first ones if none are loaded yet. */
    fun toggleReplies(commentId: Long) {
        val isExpanded = commentId in expanded.value
        expanded.update { ids -> if (isExpanded) ids.filterNot { it == commentId }.toLongArray() else ids + commentId }
        val noRepliesLoaded = comments.value?.comments?.none { it.parentId == commentId } ?: true
        if (!isExpanded && noRepliesLoaded) loadMore(commentId)
    }

    /** Loads the next comments under [parentId]: the story id for top-level comments. */
    fun loadMore(parentId: Long) {
        if (parentId in loading.value) return
        loading.update { it + parentId }
        failed.update { it - parentId }
        viewModelScope.launch {
            try {
                repository.loadMoreComments(storyId, parentId)
            } catch (e: IOException) {
                failed.update { it + parentId }
            } finally {
                loading.update { it - parentId }
            }
        }
    }

    private enum class RefreshState { RUNNING, DONE, FAILED }

    @AssistedFactory
    interface Factory {
        fun create(storyId: Long): StoryViewModel
    }

    private companion object {
        const val KEY_EXPANDED = "expanded"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
