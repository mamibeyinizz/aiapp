package com.example.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.utils.ErrorUtils
import com.example.utils.SoundSettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var garsonSound by remember { mutableStateOf(SoundSettingsManager.getSoundForCallType(context, "garson")) }
    var hesapSound by remember { mutableStateOf(SoundSettingsManager.getSoundForCallType(context, "hesap")) }
    var siparisSound by remember { mutableStateOf(SoundSettingsManager.getSoundForCallType(context, "siparis")) }

    var volume by remember { mutableFloatStateOf(SoundSettingsManager.getVolume(context).toFloat()) }
    var vibrationEnabled by remember { mutableStateOf(SoundSettingsManager.isVibrationEnabled(context)) }

    Scaffold(
        modifier = modifier.background(DarkBg),
        containerColor = DarkBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Bildirim ve Ses Ayarları",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            ErrorUtils.safeUiAction(context) {
                                onBackClick()
                            }
                        },
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = TealPrimary
                        )
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section Header: Call Sounds
            Text(
                text = "ÇAĞRI TİPİNE GÖRE BİLDİRİM SESLERİ",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TealAccent,
                letterSpacing = 1.sp
            )

            // Garson Call Sound Card
            CallTypeSoundCard(
                title = "Garson Çağrısı Sesi",
                description = "Masa garson çağırdığında çalacak ses",
                icon = Icons.Default.Person,
                selectedSoundId = garsonSound,
                volume = volume.toInt(),
                testTagPrefix = "garson",
                onSoundSelected = { soundId ->
                    ErrorUtils.safeUiAction(context) {
                        garsonSound = soundId
                        SoundSettingsManager.setSoundForCallType(context, "garson", soundId)
                    }
                }
            )

            // Hesap Call Sound Card
            CallTypeSoundCard(
                title = "Hesap İsteği Sesi",
                description = "Masa hesap istediğinde çalacak ses",
                icon = Icons.Default.ReceiptLong,
                selectedSoundId = hesapSound,
                volume = volume.toInt(),
                testTagPrefix = "hesap",
                onSoundSelected = { soundId ->
                    ErrorUtils.safeUiAction(context) {
                        hesapSound = soundId
                        SoundSettingsManager.setSoundForCallType(context, "hesap", soundId)
                    }
                }
            )

            // Siparis Call Sound Card
            CallTypeSoundCard(
                title = "Sipariş Sesi",
                description = "Yeni sipariş verildiğinde çalacak ses",
                icon = Icons.Default.ShoppingBag,
                selectedSoundId = siparisSound,
                volume = volume.toInt(),
                testTagPrefix = "siparis",
                onSoundSelected = { soundId ->
                    ErrorUtils.safeUiAction(context) {
                        siparisSound = soundId
                        SoundSettingsManager.setSoundForCallType(context, "siparis", soundId)
                    }
                }
            )

            // Section Header: Volume & Vibration
            Text(
                text = "SES SEVİYESİ VE TİTREŞİM",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TealAccent,
                letterSpacing = 1.sp
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Volume Control
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(BrandGreenHover, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (volume == 0f) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = BrandOffWhite
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Bildirim Ses Seviyesi",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "%${volume.toInt()}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = BrandGoldLight
                                )
                            }
                            Text(
                                text = "Bildirim sesi için genel ses düzeyi",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Slider(
                        value = volume,
                        onValueChange = { newVal ->
                            ErrorUtils.safeUiAction(context) {
                                volume = newVal
                                SoundSettingsManager.setVolume(context, newVal.toInt())
                            }
                        },
                        valueRange = 0f..100f,
                        steps = 100,
                        colors = SliderDefaults.colors(
                            thumbColor = BrandGoldLight,
                            activeTrackColor = BrandGreenHover,
                            inactiveTrackColor = DarkSurfaceSecondary
                        ),
                        modifier = Modifier.testTag("volume_slider")
                    )

                    HorizontalDivider(color = BorderColor)

                    // Vibration Control
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(BrandGreenHover, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Vibration,
                                    contentDescription = null,
                                    tint = BrandOffWhite
                                )
                            }

                            Column {
                                Text(
                                    text = "Titreşim",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Çağrı geldiğinde cihaz titresin",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Switch(
                            checked = vibrationEnabled,
                            onCheckedChange = { isChecked ->
                                ErrorUtils.safeUiAction(context) {
                                    vibrationEnabled = isChecked
                                    SoundSettingsManager.setVibrationEnabled(context, isChecked)
                                    if (isChecked) {
                                        SoundSettingsManager.vibrateDevice(context)
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BrandOffWhite,
                                checkedTrackColor = StatusGreen,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = DarkSurfaceSecondary
                            ),
                            modifier = Modifier.testTag("vibration_switch")
                        )
                    }
                }
            }

            // Overall Sample Test Button
            Button(
                onClick = {
                    ErrorUtils.safeUiAction(context) {
                        SoundSettingsManager.playSoundAndVibrateForCallType(context, "garson")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("test_sample_call_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandGreenHover,
                    contentColor = BrandOffWhite
                ),
                border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = BrandGoldLight,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Örnek Bildirim ve Titreşimi Test Et",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CallTypeSoundCard(
    title: String,
    description: String,
    icon: ImageVector,
    selectedSoundId: String,
    volume: Int,
    testTagPrefix: String,
    onSoundSelected: (String) -> Unit
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    val currentOption = SoundSettingsManager.SOUND_OPTIONS.find { it.id == selectedSoundId }
        ?: SoundSettingsManager.SOUND_OPTIONS.first()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(BrandGreenHover, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = BrandOffWhite
                        )
                    }

                    Column {
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = description,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                // Listen / Test Button - Subtle Gold Accent Border
                OutlinedButton(
                    onClick = {
                        ErrorUtils.safeUiAction(context) {
                            if (selectedSoundId != "silent") {
                                SoundSettingsManager.playSoundById(context, selectedSoundId, volume)
                            }
                        }
                    },
                    modifier = Modifier.testTag("test_sound_${testTagPrefix}_button"),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Dinle",
                        tint = BrandGoldLight,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Dinle",
                        color = BrandOffWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Sound Selector Box / Dropdown
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkSurfaceSecondary)
                    .clickable { isExpanded = !isExpanded }
                    .padding(12.dp)
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
                        Icon(
                            imageVector = if (selectedSoundId == "silent") Icons.Default.VolumeMute else Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = if (selectedSoundId == "silent") TextSecondary else BrandOffWhite,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = currentOption.name,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                }
            }

            // Expanded Options
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkBg)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SoundSettingsManager.SOUND_OPTIONS.forEach { option ->
                        val isSelected = option.id == selectedSoundId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) BrandGreenHover else Color.Transparent)
                                .clickable {
                                    ErrorUtils.safeUiAction(context) {
                                        onSoundSelected(option.id)
                                        isExpanded = false
                                        if (option.id != "silent") {
                                            SoundSettingsManager.playSoundById(context, option.id, volume)
                                        }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = option.name,
                                color = if (isSelected) BrandOffWhite else TextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Seçildi",
                                    tint = BrandGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
