package com.equinus.rubbishtalk

class MemorizedRandom(private val random: kotlin.random.Random) {
    private val mem = mutableMapOf<Any, MutableList<Int>>()
    fun Next(token:Any, size:Int):Int {
        if (size <= 1) return 0
        val history = mem.getOrPut(token) { MutableList(size) { 0 } }
        var r = random.nextInt((size * size - history.sum()))
        for (i in 0 .. size - 2) {
            if (history[i] > 0) {
                r -= (size - history[i])
                history[i] -= 1
            }
            else r -= size

            if (r < 0) {
                for (j in i + 1 until size) {
                    if (history[j] > 0) history[j] -= 1
                }
                history[i] = size
                return i
            }
        }
        history[size - 1] = size
        return size - 1
    }
    fun Reset() { mem.clear() }
}