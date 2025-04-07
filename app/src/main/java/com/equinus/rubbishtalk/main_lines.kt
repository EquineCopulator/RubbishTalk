package com.equinus.rubbishtalk

import android.os.SystemClock.uptimeMillis
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import java.io.File

class MainActivity_Lines(private val that:MainActivity, private val parent:MainActivity_Media) {
    fun Init() {
        val f = File(parent.dir_media, "script.txt")
        if (f.canRead()) {
            val parent_height = that.l_main.viewMain.height
            fontsize = if (parent_height > 0) (parent_height / 32).toFloat() else 30f

            time_start = System.currentTimeMillis()

            executor.execute {
                topic = ""
                LoadScript(f)
                val success = lines_main.isNotEmpty()
                that.handler_delay.post { parent.onScriptResult(success) }
                todo_jump = 0
                PrepareNextLine(2000L)
            }
        }
        else parent.onScriptResult(false)
    }

    private var lines_main = listOf("")
    private val lines_passages = mutableMapOf<String, List<String>>()
    private val label_positions = mutableMapOf<String, MutableMap<String, Int>>()
    private val callback_token = Object()
    private val executor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()
    //private val executor = object { fun execute(r:Runnable) { r.run() } }
    var text_speed = 333L

    private var fontsize = 30f
    private var topic = MainActivity.topic_uninitialized
    private var passage = lines_main
    private var passage_name = ""

    private var i_lines = -1
    private var i_lines_return = -1
    private val mr = MemorizedRandom(parent.random)

    private var time_start = 0L
    private var frequency_metronome = 0
    private var todo_jump = -1
    private var todo_wait = 0
    private var todo_metronome = -1
    private var todo_ask:Runnable? = null
    private var todo_react:Runnable? = null
    private var todo_media:Runnable? = null

