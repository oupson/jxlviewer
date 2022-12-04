package fr.oupson.jxlviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import fr.oupson.jxlviewer.databinding.ActivityViewBinding
import fr.oupson.libjxl.JxlDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewerActivity : ComponentActivity() {
    private lateinit var binding: ActivityViewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hideSystemBars()
        loadImage()
    }

    // Load image from intent if opened from file picker / other apps, or load default image.
    private fun loadImage() {
        lifecycleScope.launch(Dispatchers.IO) {
            val input = intent?.data?.let { contentResolver.openInputStream(it) }
                ?: resources.assets.open("logo.jxl")

            val image = input.use {
                JxlDecoder.loadJxl(it.readBytes())
            }

            withContext(Dispatchers.Main) {
                binding.test.setImageDrawable(image)
                image?.start()
            }
        }
    }

    // Enable immersive mode.
    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        // Configure the behavior of the hidden system bars
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Hide both the status bar and the navigation bar
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }
}