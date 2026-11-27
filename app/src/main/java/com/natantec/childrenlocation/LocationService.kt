package com.natantec.childrenlocation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Serviço em primeiro plano do aparelho da CRIANÇA.
 * A notificação é obrigatória e permanente: a criança sempre sabe que está compartilhando.
 * O Fused Location Provider combina GPS, Wi-Fi e rede celular automaticamente.
 */
class LocationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client by lazy { LocationServices.getFusedLocationProviderClient(this) }

    // Intervalo adaptativo: parado = menos gravações (economiza bateria e a cota gratuita).
    private var lastLat = 0.0
    private var lastLng = 0.0
    private var lastSentMs = 0L

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            val pairing = Prefs.loadPairing(this@LocationService) ?: return

            val now = System.currentTimeMillis()
            val moved = FloatArray(1)
            android.location.Location.distanceBetween(lastLat, lastLng, loc.latitude, loc.longitude, moved)
            val stationary = lastSentMs != 0L && moved[0] < 25f
            val minGap = if (stationary) 120_000L else 15_000L
            if (now - lastSentMs < minGap) return
            lastLat = loc.latitude; lastLng = loc.longitude; lastSentMs = now

            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            scope.launch {
                runCatching { Repo.pushLocation(pairing, loc.latitude, loc.longitude, loc.accuracy, battery) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Prefs.loadPairing(this) == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        startUpdates()
        return START_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Compartilhando localização", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("Localização compartilhada")
            .setContentText("Seus responsáveis podem ver onde você está.")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    @Suppress("MissingPermission")
    private fun startUpdates() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 15_000L)
            .setMinUpdateIntervalMillis(10_000L)
            .build()
        client.requestLocationUpdates(req, callback, mainLooper)
    }

    override fun onDestroy() {
        client.removeLocationUpdates(callback)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "location_sharing"
        private const val NOTIF_ID = 1

        fun start(ctx: Context) {
            val i = Intent(ctx, LocationService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, LocationService::class.java))
        }
    }
}