    private val cm = CScriptVirtualMachine().apply {
        //int exit()
        func["exit"] = {
            that.handler_delay.removeCallbacksAndMessages(callback_token)
            that.handler_delay.post(parent::Exit)
            passage = listOf()
            0
        }
        //int jmp(int jump_offset)
        func["jmp"] = {
            todo_jump = i_lines + it[0] as Int
            todo_jump
        }
        //int jmpto(int jump_to_line)
        func["jmpto"] = {
            val todo_jump_original = todo_jump
            todo_jump = it[0] as Int
            todo_jump_original
        }
        //int lb(string label_name)
        func["lb"] = {
            label_positions[passage_name]?.get(it[0] as String) ?: -1
        }
        //int lb(string passage_name, string label_name)
        func["lbp"] = {
            label_positions[it[0] as String]?.get(it[1] as String) ?: -1
        }
        //int lb(int upper_limit)
        func["random"] = {
            val max = it[0] as Int
            if (max <= 0) -1
            else parent.random.nextInt(max)
        }
        //int time()
        func["time"] = {
            (System.currentTimeMillis() - time_start).toInt()
        }
        //int timeunix(int right_shift_bits)
        func["timeunix"] = {
            (System.currentTimeMillis() ushr Int.SIZE_BITS * it[0] as Int).toInt()
        }
        //int retpos()
        func["retpos"] = {
            i_lines_return
        }
        //int wait(int wait_time)
        func["wait"] = {
            val todo_wait_original = todo_wait
            todo_wait += it[0] as Int
            todo_wait_original
        }
        //int metronome(int interval)
        func["metronome"] = {
            todo_metronome = it[0] as Int
            frequency_metronome
        }
        //string media(int type, string topic)
        func["media"] = {
            val topic_new = it[1] as String
            when(it[0] as? Int) {
                1 -> {
                    todo_media = Runnable{ parent.topic_images = topic_new }
                    parent.topic_images
                }
                2 -> {
                    todo_media = Runnable{ parent.topic_audios = topic_new }
                    parent.topic_audios
                }
                else -> ""
            }
        }
        //void ask(int *answer_storage, ...)
        //... := string item1, int value1, string item2, int value2, ...
        func["ask"] = {
            val ret = it[0].toString()
            val answers = it.drop(1)
            mem.remove(ret)
            todo_ask = Runnable {
                that.l_main.llQuestion.removeAllViews()
                for (i in 0 .. answers.size - 2 step 2) {
                    val s = answers[i] as String
                    val n = answers[i + 1] as Int
                    that.l_main.llQuestion.addView(LabeledRoundButton(that).apply {
                        val color_id = MainActivity.button_colors[(MainActivity.button_colors.size / 2 + i) % MainActivity.button_colors.size]
                        color = if (android.os.Build.VERSION.SDK_INT >= 23)
                            that.resources.getColor(color_id, null)
                        else @Suppress("DEPRECATION") that.resources.getColor(color_id)
                        text = s
                        textSize = fontsize
                        setCircleOnClickListener {
                            that.l_main.llQuestion.visibility = View.INVISIBLE
                            mem[ret] = n
                        }
                    })
                }
                if (answers.isNotEmpty()) that.l_main.llQuestion.visibility = View.VISIBLE
            }
        }
        //void react(...)
        //... := string react_passage1, string react_passage2, ...
        func["react"] = {
            val actions = it.filter { s -> s in lines_passages }
            todo_react = Runnable {
                var i0 = 0
                that.l_main.llResponse.removeAllViews()
                for (s in actions) {
                    that.l_main.llResponse.addView(LabeledRoundButton(that).apply {
                        color = if (android.os.Build.VERSION.SDK_INT >= 23)
                            that.resources.getColor(MainActivity.button_colors[i0], null)
                        else @Suppress("DEPRECATION") that.resources.getColor(MainActivity.button_colors[i0])
                        text = s as String
                        textSize = fontsize
                        setCircleOnClickListener(::React)
                    })
                    i0 = (i0 + 1) % MainActivity.button_colors.size
                }
                that.OnClickLower(that.l_main.llResponse)
            }
        }
        //int load(string module_name)
        func["load"] = {
            try {
                that.openFileInput(it[0] as String).apply {
                    @Suppress("UNCHECKED_CAST")
                    val mem_loaded = java.io.ObjectInputStream(this).readObject() as MutableMap<String, Int>
                    mem.clear()
                    mem += mem_loaded
                    close()
                }; 0
            } catch (e: java.io.FileNotFoundException) {
                1
            } catch (e: TypeCastException) {
                that.Alert(that.getString(R.string.merr_badmemory)); 2
            }
        }
        //int save(string module_name)
        func["save"] = {
            try {
                that.openFileOutput(it[0] as String, android.content.Context.MODE_PRIVATE)
                    .apply {
                        java.io.ObjectOutputStream(this).writeObject(mem)
                        close()
                    }; 0
            } catch (e: java.io.FileNotFoundException) {
                1
            }
        }
    }

    //Script thread
    private fun RunScript(s:String):Any {
        try {
            val ret = cm.Eval(s)
            return (ret as? Pair<*,*>)?.first ?: ret
        }
        catch (e:CScriptVirtualMachine.BadScriptException) {
            val line = i_lines + 1
            val ns = topic
            that.handler_delay.post {
                that.Alert(that.getString(R.string.merr_badscript,
                    when (e) {
                        is CScriptVirtualMachine.InvalidSyntax -> that.getString(R.string.merr_badscript_syntax)
                        is CScriptVirtualMachine.UnbalancedBracket -> that.getString(R.string.merr_badscript_bracket)
                        is CScriptVirtualMachine.UndefinedName -> that.getString(R.string.merr_badscript_name)
                        is CScriptVirtualMachine.AssignToRvalue -> that.getString(R.string.merr_badscript_rvalue)
                        is CScriptVirtualMachine.TypeError -> that.getString(R.string.merr_badscript_type, e.s_value, e.s_type)
                    },
                    if (e.message?.isNotBlank() == true) ": ${e.message}" else ".",
                    line,
                    if (ns == "") "script.txt" else "script_${ns}.txt"))
            }
        }
        catch (e:Exception) {
            val line = i_lines + 1
            val ns = topic
            that.handler_delay.post {
                that.Alert(that.getString(R.string.merr_badscript, e.toString(), "", line, if (ns == "")
                    "script.txt"
                else "script_${ns}.txt"))
            }
        }
        return ""
    }

