package com.example.test
import android.util.Log
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*



class Location(private val context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        2000 // Update interval in milliseconds
    ).build()

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(onSuccess: (latitude: String, longitude: String) -> Unit,
                           onFailure: (error: String) -> Unit) {
        // Check for location permissions

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            onFailure("Location permission not granted")
            // Permission is not granted, handle it in the activity
            return
        }

        val timeoutHandler = android.os.Handler(Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            onFailure("Location request timed out")
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    super.onLocationResult(locationResult)
                    val location: Location? = locationResult.lastLocation
                    if (location != null) {
                        onSuccess(location.latitude.toString(), location.longitude.toString())
                        // Stop location updates after getting the location
                        //fusedLocationClient.removeLocationUpdates(this)
                    }
                }
            },
            Looper.getMainLooper()
        )
    }
}


