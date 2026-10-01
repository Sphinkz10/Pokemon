package com.rui.pvpgo.live

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID

private const val CHANNEL_ID = "pvpgo_capture"
private const val NOTIFICATION_ID = 4217
private const val ACTION_START = "com.rui.pvpgo.live.START_CAPTURE"
private const val ACTION_STOP = "com.rui.pvpgo.live.STOP_CAPTURE"
private const val EXTRA_RESULT_CODE = "resultCode"
private const val EXTRA_RESULT_DATA = "resultData"

enum class CaptureRuntimeStatus { IDLE, STARTING, CAPTURING, STOPPING, STOPPED, ERROR }

data class CaptureRuntimeState(
    val status: CaptureRuntimeStatus = CaptureRuntimeStatus.IDLE,
    val sessionId: String? = null,
    val startedAtEpochMs: Long? = null,
    val frameCount: Long = 0,
    val lastFrame: CapturedFrame? = null,
    val averageFps: Double? = null,
    val maxFrameGapMs: Long = 0,
    val rotationChanges: Int = 0,
    val frameQuality: FrameQualitySnapshot = FrameQualitySnapshot(),
    val batteryPercent: Int? = null,
    val thermalStatus: Int? = null,
    val error: String? = null
)

object MediaProjectionFrameBus {
    private val _state = MutableStateFlow(CaptureRuntimeState())
    val state: StateFlow<CaptureRuntimeState> = _state.asStateFlow()

    internal fun update(state: CaptureRuntimeState) { _state.value = state }
}

/**
 * Android capture foundation only. It observes frame cadence/geometry and discards every Image
 * immediately. No video or screenshot pixels are persisted in V1.36-dev.
 */
