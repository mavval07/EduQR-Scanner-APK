package id.eduqr.scanner

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.math.min

class ScannerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density

    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(145, 9, 13, 22)
        style = Paint.Style.FILL
    }

    private val innerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#10B981")
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val laserPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val scanRect = RectF()
    private val cornerPath = Path()
    private var laserFraction = 0f
    private var isFlashingSuccess = false

    private val laserAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1900L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { anim ->
            laserFraction = anim.animatedValue as Float
            invalidate()
        }
    }

    private val resetFlashRunnable = Runnable {
        isFlashingSuccess = false
        cornerPaint.color = Color.parseColor("#10B981")
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!laserAnimator.isStarted) {
            laserAnimator.start()
        }
    }

    override fun onDetachedFromWindow() {
        laserAnimator.cancel()
        removeCallbacks(resetFlashRunnable)
        super.onDetachedFromWindow()
    }

    fun flashScanSuccess() {
        removeCallbacks(resetFlashRunnable)
        isFlashingSuccess = true
        cornerPaint.color = Color.parseColor("#34D399")
        invalidate()
        postDelayed(resetFlashRunnable, 450L)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val boxSize = min(w * 0.68f, min(h * 0.62f, 250f * density))
        val left = (w - boxSize) / 2f
        val top = (h - boxSize) / 2f - (8f * density)
        val right = left + boxSize
        val bottom = top + boxSize
        scanRect.set(left, top, right, bottom)

        canvas.drawRect(0f, 0f, w, top, maskPaint)
        canvas.drawRect(0f, bottom, w, h, maskPaint)
        canvas.drawRect(0f, top, left, bottom, maskPaint)
        canvas.drawRect(right, top, w, bottom, maskPaint)

        val radius = 14f * density
        canvas.drawRoundRect(scanRect, radius, radius, innerBorderPaint)

        val arm = 28f * density
        cornerPath.reset()
        cornerPath.moveTo(left, top + arm)
        cornerPath.lineTo(left, top + radius)
        cornerPath.quadTo(left, top, left + radius, top)
        cornerPath.lineTo(left + arm, top)

        cornerPath.moveTo(right - arm, top)
        cornerPath.lineTo(right - radius, top)
        cornerPath.quadTo(right, top, right, top + radius)
        cornerPath.lineTo(right, top + arm)

        cornerPath.moveTo(right, bottom - arm)
        cornerPath.lineTo(right, bottom - radius)
        cornerPath.quadTo(right, bottom, right - radius, bottom)
        cornerPath.lineTo(right - arm, bottom)

        cornerPath.moveTo(left + arm, bottom)
        cornerPath.lineTo(left + radius, bottom)
        cornerPath.quadTo(left, bottom, left, bottom - radius)
        cornerPath.lineTo(left, bottom - arm)

        canvas.drawPath(cornerPath, cornerPaint)

        val pad = 12f * density
        val laserY = (top + pad) + laserFraction * (boxSize - 2f * pad)
        val laserHeight = 3f * density
        laserPaint.shader = LinearGradient(
            left + pad, laserY, right - pad, laserY,
            intArrayOf(
                Color.TRANSPARENT,
                if (isFlashingSuccess) Color.parseColor("#6EE7B7") else Color.parseColor("#10B981"),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(
            left + pad,
            laserY - laserHeight / 2f,
            right - pad,
            laserY + laserHeight / 2f,
            laserHeight,
            laserHeight,
            laserPaint
        )
    }
}
