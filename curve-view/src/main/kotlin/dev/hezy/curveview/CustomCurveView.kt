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

    private var lineColor: Int
    private var lineWidth: Float
    private var selectedBlockColor: Int
    private var selectedBlockTextColor: Int
    private var axisLineOverlySize: Float

    private val linePath = Path()
    private val graphBgPath = Path()
    private var dataList: List<Float> = emptyList()
    private var maxValue: Float = 0F
    private var points: List<PointF> = emptyList()
    private var currentIndex: Int = 0
    private var selectedBlockIndex = -1

    private val graphPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedBlockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedBlockTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.WeatherCurveView,
            0, 0
        ).apply {
            try {
                lineColor = getColor(R.styleable.WeatherCurveView_lineColor, Color.WHITE)
                lineWidth = getDimension(R.styleable.WeatherCurveView_lineWidth, 2.dp2px)
                selectedBlockColor = getColor(
                    R.styleable.WeatherCurveView_selectedBlockColor,
                    Color.parseColor("#66FFFFFF")
                )
                selectedBlockTextColor =
                    getColor(R.styleable.WeatherCurveView_selectedBlockTextColor, Color.WHITE)
                axisLineOverlySize =
                    getDimension(R.styleable.WeatherCurveView_axisLineOverlySize, 100.dp2px)
            } finally {
                recycle()
            }
        }

        setupPaints()
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

    fun setData(data: List<Float>) {
        dataList = data.apply {
            points = map {
                maxValue = if (maxValue >= it) maxValue else it
                PointF()
            }
        }
    }

    fun setCurrentIndex(index: Int) {
        if (index in dataList.indices) {
            currentIndex = index
            invalidate()
        }
    }

    private val graphBgGradient by lazy {
        LinearGradient(
            0F,
            0F,
            0F,
            measuredHeight.toFloat(),
            intArrayOf(
                Color.parseColor("#33FFFFFF"),
                Color.TRANSPARENT,
            ),
            listOf(0.25F, 1F).toFloatArray(),
            Shader.TileMode.REPEAT
        )
    }

    override fun onDraw(canvas: Canvas) {
        graphPaint.apply {
            style = Paint.Style.FILL
            shader = graphBgGradient
        }
        canvas.drawPath(graphBgPath, graphPaint)

        graphPaint.apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            shader = null
        }
        canvas.drawPath(linePath, graphPaint)

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

            //todo modify to config
            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_cloudy)!!
            val drawableWidth = 40.dp2px
            val drawableHeight = 40.dp2px
            val drawableLeft = left + (blockWidth - drawableWidth) / 2
            val drawableTop = top + blockHeight - drawableHeight - 50.dp2px
            drawable.setBounds(
                drawableLeft.toInt(),
                drawableTop.toInt(),
                (drawableLeft + drawableWidth).toInt(),
                (drawableTop + drawableHeight).toInt()
            )
            drawable.draw(canvas)

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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val w = measuredWidth
        val h = measuredHeight
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
