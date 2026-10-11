// SPDX-License-Identifier: MIT
// Copyright (c) 2024-2026 Zhongyang He

package dev.hezy.curveview

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import java.text.DecimalFormat
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * [INPUT]: Android Canvas、有限温度数据及宿主提供的图标/标签。
 * [OUTPUT]: 平滑天气曲线、可配置样式、触摸/键盘/无障碍选择及选择回调。
 * [POS]: curve-view 唯一绘制组件；示例和外部 AAR 消费方共用此实现。
 * 所有数据、样式和选择更新必须在主线程调用，与 Android View 线程约束一致。
 * [PROTOCOL]: 修改公开 API 时同步 README 并检查接入验证。
 */
class CustomCurveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    /** 不可变样式；尺寸按字段名使用 dp/sp，动画时长使用毫秒，跨度使用原始数据单位。 */
    data class Style(
        val lineColor: Int = Color.WHITE,
        val lineWidthDp: Float = 1.5f,
        val gradientColorStart: Int = 0x33FFFFFF,
        val gradientColorEnd: Int = Color.TRANSPARENT,
        val selectedBlockColor: Int = 0x24FFFFFF,
        val selectedBlockTextColor: Int = Color.WHITE,
        val markerColor: Int = 0xFF6EACFF.toInt(),
        val markerBorderColor: Int = Color.WHITE,
        val markerRadiusDp: Float = 4.5f,
        val valueTextSizeSp: Float = 22f,
        val labelTextColor: Int = 0xFFABB5C4.toInt(),
        val labelTextSizeSp: Float = 12f,
        val iconSizeDp: Float = 28f,
        val iconBottomPaddingDp: Float = 12f,
        val iconTint: Int? = null,
        val selectionAnimationDurationMillis: Long = 160L,
        val minimumRange: Float = 12f,
        val showLabels: Boolean = true,
        val emptyText: String = "No weather data"
    )

    /** 主线程回调：清除选择时 index=-1、value=null；仅手势、键盘和无障碍操作标记为用户输入。 */
    fun interface OnSelectionChangedListener {
        fun onSelectionChanged(index: Int, value: Float?, fromUser: Boolean)
    }

    /** 仅格式化显示文本；不改变曲线数据、数值范围或回调值。 */
    fun interface ValueFormatter {
        fun format(value: Float): String
    }

    private val density = resources.displayMetrics.density
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val linePath = Path()
    private val fillPath = Path()
    private val numberFormat = DecimalFormat("0.#")
    private var values = FloatArray(0)
    private var icons = emptyArray<Drawable?>()
    private var labels = emptyList<String>()
    private var displayLabels = emptyArray<String>()
    private var xs = FloatArray(0)
    private var ys = FloatArray(0)
    // 固定范围保存为 [min, max]；null 表示自动缩放，避免两个可空端点出现不一致状态。
    private var fixedRange: DoubleArray? = null
    private var restoredIndex: Int? = null
    private var selectedTextWidth = 0f
    private var selectedText = ""
    private var cellWidth = 0f
    private var contentLeft = 0f
    private var contentTop = 0f
    private var contentRight = 0f
    private var contentBottom = 0f
    private var valueBaseline = 0f
    private var labelBaseline = 0f
    private var radius = 0f
    private var visiblePosition = 0f
    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var touching = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val selectionAnimator = ValueAnimator().apply {
        interpolator = DecelerateInterpolator()
        addUpdateListener {
            visiblePosition = it.animatedValue as Float
            invalidate()
        }
    }

    /** 当前逻辑选择，初始/清除状态为 -1；动画期间不暴露过渡位置。 */
    var selectedIndex: Int = -1
        private set
    /** 未格式化的原始值；没有选择时为 null。 */
    val selectedValue: Float? get() = values.getOrNull(selectedIndex)
    /** 当前数据点总数，不受选择状态影响。 */
    val pointCount: Int get() = values.size
    /** 赋值不会补发当前选择；需要初始事件时在 [setData] 前注册。 */
    var onSelectionChangedListener: OnSelectionChangedListener? = null

    /** 数据或选择变更时更新文本，绘制帧不会重复调用格式化器。 */
    var valueFormatter: ValueFormatter = ValueFormatter { "${numberFormat.format(it)}°C" }
        set(value) {
            field = value
            updateSelectionText()
            invalidate()
        }

    /** 一次应用完整样式并重建缓存；无效尺寸或时长会在替换样式前被拒绝。 */
    var style: Style = Style()
        set(value) {
            validateStyle(value)
            field = value
            selectionAnimator.cancel()
            visiblePosition = selectedIndex.coerceAtLeast(0).toFloat()
            applyStyle()
            updateSelectionText()
            rebuildGeometry()
            invalidate()
        }

    init {
        isFocusable = true
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        style = readStyle(attrs, defStyleAttr)
    }

    /**
     * 复制数据与标签，并缓存图标；保留有效选择，缩短数据时将索引限制在新范围。
     * 空数据清除选择并显示 [Style.emptyText]；回调为非用户事件。
     *
     * @param data 任意有限 Float，可为负数、零、平线或单个数据点。
     * @param drawables 数据索引到 Android drawable 资源 ID 的映射；省略表示不显示图标。
     * @param labels 空列表或与数据等长的时间标签；长文本在显示时省略。
     * @throws IllegalArgumentException 非有限数据、越界图标索引或不等长标签。
     */
    @JvmOverloads
    fun setData(data: List<Float>, drawables: Map<Int, Int> = emptyMap(), labels: List<String> = emptyList()) {
        val copy = data.toFloatArray()
        require(copy.all { it.isFinite() }) { "Weather values must be finite" }
        require(labels.isEmpty() || labels.size == copy.size) { "Labels must match data size" }
        require(drawables.keys.all { it in copy.indices }) { "Drawable index outside data" }
        val newIcons = arrayOfNulls<Drawable>(copy.size)
        for ((index, resource) in drawables) newIcons[index] = context.getDrawable(resource)?.mutate()
        val newLabels = labels.toList()
        val previousIndex = selectedIndex
        val previousValue = selectedValue
        selectionAnimator.cancel()
        values = copy
        icons = newIcons
        this.labels = newLabels
        xs = FloatArray(copy.size)
        ys = FloatArray(copy.size)
        selectedIndex = if (copy.isEmpty()) -1 else {
            (restoredIndex ?: selectedIndex.coerceAtLeast(0)).coerceIn(-1, copy.lastIndex)
        }
        restoredIndex = null
        visiblePosition = selectedIndex.coerceAtLeast(0).toFloat()
        applyIconTint()
        rebuildGeometry()
        updateSelectionText()
        invalidate()
        notifySelection(previousIndex, previousValue, false)
    }

    /** 选择合法索引，-1 清除；重复选择不发送回调，非法索引抛出 IllegalArgumentException。 */
    fun setCurrentIndex(index: Int) = select(index, false)

    /** 保留旧接口的像素单位；新代码可使用 style.iconSizeDp。 */
    fun setDrawableSize(drawableSize: Float) {
        require(drawableSize.isFinite() && drawableSize >= 0f) { "Drawable size must be finite and non-negative" }
        style = style.copy(iconSizeDp = drawableSize / density)
    }

    /** 固定范围外的数据在绘图边界截断，回调仍返回原始数值。 */
    fun setValueRange(min: Float, max: Float) {
        require(min.isFinite() && max.isFinite() && min < max) { "Range must have finite increasing endpoints" }
        fixedRange = doubleArrayOf(min.toDouble(), max.toDouble())
        rebuildGeometry()
        invalidate()
    }

    /** 清除固定范围，恢复带最小跨度及上下留白的自动缩放。 */
    fun clearValueRange() {
        fixedRange = null
        rebuildGeometry()
        invalidate()
    }

    private fun validateStyle(value: Style) {
        require(value.lineWidthDp.isFinite() && value.lineWidthDp > 0f) { "Line width must be positive" }
        require(value.minimumRange.isFinite() && value.minimumRange > 0f) { "Minimum range must be positive" }
        require(value.selectionAnimationDurationMillis >= 0) { "Animation duration must be non-negative" }
        val dimensions = floatArrayOf(value.markerRadiusDp, value.valueTextSizeSp, value.labelTextSizeSp,
            value.iconSizeDp, value.iconBottomPaddingDp)
        require(dimensions.all { it.isFinite() && it >= 0f }) { "Style dimensions must be finite and non-negative" }
    }

    private fun applyStyle() {
        val config = style
        linePaint.apply {
            color = config.lineColor
            strokeWidth = config.lineWidthDp * density
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        blockPaint.color = style.selectedBlockColor
        valuePaint.apply {
            color = config.selectedBlockTextColor
            textSize = spToPx(config.valueTextSizeSp)
            textAlign = Paint.Align.CENTER
        }
        labelPaint.apply {
            color = config.labelTextColor
            textSize = spToPx(config.labelTextSizeSp)
            textAlign = Paint.Align.CENTER
        }
        radius = style.markerRadiusDp * density
        applyIconTint()
    }

    private fun applyIconTint() {
        val tint = style.iconTint
        for (icon in icons) {
            if (tint == null) icon?.setTintList(null) else icon?.setTint(tint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = max(suggestedMinimumWidth, ceil(336f * density).toInt() + paddingLeft + paddingRight)
        val measuredWidth = resolveSizeAndState(desiredWidth, widthMeasureSpec, 0)
        val width = measuredWidth and MEASURED_SIZE_MASK
        val desiredHeight = max(suggestedMinimumHeight,
            ceil((width - paddingLeft - paddingRight).coerceAtLeast(0) * 2f / 3f).toInt() + paddingTop + paddingBottom)
        setMeasuredDimension(measuredWidth, resolveSizeAndState(desiredHeight, heightMeasureSpec, 0))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // padding 和 RTL 可在尺寸不变时变化，几何只在布局阶段更新。
        rebuildGeometry()
    }

    private fun rebuildGeometry() {
        linePath.rewind()
        fillPath.rewind()
        contentLeft = paddingLeft.toFloat()
        contentTop = paddingTop.toFloat()
        contentRight = max(contentLeft, width - paddingRight.toFloat())
        contentBottom = max(contentTop, height - paddingBottom.toFloat())
        cellWidth = if (values.isEmpty()) 0f else (contentRight - contentLeft) / values.size
        valueBaseline = contentTop + 8f * density - valuePaint.ascent()
        labelBaseline = contentBottom - 8f * density - labelPaint.descent()
        if (values.isEmpty() || cellWidth <= 0f || contentBottom <= contentTop) return
        val labelHeight = if (style.showLabels && labels.isNotEmpty()) labelPaint.fontSpacing + 12f * density else 0f
        val hasIcons = icons.any { it != null }
        val iconSize = if (hasIcons) min(style.iconSizeDp * density, max(0f, cellWidth - 8f * density)) else 0f
        val iconBottom = contentBottom - labelHeight - style.iconBottomPaddingDp * density
        val plotTop = min(contentBottom, valueBaseline + valuePaint.descent() + radius + 16f * density)
        val plotBottom = max(plotTop, iconBottom - iconSize - radius - 12f * density)
        val range = fixedRange
        var minimum = range?.get(0) ?: values[0].toDouble()
        var maximum = range?.get(1) ?: minimum
        if (range == null) {
            for (i in 1 until values.size) {
                val value = values[i].toDouble()
                minimum = min(minimum, value)
                maximum = max(maximum, value)
            }
        }
        // 中心/跨度归一化避免大数平线在 min/max 加小余量时丢失精度。
        val center = minimum / 2.0 + maximum / 2.0
        val scale = if (range == null) max(maximum - minimum, style.minimumRange.toDouble()) * 1.2
            else maximum - minimum
        displayLabels = Array(values.size) { index ->
            if (labels.isEmpty()) "" else TextUtils.ellipsize(labels[index], labelPaint,
                max(0f, cellWidth - 8f * density), TextUtils.TruncateAt.END).toString()
        }
        val halfIcon = iconSize / 2f
        for (i in values.indices) {
            val position = if (layoutDirection == LAYOUT_DIRECTION_RTL) values.lastIndex - i else i
            xs[i] = contentLeft + (position + 0.5f) * cellWidth
            val normalized = ((values[i].toDouble() - center) / scale + 0.5).coerceIn(0.0, 1.0)
            ys[i] = plotBottom - (normalized * (plotBottom - plotTop)).toFloat()
            icons[i]?.setBounds((xs[i] - halfIcon).toInt(), (iconBottom - iconSize).toInt(),
                (xs[i] + halfIcon).toInt(), iconBottom.toInt())
        }
        constructPaths()
        fillPaint.shader = LinearGradient(0f, plotTop, 0f, max(plotTop + 1f, contentBottom),
            style.gradientColorStart, style.gradientColorEnd, Shader.TileMode.CLAMP)
    }

    private fun constructPaths() {
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val first = if (rtl) values.lastIndex else 0
        linePath.moveTo(contentLeft, ys[first])
        linePath.lineTo(xs[first], ys[first])
        for (position in 1 until values.size) {
            val i = if (rtl) values.lastIndex - position else position
            val previous = if (rtl) i + 1 else i - 1
            val middle = (xs[previous] + xs[i]) / 2f
            // 保留原曲线的水平切线：控制点位于两端值之间，不产生虚假的温度峰谷。
            linePath.cubicTo(middle, ys[previous], middle, ys[i], xs[i], ys[i])
        }
        linePath.lineTo(contentRight, ys[if (rtl) 0 else values.lastIndex])
        fillPath.set(linePath)
        fillPath.lineTo(contentRight, contentBottom)
        fillPath.lineTo(contentLeft, contentBottom)
        fillPath.close()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val save = canvas.save()
        canvas.clipRect(contentLeft, contentTop, contentRight, contentBottom)
        if (values.isEmpty()) {
            canvas.drawText(style.emptyText, (contentLeft + contentRight) / 2f,
                (contentTop + contentBottom - labelPaint.ascent() - labelPaint.descent()) / 2f, labelPaint)
            canvas.restoreToCount(save)
            return
        }
        if (cellWidth <= 0f) {
            canvas.restoreToCount(save)
            return
        }
        canvas.drawPath(fillPath, fillPaint)
        if (selectedIndex >= 0) {
            val x = markerX()
            canvas.drawRect(x - cellWidth / 2f, contentTop, x + cellWidth / 2f, contentBottom, blockPaint)
            canvas.drawText(selectedText, x.coerceIn(contentLeft + selectedTextWidth / 2f,
                max(contentLeft + selectedTextWidth / 2f, contentRight - selectedTextWidth / 2f)), valueBaseline, valuePaint)
        }
        canvas.drawPath(linePath, linePaint)
        for (index in values.indices) {
            icons[index]?.draw(canvas)
            if (style.showLabels && displayLabels.isNotEmpty()) {
                canvas.drawText(displayLabels[index], xs[index], labelBaseline, labelPaint)
            }
        }
        if (selectedIndex >= 0) {
            val x = markerX()
            val y = markerY()
            markerPaint.color = style.markerBorderColor
            canvas.drawCircle(x, y, radius + linePaint.strokeWidth, markerPaint)
            markerPaint.color = style.markerColor
            canvas.drawCircle(x, y, radius, markerPaint)
        }
        canvas.restoreToCount(save)
    }

    private fun markerX(): Float {
        val position = if (layoutDirection == LAYOUT_DIRECTION_RTL) values.lastIndex - visiblePosition else visiblePosition
        return contentLeft + (position + 0.5f) * cellWidth
    }

    private fun markerY(): Float {
        val i = visiblePosition.toInt().coerceIn(0, values.lastIndex)
        if (i == values.lastIndex) return ys[i]
        // 反求同一条 cubic 的横坐标；只在动画帧计算，不创建 PathMeasure/采样数组。
        val target = visiblePosition - i
        if (target == 0f) return ys[i]
        var low = 0f
        var high = 1f
        repeat(12) {
            val t = (low + high) / 2f
            val x = 1.5f * t - 1.5f * t * t + t * t * t
            if (x < target) low = t else high = t
        }
        val t = (low + high) / 2f
        val eased = t * t * (3f - 2f * t)
        return ys[i] + (ys[i + 1] - ys[i]) * eased
    }

    private fun select(index: Int, fromUser: Boolean) {
        require(index == -1 || index in values.indices) { "Selected index outside data" }
        if (index == selectedIndex) {
            if (touching) {
                selectionAnimator.cancel()
                visiblePosition = index.coerceAtLeast(0).toFloat()
                invalidate()
            }
            return
        }
        val previousIndex = selectedIndex
        val previousValue = selectedValue
        selectionAnimator.cancel()
        selectedIndex = index
        updateSelectionText()
        if (index >= 0 && previousIndex >= 0 && !touching && isShown &&
            style.selectionAnimationDurationMillis > 0 &&
            (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled())) {
            selectionAnimator.setFloatValues(visiblePosition, index.toFloat())
            selectionAnimator.duration = style.selectionAnimationDurationMillis
            selectionAnimator.start()
        } else visiblePosition = index.coerceAtLeast(0).toFloat()
        invalidate()
        notifySelection(previousIndex, previousValue, fromUser)
    }

    private fun updateSelectionText() {
        selectedText = selectedValue?.let { valueFormatter.format(it) } ?: ""
        selectedTextWidth = valuePaint.measureText(selectedText)
        contentDescription = if (selectedIndex < 0) {
            if (values.isEmpty()) style.emptyText else "Weather curve"
        } else if (labels.isEmpty()) selectedText else "${labels[selectedIndex]}, $selectedText"
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    }

    private fun notifySelection(previousIndex: Int, previousValue: Float?, fromUser: Boolean) {
        if (previousIndex != selectedIndex || previousValue != selectedValue) {
            onSelectionChangedListener?.onSelectionChanged(selectedIndex, selectedValue, fromUser)
            if (fromUser) sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED)
        }
    }

    private fun indexAt(x: Float): Int {
        val visual = ((x - contentLeft) / cellWidth).toInt().coerceIn(0, values.lastIndex)
        return if (layoutDirection == LAYOUT_DIRECTION_RTL) values.lastIndex - visual else visual
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || values.isEmpty() || cellWidth <= 0f) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.x !in contentLeft..contentRight || event.y !in contentTop..contentBottom) return false
                downX = event.x
                downY = event.y
                dragging = false
                touching = true
                select(indexAt(event.x), true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!touching) return false
                val dx = abs(event.x - downX)
                val dy = abs(event.y - downY)
                if (!dragging && dy > touchSlop && dy > dx) {
                    touching = false
                    return false
                }
                if (dx > touchSlop) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (dragging) select(indexAt(event.x), true)
            }
            MotionEvent.ACTION_UP -> {
                if (!touching) return false
                touching = false
                parent?.requestDisallowInterceptTouchEvent(false)
                if (!dragging) performClick()
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> {
                touching = false
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isEnabled || values.isEmpty()) return super.onKeyDown(keyCode, event)
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val delta = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (rtl) 1 else -1
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (rtl) -1 else 1
            else -> return super.onKeyDown(keyCode, event)
        }
        select((selectedIndex.coerceAtLeast(0) + delta).coerceIn(0, values.lastIndex), true)
        return true
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.SeekBar"
        if (values.isEmpty()) return
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(
            AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, 0f, values.lastIndex.toFloat(), selectedIndex.coerceAtLeast(0).toFloat())
        if (!isEnabled) return
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
        if (selectedIndex < values.lastIndex) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        if (selectedIndex > 0) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (!isEnabled || values.isEmpty()) return super.performAccessibilityAction(action, arguments)
        val index = when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> (selectedIndex + 1).coerceAtMost(values.lastIndex)
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> (selectedIndex - 1).coerceAtLeast(0)
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id -> {
                if (arguments?.containsKey(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE) != true) return false
                val value = arguments.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)
                if (!value.isFinite() || value < 0 || value > values.lastIndex) return false
                value.toInt()
            }
            else -> return super.performAccessibilityAction(action, arguments)
        }
        select(index, true)
        return true
    }

    override fun onDetachedFromWindow() {
        selectionAnimator.cancel()
        touching = false
        visiblePosition = selectedIndex.coerceAtLeast(0).toFloat()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != VISIBLE) {
            selectionAnimator.cancel()
            visiblePosition = selectedIndex.coerceAtLeast(0).toFloat()
        }
    }

    override fun onSaveInstanceState(): Parcelable = SavedState(super.onSaveInstanceState()).also {
        it.index = restoredIndex ?: selectedIndex
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        if (values.isEmpty()) restoredIndex = state.index else select(state.index.coerceIn(-1, values.lastIndex), false)
    }

    internal class SavedState : BaseSavedState {
        var index: Int = -1
        constructor(superState: Parcelable?) : super(superState)
        constructor(parcel: Parcel) : super(parcel) { index = parcel.readInt() }
        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(index)
        }
        companion object {
            @JvmField val CREATOR = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel) = SavedState(source)
                override fun newArray(size: Int) = arrayOfNulls<SavedState>(size)
            }
        }
    }

    private fun spToPx(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private fun pxToSp(value: Float): Float = if (Build.VERSION.SDK_INT >= 34) {
        TypedValue.deriveDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
    } else value / (density * resources.configuration.fontScale)

    private fun readStyle(attrs: AttributeSet?, defStyleAttr: Int): Style {
        val a = context.obtainStyledAttributes(attrs, R.styleable.WeatherCurveView, defStyleAttr, 0)
        val defaults = Style()
        try {
            return defaults.copy(
                lineColor = a.getColor(R.styleable.WeatherCurveView_lineColor, defaults.lineColor),
                lineWidthDp = a.getDimension(R.styleable.WeatherCurveView_lineWidth, defaults.lineWidthDp * density) / density,
                gradientColorStart = a.getColor(R.styleable.WeatherCurveView_gradientColorStart, defaults.gradientColorStart),
                gradientColorEnd = a.getColor(R.styleable.WeatherCurveView_gradientColorEnd, defaults.gradientColorEnd),
                selectedBlockColor = a.getColor(R.styleable.WeatherCurveView_selectedBlockColor, defaults.selectedBlockColor),
                selectedBlockTextColor = a.getColor(R.styleable.WeatherCurveView_selectedBlockTextColor, defaults.selectedBlockTextColor),
                markerColor = a.getColor(R.styleable.WeatherCurveView_markerColor, defaults.markerColor),
                markerBorderColor = a.getColor(R.styleable.WeatherCurveView_markerBorderColor, defaults.markerBorderColor),
                markerRadiusDp = a.getDimension(R.styleable.WeatherCurveView_markerRadius, defaults.markerRadiusDp * density) / density,
                valueTextSizeSp = pxToSp(a.getDimension(R.styleable.WeatherCurveView_valueTextSize, spToPx(defaults.valueTextSizeSp))),
                labelTextColor = a.getColor(R.styleable.WeatherCurveView_labelTextColor, defaults.labelTextColor),
                labelTextSizeSp = pxToSp(a.getDimension(R.styleable.WeatherCurveView_labelTextSize, spToPx(defaults.labelTextSizeSp))),
                iconSizeDp = a.getDimension(R.styleable.WeatherCurveView_iconSize, defaults.iconSizeDp * density) / density,
                iconBottomPaddingDp = a.getDimension(R.styleable.WeatherCurveView_iconBottomPadding, defaults.iconBottomPaddingDp * density) / density,
                iconTint = if (a.hasValue(R.styleable.WeatherCurveView_iconTint)) a.getColor(R.styleable.WeatherCurveView_iconTint, 0) else null,
                minimumRange = a.getFloat(R.styleable.WeatherCurveView_minimumRange, defaults.minimumRange),
                showLabels = a.getBoolean(R.styleable.WeatherCurveView_showLabels, defaults.showLabels),
                selectionAnimationDurationMillis = a.getInt(R.styleable.WeatherCurveView_selectionAnimationDuration,
                    defaults.selectionAnimationDurationMillis.toInt()).toLong(),
                emptyText = a.getString(R.styleable.WeatherCurveView_emptyText) ?: defaults.emptyText
            )
        } finally { a.recycle() }
    }
}
