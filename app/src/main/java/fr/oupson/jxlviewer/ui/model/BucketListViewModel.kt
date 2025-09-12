package fr.oupson.jxlviewer.ui.model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.oupson.jxlviewer.repository.MediaStoreRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = BucketListViewModel.Factory::class)
class BucketListViewModel
@AssistedInject constructor(val mediaStoreRepository: MediaStoreRepository, @Assisted() val bucketId: Long?) :
    ViewModel() {
    suspend fun listEntries(): List<MediaStoreRepository.Entry> = if (bucketId != null) {
        mediaStoreRepository.listMedias(bucketId)
    } else {
        mediaStoreRepository.listBuckets()
    }

    suspend fun queryBucketName(): String? = if (bucketId != null) {
        mediaStoreRepository.getBucketName(bucketId)
    } else {
        null
    }

    private val cookieFlow = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val currentElements: StateFlow<UiState> =
        cookieFlow.transformLatest {
            emit(UiState.Loading(emptyList()))
            try {
                emit(UiState.EntryList(listEntries()))
            } catch (_: MediaStoreRepository.MissingPermissionsException) {
                emit(UiState.MissingPermissions)
            } catch (e: Exception) {
                if (Log.isLoggable(TAG, Log.ERROR)) {
                    Log.e(TAG, "during folder list with bucketId: $bucketId", e)
                }
                emit(UiState.Error)
            }
        }.runningFold<UiState, UiState>(UiState.Loading(emptyList())) { previous, next ->
            if (previous is UiState.EntryList && next is UiState.Loading) {
                UiState.Loading(previous.entries)
            } else {
                next
            }
        }.debounce {
            if (it is UiState.Loading) {
                100
            } else {
                0
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading(emptyList()))

    val currentBucketName = cookieFlow.map {
        try {
            queryBucketName()
        } catch (_: MediaStoreRepository.MissingPermissionsException) {
            null
        } catch (e: Exception) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "during folder list with bucketId: $bucketId", e)
            }
            null
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    fun reloadList() {
        viewModelScope.launch {
            cookieFlow.update { it + 1 }
        }
    }

    sealed interface UiState {
        data class Loading(val entries: List<MediaStoreRepository.Entry>) : UiState

        data object MissingPermissions : UiState

        data object Error : UiState

        data class EntryList(val entries: List<MediaStoreRepository.Entry>) : UiState
    }

    @AssistedFactory
    interface Factory {
        fun create(bucketId: Long?): BucketListViewModel
    }

    companion object {
        const val TAG = "ListFolderViewModel"
    }
}
