package com.equinus.rubbishtalk

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.equinus.rubbishtalk.databinding.*
import java.io.File

class MainActivity : Activity() {
    companion object {
        const val topic_uninitialized = "uninitialized"
        const val prefkey_welcome = "prefkey_welcome"

        val text_colors = arrayOf(
            R.color.Poppy,
            R.color.Rose,
            R.color.Phlox,
            R.color.Byzantium,
            R.color.Pansy)

        val button_colors = arrayOf(
            R.color.Red,
            R.color.Orange,
            R.color.Yellow,
            R.color.Green,
            R.color.Blue,
            R.color.Cyan,
            R.color.Purple)

        val accepted_image_extension = arrayOf(
            "bmp", "gif", "jpg", "jpeg", "png",
            "webp", "heif", "heic", "avif")

        val accepted_audio_extension = arrayOf(
            "3gp", "mp4", "m4a", "aac", "ts",
            "amr", "flac", "mid", "xmf", "mxmf",
            "rtttl", "rtx", "ota", "imy", "mp3",
            "mkv", "ogg", "wav", "webm")
    }

    private val media = MainActivity_Media(this)

    lateinit var l_main : MainBinding
    private lateinit var l_option : OptionBinding
    private lateinit var alert_option : AlertDialog

    lateinit var handler_delay:Handler
        private set

    private var name_game = ""

    private val procedures_requiring_permissions = arrayOf (
        ::InitGameMenu,
        ::TryInitMedia,
    )

    fun Alert(s:String) {
        AlertDialog.Builder(this).apply {
            setMessage(s)
            setPositiveButton(getString(R.string.ok)) { _, _ -> }
            create().show()
        }
    }
    fun Alert(s:String, action: (DialogInterface, Int) -> Unit) {
        AlertDialog.Builder(this).apply {
            setMessage(s)
            setPositiveButton(getString(R.string.ok), action)
            setNegativeButton(getString(R.string.cancel)) { _, _ -> }
            create().show()
        }
    }

    fun CheckFileAccess(s:String) {
        Alert(getString(R.string.merr_fileaccess, s)
                + "\nSDK ${android.os.Build.VERSION.SDK_INT}"
                + "\nREAD_EXTERNAL_STORAGE ${ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)}"
                + "\nMANAGE_EXTERNAL_STORAGE ${if (android.os.Build.VERSION.SDK_INT >= 30 )
                    ContextCompat.checkSelfPermission(this, Manifest.permission.MANAGE_EXTERNAL_STORAGE)
                else PERMISSION_GRANTED}")
    }

