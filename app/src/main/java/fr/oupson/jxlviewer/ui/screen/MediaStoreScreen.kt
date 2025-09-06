package fr.oupson.jxlviewer.ui.screen

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import fr.oupson.jxlviewer.R
import fr.oupson.jxlviewer.repository.MediaStoreRepository
import fr.oupson.jxlviewer.ui.loading.JxlLoader
import fr.oupson.jxlviewer.ui.loading.rememberJxlLoader
import fr.oupson.jxlviewer.ui.model.BucketListViewModel
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaStoreScreen(
    entryId: Long?,
    backEnabled: Boolean,
    showNames: Boolean,
    onBackPressed: () -> Unit,
    onEntryClick: (MediaStoreRepository.Entry) -> Unit,
    onFilePicked: (Uri) -> Unit
) {
    val listViewModel = hiltViewModel<BucketListViewModel, BucketListViewModel.Factory>(creationCallback = { factory ->
        factory.create(entryId)
    })

    val bucketName by listViewModel.currentBucketName.collectAsState()

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = {
                when (val name = bucketName) {
                    null -> Text(stringResource(R.string.gallery_title))
                    else -> Text(name)
                }
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
            },

            actions = {
            }

        )
    }, floatingActionButton = {
        val openFileLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                onFilePicked.invoke(uri)
            }
        }
        FloatingActionButton(onClick = {
            openFileLauncher.launch(arrayOf("image/jxl"))
        }) {
            Icon(
                painter = painterResource(R.drawable.file_open),
                contentDescription = stringResource(R.string.description_open_file)
            )
        }
    }) {
        Surface(modifier = Modifier.padding(it)) {
            val uiState: BucketListViewModel.UiState by listViewModel.currentElements.collectAsState()

            PullToRefreshBox(
                isRefreshing = uiState is BucketListViewModel.UiState.Loading,
                onRefresh = {
                    listViewModel.reloadList()
                }

            ) {
                when (val state = uiState) {
                    is BucketListViewModel.UiState.EntryList -> {
                        EntryList(state, onClick = { entry ->
                            onEntryClick.invoke(entry)
                        }, showNames = showNames, modifier = Modifier.fillMaxSize())
                    }

                    BucketListViewModel.UiState.Loading -> {}

                    BucketListViewModel.UiState.Error -> {
                        Text(stringResource(R.string.error_loading_files))
                    }

                    BucketListViewModel.UiState.MissingPermissions -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                            modifier = Modifier.fillMaxSize().padding(16.dp)
                        ) {
                            Text(stringResource(R.string.error_missing_permission), textAlign = TextAlign.Justify)

                            val onClick: () -> Unit = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val launcher = rememberLauncherForActivityResult(
                                    ActivityResultContracts.StartActivityForResult()
                                ) {
                                    listViewModel.reloadList()
                                }

                                (
                                    {
                                        launcher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                                    }
                                    )
                            } else {
                                val launcher = rememberLauncherForActivityResult(
                                    contract = ActivityResultContracts.RequestPermission()
                                ) {
                                    listViewModel.reloadList()
                                }

                                (
                                    {
                                        launcher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                                    }
                                    )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.request_permission))
                                }
                                OutlinedButton(onClick = {
                                    val uri = "asset:///logo.jxl"
                                    onFilePicked.invoke(uri.toUri())
                                }, modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.open_example_file))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryList(
    folderList: BucketListViewModel.UiState.EntryList,
    showNames: Boolean,
    onClick: (folder: MediaStoreRepository.Entry) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints {
        val contentWidth = maxWidth - (16 * 2).dp
        val columnCount = max((contentWidth / (196 + 8).dp).toInt(), 2)
        LazyVerticalGrid(
            GridCells.Fixed(columnCount),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
            modifier = modifier
        ) {
            items(folderList.entries) { folder ->
                Item(folder.uri, folder.name, showNames, onClick = {
                    onClick(folder)
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Item(uri: Uri, name: String, showName: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            val painter = rememberJxlLoader(
                uri,
                decodePreview = JxlLoader.DecodePreview.WithoutFullImage,
                animated = false
            )
            val state by painter.state().collectAsState()

            when (val s = state) {
                JxlLoader.JxlState.Empty -> {}
                is JxlLoader.JxlState.Error -> {
                    Image(
                        painterResource(R.drawable.broken_image),
                        contentDescription = stringResource(R.string.error_failed_to_load_file),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.large),
                        contentScale = ContentScale.Crop
                    )
                }

                is JxlLoader.JxlState.Loading -> {
                    Box(contentAlignment = Alignment.Center) {
                        LoadingIndicator(modifier = Modifier.fillMaxSize())
                    }
                }

                is JxlLoader.JxlState.Loaded -> Image(
                    s.painter,
                    contentDescription = stringResource(R.string.description_a_preview_of, name),
                    modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.large),
                    contentScale = ContentScale.Crop
                )

                is JxlLoader.JxlState.Preview -> {
                    Image(
                        s.painter,
                        contentDescription = stringResource(R.string.description_a_preview_of, name),
                        modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.large),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
        if (showName) {
            Text(
                name,
                textAlign = TextAlign.Center,
                maxLines = 1,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
