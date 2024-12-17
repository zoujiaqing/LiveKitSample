package com.asim.livekitsample

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class LockScreenActivity : AppCompatActivity() {

    private lateinit var lockScreenView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 如果没有权限，要求用户授权
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            startActivityForResult(intent, 0)  // 请求权限
            return // 如果没有权限，停止执行后续操作
        }

        // 设置无标题
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        // 设置为全屏
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        // 创建 Lock Screen 视图
        lockScreenView = LayoutInflater.from(this).inflate(R.layout.activity_lock_screen, null)

        // 配置 WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY  // 对应 Android O 以上的权限类型
            } else {
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT  // 适用于 Android 6.0 - 7.1
            },
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE // 不允许用户交互（如果需要可以调整）
        )

        // 获取 WindowManager 服务
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.addView(lockScreenView, params)

        // 禁止屏幕最小化
        setLockScreen()

        // 防止屏幕休眠
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setLockScreen() {
        // 防止返回键和菜单键事件
//        onBackPressedDispatcher.addCallback(this) {
//            // 阻止返回键
//        }
    }

    // 使返回键、菜单键不可用，避免锁屏界面被退出
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
            return true // 拦截返回键和菜单键事件
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 销毁锁屏视图
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.removeView(lockScreenView)
    }
}