package com.equinus.rubbishtalk

import java.io.File
import kotlin.collections.filter

sealed class MediaNonlines(
    protected val handler:android.os.Handler,
    private val random:kotlin.random.Random)
{
    abstract fun pause()
    abstract fun resume()

    protected abstract val accepted_extensions:Array<String>
    protected abstract fun displayMedia()
    protected abstract fun removeMedia()
    protected open fun decideNextImage() {}

    var topic = SharedConst.TOPIC_UNINITED
        set(value) {
            if (field != value) {
                handler.removeCallbacks(runnableChooseMedia)
                val files_topic_new = files[value]
                if (files_topic_new != null) {
                    field = value
                    files_topic = files_topic_new
                    pos_i_series = random.nextInt(files_topic_new.size)
                    pos_i_img = 0
                    displayMedia()
                    decideNextImage()
                }
                else {
                    field = SharedConst.TOPIC_UNINITED
                    files_topic = files_topic_dummy
                    removeMedia()
                }
            }
        }

    private val files = mutableMapOf<String, MutableList<MutableList<File>>>()
    protected var files_topic = files_topic_dummy
    protected var pos_i_series = 0
    protected var pos_i_img = 0
    private val mr = MemorizedRandom(random)

    protected val runnableChooseMedia = Runnable(::ChooseMedia)

    companion object {
        const val LOADFILE_OK = 0
        const val LOADFILE_DENIED = 1
        const val LOADFILE_NONEXIST = 2

        private val files_topic_dummy = emptyList<List<File>>()
    }

    fun loadMedia(dir:String, topic_this:String?):Int {
        val dir_file = File(dir)
        val files_new = if (dir_file.isDirectory)
            dir_file.listFiles {
                f -> f.extension.lowercase() in accepted_extensions
            } ?: return LOADFILE_DENIED
        else if (dir_file.isFile) {
            if (!dir_file.canRead()) return LOADFILE_DENIED
            if (dir_file.extension in accepted_extensions) arrayOf(dir_file)
            else arrayOf()
        }
        else return LOADFILE_NONEXIST

        files_new.sortBy { it.name }
        val index_trunk = mutableMapOf<Pair<String, String>, Int>()
        for (f in files_new) {
            if (!f.canRead()) return LOADFILE_DENIED

            val filename = f.nameWithoutExtension

            val suffix = filename.substringAfterLast('_', "")
            val i_suffix = if (suffix.toIntOrNull(10) != null) filename.length - ( suffix.length + 1 )
            else filename.length

            val filename_without_suffix = filename.substring(0, i_suffix)
            val prefix = (topic_this ?: filename_without_suffix.substringBeforeLast('_', "")).trim {
                it.isWhitespace() || it == '_'
            }
            val trunk =
                if (topic_this == null) filename_without_suffix.substringAfterLast('_')
                else filename_without_suffix

            val files_topic_new = files.getOrPut(prefix) { mutableListOf() }
            val i_files = index_trunk.getOrPut(Pair(prefix, trunk)) {
                files_topic_new.add(mutableListOf())
                files_topic_new.size - 1
            }
            files_topic_new[i_files].add(f)
        }

        return LOADFILE_OK
    }

    fun isEmpty() = files.values.all { it.isEmpty() }

    fun nextMedia() {
        handler.removeCallbacks(runnableChooseMedia)
        ChooseMedia()
    }

    fun prevMedia() {
        handler.removeCallbacks(runnableChooseMedia)

        if (files_topic.isNotEmpty()) {
            --pos_i_img
            if (pos_i_img < 0) {
                if (files_topic.size > 1) {
                    pos_i_series = mr.Next(topic, files_topic.size)
                }
                pos_i_img = files_topic[pos_i_series].size - 1
            }
            displayMedia()
            decideNextImage()
        }
    }

    fun topics() = files.asSequence().filter {
        it.value.isNotEmpty()
    }.map { it.key }.toSet()

    private fun ChooseMedia() {
        if (files_topic.isNotEmpty()) {
            ++pos_i_img
            if (pos_i_img >= files_topic[pos_i_series].size) {
                if (files_topic.size > 1) {
                    pos_i_series = mr.Next(topic, files_topic.size)
                }
                pos_i_img = 0
            }
            displayMedia()
            decideNextImage()
        }
    }
}

