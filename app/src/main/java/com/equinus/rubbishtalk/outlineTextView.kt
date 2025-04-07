package com.equinus.rubbishtalk

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.widget.TextView

@SuppressLint("AppCompatCustomView")
class outlineTextView(context: Context): TextView(context) {
    companion object {
        private const val range = """[\u2600-\u27BF\U0001F300-\U0001F64F\U0001F900-\U0001F9FF]"""
        val rg = Regex("(?<!$range)(?=$range).+?(?<=$range)(?!$range)")
    }
    var outline_color:Int = -1
    var outline_width:Float = 0f

    private class EmojiFontSpan(context: Context): android.text.style.MetricAffectingSpan() {
        private val emoji_font:android.graphics.Typeface? = null
        init { android.graphics.Typeface.createFromAsset(context.resources.assets, "font/noto_bold.ttf") }
        override fun updateDrawState(ds: TextPaint) {
            ds.typeface = emoji_font
        }
        override fun updateMeasureState(paint: TextPaint) {
            paint.typeface = emoji_font
        }
    }

    override fun onDraw(canvas: Canvas) {
        val color_original = currentTextColor

        paint.strokeWidth = paint.textSize * outline_width
        paint.style = Paint.Style.STROKE
        setTextColor(outline_color)
        super.onDraw(canvas)

        paint.style = Paint.Style.FILL
        setTextColor(color_original)
        super.onDraw(canvas)

        paint.strokeWidth = paint.textSize * 0.01f
        paint.style = Paint.Style.STROKE
        setTextColor(outline_color)
        super.onDraw(canvas)

        paint.style = Paint.Style.FILL
        setTextColor(color_original)
    }

    fun DoEmoji(context: Context) {
        val s = android.text.SpannableString(text)
        for (m in rg.findAll(text)) {
            android.util.Log.i(null, m.value)
            android.util.Log.i(null, m.range.toString())
            s.setSpan(EmojiFontSpan(context), m.range.first, m.range.last + 1, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        text = s
    }
}