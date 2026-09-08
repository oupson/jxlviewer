package fr.oupson.jxlviewer.ui.model

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.oupson.jxlviewer.repository.MediaStoreRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel(assistedFactory = ViewerViewModel.Factory::class)
class ViewerViewModel @AssistedInject constructor(
    private val mediaStoreRepository: MediaStoreRepository,
    @Assisted() val imageUri: Uri
) : ViewModel() {

    val nameFlow = flow {
        emit(mediaStoreRepository.getFileName(imageUri))
    }.catch { e ->
        if (Log.isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, "failed to get filename", e)
        }
        null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    @AssistedFactory
    interface Factory {
        fun create(imageUri: Uri): ViewerViewModel
    }

    companion object {
        private const val TAG = "ViewerViewModel"
    }
}
