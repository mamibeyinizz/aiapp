package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import com.example.data.Branch
import com.example.ui.theme.*
import com.example.utils.AddressDetails
import com.example.utils.ErrorUtils
import com.example.utils.LocationUtils
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Address Detail Row component displaying key-value pairs with icon.
 */
@Composable
fun AddressDetailItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(BrandGreenHover.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = BrandGoldLight,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "$label:",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(90.dp)
        )
        Text(
            text = value.ifBlank { "—" },
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Comprehensive Branch Location & Address Card.
 * Displays reverse-geocoded address: İl, İlçe, Mahalle, Sokak/Cadde, Kapı No
 * along with small secondary GPS coordinates.
 */
@Composable
fun BranchLocationAddressCard(
    branch: Branch,
    modifier: Modifier = Modifier,
    onAddressResolved: ((AddressDetails) -> Unit)? = null
) {
    val context = LocalContext.current
    var addressDetails by remember(branch.lat, branch.lng) {
        mutableStateOf(
            if (branch.lat != null && branch.lng != null) {
                AddressDetails(isLoading = true)
            } else {
                AddressDetails(isLoading = false)
            }
        )
    }

    LaunchedEffect(branch.lat, branch.lng) {
        if (branch.lat != null && branch.lng != null) {
            addressDetails = AddressDetails(isLoading = true)
            try {
                val resolved = LocationUtils.getAddressFromCoordinates(context, branch.lat, branch.lng)
                addressDetails = resolved
                onAddressResolved?.invoke(resolved)
            } catch (e: Exception) {
                addressDetails = AddressDetails(
                    fullAddress = branch.address ?: String.format(Locale.US, "%.5f, %.5f", branch.lat, branch.lng),
                    isLoading = false,
                    error = "Adres bilgisi alınamadı"
                )
            }
        } else {
            addressDetails = AddressDetails(isLoading = false)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkBg.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
            .border(1.dp, BorderColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        // Card Top Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = BrandGold,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Şube Konumu & Adresi",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (branch.lat != null && branch.lng != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StatusGreen.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, StatusGreen.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(StatusGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "GPS Doğrulandı",
                            color = StatusGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StatusAmber.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, StatusAmber.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(StatusAmber, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Konum Ayarlanmadı",
                            color = StatusAmber,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (branch.lat != null && branch.lng != null) {
            // Address Details or Loading
            if (addressDetails.isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = BrandGold,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Adres bilgisi çözümleniyor (Reverse Geocoding)...",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            } else {
                // Address Component Hierarchy
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .border(0.5.dp, BorderColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    AddressDetailItem(
                        label = "İl",
                        value = addressDetails.city.ifBlank { "Belirlenemedi" },
                        icon = Icons.Default.LocationCity
                    )
                    AddressDetailItem(
                        label = "İlçe",
                        value = addressDetails.district.ifBlank { "Belirlenemedi" },
                        icon = Icons.Default.Apartment
                    )
                    AddressDetailItem(
                        label = "Mahalle",
                        value = addressDetails.neighborhood.ifBlank { "Belirlenemedi" },
                        icon = Icons.Default.Home
                    )
                    AddressDetailItem(
                        label = "Sokak / Cadde",
                        value = addressDetails.street.ifBlank { "Belirlenemedi" },
                        icon = Icons.Default.Signpost
                    )
                    if (addressDetails.doorNumber.isNotBlank()) {
                        AddressDetailItem(
                            label = "Kapı No",
                            value = addressDetails.doorNumber,
                            icon = Icons.Default.DoorSliding
                        )
                    }

                    if (addressDetails.fullAddress.isNotBlank()) {
                        Divider(
                            color = BorderColor.copy(alpha = 0.3f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Açık Adres (Özet):",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = addressDetails.fullAddress,
                                    color = BrandOffWhite,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Şube Adresi", addressDetails.fullAddress))
                                    Toast.makeText(context, "Adres kopyalandı", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Adresi Kopyala",
                                    tint = BrandGoldLight,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary Small Coordinates & Radius (as specifically required by user prompt)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBg.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = null,
                        tint = BrandTextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GPS: ${String.format(Locale.US, "%.5f", branch.lat)}, ${String.format(Locale.US, "%.5f", branch.lng)}",
                        color = BrandTextMuted,
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = "İzin Yarıçapı: ${(branch.radius ?: 150.0).toInt()}m",
                    color = BrandGoldLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            // No location set warning
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .border(0.5.dp, StatusAmber.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = StatusAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Şube konumu henüz kaydedilmedi",
                        color = BrandOffWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Garsonların vardiyaya yalnızca şube sınırları içerisinde başlayabilmesi için aşağıdaki butonları kullanarak şube konumunu belirleyin.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

/**
 * Fullscreen Interactive Map Picker Dialog using MapLibre Native SDK and OpenFreeMap Liberty style.
 * Provides GPU-accelerated vector map rendering, touch-to-pick marker, real device GPS positioning,
 * and debounced hierarchical reverse geocoding with 100% offline resilience and zero blank screens.
 */
@Composable
fun MapLocationPickerDialog(
    initialLat: Double?,
    initialLng: Double?,
    onConfirm: (lat: Double, lng: Double, address: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize MapLibre engine
    LaunchedEffect(Unit) {
        try {
            MapLibre.getInstance(context)
        } catch (e: Exception) {
            android.util.Log.e("MapLocationPicker", "MapLibre.getInstance error: ${e.message}")
        }
    }

    var selectedLat by remember { mutableStateOf(initialLat ?: 41.0082) }
    var selectedLng by remember { mutableStateOf(initialLng ?: 28.9784) }
    var liveAddress by remember { mutableStateOf(AddressDetails(isLoading = true)) }
    var isLocatingCurrent by remember { mutableStateOf(false) }
    var isMapReady by remember { mutableStateOf(false) }
    var mapLoadError by remember { mutableStateOf<String?>(null) }

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var markerRef by remember { mutableStateOf<Marker?>(null) }

    // Native MapView instance remembered across recompositions
    val mapView = remember {
        MapView(context).apply {
            onCreate(null)
        }
    }

    // MapView Lifecycle management to prevent leaks and surface destruction
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            } catch (e: Exception) {
                android.util.Log.w("MapLocationPicker", "MapView cleanup error: ${e.message}")
            }
        }
    }

    // Reverse geocode whenever selectedLat/selectedLng changes (debounced)
    LaunchedEffect(selectedLat, selectedLng) {
        liveAddress = AddressDetails(isLoading = true)
        delay(350) // Debounce
        try {
            liveAddress = LocationUtils.getAddressFromCoordinates(context, selectedLat, selectedLng)
        } catch (e: Exception) {
            liveAddress = AddressDetails(
                fullAddress = String.format(Locale.US, "%.5f, %.5f", selectedLat, selectedLng),
                isLoading = false
            )
        }
    }

    // Function to load style and setup listeners on the MapLibreMap instance
    fun applyMapStyle(map: MapLibreMap) {
        map.uiSettings.isAttributionEnabled = true
        map.uiSettings.isLogoEnabled = false
        map.uiSettings.isCompassEnabled = true

        val currentPoint = LatLng(selectedLat, selectedLng)
        val cameraPosition = CameraPosition.Builder()
            .target(currentPoint)
            .zoom(15.5)
            .build()
        map.cameraPosition = cameraPosition

        // OpenFreeMap Liberty style (No API key needed, commercial use permitted)
        map.setStyle(Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")) {
            isMapReady = true
            mapLoadError = null
            if (markerRef == null) {
                markerRef = map.addMarker(
                    MarkerOptions()
                        .position(currentPoint)
                        .title("Şube Konumu")
                )
            } else {
                markerRef?.position = currentPoint
            }
        }
    }

    // Map initialization and click handling
    LaunchedEffect(mapView) {
        mapView.addOnDidFailLoadingMapListener { error ->
            android.util.Log.e("MapLocationPicker", "MapLibre harita yükleme hatası: $error")
            mapLoadError = "Harita yüklenemedi. İnternet bağlantınızı kontrol edip tekrar deneyin."
        }

        mapView.getMapAsync { map ->
            mapLibreMap = map
            applyMapStyle(map)

            map.addOnMapClickListener { point ->
                selectedLat = point.latitude
                selectedLng = point.longitude
                if (markerRef != null) {
                    markerRef?.position = point
                } else {
                    markerRef = map.addMarker(
                        MarkerOptions()
                            .position(point)
                            .title("Şube Konumu")
                    )
                }
                true
            }
        }
    }

    // Handler when real device GPS location is obtained
    fun onLocationObtainedFromGps(loc: android.location.Location) {
        isLocatingCurrent = false
        selectedLat = loc.latitude
        selectedLng = loc.longitude
        val target = LatLng(loc.latitude, loc.longitude)
        mapLibreMap?.let { map ->
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 16.5))
            if (markerRef != null) {
                markerRef?.position = target
            } else {
                markerRef = map.addMarker(
                    MarkerOptions()
                        .position(target)
                        .title("Şube Konumu")
                )
            }
        }
        Toast.makeText(context, "Gerçek cihaz konumuna gidildi", Toast.LENGTH_SHORT).show()
    }

    val locPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            isLocatingCurrent = true
            LocationUtils.getCurrentLocation(
                context = context,
                onLocationObtained = { loc ->
                    onLocationObtainedFromGps(loc)
                },
                onError = { err ->
                    isLocatingCurrent = false
                    val msg = when (err) {
                        "LOCATION_DISABLED" -> "Cihazın konum (GPS) servisi kapalı. Lütfen açın."
                        "PERMISSION_DENIED" -> "Konum izni verilmedi."
                        else -> err
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            Toast.makeText(context, "Konum izni verilmedi. Gerçek cihaz GPS konumu alınamadı.", Toast.LENGTH_LONG).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = DarkBg
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface)
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Kapat",
                                tint = TextPrimary
                            )
                        }
                        Column {
                            Text(
                                text = "Haritadan Konum Seç",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Şube noktasını haritadan işaretleyin",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // My Location Button (Gets Real Device GPS Location via FusedLocationProviderClient)
                    IconButton(
                        onClick = {
                            if (LocationUtils.hasLocationPermission(context)) {
                                isLocatingCurrent = true
                                LocationUtils.getCurrentLocation(
                                    context = context,
                                    onLocationObtained = { loc ->
                                        onLocationObtainedFromGps(loc)
                                    },
                                    onError = { err ->
                                        isLocatingCurrent = false
                                        val msg = when (err) {
                                            "LOCATION_DISABLED" -> "Cihazın konum (GPS) servisi kapalı. Lütfen açın."
                                            "PERMISSION_DENIED" -> "Konum izni verilmedi."
                                            else -> err
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            } else {
                                locPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .background(BrandGreenHover, CircleShape)
                            .testTag("map_my_location_button")
                    ) {
                        if (isLocatingCurrent) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = BrandGold, strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Konumuma Git",
                                tint = BrandGoldLight,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Map Area with MapLibre Native View
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    AndroidView(
                        factory = { mapView },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Error view if map tiles fail to load (e.g., offline or network error)
                    if (mapLoadError != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(DarkSurface.copy(alpha = 0.95f))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = StatusRed,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = "Harita yüklenemedi. İnternet bağlantınızı kontrol edip tekrar deneyin.",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Harita verisi OpenFreeMap üzerinden alınır. Çevrimdışı olsanız bile kayıtlı koordinat veya GPS konumu ile devam edebilirsiniz.",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = {
                                        mapLoadError = null
                                        isMapReady = false
                                        mapLibreMap?.let { map ->
                                            applyMapStyle(map)
                                        } ?: run {
                                            mapView.getMapAsync { map ->
                                                mapLibreMap = map
                                                applyMapStyle(map)
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreenHover),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Yeniden Dene", color = BrandOffWhite, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    // Loading overlay while style initializes
                    if (!isMapReady && mapLoadError == null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .background(DarkBg.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                                .border(1.dp, BrandGold.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = BrandGold, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Harita yükleniyor...", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    // Overlay Map Touch Hint
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                            .background(DarkBg.copy(alpha = 0.88f), RoundedCornerShape(20.dp))
                            .border(0.5.dp, BorderColor, RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = BrandGoldLight,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Haritaya dokunarak şube konumunu belirleyin",
                                color = TextPrimary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Attribution Badge (OpenFreeMap + OpenStreetMap)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 8.dp, bottom = 8.dp)
                            .background(DarkBg.copy(alpha = 0.78f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Harita: OpenFreeMap · © OpenStreetMap",
                            color = TextSecondary,
                            fontSize = 9.sp
                        )
                    }
                }

                // Bottom Panel: Hierarchical Address Resolution, Coordinates, and Confirm Button
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface)
                        .padding(14.dp)
                ) {
                    val (line1, line2) = liveAddress.getHierarchyAddress()

                    // Title
                    Text(
                        text = "Seçilen Konum:",
                        color = BrandGoldLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    if (liveAddress.isLoading) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), color = BrandGold, strokeWidth = 1.5.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Adres çözümleniyor...", color = TextSecondary, fontSize = 11.sp)
                        }
                    } else {
                        // Line 1: İl · İlçe · Mahalle
                        Text(
                            text = line1,
                            color = BrandOffWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Line 2: Sokak / Cadde / Kapı No
                        if (line2.isNotBlank()) {
                            Text(
                                text = line2,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Coordinate display (Primary source of truth)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Koordinat: ${String.format(Locale.US, "%.5f, %.5f", selectedLat, selectedLng)}",
                            color = BrandGoldLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Asıl Kaynak",
                            color = TextSecondary,
                            fontSize = 10.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Text("Vazgeç", color = TextSecondary)
                        }

                        Button(
                            onClick = {
                                val fullAddr = liveAddress.fullAddress.ifBlank {
                                    listOfNotNull(
                                        liveAddress.street.takeIf { it.isNotBlank() },
                                        liveAddress.neighborhood.takeIf { it.isNotBlank() },
                                        liveAddress.district.takeIf { it.isNotBlank() },
                                        liveAddress.city.takeIf { it.isNotBlank() }
                                    ).joinToString(", ")
                                }
                                onConfirm(selectedLat, selectedLng, fullAddr)
                            },
                            modifier = Modifier.weight(1.5f).height(46.dp).testTag("confirm_map_location_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandGreenHover,
                                contentColor = BrandOffWhite
                            ),
                            border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bu Konumu Seç", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
