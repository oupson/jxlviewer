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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentElements: StateFlow<UiState> = cookieFlow.transformLatest {
        emit(UiState.Loading)
        try {
            emit(UiState.EntryList(listEntries()))
        } catch (e: MediaStoreRepository.MissingPermissionsException) {

            emit(UiState.MissingPermissions)
        } catch (e: Exception) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "during folder list with bucketId: $bucketId", e)
            }
            emit(UiState.Error)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UiState.Loading
    )

    val currentBucketName = cookieFlow.map {
        try {
            queryBucketName()
        } catch (e: MediaStoreRepository.MissingPermissionsException) {
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
        // TODO: keep old list
        data object Loading : UiState

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
