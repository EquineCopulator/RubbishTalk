package com.equinus.rubbishtalk

import java.io.File

sealed class MainActivity_Nonlines(protected val that:MainActivity, private val parent:MainActivity_Media) {
    protected abstract val accepted_extensions:Array<String>
    protected abstract fun DisplayMedia()
    protected abstract fun RemoveMedia()
    protected open fun DecideNextImage() {}

    companion object {
        private val files_topic_dummy = emptyList<List<File>>()
    }

    var topic = MainActivity.topic_uninitialized
        set(value) {
            if (field != value) {
                that.handler_delay.removeCallbacks(runnable_ChooseMedia)
                val files_topic_new = files[value]
                if (files_topic_new != null) {
                    field = value
                    files_topic = files_topic_new
                    pos_i_series = parent.random.nextInt(files_topic_new.size)
                    pos_i_img = 0
                    DisplayMedia()
                    DecideNextImage()
                }
                else {
                    field = MainActivity.topic_uninitialized
                    files_topic = files_topic_dummy
                    RemoveMedia()
                }
            }
        }

    private val files = mutableMapOf<String, MutableList<MutableList<File>>>()
    val is_empty get() = files.isEmpty()
    protected var files_topic = files_topic_dummy
    protected var pos_i_series = 0
    protected var pos_i_img = 0
    private val mr = MemorizedRandom(parent.random)

    private fun ChooseMedia() {
        ++pos_i_img
        if (pos_i_img >= files_topic[pos_i_series].size) {
            if (files_topic.size > 1) {
                pos_i_series = mr.Next(topic, files_topic.size)
            }
            pos_i_img = 0
        }
        DisplayMedia()
        DecideNextImage()
    }
    protected val runnable_ChooseMedia = Runnable(::ChooseMedia)

    fun NextMedia() {
        if (files_topic !== files_topic_dummy) {
            that.handler_delay.removeCallbacks(runnable_ChooseMedia)
            ChooseMedia()
        }
    }

    fun PrevMedia() {
        if (files_topic !== files_topic_dummy) {
            that.handler_delay.removeCallbacks(runnable_ChooseMedia)

            --pos_i_img
            if (pos_i_img < 0) {
                if (files_topic.size > 1) {
                    pos_i_series = mr.Next(topic, files_topic.size)
                }
                pos_i_img = files_topic[pos_i_series].size - 1
            }
            DisplayMedia()
            DecideNextImage()
        }
    }

    fun LoadMedia(dir:String, topic_this:String?) {
        val dir_this = dir.trimStart()
        val dir_file =
        if (dir_this.startsWith("." + File.separator) || dir_this.startsWith(".." + File.separator))
            File(parent.dir_media + File.separator + dir_this)
        else File(dir_this)
        val files_new = if (dir_file.isDirectory) dir_file.listFiles {
            f:File -> f.extension.lowercase() in accepted_extensions
        }
        else if (dir_file.isFile) {
            if (dir_file.extension in accepted_extensions) arrayOf(dir_file)
            else arrayOf()
        }
        else null
        if (files_new != null) {
            java.util.Arrays.sort(files_new)
            val index_trunk = mutableMapOf<Pair<String, String>, Int>()
            for (f in files_new) {
                val filename = f.nameWithoutExtension

                val suffix = filename.substringAfterLast('_', "")
                val i_suffix = if (suffix.toIntOrNull(10) != null) filename.length - ( suffix.length + 1 )
                else filename.length

                val filename_without_suffix = filename.substring(0, i_suffix)
                val prefix = (topic_this ?: filename_without_suffix.substringBeforeLast('_', "")).trim { it.isWhitespace() || it == '_' }
                val trunk = if (topic_this == null) filename_without_suffix.substringAfterLast('_')
                else filename_without_suffix

                val files_topic_new = files.getOrPut(prefix) { mutableListOf() }
                val i_files = index_trunk.getOrPut(Pair(prefix, trunk)) {
                    files_topic_new.add(mutableListOf())
                    files_topic_new.size - 1
                }
                files_topic_new[i_files].add(f)
            }
        }
        else if (dir_file.exists()) that.CheckFileAccess(dir_this)
    }
}

