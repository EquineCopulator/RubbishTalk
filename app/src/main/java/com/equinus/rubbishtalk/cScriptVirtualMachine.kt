package com.equinus.rubbishtalk

fun cEscape(s:String):String {
    return Regex("""\\(?:([abefnrtv\\'"?])|([0-7]{1,3})|x([A-Fa-f0-9]+)|u([A-Fa-f0-9]{4})|U([A-Fa-f0-9]{8})|)""").replace(s) {
        when {
            it.groups[1] != null -> when(it.groups[1]!!.value) {
				"a" -> "\u0007"
				"b" -> "\u0008"
				"e" -> "\u001B"
				"f" -> "\u000C"
				"n" -> "\u000A"
				"r" -> "\u000D"
				"t" -> "\u0009"
				"v" -> "\u000B"
				else -> it.groups[1]!!.value
			}
            it.groups[2] != null -> {
                it.groups[2]!!.value.toInt(8).toChar().toString()
            }
            it.groups[3] != null -> {
                it.groups[3]!!.value.toInt(16).toChar().toString()
            }
            it.groups[4] != null -> {
                it.groups[4]!!.value.toInt(16).toChar().toString()
            }
            it.groups[5] != null -> {
                val c = it.groups[5]!!.value.toInt(16)
                "%c".format(c)
            }
            else -> ""
        }
    }
}

class CScriptVirtualMachine {
    companion object {
        private val regex_id = Regex("""(?!\d)\w+""")
        private val regex_num8 = Regex("""0\d+""")
        private val regex_num10 = Regex("""(?!0)\d+""")
        private val regex_num16 = Regex("""0x[\da-fA-F]+""")
        private val regex_idlit = Regex("""${regex_id.pattern}|${regex_num8.pattern}|${regex_num10.pattern}|${regex_num16.pattern}|0""")
        private val regex_border = Regex("""\s*(\S+?)\s*(?:$|(?<=\+\+|--|[])"])|(?=\+\+|--|[!~*("])|(?<=\()(?=\))|(?<=\[)(?=])|(?<=<)(?=>)|(?<!\+)(?=\+)|(?<!-)(?=-)|(?<!<)(?=<)|(?<=>)(?![=>])|(?<=\w)(?=\W)|(?<=\W)(?=\w)|(?<=\S)(?=\s))""")

        private val precedence = mapOf(
            "(L" to 0, "\"L" to 0,
            "(" to 2, "[" to 2,
            "+L" to 3, "-L" to 3, "!L" to 3, "~L" to 3, "*L" to 3,
            "*" to 5, "/" to 5, "%" to 5,
            "+" to 6, "-" to 6,
            "<<" to 7, ">>" to 7,
            "<" to 9, "<=" to 9, ">" to 9, ">=" to 9,
            "==" to 10, "!=" to 10,
            "&" to 11, "^" to 12, "|" to 13,
            "&&" to 14, "||" to 15,
            "?" to 17, "=" to 17, "+=" to 17, "-=" to 17, "*=" to 17, "/=" to 17, "%=" to 17, "<<=" to 17, ">>=" to 17, "&=" to 17, "^=" to 17, "|=" to 17,
            "," to 18)
        private val bracket = mapOf("(L" to ")", "(" to ")", "?" to ":", "[" to "]")
        private val right = mapOf("++" to true, "--" to true, ")" to true, ">" to true, "]" to true)
    }

    sealed class BadScriptException(s:String):Exception(s)
    class InvalidSyntax(s:String):BadScriptException(s)
    class UnbalancedBracket(s:String):BadScriptException(s)
    class UndefinedName(s:String):BadScriptException(s)
    class AssignToRvalue(s:String):BadScriptException(s)
    class TypeError(val s_value:String, val s_type:String):BadScriptException("")

    val mem = mutableMapOf<String, Any>()
    val func = mutableMapOf<String, (List<Any>)->Any>("ifdef" to ::IfDef, "undef" to ::Undef, "reset" to ::Reset, "str" to ::Str)

    private inline fun<reified T> CValue(v:Any):T {
        val vv = v as? Pair<*, *>
        return if (vv != null)
            (vv.first ?: throw UndefinedName(vv.second as String)) as? T ?: throw TypeError(vv.second.toString(), T::class.toString())
        else v as? T ?: throw TypeError(v.toString(), T::class.toString())
    }
    private fun CAddress(v:Any):String {
        return (v as? Pair<*, *> ?: throw AssignToRvalue(v.toString())).second as String
    }

