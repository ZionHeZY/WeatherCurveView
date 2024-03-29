package dev.hezy.customcurveview

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.hezy.customcurveview.databinding.ActivitySampleBinding

class SampleActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySampleBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initView()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun initView() {
        binding = ActivitySampleBinding.inflate(layoutInflater)

        val graphData = listOf(25f, 30f, 28f, 24f, 36f, 29f, 27f)
        val drawablesForDataPoints = graphData.indices.associateWith { R.drawable.ic_cloudy }
        binding.temperature.setData(graphData, drawablesForDataPoints)

        binding.temperature.setCurrentIndex(2)
        setContentView(binding.root)
    }
}