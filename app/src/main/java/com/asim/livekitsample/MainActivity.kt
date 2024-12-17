package com.asim.livekitsample

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.participant.VideoTrackPublishOptions
import io.livekit.android.room.track.LocalScreencastVideoTrack
import io.livekit.android.room.track.LocalVideoTrackOptions
import io.livekit.android.room.track.VideoCaptureParameter
import io.livekit.android.room.track.VideoEncoding
import io.livekit.android.room.track.VideoPreset169
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : AppCompatActivity() {

    // TODO add URL and Token for LiveKit
    private var liveKitUrl = "ws://139.224.9.21:7880"
    private var liveKitToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE3MzQ5ODY1MzMsImlzcyI6ImFwcGtleSIsIm5hbWUiOiJkZXZpY2VfMTIzNDUiLCJuYmYiOjE3MzQxMjI1MzMsInN1YiI6ImRldmljZV8xMjM0NSIsInZpZGVvIjp7InJvb20iOiJ0ZXN0X3Jvb20iLCJyb29tSm9pbiI6dHJ1ZX19.0l5qpFV_QIj9PhFuKw6oH1wVzuGEece33dDzOHxYblw"

    lateinit var room: Room
    private var screencastTrack: LocalScreencastVideoTrack? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

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
            GlobalScope.launch {
                // events collection
                launch {
                    room.events.collect { event ->
                        if (event is RoomEvent.TrackPublished) {
                            /*withContext(Dispatchers.Main) {
                                initLayout()
                            }*/
                        }
                    }
                }
                // Connect to server.
                room.connect(url, token)

                room.localParticipant.setScreenShareEnabled(true, intentData)
            }
            startScreenCapture(intentData)
        }
    }

    // 获取屏幕分辨率（竖屏模式下宽度较小，高度较大）
    fun getScreenResolution(context: Context): Pair<Int, Int> {
        val metrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels

        Log.d("MAIN", "Screen Resolution: " + width + "x" + height)
        return Pair(width, height)
    }

    enum class VideoResolution(val value: Int) {
        H240(240),
        H480(480),
        H720(720),
        H1080(1080);
    }

    fun getCaptureParams(context: Context, videoResolution: VideoResolution, frameRate: Int): VideoCaptureParameter {
        // 获取屏幕分辨率
        val (screenWidth, screenHeight) = getScreenResolution(context)

        // 根据 videoResolution 设置宽度
        val captureWidth: Int
        val captureHeight: Int

        // 根据 videoResolution 计算捕获的宽度和高度
        when (videoResolution) {
            VideoResolution.H240 -> {
                captureWidth = 240
            }
            VideoResolution.H480 -> {
                captureWidth = 480
            }
            VideoResolution.H720 -> {
                captureWidth = 720
            }
            VideoResolution.H1080 -> {
                captureWidth = 1080
            }
        }

        // 计算高度，保持宽高比
        captureHeight = (captureWidth * screenHeight) / screenWidth

        // 确保高度不超过设备屏幕的高度
        val finalWidth = minOf(captureWidth, screenWidth)
        val finalHeight = minOf(captureHeight, screenHeight)

        // 设置帧率
        val finalFrameRate = frameRate

        // 返回自定义的 VideoCaptureParameter
        return VideoCaptureParameter(finalWidth, finalHeight, finalFrameRate)
    }

    fun createCustomVideoEncoding(context: Context, videoResolution: VideoResolution, frameRate: Int): VideoEncoding {
        // 获取屏幕分辨率
        val (screenWidth, screenHeight) = getScreenResolution(context)

        // 设置默认的视频捕获和编码参数
        var captureWidth = screenWidth
        var captureHeight = screenHeight
        var bitrate = 1_700_000  // 默认比特率

        // 根据传入的 videoResolution 设置捕获分辨率和比特率
        when (videoResolution) {
            VideoResolution.H240 -> {
                captureWidth = 240
                captureHeight = (captureWidth * screenHeight) / screenWidth
                bitrate = 500_000  // 比较低的比特率
            }
            VideoResolution.H480 -> {
                captureWidth = 480
                captureHeight = (captureWidth * screenHeight) / screenWidth
                bitrate = 1_000_000  // 较低的比特率
            }
            VideoResolution.H720 -> {
                captureWidth = 720
                captureHeight = (captureWidth * screenHeight) / screenWidth
                bitrate = 1_700_000  // 较高的比特率
            }
            VideoResolution.H1080 -> {
                captureWidth = 1080
                captureHeight = (captureWidth * screenHeight) / screenWidth
                bitrate = 3_000_000  // 较高的比特率
            }
        }

        // 创建自定义的 VideoCaptureParameter
        val videoCaptureParameter = VideoCaptureParameter(captureWidth, captureHeight, frameRate)

        // 创建视频编码参数，根据分辨率和比特率来调整
        val videoEncoding = VideoEncoding(bitrate, frameRate)

        // 返回自定义的视频编码
        return videoEncoding
    }

    private fun startScreenCapture(intentData: Intent) {
        val localParticipant = room.localParticipant
        GlobalScope.launch {
            screencastTrack = localParticipant.createScreencastTrack(name = "ScreenShare", mediaProjectionPermissionResultData = intentData)

            // 根据分辨率获取图像
            val captureParams = getCaptureParams(applicationContext, VideoResolution.H480, 6)

            // TODO check device specs before sharing
            //  VideoPreset169.H1080 is good for -> 4k screen with 16x9 aspect ratio
            localParticipant.publishVideoTrack(
                track = screencastTrack!!,
                options = VideoTrackPublishOptions(
                    simulcast = false,  // 设置为 false 使用高质量的视频流
                    videoEncoding = createCustomVideoEncoding(applicationContext, VideoResolution.H480, 6)
                )
            )

            screencastTrack!!.options = LocalVideoTrackOptions(captureParams = captureParams)

            // Must start the foreground prior to startCapture.
            screencastTrack!!.startForegroundService(null, null)
            screencastTrack!!.startCapture()

            val serviceIntent = Intent(applicationContext, LockScreenService::class.java)
            startService(serviceIntent)
        }
    }

    private fun stopLiveKitUtil() {
        stopScreenCapture()
        clearResources()
    }

    private fun stopScreenCapture() {
        GlobalScope.launch {
            screencastTrack?.let { localScreencastVideoTrack ->
                executeWithSafety {
                    localScreencastVideoTrack.stop()
                    room.localParticipant.unpublishTrack(localScreencastVideoTrack)
                }
            }

            // 关闭锁屏界面
            val lockScreenIntent = Intent(this@MainActivity, LockScreenActivity::class.java)
            stopService(lockScreenIntent)
        }
    }

    private fun clearResources() {
        // Make sure to release any resources associated with LiveKit
        executeWithSafety { room.disconnect() }
        executeWithSafety { room.release() }
        // Clean up foreground service
        stopForegroundService()

        screencastTrack = null
    }

    private fun executeWithSafety(function: () -> Unit) {
        try {
            function()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        stopLiveKitUtil()
        super.onDestroy()
    }
}