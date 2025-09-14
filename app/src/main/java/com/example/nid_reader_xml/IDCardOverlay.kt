package com.example.nid_reader_xml

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class IDCardOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val overlayPaint = Paint().apply {
        color = Color.BLACK
        alpha = 100 // Semi-transparent overlay
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    private val idCardAspectRatio = 1.586f // Standard ID card ratio (85.6mm x 54mm)
    private var cardRect = RectF()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Calculate ID card rectangle dimensions (90% of view width, positioned 20% from top)
        val cardWidth = w * 0.9f
        val cardHeight = cardWidth / idCardAspectRatio
        cardRect.left = (w - cardWidth) / 2
        cardRect.top = h * 0.2f // 20% from top
        cardRect.right = cardRect.left + cardWidth
        cardRect.bottom = cardRect.top + cardHeight
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw semi-transparent overlay for the entire view
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlayPaint)
        // Clear the ID card area (transparent)
        canvas.drawRect(cardRect, Paint().apply {
            color = Color.TRANSPARENT
            style = Paint.Style.FILL
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
        })
        // Draw white border around the ID card area
        canvas.drawRect(cardRect, borderPaint)
    }

    fun getOverlayRect(): RectF {
        return RectF(cardRect)
    }
}