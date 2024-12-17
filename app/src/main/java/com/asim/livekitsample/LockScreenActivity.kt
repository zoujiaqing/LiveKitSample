package com.asim.livekitsample

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity

class LockScreenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置无标题
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_lock_screen)

        // 设置全屏，隐藏状态栏和导航栏
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        // 使界面无法最小化，保持全屏覆盖
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        }

        // 禁止屏幕最小化或关闭
        setLockScreen()

        // 防止屏幕休眠
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setLockScreen() {
        // 防止返回键和菜单键事件
        onBackPressedDispatcher.addCallback(this) {
            // 阻止返回键
        }
    }

    // 使返回键、菜单键不可用，避免锁屏界面被退出
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
            return true // 拦截返回键和菜单键事件
        }
        return super.onKeyDown(keyCode, event)
    }
}