package com.example.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume

data class AddressDetails(
    val city: String = "",          // İl
    val district: String = "",      // İlçe
    val neighborhood: String = "",  // Mahalle
    val street: String = "",        // Sokak/Cadde
    val doorNumber: String = "",    // Kapı No
    val fullAddress: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
) {
    val hasDetails: Boolean
        get() = city.isNotBlank() || district.isNotBlank() || neighborhood.isNotBlank() || street.isNotBlank() || fullAddress.isNotBlank()

    fun getFormattedShort(): String {
        val parts = listOfNotNull(
            neighborhood.takeIf { it.isNotBlank() },
            district.takeIf { it.isNotBlank() },
            city.takeIf { it.isNotBlank() }
        )
        return if (parts.isNotEmpty()) parts.joinToString(", ") else fullAddress
    }

    /**
     * Returns structured hierarchical address:
     * Line 1: İl · İlçe · Mahalle
     * Line 2: Cadde / Sokak / Kapı No (or full address fallback)
     */
    fun getHierarchyAddress(): Pair<String, String> {
        val line1Parts = listOfNotNull(
            city.takeIf { it.isNotBlank() },
            district.takeIf { it.isNotBlank() },
            neighborhood.takeIf { it.isNotBlank() }
        )
        val line1 = if (line1Parts.isNotEmpty()) line1Parts.joinToString(" · ") else "Seçilen Konum"
        val line2 = listOfNotNull(
            street.takeIf { it.isNotBlank() },
            doorNumber.takeIf { it.isNotBlank() }?.let { "No: $it" }
        ).joinToString(" ").ifBlank {
            if (fullAddress.isNotBlank() && fullAddress != line1) fullAddress else ""
        }
        return Pair(line1, line2)
    }
}

object LocationUtils {

