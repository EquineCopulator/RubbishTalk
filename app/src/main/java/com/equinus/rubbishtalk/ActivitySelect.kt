package com.equinus.rubbishtalk

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager.*
import android.Manifest.permission.*
import android.os.Build.VERSION.SDK_INT
import android.widget.Button
import android.widget.LinearLayout.LayoutParams
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.app.ActivityCompat.requestPermissions
import com.equinus.rubbishtalk.databinding.SelectBinding
import com.equinus.rubbishtalk.databinding.OptionBinding
import java.io.File

class ActivitySelect:android.app.Activity() {
    private lateinit var lytSelect:SelectBinding
    private lateinit var lytNew:OptionBinding
    private lateinit var lytOption:OptionBinding

    private lateinit var dlgNew:AlertDialog
    private lateinit var dlgOption:AlertDialog
    private lateinit var dlgErr:AlertDialog
    private lateinit var dlgPerm:AlertDialog
    private lateinit var dlgDelete:AlertDialog

    private lateinit var bCreate:Button

    private lateinit var dirSys:File
    private lateinit var dirSettings:File
    private lateinit var fGameList:File

    private val games = java.util.TreeSet<String>()
    private var gameSelected = ""

    private var dirMedia:String? = null
    private var textSpeed = -1L
    private var imageSpeed = -1L

    private var reqPerm = READ_EXTERNAL_STORAGE
    private var reqCode = 0

    companion object {
        private const val TEXT_SPEED_DEFAULT = 333L
        private const val IMAGE_SPEED_DEFAULT = 10000L

        private const val REQUEST_WHEN_LOADLIST = 0
        private const val REQUEST_WHEN_OPENOPTION = 1
        private const val REQUEST_WHEN_NEWGAME = 2
        private const val REQUEST_WHEN_SAVEOPTION = 3
        private const val REQUEST_WHEN_START = 4

        private const val ACT_START = 0
        private const val ACT_BROWSE_NEW = 1
        private const val ACT_BROWSE_CHANGE = 2

        private const val PFKEY_WELCOME = "prefkey_welcome"

        private val dirBase =
            android.os.Environment.getExternalStorageDirectory()
        private val dirMediaDefault =
            File(dirBase, "Rubbish Talk").absolutePath
        private val validGameName = Regex("""[\w_ ]+""")
        private val lparamButton = LayoutParams(600, LayoutParams.WRAP_CONTENT)
    }

    override fun onCreate(savedInstanceState:android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        lytSelect = SelectBinding.inflate(layoutInflater)
        lytNew = OptionBinding.inflate(layoutInflater)
        lytOption = OptionBinding.inflate(layoutInflater)

        dlgNew = AlertDialog.Builder(this)
            .setTitle(R.string.tv_name_new_game)
            .setView(lytNew.root)
            .setPositiveButton(R.string.ok) { _, _ -> newGame() }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .create()
        dlgOption = AlertDialog.Builder(this)
            .setTitle(R.string.tv_game_setting)
            .setView(lytOption.root)
            .setPositiveButton(R.string.ok) { _, _ -> changeGame() }
            .setNeutralButton(R.string.delete) { _, _ -> dlgDelete.show() }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .create()
        dlgErr = AlertDialog.Builder(this)
            .setPositiveButton(R.string.ok) { _, _ -> }
            .create()
        dlgPerm = AlertDialog.Builder(this)
            .setPositiveButton(R.string.ok) { _, _ -> requestPermStart() }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .create()
        dlgDelete = AlertDialog.Builder(this)
            .setMessage(R.string.tv_delete_game)
            .setPositiveButton(R.string.ok) { _, _ -> deleteGame() }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .create()

        bCreate = Button(this)
        bCreate.layoutParams = lparamButton
        bCreate.setText(R.string.tv_add_new_game)
        bCreate.setOnClickListener { showDialogNew() }

        dirSys = File(filesDir, "sys")
        dirSettings = File(dirSys, "settings")
        fGameList = File(dirSys, "game_list")

        lytNew.bOptionBrowse.setOnClickListener { browseMediaDirNew() }
        lytOption.bOptionBrowse.setOnClickListener { browseMediaDirChange() }
        lytSelect.buttonHelp.setOnClickListener { showHelp() }

        loadGameList()
    }

    override fun onRequestPermissionsResult(
        requestCode:Int,
        permissions:Array<String>,
        grantResults:IntArray)
    {
        if (grantResults[0] == PERMISSION_GRANTED) {
            when (requestCode) {
                REQUEST_WHEN_LOADLIST -> loadGameList()
                REQUEST_WHEN_OPENOPTION -> showDialogChange()
                REQUEST_WHEN_NEWGAME -> newGame()
                REQUEST_WHEN_SAVEOPTION -> changeGame()
                REQUEST_WHEN_START -> startGame()
            }
        }
        else {
            dlgErr.setMessage(getString(R.string.merr_permission_denied, permissions[0]))
            dlgErr.show()
        }
    }

