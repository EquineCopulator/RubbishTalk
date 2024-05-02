package com.equinus.rubbishtalk

import android.view.MotionEvent
import android.view.View

class swipeListener(
    private val cond:(p0: View?, p1: MotionEvent, x_src:Float, y_src:Float) -> Boolean,
    private val l:(p0: View?, p1: MotionEvent, x_src:Float, y_src:Float) -> Boolean
) : View.OnTouchListener {
    private var x_src = 0F
    private var y_src = 0F
    private var executed = true
    override fun onTouch(p0: View?, p1: MotionEvent?): Boolean {
        if (p1 != null) {
            when(p1.action) {
                MotionEvent.ACTION_DOWN -> {
                    x_src = p1.x
                    y_src = p1.y
                    executed = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!executed && cond(p0, p1, x_src, y_src)) {
                        executed = true
                        return l(p0, p1, x_src, y_src)
                    }
                }
                MotionEvent.ACTION_UP -> p0?.performClick()
            }
        }
        return false
    }
}