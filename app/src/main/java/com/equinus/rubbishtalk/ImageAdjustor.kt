package com.equinus.rubbishtalk

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

class ImageAdjustor(
    context:android.content.Context,
    width:Int,
    height:Int,
    private val view:View,
    private val prev:()->Unit,
    private val next:()->Unit,
    private val retime:()->Unit)
{
    private val semiWidth = width.toFloat() / 2
    private val semiHeight = height.toFloat() / 2

    private val scroller = GestureDetector(context, GestureListener(view))
    private val enlarger = ScaleGestureDetector(context, ScaleListener(semiWidth, semiHeight, view))

    companion object {
        private const val SCROLL_DISTANCE = 250f

        private fun resetImage(view:View) {
            view.x = 0f
            view.y = 0f
            view.scaleX = 1f
            view.scaleY = 1f
        }
    }

    private class GestureListener(private val v:View)
        :GestureDetector.SimpleOnGestureListener()
    {
        override fun onScroll(e1:MotionEvent, e2:MotionEvent, distanceX:Float, distanceY:Float):Boolean {
            v.x -= distanceX
            v.y -= distanceY
            return true
        }

        override fun onDoubleTap(e:MotionEvent):Boolean {
            resetImage(v)
            return true
        }
    }

    private class ScaleListener(
        private val semiWidth:Float,
        private val semiHeight:Float,
        private val v:View)
        :ScaleGestureDetector.SimpleOnScaleGestureListener()
    {
        override fun onScale(detector:ScaleGestureDetector):Boolean {
            val scale1 = v.scaleX
			val scale2:Float
			val scaleNew:Float
			
			val scaleNewTry = scale1 * detector.scaleFactor
			if (scaleNewTry < 1) {
				scale2 = 1 / scale1
				scaleNew = 1f
			}
			else if (scaleNewTry > 5) {
				scale2 = 5 / scale1
				scaleNew = 5f
			} 
			else {
				scale2 = detector.scaleFactor
				scaleNew = scaleNewTry
			}
			
            v.scaleX = scaleNew
            v.scaleY = scaleNew
			
			val x = scale2 * v.x + (1 - scale2) * (detector.focusX - semiWidth)
			val y = scale2 * v.y + (1 - scale2) * (detector.focusY - semiHeight)

            val sc = scaleNew - 1
            val w = semiWidth * sc
            val h = semiHeight * sc
			
			v.x =
				if (x > w) w
				else if (x < -w) -w
				else x
			v.y =
				if (y > h) h
				else if (y < -h) -h
				else y
			
            return true
        }
    }

    private fun scrollEnd() {
        //val toScreenLeft = view.x + view.pivotX * (1 - view.scaleX)
        //val toScreenRight = view.x + (view.pivotX - width) * (1 - view.scaleX)

        val sc = view.scaleX - 1
        val w = semiWidth * sc
        val toScreenLeft = view.x - w
        if (toScreenLeft > SCROLL_DISTANCE) {
            resetImage(view)
            return prev()
        }

        val toScreenRight = view.x + w
        if (toScreenRight < -SCROLL_DISTANCE) {
            resetImage(view)
            return next()
        }

        val anime = view.animate()

        if (toScreenLeft > 0)
            anime.x(w)
        else if (toScreenRight < 0)
            anime.x(-w)

        val h = semiHeight * sc
        if (view.y > h)
            anime.y(h)
        else {
            if (view.y < -h)
                anime.y(-h)
        }
    }

    fun onTouchEvent(event:MotionEvent):Boolean {
        enlarger.onTouchEvent(event)
        if (enlarger.isInProgress) {
            retime()
            return true
        }

        if (event.action == MotionEvent.ACTION_UP)
            scrollEnd()

        if (scroller.onTouchEvent(event)) {
            retime()
            return true
        }

        return false
    }
}