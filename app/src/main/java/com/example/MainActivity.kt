package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.*
import com.example.ui.theme.*
import com.example.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        org.maplibre.android.MapLibre.getInstance(this)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val userRole by viewModel.userRole.collectAsStateWithLifecycle()

                // Battery Optimizations state check
                var isIgnoringBattery by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    isIgnoringBattery = isIgnoringBatteryOptimizations(context)
                }

                // Dynamic permission request for POST_NOTIFICATIONS (Android 13+)
                var hasNotificationPermission by remember {
                    mutableStateOf(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                        } else {
                            true
                        }
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    hasNotificationPermission = isGranted
                    if (!isGranted) {
                        Toast.makeText(
                            context,
                            "Anlık bildirimler için bildirim izni vermeniz önerilir.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                // Auto request permission on app start or login
                LaunchedEffect(userRole) {
                    if (userRole.isNotEmpty() && !hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = BrandDeepForest,
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                            .background(BrandDeepForest)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Suggest ignore battery optimization banner if not ignoring and user is manager/waiter
                            if (!isIgnoringBattery && userRole.isNotEmpty() && (userRole == "mudur" || userRole == "garson")) {
                                BatteryOptimizationBanner(
                                    onRequestIgnore = {
                                        requestIgnoreBatteryOptimizations(context)
                                        // Refresh state
                                        isIgnoringBattery = isIgnoringBatteryOptimizations(context)
                                    }
                                )
                            }

                            // Router based on User Role State
                            AnimatedContent(
                                targetState = userRole,
                                transitionSpec = {
                                    fadeIn() togetherWith fadeOut()
                                },
                                label = "screen_navigation"
                            ) { role ->
                                when (role) {
                                    "" -> {
                                        LoginScreen(viewModel = viewModel)
                                    }
                                    "admin" -> {
                                        var currentBranchId by remember { mutableStateOf<String?>(null) }
                                        if (currentBranchId == null) {
                                            AdminMainScreen(
                                                viewModel = viewModel,
                                                onBranchClick = { currentBranchId = it }
                                            )
                                        } else {
                                            BranchDetailScreen(
                                                viewModel = viewModel,
                                                branchId = currentBranchId!!,
                                                onBackClick = { currentBranchId = null }
                                            )
                                        }
                                    }
                                    "mudur" -> {
                                        ManagerMainScreen(viewModel = viewModel)
                                    }
                                    "garson" -> {
                                        WaiterMainScreen(viewModel = viewModel)
                                    }
                                    else -> {
                                        LoginScreen(viewModel = viewModel)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    private fun requestIgnoreBatteryOptimizations(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to general settings
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (ex: Exception) {
                    Toast.makeText(context, "Pil ayarlarına gidilemedi, lütfen manuel olarak muaf tutun.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

@Composable
fun BatteryOptimizationBanner(onRequestIgnore: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .testTag("battery_optimization_banner"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.BatteryAlert,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pil Optimizasyon Uyarısı",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "Çağrı bildirimlerini zamanında alabilmek için pil tasarrufundan muaf tutmanız önerilir.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    com.example.utils.ErrorUtils.safeUiAction(context, "Pil ayarlarına erişilemedi") {
                        onRequestIgnore()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandGreenHover,
                    contentColor = TextPrimary
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, BrandGold.copy(alpha = 0.4f)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("İzin Ver", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
