package com.example.healthsyncandroid.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.request.receive
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.stop
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class LocalServerService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var serverJob: Job? = null
    private var server: io.ktor.server.engine.ApplicationEngine? = null

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Health Sync Server")
            .setContentText("Local server is running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        startForeground(1, notification)

        acquireLocks()
        startServer()

        return START_STICKY
    }

    private fun acquireLocks() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HealthSync::CpuLock").apply {
            acquire()
        }

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "HealthSync::WifiLock").apply {
            acquire()
        }
    }

    private fun startServer() {
        if (server != null) return

        serverJob = serviceScope.launch {
            server = embeddedServer(CIO, port = 8080) {
                install(ContentNegotiation) {
                    json(Json { 
                        ignoreUnknownKeys = true 
                        prettyPrint = true 
                    })
                }
                routing {
                    get("/api/ping") {
                        call.respond(mapOf("status" to "active"))
                    }
                    post("/api/sync/bulk") {
                        com.example.healthsyncandroid.utils.SyncLogManager.log("Receiving bulk payload from iOS...")
                        val payload = call.receive<com.example.healthsyncandroid.data.BulkSyncPayload>()
                        val success = com.example.healthsyncandroid.data.HealthRepository(applicationContext).saveBulk(payload)
                        call.respond(mapOf("success" to success))
                    }
                }
            }.start(wait = true)
        }
    }

    override fun onDestroy() {
        server?.stop(1000, 2000)
        server = null
        serverJob?.cancel()

        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wifiLock?.let {
            if (it.isHeld) it.release()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Health Sync Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "LocalServerChannel"
    }
}
