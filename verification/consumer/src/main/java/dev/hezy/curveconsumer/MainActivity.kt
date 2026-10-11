package dev.hezy.curveconsumer

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import dev.hezy.curveview.CustomCurveView

class MainActivity : Activity() {
    lateinit var curve: CustomCurveView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_consumer)
        curve = findViewById(R.id.curve)
        curve.style = curve.style.copy(markerColor = Color.CYAN, selectionAnimationDurationMillis = 0)
        curve.valueFormatter = CustomCurveView.ValueFormatter { "$it C" }
        curve.onSelectionChangedListener = CustomCurveView.OnSelectionChangedListener { _, _, fromUser ->
            check(!fromUser)
        }
        curve.setData(listOf(-2f, 4f, 1f), mapOf(1 to android.R.drawable.ic_menu_compass), listOf("06:00", "12:00", "18:00"))
        curve.setValueRange(-10f, 10f)
        curve.setCurrentIndex(2)
        curve.clearValueRange()
        JavaApiConsumer.exercise(curve)
    }
}