    fun Eval(s:String):Any {
        when {
            regex_id.matches(s) -> return Pair(mem[s], s)
            s == "0" -> return 0
            regex_num10.matches(s) -> return s.toInt(10)
            regex_num16.matches(s) -> return s.substring(2).toInt(16)
            regex_num8.matches(s) -> return s.toInt(8)
        }

        var pre = 0
        var operator:String? = null
        var left_op = true
        var r1 = IntRange.EMPTY
        var r2 = IntRange.EMPTY
        var r3 = IntRange.EMPTY
        var r_bracket = IntRange.EMPTY
        for (m in regex_border.findAll(s)) {
            if (m.range.last in r_bracket) continue
            var mv = m.groups[1]!!.value
            if (!regex_idlit.matches(mv)) {
                val mvf = mv
                if (left_op) mv += 'L'
                val pre_new = precedence[mv] ?: 0

                val mvb = bracket[mv]
                if (mvb != null) {
                    var i0 = m.range.last + 1
                    var level = 1
                    while (level > 0) {
                        val i1 = s.indexOf(mvb, i0)
                        if (i1 == -1) throw UnbalancedBracket(s)
                        val i2 = s.indexOf(mvf, i0)
                        if (i2 == -1 || i1 < i2) {
                            i0 = i1 + mvb.length
                            level -= 1
                        }
                        else {
                            i0 = i2 + mvf.length
                            level += 1
                        }
                    }
                    r_bracket = m.range.first until i0
                    left_op = false
                }
                else if (mvf == "\"") {
                    var i0 = m.range.last
                    do {
                        i0 = s.indexOf('"', i0 + 1)
                        if (i0 == -1) throw UnbalancedBracket(s)
                        var i1 = i0 - 1
                        while (i1 > 0 && s[i1] == '\\') i1 -= 1
                    } while ((i0 - i1) % 2 == 0)
                    r_bracket = m.range.first .. i0
                    left_op = false
                }
                else {
                    r_bracket = IntRange.EMPTY
                    left_op = mv !in right
                }

                if (pre_new > pre || pre_new == pre && (pre != 17 && pre != 3)) {
                    pre = pre_new
                    operator = mv
                    r1 = 0 until m.range.first
                    if (mvb != null) {
                        r2 = m.range.last + 1 .. r_bracket.last - mvb.length
                        r3 = r_bracket.last + 1 until s.length
                    }
                    else if (mvf == "\"") {
                        r2 = m.range.last + 1 until r_bracket.last
                        r3 = r_bracket.last + 1 until s.length
                    }
                    else {
                        r2 = m.range.last + 1 until s.length
                        r3 = IntRange.EMPTY
                    }
                }
            }
            else left_op = false
        }

        val a1 = s.substring(r1).trim()
        val a2 = s.substring(r2).trim()
        val a3 = s.substring(r3).trim()
        when (operator) {
            "(L" -> return Eval(a2)
            "\"L" -> return cEscape(a2)
            "(" -> return FuncCall(a1, a2)
            "[" -> {
                val address = (CValue<Int>(Eval(a1)) + CValue<Int>(Eval(a2))).toString()
                return Pair(mem[address], address)
            }
            "+L" -> return CValue<Int>(Eval(a2))
            "-L" -> return -CValue<Int>(Eval(a2))
            "!L" -> return if (CValue<Int>(Eval(a2)) == 0) 1 else 0
            "~L" -> return -1 - CValue<Int>(Eval(a2))
            "*L" -> {
                val address = CValue<Int>(Eval(a2)).toString()
                return Pair(mem[address], address)
            }
            "*" -> return CValue<Int>(Eval(a1)) * CValue<Int>(Eval(a2))
            "/" -> return CValue<Int>(Eval(a1)) / CValue<Int>(Eval(a2))
            "%" -> return CValue<Int>(Eval(a1)) % CValue<Int>(Eval(a2))
            "+" -> return CValue<Int>(Eval(a1)) + CValue<Int>(Eval(a2))
            "-" -> return CValue<Int>(Eval(a1)) - CValue<Int>(Eval(a2))
            "<<" -> return CValue<Int>(Eval(a1)) shl CValue<Int>(Eval(a2))
            ">>" -> return CValue<Int>(Eval(a1)) ushr CValue<Int>(Eval(a2))
            "<" -> return if (CValue<Int>(Eval(a1)) < CValue<Int>(Eval(a2))) 1 else 0
            "<=" -> return if (CValue<Int>(Eval(a1)) <= CValue<Int>(Eval(a2))) 1 else 0
            ">" -> return if (CValue<Int>(Eval(a1)) > CValue<Int>(Eval(a2))) 1 else 0
            ">=" -> return if (CValue<Int>(Eval(a1)) >= CValue<Int>(Eval(a2))) 1 else 0
            "==" -> return if (CValue<Int>(Eval(a1)) == CValue<Int>(Eval(a2))) 1 else 0
            "!=" -> return if (CValue<Int>(Eval(a1)) != CValue<Int>(Eval(a2))) 1 else 0
            "&" -> return CValue<Int>(Eval(a1)) and CValue<Int>(Eval(a2))
            "^" -> return CValue<Int>(Eval(a1)) xor CValue<Int>(Eval(a2))
            "|" -> return CValue<Int>(Eval(a1)) or CValue<Int>(Eval(a2))
            "&&" -> return if (CValue<Int>(Eval(a1)) != 0 && CValue<Int>(Eval(a2)) != 0) 1 else 0
            "||" -> return if (CValue<Int>(Eval(a1)) != 0 || CValue<Int>(Eval(a2)) != 0) 1 else 0
            "?" -> return if (CValue<Int>(Eval(a1)) != 0) Eval(a2) else Eval(a3)
            "=" -> {
                val var1 = Eval(a1)
                val var2 = CValue<Any>(Eval(a2))
                val address = CAddress(var1)
                mem[address] = var2
                return Pair(var2, address)
            }
            "+=" -> {
                val var1 = Eval(a1)
                val var2 = CValue<Int>(var1) + CValue<Int>(Eval(a2))
                val address = CAddress(var1)
                mem[address] = var2
                return Pair(var2, address)
            }
            "-=" -> {
                val var1 = Eval(a1)
                val var2 = CValue<Int>(var1) - CValue<Int>(Eval(a2))
                val address = CAddress(var1)
                mem[address] = var2
                return Pair(var2, address)
            }
            "*=" -> {
                val var1 = Eval(a1)
                val var2 = CValue<Int>(var1) * CValue<Int>(Eval(a2))
                val address = CAddress(var1)
                mem[address] = var2
                return Pair(var2, address)
            }
            "/=" -> {
                val var1 = Eval(a1)
                val var2 = CValue<Int>(var1) / CValue<Int>(Eval(a2))
                val address = CAddress(var1)
                mem[address] = var2
                return Pair(var2, address)
            }
            "%=" -> {
                val var1 = Eval(a1)
                val var2 = CValue<Int>(var1) % CValue<Int>(Eval(a2))
                val address = CAddress(var1)
                mem[address] = var2
                return Pair(var2, address)
            }
            "<<=" -> {
                val var1 = Eval(a1)
                val var2 = CValue<Int>(var1) shl CValue<Int>(Eval(a2))
                val address = CAddress(var1)
                mem[address] = var2
                return Pair(var2, address)
            }
            ">>=" -> {
                val var1 = Eval(a1)
                val var2 = CValue<Int>(var1) ushr CValue<Int>(Eval(a2))
                val address = CAddress(var1)
                mem[address] = var2
                return Pair(var2, address)
            }
            "&=" -> {
                val var1 = Eval(a1)
                val var2 = CValue<Int>(var1) and CValue<Int>(Eval(a2))
                val address = CAddress(var1)
                mem[address] = var2
                return Pair(var2, address)
            }
            "^=" -> {
                val var1 = Eval(a1)
                val var2 = CValue<Int>(var1) xor CValue<Int>(Eval(a2))
                val address = CAddress(var1)
                mem[address] = var2
                return Pair(var2, address)
            }
            "|=" -> {
                val var1 = Eval(a1)
                val var2 = CValue<Int>(var1) or CValue<Int>(Eval(a2))
                val address = CAddress(var1)
                mem[address] = var2
                return Pair(var2, address)
            }
            "," -> {
                Eval(a1)
                return Eval(a2)
            }
        }

        throw InvalidSyntax(s)
    }

