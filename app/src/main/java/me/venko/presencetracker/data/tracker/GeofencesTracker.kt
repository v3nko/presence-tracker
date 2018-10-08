package me.venko.presencetracker.data.tracker

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import me.venko.presencetracker.data.events.GeofencingStatusUpdate
import me.venko.presencetracker.service.GeofencesTrackerIntentService
import me.venko.presencetracker.utils.logd
import me.venko.presencetracker.utils.loge
import me.venko.presencetracker.utils.logv
import me.venko.presencetracker.utils.logw
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * @author Victor Kosenko
 *
 */
class GeofencesTracker(
        private val context: Context,
        private val onStatusUpdate: (GeofencingStatus) -> Unit
) {

    private var geofencingClient: GeofencingClient? = null
    private val bus = EventBus.getDefault()

    @SuppressLint("MissingPermission")
    fun register(locationBounds: LocationBounds) {
        bus.register(this)
        logv { "Registering tracker" }
        geofencingClient = LocationServices.getGeofencingClient(context)

        setupGeofencing(locationBounds)
    }

    fun updateBounds(locationBounds: LocationBounds) {
        setupGeofencing(locationBounds)
    }

    @SuppressLint("MissingPermission")
    private fun setupGeofencing(locationBounds: LocationBounds) {
        when {
            locationBounds.isEmpty() -> {
                logw { "Location bounds is not set" }
                return
            }
            locationBounds.radius == 0f -> {
                logd { "Geofences radius is not set" }
                return
            }
        }
        val geofence = makeGeofence(locationBounds)

        geofencingClient?.addGeofences(getGeofencingRequest(geofence), geofencePendingIntent)?.run {
            addOnSuccessListener {
                logd { "Geofencing setup success" }
            }

            addOnFailureListener {
                loge(it) { "Geofencing setup error" }
                onStatusUpdate(GeofencingStatus.UNKNOWN)
            }
        }
    }

    private fun makeGeofence(locationBounds: LocationBounds): Geofence = Geofence.Builder()
            .setRequestId(GEOCFENCE_ID)
            .setCircularRegion(
                    locationBounds.center.latitude,
                    locationBounds.center.longitude,
                    locationBounds.radius
            )
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofencesTrackerIntentService::class.java)
        PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun getGeofencingRequest(geofence: Geofence): GeofencingRequest {
        return GeofencingRequest.Builder().apply {
            setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER
                    or GeofencingRequest.INITIAL_TRIGGER_EXIT
                    or GeofencingRequest.INITIAL_TRIGGER_DWELL)
                    .addGeofence(geofence)
        }.build()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onGeofencingStatusUpdate(event: GeofencingStatusUpdate) {
        onStatusUpdate(event.geofencingStatus)
    }

    fun unregister() {
        logv { "Unregistering tracker" }
        geofencingClient?.removeGeofences(geofencePendingIntent)
        bus.unregister(this)
    }

    companion object {
        private const val GEOCFENCE_ID = "primaryGeofence"
    }
}