class MediaProjectionCaptureService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: LiveObservationRepository
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var sessionId: String? = null
    private var startedAtEpochMs: Long? = null
    private var frameCount: Long = 0
    private var previousFrameAtEpochMs: Long? = null
    private var totalFrameIntervalMs: Long = 0
    private var frameIntervalCount: Long = 0
    private var maxFrameGapMs: Long = 0
    private var lastRotationDegrees: Int? = null
    private var rotationChanges: Int = 0
    private val frameQualityGate = CaptureFrameQualityGate()
    private var sampledBatteryPercent: Int? = null
    private var sampledThermalStatus: Int? = null
    private var stopping = false
    private var projectionCallback: MediaProjection.Callback? = null

    override fun onCreate() {
        super.onCreate()
        repository = LiveObservationRepository.get(this)
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCapture("user-stop", "STOPPED")
                stopSelf()
            }
            ACTION_START -> {
                startForegroundCompat()
                if (mediaProjection == null) {
                    val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                    val resultData = resultDataFrom(intent)
                    if (resultCode != Activity.RESULT_OK || resultData == null) {
                        fail("MediaProjection consent result missing or rejected.")
                    } else {
                        startCapture(resultCode, resultData)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (mediaProjection != null || sessionId != null) stopCapture("service-destroyed", "STOPPED")
        scope.cancel()
        super.onDestroy()
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        stopping = false
        frameCount = 0
        previousFrameAtEpochMs = null
        totalFrameIntervalMs = 0
        frameIntervalCount = 0
        maxFrameGapMs = 0
        lastRotationDegrees = null
        rotationChanges = 0
        val initialDeviceTelemetry = deviceTelemetry()
        sampledBatteryPercent = initialDeviceTelemetry.first
        sampledThermalStatus = initialDeviceTelemetry.second
        val newSessionId = UUID.randomUUID().toString()
        frameQualityGate.reset(newSessionId)
        val started = System.currentTimeMillis()
        sessionId = newSessionId
        startedAtEpochMs = started
        MediaProjectionFrameBus.update(
            CaptureRuntimeState(
                status = CaptureRuntimeStatus.STARTING,
                sessionId = newSessionId,
                startedAtEpochMs = started
            )
        )
        runBlocking(Dispatchers.IO) { repository.startSession(newSessionId, started) }

        runCatching {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = manager.getMediaProjection(resultCode, resultData)
            val thread = HandlerThread("PvPGoCaptureFrames").also { it.start() }
            val frameHandler = Handler(thread.looper)
            handlerThread = thread
            handler = frameHandler

            val metrics = resources.displayMetrics
            val width = metrics.widthPixels.coerceAtLeast(1)
            val height = metrics.heightPixels.coerceAtLeast(1)
            val density = metrics.densityDpi.coerceAtLeast(1)
            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader = reader

            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    if (!stopping) {
                        runCatching {
                            runBlocking(Dispatchers.IO) {
                                repository.event(newSessionId, System.currentTimeMillis(), "PROJECTION_STOPPED", "Android ended MediaProjection")
                            }
                        }
                        stopCapture("projection-ended", "STOPPED")
                        stopSelf()
                    }
                }
            }
            projectionCallback = callback
            projection.registerCallback(callback, frameHandler)
            mediaProjection = projection

            reader.setOnImageAvailableListener({ source ->
                val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    frameCount += 1
                    val now = System.currentTimeMillis()
                    val rotation = currentRotationDegrees()
                    previousFrameAtEpochMs?.let { previousAt ->
                        val gap = (now - previousAt).coerceAtLeast(0L)
                        totalFrameIntervalMs += gap
                        frameIntervalCount += 1
                        if (gap > maxFrameGapMs) maxFrameGapMs = gap
                    }
                    previousFrameAtEpochMs = now
                    lastRotationDegrees?.let { previousRotation ->
                        if (previousRotation != rotation) rotationChanges += 1
                    }
                    lastRotationDegrees = rotation
                    val frame = CapturedFrame(
                        frameId = frameCount,
                        capturedAtEpochMs = now,
                        width = image.width,
                        height = image.height,
                        rotationDegrees = rotation,
                        sessionId = newSessionId
                    )
                    val frameQuality = frameQualityGate.onFrame(
                        FrameQualityInput(newSessionId, frame.frameId, frame.capturedAtEpochMs,
                            frame.width, frame.height, frame.rotationDegrees)
                    )
                    if (frameCount == 1L || frameCount % 60L == 0L) {
                        val sampled = deviceTelemetry()
                        sampledBatteryPercent = sampled.first
                        sampledThermalStatus = sampled.second
                    }
                    MediaProjectionFrameBus.update(
                        CaptureRuntimeState(
                            status = CaptureRuntimeStatus.CAPTURING,
                            sessionId = newSessionId,
                            startedAtEpochMs = started,
                            frameCount = frameCount,
                            lastFrame = frame,
                            averageFps = averageFps(),
                            maxFrameGapMs = maxFrameGapMs,
                            rotationChanges = rotationChanges,
                            frameQuality = frameQuality,
                            batteryPercent = sampledBatteryPercent,
                            thermalStatus = sampledThermalStatus
                        )
                    )
                    if (frameCount == 1L || frameCount % 60L == 0L) {
                        val detail = "fps=${formatFps(averageFps())} maxGapMs=$maxFrameGapMs rotations=$rotationChanges battery=${sampledBatteryPercent ?: -1} thermal=${sampledThermalStatus ?: -1}"
                        scope.launch {
                            repository.checkpoint(newSessionId, frameCount, frame)
                            repository.event(newSessionId, now, "DEVICE_TELEMETRY", detail)
                        }
                    }
                } finally {
                    image.close()
                }
            }, frameHandler)

            virtualDisplay = projection.createVirtualDisplay(
                "PvPGoCompanion",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                frameHandler
            )

            MediaProjectionFrameBus.update(
                CaptureRuntimeState(
                    status = CaptureRuntimeStatus.CAPTURING,
                    sessionId = newSessionId,
                    startedAtEpochMs = started,
                    frameCount = 0
                )
            )
        }.onFailure { throwable ->
            fail(throwable.message ?: throwable::class.java.simpleName)
        }
    }

    private fun stopCapture(reason: String, finalStatus: String) {
        if (stopping) return
        stopping = true
        val endedSession = sessionId
        val endedCount = frameCount
        val previous = MediaProjectionFrameBus.state.value
        MediaProjectionFrameBus.update(previous.copy(status = CaptureRuntimeStatus.STOPPING))

        imageReader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null

        val projection = mediaProjection
        val callback = projectionCallback
        if (projection != null && callback != null) runCatching { projection.unregisterCallback(callback) }
        projectionCallback = null
        mediaProjection = null
        runCatching { projection?.stop() }

        handlerThread?.quitSafely()
        handlerThread = null
        handler = null

        if (endedSession != null) runCatching {
            runBlocking(Dispatchers.IO) { repository.finish(endedSession, endedCount, finalStatus, reason) }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        MediaProjectionFrameBus.update(
            previous.copy(
                status = CaptureRuntimeStatus.STOPPED,
                sessionId = endedSession,
                startedAtEpochMs = startedAtEpochMs,
                frameCount = endedCount,
                error = null
            )
        )
        sessionId = null
        startedAtEpochMs = null
        frameCount = 0
    }

    private fun fail(message: String) {
        val failedSession = sessionId
        val count = frameCount
        if (failedSession != null) runCatching {
            runBlocking(Dispatchers.IO) { repository.finish(failedSession, count, "ERROR", message) }
        }
        releaseWithoutJournal()
        stopForeground(STOP_FOREGROUND_REMOVE)
        val previous = MediaProjectionFrameBus.state.value
        MediaProjectionFrameBus.update(
            previous.copy(
                status = CaptureRuntimeStatus.ERROR,
                sessionId = failedSession,
                startedAtEpochMs = startedAtEpochMs,
                frameCount = count,
                error = message
            )
        )
        stopSelf()
    }

    private fun releaseWithoutJournal() {
        stopping = true
        imageReader?.setOnImageAvailableListener(null, null)
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        val projection = mediaProjection
        val callback = projectionCallback
        if (projection != null && callback != null) runCatching { projection.unregisterCallback(callback) }
        projectionCallback = null
        mediaProjection = null
        runCatching { projection?.stop() }
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
        sessionId = null
        frameCount = 0
    }

    private fun averageFps(): Double? {
        if (frameIntervalCount <= 0 || totalFrameIntervalMs <= 0) return null
        return (frameIntervalCount * 1000.0) / totalFrameIntervalMs.toDouble()
    }

    private fun deviceTelemetry(): Pair<Int?, Int?> {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val rawBattery = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val battery = rawBattery.takeIf { it in 0..100 }
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
        } else null
        return battery to thermal
    }

    private fun formatFps(value: Double?): String = value?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "unknown"

    private fun currentRotationDegrees(): Int {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return when (displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "PvP GO Companion", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Sessão ativa de observação do ecrã"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("PvP GO Companion ativo")
            .setContentText("A observar frames; nenhum vídeo é guardado.")
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @Suppress("DEPRECATION")
    private fun resultDataFrom(intent: Intent): Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
    } else {
        intent.getParcelableExtra(EXTRA_RESULT_DATA)
    }

    companion object {
        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, MediaProjectionCaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MediaProjectionCaptureService::class.java).apply { action = ACTION_STOP })
        }
    }
}
