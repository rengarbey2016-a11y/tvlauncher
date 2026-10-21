package com.example.tvlauncher

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "Test: uygulama çalışıyor"
        tv.textSize = 32f
        tv.setTextColor(Color.WHITE)
        tv.setBackgroundColor(Color.BLACK)
        setContentView(tv)
    }
}