class MediaImages(
    private val width:Int,
    private val height:Int,
    private val imageSpeed:Long,
    handler:android.os.Handler,
    random:kotlin.random.Random,
    private val vImage:android.widget.ImageView,
    private val vText:android.view.View): MediaNonlines(handler, random)
{
    override fun pause() = handler.removeCallbacks(runnableChooseMedia)
    override fun resume() = decideNextImage()

    override val accepted_extensions = arrayOf(
        "bmp", "gif", "jpg", "jpeg", "png",
        "webp", "heif", "heic", "avif")

    override fun displayMedia() {
        val path = files_topic[pos_i_series][pos_i_img].absolutePath
        android.graphics.BitmapFactory.decodeFile(path, optB)
        if (optB.outWidth == -1) return

        if (optB.outWidth > optB.outHeight) {
            var q = 2
            while (optB.outWidth >= width * q && optB.outHeight >= height * q)
                q *= 2
            optS.inSampleSize = q / 2

            vImage.setImageBitmap(android.graphics.BitmapFactory.decodeFile(path, optS) ?: return)

            vText.animate().setDuration(500).rotation(0f)
        } else {
            var q = 2
            while (optB.outWidth >= height * q && optB.outHeight >= width * q)
                q *= 2
            optS.inSampleSize = q / 2

            val bitmap = android.graphics.BitmapFactory.decodeFile(path, optS) ?: return
            val m = android.graphics.Matrix()
            m.postRotate(-90f)
            vImage
                .setImageBitmap(android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true))

            vText.animate().setDuration(500).rotation(-90f)
        }
    }
    override fun removeMedia() {
        vImage.setImageDrawable(null)
    }
    override fun decideNextImage() {
        if (imageSpeed > 0)
            handler.postDelayed(runnableChooseMedia, imageSpeed)
    }

    private val optB = android.graphics.BitmapFactory.Options().also { it.inJustDecodeBounds = true }
    private val optS = android.graphics.BitmapFactory.Options()

    init {
        val pivot = (if (width < height) width else height).toFloat() / 2
        vImage.pivotX = pivot
        vImage.pivotY = pivot
    }
}

abstract class MediaAudios(
    private val width:Int,
    private val height:Int,
    handler:android.os.Handler,
    random:kotlin.random.Random,
    private val vVideo:android.view.SurfaceView,
    context:android.content.Context): MediaNonlines(handler, random)
{
    protected abstract fun errmsg(str:String?)

    override fun pause() {
        if (player.isPlaying) {
            paused = true
            try { player.pause() } catch (_: IllegalStateException) {}
        }
    }
    override fun resume() {
        if (paused) {
            paused = false
            try { player.start() } catch (_:IllegalStateException) {}
        }
    }

    override val accepted_extensions = arrayOf(
        "3gp", "mp4", "m4a", "aac", "ts",
        "amr", "flac", "mid", "xmf", "mxmf",
        "rtttl", "rtx", "ota", "imy", "mp3",
        "mkv", "ogg", "wav", "webm")

    override fun displayMedia() {
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
                        val asw = width * this.height / height
                        if (asw <= this.width) {
                            vVideo.layoutParams.width = asw
                            vVideo.layoutParams.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        }
                        else {
                            vVideo.layoutParams.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            vVideo.layoutParams.height = height * this.width / width
                        }
                    }
                }
            }

            player.prepareAsync()
        }
        catch (e:Exception) { errmsg("${
            when (e) {
                is java.io.IOException -> msgFileBad
                is SecurityException -> msgFileDenied
                else -> e.toString()
            }
        }: ${ files_topic[pos_i_series][pos_i_img].absolutePath }.") }
    }
    override fun removeMedia() {
        player.reset()
        if (isVideo) {
            isVideo = false
            player.setDisplay(null)
            vVideo.visibility = android.view.View.INVISIBLE
        }
    }

    private val player = android.media.MediaPlayer()
    private val meta = android.media.MediaMetadataRetriever()
    private var isVideo = false
    private var paused = false

    private val msgFileBad = context.getString(R.string.merr_playersrc_io)
    private val msgFileDenied = context.getString(R.string.merr_playersrc_security)
    private val msgPlayerFail = context.getString(R.string.merr_player)
    private val msgPlayerFailIO = context.getString(R.string.merr_player_io)
    private val msgPlayerFailMalformed = context.getString(R.string.merr_player_malformed)
    private val msgPlayerFailUnsupported = context.getString(R.string.merr_player_unsupported)
    private val msgPlayerFailTimeout = context.getString(R.string.merr_player_timedout)

    init {
        player.setOnPreparedListener {
            if (isVideo) {
                vVideo.visibility = android.view.View.VISIBLE
                player.setDisplay(vVideo.holder)
            }
            player.start()
        }
        player.setOnCompletionListener {
            if (isVideo) {
                player.setDisplay(null)
                vVideo.visibility = android.view.View.INVISIBLE
            }
            runnableChooseMedia.run()
        }
        player.setOnErrorListener { mp, what, extra ->
            mp.reset()
            errmsg(msgPlayerFail.format(try {
                files_topic[pos_i_series][pos_i_img].absolutePath
            } catch (_:Exception) { "(no file)" }, when (extra) {
                android.media.MediaPlayer.MEDIA_ERROR_IO -> msgPlayerFailIO
                android.media.MediaPlayer.MEDIA_ERROR_MALFORMED -> msgPlayerFailMalformed
                android.media.MediaPlayer.MEDIA_ERROR_UNSUPPORTED -> msgPlayerFailUnsupported
                android.media.MediaPlayer.MEDIA_ERROR_TIMED_OUT -> msgPlayerFailTimeout
                else -> String.format("what=0x%1\$X, extra=0x%2\$X", what, extra)
            }))
            true
        }
    }
}