    private fun RequestStoragePermission(return_to:Int):Boolean {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PERMISSION_DENIED -> {
                AlertDialog.Builder(this).apply {
                    setMessage(getString(R.string.merr_nopermission_read))
                    setPositiveButton(getString(R.string.reqper)) { _, _ ->
                        ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), return_to)
                    }
                    setNegativeButton(getString(R.string.cancel)) { _, _ -> }
                    create().show()
                }
                return true
            }
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PERMISSION_DENIED -> {
                AlertDialog.Builder(this).apply {
                    setMessage(getString(R.string.merr_nopermission_write))
                    setPositiveButton("REQUEST PERMISSION") { _, _ ->
                        ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), return_to)
                    }
                    setNegativeButton(getString(R.string.cancel)) { _, _ -> }
                    create().show()
                }
                return true
            }
            android.os.Build.VERSION.SDK_INT >= 30 && ContextCompat.checkSelfPermission(this, Manifest.permission.MANAGE_EXTERNAL_STORAGE) == PERMISSION_DENIED -> {
                AlertDialog.Builder(this).apply {
                    setMessage(getString(R.string.merr_nopermission_manage))
                    setPositiveButton("REQUEST PERMISSION") { _, _ ->
                        ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.MANAGE_EXTERNAL_STORAGE), return_to)
                    }
                    setNegativeButton(getString(R.string.cancel)) { _, _ -> }
                    create().show()
                }
                return true
            }
            else -> {
                Alert(getString(R.string.merr_storagefailed))
                return false
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.getOrNull(0) == PERMISSION_GRANTED) {
            if (requestCode != -1) procedures_requiring_permissions[requestCode]()
        }
        else {
            Alert(getString(R.string.merr_permission_denied, permissions[0]))
        }
    }

    private val runnable_HideButtonsUpper = Runnable {
        l_main.buttonOption.visibility = View.INVISIBLE
        l_main.buttonHelp.visibility = View.INVISIBLE
    }
    fun OnClickUpper(v:View) {
        l_main.buttonOption.visibility = View.VISIBLE
        l_main.buttonHelp.visibility = View.VISIBLE
        handler_delay.removeCallbacks(runnable_HideButtonsUpper)
        handler_delay.postDelayed(runnable_HideButtonsUpper, 5000L)
    }

    private val runnable_HideButtonsLower = Runnable {
        l_main.llResponse.alpha = 0.3f
    }
    fun OnClickLower(v:View) {
        l_main.llResponse.alpha = 0.9f
        handler_delay.removeCallbacks(runnable_HideButtonsLower)
        handler_delay.postDelayed(runnable_HideButtonsLower, 5000L)
    }

    fun OnClickOption(v:View) {
        l_option.etName.setText(name_game)
        l_option.etDir.setText(media.dir_media)
        l_option.etTextspeed.setText(media.text_speed.toString())
        l_option.etImagespeed.setText(media.image_speed.toString())
        alert_option.show()
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.LOLLIPOP)
    fun OnClickOptionBrowse(v:View) {
        try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 1) }
        catch (_:Exception) {}
    }

    fun OnClickHelp(v:View) {
        Alert(getString(R.string.article_help))
    }

    override fun onActivityResult(requestCode:Int, resultCode:Int, data:Intent) {
        when (requestCode) {
            1 -> {
                if (resultCode == RESULT_CANCELED) return
                val path = data.data?.path
                if (path != null) {
                    val s = "${Environment.getExternalStorageDirectory().absolutePath}${File.separator}${path.substringAfter(':')}"
                    l_option.etDir.setText(s)
                }
            }
        }
    }

    private fun TryInitMedia() {
        File(media.dir_media).also {
            if (it.exists()) {
                if (it.canRead()) media.Init()
                else RequestStoragePermission(procedures_requiring_permissions.indexOf(::TryInitMedia))
            }
            else onInitMediaResult(false)
        }
    }

    fun onInitMediaResult(success: Boolean) {
        if (success) {
            l_main.tvNooutput.visibility = View.INVISIBLE
            runnable_HideButtonsUpper.run()
        }
        else {
            l_main.tvNooutput.text = resources.getString(R.string.merr_nomedia)
            l_main.tvNooutput.visibility = View.VISIBLE
        }
    }

    private fun SelectGame(name_game_selected:String) {
        val dir_sys = File(filesDir, "sys")
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
        if (dir_media != null) media.dir_media = dir_media

        val text_speed = s_settings.getOrNull(1)?.toLongOrNull()
        if (text_speed != null) media.text_speed = text_speed

        val image_speed = s_settings.getOrNull(2)?.toLongOrNull()
        if (image_speed != null) media.image_speed = image_speed

        name_game = name_game_selected
        setContentView(l_main.root)

        TryInitMedia()
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun InitGameMenu() {
        val dir_sys = File(filesDir, "sys")
        if (!dir_sys.exists()) {
            if(!dir_sys.mkdir()) {
                RequestStoragePermission(procedures_requiring_permissions.indexOf(::InitGameMenu))
                return
            }
        }
        val dir_settings = File(dir_sys, "settings")
        if (!dir_settings.exists()) {
            if(!dir_settings.mkdir()) {
                RequestStoragePermission(procedures_requiring_permissions.indexOf(::InitGameMenu))
                return
            }
        }
        val f_gameList = File(dir_sys, "game_list")
        if (!f_gameList.exists()) f_gameList.createNewFile()
        val games = f_gameList.readLines().toSortedSet()

        val l_game = GameBinding.inflate(layoutInflater)
        val layoutparam = LinearLayout.LayoutParams(600, LinearLayout.LayoutParams.WRAP_CONTENT)
        for (name_game_candidate in games) {
            val b = Button(this)
            b.layoutParams = layoutparam
            b.text = name_game_candidate
            b.setOnClickListener {
                (l_game.root.parent as? android.view.ViewGroup)?.removeView(l_game.root)
                SelectGame(name_game_candidate)
            }
            b.setOnLongClickListener {
                Alert(getString(R.string.tv_delete_game)) { _, _ ->
                    l_game.llMain.removeView(b)
                    games.remove(name_game_candidate)
                    f_gameList.writeText(games.joinToString("\n"))
                }
                true
            }
            l_game.llMain.addView(b)
        }
        val b_create = Button(this)
        b_create.layoutParams = layoutparam
        b_create.text = getString(R.string.tv_add_new_game)
        b_create.setOnClickListener {
            l_option.etName.setText(getString(R.string.tv_name_new_game))
            alert_option.show()
        }
        l_game.llMain.addView(b_create)

        alert_option = AlertDialog.Builder(this).apply {

            setTitle(getString(R.string.tv_game_setting))
            setView(l_option.root)
            setPositiveButton(getString(R.string.ok)) { _, _ ->
                if (name_game == "") {
                    val name_game_new = l_option.etName.text.toString().trim()

                    if (name_game_new in games) Alert(getString(R.string.merr_gamename_existing,name_game_new))
                    else if (!Regex("""[\w_ ]+""").matches(name_game_new)) Alert(getString(R.string.merr_gamename_invalid,name_game_new))
                    else {
                        games.add(name_game_new)
                        f_gameList.writeText(games.joinToString("\n"))

                        val f_setting = File(dir_settings, name_game_new)
                        if (!f_setting.exists()) f_setting.createNewFile()
                        f_setting.writeText("${l_option.etDir.text}\n${l_option.etTextspeed.text}\n${l_option.etImagespeed.text}")

                        (l_game.root.parent as? android.view.ViewGroup)?.removeView(l_game.root)
                        SelectGame(name_game_new)
                    }
                }
                else {
                    val f_setting = File(dir_settings, name_game)
                    if (!f_setting.exists()) f_setting.createNewFile()

                    val name_game_new = l_option.etName.text.toString().trim()
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

                    val dir_media = File(l_option.etDir.text.toString())
                    val s_dir_media:String
                    if (dir_media.isDirectory && dir_media.canRead()) {
                        s_dir_media = dir_media.absolutePath
                        if (media.topic == topic_uninitialized) {
                            media.dir_media = s_dir_media
                            TryInitMedia()
                        }
                        else if (l_option.etDir.text.toString() != media.dir_media) {
                            Alert(getString(R.string.merr_pathchanged))
                        }
                    }
                    else s_dir_media = media.dir_media
                    val text_speed = l_option.etTextspeed.text.toString().toLongOrNull()
                    if (text_speed != null && text_speed >= 100L) {
                        media.text_speed = text_speed
                    }
                    val image_speed = l_option.etImagespeed.text.toString().toLongOrNull()
                    if (image_speed != null) {
                        media.image_speed = image_speed
                    }
                    f_setting.writeText("${s_dir_media}\n${media.text_speed}\n${media.image_speed}")
                }
                /*getPreferences(Context.MODE_PRIVATE).edit()?.apply {
                    File(l_option.etDir.text.toString()).also {
                        if (it.isDirectory && it.canRead()) {
                            putString(prefkey_dir, l_option.etDir.text.toString())
                            if (media.topic == topic_uninitialized) {
                                media.dir_media = l_option.etDir.text.toString()
                                TryInitMedia(0)
                            }
                            else if (l_option.etDir.text.toString() != media.dir_media)
                                Alert(getString(R.string.merr_pathchanged))
                        }
                        else {
                            l_option.etDir.setText(media.dir_media)
                            Alert(getString(R.string.merr_badpath))
                        }
                    }

                    val text_speed = l_option.etTextspeed.text.toString().toLongOrNull()
                    if (text_speed != null && text_speed >= 100L) {
                        media.text_speed = text_speed
                        putLong(prefkey_textspeed, text_speed)
                    }

                    val image_speed = l_option.etImagespeed.text.toString().toLongOrNull()
                    if (image_speed != null) {
                        media.image_speed = image_speed
                        putLong(prefkey_imagespeed, image_speed)
                    }

                    apply()
                }*/

            }
            setNegativeButton(getString(R.string.cancel)) { _, _ -> }
        }.create()

        l_main.relaMain.setOnTouchListener(swipeListener({ _, p1, x_src, _ ->
            p1.x !in x_src - 100f .. x_src + 100f
        }) { _, _, _, _ ->
            media.ChangeImage()
            true
        })
        setContentView(l_game.root)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handler_delay = Handler(mainLooper)

        l_option = OptionBinding.inflate(layoutInflater)
        l_main = MainBinding.inflate(layoutInflater)

        l_main.llResponse.setOnClickListener(::OnClickLower)
        l_main.llOptionHelp.setOnClickListener(::OnClickUpper)
        l_main.buttonOption.setOnClickListener(::OnClickOption)
        l_main.buttonHelp.setOnClickListener(::OnClickHelp)

        getPreferences(Context.MODE_PRIVATE)?.also {
            if (!it.contains(prefkey_welcome)) {
                handler_delay.post {
                    AlertDialog.Builder(this).apply {
                        setMessage(getString(R.string.article_welcome))
                        setPositiveButton(getString(R.string.got)) { _, _ ->
                            it.edit().apply {
                                putBoolean(prefkey_welcome, true)
                                apply()
                            }
                        }
                        setNegativeButton(getString(R.string.nogot)) { _, _ -> }
                        create().show()
                    }
                }
            }
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        InitGameMenu()
    }
}
