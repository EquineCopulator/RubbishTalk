package com.equinus.rubbishtalk

class ActivityHelp:android.app.Activity() {
    override fun onCreate(savedInstanceState:android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.equinus.rubbishtalk.databinding.HelpBinding
            .inflate(layoutInflater).root)
    }
}