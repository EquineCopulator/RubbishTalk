package com.equinus.rubbishtalk

import android.os.SystemClock.uptimeMillis
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import java.io.File

class MediaLines(
    private val width:Int,
    height:Int,
    private val handler:android.os.Handler,
    private val exit:(String?)->Unit,
    private val images:MediaImages,
    private val audios:MediaAudios,
    private val dirMedia:String,
    private val textSpeed:Long,
    private val context:android.content.Context,
    private val random:kotlin.random.Random,
    private val vText:ViewGroup,
    private val vQuestion:ViewGroup,
    private val vResponse:ViewGroup)
{
    @UiThread fun init():Boolean {
        val f = File(dirMedia, "script.txt")
        return if (f.canRead()) {
            topic = ""
            loadScript(f)
            lines_main.isNotEmpty()
        }
        else false
    }

    @UiThread fun start() {
        time_start = System.currentTimeMillis()
        executor.execute {
            todo_jump = 0
            prepareNextLine(2000L)
        }
    }
    @UiThread fun stop() { stopped = true }
    @UiThread fun pause() { paused = true }
    @UiThread fun resume() { paused = false }

    private var lines_main = listOf("")
    private val lines_passages = mutableMapOf<String, List<String>>()
    private val label_positions = mutableMapOf<String, MutableMap<String, Int>>()
    private val callback_token = Object()
    private val executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
    //private val executor = object { fun execute(r:Runnable) { r.run() } }
    @Volatile private var paused = false
    @Volatile private var stopped = false

    private val fontsize = if (height > 0) (height / 32).toFloat() else 30f
    private var topic = SharedConst.TOPIC_UNINITED
    private var passage = lines_main
    private var passage_name = ""

    private var i_lines = -1
    private var i_lines_return = -1
    private val mr = MemorizedRandom(random)

    private var time_start = 0L
    private var frequency_metronome = 0
    private var todo_jump = -1
    private var todo_wait = 0
    private var todo_metronome = -1
    private var todo_ask:Runnable? = null
    private var todo_react:Runnable? = null
    private var todo_media:Runnable? = null

    private val cm = CScriptMachine()
    init {
        //int exit()
        cm.func["exit"] = {
            handler.removeCallbacksAndMessages(callback_token)
            handler.post{ exit(null) }
            passage = listOf()
            0
        }
        //int jmp(int jump_offset)
        cm.func["jmp"] = {
            todo_jump = i_lines + it[0] as Int
            todo_jump
        }
        //int jmpto(int jump_to_line)
        cm.func["jmpto"] = {
            val todo_jump_original = todo_jump
            todo_jump = it[0] as Int
            todo_jump_original
        }
        //int lb(string label_name)
        cm.func["lb"] = {
            label_positions[passage_name]?.get(it[0] as String) ?: -1
        }
        //int lb(string passage_name, string label_name)
        cm.func["lbp"] = {
            label_positions[it[0] as String]?.get(it[1] as String) ?: -1
        }
        //int lb(int upper_limit)
        cm.func["random"] = {
            val max = it[0] as Int
            if (max <= 0) -1
            else random.nextInt(max)
        }
        //int time()
        cm.func["time"] = {
            (System.currentTimeMillis() - time_start).toInt()
        }
        //int timeunix(int right_shift_bits)
        cm.func["timeunix"] = {
            (System.currentTimeMillis() ushr Int.SIZE_BITS * it[0] as Int).toInt()
        }
        //int retpos()
        cm.func["retpos"] = {
            i_lines_return
        }
        //int wait(int wait_time)
        cm.func["wait"] = {
            val todo_wait_original = todo_wait
            todo_wait += it[0] as Int
            todo_wait_original
        }
        //int metronome(int interval)
        cm.func["metronome"] = {
            todo_metronome = it[0] as Int
            frequency_metronome
        }
        //string media(int type, string topic)
        cm.func["media"] = {
            val topic_new = it[1] as String
            when(it[0] as? Int) {
                1 -> {
                    todo_media = Runnable{ images.topic = topic_new }
                    images.topic
                }
                2 -> {
                    todo_media = Runnable{ audios.topic = topic_new }
                    audios.topic
                }
                else -> ""
            }
        }
        //void ask(int *answer_storage, ...)
        //... := string item1, int value1, string item2, int value2, ...
        cm.func["ask"] = {
            val ret = it[0].toString()
            val answers = it.drop(1)
            cm.mem.remove(ret)
            todo_ask = Runnable {
                vQuestion.removeAllViews()
                for (i in 0 .. answers.size - 2 step 2) {
                    val s = answers[i] as String
                    val n = answers[i + 1] as Int
                    vQuestion.addView(LabeledRoundButton(context).apply {
                        val color_id = button_colors[(button_colors.size / 2 + i) % button_colors.size]
                        color = if (android.os.Build.VERSION.SDK_INT >= 23)
                            context.resources.getColor(color_id, null)
                        else @Suppress("DEPRECATION") context.resources.getColor(color_id)
                        text = s
                        textSize = fontsize
                        setCircleOnClickListener {
                            vQuestion.visibility = View.INVISIBLE
                            cm.mem[ret] = n
                        }
                    })
                }
                if (answers.isNotEmpty()) vQuestion.visibility = View.VISIBLE
            }
        }
        //void react(...)
        //... := string react_passage1, string react_passage2, ...
        cm.func["react"] = {
            val actions = it.filter { s -> s in lines_passages }
            todo_react = Runnable {
                var i0 = 0
                vResponse.removeAllViews()
                for (s in actions) {
                    vResponse.addView(LabeledRoundButton(context).apply {
                        color = if (android.os.Build.VERSION.SDK_INT >= 23)
                            context.resources.getColor(button_colors[i0], null)
                        else @Suppress("DEPRECATION") context.resources.getColor(button_colors[i0])
                        text = s as String
                        textSize = fontsize
                        setCircleOnClickListener(::react)
                    })
                    i0 = (i0 + 1) % button_colors.size
                }
                showResponseButton()
            }
        }
        //int load(string module_name)
        cm.func["load"] = {
            try {
                context.openFileInput(it[0] as String).apply {
                    @Suppress("UNCHECKED_CAST")
                    val mem_loaded = java.io.ObjectInputStream(this).readObject() as MutableMap<String, Int>
                    cm.mem.clear()
                    cm.mem += mem_loaded
                    close()
                }; 0
            }
            catch (e: java.io.FileNotFoundException) { 1 }
            catch (e: TypeCastException) {
                context.deleteFile(it[0] as String)
                exit(context.getString(R.string.merr_badmemory))
                2
            }
        }
        //int save(string module_name)
        cm.func["save"] = {
            try {
                val o = context.openFileOutput(it[0] as String, android.content.Context.MODE_PRIVATE)
                java.io.ObjectOutputStream(o).writeObject(cm.mem)
                o.close()
                0
            }
            catch (e: java.io.FileNotFoundException) { 1 }
        }
    }

    private val metronome = if (android.os.Build.VERSION.SDK_INT >= 21)
        android.media.SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_GAME)
                .build())
            .build()
    else @Suppress("DEPRECATION") android.media.SoundPool(3, android.media.AudioManager.STREAM_MUSIC, 0)
    private val metronomeLong = metronome.load(context, R.raw.metronome, 1)
    private val metronomeMiddle = metronomeLong
    private val metronomeShort = metronomeLong
    private var metronomeInterval = 0

    private val rHideResponse = Runnable { vResponse.alpha = 0.3f }
    private val rMetronomeLong = object:Runnable {
        override fun run() {
            metronome.play(metronomeLong, 1.0F, 1.0F, 0, 0, 1.0F)
            handler.postDelayed(this, metronomeInterval.toLong())
        }
    }
    private val rMetronomeMiddle = object:Runnable {
        override fun run() {
            metronome.play(metronomeMiddle, 1.0F, 1.0F, 0, 0, 1.0F)
            handler.postDelayed(this, metronomeInterval.toLong())
        }
    }
    private val rMetronomeShort = object:Runnable {
        override fun run() {
            metronome.play(metronomeShort, 1.0F, 1.0F, 0, 0, 1.0F)
            handler.postDelayed(this, metronomeInterval.toLong())
        }
    }

    companion object {
        private val text_colors = arrayOf(
            R.color.Poppy,
            R.color.Rose,
            R.color.Phlox,
            R.color.Byzantium,
            R.color.Pansy)

        private val button_colors = arrayOf(
            R.color.Red,
            R.color.Orange,
            R.color.Yellow,
            R.color.Green,
            R.color.Blue,
            R.color.Cyan,
            R.color.Purple)
    }

    @WorkerThread private fun runScript(s:String):Any {
        try {
            val ret = cm.Eval(s)
            return (ret as? Pair<*,*>)?.first ?: ret
        }
        catch (e:CScriptMachine.BadScriptException) {
            val line = i_lines + 1
            val ns = topic
            handler.post {
                exit(context.getString(R.string.merr_badscript,
                    when (e) {
                        is CScriptMachine.InvalidSyntax -> context.getString(R.string.merr_badscript_syntax)
                        is CScriptMachine.UnbalancedBracket -> context.getString(R.string.merr_badscript_bracket)
                        is CScriptMachine.UndefinedName -> context.getString(R.string.merr_badscript_name)
                        is CScriptMachine.AssignToRvalue -> context.getString(R.string.merr_badscript_rvalue)
                        is CScriptMachine.TypeError -> context.getString(R.string.merr_badscript_type, e.s_value, e.s_type)
                    },
                    if (e.message?.isNotBlank() == true) ": ${e.message}" else ".",
                    line,
                    if (ns == "") "script.txt" else "script_${ns}.txt"))
            }
        }
        catch (e:Exception) {
            val line = i_lines + 1
            val ns = topic
            handler.post {
                exit(context.getString(R.string.merr_badscript, e.toString(), "", line, if (ns == "")
                    "script.txt"
                else "script_${ns}.txt"))
            }
        }
        return ""
    }

    @WorkerThread private fun getLevel(s:String):Int {
        val level = s.indexOfFirst { c:Char -> !c.isWhitespace() }
        return when {
            level == -1 -> s.length shl 1
            s[level] == ':' -> level shl 1
            else -> level shl 1 or 1
        }
    }

    @WorkerThread private fun nextLine() {
        var jump_to_top = true
        if (i_lines < passage.size - 1) {
            val level_this = getLevel(passage[i_lines]) and -2
            val level_next = getLevel(passage[i_lines + 1])
            if (level_next and 1 != 0) {
                jump_to_top = false
                i_lines += 1
            }
            else if (level_next > level_this) {
                jump_to_top = false
                mutableListOf(i_lines + 1).also {
                    for (i in i_lines + 2 until passage.size) {
                        val level_new = getLevel(passage[i])
                        if (level_next == level_new) it.add(i)
                        else if (level_next > level_new) break
                    }
                    i_lines = it[mr.Next(Triple(topic, passage_name, i_lines), it.size)]
                }
            }
            else if (level_next == level_this) {
                var level_base = level_this
                for (i in i_lines + 2 until passage.size) {
                    val level_base_new = getLevel(passage[i])
                    if (level_base_new < level_base) {
                        if (level_base_new and 1 != 0) {
                            jump_to_top = false
                            i_lines = i
                            break
                        }
                        if (level_base_new == 0) break
                        level_base = level_base_new
                    }
                }
            }
        }
        if (jump_to_top) {
            mutableListOf(0).also {
                var level = getLevel(passage.first())
                for(i in 1 until passage.size) {
                    val level_this = getLevel(passage[i])
                    if (level_this and 1 == 0) {
                        if (level > level_this) {
                            level = level_this
                            it.clear()
                        }
                        it.add(i)
                    }
                }
                i_lines = it[mr.Next(Triple(topic, passage_name, -1), it.size)]
            }
        }
    }

    @WorkerThread private fun calculateDelay(s:String):Long = when {
        s.isBlank() -> 0L
        s.count { !it.isWhitespace() } == 2 && s.first().isSurrogate() -> 2000L
        else -> 1000L +
            textSpeed * (s.count { c:Char->c.isLetterOrDigit() }) /*+
            textSpeed * (s.count { c:Char->c in '\u4E00' .. '\u9FFF' })*/
    }

    @WorkerThread private fun parseLine(last_delay:Long) {
        todo_wait = 0
        todo_jump = -1
        todo_metronome = -1
        todo_ask = null
        todo_react = null
        todo_media = null
        val s = passage[i_lines]
            .trimStart()
            .removePrefix(":")
            .replace(Regex("""^@.*?@"""), "")
            .replace(Regex("""\$(\$)?\{(.*?)\}""")) {
            val s_ret = runScript(it.groups[2]!!.value)
            if (it.groups[1] != null) "" else s_ret.toString()
        }
        val wait_offset = todo_wait
        when {
            "=>" in s -> {
                val s_display = CScriptMachine.escape(s.substringBeforeLast("=>").trimEnd())
                val delay = calculateDelay(s_display)
                val target_goto = s.substringAfterLast("=>").trim { it.isWhitespace() || it == '_' }
                if (target_goto != topic) {
                    val f = File(dirMedia, "script${ if (target_goto == "") "" else "_$target_goto" }.txt")
                    if (f.canRead()) {
                        topic = target_goto
                        loadScript(f)
                        todo_jump = 0
                    }
                }
                handler.postAtTime({
                    displayLine(s_display, delay)
                    executor.execute { prepareNextLine(delay + wait_offset) }
                }, callback_token, uptimeMillis() + last_delay)
            }
            "<-" in s && passage !== lines_main -> {
                val s_display = CScriptMachine.escape(s.substringBeforeLast("<-").trimEnd())
                val delay = calculateDelay(s_display)
				passage = lines_main
                passage_name = ""
                s.substringAfterLast("<-").trimEnd().toIntOrNull().also {
                    if (it != null) todo_jump = i_lines_return + it
                    else i_lines = i_lines_return
                }
                handler.postAtTime({
                    displayLine(s_display, delay)
                    handler.postAtTime({ vResponse.visibility = View.VISIBLE }, callback_token, uptimeMillis() + delay + wait_offset)
                    executor.execute { prepareNextLine(delay + wait_offset) }
                }, callback_token, uptimeMillis() + last_delay)
            }
            "->" in s && passage === lines_main -> {
                val s_display = CScriptMachine.escape(s.substringBeforeLast("->").trimEnd())
                val delay = calculateDelay(s_display)
				val pos_block_new = lines_passages[s.substringAfterLast("->", "")]
				if (pos_block_new != null) {
					i_lines_return = i_lines
					passage = pos_block_new
                    passage_name = s.substringAfterLast("->")
                    todo_jump = 0
                    handler.postAtTime({
                        vResponse.visibility = View.INVISIBLE
                        displayLine(s_display, delay)
                        executor.execute { prepareNextLine(delay + wait_offset) }
                    }, callback_token, uptimeMillis() + last_delay)
				}
                else handler.postAtTime({
                    displayLine(s_display, delay)
                    executor.execute { prepareNextLine(delay + wait_offset) }
                }, callback_token, uptimeMillis() + last_delay)
            }
            else -> {
                val delay = calculateDelay(s)
                handler.postAtTime({
                    displayLine(CScriptMachine.escape(s), delay)
                    executor.execute { prepareNextLine(delay + wait_offset) }
                }, callback_token, uptimeMillis() + last_delay)
            }
        }
    }

    @WorkerThread private fun prepareNextLine(delay:Long) {
        if (stopped) return
        while (paused) Thread.sleep(1000L)

        if (passage.isNotEmpty()) {
            val todonow_metronome = todo_metronome
            if (todonow_metronome >= 0) {
                frequency_metronome = todonow_metronome
                handler.post{ setMetronome(todonow_metronome) }
            }

            val todonow_ask = todo_ask
            if (todonow_ask != null) handler.post(todonow_ask)

            val todonow_react = todo_react
            if (todonow_react != null) handler.post(todonow_react)

            val todonow_media = todo_media
            if (todonow_media != null) handler.post(todonow_media)

            if (todo_jump in passage.indices)
                i_lines = todo_jump
            else nextLine()
            parseLine(delay)
        }
    }

    private fun loadScript(f:File) {
        val lines = f.readLines()
        var i0 = lines.size
        for (i in lines.indices) {
            if (lines[i].startsWith('#')) {
                i0 = i
                break
            }
            else {
                val m = Regex("""^\s*:?@(.*?)@""").find(lines[i])
                if (m != null) {
                    val ns = label_positions.getOrPut("") { mutableMapOf() }
                    ns[m.groups[1]!!.value] = i
                }
            }
        }
        lines_main = lines.subList(0, i0)
        i0 += 1
        lines_passages.clear()
        for (i in i0 until lines.size) {
            if (lines[i].startsWith('#')) {
                if (i0 < i) {
                    lines_passages[lines[i0 - 1].substring(1).trim()] = lines.subList(i0, i)
                }
                i0 = i + 1
            }
            else {
                val m = Regex("""^\s*:?@(.*?)@""").find(lines[i])
                if (m != null) {
                    val ns = label_positions.getOrPut(lines[i0 - 1].substring(1)) { mutableMapOf() }
                    ns[m.groups[1]!!.value] = i - i0
                }
            }
        }
        if (i0 < lines.size)
            lines_passages[lines[i0 - 1].substring(1).trim()] = lines.subList(i0, lines.size)
        lines_passages.remove("")

        i_lines = lines_main.size - 1
        passage = lines_main
        passage_name = ""
    }

    @UiThread private fun displayLine(s:String, delay:Long) { //, -1, 0.15f
        if (delay == 0L) return
        val tv = OutlineTextView(context).also {
            it.outline_color = -1
            it.outline_width = 0.15f
            it.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            it.text = s
            it.DoEmoji(context)
            val color_id = text_colors[random.nextInt(text_colors.size)]
            it.setTextColor(if (android.os.Build.VERSION.SDK_INT >= 23)
                context.resources.getColor(color_id, null)
            else @Suppress("DEPRECATION") context.resources.getColor(color_id))
            it.textSize = fontsize//TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, fontsize, activity.resources.displayMetrics)
            it.typeface = android.graphics.Typeface.DEFAULT_BOLD
            it.maxWidth = width * 3 / 10
            it.setTextIsSelectable(false)
            it.visibility = View.INVISIBLE
        }
        vText.addView(tv)
        handler.postDelayed({
            //tv.width and tv.height will be zero if not delayed
            tv.x = fontsize * 3//multimedia.random.nextFloat() * (activity.rela_main.width - tv.width)
            tv.y = fontsize * 3//multimedia.random.nextFloat() * (activity.rela_main.height - tv.height)
            tv.visibility = View.VISIBLE
            AlphaAnimation(0f, 1f).also {
                it.duration = if(delay > 2000L) 200L else delay / 10
                tv.startAnimation(it)
            }
        }, 100L)
        handler.postDelayed({
            AlphaAnimation(1f, 0f).also {
                it.duration = if(delay > 8000L) 2000L else delay / 4
                tv.startAnimation(it)
            }
        }, delay - if(delay > 8000L) 1600L else delay / 5)
        handler.postDelayed({
            vText.removeView(tv)
        }, delay + if(delay > 8000L) 400L else delay / 20)
    }

    private fun react(v:View) {
        vResponse.visibility = View.INVISIBLE
        handler.removeCallbacksAndMessages(callback_token)
        vText.removeAllViews()
        executor.execute {
            i_lines_return = i_lines
            passage = lines_passages[(v.parent as LabeledRoundButton).text]!!
            passage_name = (v.parent as LabeledRoundButton).text.toString()
            todo_jump = 0
            prepareNextLine(0L)
        }
    }

    @UiThread private fun showResponseButton() {
        vResponse.alpha = 0.9f
        handler.removeCallbacks(rHideResponse)
        handler.postDelayed(rHideResponse, 5000L)
    }

    @UiThread private fun setMetronome(interval:Int) {
        handler.removeCallbacks(rMetronomeLong)
        handler.removeCallbacks(rMetronomeMiddle)
        handler.removeCallbacks(rMetronomeShort)
        metronomeInterval = interval
        handler.post(when {
            interval >= 3000 -> rMetronomeLong
            interval >= 1000 -> rMetronomeMiddle
            interval >= 200 -> rMetronomeShort
            else -> return
        })
    }
}