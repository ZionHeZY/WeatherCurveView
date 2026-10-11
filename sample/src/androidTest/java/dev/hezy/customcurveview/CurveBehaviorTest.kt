package dev.hezy.customcurveview

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.util.SparseArray
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.hezy.curveview.CustomCurveView
import java.util.concurrent.FutureTask
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** 在真实 Android Canvas 和 Activity 上验证公共行为，不锁定绘制实现。 */
@RunWith(AndroidJUnit4::class)
class CurveBehaviorTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun temperatureBoundariesAndMeasurement() = onMain {
        val view = chart()
        view.setData(listOf(-10f, -5f, 0f))
        val heights = FloatArray(3)
        for (i in heights.indices) {
            view.setCurrentIndex(i)
            val image = render(view)
            heights[i] = marker(image).second
            image.recycle()
        }
        assertTrue("Warmer negative values must move upwards", heights[0] > heights[1] && heights[1] > heights[2])
        for (data in listOf(listOf(0f, 0f, 0f), listOf(-20f), listOf(Float.MAX_VALUE),
            listOf(-Float.MAX_VALUE, Float.MAX_VALUE))) {
            view.setData(data)
            for (i in data.indices) {
                view.setCurrentIndex(i)
                val image = render(view)
                val (x, y) = marker(image)
                assertTrue("Marker is outside content", x in 0f..699f && y in 0f..479f)
                if (data.size == 3) assertEquals("Flat zero curve moved", 0f, y - markerAt(view, 0).second, 0.5f)
                image.recycle()
            }
        }
        view.measure(View.MeasureSpec.makeMeasureSpec(420, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(180, View.MeasureSpec.EXACTLY))
        assertEquals(420, view.measuredWidth)
        assertEquals(180, view.measuredHeight)
        view.measure(View.MeasureSpec.makeMeasureSpec(420, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(160, View.MeasureSpec.AT_MOST))
        assertTrue(view.measuredWidth <= 420 && view.measuredHeight <= 160)
    }

    @Test fun replacementEmptyAndInvalidDataAreAtomic() = onMain {
        val view = chart()
        val source = mutableListOf(10f, 20f, 30f)
        view.setData(source)
        view.setCurrentIndex(2)
        val before = render(view)
        source[2] = 900f
        assertEquals(30f, view.selectedValue!!, 0f)
        for (invalid in listOf(listOf(Float.NaN), listOf(Float.POSITIVE_INFINITY), listOf(Float.NEGATIVE_INFINITY))) {
            rejects { view.setData(invalid) }
            assertEquals(3, view.pointCount)
            assertEquals(2, view.selectedIndex)
            val after = render(view)
            assertTrue("Rejected update changed drawing", before.sameAs(after))
            after.recycle()
        }
        rejects { view.setData(listOf(1f), labels = listOf("one", "two")) }
        rejects { view.setData(listOf(1f), mapOf(2 to R.drawable.ic_cloudy)) }
        rejects { view.setCurrentIndex(4) }
        rejects { view.style = view.style.copy(lineWidthDp = 0f) }
        rejects { view.setValueRange(2f, 2f) }
        before.recycle()
        view.setData(listOf(1f))
        assertEquals(0, view.selectedIndex)
        assertEquals(1f, view.selectedValue!!, 0f)
        view.setData(emptyList())
        assertEquals(-1, view.selectedIndex)
        assertNull(view.selectedValue)
        assertEquals(0, view.pointCount)
        val empty = render(view)
        assertEquals(view.style.emptyText, view.contentDescription)
        val down = event(MotionEvent.ACTION_DOWN, 20f, 20f)
        try { assertFalse(view.onTouchEvent(down)) } finally { down.recycle() }
        empty.recycle()
        view.setData(listOf(0f))
        assertEquals(0, view.selectedIndex)
        assertEquals(0f, view.selectedValue!!, 0f)
    }

    @Test fun selectionCallbacksTouchKeyboardAccessibilityAndRtl() = onMain {
        val view = chart()
        val changes = mutableListOf<Triple<Int, Float?, Boolean>>()
        view.onSelectionChangedListener = CustomCurveView.OnSelectionChangedListener { i, v, user ->
            changes.add(Triple(i, v, user))
        }
        view.setPadding(20, 12, 40, 18)
        view.setData(listOf(-2f, 3f, 8f), labels = listOf("Morning", "Noon", "Evening"))
        view.setCurrentIndex(1)
        view.setCurrentIndex(1)
        assertEquals(listOf(Triple(0, -2f, false), Triple(1, 3f, false)), changes)
        render(view).recycle()
        dispatch(view, MotionEvent.ACTION_DOWN, 650f, 150f)
        dispatch(view, MotionEvent.ACTION_MOVE, 30f, 150f)
        dispatch(view, MotionEvent.ACTION_UP, 30f, 150f)
        assertEquals(0, view.selectedIndex)
        assertEquals(Triple(2, 8f, true), changes[2])
        assertEquals(Triple(0, -2f, true), changes[3])
        view.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(Triple(1, 3f, true), changes.last())
        val args = Bundle().apply { putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, 2f) }
        assertTrue(view.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id, args))
        assertEquals("Evening, 8°C", view.contentDescription)
        assertEquals(Triple(2, 8f, true), changes.last())
        view.isEnabled = false
        assertFalse(view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD, null))
        dispatch(view, MotionEvent.ACTION_DOWN, 30f, 150f)
        assertEquals(2, view.selectedIndex)
        view.isEnabled = true
        view.layoutDirection = View.LAYOUT_DIRECTION_RTL
        render(view).recycle()
        dispatch(view, MotionEvent.ACTION_DOWN, 650f, 150f)
        dispatch(view, MotionEvent.ACTION_UP, 650f, 150f)
        assertEquals(0, view.selectedIndex)
        assertTrue(markerAt(view, 0).first > markerAt(view, 2).first)
        view.setData(listOf(-4f, 3f, 8f))
        view.setCurrentIndex(0)
        assertEquals(Triple(0, -4f, false), changes.last())
        view.setCurrentIndex(-1)
        assertEquals(Triple(-1, null, false), changes.last())
        val count = changes.size
        view.setCurrentIndex(-1)
        assertEquals(count, changes.size)
    }

    @Test fun runtimeStyleRangeResizeAndParcelableSelection() = onMain {
        val view = chart()
        view.setData(listOf(0f, 10f, 20f))
        view.setCurrentIndex(1)
        view.style = view.style.copy(lineColor = Color.GREEN, lineWidthDp = 3f)
        view.valueFormatter = CustomCurveView.ValueFormatter { "$it°F" }
        assertEquals("10.0°F", view.contentDescription)
        val image = render(view)
        val firstY = markerAt(view, 0).second.toInt()
        assertEquals("Runtime line color ignored", Color.GREEN, image.getPixel(21, firstY))
        image.recycle()
        val automaticY = markerAt(view, 2).second
        view.setValueRange(-100f, 100f)
        val fixedY = markerAt(view, 2).second
        assertTrue("Fixed range should reduce vertical excursion", fixedY > automaticY)
        view.clearValueRange()
        assertEquals(automaticY, markerAt(view, 2).second, 0.5f)
        val resized = render(view, 350, 360)
        assertTrue(marker(resized).first < 350f)
        resized.recycle()
        val parcel = Parcel.obtain()
        view.id = R.id.temperature
        val hierarchy = SparseArray<Parcelable>()
        view.saveHierarchyState(hierarchy)
        parcel.writeParcelable(hierarchy[view.id], 0)
        parcel.setDataPosition(0)
        @Suppress("DEPRECATION")
        val copied = parcel.readParcelable<Parcelable>(CustomCurveView::class.java.classLoader)
        parcel.recycle()
        val restored = chart()
        restored.id = view.id
        restored.restoreHierarchyState(SparseArray<Parcelable>().apply { put(view.id, copied) })
        restored.setData(listOf(0f, 10f, 20f))
        assertEquals(2, restored.selectedIndex)
        assertEquals(20f, restored.selectedValue!!, 0f)
    }

    @Test fun actualDemoControlsAndRapidRetargeting() {
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, SampleActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SampleActivity
        try {
            instrumentation.waitForIdleSync()
            onMain {
                val chart = activity.findViewById<CustomCurveView>(R.id.temperature)
                assertEquals(2, chart.selectedIndex)
                assertEquals("28°C", activity.findViewById<TextView>(R.id.selected_value).text.toString())
                activity.findViewById<View>(R.id.next_point).performClick()
                assertEquals(3, chart.selectedIndex)
                assertEquals("24°C", activity.findViewById<TextView>(R.id.selected_value).text.toString())
                activity.findViewById<View>(R.id.switch_scenario).performClick()
                assertEquals(-10f, chart.selectedValue!!, 0f)
                assertEquals("−10°C", activity.findViewById<TextView>(R.id.selected_value).text.toString().replace('-', '−'))
            }
            val chart = onMain { activity.findViewById<CustomCurveView>(R.id.temperature) }
            for (i in 0 until 12) {
                onMain {
                    val before = render(chart, chart.width, chart.height)
                    chart.setCurrentIndex(i % chart.pointCount)
                    val after = render(chart, chart.width, chart.height)
                    val top = before.height / 4
                    val beforePlot = Bitmap.createBitmap(before, 0, top, before.width, before.height - top)
                    val afterPlot = Bitmap.createBitmap(after, 0, top, after.width, after.height - top)
                    try {
                        if (android.os.Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled()) {
                            assertTrue("Retargeting snapped the visible curve selection", beforePlot.sameAs(afterPlot))
                        }
                    } finally {
                        beforePlot.recycle(); afterPlot.recycle(); before.recycle(); after.recycle()
                    }
                }
                android.os.SystemClock.sleep(40)
            }
            onMain { chart.setCurrentIndex(6) }
            android.os.SystemClock.sleep(300)
            onMain {
                assertEquals(-7f, chart.selectedValue!!, 0f)
                val actual = render(chart, chart.width, chart.height)
                val reference = CustomCurveView(activity).apply {
                    style = chart.style.copy(selectionAnimationDurationMillis = 0)
                    valueFormatter = chart.valueFormatter
                    setPadding(chart.paddingLeft, chart.paddingTop, chart.paddingRight, chart.paddingBottom)
                    setData(listOf(-8f, -4f, -6f, -10f, 2f, -5f, -7f),
                        (0..6).associateWith { R.drawable.ic_cloudy }, List(7) { "%02d:00".format(6 + it * 2) })
                    setCurrentIndex(6)
                }
                val expected = render(reference, chart.width, chart.height)
                assertTrue("Retargeted chart differs from final independent render", actual.sameAs(expected))
                actual.recycle()
                expected.recycle()
            }
        } finally { onMain { activity.finish() } }
    }

    private fun chart() = CustomCurveView(instrumentation.targetContext).apply {
        style = style.copy(selectionAnimationDurationMillis = 0, markerColor = Color.RED, markerBorderColor = Color.RED,
            gradientColorStart = Color.TRANSPARENT, gradientColorEnd = Color.TRANSPARENT,
            selectedBlockColor = Color.TRANSPARENT, selectedBlockTextColor = Color.TRANSPARENT)
    }

    private fun render(view: CustomCurveView, width: Int = 700, height: Int = 480): Bitmap {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    private fun markerAt(view: CustomCurveView, index: Int): Pair<Float, Float> {
        view.setCurrentIndex(index)
        val bitmap = render(view)
        return try { marker(bitmap) } finally { bitmap.recycle() }
    }

    private fun marker(bitmap: Bitmap): Pair<Float, Float> {
        var xTotal = 0L
        var yTotal = 0L
        var count = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            if (Color.alpha(pixel) > 128 && Color.red(pixel) > 200 && Color.green(pixel) < 80 && Color.blue(pixel) < 80) {
                xTotal += x; yTotal += y; count++
            }
        }
        assertTrue("Expected a selected temperature marker", count > 0)
        return xTotal.toFloat() / count to yTotal.toFloat() / count
    }

    private fun event(action: Int, x: Float, y: Float) = MotionEvent.obtain(0, 0, action, x, y, 0)
    private fun dispatch(view: View, action: Int, x: Float, y: Float) {
        val event = event(action, x, y)
        try { view.onTouchEvent(event) } finally { event.recycle() }
    }
    private fun rejects(action: () -> Unit) {
        try { action(); fail("Invalid input accepted") } catch (_: IllegalArgumentException) { }
    }
    private fun <T> onMain(action: () -> T): T {
        val task = FutureTask(java.util.concurrent.Callable(action))
        instrumentation.runOnMainSync(task)
        return task.get()
    }
}
