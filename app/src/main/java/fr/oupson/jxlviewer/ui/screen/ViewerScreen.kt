package fr.oupson.jxlviewer.ui.screen

import android.Manifest
import android.content.Intent
import android.content.Intent.CATEGORY_DEFAULT
import android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent.FLAG_ACTIVITY_NO_HISTORY
import android.content.pm.ActivityInfo
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalFloatingToolbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import fr.oupson.jxlviewer.BuildConfig
import fr.oupson.jxlviewer.R
import fr.oupson.jxlviewer.ui.loading.JxlLoader
import fr.oupson.jxlviewer.ui.loading.rememberJxlLoader
import fr.oupson.jxlviewer.ui.model.ViewerViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ViewerScreen(imageUri: Uri, backEnabled: Boolean, onBackPressed: () -> Unit) {
    var showUiElements by remember { mutableStateOf(true) }
    val viewerViewModel = hiltViewModel<ViewerViewModel, ViewerViewModel.Factory>(creationCallback = { factory ->
        factory.create(imageUri)
    })

    val wideGamut = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val activity = LocalActivity.current
        val isWideGamutSupported = remember {
            if (activity != null) {
                ContextCompat.getDisplayOrDefault(activity).isWideColorGamut
            } else {
                false
            }
        }

        DisposableEffect(activity, isWideGamutSupported) {
            if (activity != null) {
                activity.window.colorMode = ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT

            }
            onDispose {
                if (activity != null) {
                    activity.window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
                }
            }
        }

        isWideGamutSupported
    } else {
        false
    }

    val exportUiState by viewerViewModel.exportUiStateFlow.collectAsState()

    if (exportUiState == ViewerViewModel.WorkerState.NeedNotificationPermission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NeedPermissionDialog(
                description = stringResource(R.string.export_notification_permission_description),
                permission = Manifest.permission.POST_NOTIFICATIONS,
                onPermissionResult = { viewerViewModel.startExport() },
                onDismissRequest = {
                    viewerViewModel.dismissPermission()
                })
        }
    }

    if (exportUiState == ViewerViewModel.WorkerState.NeedWriteFilePermission) {
        NeedPermissionDialog(
            description = stringResource(R.string.export_notification_write_storage_description),
            permission = Manifest.permission.WRITE_EXTERNAL_STORAGE,
            onPermissionResult = { viewerViewModel.startExport() },
            onDismissRequest = {
                viewerViewModel.dismissPermission()
            })
    }

    val name by viewerViewModel.nameFlow.collectAsState()

    Box {
        Surface(modifier = Modifier.fillMaxSize()) {
            val bitmapConfig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && wideGamut) {
                Bitmap.Config.RGBA_F16
            } else {
                Bitmap.Config.ARGB_8888
            }
            val painter = rememberJxlLoader(
                imageUri, decodePreview = JxlLoader.DecodePreview.WithFullImage, animated = true, config = bitmapConfig
            )
            val state by painter.state().collectAsState()

            var scale by remember { mutableFloatStateOf(1f) }
            var rotation by remember { mutableFloatStateOf(0f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            val transformableState = rememberTransformableState { zoomChange, offsetChange, rotationChange ->
                scale *= zoomChange
                rotation += rotationChange
                offset += offsetChange
            }

            when (val s = state) {
                JxlLoader.JxlState.Empty -> {}
                is JxlLoader.JxlState.Error -> {
                    Image(
                        painterResource(R.drawable.broken_image),
                        contentDescription = stringResource(R.string.error_failed_to_load_file),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                is JxlLoader.JxlState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }

                is JxlLoader.JxlState.Loaded -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {
                            showUiElements = !showUiElements
                        })
                        .transformable(state = transformableState)
                ) {
                    Image(
                        s.painter, contentDescription = if (name != null) {
                            stringResource(R.string.description_a_preview_of, requireNotNull(name))
                        } else {
                            stringResource(R.string.description_a_preview_no_name)
                        }, modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale, scaleY = scale, rotationZ = rotation, translationX = offset.x, translationY = offset.y
                            ), contentScale = ContentScale.Fit
                    )
                }

                is JxlLoader.JxlState.Preview -> {
                    Image(
                        s.painter, contentDescription = if (name != null) {
                            stringResource(R.string.description_a_preview_of, requireNotNull(name))
                        } else {
                            stringResource(R.string.description_a_preview_no_name)
                        }, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit
                    )
                }
            }
        }
        if (showUiElements) {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.25f)),
                title = {
                    Text(name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    if (backEnabled) {
                        IconButton(onClick = {
                            onBackPressed.invoke()
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = stringResource(R.string.description_go_back)
                            )
                        }
                    }
                })
        }

        if (showUiElements) {
            if (LocalConfiguration.current.orientation == ORIENTATION_LANDSCAPE) {
                VerticalFloatingToolbar(
                    expanded = true,
                    shape = MaterialTheme.shapes.large,
                    colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .windowInsetsPadding(WindowInsets.safeContent)
                        .padding(8.dp)
                ) {
                    ToolBarContent(viewerViewModel, exportUiState, name, imageUri)
                }
            } else {
                HorizontalFloatingToolbar(
                    expanded = true,
                    shape = MaterialTheme.shapes.large,
                    colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeContent)
                        .padding(8.dp)
                ) {
                    ToolBarContent(viewerViewModel, exportUiState, name, imageUri)
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ToolBarContent(viewerViewModel: ViewerViewModel, exportUiState: ViewerViewModel.WorkerState, name: String?, imageUri: Uri) {
    when (val s = exportUiState) {
        is ViewerViewModel.WorkerState.Exporting -> {
            IconButton(onClick = {
                viewerViewModel.cancelExport()
            }) {
                if (s.progress == null) {
                    CircularWavyProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    CircularWavyProgressIndicator(progress = { s.progress }, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }

        ViewerViewModel.WorkerState.Failed -> {
            IconButton(onClick = {
                viewerViewModel.startExport()
            }) {
                Icon(
                    painter = painterResource(R.drawable.broken_image),
                    contentDescription = stringResource(R.string.description_export_failure)
                )
            }
        }

        ViewerViewModel.WorkerState.Success -> {
            IconButton(onClick = {
                viewerViewModel.startExport()
            }) {
                Icon(
                    painter = painterResource(R.drawable.check), contentDescription = stringResource(R.string.description_export_success)
                )
            }
        }

        ViewerViewModel.WorkerState.NeedNotificationPermission, ViewerViewModel.WorkerState.NeedWriteFilePermission, ViewerViewModel.WorkerState.None -> {
            IconButton(onClick = {
                viewerViewModel.startExport()
            }) {
                Icon(
                    painter = painterResource(R.drawable.save_as), contentDescription = stringResource(R.string.description_save_file)
                )
            }
        }
    }

    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    IconButton(onClick = {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, name)
            putExtra(Intent.EXTRA_STREAM, imageUri)
            type = "image/jxl"
        }
        shareLauncher.launch(sendIntent)
    }) {
        Icon(painter = painterResource(R.drawable.share), contentDescription = stringResource(R.string.share_file))
    }
}

@Composable
fun NeedPermissionDialog(description: String, permission: String, onPermissionResult: () -> Unit, onDismissRequest: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onPermissionResult.invoke()
    }

    val launcherSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onPermissionResult.invoke()
    }

    AlertDialog(title = {
        Text(stringResource(R.string.permission_needed))
    }, text = {
        Text(description)
    }, onDismissRequest = onDismissRequest, confirmButton = {
        val activity = LocalActivity.current
        TextButton(onClick = {
            val showRational = (activity)?.shouldShowRequestPermissionRationale(
                permission
            ) ?: false

            if (showRational) {
                val intent = Intent(ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                    addCategory(CATEGORY_DEFAULT)
                    addFlags(FLAG_ACTIVITY_NEW_TASK)
                    addFlags(FLAG_ACTIVITY_NO_HISTORY)
                    addFlags(FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                }
                launcherSettings.launch(intent)
            } else {
                launcher.launch(permission)
            }
        }) {
            Text(stringResource(R.string.request_permission))
        }
    })
}
