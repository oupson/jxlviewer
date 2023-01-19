package fr.oupson.jxlviewer

import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.util.Log
import android.widget.Toast
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
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class ViewerActivity : ComponentActivity() {
    companion object {
        private const val TAG = "ViewerActivity"
    }

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
            try {
                val image = intent?.data?.let {
                    when (it.scheme) {
                        "https" -> {
                            val conn = (URL(it.toString()).openConnection() as HttpURLConnection)
                            conn.connect()

                            val img = loadImage(conn.inputStream)
                            conn.disconnect()

                            img
                        }
                        else -> loadImage(contentResolver.openInputStream(it)!!)
                    }
                }
                    ?: loadImage(resources.assets.open("logo.jxl"))

                withContext(Dispatchers.Main) {
                    binding.test.setImageDrawable(image)
                    image?.start()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load image", e)
                withContext(Dispatchers.Main) {
                    binding.test.setImageResource(R.drawable.baseline_error_outline_24)
                    Toast.makeText(this@ViewerActivity, R.string.image_load_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadImage(input: InputStream): AnimationDrawable? = input.use {
        JxlDecoder.loadJxl(it.readBytes())
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