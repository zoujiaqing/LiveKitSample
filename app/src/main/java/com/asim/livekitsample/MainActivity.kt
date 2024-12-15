package com.asim.livekitsample

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.participant.VideoTrackPublishOptions
import io.livekit.android.room.track.LocalScreencastVideoTrack
import io.livekit.android.room.track.VideoPreset169
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : AppCompatActivity() {

    private var liveKitUrl = "ws://139.224.9.21:7880"
    private var liveKitToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3MzQ5ODY1MzMsImlzcyI6ImFwcGtleSIsIm5hbWUiOiJkZXZpY2VfMTIzNDUiLCJuYmYiOjE3MzQxMjI1MzMsInN1YiI6ImRldmljZV8xMjM0NSIsInZpZGVvIjp7InJvb20iOiJ0ZXN0X3Jvb20iLCJyb29tSm9pbiI6dHJ1ZX19.0l5qpFV_QIj9PhFuKw6oH1wVzuGEece33dDzOHxYblw"

    lateinit var room: Room
    private var screencastTrack: LocalScreencastVideoTrack? = null
    private var overlayMask: OverlayMask? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        if (!checkOverlayPermission(this)) {
            requestOverlayPermission(this)
        }

        overlayMask = OverlayMask(this)

        findViewById<Button>(R.id.stop_btn).setOnClickListener {
            stopLiveKitUtil()
            finishAffinity()
        }

        // Create Room object.
        room = LiveKit.create(applicationContext, options = RoomOptions(adaptiveStream = false))        // "adaptiveStream = false" is intentionally added to show good quality only

        // Setup the video renderer
        room.initVideoRenderer(findViewById<SurfaceViewRenderer>(R.id.renderer))

        requestCapturePermission()
    }

    fun checkOverlayPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || android.provider.Settings.canDrawOverlays(context)
    }

    fun requestOverlayPermission(activity: Activity) {
        if (!checkOverlayPermission(activity)) {
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${activity.packageName}")
            )
            activity.startActivityForResult(intent, 1)
        }
    }

    private fun requestCapturePermission() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenCaptureIntentLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private val screenCaptureIntentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val resultCode = result.resultCode
        val data = result.data
        if (resultCode != Activity.RESULT_OK || data == null) {
            return@registerForActivityResult
        }

        startForegroundService()
        startLiveKit(data)
    }

    private fun startForegroundService() {
        val foregroundServiceIntent = Intent(application, ForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            application.startForegroundService(foregroundServiceIntent)
        } else {
            application.startService(foregroundServiceIntent)
        }
    }

    private fun stopForegroundService() {
        val foregroundServiceIntent = Intent(application, ForegroundService::class.java)
        application.stopService(foregroundServiceIntent)
    }

    private fun startLiveKit(intentData: Intent) {
        val url = liveKitUrl
        val token = liveKitToken

        if (url.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, "Missing url and token", Toast.LENGTH_SHORT).show()
            stopForegroundService()
        } else {
            lifecycleScope.launch {
                try {
                    // 显示遮罩
                    overlayMask?.showOverlay()

                    // 连接到 LiveKit 房间
                    room.connect(url, token)
                    Log.d("MAIN", "Room connected.")

                    // 创建虚拟显示器并开始屏幕共享
                    createVirtualDisplayAndStartLiveKit(intentData)
                } catch (e: Exception) {
                    Log.e("MAIN", "Failed to connect or start screencast", e)
                    overlayMask?.hideOverlay() // 如果连接失败，移除遮罩
                }
            }
        }
    }

    private fun stopLiveKitUtil() {
        try {
            // 隐藏遮罩
            overlayMask?.hideOverlay()

            stopScreenCapture()
            clearResources()
        } catch (e: Exception) {
            Log.e("MAIN", "Failed to stop LiveKit utilities", e)
        }
    }

    // 创建虚拟显示器并开始屏幕共享
    private fun createVirtualDisplayAndStartLiveKit(mediaProjectionIntentData: Intent) {
        GlobalScope.launch {
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mediaProjection = mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, mediaProjectionIntentData)

            if (mediaProjection == null) {
                Toast.makeText(this@MainActivity, "Failed to get media projection", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // 配置虚拟显示器
            val virtualDisplay = mediaProjection.createVirtualDisplay(
                "LiveKitVirtualDisplay",
                resources.displayMetrics.widthPixels,
                resources.displayMetrics.heightPixels,
                resources.displayMetrics.densityDpi,
                android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                null, // 这里设置为 null 表示虚拟显示器不绑定到当前屏幕上
                null,
                null
            )

            if (virtualDisplay == null) {
                Toast.makeText(this@MainActivity, "Virtual display creation failed", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // 使用正确的 mediaProjectionIntentData 创建 LocalScreencastVideoTrack
            screencastTrack = room.localParticipant.createScreencastTrack(
                name = "ScreenShare",
                mediaProjectionPermissionResultData = mediaProjectionIntentData
            )

            room.localParticipant.publishVideoTrack(
                track = screencastTrack!!,
                options = VideoTrackPublishOptions(
                    simulcast = false,
                    videoEncoding = VideoPreset169.H1080.encoding
                )
            )

            screencastTrack!!.startForegroundService(null, null)
            screencastTrack!!.startCapture()
        }
    }

    private fun stopScreenCapture() {
        GlobalScope.launch {
            screencastTrack?.let {
                try {
                    it.stop()
                    room.localParticipant.unpublishTrack(it)
                } catch (e: Exception) {
                    Log.e("MAIN", "Failed to stop screencast track", e)
                }
            }
            screencastTrack = null
        }
    }

    private fun clearResources() {
        try {
            room.disconnect()
            room.release()
            stopForegroundService()
            screencastTrack = null
        } catch (e: Exception) {
            Log.e("MAIN", "Failed to clear resources", e)
        }
    }

    private fun attachVideo(videoTrack: VideoTrack) {
        videoTrack.addRenderer(findViewById<SurfaceViewRenderer>(R.id.renderer))
        findViewById<View>(R.id.progress).visibility = View.GONE
    }

    override fun onDestroy() {
        stopLiveKitUtil()
        overlayMask?.hideOverlay()
        super.onDestroy()
    }

    class OverlayMask(private val context: Context) {

        private var windowManager: WindowManager? = null
        private var overlayView: View? = null
        fun showOverlay() {
            Log.d("MAIN", "overlayView????????")
            try {
                if (overlayView != null) return
                overlayView = View(context).apply { setBackgroundColor(0x80000000.toInt()) }
                val layoutParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        WindowManager.LayoutParams.TYPE_PHONE
                    },
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    android.graphics.PixelFormat.TRANSLUCENT
                )
                windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager?.addView(overlayView, layoutParams)
            } catch (e: Exception) {
                Log.e("OverlayMask", "Failed to show overlay", e)
            }
        }

        fun hideOverlay() {
            Log.d("MAIN", "hideOverlay????????")
            try {
                overlayView?.let {
                    windowManager?.removeView(it)
                    overlayView = null
                }
            } catch (e: Exception) {
                Log.e("OverlayMask", "Failed to hide overlay", e)
            }
        }
    }
}