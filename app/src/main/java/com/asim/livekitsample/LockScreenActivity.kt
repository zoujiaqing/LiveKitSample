package com.asim.livekitsample

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView

class LockScreenActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置为全屏、覆盖锁屏属性
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // 设置布局
        setContentView(TextView(this).apply {
            text = "共享结束后自动结束锁屏"
            textSize = 24f
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setBackgroundColor(0xFF000000.toInt()) // 黑色背景
            setTextColor(0xFFFFFFFF.toInt()) // 白色文字
        })
    }
}