class MainActivity_Images(that:MainActivity, parent:MainActivity_Media): MainActivity_Nonlines(that, parent) {
    override val accepted_extensions = MainActivity.accepted_image_extension
    override fun DisplayMedia() {
        val path = files_topic[pos_i_series][pos_i_img].absolutePath
        android.graphics.BitmapFactory.decodeFile(path, op_b)
        if (op_b.outWidth == -1) return

        if (op_b.outWidth > op_b.outHeight) {
            var q = 2
            while (op_b.outWidth >= that.l_main.viewMain.width * q && op_b.outHeight >= that.l_main.viewMain.height * q)
                q *= 2
            op_s.inSampleSize = q / 2

            that.l_main.viewMain.setImageBitmap(android.graphics.BitmapFactory.decodeFile(path, op_s) ?: return)

            that.l_main.relaMain.animate().setDuration(500).rotation(0f)
        } else {
            var q = 2
            while (op_b.outWidth >= that.l_main.viewMain.height * q && op_b.outHeight >= that.l_main.viewMain.width * q)
                q *= 2
            op_s.inSampleSize = q / 2

            val bitmap = android.graphics.BitmapFactory.decodeFile(path, op_s) ?: return
            val m = android.graphics.Matrix()
            m.postRotate(-90f)
            that.l_main.viewMain
                .setImageBitmap(android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true))

            that.l_main.relaMain.animate().setDuration(500).rotation(-90f)
        }
    }
    override fun RemoveMedia() {
        that.l_main.viewMain.setImageDrawable(null)
    }
    override fun DecideNextImage() {
        if (image_speed > 0L) that.handler_delay.postDelayed(runnable_ChooseMedia, image_speed)
    }

    var image_speed = 10000L
    private val op_b = android.graphics.BitmapFactory.Options().also { it.inJustDecodeBounds = true }
    private val op_s = android.graphics.BitmapFactory.Options()
}

class MainActivity_Audios(that:MainActivity, parent:MainActivity_Media): MainActivity_Nonlines(that, parent) {
    fun pause() {
        try { player.pause() } catch (_:IllegalStateException) {}
    }
    fun resume() {
        try { player.start() } catch (_:IllegalStateException) {}
    }

    override val accepted_extensions = MainActivity.accepted_audio_extension
    override fun DisplayMedia() {
        player.reset()
        try {
            player.setDataSource(files_topic[pos_i_series][pos_i_img].absolutePath)

            meta.setDataSource(files_topic[pos_i_series][pos_i_img].absolutePath)
            isVideo = meta.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != null
            if (isVideo) {
                val width = meta.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                if (width != null) {
                    val height = meta.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                    if (height != null) {
                        val asw = width * that.l_main.viewMain.height / height
                        if (asw <= that.l_main.viewMain.width) {
                            that.l_main.viewMainVideo.layoutParams.width = asw
                            that.l_main.viewMainVideo.layoutParams.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        }
                        else {
                            that.l_main.viewMainVideo.layoutParams.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            that.l_main.viewMainVideo.layoutParams.height = height * that.l_main.viewMain.width / width
                        }
                    }
                }
            }

            player.prepareAsync()
        }
        catch (e:Exception) { that.Alert(String.format("%1\$s: %2\$s.", when (e) {
            is java.io.IOException -> that.getString(R.string.merr_playersrc_io)
            is SecurityException -> that.getString(R.string.merr_playersrc_security)
            else -> e.toString()
        }, files_topic[pos_i_series][pos_i_img].absolutePath)) }
    }
    override fun RemoveMedia() {
        player.reset()
        if (isVideo) {
            isVideo = false
            player.setDisplay(null)
            that.l_main.viewMainVideo.visibility = android.view.View.INVISIBLE
        }
    }

    private val player = android.media.MediaPlayer()
    private val meta = android.media.MediaMetadataRetriever()
    private var isVideo = false
    init {
        player.setOnPreparedListener {
            if (isVideo) {
                that.l_main.viewMainVideo.visibility = android.view.View.VISIBLE
                player.setDisplay(that.l_main.viewMainVideo.holder)
            }
            player.start()
        }
        player.setOnCompletionListener {
            if (isVideo) {
                player.setDisplay(null)
                that.l_main.viewMainVideo.visibility = android.view.View.INVISIBLE
            }
            runnable_ChooseMedia.run()
        }
        player.setOnErrorListener { mp, what, extra ->
            mp.reset()
            that.Alert(that.getString(R.string.merr_player, try {
                files_topic[pos_i_series][pos_i_img].absolutePath
            } catch (_:Exception) { "(no file)" }, when (extra) {
                android.media.MediaPlayer.MEDIA_ERROR_IO -> that.getString(R.string.merr_player_io)
                android.media.MediaPlayer.MEDIA_ERROR_MALFORMED -> that.getString(R.string.merr_player_malformed)
                android.media.MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> that.getString(R.string.merr_player_unsupported)
                android.media.MediaPlayer.MEDIA_ERROR_TIMED_OUT -> that.getString(R.string.merr_player_timedout)
                else -> String.format("what=0x%1\$X, extra=0x%2\$X", what, extra)
            }))
            true
        }
    }
}