package dev.hezy.curveconsumer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConsumerTest {
    @Test fun publishedAarInflatesAndExposesKotlinAndJavaApis() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as MainActivity
        try {
            instrumentation.waitForIdleSync()
            instrumentation.runOnMainSync {
                assertEquals(android.graphics.Color.WHITE, activity.curve.style.lineColor)
                assertEquals(3f, activity.curve.style.lineWidthDp, 0.01f)
                assertEquals(3, activity.curve.pointCount)
                assertEquals(1, activity.curve.selectedIndex)
                assertEquals(4f, activity.curve.selectedValue!!, 0f)
                val bitmap = Bitmap.createBitmap(activity.curve.width, activity.curve.height, Bitmap.Config.ARGB_8888)
                activity.curve.draw(Canvas(bitmap))
                var markerX = 0L
                var markerY = 0L
                var markerPixels = 0
                for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                    val pixel = bitmap.getPixel(x, y)
                    if (android.graphics.Color.alpha(pixel) > 128 &&
                        android.graphics.Color.red(pixel) < 40 &&
                        android.graphics.Color.green(pixel) > 200 &&
                        android.graphics.Color.blue(pixel) > 200) {
                        markerX += x
                        markerY += y
                        markerPixels++
                    }
                }
                assertEquals("Published marker did not render at the selected column",
                    bitmap.width / 2f, markerX.toFloat() / markerPixels, 2f)
                assertTrue("Published marker escaped the drawable chart",
                    markerY.toFloat() / markerPixels in 0f..bitmap.height.toFloat())
                bitmap.recycle()
            }
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
