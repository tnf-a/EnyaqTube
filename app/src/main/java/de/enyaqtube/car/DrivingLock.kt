package de.enyaqtube.car

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.car.app.CarContext
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.common.OnCarDataAvailableListener
import androidx.car.app.hardware.info.Speed
import androidx.core.content.ContextCompat
import kotlin.math.abs

/**
 * Fahrsperre: Sobald das Auto fährt, wird das Video ausgeblendet und pausiert.
 * Quellen: Geschwindigkeit vom Auto (falls Android Auto sie liefert) und GPS vom Handy.
 *
 * Gesperrt wird ab ~10 km/h. Entsperrt wird erst, wenn eine aktuelle Messung
 * mindestens 3 Sekunden lang unter ~4 km/h liegt. Gehen während der Fahrt alle
 * Messungen verloren (z. B. Tunnel), bleibt die Sperre bestehen.
 */
class DrivingLock(
    private val carContext: CarContext,
    private val onChange: (locked: Boolean) -> Unit,
) {
    private companion object {
        const val LOCK_MS = 2.8f      // ≈ 10 km/h
        const val UNLOCK_MS = 1.1f    // ≈ 4 km/h
        const val STALE_MS = 5_000L
        const val UNLOCK_DELAY_MS = 3_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var carSpeed = 0f
    private var carSpeedAt = 0L
    private var gpsSpeed = 0f
    private var gpsSpeedAt = 0L
    private var slowSince = 0L
    private var running = false

    var locked = false
        private set

    private val speedListener = OnCarDataAvailableListener<Speed> { speed ->
        val value = speed.rawSpeedMetersPerSecond
        if (value.status == CarValue.STATUS_SUCCESS) {
            value.value?.let { carSpeed = abs(it); carSpeedAt = SystemClock.elapsedRealtime() }
        }
    }

    private val locationListener = LocationListener { loc ->
        if (loc.hasSpeed()) {
            gpsSpeed = loc.speed
            gpsSpeedAt = SystemClock.elapsedRealtime()
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            evaluate()
            if (running) handler.postDelayed(this, 500)
        }
    }

    fun start() {
        if (running) return
        running = true
        try {
            carContext.getCarService(CarHardwareManager::class.java).carInfo
                .addSpeedListener(ContextCompat.getMainExecutor(carContext), speedListener)
        } catch (_: Throwable) {
            // Auto/Host liefert keine Geschwindigkeit – dann nur GPS.
        }
        if (ContextCompat.checkSelfPermission(carContext, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                val lm = carContext.getSystemService(LocationManager::class.java)
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener, Looper.getMainLooper()
                )
            } catch (_: Throwable) {
            }
        }
        handler.post(tick)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
        try {
            carContext.getCarService(CarHardwareManager::class.java).carInfo.removeSpeedListener(speedListener)
        } catch (_: Throwable) {
        }
        try {
            carContext.getSystemService(LocationManager::class.java).removeUpdates(locationListener)
        } catch (_: Throwable) {
        }
    }

    private fun evaluate() {
        val now = SystemClock.elapsedRealtime()
        val fresh = buildList {
            if (now - carSpeedAt < STALE_MS) add(carSpeed)
            if (now - gpsSpeedAt < STALE_MS) add(gpsSpeed)
        }
        val max = fresh.maxOrNull()

        if (max != null && max >= LOCK_MS) {
            slowSince = 0
            set(true)
        } else if (locked) {
            if (max != null && max < UNLOCK_MS) {
                if (slowSince == 0L) slowSince = now
                if (now - slowSince >= UNLOCK_DELAY_MS) set(false)
            } else {
                slowSince = 0
            }
        }
    }

    private fun set(value: Boolean) {
        if (locked == value) return
        locked = value
        onChange(value)
    }
}
