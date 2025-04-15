package com.equinus.rubbishtalk

import android.app.Activity
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Handler
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.equinus.rubbishtalk.databinding.GameBinding
import java.io.File
import kotlin.math.absoluteValue
import kotlin.random.Random

class ActivityGame:Activity() {
    private lateinit var handler:Handler

    private lateinit var lytGame:GameBinding
    private lateinit var scroller:GestureDetector
    private lateinit var enlarger:ScaleGestureDetector

    private lateinit var lines:MediaLines
    private lateinit var images:MediaImages
    private lateinit var audios:MediaAudios

    private lateinit var dirMedia:String

    companion object {
        private const val SCROLL_DISTANCE = 300f
    }

    private class GestureListener(private val v:View)
        :GestureDetector.SimpleOnGestureListener()
    {
        override fun onScroll(e1:MotionEvent, e2:MotionEvent, distanceX:Float, distanceY:Float):Boolean {
            v.x -= distanceX
            return true
        }
    }

    private class ScaleListener(private val v:View)
        :ScaleGestureDetector.SimpleOnScaleGestureListener()
    {
        override fun onScale(detector:ScaleGestureDetector):Boolean {
            val scale1 = v.scaleX
            val scale2R = detector.scaleFactor

            val scale:Float
            val scale2:Float
            if (scale1 * scale2R < 1) {
                scale = 1f
                scale2 = 1 / scale1
            }
            else if (scale1 * scale2R > 5) {
                scale = 5f
                scale2 = 5 / scale1
            }
            else {
                scale = scale1 * scale2R
                scale2 = scale2R
            }

            val w1 = (scale1 - 1).absoluteValue
            val w2 = (scale2 - 1).absoluteValue
            val den = w1 + w2

            if (den > 0) {
                v.pivotX = (v.pivotX * w1 + detector.focusX * w2) / den
                v.pivotY = (v.pivotY * w1 + detector.focusY * w2) / den
                v.scaleX = scale
                v.scaleY = scale
                return true
            }
            else return false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val width:Int
        val height:Int
        if (SDK_INT >= 30) {
            val bounds = windowManager.currentWindowMetrics.bounds
            width = bounds.width()
            height = bounds.height()
        }
        else {
            val m = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") windowManager.defaultDisplay.getMetrics(m)
            width = m.widthPixels
            height = m.heightPixels
        }

        handler = Handler(mainLooper)

        lytGame = GameBinding.inflate(layoutInflater)

        val i = intent!!
        dirMedia = relToAbsPath(
            android.os.Environment.getExternalStorageDirectory().absolutePath,
            i.getStringExtra(SharedConst.EXTRA_DIR_MEDIA)!!)
        val textSpeed = i.getLongExtra(SharedConst.EXTRA_TEXT_SPEED, -1L)
        if (textSpeed < 0) throw NullPointerException()
        val imageSpeed = i.getLongExtra(SharedConst.EXTRA_IMAGE_SPEED, -1L)
        if (imageSpeed < 0) throw NullPointerException()

        images = MediaImages(
            width,
            height,
            imageSpeed,
            handler,
            Random.Default,
            lytGame.viewMain,
            lytGame.relaMain)
        audios = MediaAudios(
            width,
            height,
            handler,
            Random.Default,
            ::exit,
            lytGame.viewMainVideo,
            this)

        scroller = GestureDetector(this, GestureListener(lytGame.root))
        enlarger = ScaleGestureDetector(this, ScaleListener(lytGame.root))

        lines = MediaLines(
            width,
            height,
            handler,
            ::exit,
            images,
            audios,
            dirMedia,
            textSpeed,
            this,
            Random.Default,
            lytGame.relaMain,
            lytGame.llQuestion,
            lytGame.llResponse)

        if (!File(dirMedia).exists())
            return exit(getString(R.string.merr_nonexist, dirMedia))

        loadMedia()
    }

    override fun onDestroy() {
        lines.stop()
        super.onDestroy()
    }

    override fun onPause() {
        lines.pause()
        images.pause()
        audios.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        lines.resume()
        images.resume()
        audios.resume()
    }

    override fun onTouchEvent(event:MotionEvent):Boolean {
        val b = enlarger.onTouchEvent(event)
        if (enlarger.isInProgress) {
            images.pause()
            images.resume()
            return b
        }

        if (event.action == MotionEvent.ACTION_UP) {
            val x = lytGame.root.x
            if (x < -SCROLL_DISTANCE) {
                lytGame.root.x = 0f
                images.nextMedia()
            }
            else if (x > SCROLL_DISTANCE) {
                lytGame.root.x = 0f
                images.prevMedia()
            }
            else lytGame.root.animate().x(0f)
        }

        if (scroller.onTouchEvent(event)) {
            images.pause()
            images.resume()
            return true
        }

        return super.onTouchEvent(event)
    }

    private fun relToAbsPath(base:String, path:String):String {
        val p = path.trimStart()
        if (!p.startsWith("." + File.separator))
            if (!p.startsWith(".." + File.separator))
                return p
        return base + "." + File.separator + p
    }

    private fun requestPerm() {
        setResult(RESULT_OK, Intent().putExtra(SharedConst.EXTRA_PERM_DENIED, true))
        finish()
    }

    private fun loadMedia() {
        var ret = images.loadMedia(dirMedia, null)
        if (ret == MediaNonlines.LOADFILE_DENIED)
            return requestPerm()

        ret = audios.loadMedia(dirMedia, null)
        if (ret == MediaNonlines.LOADFILE_DENIED)
            return requestPerm()

        val fInclude = File(dirMedia, "include.txt")
        if (fInclude.canRead()) {
            for (line in fInclude.readLines()) {
                val f:String
                val topic:String?
                if (line.startsWith("<") && ">" in line) {
                    f = relToAbsPath(dirMedia, line.substringAfter(">"))
                    topic = line.substringBefore(">").substring(1)
                }
                else {
                    f = relToAbsPath(dirMedia, line)
                    topic = null
                }

                ret = images.loadMedia(f, topic)
                if (ret == MediaNonlines.LOADFILE_DENIED)
                    return requestPerm()

                ret = audios.loadMedia(f, topic)
                if (ret == MediaNonlines.LOADFILE_DENIED)
                    return requestPerm()
            }
        }
        else if (fInclude.exists()) return requestPerm()

        if (lines.init()) lines.start()
        else if (images.isEmpty() && audios.isEmpty())
            exit(getString(R.string.merr_nomedia))

        setContentView(lytGame.root)

        images.topic = ""
        audios.topic = ""
    }

    private fun exit(msg:String?) {
        if (msg != null) {
            setResult(RESULT_OK, Intent().putExtra(SharedConst.EXTRA_ERROR, msg))
        }
        finish()
    }
}
