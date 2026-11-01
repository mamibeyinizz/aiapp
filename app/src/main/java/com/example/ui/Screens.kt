package com.example.ui

import com.example.viewmodel.WORDPRESS_BASE_URL
import com.example.utils.FormatUtils
import com.example.utils.ShiftDateTimeUtils

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.utils.LocationUtils
import com.example.utils.ErrorUtils
import com.example.ui.theme.*
import com.example.viewmodel.ApprovedCallInfo
import com.example.viewmodel.AppViewModel
import com.example.viewmodel.LoginState
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// --- UTILS ---
fun formatElapsedTime(timestamp: Timestamp?): String {
    if (timestamp == null) return "az önce"
    val diffMs = System.currentTimeMillis() - timestamp.toDate().time
    val diffSec = diffMs / 1000
    if (diffSec < 0) return "az önce"
    if (diffSec < 60) return "az önce"
    val diffMin = diffSec / 60
    if (diffMin < 60) return "$diffMin dk önce"
    val diffHrs = diffMin / 60
    if (diffHrs < 24) return "$diffHrs saat önce"
    val diffDays = diffHrs / 24
    return "$diffDays gün önce"
}

fun formatPendingCallTime(createdAt: Timestamp?, nowMs: Long): String {
    if (createdAt == null) return "az önce çağrıldı"
    val diffMs = nowMs - createdAt.toDate().time
    val diffSec = diffMs / 1000
    if (diffSec < 60) {
        return if (diffSec <= 0) "az önce çağrıldı" else "$diffSec sn önce çağrıldı"
    }
    val diffMin = diffSec / 60
    if (diffMin < 60) return "$diffMin dk önce çağrıldı"
    val diffHrs = diffMin / 60
    if (diffHrs < 24) return "$diffHrs saat önce çağrıldı"
    return "${diffHrs / 24} gün önce çağrıldı"
}

// --- SHARED COMPONENTS ---
@Composable
fun LiveApprovalBanner(info: ApprovedCallInfo?, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = info != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it })
    ) {
        if (info != null) {
            LaunchedEffect(info.timestamp) {
                delay(3000)
                onDismiss()
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("live_approval_banner"),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.4f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = StatusGreen,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${FormatUtils.formatMasaTitle(info.masaNo)} Onaylandı",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "${info.onaylayanAd} tarafından onaylandı.",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExitConfirmDialog(
    onDismiss: () -> Unit,
    onConfirmExit: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text(
                text = "Çıkış",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Uygulamadan çıkmak istediğinize emin misiniz?",
                color = TextSecondary,
                fontSize = 14.sp
            )
        },
        confirmButton = {
            OutlinedButton(
                onClick = {
                    ErrorUtils.safeUiAction(context) {
                        onConfirmExit()
                    }
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = StatusRed
                ),
                border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("confirm_exit_app_button")
            ) {
                Text("Çık", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    ErrorUtils.safeUiAction(context) {
                        onDismiss()
                    }
                },
                modifier = Modifier.testTag("cancel_exit_app_button")
            ) {
                Text("İptal", color = TextSecondary)
            }
        }
    )
}

// --- 5.1 LOGIN SCREEN ---
private data class GoldParticle(
    val xRatio: Float,
    val initialYRatio: Float,
    val speed: Float,
    val radiusDp: Float,
    val alpha: Float,
    val swayPhase: Float
)

