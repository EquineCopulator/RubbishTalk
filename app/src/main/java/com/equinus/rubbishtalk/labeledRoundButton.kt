package com.equinus.rubbishtalk

import android.content.Context
import android.widget.*
import android.util.AttributeSet
import android.util.TypedValue

class LabeledRoundButton:RelativeLayout {
    constructor(context:Context):super(context) {
        val diameter = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 100f, context.resources.displayMetrics).toInt()
        layoutParams = MarginLayoutParams(diameter, diameter).apply {
            marginEnd = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, context.resources.displayMetrics).toInt()
        }
    }
    constructor(constext:Context, attr:AttributeSet?):super(constext, attr) { if (attr != null) applyAttr(attr) }
    constructor(constext:Context, attr:AttributeSet?, defStyleAttr:Int)
            :super(constext, attr, defStyleAttr) { if (attr != null) applyAttr(attr) }

    var color:Int
        get() { return 0 }
        set(value) { iv.colorFilter = ToFilter(value) }
    var text:CharSequence
        get() { return tv.text }
        set(value) {
            tv.text = value
            val count = text.count { c:Char->c.isLetterOrDigit() }
            tv.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, if (count < 4) 25f else 80f / count, context.resources.displayMetrics)
        }
    fun setCircleOnClickListener(r:(android.view.View)->Unit) {
        iv.setOnClickListener(r)
    }
    var textSize get() = tv.textSize
        set(value) { tv.textSize = value }

    @android.annotation.SuppressLint("UseCompatLoadingForDrawables")
    private val iv = ImageView(context)
    init {
        iv.colorFilter = ToFilter(-1)
        iv.setImageDrawable(if (android.os.Build.VERSION.SDK_INT >= 21)
            context.resources.getDrawable(R.drawable.draw_redcirclebutton, null)
        else @Suppress("DEPRECATION") context.resources.getDrawable(R.drawable.draw_redcirclebutton))
        iv.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }
    private val tv = TextView(context)
    init {
        tv.gravity = android.view.Gravity.CENTER
        tv.typeface = android.graphics.Typeface.defaultFromStyle(android.graphics.Typeface.ITALIC)
        tv.setTextColor(0xFF000000U.toInt())
        tv.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }
    init {
        addView(iv)
        addView(tv)
    }

    private fun ToFilter(color:Int):android.graphics.ColorMatrixColorFilter {
        return android.graphics.ColorMatrixColorFilter(floatArrayOf(
            (color ushr 16 and 0xFF).toFloat() / 0xFF, 0f, 0f, 0f, 0f,
            0f, (color ushr 8 and 0xFF).toFloat() / 0xFF, 0f, 0f, 0f,
            0f, 0f, (color and 0xFF).toFloat() / 0xFF, 0F, 0F,
            0f, 0f, 0f, (color ushr 24 and 0xFF).toFloat() / 0xFF, 0f))
    }

    private fun applyAttr(attr:AttributeSet) {
        color = attr.getAttributeUnsignedIntValue("android", "color", -1)
        text = attr.getAttributeValue("android", "text") ?: ""
    }
}