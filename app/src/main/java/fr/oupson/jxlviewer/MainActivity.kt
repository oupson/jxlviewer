package fr.oupson.jxlviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import fr.oupson.jxlviewer.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch(Dispatchers.IO) {
            val input = intent?.data?.let { contentResolver.openInputStream(it) }
                ?: resources.assets.open("logo.jxl")

            val image = input.use {
                JxlDecoder().loadJxl(it.readBytes())
            }

            withContext(Dispatchers.Main) {
                binding.test.setImageDrawable(image)
                image.start()
            }
        }
    }
}