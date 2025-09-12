package fr.oupson.jxlviewer.ui.nav

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object BucketList : NavKey

@Serializable
data class BucketView(val id: Long) : NavKey

@Serializable
data class ImageView(val uri: String) : NavKey
