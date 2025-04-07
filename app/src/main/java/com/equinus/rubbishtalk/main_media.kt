package com.equinus.rubbishtalk

import java.io.*

class MainActivity_Media(private val that:MainActivity) {
    var dir_media = "${android.os.Environment.getExternalStorageDirectory().absolutePath}${File.separator}Rubbish Talk"
    var text_speed
        get() = lines.text_speed
        set(value) { lines.text_speed = value }
    var image_speed
        get() = images.image_speed
        set(value) { images.image_speed = value }

    var topic = MainActivity.topic_uninitialized
        set(value) {
            field = value
            images.topic = value
            audios.topic = value
        }
    var topic_images
        get() = images.topic
        set(value) { images.topic = value }
    var topic_audios
        get() = audios.topic
        set(value) { audios.topic = value }
    
    val random = java.util.Random()

    fun pause() { audios.pause() }
    fun resume() { audios.resume() }

    private val lines = MainActivity_Lines(that, this)
    private val images = MainActivity_Images(that, this)
    private val audios = MainActivity_Audios(that, this)

    //private val glSurfaceView:android.opengl.GLSurfaceView? = null
    //private val glRenderer:android.opengl.GLSurfaceView.Renderer? = null

    private var metronome = if (android.os.Build.VERSION.SDK_INT >= 21)
        android.media.SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_GAME)
                .build())
            .build()
    else @Suppress("DEPRECATION") android.media.SoundPool(3, android.media.AudioManager.STREAM_MUSIC, 0)
    private var metronome_long : Int? = null
    private var metronome_middle : Int? = null
    private var metronome_short : Int? = null

    private var runnable_metronome = Runnable {}
    fun SetMetronome(interval:Int) {
        that.handler_delay.removeCallbacks(runnable_metronome)
        when {
            interval >= 3000 && metronome_long != null -> {
                runnable_metronome = Runnable {
                    metronome.play(metronome_long!!, 1.0F, 1.0F, 0, 0, 1.0F)
                    that.handler_delay.postDelayed(runnable_metronome, interval.toLong())
                }
            }
            interval >= 1000 && metronome_middle != null -> {
                runnable_metronome = Runnable {
                    metronome.play(metronome_middle!!, 1.0F, 1.0F, 0, 0, 1.0F)
                    that.handler_delay.postDelayed(runnable_metronome, interval.toLong())
                }
            }
            interval >= 200 && metronome_short != null -> {
                runnable_metronome = Runnable {
                    metronome.play(metronome_short!!, 1.0F, 1.0F, 0, 0, 1.0F)
                    that.handler_delay.postDelayed(runnable_metronome, interval.toLong())
                }
            }
            else -> return
        }
        that.handler_delay.post(runnable_metronome)
    }

    fun ChangeImage() {
        images.NextMedia()
    }

    fun ChangeImagePrev() {
        images.PrevMedia()
    }

    fun onScriptResult(success:Boolean) {
        that.onInitMediaResult(success || !images.is_empty || !audios.is_empty)
    }

    fun Init() {
        metronome_long = metronome_long ?: metronome.load(that, R.raw.metronome, 1)
        metronome_middle = metronome_long
        metronome_short = metronome_long

        if (File(dir_media).exists()) {
            images.LoadMedia(dir_media, null)
            audios.LoadMedia(dir_media, null)
            File(dir_media, "include.txt").also {
                if (it.canRead()) {
                    for (line in it.readLines()) {
                        if (line.startsWith("<") && ">" in line) {
                            images.LoadMedia(
                                line.substringAfter(">"),
                                line.substringBefore(">").substring(1)
                            )
                            audios.LoadMedia(
                                line.substringAfter(">"),
                                line.substringBefore(">").substring(1)
                            )
                        }
                        else {
                            images.LoadMedia(line, null)
                            audios.LoadMedia(line, null)
                        }
                    }
                }
                else if (it.exists()) that.CheckFileAccess(it.absolutePath)
            }

            that.handler_delay.postDelayed({
                lines.Init()
                topic = ""
            }, 100L)
        }
        else that.onInitMediaResult(false)
    }

    fun Exit() {
        images.topic = "*end"
        audios.topic = "*end"
        that.l_main.tvNooutput.text = that.resources.getString(R.string.merr_sessionend)
        that.l_main.tvNooutput.visibility = android.view.View.VISIBLE
    }
}