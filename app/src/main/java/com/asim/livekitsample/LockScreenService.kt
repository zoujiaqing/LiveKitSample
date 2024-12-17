package com.asim.livekitsample

import android.app.ActivityOptions
import android.app.Service
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi

class LockScreenService : Service() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 启动锁屏界面
        val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val displays = displayManager.displays
        for (display in displays) {
            Log.d("Display", "Display: ${display.displayId} - ${display.name}")
        }

        // 确保启动在主显示器上
        val lockScreenIntent = Intent(this, LockScreenActivity::class.java)

        // 添加 FLAG_ACTIVITY_NEW_TASK 以在 Service 中启动 Activity
        lockScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(0)
        lockScreenIntent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
        Log.d("Display", "startActivity for LockScreenActivity")
        startActivity(lockScreenIntent, options.toBundle())

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}