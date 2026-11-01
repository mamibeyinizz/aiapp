package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// --- QR MENU OFFICIAL BRAND DESIGN SYSTEM (2025/2026 Source of Truth) ---
val BrandDeepForest = Color(0xFF0D2B22)
val BrandSecondaryForest = Color(0xFF173226)
val BrandGreenHover = Color(0xFF1E3D2F)
val BrandGold = Color(0xFFC9A84C)
val BrandGoldLight = Color(0xFFE8C766)
val BrandOffWhite = Color(0xFFF4F1E8)
val BrandDarkBg = Color(0xFF080908)
val BrandTextMuted = Color(0xFFA8BCA1) // Calm sage tone with 7.2:1 contrast against BrandDeepForest (WCAG AA compliant)
val BrandInputBorderUnfocused = Color(0xFF2A4237) // Restrained non-text contrast

// --- GLOBAL THEME TOKENS (UNIFIED ACROSS THE APP) ---
val DarkBg = BrandDeepForest
val DarkSurface = BrandSecondaryForest
val DarkSurfaceSecondary = BrandGreenHover
val BrandSurfaceElevated = BrandGreenHover

// Primary Operational Tokens (Calm, understated & professional)
// Gold is reserved ONLY as an accent (BrandGold, BrandGoldLight).
// General operational components use calm forest tones, off-white, and sage.
val OperationalPrimary = BrandGreenHover
val OperationalAccent = BrandTextMuted

// Legacy compatibility aliases - mapped to calm neutral/brand values to prevent gold flood
val TealPrimary = BrandOffWhite // Clean, calm primary foreground / text / icon
val TealAccent = BrandTextMuted // Calm secondary sage accent

val GoldPrimary = BrandGold
val GoldAccent = BrandGoldLight

val TextPrimary = BrandOffWhite
val TextSecondary = BrandTextMuted

// Semantic Status Tokens harmonious with Deep Forest & Gold system - Muted & Restrained
val StatusTeal = BrandGreenHover // for subtle badges / muted backgrounds
val StatusGreen = Color(0xFF349B64) // Calm organic sage-emerald for success / active states
val StatusRed = Color(0xFFB84A4A) // Restrained terracotta-crimson for destructive actions only
val StatusAmber = Color(0xFFC8923C) // Warm muted amber for break/mola state

val ErrorColor = StatusRed
val BrandBorderSubtle = Color(0xFF243D32) // Low-contrast deep forest border (organic & calm)
val BorderColor = BrandBorderSubtle
val InputBorderUnfocused = BrandInputBorderUnfocused
