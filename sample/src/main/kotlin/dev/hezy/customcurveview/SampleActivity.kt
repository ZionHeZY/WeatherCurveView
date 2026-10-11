package dev.hezy.customcurveview

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.hezy.curveview.CustomCurveView
import dev.hezy.customcurveview.databinding.ActivitySampleBinding
import java.util.Locale
import kotlin.math.roundToInt

/** 使用真实 SDK 回调展示温度；示例数据与录屏回放不进入组件库。 */
class SampleActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySampleBinding
    private var winter = false
    private val handler = Handler(Looper.getMainLooper())
    private val demoIndices = intArrayOf(3, 4, 5, 1, 2)
    private var demoStep = 0
    private var demoRunning = false
    private val demo = object : Runnable {
        override fun run() {
            if (!demoRunning) return
            binding.temperature.setCurrentIndex(demoIndices[demoStep++])
            if (demoStep < demoIndices.size) handler.postDelayed(this, 1100L)
            else demoRunning = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        binding = ActivitySampleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setAccessibilityHeading(binding.weatherHeading, true)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        // 大字体增加标签空间，曲线不随字体缩小。
        binding.temperature.layoutParams = binding.temperature.layoutParams.apply {
            height = ((300f + 48f * (resources.configuration.fontScale - 1f).coerceAtLeast(0f)) *
                resources.displayMetrics.density).roundToInt()
            width = ViewGroup.LayoutParams.MATCH_PARENT
        }
        binding.temperature.style = CustomCurveView.Style(selectedBlockColor = 0x33FFFFFF, minimumRange = 32f)
        binding.temperature.valueFormatter = CustomCurveView.ValueFormatter { value ->
            getString(R.string.temperature_value, value.roundToInt())
        }
        binding.temperature.onSelectionChangedListener =
            CustomCurveView.OnSelectionChangedListener { index, value, fromUser ->
                if (fromUser) stopDemo()
                if (index >= 0 && value != null) showReading(index, value)
            }
        binding.temperature.setOnClickListener { stopDemo() }
        winter = savedInstanceState?.getBoolean("winter") ?: false
        showScenario(savedInstanceState?.getInt("selection", 2) ?: 2)
        binding.nextPoint.setOnClickListener {
            stopDemo()
            binding.temperature.setCurrentIndex((binding.temperature.selectedIndex + 1) % binding.temperature.pointCount)
        }
        binding.switchScenario.setOnClickListener {
            stopDemo()
            val index = binding.temperature.selectedIndex.coerceAtLeast(0)
            winter = !winter
            showScenario(index)
        }
        if (savedInstanceState == null && intent.getBooleanExtra("demo", false)) {
            demoRunning = true
            handler.postDelayed(demo, 900L)
        }
    }

    private fun showScenario(index: Int) {
        val data = if (winter) listOf(-8f, -4f, -6f, -10f, 2f, -5f, -7f)
        else listOf(25f, 30f, 28f, 24f, 36f, 29f, 27f)
        val labels = List(data.size) { String.format(Locale.getDefault(), "%02d:00", 6 + it * 2) }
        binding.scenarioName.setText(if (winter) R.string.winter_sample else R.string.warm_sample)
        binding.switchScenario.setText(if (winter) R.string.show_warm else R.string.show_winter)
        binding.temperature.setData(data, data.indices.associateWith { R.drawable.ic_cloudy }, labels)
        binding.temperature.setCurrentIndex(index)
        // 显式刷新初始读数；不依赖重复设置同一选择来触发回调。
        showReading(index, data[index])
    }

    private fun showReading(index: Int, value: Float) {
        binding.selectedValue.text = getString(R.string.temperature_value, value.roundToInt())
        binding.selectedTime.text = getString(R.string.selected_time, 6 + index * 2)
    }

    private fun stopDemo() {
        demoRunning = false
        handler.removeCallbacks(demo)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("winter", winter)
        outState.putInt("selection", binding.temperature.selectedIndex)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        stopDemo()
        super.onStop()
    }
}
