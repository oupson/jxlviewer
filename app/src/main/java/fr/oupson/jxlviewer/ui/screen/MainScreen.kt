package fr.oupson.jxlviewer.ui.screen

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSavedStateNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.rememberSceneSetupNavEntryDecorator
import fr.oupson.jxlviewer.ui.nav.BucketList
import fr.oupson.jxlviewer.ui.nav.BucketView
import fr.oupson.jxlviewer.ui.nav.ImageView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(initialDestination: NavKey) {
    val backStack = rememberNavBackStack(initialDestination)

    NavDisplay(
        backStack = backStack,
        entryDecorators =
        listOf(
            // Add the default decorators for managing scenes and saving state
            rememberSceneSetupNavEntryDecorator(),
            rememberSavedStateNavEntryDecorator(),
            // Then add the view model store decorator
            rememberViewModelStoreNavEntryDecorator()
        ),
        onBack = {
            backStack.removeAt(backStack.lastIndex)
        },
        predictivePopTransitionSpec = {
            EnterTransition.None togetherWith
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(200)
                )
        },
        entryProvider =
        entryProvider {
            entry<BucketList> {
                MediaStoreScreen(
                    entryId = null,
                    backEnabled = backStack.size > 1,
                    showNames = true,
                    onBackPressed = {
                        backStack.removeAt(backStack.lastIndex)
                    },
                    onEntryClick = { entry ->
                        backStack.add(BucketView(entry.id))
                    },
                    onFilePicked = { uri ->
                        backStack.add(ImageView(uri.toString()))
                    }
                )
            }

            entry<BucketView> { entry ->
                MediaStoreScreen(
                    entryId = entry.id,
                    backEnabled = backStack.size > 1,
                    showNames = false,
                    onBackPressed = {
                        backStack.removeAt(backStack.lastIndex)
                    },
                    onEntryClick = { entry ->
                        backStack.add(ImageView(entry.uri.toString()))
                    },
                    onFilePicked = { uri ->
                        backStack.add(ImageView(uri.toString()))
                    }
                )
            }
            entry<ImageView> { entry ->
                ViewerScreen(
                    entry.uri.toUri(),
                    backEnabled = backStack.size > 1,
                    onBackPressed = {
                        backStack.removeAt(backStack.lastIndex)
                    }
                )
            }
        }
    )
}
