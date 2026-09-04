package com.gssc.daylog

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import android.os.PowerManager
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Foreground service. This is what makes tracking survive the screen going off
 * and you switching to another app.
 */
class TrackerService : Service() {

    private lateinit var client: FusedLocationProviderClient
    private lateinit var store: Store
    private var callback: LocationCallback? = null
    private var wake: PowerManager.WakeLock? = null

    companion object {
        const val CHANNEL = "daylog_tracking"
        const val NOTIF_ID = 41
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        client = LocationServices.getFusedLocationProviderClient(this)
        createChannel()

        // Without this the system suspends the CPU between fixes once the
        // screen has been off a while, and updates dry up to a trickle.
        val pm = getSystemService(PowerManager::class.java)
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "daylog:tracking")
        wake?.setReferenceCounted(false)
        wake?.acquire()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIF_ID,
            buildNotification("Recording your day"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        )
        requestUpdates()
        return START_STICKY
    }

    private fun createChannel() {
        val ch = NotificationChannel(
            CHANNEL, "Tracking", NotificationManager.IMPORTANCE_LOW
        )
        ch.setShowBadge(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(text: String): Notification {
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL)
            .setContentTitle("Daylog")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(tap)
            .setOngoing(true)
            .build()
    }

    private fun requestUpdates() {
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30_000L)
            .setMinUpdateIntervalMillis(15_000L)
            .setMinUpdateDistanceMeters(15f)
            .setWaitForAccurateLocation(false)
            .build()

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { handle(it) }
            }
        }
        callback = cb
        try {
            client.requestLocationUpdates(req, cb, Looper.getMainLooper())
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private fun handle(loc: Location) {
        if (loc.accuracy > 120f) return

        val f = Fix(
            System.currentTimeMillis(),
            loc.latitude,
            loc.longitude,
            loc.accuracy.toInt()
        )

        val list = store.fixes()
        val prev = list.lastOrNull()
        if (prev != null) {
            if (Fmt.dayKey(prev.t) != Fmt.dayKey(f.t)) {
                store.archive(Fmt.dayKey(prev.t), Geo.buildStops(list, store.namer()))
                store.saveFixes(listOf(f))
                return
            }
            val moved = Geo.haversine(prev.lat, prev.lng, f.lat, f.lng)
            if (moved < 8.0 && f.t - prev.t < 45_000L) return
        }
        store.addFix(f)
        NameLookup.fillMissing(this, store)
    }

    override fun onDestroy() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
        try { wake?.release() } catch (e: Exception) { }
        wake = null
        super.onDestroy()
    }
}
