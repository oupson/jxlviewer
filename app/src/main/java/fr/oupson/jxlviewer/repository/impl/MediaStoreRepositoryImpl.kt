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
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import fr.oupson.jxlviewer.repository.MediaStoreRepository
import javax.inject.Inject
import kotlin.io.path.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaStoreRepositoryImpl @Inject constructor(private val application: Application) : MediaStoreRepository {
    private val contentResolver: ContentResolver = application.contentResolver

    private val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME
    )

    private val folderProjection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.BUCKET_ID,
        MediaStore.Images.Media.BUCKET_DISPLAY_NAME
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
        val selectionArgs = arrayOf("%.jxl", "image/jxl")
        contentResolver.query(
            collection,
            folderProjection,
            selectionNoBucket,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val buckets = mutableSetOf<Long>()

            buildList {
                while (cursor.moveToNext()) {
                    val bucketId = cursor.getLong(bucketIdColumn)

                    if (bucketId in buckets) {
                        continue
                    }

                    buckets.add(bucketId)

                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "no name" // TODO
                    val contentUri: Uri = ContentUris.withAppendedId(
                        collection,
                        id
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

        val selectionArgs = arrayOf(bucketId.toString(), "%.jxl", "image/jxl")
        val sortOrder = ""
        contentResolver.query(collection, projection, selectionBucket, selectionArgs, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)

                    val contentUri: Uri = ContentUris.withAppendedId(
                        collection,
                        id
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
            uri,
            folderProjection,
            selectionFilename,
            selectionArgs,
            sortOrder
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
                if (isPermissionMissing()) {
                    throw MediaStoreRepository.MissingPermissionsException()
                }

                contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && index >= 0) {
                        cursor.getString(index)
                    } else {
                        null
                    }
                }
            }
        }
    }

    override suspend fun insertMedia(mediaName: String, bucketName: String?): Uri? {
        val imageCollection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY
                )
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

        val newImageDetails = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, mediaName)
            if (bucketName != null) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Path(Environment.DIRECTORY_PICTURES, bucketName).toString())
            }
        }

        return contentResolver
            .insert(imageCollection, newImageDetails)
    }
}
