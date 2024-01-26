package com.asim.livekitsample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.SurfaceViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalScreencastVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class MainActivity : AppCompatActivity() {

    private var liveKitUrl = ""
    private var liveKitToken = ""
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
        room = LiveKit.create(applicationContext)

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
        val url = "wss://$liveKitUrl"
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
            localParticipant.publishVideoTrack(track = screencastTrack!!)

            // Must start the foreground prior to startCapture.
            screencastTrack!!.startForegroundService(null, null)
            screencastTrack!!.startCapture()
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

    private fun connectToRoom() {
        val url = "wss://your_host"
        val token = "your_token"

        lifecycleScope.launch {
            // Setup event handling.
            launch {
                room.events.collect { event ->
                    when (event) {
                        is RoomEvent.TrackSubscribed -> onTrackSubscribed(event)
                        else -> {}
                    }
                }
            }

            // Connect to server.
            room.connect(
                url,
                token,
            )

            // Turn on audio/video recording.
            val localParticipant = room.localParticipant
            localParticipant.setMicrophoneEnabled(true)
            localParticipant.setCameraEnabled(true)

            // Attach video of remote participant if already available.
            val remoteVideoTrack = room.remoteParticipants.values.firstOrNull()
                ?.getTrackPublication(Track.Source.CAMERA)
                ?.track as? VideoTrack

            if (remoteVideoTrack != null) {
                attachVideo(remoteVideoTrack)
            }
        }
    }

    private fun onTrackSubscribed(event: RoomEvent.TrackSubscribed) {
        val track = event.track
        if (track is VideoTrack) {
            attachVideo(track)
        }
    }

    private fun attachVideo(videoTrack: VideoTrack) {
        videoTrack.addRenderer(findViewById<SurfaceViewRenderer>(R.id.renderer))
        findViewById<View>(R.id.progress).visibility = View.GONE
    }

    private fun requestNeededPermissions(onHasPermissions: () -> Unit) {
        val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
                var hasDenied = false
                // Check if any permissions weren't granted.
                for (grant in grants.entries) {
                    if (!grant.value) {
                        Toast.makeText(this, "Missing permission: ${grant.key}", Toast.LENGTH_SHORT).show()

                        hasDenied = true
                    }
                }

                if (!hasDenied) {
                    onHasPermissions()
                }
            }

        // Assemble the needed permissions to request
        val neededPermissions = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            .filter { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_DENIED }
            .toTypedArray()

        if (neededPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(neededPermissions)
        } else {
            onHasPermissions()
        }
    }

    override fun onDestroy() {
        stopLiveKitUtil()
        super.onDestroy()
    }
}