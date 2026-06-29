package com.tvhanan.ui.theme

import androidx.compose.ui.graphics.Color

// ===== Base =====
val BgBase = Color(0xFF0D0E12)

// ===== Glass surface tokens (pengganti DpadGray/DarkSurface solid) =====
val GlassSurface = Color(0x0FFFFFFF)        // white alpha ~6%
val GlassSurfacePressed = Color(0x24FFFFFF) // white alpha ~14%
val GlassBorder = Color(0x1FFFFFFF)         // white alpha ~12%
val GlassBorderStrong = Color(0x3DFFFFFF)   // white alpha ~24%

// ===== Text =====
val TextPrimary = Color(0xFFF2F3F5)
val TextDim = Color(0x80F2F3F5)   // alpha ~50%
val TextFaint = Color(0x52F2F3F5) // alpha ~32%

// ===== Power gradient (pengganti PowerRed flat) =====
val PowerGradientStart = Color(0xFFFF6B4A)
val PowerGradientEnd = Color(0xFFFF3D7A)

// ===== Navigation / D-pad accent gradient (signature element) =====
val NavAccent = Color(0xFF3DD9C4)  // teal
val NavAccent2 = Color(0xFF2E8FFF) // blue

// ===== Media transport accent =====
val MediaAccent = Color(0xFFC99BFF)
val MediaAccent2 = Color(0xFF6E7BFF)

// ===== Status =====
val ConnectedColor = Color(0xFF3DD9C4)
val ConnectingColor = Color(0xFFFFC857)
val DisconnectedColor = Color(0xFFFF5A5A)

// ===== Smart Hub color keys (representasi warna asli remote fisik) =====
val ColorKeyRed = Color(0xFFFF8A8A)
val ColorKeyGreen = Color(0xFF7CE8A4)
val ColorKeyYellow = Color(0xFFFFD98C)
val ColorKeyBlue = Color(0xFF8FC0FF)

// ===== Mesh gradient blobs — dipakai sekali oleh MeshBackground =====
val MeshBlob1 = Color(0xFF2A4980)
val MeshBlob2 = Color(0xFF5C2A66)
val MeshBlob3 = Color(0xFF1F5C66)
val MeshBlob4 = Color(0xFF4B2E73)

// ===== Warning/accent angka =====
val AccentWarn = Color(0xFFFFC857)

// ===== App shortcut brand colors (dipakai subtle, alpha rendah) =====
val NetflixRed = Color(0xFFFF6B6B)
val PrimeBlue = Color(0xFF6FD2F2)
val YoutubeRed = Color(0xFFFF8080)