    private fun FuncCall(func_name:String, func_arglist:String):Any {
        var i0 = 0
        var i1 = func_arglist.indexOf(',')
        val func_args = mutableListOf<Any>()
        if (func_arglist.isNotBlank()) {
            while (i1 != -1) {
                try {
                    func_args.add(CValue(Eval(func_arglist.substring(i0, i1))))
                    i0 = i1 + 1
                    i1 = func_arglist.indexOf(',', i0)
                }
                catch(e:UnbalancedBracket) {
                    i1 = func_arglist.indexOf(',', i1 + 1)
                }
            }
            func_args.add(CValue(Eval(func_arglist.substring(i0))))
        }
        return (func[(Eval(func_name) as? Pair<*,*> ?: throw TypeError(func_name, "function")).second as String] ?: throw UndefinedName(func_name))(func_args)
    }

    private fun IfDef(func_args:List<Any>):Any =
        if (func_args[0].toString() in mem) 1 else 0
    private fun Undef(func_args:List<Any>):Any =
        mem.remove(func_args[0].toString()) ?: throw UndefinedName(func_args[0].toString())
    private fun Reset(func_args:List<Any>):Any { mem.clear(); return 0 }
    private fun Str(func_args:List<Any>):Any =
        (func_args[0] as String).format(*func_args.drop(1).toTypedArray())
}