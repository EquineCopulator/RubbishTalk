package com.equinus.rubbishtalk

import android.Manifest
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Handler
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.equinus.rubbishtalk.databinding.GameBinding
import com.equinus.rubbishtalk.databinding.OptionBinding
import java.io.*
import kotlin.math.absoluteValue
import kotlin.math.min

/*class Multimedia(private val activity:ActivityGame) {
    companion object {
        const val topic_uninitialized = "uninitialized"
    }

    var topic = topic_uninitialized
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

    fun pause() { audios.pause() }
    fun resume() { audios.resume() }

    class StringRef(var value:String)
    val dir_media = StringRef("${
        android.os.Environment.getExternalStorageDirectory().absolutePath
    }${File.separator}Rubbish Talk")

    val handler = Handler(activity.mainLooper)

    val lytMain = MainBinding.inflate(activity.layoutInflater)
    val lytOption = OptionBinding.inflate(activity.layoutInflater)
    private lateinit var alert_option:AlertDialog

    val gesture = androidx.core.view.GestureDetectorCompat(activity, object:GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float ) =
            if (velocityX.absoluteValue >= 100) {
                if (e2.x < e1.x) images.NextMedia()
                else images.PrevMedia()
                true
            }
            else super.onFling(e1, e2, velocityX, velocityY)
    })

    private var name_game = ""

    private val lines = MediaLines(activity, this, kotlin.random.Random.Default)
    private val images = MediaImages(
        handler,
        kotlin.random.Random.Default,
        dir_media,
        lytMain.viewMain,
        lytMain.relaMain)
    private val audios = MediaAudios(
        handler,
        kotlin.random.Random.Default,
        dir_media,
        lytMain.viewMain,
        lytMain.viewMainVideo,
        activity)

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

    private val procedures_requiring_permissions = arrayOf (
        ::InitGameMenu,
        ::TryInitMedia,
    )

    private val runnable_HideButtonsUpper = Runnable {
        lytMain.buttonOption.visibility = View.INVISIBLE
        lytMain.buttonHelp.visibility = View.INVISIBLE
    }

    private val runnable_HideButtonsLower = Runnable {
        lytMain.llResponse.alpha = 0.3f
    }

    private var runnable_metronome = Runnable {}
    fun SetMetronome(interval:Int) {
        handler.removeCallbacks(runnable_metronome)
        when {
            interval >= 3000 && metronome_long != null -> {
                runnable_metronome = Runnable {
                    metronome.play(metronome_long!!, 1.0F, 1.0F, 0, 0, 1.0F)
                    handler.postDelayed(runnable_metronome, interval.toLong())
                }
            }
            interval >= 1000 && metronome_middle != null -> {
                runnable_metronome = Runnable {
                    metronome.play(metronome_middle!!, 1.0F, 1.0F, 0, 0, 1.0F)
                    handler.postDelayed(runnable_metronome, interval.toLong())
                }
            }
            interval >= 200 && metronome_short != null -> {
                runnable_metronome = Runnable {
                    metronome.play(metronome_short!!, 1.0F, 1.0F, 0, 0, 1.0F)
                    handler.postDelayed(runnable_metronome, interval.toLong())
                }
            }
            else -> return
        }
        handler.post(runnable_metronome)
    }

    fun Alert(s:String) {
        AlertDialog.Builder(activity).apply {
            setMessage(s)
            setPositiveButton(activity.getString(R.string.ok)) { _, _ -> }
            create().show()
        }
    }

    fun Alert(s:String, action: (DialogInterface, Int) -> Unit) {
        AlertDialog.Builder(activity).apply {
            setMessage(s)
            setPositiveButton(activity.getString(R.string.ok), action)
            setNegativeButton(activity.getString(R.string.cancel)) { _, _ -> }
            create().show()
        }
    }

    private fun CheckFileAccess(s:String) {
        Alert(activity.getString(R.string.merr_fileaccess, s)
                + "\nSDK ${android.os.Build.VERSION.SDK_INT}"
                + "\nREAD_EXTERNAL_STORAGE ${
                    ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
                }"
                + "\nMANAGE_EXTERNAL_STORAGE ${if (android.os.Build.VERSION.SDK_INT >= 30 )
            ContextCompat.checkSelfPermission(activity, Manifest.permission.MANAGE_EXTERNAL_STORAGE)
        else PERMISSION_GRANTED
        }")
    }

    fun onScriptResult(success:Boolean) {
        onInitMediaResult(success || !images.is_empty || !audios.is_empty)
    }

    private fun Init() {
        metronome_long = metronome_long ?: metronome.load(activity, R.raw.metronome, 1)
        metronome_middle = metronome_long
        metronome_short = metronome_long

        fun MediaNonlines.loadAndWarn(dir:String, topic_this:String?) {
            val ret = LoadMedia(dir, topic_this)
            if (ret == -1) CheckFileAccess(dir)
        }

        if (File(dir_media.value).exists()) {
            images.loadAndWarn(dir_media.value, null)
            audios.loadAndWarn(dir_media.value, null)
            File(dir_media.value, "include.txt").also {
                if (it.canRead()) {
                    for (line in it.readLines()) {
                        if (line.startsWith("<") && ">" in line) {
                            images.loadAndWarn(
                                line.substringAfter(">"),
                                line.substringBefore(">").substring(1)
                            )
                            audios.loadAndWarn(
                                line.substringAfter(">"),
                                line.substringBefore(">").substring(1)
                            )
                        }
                        else {
                            images.loadAndWarn(line, null)
                            audios.loadAndWarn(line, null)
                        }
                    }
                }
                else if (it.exists()) CheckFileAccess(it.absolutePath)
            }

            handler.postDelayed({
                lines.Init()
                topic = ""
            }, 100L)
        }
        else onInitMediaResult(false)
    }

    fun Exit() {
        images.topic = "*end"
        audios.topic = "*end"
        lytMain.tvNooutput.text = activity.resources.getString(R.string.merr_sessionend)
        lytMain.tvNooutput.visibility = View.VISIBLE
    }

    fun OnClickUpper(v:View) {
        lytMain.buttonOption.visibility = View.VISIBLE
        lytMain.buttonHelp.visibility = View.VISIBLE
        handler.removeCallbacks(runnable_HideButtonsUpper)
        handler.postDelayed(runnable_HideButtonsUpper, 5000L)
    }

    fun OnClickLower(v:View) {
        lytMain.llResponse.alpha = 0.9f
        handler.removeCallbacks(runnable_HideButtonsLower)
        handler.postDelayed(runnable_HideButtonsLower, 5000L)
    }

    fun OnClickOption(v:View) {
        lytOption.etName.setText(name_game)
        lytOption.etDir.setText(dir_media.value)
        lytOption.etTextspeed.setText(lines.text_speed.toString())
        lytOption.etImagespeed.setText(images.image_speed.toString())
        alert_option.show()
    }

    @android.annotation.SuppressLint("InlinedApi") fun OnClickOptionBrowse(v:View) {
        try { activity.startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 1) }
        catch (_: Exception) {}
    }

    fun OnClickHelp(v:View) {
        Alert(activity.getString(R.string.article_help))
    }

    fun onPermissionGranted(requestCode:Int) =
        procedures_requiring_permissions[requestCode]()

    private fun TryInitMedia() {
        val f = File(dir_media.value)
        if (f.exists()) {
            if (f.canRead()) Init()
            else activity.RequestStoragePermission(procedures_requiring_permissions.indexOf(::TryInitMedia))
        }
        else onInitMediaResult(false)
    }

    private fun onInitMediaResult(success: Boolean) {
        if (success) {
            lytMain.tvNooutput.visibility = View.GONE
            runnable_HideButtonsUpper.run()
        }
        else {
            lytMain.tvNooutput.text = activity.getString(R.string.merr_nomedia)
            lytMain.tvNooutput.visibility = View.VISIBLE
        }
    }

    private fun SelectGame(name_game_selected:String) {
        val dir_sys = File(activity.filesDir, "sys")
        if (!dir_sys.exists()) {
            CheckFileAccess(dir_sys.absolutePath)
            return
        }
        val dir_settings = File(dir_sys, "settings")
        if (!dir_settings.exists()) {
            CheckFileAccess(dir_sys.absolutePath)
            return
        }
        val f_setting = File(dir_settings, name_game_selected)
        if (!f_setting.exists()) {
            CheckFileAccess(dir_sys.absolutePath)
            return
        }

        val s_settings = f_setting.readLines()

        val dir_media = s_settings.getOrNull(0)
        if (dir_media != null) this.dir_media.value = dir_media

        val text_speed = s_settings.getOrNull(1)?.toLongOrNull()
        if (text_speed != null) lines.text_speed = text_speed

        val image_speed = s_settings.getOrNull(2)?.toLongOrNull()
        if (image_speed != null) images.image_speed = image_speed

        name_game = name_game_selected
        activity.setContentView(lytMain.root)

        TryInitMedia()
    }

    fun InitGameMenu() {
        val dir_sys = File(activity.filesDir, "sys")
        if (!dir_sys.exists()) {
            if(!dir_sys.mkdir())
                return activity.RequestStoragePermission(procedures_requiring_permissions.indexOf(::InitGameMenu))
        }
        val dir_settings = File(dir_sys, "settings")
        if (!dir_settings.exists()) {
            if(!dir_settings.mkdir())
                return activity.RequestStoragePermission(procedures_requiring_permissions.indexOf(::InitGameMenu))
        }
        val f_gameList = File(dir_sys, "game_list")
        if (!f_gameList.exists()) f_gameList.createNewFile()
        val games = f_gameList.readLines().toSortedSet()

        val l_game = GameBinding.inflate(activity.layoutInflater)
        val layoutparam = LinearLayout.LayoutParams(600, LinearLayout.LayoutParams.WRAP_CONTENT)
        for (name_game_candidate in games) {
            val b = Button(activity)
            b.layoutParams = layoutparam
            b.text = name_game_candidate
            b.setOnClickListener {
                (l_game.root.parent as? android.view.ViewGroup)?.removeView(l_game.root)
                SelectGame(name_game_candidate)
            }
            b.setOnLongClickListener {
                Alert(activity.getString(R.string.tv_delete_game)) { _, _ ->
                    l_game.llMain.removeView(b)
                    games.remove(name_game_candidate)
                    f_gameList.writeText(games.joinToString("\n"))
                }
                true
            }
            l_game.llMain.addView(b)
        }
        val b_create = Button(activity)
        b_create.layoutParams = layoutparam
        b_create.text = activity.getString(R.string.tv_add_new_game)
        b_create.setOnClickListener {
            lytOption.etName.setText(activity.getString(R.string.tv_name_new_game))
            alert_option.show()
        }
        l_game.llMain.addView(b_create)

        alert_option = AlertDialog.Builder(activity).apply {
            setTitle(activity.getString(R.string.tv_game_setting))
            setView(lytOption.root)
            setPositiveButton(activity.getString(R.string.ok)) { _, _ ->
                if (name_game == "") {
                    val name_game_new = lytOption.etName.text.toString().trim()

                    if (name_game_new in games)
                        Alert(activity.getString(R.string.merr_gamename_existing,name_game_new))
                    else if (!Regex("""[\w_ ]+""").matches(name_game_new))
                        Alert(activity.getString(R.string.merr_gamename_invalid,name_game_new))
                    else {
                        games.add(name_game_new)
                        f_gameList.writeText(games.joinToString("\n"))

                        val f_setting = File(dir_settings, name_game_new)
                        if (!f_setting.exists()) f_setting.createNewFile()
                        f_setting.writeText("${lytOption.etDir.text}\n${lytOption.etTextspeed.text}\n${lytOption.etImagespeed.text}")

                        (l_game.root.parent as? android.view.ViewGroup)?.removeView(l_game.root)
                        SelectGame(name_game_new)
                    }
                }
                else {
                    val f_setting = File(dir_settings, name_game)
                    if (!f_setting.exists()) f_setting.createNewFile()

                    val name_game_new = lytOption.etName.text.toString().trim()
                    if (name_game != name_game_new && name_game_new !in games && Regex("""[\w_ ]+""").matches(name_game_new)) {
                        var success = true
                        val f_setting_new = File(dir_settings, name_game_new)
                        if (f_setting_new.exists()) success = success && f_setting_new.delete()
                        success = success && f_setting.renameTo(f_setting_new)
                        if (success) {
                            games.remove(name_game)
                            games.add(name_game_new)
                            f_gameList.writeText(games.joinToString("\n"))

                            name_game = name_game_new
                        }
                    }

                    val f_dir_media = File(lytOption.etDir.text.toString())
                    val s_dir_media:String
                    if (f_dir_media.isDirectory && f_dir_media.canRead()) {
                        s_dir_media = f_dir_media.absolutePath
                        if (topic == topic_uninitialized) {
                            dir_media.value = s_dir_media
                            TryInitMedia()
                        }
                        else if (lytOption.etDir.text.toString() != dir_media.value) {
                            Alert(activity.getString(R.string.merr_pathchanged))
                        }
                    }
                    else s_dir_media = dir_media.value
                    val text_speed_new = lytOption.etTextspeed.text.toString().toLongOrNull()
                    if (text_speed_new != null && text_speed_new >= 100L) {
                        lines.text_speed = text_speed_new
                    }
                    val image_speed_new = lytOption.etImagespeed.text.toString().toLongOrNull()
                    if (image_speed_new != null) {
                        images.image_speed = image_speed_new
                    }
                    f_setting.writeText("${s_dir_media}\n${lines.text_speed}\n${images.image_speed}")
                }
            }
            setNegativeButton(activity.getString(R.string.cancel)) { _, _ -> }
        }.create()

        activity.setContentView(l_game.root)
    }

    init {
        lytMain.llResponse.setOnClickListener(::OnClickLower)
        lytMain.llOptionHelp.setOnClickListener(::OnClickUpper)
        lytMain.buttonOption.setOnClickListener(::OnClickOption)
        lytMain.buttonHelp.setOnClickListener(::OnClickHelp)

        if (android.os.Build.VERSION.SDK_INT >= 21)
            lytOption.bOptionBrowse.setOnClickListener(::OnClickOptionBrowse)
        else lytOption.bOptionBrowse.visibility = View.GONE

        val pivot = if (android.os.Build.VERSION.SDK_INT >= 30) {
            val bounds = activity.windowManager.currentWindowMetrics.bounds
            min(bounds.width(), bounds.height())
        }
        else {
            val m = android.util.DisplayMetrics()
            @Suppress("DEPRECATION") activity.windowManager.defaultDisplay.getMetrics(m)
            min(m.widthPixels, m.heightPixels)
        }.toFloat() / 2
        lytMain.relaMain.pivotX = pivot
        lytMain.relaMain.pivotY = pivot
    }
}*/