private val loginGoldParticles = listOf(
    GoldParticle(0.08f, 0.05f, 0.8f, 2.8f, 0.26f, 0.1f),
    GoldParticle(0.16f, 0.42f, 1.1f, 3.0f, 0.24f, 0.8f),
    GoldParticle(0.22f, 0.85f, 0.7f, 2.4f, 0.18f, 2.1f),
    GoldParticle(0.32f, 0.15f, 0.9f, 1.8f, 0.10f, 1.4f),
    GoldParticle(0.41f, 0.65f, 0.8f, 1.6f, 0.08f, 3.2f),
    GoldParticle(0.48f, 0.28f, 1.0f, 1.8f, 0.09f, 0.5f),
    GoldParticle(0.55f, 0.90f, 0.6f, 1.8f, 0.10f, 1.9f),
    GoldParticle(0.62f, 0.48f, 0.9f, 1.6f, 0.08f, 2.7f),
    GoldParticle(0.68f, 0.10f, 0.8f, 1.8f, 0.11f, 0.3f),
    GoldParticle(0.78f, 0.75f, 1.1f, 2.8f, 0.24f, 1.1f),
    GoldParticle(0.85f, 0.35f, 0.8f, 3.0f, 0.26f, 2.4f),
    GoldParticle(0.92f, 0.58f, 1.0f, 2.6f, 0.24f, 0.9f),
    GoldParticle(0.12f, 0.72f, 0.9f, 3.2f, 0.27f, 1.7f),
    GoldParticle(0.36f, 0.93f, 1.0f, 2.0f, 0.12f, 2.9f),
    GoldParticle(0.58f, 0.08f, 0.8f, 2.2f, 0.14f, 0.6f),
    GoldParticle(0.66f, 0.82f, 1.1f, 2.4f, 0.18f, 1.2f),
    GoldParticle(0.76f, 0.22f, 0.7f, 3.0f, 0.26f, 2.3f),
    GoldParticle(0.88f, 0.02f, 0.9f, 2.6f, 0.22f, 3.0f)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val passwordFocusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()
    val loginState by viewModel.loginState.collectAsStateWithLifecycle()

    // Reduced motion accessibility check
    val isReducedMotion = remember(context) {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            ) == 0.0f
        } catch (_: Exception) {
            false
        }
    }

    // Refined, calm gold shimmer sweep for title:
    // 6.0s total cycle: 2.4s slow, silky light sweep across the letters + 3.6s tranquil resting pause
    val shimmerTransition = rememberInfiniteTransition(label = "titleShimmer")
    val shimmerProgress by shimmerTransition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 6000
                -0.3f at 0
                1.3f at 2400 using FastOutSlowInEasing
                1.3f at 6000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    var titleWidthPx by remember { mutableFloatStateOf(0f) }

    val titleBrush = remember(shimmerProgress, titleWidthPx, isReducedMotion) {
        if (isReducedMotion || titleWidthPx <= 0f || shimmerProgress >= 1.25f || shimmerProgress <= -0.25f) {
            Brush.linearGradient(
                colors = listOf(
                    BrandOffWhite,
                    BrandOffWhite
                )
            )
        } else {
            val currentX = titleWidthPx * shimmerProgress
            val band = titleWidthPx * 0.40f
            Brush.linearGradient(
                colors = listOf(
                    BrandOffWhite,
                    BrandOffWhite,
                    BrandGoldLight.copy(alpha = 0.85f),
                    BrandOffWhite,
                    BrandOffWhite
                ),
                start = Offset((currentX - band).coerceAtLeast(0f), 0f),
                end = Offset((currentX + band).coerceAtMost(titleWidthPx), 0f)
            )
        }
    }

    BackHandler {
        showExitDialog = true
    }

    if (showExitDialog) {
        ExitConfirmDialog(
            onDismiss = { showExitDialog = false },
            onConfirmExit = {
                showExitDialog = false
                (context as? Activity)?.finish()
            }
        )
    }

    // Soft entrance animation state
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    val entranceAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "entranceAlpha"
    )

    val entranceTranslationY by animateFloatAsState(
        targetValue = if (isVisible) 0f else 18f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "entranceTranslationY"
    )

    // GPU-friendly ambient animations: subtle forest beam sway & gold ember particles loop
    val infiniteTransition = rememberInfiniteTransition(label = "loginAmbient")

    val beamSway by infiniteTransition.animateFloat(
        initialValue = -0.15f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beamSway"
    )

    val particleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particleProgress"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BrandDeepForest)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            }
    ) {
        // GPU-Optimized Background Layers: Deep Forest Ambient Glow + Warm Gold Dust
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val w = size.width
            val h = size.height
            val maxDim = kotlin.math.hypot(w.toDouble(), h.toDouble()).toFloat()

            // Layer 1: Forest & Dark Gradient Atmosphere
            val beamCenterX = w * (0.5f + beamSway)
            val beamCenterY = h * 0.45f

            // Full-screen radial depth towards darker background
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BrandSecondaryForest.copy(alpha = 0.50f),
                        BrandDeepForest,
                        BrandDarkBg.copy(alpha = 0.85f)
                    ),
                    center = Offset(beamCenterX, beamCenterY),
                    radius = maxDim * 0.80f
                )
            )

            // Full-height diagonal linear gradient beam (subtle emerald-sage shimmer)
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        BrandGreenHover.copy(alpha = 0.15f),
                        BrandSecondaryForest.copy(alpha = 0.10f),
                        BrandDarkBg.copy(alpha = 0.05f)
                    ),
                    start = Offset(beamCenterX - w * 0.5f, 0f),
                    end = Offset(beamCenterX + w * 0.5f, h)
                )
            )

            // Layer 2: Floating Micro Gold Embers
            val totalHeightRange = h + 120.dp.toPx()

            loginGoldParticles.forEach { p ->
                val currentYProgress = (p.initialYRatio + particleProgress * p.speed) % 1.0f
                val yPos = currentYProgress * totalHeightRange - 60.dp.toPx()
                val swayPx = kotlin.math.sin((particleProgress * 2 * Math.PI + p.swayPhase).toDouble()).toFloat() * 12.dp.toPx()
                val xPos = p.xRatio * w + swayPx

                drawCircle(
                    color = BrandGold.copy(alpha = p.alpha * 0.65f),
                    radius = p.radiusDp.dp.toPx(),
                    center = Offset(xPos, yPos)
                )
            }
        }

        // Safe Area Foreground Container with dynamic keyboard accommodation
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val viewportHeight = maxHeight
            val density = LocalDensity.current
            val isImeVisible = WindowInsets.ime.getBottom(density) > 0

            // Estimated height of the centered form (~320dp) + footer (~36dp)
            val neededContentHeight = 356.dp
            val freeVerticalSpace = (viewportHeight - neededContentHeight).coerceAtLeast(0.dp)

            // When keyboard is visible or screen is compact: keep top spacing tight and stable.
            // When keyboard is closed: center the form comfortably in the viewport.
            val topSpacing = if (isImeVisible || viewportHeight < 560.dp) {
                (freeVerticalSpace * 0.30f).coerceIn(8.dp, 20.dp)
            } else {
                (freeVerticalSpace * 0.42f).coerceAtLeast(20.dp)
            }

            val bottomSpacing = if (isImeVisible || viewportHeight < 560.dp) {
                16.dp
            } else {
                (freeVerticalSpace * 0.58f).coerceAtLeast(16.dp)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(topSpacing))

                // Center Login Content Card with Entrance Animation
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 360.dp)
                        .graphicsLayer {
                            alpha = entranceAlpha
                            translationY = entranceTranslationY.dp.toPx()
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // App Context Title with calm gold light sweep
                    Text(
                        text = "Operasyon Yönetimi",
                        style = TextStyle(
                            brush = titleBrush,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp,
                            lineHeight = 36.sp,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier
                            .testTag("app_title")
                            .onSizeChanged { titleWidthPx = it.width.toFloat() }
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Subtitle with clear hierarchy
                    Text(
                        text = "Yönetim · Servis · Operasyon",
                        color = BrandTextMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 20.sp,
                        letterSpacing = 0.25.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                        // Username input
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it.lowercase().trim() },
                            label = { Text("Kullanıcı Adı") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("username_input"),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { passwordFocusRequester.requestFocus() }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandGold,
                                unfocusedBorderColor = BrandInputBorderUnfocused,
                                focusedLabelColor = BrandGoldLight,
                                unfocusedLabelColor = BrandTextMuted,
                                focusedLeadingIconColor = BrandGold,
                                unfocusedLeadingIconColor = BrandTextMuted,
                                focusedContainerColor = BrandGreenHover.copy(alpha = 0.50f),
                                unfocusedContainerColor = BrandSecondaryForest.copy(alpha = 0.40f),
                                focusedTextColor = BrandOffWhite,
                                unfocusedTextColor = BrandOffWhite,
                                cursorColor = BrandGold
                            ),
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null)
                            }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Password input
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Şifre") },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(passwordFocusRequester)
                                .testTag("password_input"),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    ErrorUtils.safeUiAction(context, "Giriş yapılamadı") {
                                        focusManager.clearFocus()
                                        if (loginState !is LoginState.Loading) {
                                            viewModel.login(username, password)
                                        }
                                    }
                                }
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandGold,
                                unfocusedBorderColor = BrandInputBorderUnfocused,
                                focusedLabelColor = BrandGoldLight,
                                unfocusedLabelColor = BrandTextMuted,
                                focusedLeadingIconColor = BrandGold,
                                unfocusedLeadingIconColor = BrandTextMuted,
                                focusedTrailingIconColor = BrandGold,
                                unfocusedTrailingIconColor = BrandTextMuted,
                                focusedContainerColor = BrandGreenHover.copy(alpha = 0.50f),
                                unfocusedContainerColor = BrandSecondaryForest.copy(alpha = 0.40f),
                                focusedTextColor = BrandOffWhite,
                                unfocusedTextColor = BrandOffWhite,
                                cursorColor = BrandGold
                            ),
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null)
                            },
                            trailingIcon = {
                                val icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                val accessibilityLabel = if (passwordVisible) "Şifreyi gizle" else "Şifreyi göster"
                                IconButton(
                                    onClick = {
                                        ErrorUtils.safeUiAction(context) {
                                            passwordVisible = !passwordVisible
                                        }
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = accessibilityLabel,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(22.dp))

                        // Primary Brand CTA Button
                        Button(
                            onClick = {
                                ErrorUtils.safeUiAction(context, "Giriş yapılamadı") {
                                    focusManager.clearFocus()
                                    viewModel.login(username, password)
                                }
                            },
                            enabled = loginState !is LoginState.Loading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("login_button")
                                .semantics {
                                    if (loginState is LoginState.Loading) {
                                        contentDescription = "Giriş yapılıyor, lütfen bekleyin"
                                    }
                                },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandSecondaryForest,
                                contentColor = BrandOffWhite,
                                disabledContainerColor = BrandSecondaryForest.copy(alpha = 0.50f),
                                disabledContentColor = BrandOffWhite.copy(alpha = 0.50f)
                            ),
                            border = BorderStroke(1.2.dp, BrandGold.copy(alpha = 0.85f)),
                            shape = RoundedCornerShape(12.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp)
                        ) {
                            if (loginState is LoginState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .semantics { contentDescription = "Giriş yapılıyor..." },
                                    color = BrandGold,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Text(
                                    text = "Giriş Yap",
                                    color = BrandOffWhite,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        // Error Text
                        if (loginState is LoginState.Error) {
                            Text(
                                text = (loginState as LoginState.Error).message,
                                color = ErrorColor,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(top = 10.dp)
                                    .testTag("login_error_text")
                            )
                        }
                }

                Spacer(modifier = Modifier.height(bottomSpacing))

                // Bottom Footer Branding Text
                Text(
                    text = "QRMENUOFFICIAL",
                    color = BrandTextMuted.copy(alpha = 0.60f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 2.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
    }
}

// --- 5.2 ADMIN MAIN SCREEN ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AdminMainScreen(
    viewModel: AppViewModel,
    onBranchClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val branches by viewModel.branches.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var showAnnouncementDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    BackHandler {
        showExitDialog = true
    }

    if (showExitDialog) {
        ExitConfirmDialog(
            onDismiss = { showExitDialog = false },
            onConfirmExit = {
                showExitDialog = false
                (context as? Activity)?.finish()
            }
        )
    }

    var branchName by remember { mutableStateOf("") }
    var branchSlug by remember { mutableStateOf("") }

    Scaffold(
        modifier = modifier.background(DarkBg),
        topBar = {
            TopAppBar(
                title = { Text("Şubeler", fontWeight = FontWeight.Bold, color = TextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg),
                actions = {
                    IconButton(
                        onClick = { showAnnouncementDialog = true },
                        modifier = Modifier.testTag("admin_announcement_button")
                    ) {
                        Icon(Icons.Default.Campaign, contentDescription = "Duyuru Gönder", tint = BrandGoldLight)
                    }
                    IconButton(
                        onClick = {
                            ErrorUtils.safeUiAction(context, "Çıkış yapılamadı") {
                                viewModel.logout()
                            }
                        },
                        modifier = Modifier.testTag("logout_button")
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Çıkış Yap", tint = TextSecondary)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    ErrorUtils.safeUiAction(context) {
                        branchName = ""
                        branchSlug = ""
                        showAddDialog = true
                    }
                },
                containerColor = BrandGreenHover,
                contentColor = BrandOffWhite,
                shape = CircleShape,
                modifier = Modifier
                    .testTag("add_branch_fab")
                    .border(1.dp, BrandGold.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Şube Ekle", tint = BrandOffWhite)
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(DarkBg)
        ) {
            if (branches.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Henüz şube eklenmemiş",
                        color = TextSecondary,
                        fontSize = 16.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(branches, key = { it.id }) { branch ->
                        val personnelCount = remember(allUsers, branch.id) {
                            allUsers.count { it.branchId == branch.id && it.rol != "admin" }
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("branch_card_${branch.id}"),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        ErrorUtils.safeUiAction(context) {
                                            onBranchClick(branch.id)
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(BrandGreenHover, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Store, contentDescription = null, tint = BrandOffWhite, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = branch.ad,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (branch.aktif) StatusGreen.copy(alpha = 0.15f) else DarkSurfaceSecondary,
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .border(
                                                    0.5.dp,
                                                    if (branch.aktif) StatusGreen.copy(alpha = 0.5f) else BorderColor.copy(alpha = 0.4f),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (branch.aktif) "Aktif" else "Pasif",
                                                color = if (branch.aktif) StatusGreen else TextSecondary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "@${branch.slug}",
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("•", color = TextSecondary.copy(alpha = 0.4f), fontSize = 11.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (branch.locationCheckEnabled) "GPS Açık" else "GPS Kapalı",
                                            color = if (branch.locationCheckEnabled) BrandGoldLight.copy(alpha = 0.9f) else TextSecondary.copy(alpha = 0.7f),
                                            fontSize = 11.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("•", color = TextSecondary.copy(alpha = 0.4f), fontSize = 11.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "$personnelCount Personel",
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.Default.ChevronRight, contentDescription = "Detay", tint = TextSecondary.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Branch Dialog (with double-tap prevention & uniqueness check)
    if (showAddDialog) {
        var isSubmittingBranch by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                if (!isSubmittingBranch) {
                    showAddDialog = false
                }
            },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Yeni Şube Oluştur",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Yeni şube için isim ve web slug belirleyin.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = branchName,
                        onValueChange = { branchName = it },
                        label = { Text("Şube Adı") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandGold,
                            focusedLabelColor = BrandGoldLight,
                            unfocusedBorderColor = BorderColor.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("dialog_branch_name_input")
                    )
                    OutlinedTextField(
                        value = branchSlug,
                        onValueChange = { branchSlug = it.lowercase().trim().replace(" ", "-") },
                        label = { Text("Slug (örn: merkez-sube)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandGold,
                            focusedLabelColor = BrandGoldLight,
                            unfocusedBorderColor = BorderColor.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("dialog_branch_slug_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = branchName.trim()
                        val slug = branchSlug.trim()
                        if (name.isEmpty() || slug.isEmpty()) {
                            Toast.makeText(context, "Lütfen tüm alanları doldurun.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSubmittingBranch = true
                        viewModel.addBranch(
                            ad = name,
                            slug = slug,
                            onSuccess = {
                                isSubmittingBranch = false
                                showAddDialog = false
                                Toast.makeText(context, "Şube başarıyla oluşturuldu.", Toast.LENGTH_SHORT).show()
                            },
                            onError = { err ->
                                isSubmittingBranch = false
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    enabled = !isSubmittingBranch,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreenHover, contentColor = BrandOffWhite),
                    border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.5f))
                ) {
                    if (isSubmittingBranch) {
                        CircularProgressIndicator(color = BrandGold, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Oluştur", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddDialog = false },
                    enabled = !isSubmittingBranch
                ) {
                    Text("Vazgeç", color = TextSecondary)
                }
            }
        )
    }

    if (showAnnouncementDialog) {
        SendAnnouncementDialog(
            viewModel = viewModel,
            branchId = "all",
            onDismiss = { showAnnouncementDialog = false }
        )
    }
}

// --- 5.2.1 BRANCH DETAIL SCREEN & ADMIN DIALOGS ---

@Composable
fun EditBranchDialog(
    branch: Branch,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember(branch) { mutableStateOf(branch.ad) }
    var slug by remember(branch) { mutableStateOf(branch.slug) }
    var isSaving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Şube Bilgilerini Düzenle", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Şube Adı") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandGold,
                        focusedLabelColor = BrandGoldLight,
                        unfocusedBorderColor = BorderColor.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("edit_branch_name_input")
                )
                OutlinedTextField(
                    value = slug,
                    onValueChange = { slug = it.lowercase().trim().replace(" ", "-") },
                    label = { Text("Slug (örn: merkez-sube)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandGold,
                        focusedLabelColor = BrandGoldLight,
                        unfocusedBorderColor = BorderColor.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("edit_branch_slug_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || slug.isBlank()) {
                        Toast.makeText(context, "Tüm alanları doldurun.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isSaving = true
                    viewModel.updateBranch(
                        branchId = branch.id,
                        ad = name.trim(),
                        slug = slug.trim(),
                        onSuccess = {
                            isSaving = false
                            Toast.makeText(context, "Şube güncellendi.", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        onError = { err ->
                            isSaving = false
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        }
                    )
                },
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreenHover, contentColor = BrandOffWhite),
                border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.6f))
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = BrandGold, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Kaydet", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç", color = TextSecondary)
            }
        }
    )
}

@Composable
fun ManualLocationDialog(
    initialLat: Double?,
    initialLng: Double?,
    initialRadius: Double?,
    onConfirm: (lat: Double, lng: Double, radius: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var latText by remember { mutableStateOf(initialLat?.let { String.format(java.util.Locale.US, "%.5f", it) } ?: "") }
    var lngText by remember { mutableStateOf(initialLng?.let { String.format(java.util.Locale.US, "%.5f", it) } ?: "") }
    var radiusText by remember { mutableStateOf((initialRadius ?: 150.0).toInt().toString()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = BrandGoldLight, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Koordinat Belirle", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Haritadan aldığınız koordinatları ve vardiya yarıçapını manuel olarak belirleyin.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it; errorText = null },
                    label = { Text("Enlem (Latitude, örn: 41.0082)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandGold,
                        focusedLabelColor = BrandGoldLight,
                        unfocusedBorderColor = BorderColor.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("manual_lat_input")
                )
                OutlinedTextField(
                    value = lngText,
                    onValueChange = { lngText = it; errorText = null },
                    label = { Text("Boylam (Longitude, örn: 28.9784)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandGold,
                        focusedLabelColor = BrandGoldLight,
                        unfocusedBorderColor = BorderColor.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("manual_lng_input")
                )
                OutlinedTextField(
                    value = radiusText,
                    onValueChange = { radiusText = it.filter { ch -> ch.isDigit() }; errorText = null },
                    label = { Text("Vardiya İzin Yarıçapı (Metre)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandGold,
                        focusedLabelColor = BrandGoldLight,
                        unfocusedBorderColor = BorderColor.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("manual_radius_input")
                )
                if (errorText != null) {
                    Text(errorText!!, color = StatusRed, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lat = latText.replace(',', '.').toDoubleOrNull()
                    val lng = lngText.replace(',', '.').toDoubleOrNull()
                    val radius = radiusText.toDoubleOrNull() ?: 150.0
                    if (lat == null || lat < -90.0 || lat > 90.0) {
                        errorText = "Geçerli bir enlem değeri girin (-90 ile +90 arası)"
                        return@Button
                    }
                    if (lng == null || lng < -180.0 || lng > 180.0) {
                        errorText = "Geçerli bir boylam değeri girin (-180 ile +180 arası)"
                        return@Button
                    }
                    onConfirm(lat, lng, radius)
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreenHover, contentColor = BrandOffWhite),
                border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.5f))
            ) {
                Text("Kaydet", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç", color = TextSecondary)
            }
        }
    )
}

@Composable
fun ConfirmDeleteBranchDialog(
    branch: Branch,
    userCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = StatusRed, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Şubeyi Sil?", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "\"${branch.ad}\" (@${branch.slug}) şubesini kalıcı olarak silmek istediğinize emin misiniz?",
                    color = TextPrimary,
                    fontSize = 14.sp
                )
                if (userCount > 0) {
                    Text(
                        text = "Dikkat: Bu şubeye bağlı $userCount personel bulunmaktadır. Bu işlem şubeyi ve bağlı tüm yönetim verilerini etkileyebilir.",
                        color = StatusAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = "Bu işlem geri alınamaz.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = StatusRed, contentColor = BrandOffWhite),
                modifier = Modifier.testTag("confirm_delete_branch_btn")
            ) {
                Text("Şubeyi Sil", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç", color = TextSecondary)
            }
        }
    )
}

@Composable
fun ConfirmRenewApiKeyDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Key, contentDescription = null, tint = BrandGoldLight, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("API Anahtarını Yenile?", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "API anahtarını yenilemek istediğinizden emin misiniz?",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Yenilendiğinde eski anahtar derhal geçersiz olacaktır. QR menü ve sunucu entegrasyonlarını yeni anahtarla güncellemeniz gerekecektir.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreenHover, contentColor = BrandOffWhite),
                border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.6f)),
                modifier = Modifier.testTag("confirm_renew_api_key_btn")
            ) {
                Text("Yenile", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç", color = TextSecondary)
            }
        }
    )
}

@Composable
fun EditUserDialog(
    user: User,
    onConfirm: (newName: String, newRole: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(user) { mutableStateOf(user.ad) }
    var role by remember(user) { mutableStateOf(user.rol.ifEmpty { "garson" }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Personel Bilgilerini Düzenle", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Ad Soyad") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandGold,
                        focusedLabelColor = BrandGoldLight,
                        unfocusedBorderColor = BorderColor.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("edit_user_name_input")
                )

                Text("Rol Seçimi:", color = TextSecondary, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { role = "mudur" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (role == "mudur") BrandGreenHover else DarkSurfaceSecondary,
                            contentColor = if (role == "mudur") BrandGoldLight else TextSecondary
                        ),
                        border = BorderStroke(1.dp, if (role == "mudur") BrandGold.copy(alpha = 0.6f) else BorderColor.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Müdür", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { role = "garson" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (role == "garson") BrandGreenHover else DarkSurfaceSecondary,
                            contentColor = if (role == "garson") BrandOffWhite else TextSecondary
                        ),
                        border = BorderStroke(1.dp, if (role == "garson") BrandGold.copy(alpha = 0.6f) else BorderColor.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Garson", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), role) },
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreenHover, contentColor = BrandOffWhite),
                border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.6f))
            ) {
                Text("Kaydet", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç", color = TextSecondary)
            }
        }
    )
}

@Composable
fun ConfirmDeleteUserDialog(
    user: User,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = StatusRed, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Kullanıcıyı Sil", color = TextPrimary, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "\"${user.ad.ifBlank { user.kullaniciAdi }}\" (@${user.kullaniciAdi}) kullanıcısını silmek istediğinize emin misiniz?",
                    color = TextPrimary,
                    fontSize = 14.sp
                )
                Text(
                    text = "Bu işlem kullanıcının hesabını ve sisteme erişimini tamamen sonlandırır. Geri alınamaz.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = StatusRed.copy(alpha = 0.85f), contentColor = BrandOffWhite)
            ) {
                Text("Evet, Sil", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç", color = TextSecondary)
            }
        }
    )
}

@Composable
fun UserPasswordDialog(
    user: User,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isNewPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var showConfirmModal by remember { mutableStateOf(false) }
    var showResetConfirmModal by remember { mutableStateOf(false) }

    val isMinLength = newPassword.length >= 6
    val isMatch = newPassword.isNotEmpty() && newPassword == confirmPassword
    val isValid = isMinLength && isMatch

    val roleTitle = when (user.rol) {
        "mudur" -> "Müdür Hesabı"
        "garson" -> "Garson Hesabı"
        else -> "Kullanıcı Hesabı"
    }

    val roleBadge = when (user.rol) {
        "mudur" -> "Müdür"
        "garson" -> "Garson"
        else -> "Personel"
    }

    if (showConfirmModal) {
        AlertDialog(
            onDismissRequest = { showConfirmModal = false },
            containerColor = DarkSurface,
            title = { Text("Şifreyi Güncelle", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "\"${user.ad.ifBlank { user.kullaniciAdi }}\" (@${user.kullaniciAdi}) hesabının şifresini güncellemek istediğinize emin misiniz?",
                    color = TextPrimary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmModal = false
                        isSubmitting = true
                        viewModel.updateUserPassword(
                            user = user,
                            newPassword = newPassword,
                            onSuccess = {
                                isSubmitting = false
                                Toast.makeText(context, "Şifre başarıyla güncellendi.", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            onError = { err ->
                                isSubmitting = false
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreenHover, contentColor = BrandOffWhite),
                    border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.6f))
                ) {
                    Text("Evet, Güncelle", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmModal = false }) {
                    Text("Geri", color = TextSecondary)
                }
            }
        )
    }

    if (showResetConfirmModal) {
        AlertDialog(
            onDismissRequest = { showResetConfirmModal = false },
            containerColor = DarkSurface,
            title = { Text("Şifre Sıfırlama Bağlantısı", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "\"${user.kullaniciAdi}@qrservis.local\" adresine şifre sıfırlama bağlantısı gönderilsin mi?",
                    color = TextPrimary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmModal = false
                        viewModel.sendPasswordResetForUser(
                            email = user.kullaniciAdi,
                            onSuccess = {
                                Toast.makeText(context, "Sıfırlama bağlantısı iletildi.", Toast.LENGTH_SHORT).show()
                            },
                            onError = { err ->
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreenHover, contentColor = BrandOffWhite),
                    border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.5f))
                ) {
                    Text("Gönder", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmModal = false }) {
                    Text("Geri", color = TextSecondary)
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Column {
                Text(roleTitle, color = TextSecondary, fontSize = 12.sp)
                Text("Şifre Yönetimi", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // User info header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkBg.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(BrandGreenHover, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (user.rol == "mudur") Icons.Default.AdminPanelSettings else Icons.Default.Person,
                            contentDescription = null,
                            tint = BrandGoldLight,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(user.ad.ifBlank { user.kullaniciAdi }, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("@${user.kullaniciAdi}", color = TextSecondary, fontSize = 12.sp)
                    }
                    Surface(
                        color = BrandGreenHover.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.5.dp, BrandGold.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = roleBadge,
                            color = BrandGoldLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                // Security notice banner (Strict security: Never show or store current password)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BrandGreenHover.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .border(0.5.dp, BrandGold.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = BrandGoldLight, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Güvenlik gereği mevcut şifre görüntülenemez.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                // New password input
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Yeni Şifre") },
                    singleLine = true,
                    visualTransformation = if (isNewPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isNewPasswordVisible = !isNewPasswordVisible }) {
                            Icon(
                                if (isNewPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (isNewPasswordVisible) "Gizle" else "Göster",
                                tint = TextSecondary
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandGold,
                        focusedLabelColor = BrandGoldLight,
                        unfocusedBorderColor = BorderColor.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("new_password_input")
                )

                // Confirm password input (Yeni Şifre Tekrar)
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Yeni Şifre Tekrar") },
                    singleLine = true,
                    visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                            Icon(
                                if (isConfirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (isConfirmPasswordVisible) "Gizle" else "Göster",
                                tint = TextSecondary
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (confirmPassword.isNotEmpty() && !isMatch) StatusRed else BrandGold,
                        focusedLabelColor = if (confirmPassword.isNotEmpty() && !isMatch) StatusRed else BrandGoldLight,
                        unfocusedBorderColor = if (confirmPassword.isNotEmpty() && !isMatch) StatusRed.copy(alpha = 0.7f) else BorderColor.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("confirm_password_input")
                )

                // Real-time validation hints
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (isMinLength) "✓ En az 6 karakter" else "• En az 6 karakter girilmeli",
                        color = if (isMinLength) StatusGreen else TextSecondary,
                        fontSize = 11.sp
                    )
                    if (confirmPassword.isNotEmpty()) {
                        Text(
                            text = if (isMatch) "✓ Şifreler uyuşuyor" else "✗ Şifreler uyuşmuyor",
                            color = if (isMatch) StatusGreen else StatusRed,
                            fontSize = 11.sp
                        )
                    }
                }

                // Password Reset link option
                TextButton(
                    onClick = { showResetConfirmModal = true },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.MailOutline, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextSecondary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sıfırlama Bağlantısı Gönder", color = TextSecondary, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValid) {
                        showConfirmModal = true
                    }
                },
                enabled = isValid && !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = BrandGreenHover, contentColor = BrandOffWhite),
                border = BorderStroke(1.dp, if (isValid) BrandGold.copy(alpha = 0.6f) else BorderColor.copy(alpha = 0.3f)),
                modifier = Modifier.testTag("submit_change_password_btn")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = BrandGold, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Şifreyi Güncelle", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç", color = TextSecondary)
            }
        }
    )
}

@Composable
fun ManagerPasswordDialog(
    user: User,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    UserPasswordDialog(user = user, viewModel = viewModel, onDismiss = onDismiss)
}

/**
 * Fully adaptive, responsive tab bar for branch management screens.
 * Perfectly balances all 4 tabs across all screen widths (320dp, 360dp, 390dp, 1366dp),
 * eliminates tab truncation and right-edge clipping, supports smooth auto-scrolling
 * to center active tabs on narrow viewports, and keeps the gold active indicator aligned.
 */
@Composable
fun AdaptiveBranchTabRow(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = remember {
        listOf(
            Triple("Kullanıcılar", Icons.Default.Group, 0),
            Triple("Personel", Icons.Default.Badge, 1),
            Triple("Duyurular", Icons.Default.Campaign, 2),
            Triple("Şube", Icons.Default.Store, 3)
        )
    }

    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurface)
    ) {
        val totalWidth = maxWidth
        val isWideScreen = totalWidth >= 640.dp

        // Tab width calculation:
        // On wide screens (>=640dp up to 1366dp): max 640dp row, centered on screen, tabs are 160dp each.
        // On standard mobile screens (300dp - 639dp): all 4 tabs share 100% of the screen width equally (totalWidth / 4).
        // Zero right-edge cut-off, zero half-visible tabs, clean 4-tab fit across 320dp, 360dp, 390dp.
        // On extreme narrow screens (<300dp): tabs have min 75dp width with smooth auto-scroll to active tab.
        val minTabWidth = 75.dp
        val needsScroll = totalWidth < (minTabWidth * tabs.size)

        val tabWidth = when {
            isWideScreen -> 160.dp
            needsScroll -> minTabWidth
            else -> totalWidth / tabs.size
        }

        val contentWidth = if (isWideScreen || needsScroll) (tabWidth * tabs.size) else totalWidth

        // Smoothly auto-align active tab into visible viewport when selected on scrollable screens
        LaunchedEffect(selectedTabIndex, needsScroll) {
            if (needsScroll) {
                val tabWidthPx = with(density) { tabWidth.toPx() }
                val containerWidthPx = with(density) { totalWidth.toPx() }
                val targetScroll = (selectedTabIndex * tabWidthPx) - (containerWidthPx / 2) + (tabWidthPx / 2)
                scrollState.animateScrollTo(targetScroll.toInt().coerceAtLeast(0))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (needsScroll) Modifier.horizontalScroll(scrollState) else Modifier),
            contentAlignment = if (isWideScreen) Alignment.Center else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .width(contentWidth)
                    .height(54.dp)
            ) {
                // Tab Items
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { (title, icon, index) ->
                        val isSelected = selectedTabIndex == index

                        Box(
                            modifier = Modifier
                                .width(tabWidth)
                                .fillMaxHeight()
                                .clickable { onTabSelected(index) }
                                .padding(horizontal = 2.dp)
                                .testTag("branch_tab_$index"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = title,
                                    modifier = Modifier.size(if (totalWidth < 340.dp) 17.dp else 19.dp),
                                    tint = if (isSelected) BrandGoldLight else TextSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = title,
                                    fontSize = when {
                                        totalWidth < 340.dp -> 10.sp
                                        totalWidth < 380.dp -> 11.sp
                                        else -> 12.sp
                                    },
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) BrandOffWhite else TextSecondary,
                                    maxLines = 1,
                                    softWrap = false,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Active Gold Indicator
                val animatedLeft by animateDpAsState(
                    targetValue = tabWidth * selectedTabIndex,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    label = "tab_indicator_left"
                )

                // Gold Line positioned cleanly under the active tab
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = animatedLeft)
                        .width(tabWidth)
                        .height(3.dp)
                        .padding(horizontal = if (isWideScreen) 16.dp else 6.dp)
                        .background(
                            color = BrandGold,
                            shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                        )
                )
            }
        }

        // Bottom subtle border divider
        HorizontalDivider(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = BorderColor.copy(alpha = 0.35f),
            thickness = 0.5.dp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchDetailScreen(
    viewModel: AppViewModel,
    branchId: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onBackClick()
    }

    val branches by viewModel.branches.collectAsStateWithLifecycle()
    val branchUsers by viewModel.branchUsers.collectAsStateWithLifecycle()
    val announcements by viewModel.announcements.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val branch = remember(branches, branchId) {
        branches.find { it.id == branchId }
    }

    var branchSection by remember { mutableIntStateOf(0) } // 0: Users, 1: Personel, 2: Duyurular, 3: Sube
    var userFilter by remember { mutableStateOf("all") } // all, mudur, garson, active, passive

    // Dialog states
    var showAddUserDialog by remember { mutableStateOf(false) }
    var selectedUserForEdit by remember { mutableStateOf<User?>(null) }
    var selectedUserForPassword by remember { mutableStateOf<User?>(null) }
    var userToDelete by remember { mutableStateOf<User?>(null) }
    var showSendAnnouncementDialog by remember { mutableStateOf(false) }
    var showEditBranchDialog by remember { mutableStateOf(false) }
    var showDeleteBranchDialog by remember { mutableStateOf(false) }
    var showRenewApiKeyDialog by remember { mutableStateOf(false) }
    var showManualLocationDialog by remember { mutableStateOf(false) }
    var showMapLocationDialog by remember { mutableStateOf(false) }

    val currentUserRole by viewModel.userRole.collectAsStateWithLifecycle()
    val currentUserBranchId by viewModel.userBranchId.collectAsStateWithLifecycle()
    val canEditLocation = currentUserRole == "admin" || (currentUserRole == "mudur" && (currentUserBranchId == branchId || currentUserBranchId.isBlank()))
    val coroutineScope = rememberCoroutineScope()

    // Add user inputs
    var userAd by remember { mutableStateOf("") }
    var userKullaniciAdi by remember { mutableStateOf("") }
    var userSifre by remember { mutableStateOf("") }
    var userRol by remember { mutableStateOf("mudur") }

    // Branch Settings states
    var isApiKeyVisible by remember { mutableStateOf(false) }

    LaunchedEffect(branchId) {
        viewModel.listenToBranchUsers(branchId)
        viewModel.startAnnouncementsListener(branchId)
    }

    DisposableEffect(branchId) {
        onDispose {
            viewModel.stopListeningToBranchUsers()
            viewModel.stopAnnouncementsListener()
        }
    }

    // Filtered users
    val filteredUsers = remember(branchUsers, userFilter) {
        when (userFilter) {
            "mudur" -> branchUsers.filter { it.rol == "mudur" }
            "garson" -> branchUsers.filter { it.rol == "garson" }
            "active" -> branchUsers.filter { it.aktif }
            "passive" -> branchUsers.filter { !it.aktif }
            else -> branchUsers
        }
    }

    Scaffold(
        modifier = modifier.background(DarkBg),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(branch?.ad ?: "Şube Detayı", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
                        branch?.slug?.let {
                            Text(it, color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg),
                navigationIcon = {
                    IconButton(onClick = {
                        ErrorUtils.safeUiAction(context) {
                            onBackClick()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = TextPrimary)
                    }
                }
            )
        },
        floatingActionButton = {
            if (branchSection == 0) {
                FloatingActionButton(
                    onClick = {
                        ErrorUtils.safeUiAction(context) {
                            userAd = ""
                            userKullaniciAdi = ""
                            userSifre = ""
                            userRol = "mudur"
                            showAddUserDialog = true
                        }
                    },
                    containerColor = BrandGreenHover,
                    contentColor = BrandOffWhite,
                    shape = CircleShape,
                    modifier = Modifier
                        .testTag("add_user_fab")
                        .border(1.dp, BrandGold.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Kullanıcı Ekle", tint = BrandOffWhite)
                }
            } else if (branchSection == 2) {
                FloatingActionButton(
                    onClick = {
                        showSendAnnouncementDialog = true
                    },
                    containerColor = BrandGreenHover,
                    contentColor = BrandOffWhite,
                    shape = CircleShape,
                    modifier = Modifier
                        .testTag("fab_send_announcement")
                        .border(1.dp, BrandGold.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.Campaign, contentDescription = "Duyuru Gönder", tint = BrandGoldLight)
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(DarkBg)
        ) {
            if (branch == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandGold)
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Navigation Tabs (Adaptive, responsive, no clipping on 320/360/390/1366dp)
                    AdaptiveBranchTabRow(
                        selectedTabIndex = branchSection,
                        onTabSelected = { branchSection = it }
                    )

                    // Tab Body
                    when (branchSection) {
                        0 -> {
                            // --- TAB 0: KULLANICILAR ---
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                // Filter chips
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val filters = listOf(
                                        "all" to "Tümü (${branchUsers.size})",
                                        "mudur" to "Müdürler (${branchUsers.count { it.rol == "mudur" }})",
                                        "garson" to "Garsonlar (${branchUsers.count { it.rol == "garson" }})",
                                        "active" to "Aktif (${branchUsers.count { it.aktif }})",
                                        "passive" to "Pasif (${branchUsers.count { !it.aktif }})"
                                    )
                                    filters.forEach { (key, label) ->
                                        val isSelected = userFilter == key
                                        Surface(
                                            modifier = Modifier.clickable { userFilter = key },
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (isSelected) BrandGreenHover else DarkSurfaceSecondary,
                                            border = BorderStroke(1.dp, if (isSelected) BrandGold.copy(alpha = 0.6f) else BorderColor.copy(alpha = 0.3f))
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isSelected) BrandOffWhite else TextSecondary,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                if (filteredUsers.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.PeopleOutline,
                                                contentDescription = null,
                                                tint = TextSecondary.copy(alpha = 0.5f),
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Bu filtreye uygun kullanıcı bulunamadı.", color = TextSecondary, fontSize = 14.sp)
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(filteredUsers, key = { it.uid }) { u ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("user_card_${u.uid}"),
                                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                                shape = RoundedCornerShape(14.dp),
                                                border = BorderStroke(0.5.dp, BorderColor.copy(alpha = 0.35f))
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(14.dp)
                                                ) {
                                                    // Top Row: Ad Soyad, Rol Rozeti, Durum Rozeti
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text(
                                                            text = u.ad.ifBlank { u.kullaniciAdi },
                                                            color = TextPrimary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 16.sp,
                                                            modifier = Modifier.weight(1f)
                                                        )

                                                        // Role Badge
                                                        Box(
                                                            modifier = Modifier
                                                                .background(
                                                                    if (u.rol == "mudur") BrandGold.copy(alpha = 0.15f) else BrandGreenHover,
                                                                    RoundedCornerShape(6.dp)
                                                                )
                                                                .border(0.5.dp, if (u.rol == "mudur") BrandGold.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(6.dp))
                                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                                        ) {
                                                            Text(
                                                                text = if (u.rol == "mudur") "Müdür" else "Garson",
                                                                color = if (u.rol == "mudur") BrandGoldLight else BrandOffWhite,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                        }

                                                        Spacer(modifier = Modifier.width(6.dp))

                                                        // Status Badge
                                                        Box(
                                                            modifier = Modifier
                                                                .background(
                                                                    if (u.aktif) StatusGreen.copy(alpha = 0.15f) else DarkSurfaceSecondary,
                                                                    RoundedCornerShape(6.dp)
                                                                )
                                                                .border(0.5.dp, if (u.aktif) StatusGreen.copy(alpha = 0.4f) else BorderColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                                        ) {
                                                            Text(
                                                                text = if (u.aktif) "Aktif" else "Pasif",
                                                                color = if (u.aktif) StatusGreen else TextSecondary,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(4.dp))

                                                    // Second Row: @kullaniciadi • branch
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text(
                                                            text = "@${u.kullaniciAdi}",
                                                            color = TextSecondary,
                                                            fontSize = 13.sp
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("•", color = TextSecondary.copy(alpha = 0.5f), fontSize = 12.sp)
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = branch.ad,
                                                            color = TextSecondary.copy(alpha = 0.8f),
                                                            fontSize = 12.sp
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.height(10.dp))
                                                    HorizontalDivider(color = BorderColor.copy(alpha = 0.25f), thickness = 0.5.dp)
                                                    Spacer(modifier = Modifier.height(6.dp))

                                                    // Actions Row: Aktif/Pasif toggle switch + Action buttons
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        // Toggle Switch
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.clickable {
                                                                viewModel.updateUserActiveStatus(
                                                                    uid = u.uid,
                                                                    aktif = !u.aktif,
                                                                    onSuccess = {
                                                                        Toast.makeText(context, if (!u.aktif) "Kullanıcı aktif yapıldı" else "Kullanıcı pasife alındı", Toast.LENGTH_SHORT).show()
                                                                    },
                                                                    onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                                                                )
                                                            }
                                                        ) {
                                                            Switch(
                                                                checked = u.aktif,
                                                                onCheckedChange = { newStatus ->
                                                                    viewModel.updateUserActiveStatus(
                                                                        uid = u.uid,
                                                                        aktif = newStatus,
                                                                        onSuccess = {
                                                                            Toast.makeText(context, if (newStatus) "Kullanıcı aktif yapıldı" else "Kullanıcı pasife alındı", Toast.LENGTH_SHORT).show()
                                                                        },
                                                                        onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                                                                    )
                                                                },
                                                                colors = SwitchDefaults.colors(
                                                                    checkedThumbColor = BrandOffWhite,
                                                                    checkedTrackColor = StatusGreen,
                                                                    uncheckedThumbColor = TextSecondary,
                                                                    uncheckedTrackColor = DarkSurfaceSecondary
                                                                ),
                                                                modifier = Modifier.testTag("user_active_switch_${u.uid}")
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = if (u.aktif) "Aktif" else "Pasif",
                                                                color = TextSecondary,
                                                                fontSize = 12.sp
                                                            )
                                                        }

                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            // Password management for both Manager and Garson
                                                            if (u.rol == "mudur" || u.rol == "garson") {
                                                                IconButton(
                                                                    onClick = {
                                                                        selectedUserForPassword = u
                                                                    },
                                                                    modifier = Modifier.testTag("user_password_btn_${u.uid}")
                                                                ) {
                                                                    Icon(Icons.Default.Key, contentDescription = "Şifre Yönetimi", tint = BrandGoldLight)
                                                                }
                                                            }

                                                            // Edit User
                                                            IconButton(
                                                                onClick = {
                                                                    selectedUserForEdit = u
                                                                },
                                                                modifier = Modifier.testTag("edit_user_btn_${u.uid}")
                                                            ) {
                                                                Icon(Icons.Default.Edit, contentDescription = "Düzenle", tint = TextSecondary)
                                                            }

                                                            // Delete User
                                                            IconButton(
                                                                onClick = {
                                                                    userToDelete = u
                                                                },
                                                                modifier = Modifier.testTag("delete_user_button_${u.uid}")
                                                            ) {
                                                                Icon(Icons.Default.Delete, contentDescription = "Sil", tint = StatusRed.copy(alpha = 0.85f))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        1 -> {
                            // --- TAB 1: PERSONEL CANLI DURUMU ---
                            PersonelTab(viewModel = viewModel, targetBranchId = branch.id)
                        }
                        2 -> {
                            // --- TAB 2: DUYURULAR ---
                            val branchAnnouncements = remember(announcements, branch.id) {
                                announcements.filter { it.branchId == branch.id || it.branchId == "all" }
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                // Top Action Banner
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showSendAnnouncementDialog = true },
                                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .background(BrandGreenHover, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Campaign, contentDescription = null, tint = BrandGoldLight, modifier = Modifier.size(20.dp))
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Yeni Personel Duyurusu", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Text("Personellere operasyonel bilgi iletin", color = TextSecondary, fontSize = 12.sp)
                                        }
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "Duyuru Geçmişi (${branchAnnouncements.size})",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                if (branchAnnouncements.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.Campaign,
                                                contentDescription = null,
                                                tint = TextSecondary.copy(alpha = 0.4f),
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Henüz duyuru gönderilmemiş.", color = TextSecondary, fontSize = 14.sp)
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(branchAnnouncements, key = { it.id }) { a ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(0.5.dp, BorderColor.copy(alpha = 0.35f))
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(14.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text(
                                                            text = a.title,
                                                            color = TextPrimary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 15.sp,
                                                            modifier = Modifier.weight(1f)
                                                        )

                                                        // Target badge
                                                        val targetText = when (a.targetType) {
                                                            "all_staff" -> "Tüm Personel (Tüm Şubeler)"
                                                            "all" -> if (a.branchId == "all") "Tüm Şubeler" else "Tüm Garsonlar"
                                                            "branch" -> if (a.targetBranchName.isNotEmpty()) a.targetBranchName else "Şube"
                                                            "user" -> a.targetUserName.ifBlank { "Belirli Personel" }
                                                            else -> "Tüm Personel"
                                                        }
                                                        Box(
                                                            modifier = Modifier
                                                                .background(BrandGreenHover.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(targetText, color = BrandGoldLight, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(a.message, color = BrandOffWhite, fontSize = 13.sp)

                                                    Spacer(modifier = Modifier.height(10.dp))
                                                    HorizontalDivider(color = BorderColor.copy(alpha = 0.25f), thickness = 0.5.dp)
                                                    Spacer(modifier = Modifier.height(8.dp))

                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        // Read Status
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            if (a.targetType == "user") {
                                                                val isRead = a.targetUserUid.isNotEmpty() && a.readUids.contains(a.targetUserUid)
                                                                Icon(
                                                                    imageVector = if (isRead) Icons.Default.CheckCircle else Icons.Default.Schedule,
                                                                    contentDescription = null,
                                                                    tint = if (isRead) StatusGreen else StatusAmber,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text(
                                                                    text = if (isRead) "Okundu" else "Henüz Okunmadı",
                                                                    color = if (isRead) StatusGreen else StatusAmber,
                                                                    fontSize = 12.sp
                                                                )
                                                            } else {
                                                                Icon(
                                                                    imageVector = Icons.Default.Visibility,
                                                                    contentDescription = null,
                                                                    tint = BrandTextMuted,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text(
                                                                    text = "${a.readUids.size} kişi okudu",
                                                                    color = BrandTextMuted,
                                                                    fontSize = 12.sp
                                                                )
                                                            }
                                                        }

                                                        // Date & Delete action
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            val dateStr = a.createdAt?.toDate()?.let {
                                                                java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(it)
                                                            } ?: ""
                                                            Text(dateStr, color = TextSecondary, fontSize = 11.sp)
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            IconButton(
                                                                onClick = {
                                                                    viewModel.deleteAnnouncement(
                                                                        announcementId = a.id,
                                                                        onSuccess = { Toast.makeText(context, "Duyuru silindi.", Toast.LENGTH_SHORT).show() },
                                                                        onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                                                                    )
                                                                },
                                                                modifier = Modifier.size(28.dp)
                                                            ) {
                                                                Icon(Icons.Default.Delete, contentDescription = "Sil", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        3 -> {
                            // --- TAB 3: ŞUBE ---
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                // 1. ŞUBE BİLGİLERİ
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(0.5.dp, BorderColor.copy(alpha = 0.35f))
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("Şube Bilgisi", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                Text("Şube adı ve web slug bağlantısı", color = TextSecondary, fontSize = 12.sp)
                                            }
                                            IconButton(onClick = { showEditBranchDialog = true }) {
                                                Icon(Icons.Default.Edit, contentDescription = "Düzenle", tint = BrandGoldLight)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        HorizontalDivider(color = BorderColor.copy(alpha = 0.3f), thickness = 0.5.dp)
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Şube Adı:", color = TextSecondary, fontSize = 13.sp)
                                            Text(branch.ad, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Şube Slug:", color = TextSecondary, fontSize = 13.sp)
                                            Text("@${branch.slug}", color = TealAccent, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                        }
                                    }
                                }

                                // 2. KONUM DOĞRULAMA & GPS
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(0.5.dp, BorderColor.copy(alpha = 0.35f))
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = BrandGoldLight, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Konum Doğrulama & GPS", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "Garsonların vardiyaya yalnızca şube sınırları içerisinde başlayabilmesini sağlar.",
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Switch row (GPS Kısıtı Açık)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(DarkBg.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("GPS Kısıtı Açık", color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                                Text(
                                                    if (branch.locationCheckEnabled) "Vardiya açılışında mesafe kontrol edilir" else "Mesafe kontrolü devre dışı",
                                                    color = TextSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            Switch(
                                                checked = branch.locationCheckEnabled,
                                                enabled = canEditLocation,
                                                onCheckedChange = { newValue ->
                                                    if (canEditLocation) {
                                                        viewModel.updateBranchLocationCheck(
                                                            branchId = branch.id,
                                                            enabled = newValue,
                                                            onSuccess = { Toast.makeText(context, if (newValue) "GPS kontrolü açıldı" else "GPS kontrolü kapatıldı", Toast.LENGTH_SHORT).show() },
                                                            onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                                                        )
                                                    }
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = BrandOffWhite,
                                                    checkedTrackColor = StatusGreen,
                                                    uncheckedThumbColor = TextSecondary,
                                                    uncheckedTrackColor = DarkSurfaceSecondary
                                                ),
                                                modifier = Modifier.testTag("detail_branch_location_switch")
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        // ŞUBE KONUMU & REVERSE GEOCODED ADRES GÖSTERİM KARTI
                                        BranchLocationAddressCard(branch = branch)

                                        Spacer(modifier = Modifier.height(14.dp))

                                        var radiusInput by remember(branch.radius) {
                                            mutableStateOf((branch.radius ?: 150.0).toInt().toString())
                                        }

                                        if (canEditLocation) {
                                            val locPermissionLauncher = rememberLauncherForActivityResult(
                                                ActivityResultContracts.RequestMultiplePermissions()
                                            ) { permissions ->
                                                val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                                                val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
                                                if (fineGranted || coarseGranted) {
                                                    val radiusVal = radiusInput.toDoubleOrNull() ?: (branch.radius ?: 150.0)
                                                    LocationUtils.getCurrentLocation(
                                                        context = context,
                                                        onLocationObtained = { loc ->
                                                            coroutineScope.launch {
                                                                val resolved = try {
                                                                    LocationUtils.getAddressFromCoordinates(context, loc.latitude, loc.longitude)
                                                                } catch (e: Exception) { null }
                                                                viewModel.updateBranchLocation(
                                                                    branchId = branch.id,
                                                                    lat = loc.latitude,
                                                                    lng = loc.longitude,
                                                                    radius = radiusVal,
                                                                    address = resolved?.fullAddress,
                                                                    onSuccess = { Toast.makeText(context, "Şube konumu GPS ile güncellendi.", Toast.LENGTH_SHORT).show() },
                                                                    onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
                                                                )
                                                            }
                                                        },
                                                        onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
                                                    )
                                                } else {
                                                    Toast.makeText(context, "Konum izni verilmedi", Toast.LENGTH_SHORT).show()
                                                }
                                            }

                                            var isLocatingGpsBranch by remember { mutableStateOf(false) }

                                            // Main GPS Actions: 1. Haritadan Konum Seç, 2. Şu Anki Konumumu Kullan, 3. Manuel Giriş
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // 1. [ Haritadan Konum Seç ] (Primary Action)
                                                Button(
                                                    onClick = { showMapLocationDialog = true },
                                                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreenHover, contentColor = BrandOffWhite),
                                                    border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.7f)),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pick_from_map_button")
                                                ) {
                                                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp), tint = BrandGoldLight)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Haritadan Konum Seç", color = BrandOffWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                }

                                                // 2. [ Şu Anki Konumumu Kullan ] (Real Device GPS)
                                                OutlinedButton(
                                                    onClick = {
                                                        ErrorUtils.safeUiAction(context, "Konum ayarlanamadı") {
                                                            val radiusVal = radiusInput.toDoubleOrNull() ?: (branch.radius ?: 150.0)
                                                            if (LocationUtils.hasLocationPermission(context)) {
                                                                isLocatingGpsBranch = true
                                                                LocationUtils.getCurrentLocation(
                                                                    context = context,
                                                                    onLocationObtained = { loc ->
                                                                        isLocatingGpsBranch = false
                                                                        coroutineScope.launch {
                                                                            val resolved = try {
                                                                                LocationUtils.getAddressFromCoordinates(context, loc.latitude, loc.longitude)
                                                                            } catch (e: Exception) { null }
                                                                            viewModel.updateBranchLocation(
                                                                                branchId = branch.id,
                                                                                lat = loc.latitude,
                                                                                lng = loc.longitude,
                                                                                radius = radiusVal,
                                                                                address = resolved?.fullAddress,
                                                                                locationCheckEnabled = true,
                                                                                onSuccess = { Toast.makeText(context, "Şube konumu gerçek cihaz GPS'i ile güncellendi.", Toast.LENGTH_SHORT).show() },
                                                                                onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
                                                                            )
                                                                        }
                                                                    },
                                                                    onError = { err ->
                                                                        isLocatingGpsBranch = false
                                                                        val msg = when (err) {
                                                                            "LOCATION_DISABLED" -> "Cihazın GPS/konum servisi kapalı. Lütfen ayarlardan açın."
                                                                            "PERMISSION_DENIED" -> "Konum izni verilmedi."
                                                                            else -> err
                                                                        }
                                                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
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
                                                        }
                                                    },
                                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkSurfaceSecondary.copy(alpha = 0.35f)),
                                                    border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.4f)),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("set_branch_location_button")
                                                ) {
                                                    if (isLocatingGpsBranch) {
                                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = BrandGold, strokeWidth = 2.dp)
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("Gerçek GPS Konumu Alınıyor...", fontWeight = FontWeight.Medium, fontSize = 12.sp, color = TextPrimary)
                                                    } else {
                                                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp), tint = BrandGoldLight)
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("Şu Anki Konumumu Kullan", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(14.dp))

                                            // Secondary Settings: Radius + Manuel Giriş
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                OutlinedTextField(
                                                    value = radiusInput,
                                                    onValueChange = { radiusInput = it.filter { ch -> ch.isDigit() } },
                                                    label = { Text("Vardiya İzin Yarıçapı (m)") },
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = BrandGold,
                                                        focusedLabelColor = BrandGoldLight,
                                                        unfocusedBorderColor = BorderColor.copy(alpha = 0.5f)
                                                    ),
                                                    modifier = Modifier.weight(1f).testTag("radius_input")
                                                )
                                                Button(
                                                    onClick = {
                                                        val rVal = radiusInput.toDoubleOrNull() ?: (branch.radius ?: 150.0)
                                                        if (branch.lat != null && branch.lng != null) {
                                                            viewModel.updateBranchLocation(
                                                                branchId = branch.id,
                                                                lat = branch.lat,
                                                                lng = branch.lng,
                                                                radius = rVal,
                                                                address = branch.address,
                                                                onSuccess = { Toast.makeText(context, "Yarıçap güncellendi.", Toast.LENGTH_SHORT).show() },
                                                                onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                                                            )
                                                        } else {
                                                            Toast.makeText(context, "Lütfen önce şube konumu belirleyin.", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreenHover, contentColor = BrandOffWhite),
                                                    border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.4f)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.height(54.dp)
                                                ) {
                                                    Text("Kaydet", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Manuel koordinat girişi ikincil seçenek
                                            OutlinedButton(
                                                onClick = { showManualLocationDialog = true },
                                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(0.7.dp, BorderColor.copy(alpha = 0.5f))
                                            ) {
                                                Icon(Icons.Default.EditLocation, contentDescription = null, modifier = Modifier.size(15.dp), tint = TextSecondary)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Manuel Koordinat Girişi (İkincil)", color = TextSecondary, fontSize = 12.sp)
                                            }
                                        } else {
                                            // Garson or Unauthorized Notice
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(DarkBg.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                    .border(0.5.dp, StatusAmber.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Lock,
                                                    contentDescription = null,
                                                    tint = StatusAmber,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = if (currentUserRole == "garson") {
                                                        "Garson hesaplarının şube GPS konumunu değiştirme yetkisi yoktur."
                                                    } else {
                                                        "Bu şubenin konumunu yalnızca yetkili Şube Müdürü veya Admin değiştirebilir."
                                                    },
                                                    color = TextSecondary,
                                                    fontSize = 12.sp,
                                                    lineHeight = 16.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                // 3. API ANAHTARI
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(0.5.dp, BorderColor.copy(alpha = 0.35f))
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Key, contentDescription = null, tint = BrandGoldLight, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("API Anahtarı", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "QR menü entegrasyonu ve sunucu iletişimi için kullanılan gizli güvenlik anahtarı.",
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(DarkBg.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val maskedKey = remember(branch.apiKey, isApiKeyVisible) {
                                                if (isApiKeyVisible) branch.apiKey
                                                else "•".repeat(maxOf(16, branch.apiKey.length.coerceAtMost(24)))
                                            }

                                            Text(
                                                text = maskedKey,
                                                color = if (isApiKeyVisible) TealAccent else TextSecondary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f).testTag("api_key_text")
                                            )

                                            IconButton(
                                                onClick = { isApiKeyVisible = !isApiKeyVisible },
                                                modifier = Modifier.size(32.dp).testTag("toggle_api_key_visibility")
                                            ) {
                                                Icon(
                                                    if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = if (isApiKeyVisible) "Gizle" else "Göster",
                                                    tint = TextSecondary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(4.dp))

                                            IconButton(
                                                onClick = {
                                                    ErrorUtils.safeUiAction(context, "API Anahtarı kopyalanamadı") {
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        val clip = ClipData.newPlainText("API Key", branch.apiKey)
                                                        clipboard.setPrimaryClip(clip)
                                                        Toast.makeText(context, "API Anahtarı kopyalandı", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp).testTag("copy_api_key_button")
                                            ) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = "Kopyala", tint = TealPrimary, modifier = Modifier.size(18.dp))
                                            }

                                            Spacer(modifier = Modifier.width(4.dp))

                                            IconButton(
                                                onClick = { showRenewApiKeyDialog = true },
                                                modifier = Modifier.size(32.dp).testTag("renew_api_key_button")
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = "Yenile", tint = BrandGoldLight, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }

                                // 4. ŞUBE DURUMU
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(0.5.dp, BorderColor.copy(alpha = 0.35f))
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Şube Durumu", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = if (branch.aktif) "Şube aktif, operasyon ve servisler açık." else "Şube pasif, işlemler geçici olarak durduruldu.",
                                                    color = TextSecondary,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            Switch(
                                                checked = branch.aktif,
                                                onCheckedChange = { newStatus ->
                                                    viewModel.updateBranchActiveStatus(
                                                        branchId = branch.id,
                                                        aktif = newStatus,
                                                        onSuccess = {
                                                            Toast.makeText(context, if (newStatus) "Şube aktif edildi" else "Şube pasife alındı", Toast.LENGTH_SHORT).show()
                                                        },
                                                        onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                                                    )
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = BrandOffWhite,
                                                    checkedTrackColor = StatusGreen,
                                                    uncheckedThumbColor = TextSecondary,
                                                    uncheckedTrackColor = DarkSurfaceSecondary
                                                ),
                                                modifier = Modifier.testTag("branch_active_switch")
                                            )
                                        }
                                    }
                                }

                                // 5. TEHLİKELİ BÖLGE — ŞUBEYİ SİL
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.4f))
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = StatusRed, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Tehlikeli Bölge", color = StatusRed, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Bu işlem şubeyi ve şubeye bağlı tüm yönetim verilerini kalıcı olarak silecektir. Bu işlem geri alınamaz.",
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Button(
                                            onClick = { showDeleteBranchDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = StatusRed, contentColor = BrandOffWhite),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("delete_branch_danger_button")
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Şubeyi Sil", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Branch Dialog
    if (showEditBranchDialog && branch != null) {
        EditBranchDialog(
            branch = branch,
            viewModel = viewModel,
            onDismiss = { showEditBranchDialog = false }
        )
    }

    // User Password Management Dialog (Manager & Garson)
    selectedUserForPassword?.let { u ->
        UserPasswordDialog(
            user = u,
            viewModel = viewModel,
            onDismiss = { selectedUserForPassword = null }
        )
    }

    // Edit User Dialog
    selectedUserForEdit?.let { u ->
        EditUserDialog(
            user = u,
            onConfirm = { newName, newRole ->
                viewModel.updateUserDetails(
                    uid = u.uid,
                    ad = newName,
                    rol = newRole,
                    onSuccess = {
                        selectedUserForEdit = null
                        Toast.makeText(context, "Kullanıcı güncellendi.", Toast.LENGTH_SHORT).show()
                    },
                    onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                )
            },
            onDismiss = { selectedUserForEdit = null }
        )
    }

    // Confirm Delete User Dialog
    userToDelete?.let { u ->
        ConfirmDeleteUserDialog(
            user = u,
            onConfirm = {
                viewModel.deleteUser(
                    uid = u.uid,
                    onSuccess = {
                        userToDelete = null
                        Toast.makeText(context, "Kullanıcı silindi.", Toast.LENGTH_SHORT).show()
                    },
                    onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                )
            },
            onDismiss = { userToDelete = null }
        )
    }

    // Send Announcement Dialog (from Duyurular tab)
    if (showSendAnnouncementDialog && branch != null) {
        SendAnnouncementDialog(
            viewModel = viewModel,
            branchId = branch.id,
            onDismiss = { showSendAnnouncementDialog = false }
        )
    }

    // Add User Dialog
    if (showAddUserDialog) {
        AlertDialog(
            onDismissRequest = {
                ErrorUtils.safeUiAction(context) {
                    showAddUserDialog = false
                }
            },
            containerColor = DarkSurface,
            title = { Text("Kullanıcı Ekle", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Role Segmented selection (Müdür / Garson)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                ErrorUtils.safeUiAction(context) {
                                    userRol = "mudur"
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (userRol == "mudur") BrandGreenHover else DarkSurface,
                                contentColor = if (userRol == "mudur") BrandOffWhite else TextSecondary
                            ),
                            border = BorderStroke(1.dp, if (userRol == "mudur") BrandGold.copy(alpha = 0.6f) else BorderColor),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Müdür", fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = {
                                ErrorUtils.safeUiAction(context) {
                                    userRol = "garson"
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (userRol == "garson") BrandGreenHover else DarkSurface,
                                contentColor = if (userRol == "garson") BrandOffWhite else TextSecondary
                            ),
                            border = BorderStroke(1.dp, if (userRol == "garson") BrandGold.copy(alpha = 0.6f) else BorderColor),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Garson", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    OutlinedTextField(
                        value = userAd,
                        onValueChange = { userAd = it },
                        label = { Text("Ad Soyad") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandGold,
                            focusedLabelColor = BrandGoldLight,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("dialog_user_ad_input")
                    )

                    OutlinedTextField(
                        value = userKullaniciAdi,
                        onValueChange = { userKullaniciAdi = it.lowercase().trim() },
                        label = { Text("Kullanıcı Adı") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandGold,
                            focusedLabelColor = BrandGoldLight,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("dialog_user_username_input")
                    )

                    OutlinedTextField(
                        value = userSifre,
                        onValueChange = { userSifre = it },
                        label = { Text("Şifre") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandGold,
                            focusedLabelColor = BrandGoldLight,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("dialog_user_password_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        ErrorUtils.safeUiAction(context, "Kullanıcı eklenemedi") {
                            val name = userAd.trim()
                            val username = userKullaniciAdi.trim()
                            val password = userSifre.trim()
                            if (name.isEmpty() || username.isEmpty() || password.isEmpty()) {
                                Toast.makeText(context, "Lütfen tüm alanları doldurun.", Toast.LENGTH_SHORT).show()
                                return@safeUiAction
                            }
                            viewModel.addUser(
                                branchId = branchId,
                                ad = name,
                                kullaniciAdi = username,
                                sifre = password,
                                rol = userRol,
                                onSuccess = {
                                    showAddUserDialog = false
                                    Toast.makeText(context, "Kullanıcı başarıyla oluşturuldu.", Toast.LENGTH_SHORT).show()
                                },
                                onError = {
                                    Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreenHover, contentColor = BrandOffWhite),
                    border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.5f))
                ) {
                    Text("Oluştur", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    ErrorUtils.safeUiAction(context) {
                        showAddUserDialog = false
                    }
                }) {
                    Text("Vazgeç", color = TextSecondary)
                }
            }
        )
    }

    // Confirm Delete Branch Dialog
    if (showDeleteBranchDialog && branch != null) {
        ConfirmDeleteBranchDialog(
            branch = branch,
            userCount = branchUsers.size,
            onConfirm = {
                viewModel.deleteBranch(
                    branchId = branch.id,
                    onSuccess = {
                        showDeleteBranchDialog = false
                        Toast.makeText(context, "Şube başarıyla silindi.", Toast.LENGTH_SHORT).show()
                        onBackClick()
                    },
                    onError = { err ->
                        showDeleteBranchDialog = false
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                )
            },
            onDismiss = { showDeleteBranchDialog = false }
        )
    }

    // Confirm Renew API Key Dialog
    if (showRenewApiKeyDialog && branch != null) {
        ConfirmRenewApiKeyDialog(
            onConfirm = {
                viewModel.renewApiKey(
                    branchId = branch.id,
                    onSuccess = {
                        showRenewApiKeyDialog = false
                        Toast.makeText(context, "API anahtarı yenilendi.", Toast.LENGTH_SHORT).show()
                    },
                    onError = { err ->
                        showRenewApiKeyDialog = false
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                )
            },
            onDismiss = { showRenewApiKeyDialog = false }
        )
    }

    // Map Location Picker Dialog
    if (showMapLocationDialog && branch != null) {
        MapLocationPickerDialog(
            initialLat = branch.lat,
            initialLng = branch.lng,
            onConfirm = { lat, lng, address ->
                val rVal = branch.radius ?: 150.0
                viewModel.updateBranchLocation(
                    branchId = branch.id,
                    lat = lat,
                    lng = lng,
                    radius = rVal,
                    address = address,
                    locationCheckEnabled = true,
                    onSuccess = {
                        showMapLocationDialog = false
                        Toast.makeText(context, "Şube konumu güncellendi.", Toast.LENGTH_SHORT).show()
                    },
                    onError = { err ->
                        showMapLocationDialog = false
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                )
            },
            onDismiss = { showMapLocationDialog = false }
        )
    }

    // Manual Location Dialog
    if (showManualLocationDialog && branch != null) {
        ManualLocationDialog(
            initialLat = branch.lat,
            initialLng = branch.lng,
            initialRadius = branch.radius,
            onConfirm = { lat, lng, radius ->
                coroutineScope.launch {
                    val resolved = try {
                        LocationUtils.getAddressFromCoordinates(context, lat, lng)
                    } catch (e: Exception) { null }
                    viewModel.updateBranchLocation(
                        branchId = branch.id,
                        lat = lat,
                        lng = lng,
                        radius = radius,
                        address = resolved?.fullAddress,
                        onSuccess = {
                            showManualLocationDialog = false
                            Toast.makeText(context, "Konum güncellendi.", Toast.LENGTH_SHORT).show()
                        },
                        onError = { err ->
                            showManualLocationDialog = false
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            },
            onDismiss = { showManualLocationDialog = false }
        )
    }
}

@Composable
fun SetBranchLocationDialog(
    viewModel: AppViewModel,
    branchId: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentBranch by viewModel.currentBranch.collectAsStateWithLifecycle()
    val currentUserRole by viewModel.userRole.collectAsStateWithLifecycle()
    val currentUserBranchId by viewModel.userBranchId.collectAsStateWithLifecycle()
    val isAuthorized = currentUserRole == "admin" || (currentUserRole == "mudur" && (currentUserBranchId == branchId || currentUserBranchId.isBlank()))
    val coroutineScope = rememberCoroutineScope()

    var showMapPicker by remember { mutableStateOf(false) }
    var showManualInput by remember { mutableStateOf(false) }
    var radiusInput by remember(currentBranch?.radius) {
        mutableStateOf((currentBranch?.radius ?: 150.0).toInt().toString())
    }

    val locPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            val radiusVal = radiusInput.toDoubleOrNull() ?: 150.0
            LocationUtils.getCurrentLocation(
                context = context,
                onLocationObtained = { loc ->
                    coroutineScope.launch {
                        val resolved = try {
                            LocationUtils.getAddressFromCoordinates(context, loc.latitude, loc.longitude)
                        } catch (e: Exception) { null }
                        viewModel.updateBranchLocation(
                            branchId = branchId,
                            lat = loc.latitude,
                            lng = loc.longitude,
                            radius = radiusVal,
                            address = resolved?.fullAddress,
                            onSuccess = {
                                Toast.makeText(context, "Konum güncellendi", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                            onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
                        )
                    }
                },
                onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
            )
        } else {
            Toast.makeText(context, "Konum izni verilmedi", Toast.LENGTH_SHORT).show()
        }
    }

    if (showMapPicker) {
        MapLocationPickerDialog(
            initialLat = currentBranch?.lat,
            initialLng = currentBranch?.lng,
            onConfirm = { lat, lng, address ->
                val radiusVal = radiusInput.toDoubleOrNull() ?: 150.0
                viewModel.updateBranchLocation(
                    branchId = branchId,
                    lat = lat,
                    lng = lng,
                    radius = radiusVal,
                    address = address,
                    locationCheckEnabled = true,
                    onSuccess = {
                        showMapPicker = false
                        Toast.makeText(context, "Şube konumu güncellendi", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    onError = { err ->
                        showMapPicker = false
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                )
            },
            onDismiss = { showMapPicker = false }
        )
    }

    if (showManualInput) {
        ManualLocationDialog(
            initialLat = currentBranch?.lat,
            initialLng = currentBranch?.lng,
            initialRadius = currentBranch?.radius,
            onConfirm = { lat, lng, radius ->
                coroutineScope.launch {
                    val resolved = try {
                        LocationUtils.getAddressFromCoordinates(context, lat, lng)
                    } catch (e: Exception) { null }
                    viewModel.updateBranchLocation(
                        branchId = branchId,
                        lat = lat,
                        lng = lng,
                        radius = radius,
                        address = resolved?.fullAddress,
                        onSuccess = {
                            showManualInput = false
                            Toast.makeText(context, "Konum kaydedildi", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        onError = { err ->
                            showManualInput = false
                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            },
            onDismiss = { showManualInput = false }
        )
    }

    AlertDialog(
        onDismissRequest = {
            ErrorUtils.safeUiAction(context) {
                onDismiss()
            }
        },
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = BrandGoldLight)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Şube Konumu & GPS Ayarı", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentBranch != null) {
                    BranchLocationAddressCard(branch = currentBranch!!)
                }

                if (isAuthorized) {
                    OutlinedTextField(
                        value = radiusInput,
                        onValueChange = { radiusInput = it.filter { char -> char.isDigit() } },
                        label = { Text("Vardiya İzin Yarıçapı (Metre)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandGold,
                            focusedLabelColor = BrandGoldLight,
                            unfocusedBorderColor = BorderColor.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("manager_radius_input")
                    )

                    var isLocatingGpsManager by remember { mutableStateOf(false) }

                    // 1. [ Haritadan Konum Seç ] (Primary Action)
                    Button(
                        onClick = { showMapPicker = true },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandGreenHover, contentColor = BrandOffWhite),
                        border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("manager_pick_map_button")
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp), tint = BrandGoldLight)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Haritadan Konum Seç", color = BrandOffWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // 2. [ Şu Anki Konumumu Kullan ] (Real Device GPS)
                    OutlinedButton(
                        onClick = {
                            ErrorUtils.safeUiAction(context, "Konum kaydedilemedi") {
                                val radiusVal = radiusInput.toDoubleOrNull() ?: 150.0
                                if (LocationUtils.hasLocationPermission(context)) {
                                    isLocatingGpsManager = true
                                    LocationUtils.getCurrentLocation(
                                        context = context,
                                        onLocationObtained = { loc ->
                                            isLocatingGpsManager = false
                                            coroutineScope.launch {
                                                val resolved = try {
                                                    LocationUtils.getAddressFromCoordinates(context, loc.latitude, loc.longitude)
                                                } catch (e: Exception) { null }
                                                viewModel.updateBranchLocation(
                                                    branchId = branchId,
                                                    lat = loc.latitude,
                                                    lng = loc.longitude,
                                                    radius = radiusVal,
                                                    address = resolved?.fullAddress,
                                                    locationCheckEnabled = true,
                                                    onSuccess = {
                                                        Toast.makeText(context, "Şube konumu gerçek cihaz GPS'i ile ayarlandı.", Toast.LENGTH_SHORT).show()
                                                        onDismiss()
                                                    },
                                                    onError = { err -> Toast.makeText(context, err, Toast.LENGTH_LONG).show() }
                                                )
                                            }
                                        },
                                        onError = { err ->
                                            isLocatingGpsManager = false
                                            val msg = when (err) {
                                                "LOCATION_DISABLED" -> "Cihazın GPS/konum servisi kapalı. Lütfen açın."
                                                "PERMISSION_DENIED" -> "Konum izni verilmedi."
                                                else -> err
                                            }
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
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
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkSurfaceSecondary.copy(alpha = 0.35f)),
                        border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("manager_set_location_confirm_button")
                    ) {
                        if (isLocatingGpsManager) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = BrandGold, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gerçek GPS Konumu Alınıyor...", fontWeight = FontWeight.Medium, fontSize = 12.sp, color = TextPrimary)
                        } else {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp), tint = BrandGoldLight)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Şu Anki Konumumu Kullan", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                        }
                    }

                    OutlinedButton(
                        onClick = { showManualInput = true },
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.7.dp, BorderColor.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.EditLocation, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextSecondary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Manuel Koordinat Girişi (İkincil)", color = TextSecondary, fontSize = 11.sp)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkBg.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .border(0.5.dp, StatusAmber.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = StatusAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (currentUserRole == "garson") {
                                "Garson hesaplarının şube GPS konumunu değiştirme yetkisi yoktur."
                            } else {
                                "Bu şubenin konumunu yalnızca yetkili Şube Müdürü veya Admin değiştirebilir."
                            },
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = {
                ErrorUtils.safeUiAction(context) {
                    onDismiss()
                }
            }) {
                Text("Kapat", color = TextSecondary)
            }
        }
    )
}

// --- 5.3 MANAGER MAIN SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerMainScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAnnouncementDialog by remember { mutableStateOf(false) }
    var showAnnouncementsScreen by remember { mutableStateOf(false) }
    val approvedCallInfo by viewModel.approvedCallEvent.collectAsStateWithLifecycle()
    val userBranchId by viewModel.userBranchId.collectAsStateWithLifecycle()
    val announcements by viewModel.announcements.collectAsStateWithLifecycle()
    val currentUserId = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.startAnnouncementsListener()
    }

    val unreadManagementCount = remember(announcements, currentUserId, userBranchId) {
        announcements.count { a ->
            val isForManager = a.targetType == "all_staff" ||
                    a.targetType == "all" ||
                    (a.targetType == "branch" && (a.branchId == userBranchId || a.targetBranchId == userBranchId || a.branchId == "all")) ||
                    a.targetUserUid == currentUserId ||
                    a.targetUserId == currentUserId
            isForManager && a.isManagement() && !a.readUids.contains(currentUserId)
        }
    }

    if (showSettings) {
        BackHandler {
            showSettings = false
        }
        SettingsScreen(onBackClick = { showSettings = false }, modifier = modifier)
        return
    }

    if (showAnnouncementsScreen) {
        BackHandler {
            showAnnouncementsScreen = false
        }
        ManagerAnnouncementsScreen(
            viewModel = viewModel,
            onBackClick = { showAnnouncementsScreen = false },
            onSendNewAnnouncement = {
                showAnnouncementDialog = true
            },
            modifier = modifier
        )
        return
    }

    BackHandler {
        if (selectedTab != 0) {
            selectedTab = 0
        } else {
            showExitDialog = true
        }
    }

    if (showExitDialog) {
        ExitConfirmDialog(
            onDismiss = { showExitDialog = false },
            onConfirmExit = {
                showExitDialog = false
                (context as? Activity)?.finish()
            }
        )
    }

    Scaffold(
        modifier = modifier.background(DarkBg),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (selectedTab) {
                            0 -> "Bildirimler"
                            1 -> "Çağrı Geçmişi"
                            2 -> "Garsonlar"
                            else -> "Personel"
                        },
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg),
                actions = {
                    IconButton(
                        onClick = {
                            ErrorUtils.safeUiAction(context) {
                                showAnnouncementsScreen = true
                            }
                        },
                        modifier = Modifier.testTag("manager_announcement_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadManagementCount > 0) {
                                    Badge(
                                        containerColor = StatusAmber,
                                        contentColor = DarkBg,
                                        modifier = Modifier.testTag("manager_announcements_badge")
                                    ) {
                                        Text(
                                            text = "$unreadManagementCount",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Campaign, contentDescription = "Duyurular", tint = BrandGoldLight)
                        }
                    }
                    IconButton(
                        onClick = {
                            ErrorUtils.safeUiAction(context) {
                                showSettings = true
                            }
                        },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Ayarlar", tint = TextSecondary)
                    }
                    IconButton(
                        onClick = {
                            ErrorUtils.safeUiAction(context) {
                                showLocationDialog = true
                            }
                        },
                        modifier = Modifier.testTag("manager_location_button")
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = "Şube Konumu", tint = TextSecondary)
                    }
                    IconButton(
                        onClick = {
                            ErrorUtils.safeUiAction(context, "Çıkış yapılamadı") {
                                viewModel.logout()
                            }
                        },
                        modifier = Modifier.testTag("logout_button")
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Çıkış Yap", tint = TextSecondary)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                modifier = Modifier.testTag("bottom_nav")
            ) {
                val tabs = listOf(
                    Triple(0, "Bildirimler", Icons.Default.Notifications),
                    Triple(1, "Geçmiş", Icons.Default.History),
                    Triple(2, "Garsonlar", Icons.Default.People),
                    Triple(3, "Personel", Icons.Default.Badge)
                )

                tabs.forEach { (index, label, icon) ->
                    val isSelected = selectedTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            ErrorUtils.safeUiAction(context) {
                                selectedTab = index
                            }
                        },
                        icon = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .width(20.dp)
                                            .height(2.dp)
                                            .background(BrandGold, RoundedCornerShape(1.dp))
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                                Icon(icon, contentDescription = label)
                            }
                        },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandOffWhite,
                            selectedTextColor = BrandOffWhite,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = when (index) {
                            1 -> Modifier.testTag("history_nav_item")
                            3 -> Modifier.testTag("personel_nav_item")
                            else -> Modifier
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(DarkBg)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (unreadManagementCount > 0 && selectedTab == 0) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable { showAnnouncementsScreen = true }
                            .testTag("manager_unread_announcement_banner"),
                        color = BrandSecondaryForest,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Campaign, contentDescription = null, tint = BrandGoldLight, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "Yönetimden $unreadManagementCount yeni duyurunuz var",
                                    color = BrandOffWhite,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text("Görüntüle →", color = BrandGoldLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedTab) {
                        0 -> NotificationsTab(viewModel)
                        1 -> CallHistoryTab(viewModel)
                        2 -> WaitersTab(viewModel)
                        else -> PersonelTab(viewModel)
                    }
                }
            }

            if (showLocationDialog) {
                SetBranchLocationDialog(
                    viewModel = viewModel,
                    branchId = userBranchId,
                    onDismiss = { showLocationDialog = false }
                )
            }

            if (showAnnouncementDialog) {
                SendAnnouncementDialog(
                    viewModel = viewModel,
                    branchId = userBranchId,
                    onDismiss = { showAnnouncementDialog = false }
                )
            }

            // Real-time top/bottom approval bar overlay
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(10f)
            ) {
                LiveApprovalBanner(
                    info = approvedCallInfo,
                    onDismiss = { viewModel.clearApprovedCallEvent() }
                )
            }
        }
    }
}

// --- 5.4 WAITER SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaiterMainScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAnnouncementsScreen by remember { mutableStateOf(false) }

    val approvedCallInfo by viewModel.approvedCallEvent.collectAsStateWithLifecycle()
    val announcements by viewModel.announcements.collectAsStateWithLifecycle()
    val currentUserId = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    val unreadAnnouncementsCount = remember(announcements, currentUserId) {
        announcements.count { a ->
            (a.targetType == "all_staff" || a.targetType == "all" || a.targetType == "branch" || a.targetUserUid == currentUserId || a.targetUserId == currentUserId) &&
            !a.readUids.contains(currentUserId)
        }
    }

    val context = LocalContext.current

    if (showSettings) {
        BackHandler {
            showSettings = false
        }
        SettingsScreen(onBackClick = { showSettings = false }, modifier = modifier)
        return
    }

    if (showAnnouncementsScreen) {
        BackHandler {
            showAnnouncementsScreen = false
        }
        WaiterAnnouncementsScreen(
            viewModel = viewModel,
            onBackClick = { showAnnouncementsScreen = false },
            modifier = modifier
        )
        return
    }

    BackHandler {
        if (selectedTab != 0) {
            selectedTab = 0
        } else {
            showExitDialog = true
        }
    }

    if (showExitDialog) {
        ExitConfirmDialog(
            onDismiss = { showExitDialog = false },
            onConfirmExit = {
                showExitDialog = false
                (context as? Activity)?.finish()
            }
        )
    }

    // 4-Tab Bottom Navigation: 1. Çağrılar, 2. Geçmiş, 3. Vardiyam, 4. Profilim (Fixed Order)
    val waiterTabs = listOf(
        Triple(0, "Çağrılar", Icons.Default.Notifications),
        Triple(1, "Geçmiş", Icons.Default.History),
        Triple(2, "Vardiyam", Icons.Default.AccessTime),
        Triple(3, "Profilim", Icons.Default.Person)
    )

    Scaffold(
        modifier = modifier.background(DarkBg),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (selectedTab) {
                            0 -> "Çağrılar"
                            1 -> "İşlem Geçmişim"
                            2 -> "Vardiyam"
                            else -> "Profilim"
                        },
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg),
                actions = {
                    if (selectedTab == 0) {
                        IconButton(
                            onClick = {
                                ErrorUtils.safeUiAction(context) {
                                    showAnnouncementsScreen = true
                                }
                            },
                            modifier = Modifier.testTag("waiter_announcements_button")
                        ) {
                            if (unreadAnnouncementsCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = BrandGold,
                                            contentColor = BrandDeepForest
                                        ) {
                                            Text(
                                                unreadAnnouncementsCount.toString(),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Campaign, contentDescription = "Duyurular", tint = BrandGoldLight)
                                }
                            } else {
                                Icon(Icons.Default.Campaign, contentDescription = "Duyurular", tint = TextSecondary)
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                modifier = Modifier.testTag("waiter_bottom_nav")
            ) {
                waiterTabs.forEach { (index, label, icon) ->
                    val isSelected = selectedTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            ErrorUtils.safeUiAction(context) {
                                selectedTab = index
                            }
                        },
                        icon = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .width(16.dp)
                                            .height(2.dp)
                                            .background(BrandGold, RoundedCornerShape(1.dp))
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(2.dp))
                                }

                                if (index == 3 && unreadAnnouncementsCount > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge(
                                                containerColor = BrandGold,
                                                contentColor = BrandDeepForest
                                            ) {
                                                Text(
                                                    unreadAnnouncementsCount.toString(),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(icon, contentDescription = label)
                                    }
                                } else {
                                    Icon(icon, contentDescription = label)
                                }
                            }
                        },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandOffWhite,
                            selectedTextColor = BrandOffWhite,
                            indicatorColor = Color.Transparent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("waiter_tab_$index")
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(DarkBg)
        ) {
            when (selectedTab) {
                0 -> NotificationsTab(viewModel)
                1 -> WaiterHistoryTab(viewModel)
                2 -> WaiterShiftTab(viewModel)
                3 -> WaiterProfileTab(
                    viewModel = viewModel,
                    onNavigateToAnnouncements = { showAnnouncementsScreen = true },
                    onNavigateToSettings = { showSettings = true }
                )
            }

            // Real-time top/bottom approval bar overlay
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(10f)
            ) {
                LiveApprovalBanner(
                    info = approvedCallInfo,
                    onDismiss = { viewModel.clearApprovedCallEvent() }
                )
            }
        }
    }
}

// --- WAITER TAB 2: GEÇMİŞ (Garson Kendi İşlem Geçmişi) ---
@Composable
fun WaiterHistoryTab(viewModel: AppViewModel) {
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val userBranchId by viewModel.userBranchId.collectAsStateWithLifecycle()
    val currentAd by viewModel.userAd.collectAsStateWithLifecycle()
    val currentUid = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" }

    DisposableEffect(userBranchId) {
        if (userBranchId.isNotEmpty()) {
            viewModel.startCallsListener(userBranchId)
        }
        onDispose {
            viewModel.stopCallsListener()
        }
    }

    var filterType by remember { mutableIntStateOf(0) } // 0: Tümü, 1: Bugün

    val myCalls = remember(calls, currentUid, currentAd, filterType) {
        val todayStartMs = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        calls.filter { call ->
            val isMyAction = (call.onaylayanUid == currentUid ||
                    (call.onaylayanAd.isNotEmpty() && call.onaylayanAd == currentAd) ||
                    (call.iptalEdenAd.isNotEmpty() && call.iptalEdenAd == currentAd))
            val isHandled = call.durum == "onaylandi" || call.durum == "iptal"
            if (!isMyAction || !isHandled) return@filter false

            if (filterType == 1) {
                val actionTime = call.onaylandiAt?.toDate()?.time
                    ?: call.iptalAt?.toDate()?.time
                    ?: call.createdAt?.toDate()?.time
                    ?: 0L
                actionTime >= todayStartMs
            } else {
                true
            }
        }.sortedByDescending {
            it.onaylandiAt?.toDate()?.time ?: it.iptalAt?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: 0L
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Filter tabs (Tümü / Bugün)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Tümü", "Bugün").forEachIndexed { index, title ->
                val isSelected = filterType == index
                FilterTabButton(
                    title = title,
                    isSelected = isSelected,
                    onClick = { filterType = index },
                    testTag = "history_filter_tab_$index"
                )
            }
        }

        if (myCalls.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "İşlem geçmişiniz bulunmuyor",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Onayladığınız veya işlem yaptığınız çağrılar burada listelenir.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(myCalls, key = { it.id }) { call ->
                    val isApproved = call.durum == "onaylandi"
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("waiter_history_card_${call.id}"),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, if (isApproved) StatusGreen.copy(alpha = 0.35f) else StatusRed.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = FormatUtils.formatMasaTitle(call.masaNo),
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    val badgeLabel = when (call.tip) {
                                        "siparis" -> "Sipariş"
                                        "hesap" -> "Hesap"
                                        else -> "Garson"
                                    }
                                    Surface(
                                        color = BrandGreenHover,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, BorderColor)
                                    ) {
                                        Text(
                                            text = badgeLabel,
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = if (isApproved) "✓ Onaylandı" else "✕ İptal",
                                    color = if (isApproved) StatusGreen else StatusRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            val actionTime = remember(call) {
                                val t = call.onaylandiAt?.toDate() ?: call.iptalAt?.toDate() ?: call.createdAt?.toDate()
                                if (t != null) {
                                    java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale("tr")).format(t)
                                } else ""
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = actionTime,
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )

                                if (call.tip == "siparis" && call.items.isNotEmpty()) {
                                    Text(
                                        text = "${call.items.size} ürün",
                                        color = BrandTextMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- WAITER TAB 3: VARDİYAM (Vardiya & Mola Yönetimi) ---
@Composable
fun WaiterShiftTab(viewModel: AppViewModel) {
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()
    val activeShiftBreaks by viewModel.activeShiftBreaks.collectAsStateWithLifecycle()
    val currentBranch by viewModel.currentBranch.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showEndShiftConfirmDialog by remember { mutableStateOf(false) }

    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = System.currentTimeMillis()
        }
    }

    val startShiftPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            viewModel.startShiftWithLocationCheck(
                context = context,
                onSuccess = { Toast.makeText(context, "Vardiya başlatıldı", Toast.LENGTH_SHORT).show() },
                onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
            )
        } else {
            Toast.makeText(context, "Vardiya başlatmak için konum izni gerekli", Toast.LENGTH_LONG).show()
        }
    }

    val isShiftOpen = activeShift != null
    val isBreak = activeShift?.molada == true
    val branchLocEnabled = currentBranch?.locationCheckEnabled ?: true

    // Confirmation dialog for ending shift to prevent accidental clicks
    if (showEndShiftConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEndShiftConfirmDialog = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Vardiyayı Bitir",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Aktif vardiyanızı bitirmek istediğinize emin misiniz? Çağrı bildirimleri durdurulacaktır.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEndShiftConfirmDialog = false
                        viewModel.endShift(
                            onSuccess = {
                                Toast.makeText(context, "Vardiya sonlandırıldı", Toast.LENGTH_SHORT).show()
                            },
                            onError = { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusRed,
                        contentColor = BrandOffWhite
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_end_shift_button")
                ) {
                    Text("Vardiyayı Bitir", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEndShiftConfirmDialog = false },
                    modifier = Modifier.testTag("cancel_end_shift_button")
                ) {
                    Text("İptal", color = TextSecondary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Status Card
        Card(
            modifier = Modifier.fillMaxWidth().testTag("waiter_shift_hero_card"),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, if (isShiftOpen) (if (isBreak) StatusAmber.copy(alpha = 0.4f) else StatusGreen.copy(alpha = 0.4f)) else BorderColor),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    when {
                                        !isShiftOpen -> TextSecondary.copy(alpha = 0.5f)
                                        isBreak -> StatusAmber
                                        else -> StatusGreen
                                    },
                                    CircleShape
                                )
                        )
                        Text(
                            text = when {
                                !isShiftOpen -> "Vardiya Kapalı"
                                isBreak -> "Molada"
                                else -> "Vardiya Açık — Çalışıyor"
                            },
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    if (isShiftOpen) {
                        Surface(
                            color = if (isBreak) StatusAmber.copy(alpha = 0.15f) else StatusGreen.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (isBreak) StatusAmber.copy(alpha = 0.35f) else StatusGreen.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = if (isBreak) "MOLA" else "AKTİF",
                                color = if (isBreak) StatusAmber else StatusGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isShiftOpen) {
                    val startStr = remember(activeShift?.baslangic) {
                        activeShift?.baslangic?.toDate()?.let {
                            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(it)
                        } ?: ""
                    }
                    val elapsedStr = remember(activeShift?.baslangic, currentTime) {
                        ShiftDateTimeUtils.formatLiveDuration(activeShift?.baslangic, currentTime)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Başlangıç Zamanı", color = TextSecondary, fontSize = 12.sp)
                            Text(startStr.ifEmpty { "--:--" }, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Geçen Süre", color = TextSecondary, fontSize = 12.sp)
                            Text(elapsedStr.ifEmpty { "0 saniye" }, color = BrandGoldLight, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                    }
                } else {
                    Text(
                        text = "Vardiyanızı başlattığınızda çağrı bildirimlerini almaya başlar ve masalardan gelen istekleri yanıtlayabilirsiniz.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            ErrorUtils.safeUiAction(context, "Vardiya başlatılamadı") {
                                if (!branchLocEnabled) {
                                    viewModel.startShift(
                                        onSuccess = { Toast.makeText(context, "Vardiya başlatıldı", Toast.LENGTH_SHORT).show() },
                                        onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                                    )
                                } else {
                                    if (LocationUtils.hasLocationPermission(context)) {
                                        viewModel.startShiftWithLocationCheck(
                                            context = context,
                                            onSuccess = { Toast.makeText(context, "Vardiya başlatıldı", Toast.LENGTH_SHORT).show() },
                                            onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                                        )
                                    } else {
                                        startShiftPermissionLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("start_shift_button_tab"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandGreenHover,
                            contentColor = BrandOffWhite
                        ),
                        border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = BrandGoldLight, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Vardiyayı Başlat", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Active shift actions (Break & End Shift)
        if (isShiftOpen) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Vardiya İşlemleri",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isBreak) {
                            Button(
                                onClick = {
                                    viewModel.endBreak(
                                        onSuccess = { Toast.makeText(context, "Moladan dönüldü", Toast.LENGTH_SHORT).show() },
                                        onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("end_break_button_tab"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrandGreenHover,
                                    contentColor = BrandOffWhite
                                ),
                                border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Moladan Dön", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    viewModel.startBreak(
                                        onSuccess = { Toast.makeText(context, "Molaya çıkıldı", Toast.LENGTH_SHORT).show() },
                                        onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .testTag("start_break_button_tab"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BrandSecondaryForest,
                                    contentColor = BrandOffWhite
                                ),
                                border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Molaya Çık", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                showEndShiftConfirmDialog = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("end_shift_button_tab"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = StatusRed
                            ),
                            border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Vardiyayı Bitir", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Mola Geçmişi (Break History)
            Card(
                modifier = Modifier.fillMaxWidth().testTag("waiter_break_history_card"),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, BorderColor),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mola Geçmişi",
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "${activeShiftBreaks.size} mola",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (activeShiftBreaks.isEmpty()) {
                        Text(
                            text = "Bu vardiyada henüz molaya çıkılmadı.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        activeShiftBreaks.forEachIndexed { idx, brk ->
                            if (idx > 0) {
                                HorizontalDivider(color = BorderColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 6.dp))
                            }
                            val startFormatted = brk.baslangic?.toDate()?.let {
                                java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(it)
                            } ?: "--:--"
                            val endFormatted = brk.bitis?.toDate()?.let {
                                java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(it)
                            } ?: "Devam ediyor"

                            val durationStr = if (brk.baslangic != null && brk.bitis != null) {
                                val diffMs = brk.bitis.toDate().time - brk.baslangic.toDate().time
                                ShiftDateTimeUtils.formatDurationDetailed(diffMs)
                            } else if (brk.baslangic != null) {
                                val liveDiffMs = (currentTime - brk.baslangic.toDate().time).coerceAtLeast(0L)
                                ShiftDateTimeUtils.formatDurationDetailed(liveDiffMs)
                            } else {
                                "Veri eksik"
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Mola ${activeShiftBreaks.size - idx}",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "$startFormatted – $endFormatted ($durationStr)",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- WAITER TAB 4: PROFİLİM (Hesap & Ayarlar) ---
@Composable
fun WaiterProfileTab(
    viewModel: AppViewModel,
    onNavigateToAnnouncements: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val userAd by viewModel.userAd.collectAsStateWithLifecycle()
    val userKullaniciAdi by viewModel.userKullaniciAdi.collectAsStateWithLifecycle()
    val currentBranch by viewModel.currentBranch.collectAsStateWithLifecycle()
    val announcements by viewModel.announcements.collectAsStateWithLifecycle()
    val currentUserId = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    val unreadAnnouncementsCount = remember(announcements, currentUserId) {
        announcements.count { a ->
            (a.targetType == "all_staff" || a.targetType == "all" || a.targetType == "branch" || a.targetUserUid == currentUserId || a.targetUserId == currentUserId) &&
            !a.readUids.contains(currentUserId)
        }
    }

    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            containerColor = DarkSurface,
            title = {
                Text("Çıkış Yap", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Oturumunuzu kapatmak istediğinize emin misiniz?", color = TextSecondary, fontSize = 14.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmDialog = false
                        viewModel.logout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusRed,
                        contentColor = BrandOffWhite
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_logout_button")
                ) {
                    Text("Çıkış Yap", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutConfirmDialog = false },
                    modifier = Modifier.testTag("cancel_logout_button")
                ) {
                    Text("İptal", color = TextSecondary)
                }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = DarkSurface,
            title = {
                Text("Uygulama Hakkında", color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("QR Servis — Operasyon Yönetimi", color = BrandGoldLight, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Versiyon: 29.0", color = TextSecondary, fontSize = 13.sp)
                    Text("Altyapı: QRMENUOFFICIAL", color = TextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Garson & Yönetim Canlı Çağrı ve Operasyon Sistemi.", color = BrandOffWhite, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Tamam", color = BrandGold)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // User Profile Header Card
        Card(
            modifier = Modifier.fillMaxWidth().testTag("waiter_profile_header_card"),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, BorderColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(BrandGreenHover, CircleShape)
                        .border(1.dp, BrandGold.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = BrandOffWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = userAd.ifEmpty { "Garson" },
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "@$userKullaniciAdi",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            color = BrandGreenHover,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, StatusGreen.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "Garson",
                                color = StatusGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        if (currentBranch?.ad?.isNotEmpty() == true) {
                            Surface(
                                color = BrandSecondaryForest,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, BorderColor)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Şube: ",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                    Text(
                                        text = currentBranch?.ad ?: "",
                                        color = BrandOffWhite,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Action Options
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, BorderColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Announcements option
                ProfileMenuRow(
                    icon = Icons.Default.Campaign,
                    title = "Müdür Duyuruları",
                    subtitle = "Şube yönetiminden gelen mesajlar",
                    badge = if (unreadAnnouncementsCount > 0) "$unreadAnnouncementsCount yeni" else null,
                    badgeColor = BrandGold,
                    onClick = onNavigateToAnnouncements,
                    testTag = "profile_announcements_row"
                )

                HorizontalDivider(color = BorderColor, thickness = 1.dp)

                // Sound & Notification Settings option
                ProfileMenuRow(
                    icon = Icons.Default.VolumeUp,
                    title = "Ses ve Bildirim Ayarları",
                    subtitle = "Zil sesleri, ses seviyesi ve titreşim",
                    onClick = onNavigateToSettings,
                    testTag = "profile_settings_row"
                )

                HorizontalDivider(color = BorderColor, thickness = 1.dp)

                // About app option
                ProfileMenuRow(
                    icon = Icons.Default.Info,
                    title = "Uygulama Hakkında",
                    subtitle = "QR Servis Operasyon Paneli v29.0",
                    onClick = { showAboutDialog = true },
                    testTag = "profile_about_row"
                )

                HorizontalDivider(color = BorderColor, thickness = 1.dp)

                // Logout option
                ProfileMenuRow(
                    icon = Icons.Default.ExitToApp,
                    title = "Çıkış Yap",
                    subtitle = "Hesaptan güvenli çıkış yapın",
                    titleColor = StatusRed,
                    iconTint = StatusRed,
                    onClick = { showLogoutConfirmDialog = true },
                    testTag = "profile_logout_row"
                )
            }
        }
    }
}

@Composable
fun ProfileMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    badgeColor: Color = BrandGold,
    titleColor: Color = TextPrimary,
    iconTint: Color = TextSecondary,
    onClick: () -> Unit,
    testTag: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(if (titleColor == StatusRed) StatusRed.copy(alpha = 0.12f) else BrandGreenHover, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = titleColor, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(text = subtitle, color = TextSecondary, fontSize = 12.sp)
        }

        if (badge != null) {
            Surface(
                color = badgeColor.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
            ) {
                Text(
                    text = badge,
                    color = badgeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// --- WAITER ANNOUNCEMENTS SCREEN (Profilim -> Duyurular) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaiterAnnouncementsScreen(
    viewModel: AppViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val announcements by viewModel.announcements.collectAsStateWithLifecycle()
    val currentUserId = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    var selectedSourceTab by remember { mutableIntStateOf(0) } // 0: YÖNETİM, 1: MÜDÜR
    var filterType by remember { mutableIntStateOf(0) } // 0: Tümü, 1: Okunmamış

    LaunchedEffect(Unit) {
        viewModel.startAnnouncementsListener()
    }

    // Unread count for YÖNETİM (Admin / Sistem)
    val unreadManagementCount = remember(announcements, currentUserId) {
        announcements.count { a ->
            val isForMe = a.targetType == "all_staff" || a.targetType == "all" || a.targetType == "branch" || a.targetUserUid == currentUserId || a.targetUserId == currentUserId
            isForMe && a.isManagement() && !a.readUids.contains(currentUserId)
        }
    }

    // Unread count for MÜDÜR
    val unreadManagerCount = remember(announcements, currentUserId) {
        announcements.count { a ->
            val isForMe = a.targetType == "all_staff" || a.targetType == "all" || a.targetType == "branch" || a.targetUserUid == currentUserId || a.targetUserId == currentUserId
            isForMe && a.isManager() && !a.readUids.contains(currentUserId)
        }
    }

    // Base announcements filtered for active source tab
    val sourceList = remember(announcements, currentUserId, selectedSourceTab) {
        announcements.filter { a ->
            val isForMe = a.targetType == "all_staff" || a.targetType == "all" || a.targetType == "branch" || a.targetUserUid == currentUserId || a.targetUserId == currentUserId
            if (!isForMe) return@filter false
            if (selectedSourceTab == 0) a.isManagement() else a.isManager()
        }
    }

    // Final list based on Tümü / Okunmamış subfilter
    val filteredList = remember(sourceList, currentUserId, filterType) {
        if (filterType == 1) {
            sourceList.filter { !it.readUids.contains(currentUserId) }
        } else {
            sourceList
        }
    }

    val currentTabUnreadCount = if (selectedSourceTab == 0) unreadManagementCount else unreadManagerCount

    Scaffold(
        modifier = modifier.background(DarkBg),
        topBar = {
            TopAppBar(
                title = { Text("Duyurular", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick, modifier = Modifier.testTag("announcements_back_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // --- TOP SOURCE TABS: YÖNETİM & MÜDÜR ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Tab 0: YÖNETİM
                val isMgmtSelected = selectedSourceTab == 0
                val mgmtTitle = if (unreadManagementCount > 0) "Yönetim ($unreadManagementCount)" else "Yönetim"
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { selectedSourceTab = 0 }
                        .testTag("tab_announcements_management"),
                    color = if (isMgmtSelected) BrandGreenHover else Color.Transparent,
                    border = if (isMgmtSelected) BorderStroke(1.dp, BrandGold.copy(alpha = 0.6f)) else null,
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = if (isMgmtSelected) BrandGoldLight else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = mgmtTitle,
                            color = if (isMgmtSelected) BrandOffWhite else TextSecondary,
                            fontWeight = if (isMgmtSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }

                // Tab 1: MÜDÜR
                val isMgrSelected = selectedSourceTab == 1
                val mgrTitle = if (unreadManagerCount > 0) "Müdür ($unreadManagerCount)" else "Müdür"
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { selectedSourceTab = 1 }
                        .testTag("tab_announcements_manager"),
                    color = if (isMgrSelected) BrandGreenHover else Color.Transparent,
                    border = if (isMgrSelected) BorderStroke(1.dp, BrandGold.copy(alpha = 0.6f)) else null,
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SupervisorAccount,
                            contentDescription = null,
                            tint = if (isMgrSelected) BrandGoldLight else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = mgrTitle,
                            color = if (isMgrSelected) BrandOffWhite else TextSecondary,
                            fontWeight = if (isMgrSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sub-filter row: Tümü / Okunmamış for the currently active source
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterTabButton(
                    title = "Tümü (${sourceList.size})",
                    isSelected = filterType == 0,
                    onClick = { filterType = 0 },
                    testTag = "filter_tab_all_announcements"
                )
                FilterTabButton(
                    title = "Okunmamış ($currentTabUnreadCount)",
                    isSelected = filterType == 1,
                    onClick = { filterType = 1 },
                    testTag = "filter_tab_unread_announcements"
                )
            }

            if (filteredList.isEmpty()) {
                val emptyMessage = when {
                    filterType == 1 && selectedSourceTab == 0 -> "Okunmamış yönetim duyurunuz yok"
                    filterType == 1 && selectedSourceTab == 1 -> "Okunmamış müdür duyurunuz yok"
                    selectedSourceTab == 0 -> "Henüz yönetim duyurusu yok"
                    else -> "Henüz müdür duyurusu yok"
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (selectedSourceTab == 0) Icons.Default.AdminPanelSettings else Icons.Default.SupervisorAccount,
                            contentDescription = null,
                            tint = TextSecondary.copy(alpha = 0.35f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = emptyMessage,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        val isRead = item.readUids.contains(currentUserId)
                        val isPersonal = item.targetType == "user"
                        val isManagementItem = item.isManagement()
                        val dateStr = remember(item.createdAt) {
                            item.createdAt?.toDate()?.let {
                                java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale("tr")).format(it)
                            } ?: ""
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isRead) {
                                        viewModel.markAnnouncementAsRead(item.id)
                                    }
                                }
                                .testTag("announcement_card_${item.id}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isRead) DarkSurface else BrandSecondaryForest
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isRead) BorderColor else StatusAmber.copy(alpha = 0.35f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        // Source Tag
                                        Surface(
                                            color = if (isManagementItem) BrandGold.copy(alpha = 0.15f) else BrandGreenHover,
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(1.dp, if (isManagementItem) BrandGold.copy(alpha = 0.45f) else BorderColor)
                                        ) {
                                            Text(
                                                text = if (isManagementItem) "Yönetim" else "Müdür",
                                                color = if (isManagementItem) BrandGoldLight else BrandOffWhite,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Text(
                                            text = item.title,
                                            color = BrandOffWhite,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (!isRead) {
                                        Surface(
                                            color = StatusAmber.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.35f)),
                                            modifier = Modifier.clickable {
                                                viewModel.markAnnouncementAsRead(item.id)
                                            }.testTag("mark_read_button_${item.id}")
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .background(StatusAmber, CircleShape)
                                                )
                                                Text(
                                                    text = "Yeni",
                                                    color = StatusAmber,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "✓ Okundu",
                                            color = TextSecondary.copy(alpha = 0.7f),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Normal
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = item.message,
                                    color = BrandOffWhite.copy(alpha = 0.9f),
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${item.senderName.ifEmpty { if (isManagementItem) "Yönetim (Admin)" else "Şube Müdürü" }} · $dateStr",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )

                                    if (isPersonal) {
                                        Surface(
                                            color = BrandGreenHover,
                                            shape = RoundedCornerShape(4.dp),
                                            border = BorderStroke(1.dp, BorderColor)
                                        ) {
                                            Text(
                                                text = "Size Özel",
                                                color = BrandTextMuted,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- 5.3.1 MANAGER ANNOUNCEMENTS SCREEN (Yönetim & Müdür Duyuruları) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerAnnouncementsScreen(
    viewModel: AppViewModel,
    onBackClick: () -> Unit,
    onSendNewAnnouncement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val announcements by viewModel.announcements.collectAsStateWithLifecycle()
    val userBranchId by viewModel.userBranchId.collectAsStateWithLifecycle()
    val currentUserId = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" }
    var selectedSourceTab by remember { mutableIntStateOf(0) } // 0: YÖNETİM, 1: MÜDÜR
    var filterType by remember { mutableIntStateOf(0) } // 0: Tümü, 1: Okunmamış

    LaunchedEffect(Unit) {
        viewModel.startAnnouncementsListener()
    }

    // Helper for matching management announcements meant for this manager
    val isForManager = remember(currentUserId, userBranchId) {
        { a: Announcement ->
            val targetMatch = a.targetType == "all_staff" ||
                    a.targetType == "all" ||
                    (a.targetType == "branch" && (a.branchId == userBranchId || a.targetBranchId == userBranchId || a.branchId == "all")) ||
                    a.targetUserUid == currentUserId ||
                    a.targetUserId == currentUserId
            targetMatch && a.isManagement()
        }
    }

    // Unread count for YÖNETİM (Admin / Sistem)
    val unreadManagementCount = remember(announcements, currentUserId, userBranchId) {
        announcements.count { isForManager(it) && !it.readUids.contains(currentUserId) }
    }

    // Management announcements
    val managementList = remember(announcements, currentUserId, userBranchId) {
        announcements.filter { isForManager(it) }
    }

    // Filtered Management list (Tümü / Okunmamış)
    val filteredManagementList = remember(managementList, currentUserId, filterType) {
        if (filterType == 1) {
            managementList.filter { !it.readUids.contains(currentUserId) }
        } else {
            managementList
        }
    }

    // Manager's own sent announcements and branch manager announcements
    val managerSentList = remember(announcements, currentUserId, userBranchId) {
        announcements.filter {
            it.senderUid == currentUserId ||
            (it.isManager() && (it.branchId == userBranchId || it.targetBranchId == userBranchId))
        }
    }

    Scaffold(
        modifier = modifier.background(DarkBg),
        topBar = {
            TopAppBar(
                title = { Text("Duyurular", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBackClick, modifier = Modifier.testTag("manager_announcements_back_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = TextSecondary)
                    }
                },
                actions = {
                    IconButton(
                        onClick = onSendNewAnnouncement,
                        modifier = Modifier.testTag("manager_announcements_top_send_button")
                    ) {
                        Icon(Icons.Default.Campaign, contentDescription = "Duyuru Gönder", tint = BrandGoldLight)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBg)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onSendNewAnnouncement,
                containerColor = BrandGreenHover,
                contentColor = BrandOffWhite,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .border(1.dp, BrandGold.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .testTag("manager_new_announcement_fab")
            ) {
                Icon(Icons.Default.Campaign, contentDescription = null, tint = BrandGoldLight)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Yeni Duyuru Gönder", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // --- TOP SOURCE TABS: YÖNETİM & MÜDÜR ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Tab 0: YÖNETİM
                val isMgmtSelected = selectedSourceTab == 0
                val mgmtTitle = if (unreadManagementCount > 0) "Yönetim ($unreadManagementCount)" else "Yönetim"
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { selectedSourceTab = 0 }
                        .testTag("tab_announcements_management"),
                    color = if (isMgmtSelected) BrandGreenHover else Color.Transparent,
                    border = if (isMgmtSelected) BorderStroke(1.dp, BrandGold.copy(alpha = 0.6f)) else null,
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = if (isMgmtSelected) BrandGoldLight else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = mgmtTitle,
                            color = if (isMgmtSelected) BrandOffWhite else TextSecondary,
                            fontWeight = if (isMgmtSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }

                // Tab 1: MÜDÜR
                val isMgrSelected = selectedSourceTab == 1
                val mgrTitle = "Müdür"
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { selectedSourceTab = 1 }
                        .testTag("tab_announcements_manager"),
                    color = if (isMgrSelected) BrandGreenHover else Color.Transparent,
                    border = if (isMgrSelected) BorderStroke(1.dp, BrandGold.copy(alpha = 0.6f)) else null,
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SupervisorAccount,
                            contentDescription = null,
                            tint = if (isMgrSelected) BrandGoldLight else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = mgrTitle,
                            color = if (isMgrSelected) BrandOffWhite else TextSecondary,
                            fontWeight = if (isMgrSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sub-filter row
            if (selectedSourceTab == 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterTabButton(
                        title = "Tümü (${managementList.size})",
                        isSelected = filterType == 0,
                        onClick = { filterType = 0 },
                        testTag = "filter_tab_all_management"
                    )
                    FilterTabButton(
                        title = "Okunmamış ($unreadManagementCount)",
                        isSelected = filterType == 1,
                        onClick = { filterType = 1 },
                        testTag = "filter_tab_unread_management"
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterTabButton(
                        title = "Gönderilenler (${managerSentList.size})",
                        isSelected = true,
                        onClick = {},
                        testTag = "filter_tab_all_manager_sent"
                    )
                }
            }

            // List or Empty State
            if (selectedSourceTab == 0) {
                if (filteredManagementList.isEmpty()) {
                    val emptyMsg = if (filterType == 1) "Okunmamış yönetim duyurunuz yok" else "Henüz yönetim duyurusu yok"
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = null,
                                tint = TextSecondary.copy(alpha = 0.35f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = emptyMsg,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(filteredManagementList, key = { it.id }) { item ->
                            val isRead = item.readUids.contains(currentUserId)
                            val isPersonal = item.targetType == "user"
                            val dateStr = remember(item.createdAt) {
                                item.createdAt?.toDate()?.let {
                                    java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale("tr")).format(it)
                                } ?: ""
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!isRead) {
                                            viewModel.markAnnouncementAsRead(item.id)
                                        }
                                    }
                                    .testTag("announcement_card_${item.id}"),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isRead) DarkSurface else BrandSecondaryForest
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isRead) BorderColor else StatusAmber.copy(alpha = 0.35f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                color = BrandGold.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(4.dp),
                                                border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.45f))
                                            ) {
                                                Text(
                                                    text = "Yönetim",
                                                    color = BrandGoldLight,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }

                                            Text(
                                                text = item.title,
                                                color = BrandOffWhite,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        if (!isRead) {
                                            Surface(
                                                color = StatusAmber.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp),
                                                border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.35f)),
                                                modifier = Modifier.clickable {
                                                    viewModel.markAnnouncementAsRead(item.id)
                                                }.testTag("mark_read_button_${item.id}")
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .background(StatusAmber, CircleShape)
                                                    )
                                                    Text(
                                                        text = "Yeni",
                                                        color = StatusAmber,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        } else {
                                            Text(
                                                text = "✓ Okundu",
                                                color = TextSecondary.copy(alpha = 0.7f),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Normal
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = item.message,
                                        color = BrandOffWhite.copy(alpha = 0.9f),
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${item.senderName.ifEmpty { "Yönetim (Admin)" }} · $dateStr",
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )

                                        if (isPersonal) {
                                            Surface(
                                                color = BrandGreenHover,
                                                shape = RoundedCornerShape(4.dp),
                                                border = BorderStroke(1.dp, BorderColor)
                                            ) {
                                                Text(
                                                    text = "Müdüre Özel",
                                                    color = BrandTextMuted,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Tab 1: MÜDÜR (Gönderdiklerim)
                if (managerSentList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.SupervisorAccount,
                                contentDescription = null,
                                tint = TextSecondary.copy(alpha = 0.35f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Henüz müdür duyurusu yok",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Personelinize duyuru göndermek için alttaki butonu kullanın",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(managerSentList, key = { it.id }) { item ->
                            val isPersonal = item.targetType == "user"
                            val dateStr = remember(item.createdAt) {
                                item.createdAt?.toDate()?.let {
                                    java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale("tr")).format(it)
                                } ?: ""
                            }
                            val targetDesc = if (isPersonal) {
                                "Personel: ${item.targetUserName.ifEmpty { "Seçili Personel" }}"
                            } else {
                                "Tüm Personelim"
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("manager_sent_card_${item.id}"),
                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                border = BorderStroke(1.dp, BorderColor),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                color = BrandGreenHover,
                                                shape = RoundedCornerShape(4.dp),
                                                border = BorderStroke(1.dp, BorderColor)
                                            ) {
                                                Text(
                                                    text = "Müdür",
                                                    color = BrandOffWhite,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }

                                            Text(
                                                text = item.title,
                                                color = BrandOffWhite,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Surface(
                                            color = BrandGreenHover.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = targetDesc,
                                                color = BrandGoldLight,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = item.message,
                                        color = BrandOffWhite.copy(alpha = 0.9f),
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${item.senderName.ifEmpty { "Şube Müdürü" }} · $dateStr",
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )

                                        Text(
                                            text = if (item.readUids.isEmpty()) "Henüz okunmadı" else "✓ ${item.readUids.size} personel okudu",
                                            color = if (item.readUids.isEmpty()) TextSecondary else StatusGreen,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- ANNOUNCEMENT SEND DIALOG (Admin & Manager) ---
@Composable
fun SendAnnouncementDialog(
    viewModel: AppViewModel,
    branchId: String = "",
    onDismiss: () -> Unit
) {
    val waiters by viewModel.waiters.collectAsStateWithLifecycle()
    val branches by viewModel.branches.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val currentUserRole by viewModel.userRole.collectAsStateWithLifecycle()
    val currentUserBranchId by viewModel.userBranchId.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val currentAuthEmail = remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email?.lowercase() ?: "" }
    val isAdmin = currentUserRole in listOf("admin", "yonetici", "yönetici", "administrator", "sistem", "kurucu", "patron") ||
            currentAuthEmail == "admin@qrservis.local" ||
            currentAuthEmail == "muhammedulusoyresmi@gmail.com" ||
            currentAuthEmail.contains("admin") ||
            currentAuthEmail.contains("yonetici") ||
            (currentUserRole.isEmpty() && currentUserBranchId.isEmpty())
    val isManager = !isAdmin && (currentUserRole in listOf("mudur", "müdür", "manager") || currentUserBranchId.isNotEmpty())

    // Target Scope:
    // Admin: "all_staff" (Tüm Şubelerdeki Personel), "branch" (Belirli Şube - Tüm Garsonlar), "user" (Belirli Personel)
    // Manager: "branch" (Kendi Şubesindeki Tüm Personel), "user" (Belirli Personel)
    var targetScope by remember {
        mutableStateOf(
            if (isAdmin) {
                if (branchId.isNotEmpty() && branchId != "all") "branch" else "all_staff"
            } else {
                "branch"
            }
        )
    }

    var selectedBranchId by remember(branchId, branches) {
        mutableStateOf(
            if (branchId.isNotEmpty() && branchId != "all") branchId
            else if (isManager) currentUserBranchId
            else branches.firstOrNull()?.id ?: ""
        )
    }

    var selectedPersonnelUid by remember { mutableStateOf("") }
    var selectedPersonnelName by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var sendErrorMessage by remember { mutableStateOf<String?>(null) }

    val selectedBranch = remember(branches, selectedBranchId, currentUserBranchId, isManager) {
        if (isManager) {
            branches.find { it.id == currentUserBranchId }
        } else {
            branches.find { it.id == selectedBranchId }
        }
    }

    // Candidate personnel for specific user targeting:
    // Manager can ONLY see waiters and personnel belonging to their own branch
    val candidateUsers = remember(isAdmin, allUsers, waiters, selectedBranchId, targetScope, currentUserBranchId) {
        if (isAdmin) {
            allUsers.filter { user ->
                user.rol != "admin" && (selectedBranchId.isEmpty() || selectedBranchId == "all" || user.branchId == selectedBranchId)
            }
        } else {
            waiters.filter { it.rol != "admin" && (currentUserBranchId.isEmpty() || it.branchId == currentUserBranchId) }
        }
    }

    // Explicit Target Summary (Matches exact format: "Tüm Personel" | "Şube: X" | "Personel: X")
    val targetSummaryText = remember(targetScope, isAdmin, selectedBranch, selectedPersonnelName, selectedBranchId) {
        when {
            isAdmin && targetScope == "all_staff" -> "Tüm Personel"
            isAdmin && targetScope == "branch" -> {
                val bName = selectedBranch?.ad ?: "Seçili Şube"
                "Şube: $bName"
            }
            !isAdmin && (targetScope == "all" || targetScope == "branch") -> {
                val bName = selectedBranch?.ad ?: "Şubem"
                "Tüm Personelim ($bName)"
            }
            targetScope == "user" -> {
                if (selectedPersonnelName.isNotEmpty()) {
                    "Personel: $selectedPersonnelName"
                } else {
                    "Personel: (Seçilmedi)"
                }
            }
            else -> "Tüm Personel"
        }
    }

    val finalTargetType = when {
        isAdmin && targetScope == "all_staff" -> "all_staff"
        isAdmin && targetScope == "branch" -> "branch"
        targetScope == "user" -> "user"
        else -> "branch" // Manager's own branch staff
    }

    val finalBranchId = when {
        isAdmin && targetScope == "all_staff" -> "all"
        isAdmin && targetScope == "branch" -> selectedBranchId
        isAdmin && targetScope == "user" -> {
            val userBranch = candidateUsers.find { it.uid == selectedPersonnelUid }?.branchId ?: ""
            if (userBranch.isNotEmpty()) userBranch else selectedBranchId
        }
        else -> currentUserBranchId
    }

    val finalBranchName = when {
        isAdmin && targetScope == "all_staff" -> "Tüm Şubeler"
        selectedBranch != null -> selectedBranch.ad
        else -> ""
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            containerColor = DarkSurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Campaign, contentDescription = null, tint = BrandGoldLight, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Duyuru Gönderilsin mi?", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Hedef:",
                        color = BrandGoldLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = targetSummaryText,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = BorderColor.copy(alpha = 0.3f), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Başlık: $title", color = BrandGoldLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(message, color = TextSecondary, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        isSending = true
                        sendErrorMessage = null
                        viewModel.sendAnnouncement(
                            branchId = finalBranchId,
                            branchName = finalBranchName,
                            title = title,
                            message = message,
                            targetType = finalTargetType,
                            targetBranchId = finalBranchId,
                            targetBranchName = finalBranchName,
                            targetUserUid = if (targetScope == "user") selectedPersonnelUid else "",
                            targetUserName = if (targetScope == "user") selectedPersonnelName else "",
                            onSuccess = { statusMsg ->
                                isSending = false
                                Toast.makeText(context, statusMsg, Toast.LENGTH_LONG).show()
                                onDismiss()
                            },
                            onError = { err ->
                                isSending = false
                                sendErrorMessage = err
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreenHover, contentColor = BrandOffWhite),
                    border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_send_announcement_button")
                ) {
                    Text("Onayla ve Gönder", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Geri", color = TextSecondary)
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.70f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            val isNarrow = maxWidth < 350.dp
            val horizontalPadding = if (isNarrow) 12.dp else 16.dp

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.96f)
                    .widthIn(max = 680.dp),
                color = DarkSurface,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.6f))
            ) {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    containerColor = DarkSurface,
                    topBar = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurface)
                                .padding(top = 10.dp)
                        ) {
                            // Sheet Drag Handle
                            Box(
                                modifier = Modifier
                                    .size(width = 44.dp, height = 4.dp)
                                    .background(BorderColor, RoundedCornerShape(2.dp))
                                    .align(Alignment.CenterHorizontally)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = horizontalPadding, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(BrandGreenHover, CircleShape)
                                            .border(1.dp, BrandGold.copy(alpha = 0.5f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Campaign,
                                            contentDescription = null,
                                            tint = BrandGoldLight,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Yeni Duyuru Gönder",
                                            color = TextPrimary,
                                            fontSize = if (isNarrow) 16.sp else 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (isAdmin) "Personel veya şubeye anlık bildirim" else "Şubenizdeki personellere anlık bildirim",
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("cancel_announcement_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Kapat",
                                        tint = TextSecondary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = BorderColor.copy(alpha = 0.4f), thickness = 1.dp)
                        }
                    },
                    bottomBar = {
                        // Fixed bottom bar that sits right above keyboard when IME is open
                        Surface(
                            color = DarkSurface,
                            border = BorderStroke(1.dp, BorderColor.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = horizontalPadding, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                                    border = BorderStroke(1.dp, BorderColor),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("Vazgeç", fontSize = 13.5.sp)
                                }

                                Button(
                                    onClick = {
                                        sendErrorMessage = null
                                        if (targetScope == "user" && selectedPersonnelUid.isEmpty()) {
                                            Toast.makeText(context, "Lütfen bir personel seçin", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        if (isAdmin && targetScope == "branch" && selectedBranchId.isEmpty()) {
                                            Toast.makeText(context, "Lütfen bir şube seçin", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        if (title.isBlank() || message.isBlank()) {
                                            Toast.makeText(context, "Başlık ve mesaj boş olamaz", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        showConfirmDialog = true
                                    },
                                    enabled = !isSending,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = BrandGreenHover,
                                        contentColor = BrandOffWhite,
                                        disabledContainerColor = DarkBg,
                                        disabledContentColor = TextSecondary
                                    ),
                                    border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.6f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("send_announcement_submit_button")
                                ) {
                                    if (isSending) {
                                        CircularProgressIndicator(
                                            color = BrandGold,
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Campaign,
                                            contentDescription = null,
                                            tint = BrandGoldLight,
                                            modifier = Modifier.size(19.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Duyuruyu Gönder",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = if (isNarrow) 13.5.sp else 14.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = horizontalPadding, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (sendErrorMessage != null) {
                            Surface(
                                color = StatusRed.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = StatusRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = sendErrorMessage ?: "",
                                        color = TextPrimary,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // 1. Hedef Seçimi (Tüm / Şube / Personel)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Hedef Seçimi",
                                    color = TextSecondary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = when {
                                        targetScope == "user" -> "Belirli bir personel hedefleniyor"
                                        targetScope == "branch" -> "Seçili şubedeki personeller hedefleniyor"
                                        isAdmin -> "Tüm şubelerdeki kayıtlı personeller hedefleniyor"
                                        else -> "Şubenizdeki tüm personeller hedefleniyor"
                                    },
                                    color = BrandGoldLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (isAdmin) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    TargetScopeButton(
                                        title = "Tüm",
                                        icon = Icons.Default.People,
                                        isSelected = targetScope == "all_staff",
                                        onClick = {
                                            targetScope = "all_staff"
                                            selectedPersonnelUid = ""
                                            selectedPersonnelName = ""
                                        },
                                        testTag = "target_all_staff_button",
                                        modifier = Modifier.weight(1f)
                                    )
                                    TargetScopeButton(
                                        title = "Şube",
                                        icon = Icons.Default.Storefront,
                                        isSelected = targetScope == "branch",
                                        onClick = {
                                            targetScope = "branch"
                                            selectedPersonnelUid = ""
                                            selectedPersonnelName = ""
                                        },
                                        testTag = "target_branch_button",
                                        modifier = Modifier.weight(1f)
                                    )
                                    TargetScopeButton(
                                        title = "Personel",
                                        icon = Icons.Default.Person,
                                        isSelected = targetScope == "user",
                                        onClick = { targetScope = "user" },
                                        testTag = "target_user_button",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    TargetScopeButton(
                                        title = "Tüm Personelim",
                                        icon = Icons.Default.People,
                                        isSelected = targetScope == "all" || targetScope == "branch",
                                        onClick = {
                                            targetScope = "branch"
                                            selectedPersonnelUid = ""
                                            selectedPersonnelName = ""
                                        },
                                        testTag = "target_all_button",
                                        modifier = Modifier.weight(1f)
                                    )
                                    TargetScopeButton(
                                        title = "Belirli Personel",
                                        icon = Icons.Default.Person,
                                        isSelected = targetScope == "user",
                                        onClick = { targetScope = "user" },
                                        testTag = "target_user_button",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // 2. Şube Seçimi (Eğer Admin ve Şube veya Personel seçilmişse)
                        if (isAdmin && (targetScope == "branch" || targetScope == "user")) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (targetScope == "branch") "Duyuru İletilecek Şube:" else "Personel Listesini Filtrele (Şube):",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (branches.isEmpty()) {
                                    Text("Kayıtlı şube bulunamadı.", color = StatusRed, fontSize = 12.sp)
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (targetScope == "user") {
                                            val isAllSelected = selectedBranchId.isEmpty() || selectedBranchId == "all"
                                            Surface(
                                                modifier = Modifier
                                                    .clickable {
                                                        selectedBranchId = "all"
                                                    },
                                                color = if (isAllSelected) BrandGreenHover else DarkBg,
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, if (isAllSelected) BrandGold else BorderColor)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.People,
                                                        contentDescription = null,
                                                        tint = if (isAllSelected) BrandGoldLight else TextSecondary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "Tüm Şubeler",
                                                        color = if (isAllSelected) BrandOffWhite else TextSecondary,
                                                        fontSize = 12.5.sp,
                                                        fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            }
                                        }

                                        branches.forEach { b ->
                                            val isSelected = selectedBranchId == b.id
                                            Surface(
                                                modifier = Modifier
                                                    .clickable {
                                                        selectedBranchId = b.id
                                                        if (targetScope == "branch") {
                                                            selectedPersonnelUid = ""
                                                            selectedPersonnelName = ""
                                                        }
                                                    }
                                                    .testTag("branch_chip_${b.id}"),
                                                color = if (isSelected) BrandGreenHover else DarkBg,
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, if (isSelected) BrandGold else BorderColor)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Storefront,
                                                        contentDescription = null,
                                                        tint = if (isSelected) BrandGoldLight else TextSecondary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = b.ad,
                                                        color = if (isSelected) BrandOffWhite else TextSecondary,
                                                        fontSize = 12.5.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Personel Seçimi (Eğer Personel seçilmişse)
                        if (targetScope == "user") {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Personel Seçin:",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (candidateUsers.isEmpty()) {
                                    Text("Bu kapsamda kayıtlı personel bulunamadı.", color = StatusRed, fontSize = 12.sp)
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 200.dp)
                                            .background(DarkBg, RoundedCornerShape(10.dp))
                                            .border(1.dp, BorderColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                            .padding(6.dp)
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        candidateUsers.forEach { u ->
                                            val isSelected = selectedPersonnelUid == u.uid
                                            val branchLabel = if (isAdmin) {
                                                val bName = branches.find { it.id == u.branchId }?.ad ?: ""
                                                if (bName.isNotEmpty()) " · $bName" else ""
                                            } else ""

                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedPersonnelUid = u.uid
                                                        selectedPersonnelName = u.ad.ifEmpty { u.kullaniciAdi }
                                                    }
                                                    .testTag("personnel_item_${u.uid}"),
                                                color = if (isSelected) BrandGreenHover else DarkBg,
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, if (isSelected) BrandGold.copy(alpha = 0.6f) else BorderColor.copy(alpha = 0.3f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Default.Person,
                                                            contentDescription = null,
                                                            tint = if (isSelected) BrandGoldLight else TextSecondary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = "${u.ad.ifEmpty { u.kullaniciAdi }}$branchLabel",
                                                            color = if (isSelected) BrandOffWhite else TextPrimary,
                                                            fontSize = 13.sp,
                                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                                        )
                                                    }
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = BrandGoldLight,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Seçilen Hedef Göstergesi (Net ve dikkat çekici)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("target_summary_box"),
                            color = DarkBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(BrandGreenHover.copy(alpha = 0.7f), CircleShape)
                                        .border(1.dp, BrandGold.copy(alpha = 0.4f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (targetScope) {
                                            "branch" -> Icons.Default.Storefront
                                            "user" -> Icons.Default.Person
                                            else -> Icons.Default.Campaign
                                        },
                                        contentDescription = null,
                                        tint = BrandGoldLight,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "SEÇİLEN HEDEF",
                                        color = BrandGoldLight,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = targetSummaryText,
                                        color = BrandOffWhite,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = when {
                                            targetScope == "user" && selectedPersonnelName.isEmpty() -> "Lütfen yukarıdaki listeden bir personel seçiniz"
                                            isAdmin && targetScope == "all_staff" -> "Tüm şubelerdeki personellere anlık iletilecektir"
                                            targetScope == "branch" -> "Seçilen şubedeki personellere anlık iletilecektir"
                                            targetScope == "user" -> "Yalnızca bu personele anlık bildirim iletilecektir"
                                            else -> "Şubenizdeki tüm personellere anlık iletilecektir"
                                        },
                                        color = if (targetScope == "user" && selectedPersonnelName.isEmpty()) StatusAmber else TextSecondary,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // 5. Başlık Alanı (Daha büyük ve ferah)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Duyuru Başlığı",
                                color = TextSecondary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                placeholder = {
                                    Text(
                                        "Örn: Akşam Servisi Hazırlığı ve Görev Dağılımı",
                                        color = TextSecondary.copy(alpha = 0.65f),
                                        fontSize = 14.5.sp
                                    )
                                },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BrandOffWhite
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BrandGold,
                                    unfocusedBorderColor = BorderColor,
                                    focusedLabelColor = BrandGoldLight,
                                    focusedTextColor = BrandOffWhite,
                                    unfocusedTextColor = BrandOffWhite,
                                    focusedContainerColor = DarkBg,
                                    unfocusedContainerColor = DarkBg,
                                    cursorColor = BrandGold
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("announcement_title_input")
                            )
                        }

                        // 6. Mesaj Alanı (Çok satırlı, büyük ve rahat yazılabilir)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Duyuru Mesajı",
                                    color = TextSecondary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (message.isNotEmpty()) {
                                    Text(
                                        text = "${message.length} karakter",
                                        color = TextSecondary.copy(alpha = 0.8f),
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = message,
                                onValueChange = { message = it },
                                placeholder = {
                                    Text(
                                        "Personelin ekranında ve anlık bildiriminde görünecek duyuru metnini buraya rahatça yazabilirsiniz...",
                                        color = TextSecondary.copy(alpha = 0.65f),
                                        fontSize = 14.sp,
                                        lineHeight = 21.sp
                                    )
                                },
                                minLines = 6,
                                maxLines = 14,
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                    color = BrandOffWhite
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BrandGold,
                                    unfocusedBorderColor = BorderColor,
                                    focusedLabelColor = BrandGoldLight,
                                    focusedTextColor = BrandOffWhite,
                                    unfocusedTextColor = BrandOffWhite,
                                    focusedContainerColor = DarkBg,
                                    unfocusedContainerColor = DarkBg,
                                    cursorColor = BrandGold
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("announcement_message_input")
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TargetScopeButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) BrandGreenHover else DarkBg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (isSelected) BrandGold.copy(alpha = 0.7f) else BorderColor),
        modifier = modifier
            .height(44.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) BrandGoldLight else TextSecondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                color = if (isSelected) BrandOffWhite else TextSecondary,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// --- TAB 1: NOTIFICATIONS (Manager & Waiter Common) ---
@Composable
fun NotificationsTab(viewModel: AppViewModel) {
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val userBranchId by viewModel.userBranchId.collectAsStateWithLifecycle()
    val userRole by viewModel.userRole.collectAsStateWithLifecycle()
    val activeShift by viewModel.activeShift.collectAsStateWithLifecycle()
    val currentBranch by viewModel.currentBranch.collectAsStateWithLifecycle()
    val clearedTs by viewModel.clearedNotificationTimestamp.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    var cancelCallId by remember { mutableStateOf<String?>(null) }
    var cancelReason by remember { mutableStateOf("") }
    var cancelReasonError by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val startShiftPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            viewModel.startShiftWithLocationCheck(
                context = context,
                onSuccess = { Toast.makeText(context, "Vardiya başlatıldı", Toast.LENGTH_SHORT).show() },
                onError = { msg -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
            )
        } else {
            Toast.makeText(context, "Vardiya başlatmak için konum izni gerekli", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.ensureShiftListener()
    }

    DisposableEffect(userBranchId) {
        if (userBranchId.isNotEmpty()) {
            viewModel.startCallsListener(userBranchId)
        }
        onDispose {
            viewModel.stopCallsListener()
        }
    }

    // Timer to update elapsed seconds and expire approved calls after 120s
    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = System.currentTimeMillis()
        }
    }

    // Filter calls: approved or cancelled calls disappear 120 seconds after action OR if cleared by manager
    val visibleCalls = remember(calls, currentTime, clearedTs) {
        calls.filter { call ->
            if (call.durum == "onaylandi") {
                val onayAtMs = call.onaylandiAt?.toDate()?.time ?: call.createdAt?.toDate()?.time ?: 0L
                if (clearedTs > 0L && onayAtMs <= clearedTs) {
                    false // hidden by manager clear button
                } else {
                    val diffSec = (currentTime - onayAtMs) / 1000
                    diffSec <= 120
                }
            } else if (call.durum == "iptal") {
                val iptalAtMs = call.iptalAt?.toDate()?.time ?: call.createdAt?.toDate()?.time ?: 0L
                if (clearedTs > 0L && iptalAtMs <= clearedTs) {
                    false // hidden by manager clear button
                } else {
                    val diffSec = (currentTime - iptalAtMs) / 1000
                    diffSec <= 120
                }
            } else {
                true // bekliyor calls stay indefinitely until approved or cancelled
            }
        }
    }

    val isWaiter = userRole == "garson"
    val isShiftActive = activeShift != null

    Column(modifier = Modifier.fillMaxSize()) {
        // Clear Button for Manager/Admin at top right
        if (!isWaiter) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Canlı Bildirimler",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Aktif bekleyen ve son 120sn onaylanan çağrılar",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Button(
                    onClick = { showClearDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BrandGreenHover,
                        contentColor = TextPrimary
                    ),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("clear_approved_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CleaningServices,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Temizle", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        // Shift status card for garson role at the VERY TOP
        if (isWaiter) {
            val branchLocEnabled = currentBranch?.locationCheckEnabled ?: true

            if (!isShiftActive) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .testTag("shift_card_closed"),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Location Check status badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .background(
                                    if (branchLocEnabled) BrandGreenHover else DarkSurfaceSecondary.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp)
                                )
                                .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("location_check_status_badge")
                        ) {
                            Icon(
                                imageVector = if (branchLocEnabled) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                                contentDescription = null,
                                tint = if (branchLocEnabled) StatusGreen else TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (branchLocEnabled) "Konum Doğrulaması Aktif" else "Konum Doğrulaması Kapalı",
                                color = if (branchLocEnabled) TextPrimary else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                ErrorUtils.safeUiAction(context, "Vardiya başlatılamadı") {
                                    if (!branchLocEnabled) {
                                        // Konum kontrolü kapalı: GPS izni ve API sorgusu yapılmaz
                                        viewModel.startShift(
                                            onSuccess = {
                                                Toast.makeText(context, "Vardiya başlatıldı", Toast.LENGTH_SHORT).show()
                                            },
                                            onError = { msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    } else {
                                        // Konum kontrolü açık: GPS izin ve mesafe kontrolü
                                        if (LocationUtils.hasLocationPermission(context)) {
                                            viewModel.startShiftWithLocationCheck(
                                                context = context,
                                                onSuccess = {
                                                    Toast.makeText(context, "Vardiya başlatıldı", Toast.LENGTH_SHORT).show()
                                                },
                                                onError = { msg ->
                                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                }
                                            )
                                        } else {
                                            startShiftPermissionLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                                )
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("start_shift_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandGreenHover,
                                contentColor = BrandOffWhite
                            ),
                            border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = BrandGoldLight, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Vardiyayı Başlat", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BrandOffWhite)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Vardiyanı başlatınca çağrılar görünecek",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                val isBreak = activeShift?.molada == true

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("shift_card_open"),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(if (isBreak) StatusAmber else StatusGreen, CircleShape)
                                )
                                if (isBreak) {
                                    val breakDurationText = remember(activeShift?.molaBaslangic, currentTime) {
                                        ShiftDateTimeUtils.formatLiveBreakStatus(activeShift?.molaBaslangic, currentTime)
                                    }
                                    Text(
                                        text = breakDurationText,
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    val shiftDurationText = remember(activeShift?.baslangic, currentTime) {
                                        ShiftDateTimeUtils.formatLiveShiftStatus(activeShift?.baslangic, currentTime)
                                    }
                                    Text(
                                        text = shiftDurationText,
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            if (isBreak) {
                                Surface(
                                    color = StatusAmber.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "MOLA",
                                        color = StatusAmber,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isBreak) {
                                Button(
                                    onClick = {
                                        viewModel.endBreak(
                                            onSuccess = {
                                                Toast.makeText(context, "Moladan dönüldü", Toast.LENGTH_SHORT).show()
                                            },
                                            onError = { msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = BrandGreenHover,
                                        contentColor = BrandOffWhite
                                    ),
                                    border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .testTag("end_break_button")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = BrandGoldLight, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Moladan Dön", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        viewModel.startBreak(
                                            onSuccess = {
                                                Toast.makeText(context, "Molaya çıkıldı", Toast.LENGTH_SHORT).show()
                                            },
                                            onError = { msg ->
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = BrandSecondaryForest,
                                        contentColor = BrandOffWhite
                                    ),
                                    border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .testTag("start_break_button")
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = null, tint = BrandGoldLight, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Molaya Çık", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.endShift(
                                        onSuccess = {
                                            Toast.makeText(context, "Vardiya bitirildi", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    )
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = StatusRed
                                ),
                                border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.45f)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("end_shift_button")
                            ) {
                                Text("Vardiyayı Bitir", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // Call List or Empty State
        if (isWaiter && !isShiftActive) {
            // Hide calls list when shift is closed
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Vardiya Kapalı",
                        color = TextSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else if (visibleCalls.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.NotificationsNone,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Çağrı yok",
                        color = TextSecondary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(visibleCalls, key = { it.id }) { call ->
                    val isApproved = call.durum == "onaylandi"
                    val isCancelled = call.durum == "iptal"
                    val isPending = !isApproved && !isCancelled

                    val pulseTransition = rememberInfiniteTransition(label = "badgePulse_${call.id}")
                    val pulseAlpha by if (isPending && (call.tip == "garson" || call.tip == "hesap" || call.tip.isEmpty())) {
                        pulseTransition.animateFloat(
                            initialValue = 0.50f,
                            targetValue = 0.95f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulseAlpha"
                        )
                    } else {
                        remember { mutableFloatStateOf(0.40f) }
                    }

                    val borderStroke = when {
                        isApproved -> BorderStroke(1.dp, StatusGreen.copy(alpha = 0.4f))
                        isCancelled -> BorderStroke(1.dp, StatusRed.copy(alpha = 0.4f))
                        else -> BorderStroke(1.dp, BorderColor)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("call_card_${call.id}"),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = borderStroke,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left: Table No & Elapsed time / Approval status / Cancel status
                                Column(
                                    horizontalAlignment = Alignment.Start,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = FormatUtils.formatMasaTitle(call.masaNo),
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val statusSubtext = when {
                                        isApproved -> {
                                            if (call.createdAt != null && call.onaylandiAt != null) {
                                                val sec = (call.onaylandiAt.toDate().time - call.createdAt.toDate().time) / 1000
                                                if (sec >= 0) "$sec. saniyede onaylandı" else "${call.onaylayanAd.ifEmpty { "Personel" }} onayladı"
                                            } else if (call.onaylayanAd.isNotEmpty()) {
                                                "${call.onaylayanAd} onayladı"
                                            } else {
                                                "Onaylandı"
                                            }
                                        }
                                        isCancelled -> {
                                            val iptalEden = call.iptalEdenAd.ifEmpty { "Personel" }
                                            "İptal edildi — $iptalEden"
                                        }
                                        else -> formatPendingCallTime(call.createdAt, currentTime)
                                    }

                                    Text(
                                        text = statusSubtext,
                                        color = when {
                                            isApproved -> StatusGreen
                                            isCancelled -> StatusRed
                                            else -> TextSecondary
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = if (isCancelled) FontWeight.SemiBold else FontWeight.Normal
                                    )

                                    if (isCancelled && call.iptalNedeni.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Neden: ${call.iptalNedeni}",
                                            color = StatusRed,
                                            fontSize = 11.sp,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Center: Call type Label (sakin yüzey + görünür muted/amber metin, altın border yok)
                                val badgeTextColor = when (call.tip) {
                                    "siparis" -> StatusGreen
                                    "hesap" -> StatusAmber
                                    else -> BrandOffWhite
                                }
                                val badgeLabel = when (call.tip) {
                                    "siparis" -> "Sipariş"
                                    "hesap" -> "Hesap İstiyor"
                                    else -> "Garson Çağırıyor"
                                }

                                Surface(
                                    color = BrandSecondaryForest,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, BorderColor)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                    ) {
                                        if (isPending) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .background(
                                                        color = (if (call.tip == "hesap") StatusAmber else BrandGoldLight).copy(alpha = pulseAlpha),
                                                        shape = CircleShape
                                                    )
                                            )
                                        }
                                        Text(
                                            text = badgeLabel,
                                            color = badgeTextColor,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Right: Actions or Status
                                if (isApproved) {
                                    val onaylayan = call.onaylayanAd.ifEmpty { "Personel" }
                                    Text(
                                        text = "✓ $onaylayan onayladı",
                                        color = StatusGreen,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.testTag("approved_text_${call.id}")
                                    )
                                } else if (isCancelled) {
                                    Text(
                                        text = "✕ İptal",
                                        color = StatusRed,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.testTag("cancelled_text_${call.id}")
                                    )
                                } else {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (call.tip == "siparis") {
                                            OutlinedButton(
                                                onClick = {
                                                    cancelCallId = call.id
                                                    cancelReason = ""
                                                    cancelReasonError = false
                                                },
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    contentColor = StatusRed
                                                ),
                                                border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.45f)),
                                                shape = RoundedCornerShape(10.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                modifier = Modifier
                                                    .height(40.dp)
                                                    .testTag("cancel_button_${call.id}")
                                            ) {
                                                Text("İptal", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                viewModel.approveCall(
                                                    callId = call.id,
                                                    onError = { errorMsg ->
                                                        Toast.makeText(context, "Onay hatası: $errorMsg", Toast.LENGTH_LONG).show()
                                                    }
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = BrandGreenHover,
                                                contentColor = BrandOffWhite
                                            ),
                                            border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.5f)),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                            modifier = Modifier
                                                .height(42.dp)
                                                .testTag("approve_button_${call.id}")
                                        ) {
                                            Text("Onayla", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandOffWhite)
                                        }
                                    }
                                }
                            }

                            // Body for Sipariş products list
                            if (call.tip == "siparis" && call.items.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = BorderColor, thickness = 1.dp)
                                Spacer(modifier = Modifier.height(10.dp))

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    call.items.forEach { item ->
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                text = "• ${item.adet}x ${item.urunAdi}",
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 14.sp
                                            )

                                            val hasError = item.ceviri_hata == true
                                            val noteToShow = if (hasError) {
                                                item.notOrijinal
                                            } else {
                                                item.notTr.takeIf { !it.isNullOrEmpty() } ?: item.notOrijinal
                                            }

                                            if (!noteToShow.isNullOrEmpty()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(start = 12.dp)
                                                ) {
                                                    Text(
                                                        text = "Not: $noteToShow",
                                                        color = TextSecondary,
                                                        fontSize = 12.sp,
                                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                                    )
                                                    if (hasError) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "(çevrilemedi)",
                                                            color = StatusAmber,
                                                            fontSize = 11.sp,
                                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (cancelCallId != null) {
        val targetCallId = cancelCallId!!
        AlertDialog(
            onDismissRequest = {
                cancelCallId = null
                cancelReason = ""
                cancelReasonError = false
            },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Siparişi İptal Et",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Lütfen siparişin iptal edilme nedenini giriniz:",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = cancelReason,
                        onValueChange = {
                            cancelReason = it
                            if (it.trim().isNotEmpty()) cancelReasonError = false
                        },
                        label = { Text("İptal nedeni") },
                        isError = cancelReasonError,
                        singleLine = false,
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealPrimary,
                            unfocusedBorderColor = TextSecondary,
                            focusedLabelColor = TealPrimary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            errorBorderColor = StatusRed,
                            errorLabelColor = StatusRed
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cancel_reason_input")
                    )
                    if (cancelReasonError) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Lütfen iptal nedenini giriniz.",
                            color = StatusRed,
                            fontSize = 11.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        ErrorUtils.safeUiAction(context, "Sipariş iptal edilemedi") {
                            val trimmed = cancelReason.trim()
                            if (trimmed.isEmpty()) {
                                cancelReasonError = true
                                Toast.makeText(context, "Lütfen iptal nedenini giriniz", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.cancelCall(
                                    callId = targetCallId,
                                    reason = trimmed,
                                    onSuccess = {
                                        Toast.makeText(context, "Sipariş iptal edildi", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                cancelCallId = null
                                cancelReason = ""
                                cancelReasonError = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusRed,
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_cancel_button")
                ) {
                    Text("İptal Et", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        ErrorUtils.safeUiAction(context) {
                            cancelCallId = null
                            cancelReason = ""
                            cancelReasonError = false
                        }
                    },
                    modifier = Modifier.testTag("dismiss_cancel_button")
                ) {
                    Text("Vazgeç", color = TextSecondary)
                }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = DarkSurface,
            title = { Text("Bildirimleri Temizle", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Onaylanmış bildirimler temizlensin mi?", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        ErrorUtils.safeUiAction(context, "Bildirimler temizlenemedi") {
                            viewModel.clearApprovedNotifications()
                            showClearDialog = false
                            Toast.makeText(context, "Onaylanmış bildirimler temizlendi.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreenHover, contentColor = BrandOffWhite),
                    border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.5f))
                ) {
                    Text("Temizle", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Vazgeç", color = TextSecondary)
                }
            }
        )
    }
}

// --- TAB 2: CALL HISTORY (Manager & Admin) ---
@Composable
fun CallHistoryTab(viewModel: AppViewModel) {
    val context = LocalContext.current
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val userBranchId by viewModel.userBranchId.collectAsStateWithLifecycle()

    DisposableEffect(userBranchId) {
        if (userBranchId.isNotEmpty()) {
            viewModel.startCallsListener(userBranchId)
        }
        onDispose {
            viewModel.stopCallsListener()
        }
    }

    var activeFilter by remember { mutableStateOf(StaffDateFilter.TODAY) }
    var showCustomDatePickerDialog by remember { mutableStateOf(false) }

    var customStartCal by remember {
        mutableStateOf(java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        })
    }

    var customEndCal by remember {
        mutableStateOf(java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        })
    }

    val customRangeStr = remember(customStartCal, customEndCal) {
        val startFormatted = java.text.SimpleDateFormat("d MMM", java.util.Locale("tr")).format(customStartCal.time)
        val endFormatted = java.text.SimpleDateFormat("d MMM", java.util.Locale("tr")).format(customEndCal.time)
        "$startFormatted – $endFormatted"
    }

    val (rangeStartMs, rangeEndMs) = remember(activeFilter, customStartCal, customEndCal) {
        when (activeFilter) {
            StaffDateFilter.TODAY -> {
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, Long.MAX_VALUE)
            }
            StaffDateFilter.WEEK -> {
                val cal = java.util.Calendar.getInstance()
                cal.firstDayOfWeek = java.util.Calendar.MONDAY
                cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                if (cal.timeInMillis > System.currentTimeMillis()) {
                    cal.add(java.util.Calendar.DAY_OF_MONTH, -7)
                }
                Pair(cal.timeInMillis, Long.MAX_VALUE)
            }
            StaffDateFilter.MONTH -> {
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, Long.MAX_VALUE)
            }
            StaffDateFilter.CUSTOM -> {
                Pair(customStartCal.timeInMillis, customEndCal.timeInMillis)
            }
        }
    }

    val historyCalls = remember(calls, rangeStartMs, rangeEndMs) {
        calls.filter { call ->
            (call.durum == "onaylandi" || call.durum == "iptal") && run {
                val ts = call.iptalAt?.toDate()?.time ?: call.onaylandiAt?.toDate()?.time ?: call.createdAt?.toDate()?.time ?: 0L
                ts in rangeStartMs..rangeEndMs
            }
        }.sortedByDescending { call ->
            call.iptalAt?.toDate()?.time ?: call.onaylandiAt?.toDate()?.time ?: call.createdAt?.toDate()?.time ?: 0L
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(bottom = 12.dp)) {
            Text(
                text = "Çağrı Geçmişi",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Seçilen filtredeki tüm onaylanmış ve iptal edilmiş çağrılar saklanır",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        // Date Filter Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterTabButton(
                title = "Bugün",
                isSelected = activeFilter == StaffDateFilter.TODAY,
                onClick = { activeFilter = StaffDateFilter.TODAY },
                testTag = "call_history_filter_today",
                modifier = Modifier.weight(1f)
            )
            FilterTabButton(
                title = "Bu Hafta",
                isSelected = activeFilter == StaffDateFilter.WEEK,
                onClick = { activeFilter = StaffDateFilter.WEEK },
                testTag = "call_history_filter_week",
                modifier = Modifier.weight(1f)
            )
            FilterTabButton(
                title = "Bu Ay",
                isSelected = activeFilter == StaffDateFilter.MONTH,
                onClick = { activeFilter = StaffDateFilter.MONTH },
                testTag = "call_history_filter_month",
                modifier = Modifier.weight(1f)
            )
            FilterTabButton(
                title = if (activeFilter == StaffDateFilter.CUSTOM) customRangeStr else "Tarih Seç",
                isSelected = activeFilter == StaffDateFilter.CUSTOM,
                onClick = { showCustomDatePickerDialog = true },
                testTag = "call_history_filter_custom",
                modifier = Modifier.weight(1.2f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (historyCalls.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Bu aralıkta kayıt yok",
                        color = TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            val dateTimeFormat = remember { java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale("tr")) }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(historyCalls, key = { it.id }) { call ->
                    val isCancelled = call.durum == "iptal"
                    val isSiparis = call.tip == "siparis"
                    val isHesap = call.tip == "hesap"

                    val islemTarihi = if (isCancelled) {
                        call.iptalAt?.toDate() ?: call.createdAt?.toDate()
                    } else {
                        call.onaylandiAt?.toDate() ?: call.createdAt?.toDate()
                    }
                    val tarihSaatStr = if (islemTarihi != null) dateTimeFormat.format(islemTarihi) else "-"
                    val personName = if (isCancelled) {
                        call.iptalEdenAd.ifEmpty { "Personel" }
                    } else {
                        call.onaylayanAd.ifEmpty { "Personel" }
                    }

                    val iconVector = when {
                        isCancelled -> Icons.Default.Cancel
                        isSiparis -> Icons.Default.Restaurant
                        isHesap -> Icons.Default.ReceiptLong
                        else -> Icons.Default.Notifications
                    }

                    val iconBg = when {
                        isCancelled -> StatusRed.copy(alpha = 0.15f)
                        isSiparis -> StatusGreen.copy(alpha = 0.15f)
                        else -> StatusTeal.copy(alpha = 0.15f)
                    }

                    val iconTint = when {
                        isCancelled -> StatusRed
                        isSiparis -> StatusGreen
                        else -> StatusTeal
                    }

                    val tipStr = when {
                        isCancelled -> "İptal"
                        isSiparis -> "Sipariş (${call.items.size} ürün)"
                        isHesap -> "Hesap"
                        else -> "Garson"
                    }

                    var isExpanded by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isSiparis && call.items.isNotEmpty()) {
                                ErrorUtils.safeUiAction(context) {
                                    isExpanded = !isExpanded
                                }
                            }
                            .testTag("history_call_card_${call.id}"),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left Icon
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(iconBg, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = iconVector,
                                        contentDescription = null,
                                        tint = iconTint,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = FormatUtils.formatMasaTitle(call.masaNo),
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(iconBg, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = tipStr,
                                                color = iconTint,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    if (isCancelled) {
                                        Text(
                                            text = "$personName iptal etti — $tarihSaatStr",
                                            color = StatusRed,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (call.iptalNedeni.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Neden: ${call.iptalNedeni}",
                                                color = TextSecondary,
                                                fontSize = 12.sp,
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "$personName onayladı — $tarihSaatStr",
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                if (isSiparis && call.items.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Detay",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            if (isSiparis && isExpanded && call.items.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = TextSecondary.copy(alpha = 0.2f), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(8.dp))

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    call.items.forEach { item ->
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                text = "• ${item.adet}x ${item.urunAdi}",
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 13.sp
                                            )

                                            val hasError = item.ceviri_hata == true
                                            val noteToShow = if (hasError) {
                                                item.notOrijinal
                                            } else {
                                                item.notTr.takeIf { !it.isNullOrEmpty() } ?: item.notOrijinal
                                            }

                                            if (!noteToShow.isNullOrEmpty()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(start = 12.dp)
                                                ) {
                                                    Text(
                                                        text = "Not: $noteToShow",
                                                        color = TextSecondary,
                                                        fontSize = 11.sp,
                                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                                    )
                                                    if (hasError) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "(çevrilemedi)",
                                                            color = StatusAmber,
                                                            fontSize = 10.sp,
                                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCustomDatePickerDialog) {
        CustomDateRangePickerDialog(
            initialStartCal = customStartCal,
            initialEndCal = customEndCal,
            onDismiss = { showCustomDatePickerDialog = false },
            onConfirm = { startCal, endCal ->
                customStartCal = startCal
                customEndCal = endCal
                activeFilter = StaffDateFilter.CUSTOM
                showCustomDatePickerDialog = false
            }
        )
    }
}

// --- TAB 3: WAITERS (Manager) ---
@Composable
fun WaitersTab(viewModel: AppViewModel) {
    val waiters by viewModel.waiters.collectAsStateWithLifecycle()
    val branchId by viewModel.userBranchId.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var waiterToDelete by remember { mutableStateOf<User?>(null) }

    var waiterAd by remember { mutableStateOf("") }
    var waiterUsername by remember { mutableStateOf("") }
    var waiterSifre by remember { mutableStateOf("") }
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    ErrorUtils.safeUiAction(context) {
                        waiterAd = ""
                        waiterUsername = ""
                        waiterSifre = ""
                        showAddDialog = true
                    }
                },
                containerColor = BrandGreenHover,
                contentColor = BrandOffWhite,
                shape = CircleShape,
                modifier = Modifier
                    .testTag("add_waiter_fab")
                    .border(1.dp, BrandGold.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Garson Ekle", tint = BrandOffWhite)
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(DarkBg)
        ) {
            if (waiters.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.PeopleOutline,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Henüz garson eklenmemiş",
                        color = TextSecondary,
                        fontSize = 16.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(waiters, key = { it.uid }) { waiter ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("waiter_card_${waiter.uid}"),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = BorderStroke(1.dp, BorderColor),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(BrandGreenHover, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = BrandOffWhite)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        text = waiter.ad,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = waiter.kullaniciAdi,
                                        color = TextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = {
                                        ErrorUtils.safeUiAction(context) {
                                            waiterToDelete = waiter
                                        }
                                    },
                                    modifier = Modifier.testTag("delete_waiter_button_${waiter.uid}")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Sil", tint = StatusRed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (waiterToDelete != null) {
        val targetWaiter = waiterToDelete
        AlertDialog(
            onDismissRequest = { waiterToDelete = null },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Garsonu Sil",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "${targetWaiter?.ad?.ifEmpty { targetWaiter.kullaniciAdi }} silinsin mi? Bu işlem geri alınamaz.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        ErrorUtils.safeUiAction(context, "Garson silinirken hata oluştu") {
                            val uidToDelete = targetWaiter?.uid ?: ""
                            waiterToDelete = null
                            if (uidToDelete.isNotEmpty()) {
                                viewModel.deleteUser(
                                    uid = uidToDelete,
                                    onSuccess = {
                                        Toast.makeText(context, "Garson silindi.", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = { msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = StatusRed
                    ),
                    border = BorderStroke(1.dp, StatusRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_delete_waiter_button")
                ) {
                    Text("Sil", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        ErrorUtils.safeUiAction(context) {
                            waiterToDelete = null
                        }
                    },
                    modifier = Modifier.testTag("cancel_delete_waiter_button")
                ) {
                    Text("İptal", color = TextSecondary)
                }
            }
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = DarkSurface,
            title = { Text("Garson Ekle", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = waiterAd,
                        onValueChange = { waiterAd = it },
                        label = { Text("Garson Adı") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealPrimary,
                            focusedLabelColor = TealPrimary,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("dialog_waiter_ad_input")
                    )
                    OutlinedTextField(
                        value = waiterUsername,
                        onValueChange = { waiterUsername = it.lowercase().trim() },
                        label = { Text("Kullanıcı Adı") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealPrimary,
                            focusedLabelColor = TealPrimary,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("dialog_waiter_username_input")
                    )
                    OutlinedTextField(
                        value = waiterSifre,
                        onValueChange = { waiterSifre = it },
                        label = { Text("Şifre") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealPrimary,
                            focusedLabelColor = TealPrimary,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("dialog_waiter_password_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        ErrorUtils.safeUiAction(context, "Garson eklenirken hata oluştu") {
                            val trimmedAd = waiterAd.trim()
                            val trimmedUser = waiterUsername.trim()
                            val trimmedPass = waiterSifre.trim()
                            if (trimmedAd.isEmpty() || trimmedUser.isEmpty() || trimmedPass.isEmpty()) {
                                Toast.makeText(context, "Lütfen tüm alanları doldurunuz.", Toast.LENGTH_SHORT).show()
                            } else if (branchId.isEmpty()) {
                                Toast.makeText(context, "Şube bilgisi bulunamadı.", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.addUser(
                                    branchId = branchId,
                                    ad = trimmedAd,
                                    kullaniciAdi = trimmedUser,
                                    sifre = trimmedPass,
                                    rol = "garson",
                                    onSuccess = {
                                        showAddDialog = false
                                        Toast.makeText(context, "Garson başarıyla oluşturuldu.", Toast.LENGTH_SHORT).show()
                                    },
                                    onError = {
                                        Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGreenHover, contentColor = BrandOffWhite),
                    border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.5f))
                ) {
                    Text("Oluştur", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    ErrorUtils.safeUiAction(context) {
                        showAddDialog = false
                    }
                }) {
                    Text("İptal", color = TextSecondary)
                }
            }
        )
    }
}

// --- 5.5 PERSONEL (STAFF) TAB & SHIFT HISTORY ---

enum class StaffDateFilter {
    TODAY, WEEK, MONTH, CUSTOM
}

fun getRangeStartTimestamp(filter: StaffDateFilter): Long {
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)

    when (filter) {
        StaffDateFilter.TODAY -> {
            // Today 00:00
        }
        StaffDateFilter.WEEK -> {
            cal.firstDayOfWeek = java.util.Calendar.MONDAY
            cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
            if (cal.timeInMillis > System.currentTimeMillis()) {
                cal.add(java.util.Calendar.DAY_OF_MONTH, -7)
            }
        }
        StaffDateFilter.MONTH -> {
            cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        StaffDateFilter.CUSTOM -> {
            // Custom handled in composable
        }
    }
    return cal.timeInMillis
}

@Composable
fun PersonelTab(
    viewModel: AppViewModel,
    targetBranchId: String? = null,
    modifier: Modifier = Modifier
) {
    val defaultBranchId by viewModel.userBranchId.collectAsStateWithLifecycle()
    val branchId = targetBranchId ?: defaultBranchId
    var selectedGarson by remember { mutableStateOf<User?>(null) }

    Box(modifier = modifier.fillMaxSize().background(DarkBg)) {
        if (selectedGarson == null) {
            PersonelListContent(
                viewModel = viewModel,
                branchId = branchId,
                onGarsonClick = { garson -> selectedGarson = garson }
            )
        } else {
            GarsonShiftDetailContent(
                viewModel = viewModel,
                garson = selectedGarson!!,
                onBackClick = { selectedGarson = null }
            )
        }
    }
}

@Composable
fun PersonelListContent(
    viewModel: AppViewModel,
    branchId: String,
    onGarsonClick: (User) -> Unit
) {
    val context = LocalContext.current
    val branchActiveShifts by viewModel.branchActiveShifts.collectAsStateWithLifecycle()
    var waiters by remember { mutableStateOf<List<User>>(emptyList()) }

    DisposableEffect(branchId) {
        if (branchId.isNotEmpty()) {
            viewModel.startBranchActiveShiftsListener(branchId)
            val reg = viewModel.getWaitersForBranch(branchId) { loaded ->
                waiters = loaded
            }
            onDispose {
                reg.remove()
                viewModel.stopBranchActiveShiftsListener()
            }
        } else {
            onDispose {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Personel Canlı Durumu",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (waiters.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.PeopleOutline,
                        contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Şubede kayıtlı garson bulunamadı.",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(waiters, key = { it.uid }) { u ->
                    val activeShift = branchActiveShifts[u.uid]

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("personel_item_${u.uid}")
                            .clickable {
                                ErrorUtils.safeUiAction(context) {
                                    onGarsonClick(u)
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar Icon
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(BrandGreenHover, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = BrandOffWhite,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = u.ad.ifEmpty { u.kullaniciAdi },
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "@${u.kullaniciAdi}",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Live Status Badge
                            val (badgeBg, badgeBorder, badgeText, badgeIcon) = when {
                                activeShift != null && activeShift.molada -> {
                                    Quadruple(
                                        StatusAmber.copy(alpha = 0.15f),
                                        StatusAmber,
                                        "🟠 Molada",
                                        StatusAmber
                                    )
                                }
                                activeShift != null && !activeShift.molada -> {
                                    Quadruple(
                                        StatusGreen.copy(alpha = 0.15f),
                                        StatusGreen,
                                        "🟢 Vardiyada",
                                        StatusGreen
                                    )
                                }
                                else -> {
                                    Quadruple(
                                        DarkBg,
                                        TextSecondary.copy(alpha = 0.3f),
                                        "⚫ Çevrimdışı",
                                        TextSecondary
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .background(badgeBg, RoundedCornerShape(20.dp))
                                    .border(1.dp, badgeBorder, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    color = badgeIcon,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun GarsonShiftDetailContent(
    viewModel: AppViewModel,
    garson: User,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var activeFilter by remember { mutableStateOf(StaffDateFilter.TODAY) }
    var showCustomDatePickerDialog by remember { mutableStateOf(false) }

    var customStartCal by remember {
        mutableStateOf(java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        })
    }

    var customEndCal by remember {
        mutableStateOf(java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        })
    }

    var shiftsWithBreaks by remember { mutableStateOf<List<ShiftWithBreaks>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    DisposableEffect(garson.uid) {
        val reg = viewModel.listenGarsonShiftsWithBreaks(garson.uid) { list ->
            shiftsWithBreaks = list
            isLoading = false
        }
        onDispose {
            reg.remove()
        }
    }

    val customRangeStr = remember(customStartCal, customEndCal) {
        val startFormatted = java.text.SimpleDateFormat("d MMM", java.util.Locale("tr")).format(customStartCal.time)
        val endFormatted = java.text.SimpleDateFormat("d MMM", java.util.Locale("tr")).format(customEndCal.time)
        "$startFormatted – $endFormatted"
    }

    val (rangeStartMs, rangeEndMs) = remember(activeFilter, customStartCal, customEndCal) {
        when (activeFilter) {
            StaffDateFilter.TODAY -> {
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, Long.MAX_VALUE)
            }
            StaffDateFilter.WEEK -> {
                val cal = java.util.Calendar.getInstance()
                cal.firstDayOfWeek = java.util.Calendar.MONDAY
                cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                if (cal.timeInMillis > System.currentTimeMillis()) {
                    cal.add(java.util.Calendar.DAY_OF_MONTH, -7)
                }
                Pair(cal.timeInMillis, Long.MAX_VALUE)
            }
            StaffDateFilter.MONTH -> {
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, Long.MAX_VALUE)
            }
            StaffDateFilter.CUSTOM -> {
                Pair(customStartCal.timeInMillis, customEndCal.timeInMillis)
            }
        }
    }

    val filteredShifts = remember(shiftsWithBreaks, rangeStartMs, rangeEndMs) {
        shiftsWithBreaks.filter { item ->
            val startMs = item.shift.baslangic?.toDate()?.time ?: 0L
            startMs in rangeStartMs..rangeEndMs
        }
    }

    // Total summary calculations strictly from valid timestamps
    val validShifts = remember(filteredShifts) {
        filteredShifts.filter { item ->
            val start = item.shift.baslangic?.toDate()?.time
            val end = item.shift.bitis?.toDate()?.time
            start != null && end != null && end >= start && (end - start) <= ShiftDateTimeUtils.MAX_VALID_SHIFT_DURATION_MS
        }
    }

    val totalGrossMs = remember(validShifts) {
        validShifts.sumOf { item ->
            val start = item.shift.baslangic!!.toDate().time
            val end = item.shift.bitis!!.toDate().time
            end - start
        }
    }
    val totalGrossStr = remember(totalGrossMs) {
        if (totalGrossMs > 0) ShiftDateTimeUtils.formatDurationDetailed(totalGrossMs) else "0 saniye"
    }

    val totalBreakMs = remember(filteredShifts) {
        filteredShifts.sumOf { item ->
            item.breaks.sumOf { mola ->
                val start = mola.baslangic?.toDate()?.time
                val end = mola.bitis?.toDate()?.time
                if (start != null && end != null && end >= start && (end - start) <= 24 * 3600 * 1000L) {
                    end - start
                } else 0L
            }
        }
    }
    val totalBreakStr = remember(totalBreakMs) {
        if (totalBreakMs > 0) ShiftDateTimeUtils.formatDurationDetailed(totalBreakMs) else "0 saniye"
    }

    val totalNetMs = remember(totalGrossMs, totalBreakMs) {
        (totalGrossMs - totalBreakMs).coerceAtLeast(0L)
    }
    val totalNetStr = remember(totalNetMs) {
        if (totalNetMs > 0) ShiftDateTimeUtils.formatDurationDetailed(totalNetMs) else "0 saniye"
    }

    var showGarsonPasswordDialog by remember { mutableStateOf(false) }

    if (showGarsonPasswordDialog) {
        UserPasswordDialog(
            user = garson,
            viewModel = viewModel,
            onDismiss = { showGarsonPasswordDialog = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Back Header with Staff Info and Password Management button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = {
                        ErrorUtils.safeUiAction(context) {
                            onBackClick()
                        }
                    },
                    modifier = Modifier.testTag("back_to_personel_list")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Geri",
                        tint = BrandOffWhite
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = garson.ad.ifEmpty { garson.kullaniciAdi },
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "@${garson.kullaniciAdi} — Vardiya Kayıtları",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            IconButton(
                onClick = { showGarsonPasswordDialog = true },
                modifier = Modifier.testTag("garson_detail_password_btn_${garson.uid}")
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = "Şifre Yönetimi",
                    tint = BrandGoldLight
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4 Filter Tabs (Bugün, Bu Hafta, Bu Ay, Tarih Seç)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterTabButton(
                title = "Bugün",
                isSelected = activeFilter == StaffDateFilter.TODAY,
                onClick = { activeFilter = StaffDateFilter.TODAY },
                testTag = "filter_today",
                modifier = Modifier.weight(1f)
            )
            FilterTabButton(
                title = "Bu Hafta",
                isSelected = activeFilter == StaffDateFilter.WEEK,
                onClick = { activeFilter = StaffDateFilter.WEEK },
                testTag = "filter_week",
                modifier = Modifier.weight(1f)
            )
            FilterTabButton(
                title = "Bu Ay",
                isSelected = activeFilter == StaffDateFilter.MONTH,
                onClick = { activeFilter = StaffDateFilter.MONTH },
                testTag = "filter_month",
                modifier = Modifier.weight(1f)
            )
            FilterTabButton(
                title = if (activeFilter == StaffDateFilter.CUSTOM) customRangeStr else "Tarih Seç",
                isSelected = activeFilter == StaffDateFilter.CUSTOM,
                onClick = {
                    showCustomDatePickerDialog = true
                },
                testTag = "filter_custom",
                modifier = Modifier.weight(1.2f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = BrandGold)
            }
        } else if (filteredShifts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Seçili tarih aralığında çalışma kaydı bulunamadı.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(filteredShifts, key = { it.shift.id }) { item ->
                    val shift = item.shift
                    val breaks = item.breaks
                    val isOpen = shift.durum == "acik" || (shift.bitis == null && shift.baslangic != null)

                    val dateHeaderStr = ShiftDateTimeUtils.formatShiftHeaderDate(shift.baslangic)
                    val timesDisplay = ShiftDateTimeUtils.formatShiftTimes(
                        start = shift.baslangic,
                        end = shift.bitis,
                        isOpen = isOpen
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("shift_card_${shift.id}"),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            // Date header & Gross time badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = BrandGoldLight,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = dateHeaderStr,
                                        color = BrandOffWhite,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                }

                                val (badgeBg, badgeTextColor) = when {
                                    timesDisplay.durationStr == "Veri eksik" -> Pair(StatusRed.copy(alpha = 0.15f), StatusRed)
                                    timesDisplay.durationStr == "Devam ediyor" -> Pair(StatusAmber.copy(alpha = 0.15f), StatusAmber)
                                    else -> Pair(BrandGreenHover, BrandGoldLight)
                                }

                                Box(
                                    modifier = Modifier
                                        .background(badgeBg, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = timesDisplay.durationStr,
                                        color = badgeTextColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Giriş Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Giriş: ",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(55.dp)
                                )
                                Text(
                                    text = timesDisplay.girisStr,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Çıkış Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Çıkış: ",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(55.dp)
                                )
                                Text(
                                    text = timesDisplay.cikisStr,
                                    color = if (timesDisplay.cikisStr == "Veri eksik") StatusRed else if (timesDisplay.cikisStr == "Devam ediyor") StatusAmber else TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Toplam Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Toplam: ",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(55.dp)
                                )
                                Text(
                                    text = timesDisplay.durationStr,
                                    color = if (timesDisplay.durationStr == "Veri eksik") StatusRed else if (timesDisplay.durationStr == "Devam ediyor") StatusAmber else BrandGoldLight,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Breaks sub-items
                            if (breaks.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = TextSecondary.copy(alpha = 0.2f))
                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "Molalar:",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                breaks.forEach { b ->
                                    val breakDisplay = ShiftDateTimeUtils.formatBreakDisplay(b.baslangic, b.bitis)
                                    Row(
                                        modifier = Modifier.padding(start = 6.dp, top = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "• $breakDisplay",
                                            color = StatusAmber,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Summary Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .testTag("summary_card"),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, BorderColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Text(
                    text = "Aralık Özet Bilgisi (${when (activeFilter) {
                        StaffDateFilter.TODAY -> "Bugün"
                        StaffDateFilter.WEEK -> "Bu Hafta"
                        StaffDateFilter.MONTH -> "Bu Ay"
                        StaffDateFilter.CUSTOM -> customRangeStr
                    }})",
                    color = BrandOffWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Toplam Çalışma Süresi (Brüt):", color = TextSecondary, fontSize = 13.sp)
                    Text(text = totalGrossStr, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Toplam Mola Süresi:", color = TextSecondary, fontSize = 13.sp)
                    Text(text = totalBreakStr, color = StatusAmber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Net Çalışma Süresi:", color = TextSecondary, fontSize = 13.sp)
                    Text(text = totalNetStr, color = BrandGoldLight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }

    if (showCustomDatePickerDialog) {
        CustomDateRangePickerDialog(
            initialStartCal = customStartCal,
            initialEndCal = customEndCal,
            onDismiss = { showCustomDatePickerDialog = false },
            onConfirm = { startCal, endCal ->
                customStartCal = startCal
                customEndCal = endCal
                activeFilter = StaffDateFilter.CUSTOM
                showCustomDatePickerDialog = false
            }
        )
    }
}

@Composable
fun CustomDateRangePickerDialog(
    initialStartCal: java.util.Calendar,
    initialEndCal: java.util.Calendar,
    onDismiss: () -> Unit,
    onConfirm: (java.util.Calendar, java.util.Calendar) -> Unit
) {
    val context = LocalContext.current
    var tempStartCal by remember {
        mutableStateOf(java.util.Calendar.getInstance().apply {
            timeInMillis = initialStartCal.timeInMillis
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        })
    }
    var tempEndCal by remember {
        mutableStateOf(java.util.Calendar.getInstance().apply {
            timeInMillis = initialEndCal.timeInMillis
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        })
    }

    val dateFormatter = remember { java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale("tr")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Text(
                text = "Tarih Aralığı Seçin",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Başlangıç Tarihi
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceSecondary),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Başlangıç Tarihi", color = TextSecondary, fontSize = 12.sp)
                            Text(
                                text = dateFormatter.format(tempStartCal.time),
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Button(
                            onClick = {
                                ErrorUtils.safeUiAction(context, "Tarih seçici açılamadı") {
                                    android.app.DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            val newCal = java.util.Calendar.getInstance().apply {
                                                set(year, month, dayOfMonth, 0, 0, 0)
                                                set(java.util.Calendar.MILLISECOND, 0)
                                            }
                                            tempStartCal = newCal
                                        },
                                        tempStartCal.get(java.util.Calendar.YEAR),
                                        tempStartCal.get(java.util.Calendar.MONTH),
                                        tempStartCal.get(java.util.Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandGreenHover,
                                contentColor = BrandOffWhite
                            ),
                            border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Seç", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Bitiş Tarihi
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceSecondary),
                    border = BorderStroke(1.dp, BorderColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Bitiş Tarihi", color = TextSecondary, fontSize = 12.sp)
                            Text(
                                text = dateFormatter.format(tempEndCal.time),
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Button(
                            onClick = {
                                ErrorUtils.safeUiAction(context, "Tarih seçici açılamadı") {
                                    android.app.DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            val newCal = java.util.Calendar.getInstance().apply {
                                                set(year, month, dayOfMonth, 23, 59, 59)
                                                set(java.util.Calendar.MILLISECOND, 999)
                                            }
                                            tempEndCal = newCal
                                        },
                                        tempEndCal.get(java.util.Calendar.YEAR),
                                        tempEndCal.get(java.util.Calendar.MONTH),
                                        tempEndCal.get(java.util.Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandGreenHover,
                                contentColor = BrandOffWhite
                            ),
                            border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Seç", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    ErrorUtils.safeUiAction(context) {
                        val finalStart = (if (tempStartCal.after(tempEndCal)) tempEndCal.clone() else tempStartCal.clone()) as java.util.Calendar
                        val finalEnd = (if (tempStartCal.after(tempEndCal)) tempStartCal.clone() else tempEndCal.clone()) as java.util.Calendar
                        finalStart.set(java.util.Calendar.HOUR_OF_DAY, 0)
                        finalStart.set(java.util.Calendar.MINUTE, 0)
                        finalStart.set(java.util.Calendar.SECOND, 0)
                        finalStart.set(java.util.Calendar.MILLISECOND, 0)

                        finalEnd.set(java.util.Calendar.HOUR_OF_DAY, 23)
                        finalEnd.set(java.util.Calendar.MINUTE, 59)
                        finalEnd.set(java.util.Calendar.SECOND, 59)
                        finalEnd.set(java.util.Calendar.MILLISECOND, 999)

                        onConfirm(finalStart, finalEnd)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandGreenHover,
                    contentColor = BrandOffWhite
                ),
                border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Uygula", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                ErrorUtils.safeUiAction(context) {
                    onDismiss()
                }
            }) {
                Text("İptal", color = TextSecondary)
            }
        }
    )
}

@Composable
fun FilterTabButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Button(
        onClick = {
            ErrorUtils.safeUiAction(context) {
                onClick()
            }
        },
        modifier = modifier.testTag(testTag),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) BrandGreenHover else DarkSurface,
            contentColor = if (isSelected) BrandOffWhite else TextSecondary
        ),
        border = BorderStroke(1.dp, if (isSelected) BrandGold.copy(alpha = 0.5f) else BorderColor),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// --- 5.6 ANALYTICS TAB ---
@Composable
fun AnalyticsTab(
    viewModel: AppViewModel,
    siteUrl: String = WORDPRESS_BASE_URL
) {
    val context = LocalContext.current
    var activeFilter by remember { mutableStateOf(StaffDateFilter.TODAY) }
    var showCustomDatePickerDialog by remember { mutableStateOf(false) }

    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val userBranchId by viewModel.userBranchId.collectAsStateWithLifecycle()

    LaunchedEffect(userBranchId) {
        if (userBranchId.isNotEmpty()) {
            viewModel.startCallsListener(userBranchId)
        }
    }

    var customStartCal by remember {
        mutableStateOf(java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        })
    }

    var customEndCal by remember {
        mutableStateOf(java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        })
    }

    val customRangeStr = remember(customStartCal, customEndCal) {
        val startFormatted = java.text.SimpleDateFormat("d MMM", java.util.Locale("tr")).format(customStartCal.time)
        val endFormatted = java.text.SimpleDateFormat("d MMM", java.util.Locale("tr")).format(customEndCal.time)
        "$startFormatted – $endFormatted"
    }

    val (baslangicStr, bitisStr) = remember(activeFilter, customStartCal, customEndCal) {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val todayCal = java.util.Calendar.getInstance()
        val todayStr = sdf.format(todayCal.time)

        when (activeFilter) {
            StaffDateFilter.TODAY -> {
                Pair(todayStr, todayStr)
            }
            StaffDateFilter.WEEK -> {
                val cal = java.util.Calendar.getInstance()
                cal.firstDayOfWeek = java.util.Calendar.MONDAY
                cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
                if (cal.after(todayCal)) {
                    cal.add(java.util.Calendar.DAY_OF_MONTH, -7)
                }
                Pair(sdf.format(cal.time), todayStr)
            }
            StaffDateFilter.MONTH -> {
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                Pair(sdf.format(cal.time), todayStr)
            }
            StaffDateFilter.CUSTOM -> {
                Pair(sdf.format(customStartCal.time), sdf.format(customEndCal.time))
            }
        }
    }

    val (rangeStartMs, rangeEndMs) = remember(activeFilter, customStartCal, customEndCal) {
        when (activeFilter) {
            StaffDateFilter.TODAY -> {
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, Long.MAX_VALUE)
            }
            StaffDateFilter.WEEK -> {
                val cal = java.util.Calendar.getInstance()
                cal.firstDayOfWeek = java.util.Calendar.MONDAY
                cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                if (cal.timeInMillis > System.currentTimeMillis()) {
                    cal.add(java.util.Calendar.DAY_OF_MONTH, -7)
                }
                Pair(cal.timeInMillis, Long.MAX_VALUE)
            }
            StaffDateFilter.MONTH -> {
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, Long.MAX_VALUE)
            }
            StaffDateFilter.CUSTOM -> {
                Pair(customStartCal.timeInMillis, customEndCal.timeInMillis)
            }
        }
    }

    val tableSiparisCalls = remember(calls, rangeStartMs, rangeEndMs, userBranchId) {
        calls.filter { call ->
            (userBranchId.isEmpty() || call.branchId == userBranchId) &&
            call.tip == "siparis" && run {
                val ts = call.createdAt?.toDate()?.time ?: 0L
                ts in rangeStartMs..rangeEndMs
            }
        }
    }

    val groupedTableOrders = remember(tableSiparisCalls) {
        tableSiparisCalls.groupBy { it.masaNo }
            .mapValues { entry -> entry.value.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L } }
            .toList()
            .sortedBy { (masaNo, _) -> masaNo.toIntOrNull() ?: Int.MAX_VALUE }
    }

    var analyticsData by remember { mutableStateOf<AnalyticsResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadAnalytics() {
        isLoading = true
        errorMessage = null
        viewModel.fetchAnalytics(
            baslangic = baslangicStr,
            bitis = bitisStr,
            siteUrl = siteUrl
        ) { data, err ->
            isLoading = false
            if (err != null) {
                errorMessage = err
            } else {
                analyticsData = data
            }
        }
    }

    LaunchedEffect(baslangicStr, bitisStr, siteUrl) {
        loadAnalytics()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Top Header with Refresh Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Analiz Özeti",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            IconButton(
                onClick = {
                    ErrorUtils.safeUiAction(context, "Analiz yenilenemedi") {
                        loadAnalytics()
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.testTag("refresh_analytics_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = BrandGold,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Yenile",
                        tint = BrandOffWhite
                    )
                }
            }
        }

        // Filter Tabs (Bugün, Bu Hafta, Bu Ay, Tarih Seç)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterTabButton(
                title = "Bugün",
                isSelected = activeFilter == StaffDateFilter.TODAY,
                onClick = { activeFilter = StaffDateFilter.TODAY },
                testTag = "analytics_filter_today",
                modifier = Modifier.widthIn(min = 72.dp)
            )
            FilterTabButton(
                title = "Bu Hafta",
                isSelected = activeFilter == StaffDateFilter.WEEK,
                onClick = { activeFilter = StaffDateFilter.WEEK },
                testTag = "analytics_filter_week",
                modifier = Modifier.widthIn(min = 84.dp)
            )
            FilterTabButton(
                title = "Bu Ay",
                isSelected = activeFilter == StaffDateFilter.MONTH,
                onClick = { activeFilter = StaffDateFilter.MONTH },
                testTag = "analytics_filter_month",
                modifier = Modifier.widthIn(min = 76.dp)
            )
            FilterTabButton(
                title = if (activeFilter == StaffDateFilter.CUSTOM) customRangeStr else "Tarih Seç",
                isSelected = activeFilter == StaffDateFilter.CUSTOM,
                onClick = {
                    ErrorUtils.safeUiAction(context) {
                        showCustomDatePickerDialog = true
                    }
                },
                testTag = "analytics_filter_custom",
                modifier = Modifier.widthIn(min = 90.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = BrandGold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Analiz verileri yükleniyor...", color = TextSecondary, fontSize = 14.sp)
                }
            }
        } else if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.CloudOff,
                        contentDescription = null,
                        tint = BrandGoldLight.copy(alpha = 0.85f),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage ?: "Sunucu yanıt vermedi, lütfen tekrar deneyin.",
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            ErrorUtils.safeUiAction(context) {
                                loadAnalytics()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandGreenHover,
                            contentColor = BrandOffWhite
                        ),
                        border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Tekrar Dene", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            val data = analyticsData
            val hasData = data != null && (
                data.toplamGoruntulenme > 0 ||
                data.tekilZiyaretci > 0 ||
                data.toplamTiklama > 0 ||
                data.enCok.isNotEmpty() ||
                data.enAz.isNotEmpty() ||
                data.masalar.isNotEmpty()
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                // Masa Bazlı Siparişler
                if (groupedTableOrders.isNotEmpty()) {
                    item {
                        AnalyticsTableOrdersSection(groupedOrders = groupedTableOrders)
                    }
                }

                if (!hasData && groupedTableOrders.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.BarChart,
                                    contentDescription = null,
                                    tint = BrandTextMuted.copy(alpha = 0.5f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Henüz Veri Yok",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Seçilen tarih aralığında sipariş veya menü görüntüleme kaydı bulunmuyor.",
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                    }
                }

                if (hasData) {
                    // Top 3 Summary Cards
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AnalyticsSummaryCard(
                                title = "Görüntülenme",
                                value = data!!.toplamGoruntulenme.toString(),
                                icon = Icons.Default.Visibility,
                                modifier = Modifier.weight(1f)
                            )
                            AnalyticsSummaryCard(
                                title = "Tekil Ziyaretçi",
                                value = data.tekilZiyaretci.toString(),
                                icon = Icons.Default.Person,
                                modifier = Modifier.weight(1f)
                            )
                            AnalyticsSummaryCard(
                                title = "Ürün Tıklaması",
                                value = data.toplamTiklama.toString(),
                                icon = Icons.Default.TouchApp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // En Çok İlgi Gören Ürünler
                    if (data!!.enCok.isNotEmpty()) {
                        item {
                            AnalyticsProductListSection(
                                title = "En Çok İlgi Gören Ürünler",
                                icon = Icons.Default.TrendingUp,
                                items = data.enCok.take(10)
                            )
                        }
                    }

                    // En Az İlgi Gören Ürünler
                    if (data.enAz.isNotEmpty()) {
                        item {
                            AnalyticsProductListSection(
                                title = "En Az İlgi Gören Ürünler",
                                icon = Icons.Default.TrendingDown,
                                items = data.enAz.take(10)
                            )
                        }
                    }

                    // Masa Bazlı Trafik
                    if (data.masalar.isNotEmpty()) {
                        item {
                            AnalyticsTableListSection(
                                title = "Masa Bazlı Trafik",
                                icon = Icons.Default.TableRestaurant,
                                items = data.masalar
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCustomDatePickerDialog) {
        CustomDateRangePickerDialog(
            initialStartCal = customStartCal,
            initialEndCal = customEndCal,
            onDismiss = {
                ErrorUtils.safeUiAction(context) {
                    showCustomDatePickerDialog = false
                }
            },
            onConfirm = { startCal, endCal ->
                ErrorUtils.safeUiAction(context) {
                    customStartCal = startCal
                    customEndCal = endCal
                    activeFilter = StaffDateFilter.CUSTOM
                    showCustomDatePickerDialog = false
                }
            }
        )
    }
}

@Composable
fun AnalyticsTableOrdersSection(
    groupedOrders: List<Pair<String, List<Call>>>
) {
    val context = LocalContext.current
    var expandedMasaNos by remember { mutableStateOf(setOf<String>()) }
    val dateTimeFormat = remember { java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale("tr")) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("analytics_table_orders_section"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(BrandGreenHover, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Restaurant,
                        contentDescription = null,
                        tint = BrandOffWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Masa Bazlı Siparişler",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    val totalSiparis = groupedOrders.sumOf { it.second.size }
                    Text(
                        text = "${groupedOrders.size} masa · toplam $totalSiparis sipariş",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (groupedOrders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Bu tarih aralığında sipariş bulunamadı.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedOrders.forEach { (masaNo, orders) ->
                        val isExpanded = expandedMasaNos.contains(masaNo)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ErrorUtils.safeUiAction(context) {
                                        expandedMasaNos = if (isExpanded) {
                                            expandedMasaNos - masaNo
                                        } else {
                                            expandedMasaNos + masaNo
                                        }
                                    }
                                }
                                .testTag("table_orders_row_$masaNo"),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (isExpanded) BrandGold.copy(alpha = 0.35f) else BorderColor)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${FormatUtils.formatMasaTitle(masaNo)} — ${orders.size} sipariş",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )

                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (isExpanded) "Daralt" else "Genişlet",
                                        tint = if (isExpanded) BrandOffWhite else TextSecondary
                                    )
                                }

                                if (isExpanded) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = BorderColor, thickness = 1.dp)
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        orders.forEach { call ->
                                            val isApproved = call.durum == "onaylandi"
                                            val isCancelled = call.durum == "iptal"

                                            val statusBg = when {
                                                isApproved -> StatusGreen.copy(alpha = 0.15f)
                                                isCancelled -> StatusRed.copy(alpha = 0.15f)
                                                else -> BrandGreenHover
                                            }
                                            val statusColor = when {
                                                isApproved -> StatusGreen
                                                isCancelled -> StatusRed
                                                else -> BrandOffWhite
                                            }
                                            val statusText = when {
                                                isApproved -> if (call.onaylayanAd.isNotEmpty()) "Onaylandı (${call.onaylayanAd})" else "Onaylandı"
                                                isCancelled -> if (call.iptalEdenAd.isNotEmpty()) "İptal (${call.iptalEdenAd})" else "İptal"
                                                else -> "Bekliyor"
                                            }

                                            val callDate = call.createdAt?.toDate()
                                            val dateStr = if (callDate != null) dateTimeFormat.format(callDate) else "-"

                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceSecondary),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, BorderColor)
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(10.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .background(statusBg, RoundedCornerShape(4.dp))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = statusText,
                                                                color = statusColor,
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }

                                                        Text(
                                                            text = dateStr,
                                                            color = TextSecondary,
                                                            fontSize = 11.sp
                                                        )
                                                    }

                                                    if (isCancelled && call.iptalNedeni.isNotEmpty()) {
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = "İptal Nedeni: ${call.iptalNedeni}",
                                                            color = StatusRed.copy(alpha = 0.85f),
                                                            fontSize = 11.sp,
                                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.height(8.dp))

                                                    // Items
                                                    Column(
                                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        call.items.forEach { item ->
                                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                                Text(
                                                                    text = "${item.adet}x ${item.urunAdi}",
                                                                    color = TextPrimary,
                                                                    fontSize = 13.sp,
                                                                    fontWeight = FontWeight.Medium
                                                                )
                                                                val noteToShow = item.notTr?.takeIf { it.isNotBlank() } ?: item.notOrijinal
                                                                if (!noteToShow.isNullOrBlank()) {
                                                                    Text(
                                                                        text = "Not: $noteToShow",
                                                                        color = TealAccent,
                                                                        fontSize = 11.sp,
                                                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnalyticsSummaryCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TealPrimary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                color = TextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AnalyticsProductListSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    items: List<AnalyticsProductItem>
) {
    val maxClicks = remember(items) { (items.maxOfOrNull { it.tiklamaAdedi } ?: 1).coerceAtLeast(1) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = BrandGoldLight, modifier = Modifier.size(20.dp))
                Text(
                    text = title,
                    color = BrandOffWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        color = BorderColor,
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                val progress = (item.tiklamaAdedi.toFloat() / maxClicks.toFloat()).coerceIn(0.05f, 1f)

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.urunAd.ifEmpty { "İsimsiz Ürün" },
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (item.kategori.isNotEmpty()) {
                                Text(
                                    text = item.kategori,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            text = "${item.tiklamaAdedi} tık",
                            color = BrandTextMuted,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Calm Emerald Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(DarkSurfaceSecondary)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .clip(RoundedCornerShape(3.dp))
                                .background(StatusGreen)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnalyticsTableListSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    items: List<AnalyticsTableItem>
) {
    val maxViews = remember(items) { (items.maxOfOrNull { it.goruntulenmeAdedi } ?: 1).coerceAtLeast(1) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = BrandGoldLight, modifier = Modifier.size(20.dp))
                Text(
                    text = title,
                    color = BrandOffWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            items.forEachIndexed { index, item ->
                if (index > 0) {
                    HorizontalDivider(
                        color = BorderColor,
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                val progress = (item.goruntulenmeAdedi.toFloat() / maxViews.toFloat()).coerceIn(0.05f, 1f)

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.masaAd.ifEmpty { "İsimsiz Masa" },
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${item.goruntulenmeAdedi} açılış",
                            color = BrandTextMuted,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Calm Emerald Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(DarkSurfaceSecondary)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .clip(RoundedCornerShape(3.dp))
                                .background(StatusGreen)
                        )
                    }
                }
            }
        }
    }
}

