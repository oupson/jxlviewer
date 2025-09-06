package fr.oupson.jxlviewer.ui.model

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.oupson.jxlviewer.repository.MediaStoreRepository
import fr.oupson.jxlviewer.worker.ExportWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = ViewerViewModel.Factory::class)
class ViewerViewModel @AssistedInject constructor(
    val mediaStoreRepository: MediaStoreRepository,
    val application: Application,
    @Assisted() val imageUri: Uri
) : ViewModel() {

    private val workFlow = ExportWorker.getWorkerFlow(application, imageUri).map {
        it.getOrNull(0)
    }

    private fun checkNotificationPermission() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        application.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    private val notificationPermissionGrantedFlow = MutableStateFlow(false)

    fun exportUiStateFlow() = workFlow.combine(notificationPermissionGrantedFlow) { work, needPermissions ->
        if (needPermissions) {
            WorkerState.NeedNotificationPermission
        } else {
            Log.e(TAG, "${work?.progress} ${work?.progress?.hasKeyWithValueOfType(ExportWorker.PROGRESS, Double::class.java)}")
            when (work?.state) {
                WorkInfo.State.ENQUEUED -> WorkerState.Exporting(null)
                WorkInfo.State.RUNNING -> {
                    val progress = work.progress.getFloat(ExportWorker.PROGRESS, Float.NaN)
                    if (progress.isNaN()) {
                        WorkerState.Exporting(null)
                    } else {
                        WorkerState.Exporting(progress)
                    }
                }

                WorkInfo.State.SUCCEEDED -> WorkerState.Success
                WorkInfo.State.FAILED, WorkInfo.State.BLOCKED, WorkInfo.State.CANCELLED -> WorkerState.Failed
                null -> WorkerState.None
            }
        }
    }

    val exportUiStateFlow = exportUiStateFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), WorkerState.None)

    val nameFlow = flow {
        emit(mediaStoreRepository.getFileName(imageUri))
    }.catch { e ->
        if (Log.isLoggable(TAG, Log.ERROR)) {
            Log.e(TAG, "failed to get filename", e)
        }
        null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), null)

    fun startExport() {
        viewModelScope.launch {
            if (checkNotificationPermission()) {
                notificationPermissionGrantedFlow.emit(false)
                ExportWorker.scheduleWork(application, imageUri)
            } else {
                notificationPermissionGrantedFlow.emit(true)
            }
        }
    }

    fun dismissPermission() {
        viewModelScope.launch {
            notificationPermissionGrantedFlow.emit(false)
        }
    }

    fun cancelExport() {
        viewModelScope.launch {
            ExportWorker.cancel(application, imageUri)
        }
    }

    sealed interface WorkerState {
        data object NeedNotificationPermission : WorkerState
        data class Exporting(val progress: Float?) : WorkerState
        data object Success : WorkerState
        data object Failed : WorkerState
        data object None : WorkerState
    }

    @AssistedFactory
    interface Factory {
        fun create(imageUri: Uri): ViewerViewModel
    }

    companion object {
        private const val TAG = "ViewerViewModel"
    }
}