    private const val TAG = "LocationUtils"

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        val gpsEnabled = try { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (e: Exception) { false }
        val netEnabled = try { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { false }
        return gpsEnabled || netEnabled
    }

    /**
     * Gets real device GPS location using Google Play Services FusedLocationProviderClient
     * with high accuracy and automatic fallback to LocationManager.
     */
    fun getCurrentLocation(
        context: Context,
        onLocationObtained: (Location) -> Unit,
        onError: (String) -> Unit
    ) {
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            Log.w(TAG, "getCurrentLocation: Konum izni verilmemiş (ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION).")
            onError("PERMISSION_DENIED")
            return
        }

        if (!isLocationEnabled(context)) {
            Log.w(TAG, "getCurrentLocation: Cihaz konum servisi kapalı.")
            onError("LOCATION_DISABLED")
            return
        }

        val priority = if (fineGranted) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()

            fusedClient.getCurrentLocation(priority, cts.token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        Log.d(TAG, "FusedLocationProviderClient konum aldı: lat=${location.latitude}, lng=${location.longitude}, acc=${location.accuracy}m")
                        onLocationObtained(location)
                    } else {
                        // Fallback to fusedClient.lastLocation
                        fusedClient.lastLocation
                            .addOnSuccessListener { lastLoc ->
                                if (lastLoc != null) {
                                    Log.d(TAG, "FusedLocationProviderClient lastLocation kullandı: lat=${lastLoc.latitude}, lng=${lastLoc.longitude}")
                                    onLocationObtained(lastLoc)
                                } else {
                                    Log.d(TAG, "FusedLocationProviderClient null döndü, LocationManager fallback deneniyor.")
                                    fallbackToLocationManager(context, onLocationObtained, onError)
                                }
                            }
                            .addOnFailureListener {
                                fallbackToLocationManager(context, onLocationObtained, onError)
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "FusedLocationProviderClient hatası: ${e.message}, LocationManager fallback yapılıyor.")
                    fallbackToLocationManager(context, onLocationObtained, onError)
                }
        } catch (e: SecurityException) {
            Log.e(TAG, "Güvenlik hatası: ${e.message}")
            onError("PERMISSION_DENIED")
        } catch (e: Exception) {
            Log.w(TAG, "FusedLocation başlatılamadı (${e.message}), LocationManager fallback yapılıyor.")
            fallbackToLocationManager(context, onLocationObtained, onError)
        }
    }

    private fun fallbackToLocationManager(
        context: Context,
        onLocationObtained: (Location) -> Unit,
        onError: (String) -> Unit
    ) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            onError("Konum servisi kullanılamıyor.")
            return
        }

        var lastKnown: Location? = null
        try {
            val gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val netLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            lastKnown = if (gpsLoc != null && netLoc != null) {
                if (gpsLoc.time > netLoc.time) gpsLoc else netLoc
            } else gpsLoc ?: netLoc
        } catch (e: SecurityException) {
            onError("PERMISSION_DENIED")
            return
        } catch (e: Exception) {
            Log.w(TAG, "LocationManager lastKnown hata: ${e.message}")
        }

        if (lastKnown != null && (System.currentTimeMillis() - lastKnown.time) < 3 * 60 * 1000) {
            Log.d(TAG, "LocationManager son bilinen taze konum kullanıldı: ${lastKnown.latitude}, ${lastKnown.longitude}")
            onLocationObtained(lastKnown)
            return
        }

        val provider = when {
            try { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (e: Exception) { false } -> LocationManager.GPS_PROVIDER
            try { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { false } -> LocationManager.NETWORK_PROVIDER
            else -> null
        }

        if (provider == null) {
            if (lastKnown != null) {
                onLocationObtained(lastKnown)
            } else {
                onError("LOCATION_DISABLED")
            }
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                locationManager.getCurrentLocation(
                    provider,
                    null,
                    context.mainExecutor
                ) { location ->
                    if (location != null) {
                        Log.d(TAG, "LocationManager getCurrentLocation başarılı: ${location.latitude}, ${location.longitude}")
                        onLocationObtained(location)
                    } else if (lastKnown != null) {
                        onLocationObtained(lastKnown)
                    } else {
                        onError("Gerçek cihaz konumu alınamadı. GPS uydusu bekleniyor veya konum açık değil.")
                    }
                }
            } else {
                locationManager.requestSingleUpdate(
                    provider,
                    object : android.location.LocationListener {
                        override fun onLocationChanged(location: Location) {
                            onLocationObtained(location)
                        }
                        override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}
                        override fun onProviderEnabled(p: String) {}
                        override fun onProviderDisabled(p: String) {}
                    },
                    null
                )
            }
        } catch (e: SecurityException) {
            onError("PERMISSION_DENIED")
        } catch (e: Exception) {
            if (lastKnown != null) {
                onLocationObtained(lastKnown)
            } else {
                onError("Gerçek cihaz GPS konumu alınamadı, lütfen açık bir alanda tekrar deneyin.")
            }
        }
    }

    fun calculateDistanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results[0]
    }

    suspend fun getAddressFromCoordinates(context: Context, lat: Double, lng: Double): AddressDetails = withContext(Dispatchers.IO) {
        // 1. Android Geocoder
        if (Geocoder.isPresent()) {
            try {
                val geocoder = Geocoder(context, Locale("tr", "TR"))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val details = suspendCancellableCoroutine<AddressDetails?> { cont ->
                        try {
                            geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    if (addresses.isNotEmpty()) {
                                        cont.resume(parseAndroidAddress(addresses[0]))
                                    } else {
                                        cont.resume(null)
                                    }
                                }
                                override fun onError(errorMessage: String?) {
                                    cont.resume(null)
                                }
                            })
                        } catch (e: Exception) {
                            cont.resume(null)
                        }
                    }
                    if (details != null && details.hasDetails) {
                        return@withContext details
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(lat, lng, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val details = parseAndroidAddress(addresses[0])
                        if (details.hasDetails) {
                            return@withContext details
                        }
                    }
                }
            } catch (e: Exception) {
                // Fall through to Nominatim
            }
        }

        // 2. OpenStreetMap Nominatim Fallback
        try {
            val url = URL(String.format(Locale.US, "https://nominatim.openstreetmap.org/reverse?format=json&lat=%.6f&lon=%.6f&zoom=18&addressdetails=1", lat, lng))
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "QRServis-AndroidApp/1.0")
                setRequestProperty("Accept-Language", "tr,en;q=0.8")
            }
            if (conn.responseCode == 200) {
                val stream = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(stream)
                val addressObj = json.optJSONObject("address")
                if (addressObj != null) {
                    val city = addressObj.optString("province").ifBlank {
                        addressObj.optString("city").ifBlank { addressObj.optString("state", "") }
                    }
                    val district = addressObj.optString("district").ifBlank {
                        addressObj.optString("county").ifBlank {
                            addressObj.optString("town").ifBlank { addressObj.optString("city_district", "") }
                        }
                    }
                    val neighborhood = addressObj.optString("suburb").ifBlank {
                        addressObj.optString("neighbourhood").ifBlank {
                            addressObj.optString("quarter").ifBlank { addressObj.optString("village", "") }
                        }
                    }
                    val street = addressObj.optString("road").ifBlank {
                        addressObj.optString("pedestrian").ifBlank {
                            addressObj.optString("street").ifBlank { addressObj.optString("footway", "") }
                        }
                    }
                    val doorNumber = addressObj.optString("house_number", "")
                    val full = json.optString("display_name", "")
                    return@withContext AddressDetails(
                        city = city,
                        district = district,
                        neighborhood = neighborhood,
                        street = street,
                        doorNumber = doorNumber,
                        fullAddress = full
                    )
                }
            }
        } catch (e: Exception) {
            // Ignored
        }

        AddressDetails(
            fullAddress = String.format(Locale.US, "%.5f, %.5f", lat, lng),
            error = "Adres detayları alınamadı"
        )
    }

    private fun parseAndroidAddress(addr: Address): AddressDetails {
        val city = addr.adminArea ?: ""
        val district = addr.subAdminArea ?: (if (addr.locality != null && addr.locality != city) addr.locality else "")
        val neighborhood = addr.subLocality ?: ""
        val street = addr.thoroughfare ?: ""
        val doorNumber = addr.subThoroughfare ?: ""
        val full = (0..addr.maxAddressLineIndex).mapNotNull { addr.getAddressLine(it) }.joinToString(", ").ifBlank {
            listOfNotNull(street.takeIf { it.isNotBlank() }, neighborhood.takeIf { it.isNotBlank() }, district.takeIf { it.isNotBlank() }, city.takeIf { it.isNotBlank() }).joinToString(", ")
        }
        return AddressDetails(
            city = city,
            district = district,
            neighborhood = neighborhood,
            street = street,
            doorNumber = doorNumber,
            fullAddress = full
        )
    }
}