    override fun onActivityResult(requestCode:Int, resultCode:Int, data:Intent?) {
        when (requestCode) {
            ACT_START -> if (data != null) {
                if (data.getBooleanExtra(SharedConst.EXTRA_PERM_DENIED, false))
                    requestPerm(REQUEST_WHEN_START)
                else {
                    dlgErr.setMessage(data.getStringExtra(SharedConst.EXTRA_ERROR) ?: return)
                    dlgErr.show()
                }
            }
            ACT_BROWSE_NEW -> if (resultCode == RESULT_OK) {
                val path = data?.data?.path ?: return
                lytNew.etDir.setText(File(dirBase, path.substringAfter(':')).absolutePath)
            }
            ACT_BROWSE_CHANGE -> if (resultCode == RESULT_OK) {
                val path = data?.data?.path ?: return
                lytOption.etDir.setText(File(dirBase, path.substringAfter(':')).absolutePath)
            }
        }
    }

    private fun tryCreateDirs() =
        if (!dirSys.exists() && !dirSys.mkdir()) true
        else if (!dirSettings.exists() && !dirSettings.mkdir()) true
        else false

    private fun requestPerm(requestCode:Int) {
        reqCode = requestCode
        if (checkSelfPermission(this, READ_EXTERNAL_STORAGE) == PERMISSION_DENIED) {
            reqPerm = READ_EXTERNAL_STORAGE
            dlgPerm.setMessage(getString(R.string.merr_nopermission_read))
            dlgPerm.show()
        }
        else if (checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == PERMISSION_DENIED) {
            reqPerm = WRITE_EXTERNAL_STORAGE
            dlgPerm.setMessage(getString(R.string.merr_nopermission_write))
            dlgPerm.show()
        }
        else if (SDK_INT >= 30 && checkSelfPermission(this, MANAGE_EXTERNAL_STORAGE) == PERMISSION_DENIED) {
            reqPerm = MANAGE_EXTERNAL_STORAGE
            dlgPerm.setMessage(getString(R.string.merr_nopermission_manage))
            dlgPerm.show()
        }
        else {
            dlgErr.setMessage(getString(R.string.merr_storagefailed))
            dlgErr.show()
        }
    }

    private fun requestPermStart() =
        requestPermissions(this, arrayOf(reqPerm), reqCode)

    private fun readSetting(name:String):Boolean {
        val fSetting = File(dirSettings, name)
        if (fSetting.canRead()) {
            fSetting.useLines {
                val lines = it.iterator()

                dirMedia = if (!lines.hasNext()) null
                else lines.next()

                textSpeed = if (!lines.hasNext()) -1L
                else {
                    val r = lines.next().toLongOrNull() ?: -1L
                    if (r >= 100) r
                    else -1L
                }

                imageSpeed = if (!lines.hasNext()) -1L
                else {
                    val r = lines.next().toLongOrNull() ?: -1L
                    if (r >= 0) r
                    else -1L
                }
            }
            return false
        }
        else {
            dirMedia = null
            textSpeed = -1L
            imageSpeed = -1L
            return fSetting.exists()
        }
    }

    private fun showDialogNew() {
        gameSelected = ""
        lytNew.etName.text.clear()
        lytNew.etDir.setText(dirMediaDefault)
        lytNew.etImagespeed.text.clear()
        lytNew.etTextspeed.text.clear()
        dlgNew.show()
    }

    private fun showDialogChange() {
        val name = gameSelected
        lytOption.etName.setText(name)

        if (readSetting(name)) return requestPerm(REQUEST_WHEN_OPENOPTION)

        if (dirMedia == null) lytOption.etDir.setText(dirMediaDefault)
        else lytOption.etDir.setText(dirMedia)
        if (textSpeed < 0) lytOption.etTextspeed.text.clear()
        else lytOption.etTextspeed.setText(textSpeed.toString())
        if (imageSpeed < 0) lytOption.etImagespeed.text.clear()
        else lytOption.etImagespeed.setText(imageSpeed.toString())

        dlgOption.show()
    }

