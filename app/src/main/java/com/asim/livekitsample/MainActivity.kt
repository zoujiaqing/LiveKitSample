package com.asim.livekitsample

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
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

    private fun startScreenCapture(intentData: Intent) {
        val localParticipant = room.localParticipant
        GlobalScope.launch {
            screencastTrack = localParticipant.createScreencastTrack(name = "ScreenShare", mediaProjectionPermissionResultData = intentData)

            // TODO check device specs before sharing
            //  VideoPreset169.H1080 is good for -> 4k screen with 16x9 aspect ratio
            localParticipant.publishVideoTrack(
                track = screencastTrack!!,
                options = VideoTrackPublishOptions(
                    simulcast = false,      // "simulcast = false" is intentionally added to show good quality only
                    videoEncoding = VideoPreset169.H720.encoding
                )
            )
            screencastTrack!!.options = LocalVideoTrackOptions(captureParams = VideoPreset169.H1080.capture)

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