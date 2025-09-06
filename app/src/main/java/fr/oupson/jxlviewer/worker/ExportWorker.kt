package fr.oupson.jxlviewer.worker

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import fr.oupson.jxlviewer.R
import fr.oupson.jxlviewer.repository.impl.MediaStoreRepositoryImpl
import fr.oupson.jxlviewer.util.getMatrixForExifOrientation
import fr.oupson.libjxl.JxlDecoder
import java.io.FileNotFoundException
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.log10
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ExportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val mediaStoreRepository = MediaStoreRepositoryImpl(context.applicationContext as Application)

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val jxlDecoder = JxlDecoder.getInstance()

    override suspend fun doWork(): Result {
        coroutineScope {
            val fileUri = requireNotNull(inputData.getString(FILE_URI)?.toUri())
            val filename = mediaStoreRepository.getFileName(fileUri) ?: applicationContext.getString(R.string.unknown)

            val workFlow = getWorkFlow(fileUri, filename).shareIn(this, SharingStarted.Lazily)

            launch {
                workFlow.takeWhile { it !is WorkState.Success }.collect { p ->
                    when (p) {
                        WorkState.Loading -> setProgress(workDataOf(PROGRESS to null))
                        is WorkState.Progress -> setProgress(workDataOf(PROGRESS to p.exported.toFloat() / p.total.toFloat()))
                        is WorkState.Success -> setProgress(workDataOf(PROGRESS to 1.0f))
                    }
                }
            }

            launch {
                val notificationId = ID.andDecrement
                workFlow.takeWhile { it !is WorkState.Success }.collect { progress ->
                    val notification = createNotification(progress, filename)
                    when (progress) {
                        WorkState.Loading -> {
                            val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                                ForegroundInfo(notificationId, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
                            } else {
                                ForegroundInfo(notificationId, notification)
                            }
                            setForeground(foregroundInfo)
                        }

                        is WorkState.Progress -> {
                            notificationManager.notify(notificationId, notification)
                        }

                        is WorkState.Success -> {
                            notificationManager.notify(notificationId, notification)
                        }
                    }
                }
            }
        }
        Log.e("TAG", "END")
        return Result.success()
    }

    @OptIn(FlowPreview::class)
    fun getWorkFlow(fileUri: Uri, filename: String) = channelFlow {
        launch {
            val frameCount = getFrameCount(fileUri)

            val progress = WorkState.Progress(frameCount, 0)
            send(progress)

            val btmConfig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Bitmap.Config.RGBA_F16
            } else {
                Bitmap.Config.ARGB_8888
            }

            var count = 0

            val bucketName = if (frameCount > 1) {
                filename.substringBeforeLast(".")
            } else {
                null
            }
            val format = "%0${(log10(frameCount.toDouble()) + 1).toInt()}d.png"

            var matrix = Matrix()

            val options = JxlDecoder.Options().setFormat(btmConfig).setDecodeFrames(true).setDecodeProgressive(false)
            val callback = object : JxlDecoder.Callback {
                override fun onHeaderDecoded(
                    width: Int,
                    height: Int,
                    intrinsicWidth: Int,
                    intrinsicHeight: Int,
                    isAnimated: Boolean,
                    orientation: Int
                ): Boolean {
                    matrix = getMatrixForExifOrientation(orientation, width, height)
                    return isActive
                }

                override fun onProgressiveFrame(btm: Bitmap): Boolean = isActive

                override fun onFrameDecoded(duration: Int, btm: Bitmap): Boolean {
                    runBlocking(Dispatchers.IO) {
                        val filename = if (frameCount > 1) {
                            format.format(count)
                        } else {
                            filename.substringBeforeLast(".") + ".png"
                        }

                        val saveBtm = btm.rotateBitmap(matrix)
                        val path = requireNotNull(mediaStoreRepository.insertMedia(filename, bucketName))
                        applicationContext.contentResolver.openOutputStream(path)!!.use { out ->
                            saveBtm.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }

                        count += 1
                        send(progress.copy(exported = count))
                    }
                    return isActive
                }
            }

            decodeJxl(fileUri, options, callback)
            send(WorkState.Success(frameCount))

            close()
        }
    }.onStart { emit(WorkState.Loading) }.debounce { p -> if (p is WorkState.Progress) 1000L else 0L }

    sealed interface WorkState {
        data object Loading : WorkState
        data class Progress(val total: Int, val exported: Int) : WorkState
        data class Success(val total: Int) : WorkState
    }

    fun getFrameCount(uri: Uri): Int {
        val options = JxlDecoder.Options().setDecodeFrames(false).setDecodeProgressive(false)
        val callback = object : JxlDecoder.Callback {
            override fun onHeaderDecoded(
                width: Int,
                height: Int,
                intrinsicWidth: Int,
                intrinsicHeight: Int,
                isAnimated: Boolean,
                orientation: Int
            ): Boolean = true

            override fun onProgressiveFrame(btm: Bitmap?): Boolean = true
            override fun onFrameDecoded(duration: Int, btm: Bitmap?): Boolean = true
        }

        return decodeJxl(uri, options, callback)
    }

    fun decodeJxl(uri: Uri, options: JxlDecoder.Options, callback: JxlDecoder.Callback) = when (uri.scheme) {
        "http", "https" -> {
            URL(uri.toString()).openConnection().inputStream.use {
                jxlDecoder.decodeImage(
                    it,
                    options,
                    callback
                )
            }
        }

        "asset" -> {
            applicationContext.assets.open(requireNotNull(uri.path).removePrefix("/"), AssetManager.ACCESS_STREAMING).use { inputStream ->
                jxlDecoder.decodeImage(
                    inputStream,
                    options,
                    callback
                )
            }
        }

        else -> {
            try {
                applicationContext.contentResolver.openFileDescriptor(uri, "r")!!.use { fd ->
                    jxlDecoder.decodeImage(
                        fd,
                        options,
                        callback
                    )
                }
            } catch (_: FileNotFoundException) {
                applicationContext.contentResolver.openInputStream(uri)!!.use { inputStream ->
                    jxlDecoder.decodeImage(
                        inputStream,
                        options,
                        callback
                    )
                }
            }
        }
    }

    private fun Bitmap.rotateBitmap(transformMatrix: Matrix): Bitmap = if (transformMatrix.isIdentity) {
        this
    } else {
        Bitmap.createBitmap(this, 0, 0, this.width, this.height, transformMatrix, true)
    }

    private fun createNotification(progress: WorkState, fileName: String): Notification {
        val channelId = applicationContext.getString(R.string.export_notification_channel_id)
        val title = applicationContext.getString(R.string.export_notification_title)
        val cancel = applicationContext.getString(R.string.export_notification_cancel)
        val intent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel(channelId)
        }
        val notification =
            NotificationCompat.Builder(applicationContext, channelId).setContentTitle(title).setTicker(title).setContentText(fileName)
                .setSmallIcon(
                    R.drawable.save_as
                ).setOngoing(true).addAction(android.R.drawable.ic_delete, cancel, intent).setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW).setGroup(GROUP_KEY)

        when (progress) {
            WorkState.Loading -> notification.setProgress(1, 0, true)
            is WorkState.Progress -> notification.setProgress(progress.total, progress.exported, false)
            is WorkState.Success -> {}
        }

        return notification.build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel(channelId: String) {
        val title = applicationContext.getString(R.string.export_notification_channel_title)
        val description = applicationContext.getString(R.string.export_notification_channel_description)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelId, title, importance)
        channel.description = description
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        const val PROGRESS = "PROGRESS"
        const val FILE_URI = "FILE_URI"
        private const val GROUP_KEY = "EXPORT"

        private val ID = AtomicInteger(0)

        fun getWorkerFlow(context: Context, uri: Uri): Flow<List<WorkInfo>> =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(uri.toString())

        fun scheduleWork(context: Context, uri: Uri) {
            val stringUri = uri.toString()
            val workRequest = OneTimeWorkRequestBuilder<ExportWorker>().setInputData(workDataOf(FILE_URI to stringUri))
                .keepResultsForAtLeast(10L, TimeUnit.MINUTES).build()

            val workManager = WorkManager.getInstance(context)
            workManager.enqueueUniqueWork(stringUri, ExistingWorkPolicy.REPLACE, workRequest)
        }

        fun cancel(context: Context, uri: Uri) {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork(uri.toString())
        }
    }
}
