package fr.oupson.jxlviewer.repository.impl

import android.Manifest
import android.app.Application
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import fr.oupson.jxlviewer.R
import fr.oupson.jxlviewer.repository.MediaStoreRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MediaStoreRepositoryImpl @Inject constructor(private val application: Application) : MediaStoreRepository {
    private val contentResolver: ContentResolver = application.contentResolver

    private val projection = arrayOf(
        MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME
    )

    private val folderProjection = arrayOf(
        MediaStore.MediaColumns._ID, MediaStore.MediaColumns.BUCKET_ID, MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
    )

    private val selectionNoBucket = "${MediaStore.MediaColumns.DATA} LIKE ? OR ${MediaStore.MediaColumns.MIME_TYPE} = ?"
    private val selectionBucket =
        "${MediaStore.MediaColumns.BUCKET_ID} = ? AND (${MediaStore.MediaColumns.DATA} LIKE ? OR ${MediaStore.MediaColumns.MIME_TYPE} = ?)"
    private val selectionFilename = "${MediaStore.MediaColumns.BUCKET_ID} = ?"

    private val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

    private val collection = MediaStore.Files.getContentUri("external")

    fun isPermissionMissing(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        !Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(application, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
    }

    override suspend fun listBuckets(): List<MediaStoreRepository.Entry> = withContext(Dispatchers.IO) {
        if (isPermissionMissing()) {
            throw MediaStoreRepository.MissingPermissionsException()
        }

        val unknownName = application.getString(R.string.unknown)

        val selectionArgs = arrayOf("%.jxl", "image/jxl")
        contentResolver.query(
            collection, folderProjection, selectionNoBucket, selectionArgs, sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val buckets = mutableSetOf<Long>()

            buildList {
                while (cursor.moveToNext()) {
                    val bucketId = cursor.getLong(bucketIdColumn)

                    if (bucketId in buckets) {
                        continue
                    }

                    buckets.add(bucketId)

                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: unknownName
                    val contentUri: Uri = ContentUris.withAppendedId(
                        collection, id
                    )

                    add(MediaStoreRepository.Entry(bucketId, contentUri, name))
                }
            }
        } ?: emptyList()
    }

    override suspend fun listMedias(bucketId: Long): List<MediaStoreRepository.Entry> = withContext(Dispatchers.IO) {
        if (isPermissionMissing()) {
            throw MediaStoreRepository.MissingPermissionsException()
        }

        val unknownName = application.getString(R.string.unknown)
        val selectionArgs = arrayOf(bucketId.toString(), "%.jxl", "image/jxl")
        val sortOrder = ""
        contentResolver.query(collection, projection, selectionBucket, selectionArgs, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: unknownName

                    val contentUri: Uri = ContentUris.withAppendedId(
                        collection, id
                    )
                    add(MediaStoreRepository.Entry(id, contentUri, name))
                }
            }
        } ?: emptyList()
    }

    override suspend fun getBucketName(bucketId: Long): String? = withContext(Dispatchers.IO) {
        if (isPermissionMissing()) {
            throw MediaStoreRepository.MissingPermissionsException()
        }
        val selectionArgs = arrayOf(bucketId.toString())
        val uri = collection.buildUpon().appendQueryParameter("limit", "1").build()
        val cursor = contentResolver.query(
            uri, folderProjection, selectionFilename, selectionArgs, sortOrder
        )?.use { cursor ->
            if (cursor.moveToNext()) {
                cursor.getString(2)
            } else {
                null
            }
        }
        cursor
    }

    override suspend fun getFileName(fileUri: Uri): String? = withContext(Dispatchers.IO) {
        when (fileUri.scheme) {
            "http", "https", "file", "asset" -> {
                fileUri.path?.substringAfterLast('/')
            }

            else -> {
                contentResolver.query(
                    fileUri, arrayOf(
                        MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.TITLE
                    ), null, null, null
                )?.use { cursor ->
                    val indexDisplayName = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    val indexTitle = cursor.getColumnIndex(MediaStore.MediaColumns.TITLE)
                    if (cursor.moveToFirst()) {
                        cursor.getString(indexDisplayName) ?: cursor.getString(indexTitle)
                    } else {
                        null
                    }
                }
            }
        }
    }

    override suspend fun insertMedia(mediaName: String, bucketName: String?): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            insertMediaAndroid10(mediaName, bucketName)
        } else {
            insertMediaLegacy(mediaName, bucketName)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun insertMediaAndroid10(mediaName: String, bucketName: String?): Uri? {
        val imageCollection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val newImageDetails = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, mediaName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (bucketName != null) {
                put(MediaStore.Images.Media.RELATIVE_PATH, joinPath(Environment.DIRECTORY_PICTURES, bucketName))
            }
        }

        return withContext(Dispatchers.IO) {
            contentResolver.insert(imageCollection, newImageDetails)
        }
    }

    private suspend fun insertMediaLegacy(mediaName: String, bucketName: String?): Uri? {
        var dirPath = Environment.getExternalStorageDirectory().resolve(Environment.DIRECTORY_PICTURES)
        if (bucketName != null) {
            dirPath = dirPath.resolve(bucketName)
        }
        withContext(Dispatchers.IO) {
            dirPath.mkdirs()
        }
        return dirPath.resolve(mediaName).toUri()
    }

    private fun joinPath(parent: String, child: String): String = buildString {
        append(parent)
        if (!parent.endsWith("/")) {
            append('/')
        }
        append(child)
    }
}