    private fun browseMediaDirNew() {
        if (SDK_INT >= 21)
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), ACT_BROWSE_NEW)
    }

    private fun browseMediaDirChange() {
        if (SDK_INT >= 21)
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), ACT_BROWSE_CHANGE)
    }

    private fun createGameButton() {
        lytSelect.llMain.removeAllViews()

        for (g in games) {
            val b = Button(this)
            b.layoutParams = lparamButton
            b.text = g
            b.setOnClickListener {
                gameSelected = g
                startGame()
            }
            b.setOnLongClickListener {
                gameSelected = g
                showDialogChange()
                true
            }
            lytSelect.llMain.addView(b)
        }

        lytSelect.llMain.addView(bCreate)
    }

    private fun loadGameList() {
        if (tryCreateDirs()) return requestPerm(REQUEST_WHEN_LOADLIST)

        if (fGameList.exists())
            fGameList.forEachLine { games.add(it) }
        else fGameList.createNewFile()

        createGameButton()

        setContentView(lytSelect.root)

        val pref = getPreferences(MODE_PRIVATE)
        if (pref != null && !pref.contains(PFKEY_WELCOME)) {
            AlertDialog.Builder(this)
                .setMessage(R.string.article_welcome)
                .setPositiveButton(R.string.got) { _, _ ->
                    pref.edit().putBoolean(PFKEY_WELCOME, true).apply()
                }
                .setNegativeButton(R.string.nogot) { _, _ -> }
                .create()
                .show()
        }
    }

    private fun isGameNameValid(newName:String) =
        if (!validGameName.matches(newName)) {
            dlgErr.setMessage(getString(R.string.merr_gamename_invalid, newName))
            dlgErr.show()
            false
        }
        else if (newName in games) {
            dlgErr.setMessage(getString(R.string.merr_gamename_existing, newName))
            dlgErr.show()
            false
        }
        else true

    private fun newGame() {
        val newName = lytNew.etName.text.toString()
        if (isGameNameValid(newName)) {
            val fSetting = File(dirSettings, newName)
            if (!fSetting.exists()) {
                if (tryCreateDirs()) return requestPerm(REQUEST_WHEN_NEWGAME)
                fSetting.createNewFile()
            }

            games.add(newName)
            createGameButton()
            fGameList.writeText(games.joinToString("\n"))

            var textSpeed = lytNew.etTextspeed.text.toString().toLongOrNull() ?: -1L
            if (textSpeed < 100) textSpeed = TEXT_SPEED_DEFAULT

            var imageSpeed = lytNew.etImagespeed.text.toString().toLongOrNull() ?: -1L
            if (imageSpeed < 0) imageSpeed = IMAGE_SPEED_DEFAULT

            fSetting.writeText("${ lytNew.etDir.text }\n$textSpeed\n$imageSpeed")
        }
    }

    private fun changeGame() {
        val newName = lytOption.etName.text.toString()

        val fSetting:File
        if (gameSelected != newName && isGameNameValid(newName)) {
            fSetting = File(dirSettings, newName)
            if (!fSetting.exists()) {
                if (tryCreateDirs()) return requestPerm(REQUEST_WHEN_SAVEOPTION)
                fSetting.createNewFile()
            }

            File(dirSettings, gameSelected).delete()
            games.remove(gameSelected)
            games.add(newName)
            createGameButton()
            fGameList.writeText(games.joinToString("\n"))
        }
        else {
            fSetting = File(dirSettings, gameSelected)
            if (!fSetting.exists()) {
                if (tryCreateDirs()) return requestPerm(REQUEST_WHEN_SAVEOPTION)
                fSetting.createNewFile()
            }
        }

        var textSpeed = lytOption.etTextspeed.text.toString().toLongOrNull() ?: -1L
        if (textSpeed < 100) textSpeed = TEXT_SPEED_DEFAULT

        var imageSpeed = lytOption.etImagespeed.text.toString().toLongOrNull() ?: -1L
        if (imageSpeed < 0) imageSpeed = IMAGE_SPEED_DEFAULT

        fSetting.writeText("${ lytOption.etDir.text }\n$textSpeed\n$imageSpeed")
    }

    private fun deleteGame() {
        games.remove(gameSelected)
        createGameButton()
        fGameList.writeText(games.joinToString("\n"))
    }

    private fun startGame() {
        if (readSetting(gameSelected)) return requestPerm(REQUEST_WHEN_START)

        if (dirMedia == null || textSpeed < 0 || imageSpeed < 0)
            return showDialogChange()

        startActivityForResult(Intent(this, ActivityGame::class.java)
            .putExtra(SharedConst.EXTRA_DIR_MEDIA, dirMedia)
            .putExtra(SharedConst.EXTRA_TEXT_SPEED, textSpeed)
            .putExtra(SharedConst.EXTRA_IMAGE_SPEED, imageSpeed), ACT_START)
    }

    private fun showHelp() =
        startActivity(Intent(this, ActivityHelp::class.java))
}