    private fun GetLevel(s:String):Int {
        val level = s.indexOfFirst { c:Char -> !c.isWhitespace() }
        return when {
            level == -1 -> s.length shl 1
            s[level] == ':' -> level shl 1
            else -> level shl 1 or 1
        }
    }

    private fun NextLine() {
        var jump_to_top = true
        if (i_lines < passage.size - 1) {
            val level_this = GetLevel(passage[i_lines]) and -2
            val level_next = GetLevel(passage[i_lines + 1])
            if (level_next and 1 != 0) {
                jump_to_top = false
                i_lines += 1
            }
            else if (level_next > level_this) {
                jump_to_top = false
                mutableListOf(i_lines + 1).also {
                    for (i in i_lines + 2 until passage.size) {
                        val level_new = GetLevel(passage[i])
                        if (level_next == level_new) it.add(i)
                        else if (level_next > level_new) break
                    }
                    i_lines = it[mr.Next(Triple(topic, passage_name, i_lines), it.size)]
                }
            }
            else if (level_next == level_this) {
                var level_base = level_this
                for (i in i_lines + 2 until passage.size) {
                    val level_base_new = GetLevel(passage[i])
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
                var level = GetLevel(passage.first())
                for(i in 1 until passage.size) {
                    val level_this = GetLevel(passage[i])
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

    private fun CalculateDelay(s:String):Long = when {
        s.isBlank() -> 0L
        s.count { !it.isWhitespace() } == 2 && s.first().isSurrogate() -> 2000L
        else -> 1000L +
            text_speed * (s.count { c:Char->c.isLetterOrDigit() }) /*+
            text_speed * (s.count { c:Char->c in '\u4E00' .. '\u9FFF' })*/
    }

    private fun ParseLine(last_delay:Long) {
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
            val s_ret = RunScript(it.groups[2]!!.value)
            if (it.groups[1] != null) "" else s_ret.toString()
        }
        val wait_offset = todo_wait
        when {
            "=>" in s -> {
                val s_display = CEscape(s.substringBeforeLast("=>").trimEnd())
                val delay = CalculateDelay(s_display)
                val target_goto = s.substringAfterLast("=>").trim { it.isWhitespace() || it == '_' }
                if (target_goto != topic) {
                    val f = File(parent.dir_media, "script${ if (target_goto == "") "" else "_$target_goto" }.txt")
                    if (f.canRead()) {
                        topic = target_goto
                        LoadScript(f)
                        todo_jump = 0
                    }
                }
                that.handler_delay.postAtTime({
                    DisplayLine(s_display, delay)
                    executor.execute { PrepareNextLine(delay + wait_offset) }
                }, callback_token, uptimeMillis() + last_delay)
            }
            "<-" in s && passage !== lines_main -> {
                val s_display = CEscape(s.substringBeforeLast("<-").trimEnd())
                val delay = CalculateDelay(s_display)
				passage = lines_main
                passage_name = ""
                s.substringAfterLast("<-").trimEnd().toIntOrNull().also {
                    if (it != null) todo_jump = i_lines_return + it
                    else i_lines = i_lines_return
                }
                that.handler_delay.postAtTime({
                    DisplayLine(s_display, delay)
                    that.handler_delay.postAtTime({ that.l_main.llResponse.visibility = View.VISIBLE }, callback_token, uptimeMillis() + delay + wait_offset)
                    executor.execute { PrepareNextLine(delay + wait_offset) }
                }, callback_token, uptimeMillis() + last_delay)
            }
            "->" in s && passage === lines_main -> {
                val s_display = CEscape(s.substringBeforeLast("->").trimEnd())
                val delay = CalculateDelay(s_display)
				val pos_block_new = lines_passages[s.substringAfterLast("->", "")]
				if (pos_block_new != null) {
					i_lines_return = i_lines
					passage = pos_block_new
                    passage_name = s.substringAfterLast("->")
                    todo_jump = 0
                    that.handler_delay.postAtTime({
                        that.l_main.llResponse.visibility = View.INVISIBLE
                        DisplayLine(s_display, delay)
                        executor.execute { PrepareNextLine(delay + wait_offset) }
                    }, callback_token, uptimeMillis() + last_delay)
				}
                else that.handler_delay.postAtTime({
                    DisplayLine(s_display, delay)
                    executor.execute { PrepareNextLine(delay + wait_offset) }
                }, callback_token, uptimeMillis() + last_delay)
            }
            else -> {
                val delay = CalculateDelay(s)
                that.handler_delay.postAtTime({
                    DisplayLine(CEscape(s), delay)
                    executor.execute { PrepareNextLine(delay + wait_offset) }
                }, callback_token, uptimeMillis() + last_delay)
            }
        }
    }

    private fun PrepareNextLine(delay:Long) {
        if (passage.isNotEmpty()) {
            val todonow_metronome = todo_metronome
            if (todonow_metronome >= 0) {
                frequency_metronome = todonow_metronome
                that.handler_delay.post{ parent.SetMetronome(todonow_metronome) }
            }

            val todonow_ask = todo_ask
            if (todonow_ask != null) that.handler_delay.post(todonow_ask)

            val todonow_react = todo_react
            if (todonow_react != null) that.handler_delay.post(todonow_react)

            val todonow_media = todo_media
            if (todonow_media != null) that.handler_delay.post(todonow_media)

            if (todo_jump in passage.indices)
                i_lines = todo_jump
            else NextLine()
            ParseLine(delay)
        }
    }

    private fun LoadScript(f:File) {
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

    //Main thread
    private fun DisplayLine(s:String, delay:Long) { //, -1, 0.15f
        if (delay == 0L) return
        val tv = outlineTextView(that).also {
            it.outline_color = -1
            it.outline_width = 0.15f
            it.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            it.text = s
            it.DoEmoji(that)
            val color_id = MainActivity.text_colors[parent.random.nextInt(MainActivity.text_colors.size)]
            it.setTextColor(if (android.os.Build.VERSION.SDK_INT >= 23)
                that.resources.getColor(color_id, null)
            else @Suppress("DEPRECATION") that.resources.getColor(color_id))
            it.textSize = fontsize//TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, fontsize, that.resources.displayMetrics)
            it.typeface = android.graphics.Typeface.DEFAULT_BOLD
            it.maxWidth = that.l_main.relaMain.width * 3 / 10
            it.setTextIsSelectable(false)
            it.visibility = View.INVISIBLE
        }
        that.l_main.relaMain.addView(tv)
        that.handler_delay.postDelayed({
            //tv.width and tv.height will be zero if not delayed
            tv.x = fontsize * 3//parent.random.nextFloat() * (that.rela_main.width - tv.width)
            tv.y = fontsize * 3//parent.random.nextFloat() * (that.rela_main.height - tv.height)
            tv.visibility = View.VISIBLE
            AlphaAnimation(0f, 1f).also {
                it.duration = if(delay > 2000L) 200L else delay / 10
                tv.startAnimation(it)
            }
        }, 100L)
        that.handler_delay.postDelayed({
            AlphaAnimation(1f, 0f).also {
                it.duration = if(delay > 8000L) 2000L else delay / 4
                tv.startAnimation(it)
            }
        }, delay - if(delay > 8000L) 1600L else delay / 5)
        that.handler_delay.postDelayed({
            that.l_main.relaMain.removeView(tv)
        }, delay + if(delay > 8000L) 400L else delay / 20)
    }

    private fun React(v:View) {
        that.l_main.llResponse.visibility = View.INVISIBLE
        that.handler_delay.removeCallbacksAndMessages(callback_token)
        that.l_main.relaMain.removeAllViews()
        executor.execute {
            i_lines_return = i_lines
            passage = lines_passages[(v.parent as LabeledRoundButton).text]!!
            passage_name = (v.parent as LabeledRoundButton).text.toString()
            todo_jump = 0
            PrepareNextLine(0L)
        }
    }
}