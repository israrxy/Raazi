package com.israrxy.raazi.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush

// Sonic Palette (Tailwind Zinc & Emerald)
val Zinc950 = Color(0xFF09090B) // Main Background
val Zinc900 = Color(0xFF18181B) // Surface / Cards
val Zinc800 = Color(0xFF27272A) // Borders / Accents
val Zinc700 = Color(0xFF3F3F46) // Secondary Elements
val Zinc600 = Color(0xFF52525B) // Muted Text
val Zinc500 = Color(0xFF71717A) // Disabled / Hints
val Zinc400 = Color(0xFFA1A1AA) // Secondary Text
val Zinc300 = Color(0xFFD4D4D8)
val Zinc200 = Color(0xFFE4E4E7)
val Zinc100 = Color(0xFFF4F4F5)
val Zinc50 = Color(0xFFFAFAFA)  // Primary Text

val Emerald500 = Color(0xFF10B981) // Primary Accent
val Emerald400 = Color(0xFF34D399) // Lighter Accent
val Emerald600 = Color(0xFF059669)

val ErrorRed = Color(0xFFEF4444)
val OverlayBlack = Color(0xCC000000)

// Backward Compatibility Aliases for existing code that hasn't been refactored yet
val PureBlack = Zinc950
val DeepestCharcoal = Zinc800
val SurfaceDark = Zinc900
val ElectricViolet = Emerald500 // Mapping old primary to new emerald
val ElectricVioletDark = Emerald600
val NeonPink = Color(0xFFFF2E63)
val NeonCyan = Zinc200
val TextPrimary = Zinc50
val TextSecondary = Zinc400
val TextMuted = Zinc600

// Gradients
val PlayerGradientStart = Color(0xFF064E3B) // Deep Emerald for player background

val MainGradient = Brush.verticalGradient(
    colors = listOf(Zinc950, Zinc900)
)