package fr.oupson.jxlviewer.activity

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import fr.oupson.jxlviewer.ui.nav.BucketList
import fr.oupson.jxlviewer.ui.nav.ImageView
import fr.oupson.jxlviewer.ui.screen.MainScreen
import fr.oupson.jxlviewer.ui.theme.AppTheme


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        val initialDestination = getIntentDestination() ?: BucketList

        setContent {
            AppTheme {
                MainScreen(initialDestination)
            }
        }
    }

    private fun getIntentDestination(): ImageView? {
        val intent = this.intent ?: return null
        return if (intent.action == Intent.ACTION_VIEW) {
            val intentData = this.intent?.data
            if (intentData != null) {
                ImageView(intentData.toString())
            } else {
                null
            }
        } else {
            null
        }
    }
}
