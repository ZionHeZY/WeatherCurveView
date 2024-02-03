package dev.hezy.curveview

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat

class CustomCurveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var dataPointDrawables: Map<Int, Int> = emptyMap()
    private var gradientColorStart: Int
    private var gradientColorEnd: Int
    private var lineColor: Int
    private var lineWidth: Float
    private var selectedBlockColor: Int
    private var selectedBlockTextColor: Int
    private var axisLineOverlySize: Float = 0F

    private var dataList: List<Float> = emptyList()
    private var maxValue: Float = 0F
    private var points: List<PointF> = emptyList()
    private var currentIndex: Int = 0
    private var selectedBlockIndex = -1
    private var iconBottomPadding = 20.dp2px
    private var iconSize = 40.dp2px

    private val graphPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedBlockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedBlockTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePath = Path()
    private val graphBgPath = Path()

    init {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.WeatherCurveView,
            0, 0
        ).apply {
            try {
                gradientColorStart = getColor(
                    R.styleable.WeatherCurveView_gradientColorStart,
                    Color.parseColor("#33FFFFFF")
                )
                gradientColorEnd =
                    getColor(R.styleable.WeatherCurveView_gradientColorEnd, Color.TRANSPARENT)
                lineColor = getColor(R.styleable.WeatherCurveView_lineColor, Color.WHITE)
                lineWidth = getDimension(R.styleable.WeatherCurveView_lineWidth, 2.dp2px)
                selectedBlockColor = getColor(
                    R.styleable.WeatherCurveView_selectedBlockColor,
                    Color.parseColor("#66FFFFFF")
                )
                selectedBlockTextColor =
                    getColor(R.styleable.WeatherCurveView_selectedBlockTextColor, Color.WHITE)
            } finally {
                recycle()
            }
        }

        setupPaints()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawGraphBackground(canvas)
        drawGraphLine(canvas)
        drawDataBlocks(canvas)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val originalWidth = MeasureSpec.getSize(widthMeasureSpec)
        val originalHeight = MeasureSpec.getSize(heightMeasureSpec)

        val calculatedHeight = (originalWidth * 2) / 3
        val calculatedWidth = (originalHeight * 3) / 2

        val finalWidth: Int
        val finalHeight: Int

        if (calculatedHeight <= originalHeight) {
            finalWidth = originalWidth
            finalHeight = calculatedHeight
        } else {
            finalWidth = calculatedWidth
            finalHeight = originalHeight
        }

        setMeasuredDimension(finalWidth, finalHeight)
        calculatePoints()
    }

    private fun setupPaints() {
        graphPaint.apply {
            strokeWidth = lineWidth
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
            color = lineColor
        }

        selectedBlockPaint.apply {
            strokeWidth = lineWidth
            strokeCap = Paint.Cap.ROUND
            color = selectedBlockColor
        }

        selectedBlockTextPaint.apply {
            color = selectedBlockTextColor
            textSize = 44f.dp2px
        }
    }

    fun setData(data: List<Float>, drawables: Map<Int, Int> = emptyMap()) {
        dataList = data.apply {
            points = map {
                maxValue = if (maxValue >= it) maxValue else it
                PointF()
            }
        }

        dataPointDrawables = drawables
        calculatePoints()
        invalidate()
    }

    fun setCurrentIndex(index: Int) {
        if (index in dataList.indices) {
            currentIndex = index
            selectedBlockIndex = index
            invalidate()
        }
    }

    fun setDrawableSize(drawableSize: Float) {
        iconSize = drawableSize
        invalidate()
    }

    private val graphBgGradient by lazy {
        LinearGradient(
            0F,
            0F,
            0F,
            measuredHeight.toFloat(),
            intArrayOf(
                gradientColorStart,
                gradientColorEnd,
            ),
            listOf(0.25F, 1F).toFloatArray(),
            Shader.TileMode.REPEAT
        )
    }

    private fun drawDataBlocks(canvas: Canvas) {
        val blockWidth = (width - paddingLeft - paddingRight) / dataList.size
        val blockHeight = height - paddingTop - paddingBottom

        dataList.forEachIndexed { index, value ->
            val left = paddingLeft + index * blockWidth
            val top = paddingTop
            val right = left + blockWidth
            val bottom = top + blockHeight

            if (index == selectedBlockIndex) {
                canvas.drawRect(
                    left.toFloat(),
                    top.toFloat() - axisLineOverlySize,
                    right.toFloat(),
                    bottom.toFloat(),
                    selectedBlockPaint
                )

                selectedBlockTextPaint.textSize = 44f
                val text = "${value.toInt()}℃"
                val textWidth = selectedBlockTextPaint.measureText(text)
                val textHeight =
                    selectedBlockTextPaint.fontMetrics.bottom - selectedBlockTextPaint.fontMetrics.top
                val textX = left + (blockWidth - textWidth) / 2

                val textY =
                    top + selectedBlockTextPaint.fontMetrics.bottom + textHeight

                canvas.drawText(text, textX, textY, selectedBlockTextPaint)
            }

            dataPointDrawables[index]?.let { drawableRes ->
                val drawable = ContextCompat.getDrawable(context, drawableRes)
                drawable?.let {
                    val drawableWidth = iconSize
                    val drawableHeight = iconSize
                    val drawableLeft = left + (blockWidth - drawableWidth) / 2
                    val drawableTop = top + blockHeight - drawableHeight - iconBottomPadding
                    it.setBounds(
                        drawableLeft.toInt(),
                        drawableTop.toInt(),
                        (drawableLeft + drawableWidth).toInt(),
                        (drawableTop + drawableHeight).toInt()
                    )
                    it.draw(canvas)
                }
            }

            val currentPoint = points[currentIndex]
            val circleRadius = lineWidth * 4.5F

            graphPaint.color = Color.WHITE
            graphPaint.style = Paint.Style.FILL
            canvas.drawCircle(currentPoint.x, currentPoint.y, circleRadius, graphPaint)

            graphPaint.color = Color.parseColor("#FF6EACFF")
            graphPaint.style = Paint.Style.FILL
            canvas.drawCircle(currentPoint.x, currentPoint.y, circleRadius - lineWidth, graphPaint)
        }
    }

    private fun drawGraphLine(canvas: Canvas) {
        graphPaint.apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            shader = null
        }
        canvas.drawPath(linePath, graphPaint)
    }

    private fun drawGraphBackground(canvas: Canvas) {
        graphPaint.apply {
            style = Paint.Style.FILL
            shader = graphBgGradient
        }
        canvas.drawPath(graphBgPath, graphPaint)
    }

    private fun calculatePoints() {
        if (points.size != dataList.size) {
            points = List(dataList.size) { PointF() }
        }

        val w = measuredWidth
        val h = measuredHeight

        axisLineOverlySize = h * 0.2f
        val availableH = h - paddingTop - paddingBottom - lineWidth * 4F - axisLineOverlySize

        val oneSpace =
            (w.toFloat() - paddingStart - paddingEnd) / dataList.size
        val leftStart = paddingStart.toFloat()
        val graphTop = paddingTop + lineWidth * 1.5F + axisLineOverlySize

        points.forEachIndexed { i, p ->
            p.x = leftStart + i * oneSpace + oneSpace / 2
            dataList[i].also {
                p.y = graphTop + (availableH - it / maxValue * availableH)
            }
        }
        constructLinePath()
        constructBackgroundPath(h, oneSpace, leftStart)
    }

    private fun constructLinePath() {
        linePath.reset()
        var startP: PointF
        var endP: PointF
        for (i in 0 until points.lastIndex) {
            startP = points[i]
            endP = points[i + 1]

            val specWidth = (endP.x - startP.x) / 2
            if (i == 0) {
                val extendedStartX = startP.x - specWidth
                linePath.moveTo(startP.x - specWidth, startP.y)
                linePath.cubicTo(extendedStartX, startP.y, startP.x, startP.y, startP.x, startP.y)
            }

            ((startP.x + endP.x) / 2F).also {
                linePath.cubicTo(it, startP.y, it, endP.y, endP.x, endP.y)
            }

            if (i == points.lastIndex - 1) {
                val extendedEndX = endP.x + specWidth
                linePath.cubicTo(endP.x, endP.y, extendedEndX, endP.y, extendedEndX, endP.y)
            }
        }
    }

    private fun constructBackgroundPath(h: Int, oneSpace: Float, leftStart: Float) {
        graphBgPath.set(linePath)
        val bottom = h - paddingBottom
        graphBgPath.lineTo(points.last().x + oneSpace / 2, bottom.toFloat())
        graphBgPath.lineTo(leftStart, bottom.toFloat())
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x.toInt()
                val y = event.y.toInt()

                val blockWidth = (width - paddingLeft - paddingRight) / dataList.size
                val blockIndex = (x - paddingLeft) / blockWidth

                if (blockIndex in dataList.indices) {
                    selectedBlockIndex = blockIndex

                    currentIndex = blockIndex
                    invalidate()
                }
            }
        }
        return true
    }

    private val Int.dp2px: Float
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), Resources.getSystem().displayMetrics
        )

    private val Float.dp2px: Float
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, this, Resources.getSystem().displayMetrics
        )
}
