package fr.oupson.jxlviewer.repository

import android.net.Uri

interface MediaStoreRepository {
    data class Entry(val id: Long, val uri: Uri, val name: String)

    class MissingPermissionsException : Exception("Missing permission to access medias")

    @Throws(MissingPermissionsException::class)
    suspend fun listBuckets(): List<Entry>

    @Throws(MissingPermissionsException::class)
    suspend fun listMedias(bucketId: Long): List<Entry>

    @Throws(MissingPermissionsException::class)
    suspend fun getBucketName(bucketId: Long): String?

    @Throws(MissingPermissionsException::class)
    suspend fun getFileName(fileUri: Uri): String?

    suspend fun insertMedia(mediaName: String, bucketName: String?): Uri?
}
