package com.tvhanan.ui.remote

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.SettingsInputHdmi
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tvhanan.domain.model.ConnectionState
import com.tvhanan.domain.model.RemoteKey
import com.tvhanan.ui.components.DpadRing
import com.tvhanan.ui.components.HapticGlassButton
import com.tvhanan.ui.components.HapticGlassLabelButton
import com.tvhanan.ui.components.MeshGradientBackground
import com.tvhanan.ui.components.ZoneLabel
import com.tvhanan.ui.theme.*
import com.tvhanan.util.HapticUtil

/**
 * Layar remote utama. Dibagi 9 zona sesuai preview yang disepakati,
 * ditampilkan via LazyColumn + stickyHeader bawaan Compose Foundation
 * supaya status bar + tombol Settings selalu terlihat saat scroll panjang ke bawah.
 *
 * @param scaleFactor faktor skala ukuran tombol, berasal dari SettingsViewModel.
 */

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun RemoteScreen(
    viewModel: RemoteViewModel,
    onOpenSettings: () -> Unit,
    scaleFactor: Float = 1f,
    keepScreenOn: Boolean = true,
    hapticEnabled: Boolean = true,           // Parameter baru untuk sinkronisasi getar
    meshBackgroundEnabled: Boolean = true     // Parameter baru untuk sinkronisasi aurora
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val isMacAvailable by viewModel.isMacAvailable.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // SINKRONISASI GETAR: Hubungkan setelan dinamis haptic ke utility getar
    LaunchedEffect(hapticEnabled) {
        HapticUtil.isEnabled = hapticEnabled
    }

    // Set Flag Window agar layar HP tidak meredup/mati 
    DisposableEffect(keepScreenOn) {
        val window = context.findActivity()?.window
        if (keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.connect()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.disconnect()
        }
    }

    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize().background(BgBase)) {
        if (meshBackgroundEnabled) {
            MeshGradientBackground(modifier = Modifier.fillMaxSize())
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy((18 * scaleFactor).dp)
        ) {
            item(key = "header") {
                RemoteHeaderBar(
                    connectionState = connectionState,
                    isMacAvailable = isMacAvailable,
                    onSettingsClick = onOpenSettings
                )
            }

            if (connectionState == ConnectionState.ERROR || connectionState == ConnectionState.DISCONNECTED) {
                if (isMacAvailable) {
                    item(key = "standby_banner") {
                        StandbyBanner(
                            onWakeClick = { viewModel.wakeOnLan() },
                            scaleFactor = scaleFactor
                        )
                    }
                } else {
                    errorMessage?.let { message ->
                        item(key = "error_banner") { ErrorBanner(message = message, onRetry = { viewModel.connect() }) }
                    }
                }
            }

            item(key = "power_row") { PowerSourceSleepRow(viewModel, scaleFactor) }

            item(key = "navigation") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ZoneLabel("Navigasi", accentColor = NavAccent)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        DpadRing(
                            onUp = { viewModel.sendKey(RemoteKey.DPAD_UP) },
                            onDown = { viewModel.sendKey(RemoteKey.DPAD_DOWN) },
                            onLeft = { viewModel.sendKey(RemoteKey.DPAD_LEFT) },
                            onRight = { viewModel.sendKey(RemoteKey.DPAD_RIGHT) },
                            onOk = { viewModel.sendKey(RemoteKey.ENTER) },
                            size = (216 * scaleFactor).dp
                        )
                    }
                    BackHomeExitRow(viewModel, scaleFactor)
                }
            }

            item(key = "volume_channel") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ZoneLabel("Volume & Channel", accentColor = NavAccent2)
                    VolumeChannelSection(viewModel, scaleFactor)
                }
            }

            item(key = "numpad") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZoneLabel("Angka", accentColor = AccentWarn)
                    NumpadGrid(viewModel, scaleFactor)
                }
            }

            item(key = "color_keys") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZoneLabel(
                        "Smart Hub Color Keys",
                        accentBrush = Brush.horizontalGradient(listOf(ColorKeyRed, ColorKeyGreen))
                    )
                    ColorKeysRow(viewModel, scaleFactor)
                }
            }

            item(key = "media") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZoneLabel("Media", accentColor = MediaAccent)
                    MediaTransportRow(viewModel, scaleFactor)
                }
            }

            item(key = "menu_info") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZoneLabel("Menu & Info", accentColor = TextDim)
                    MenuInfoGrid(viewModel, scaleFactor)
                }
            }

            item(key = "app_shortcuts") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZoneLabel(
                        "App Pintasan",
                        accentBrush = Brush.horizontalGradient(listOf(NetflixRed, PrimeBlue))
                    )
                    AppShortcutsRow(viewModel, scaleFactor)
                }
            }
        }
    }
}

@Composable
private fun RemoteHeaderBar(
    connectionState: ConnectionState, 
    isMacAvailable: Boolean,
    onSettingsClick: () -> Unit
) {
    val (label, color) = when {
        connectionState == ConnectionState.CONNECTED -> "Connected" to ConnectedColor
        connectionState == ConnectionState.CONNECTING -> "Menghubungkan..." to ConnectingColor
        isMacAvailable && (connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.ERROR) -> "Siaga (Standby)" to ConnectingColor
        connectionState == ConnectionState.DISCONNECTED -> "Terputus" to DisconnectedColor
        else -> "Error" to DisconnectedColor
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgBase.copy(alpha = 0.78f), RoundedCornerShape(999.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(999.dp))
            .padding(start = 14.dp, end = 8.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.size(width = 8.dp, height = 1.dp))
        Text(text = label, color = TextDim, style = MaterialTheme.typography.bodySmall)

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "SAMSUNG · N4300",
            color = TextPrimary,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )

        Spacer(modifier = Modifier.size(width = 10.dp, height = 1.dp))

        HapticGlassButton(
            onClick = onSettingsClick,
            modifier = Modifier.size(34.dp),
            shape = CircleShape
        ) {
            Icon(Icons.Filled.Settings, contentDescription = null, tint = TextDim, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DisconnectedColor.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .border(1.dp, DisconnectedColor.copy(alpha = 0.30f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Text(text = message, color = DisconnectedColor, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(10.dp))
        HapticGlassButton(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(40.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Coba Lagi", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StandbyBanner(onWakeClick: () -> Unit, scaleFactor: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NavAccent2.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .border(1.dp, NavAccent2.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, tint = NavAccent2, modifier = Modifier.size((28 * scaleFactor).dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "TV dalam Mode Siaga (Standby)",
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Tekan tombol daya merah di bawah atau tombol di bawah ini untuk menyalakan TV.",
            color = TextDim,
            style = MaterialTheme.typography.bodySmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(14.dp))
        HapticGlassButton(
            onClick = onWakeClick,
            modifier = Modifier.fillMaxWidth().height((44 * scaleFactor).dp),
            gradientColors = listOf(NavAccent.copy(alpha = 0.20f), NavAccent2.copy(alpha = 0.16f)),
            borderColor = NavAccent.copy(alpha = 0.35f)
        ) {
            Text("Nyalakan TV (WOL)", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PowerSourceSleepRow(viewModel: RemoteViewModel, scaleFactor: Float) {
    val height = (60 * scaleFactor).dp
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HapticGlassButton(
            onClick = { viewModel.sendKey(RemoteKey.POWER) },
            modifier = Modifier.weight(1f).height(height),
            gradientColors = listOf(PowerGradientStart.copy(alpha = 0.24f), PowerGradientEnd.copy(alpha = 0.16f)),
            borderColor = PowerGradientStart.copy(alpha = 0.38f)
        ) {
            // Menggunakan Simbol Daya IEC ⏻ (Unicode Power)
            Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, tint = Color(0xFFFFB199), modifier = Modifier.size((24 * scaleFactor).dp))
        }
        HapticGlassButton(
            onClick = { viewModel.sendKey(RemoteKey.SOURCE) },
            modifier = Modifier.weight(1f).height(height)
        ) {
            // Menggunakan Simbol Input Berputar/Siklus ⇥
            Icon(Icons.Filled.Input, contentDescription = null, tint = TextPrimary, modifier = Modifier.size((22 * scaleFactor).dp))
        }
        HapticGlassButton(
            onClick = { viewModel.sendKey(RemoteKey.HDMI) },
            modifier = Modifier.weight(1f).height(height)
        ) {
            // Menggunakan Simbol Steker/Konektor Kabel 🔌
            Icon(Icons.Filled.SettingsInputHdmi, contentDescription = null, tint = TextPrimary, modifier = Modifier.size((22 * scaleFactor).dp))
        }
    }
}

@Composable
private fun BackHomeExitRow(viewModel: RemoteViewModel, scaleFactor: Float) {
    val height = (50 * scaleFactor).dp
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HapticGlassButton(
            onClick = { viewModel.sendKey(RemoteKey.BACK) },
            modifier = Modifier.weight(1f).height(height)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = TextPrimary, modifier = Modifier.size((20 * scaleFactor).dp))
        }
        HapticGlassButton(
            onClick = { viewModel.sendKey(RemoteKey.HOME) },
            modifier = Modifier.weight(1f).height(height)
        ) {
            Icon(Icons.Filled.Home, contentDescription = null, tint = TextPrimary, modifier = Modifier.size((22 * scaleFactor).dp))
        }
        HapticGlassButton(
            onClick = { viewModel.sendKey(RemoteKey.EXIT) },
            modifier = Modifier.weight(1f).height(height)
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = TextPrimary, modifier = Modifier.size((18 * scaleFactor).dp))
        }
    }
}

private data class PillCell(
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val label: String? = null,
    val autoRepeat: Boolean = false,
    val onClick: () -> Unit
)

@Composable
private fun VolumeChannelSection(viewModel: RemoteViewModel, scaleFactor: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PillRow(
            scaleFactor = scaleFactor,
            cells = listOf(
                PillCell(icon = Icons.Filled.Remove, autoRepeat = true) { viewModel.sendKey(RemoteKey.VOL_DOWN) },
                PillCell(icon = Icons.AutoMirrored.Filled.VolumeUp) { },
                PillCell(icon = Icons.Filled.Add, autoRepeat = true) { viewModel.sendKey(RemoteKey.VOL_UP) },
                PillCell(icon = Icons.AutoMirrored.Filled.VolumeOff) { viewModel.sendKey(RemoteKey.MUTE) }
            )
        )
        PillRow(
            scaleFactor = scaleFactor,
            cells = listOf(
                PillCell(icon = Icons.Filled.Remove, autoRepeat = true) { viewModel.sendKey(RemoteKey.CH_DOWN) },
                PillCell(icon = Icons.Filled.Tv) { },
                PillCell(icon = Icons.Filled.Add, autoRepeat = true) { viewModel.sendKey(RemoteKey.CH_UP) },
                PillCell(icon = Icons.AutoMirrored.Filled.List) { viewModel.sendKey(RemoteKey.CH_LIST) }
            )
        )
        HapticGlassButton(
            onClick = { viewModel.sendKey(RemoteKey.PRE_CH) },
            modifier = Modifier.fillMaxWidth().height((46 * scaleFactor).dp)
        ) {
            Icon(Icons.Filled.SwapHoriz, contentDescription = null, tint = TextPrimary, modifier = Modifier.size((20 * scaleFactor).dp))
        }
    }
}

@Composable
private fun PillRow(cells: List<PillCell>, scaleFactor: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height((54 * scaleFactor).dp)
            .background(Color.Transparent, RoundedCornerShape(18.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp)),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        cells.forEachIndexed { index, cell ->
            val shape = when (index) {
                0 -> RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
                cells.lastIndex -> RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp)
                else -> RoundedCornerShape(0.dp)
            }
            HapticGlassButton(
                onClick = cell.onClick,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = shape,
                autoRepeat = cell.autoRepeat, // SALURKAN AUTO-REPEAT DI SINI
                borderColor = Color.Transparent
            ) {
                if (cell.icon != null) {
                    Icon(cell.icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                } else {
                    Text(text = cell.label ?: "", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun NumpadGrid(viewModel: RemoteViewModel, scaleFactor: Float) {
    val rows = listOf(
        listOf(RemoteKey.KEY_1, RemoteKey.KEY_2, RemoteKey.KEY_3),
        listOf(RemoteKey.KEY_4, RemoteKey.KEY_5, RemoteKey.KEY_6),
        listOf(RemoteKey.KEY_7, RemoteKey.KEY_8, RemoteKey.KEY_9),
        listOf(RemoteKey.KEY_0)
    )
    val keyHeight = (54 * scaleFactor).dp
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    HapticGlassLabelButton(
                        label = key.label,
                        onClick = { viewModel.sendKey(key) },
                        modifier = Modifier.weight(1f).height(keyHeight),
                        fontSize = 19.sp
                    )
                }
                repeat(3 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ColorKeysRow(viewModel: RemoteViewModel, scaleFactor: Float) {
    val height = (46 * scaleFactor).dp
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ColorKeyButton("A", ColorKeyRed, Modifier.weight(1f), height) { viewModel.sendKey(RemoteKey.RED) }
        ColorKeyButton("B", ColorKeyGreen, Modifier.weight(1f), height) { viewModel.sendKey(RemoteKey.GREEN) }
        ColorKeyButton("C", ColorKeyYellow, Modifier.weight(1f), height) { viewModel.sendKey(RemoteKey.YELLOW) }
        ColorKeyButton("D", ColorKeyBlue, Modifier.weight(1f), height) { viewModel.sendKey(RemoteKey.BLUE) }
    }
}

@Composable
private fun ColorKeyButton(label: String, color: Color, modifier: Modifier, height: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    HapticGlassButton(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(14.dp),
        gradientColors = listOf(color.copy(alpha = 0.20f), color.copy(alpha = 0.07f)),
        borderColor = color.copy(alpha = 0.35f),
        contentColor = color
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun MediaTransportRow(viewModel: RemoteViewModel, scaleFactor: Float) {
    val buttons = listOf<Triple<androidx.compose.ui.graphics.vector.ImageVector, () -> Unit, Boolean>>(
        Triple(Icons.Filled.FastRewind, { viewModel.sendKey(RemoteKey.REWIND) }, true),
        Triple(Icons.Filled.PlayArrow, { viewModel.sendKey(RemoteKey.PLAY) }, false),
        Triple(Icons.Filled.Pause, { viewModel.sendKey(RemoteKey.PAUSE) }, false),
        Triple(Icons.Filled.Stop, { viewModel.sendKey(RemoteKey.STOP) }, false),
        Triple(Icons.Filled.FastForward, { viewModel.sendKey(RemoteKey.FAST_FORWARD) }, true)
    )
    val height = (52 * scaleFactor).dp
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        buttons.forEach { (icon, action, autoRepeat) ->
            HapticGlassButton(
                onClick = action,
                modifier = Modifier.weight(1f).height(height),
                shape = RoundedCornerShape(15.dp),
                autoRepeat = autoRepeat,
                gradientColors = listOf(MediaAccent.copy(alpha = 0.14f), MediaAccent2.copy(alpha = 0.10f)),
                borderColor = MediaAccent.copy(alpha = 0.25f)
            ) {
                Icon(icon, contentDescription = null, tint = MediaAccent, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun MenuInfoGrid(viewModel: RemoteViewModel, scaleFactor: Float) {
    val items = listOf<Pair<androidx.compose.ui.graphics.vector.ImageVector, RemoteKey>>(
        Icons.Filled.Menu to RemoteKey.MENU,
        Icons.Filled.DateRange to RemoteKey.GUIDE,
        Icons.Filled.Info to RemoteKey.INFO
    )
    val height = (58 * scaleFactor).dp
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        items.forEach { (icon, key) ->
            HapticGlassButton(
                onClick = { viewModel.sendKey(key) },
                modifier = Modifier.weight(1f).height(height),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(icon, contentDescription = null, tint = TextPrimary.copy(alpha = 0.85f), modifier = Modifier.size((22 * scaleFactor).dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppShortcutsRow(viewModel: RemoteViewModel, scaleFactor: Float) {
    val height = (54 * scaleFactor).dp
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AppShortcutButton(
            label = "NETFLIX",
            color = NetflixRed,
            modifier = Modifier.weight(1f),
            height = height,
            onLaunch = { viewModel.launchApp(com.tvhanan.domain.model.AppShortcut.NETFLIX.appId) },
            onClose = { viewModel.closeApp(com.tvhanan.domain.model.AppShortcut.NETFLIX.appId) }
        )
        AppShortcutButton(
            label = "PRIME",
            color = PrimeBlue,
            modifier = Modifier.weight(1f),
            height = height,
            onLaunch = { viewModel.launchApp(com.tvhanan.domain.model.AppShortcut.PRIME_VIDEO.appId) },
            onClose = { viewModel.closeApp(com.tvhanan.domain.model.AppShortcut.PRIME_VIDEO.appId) }
        )
        AppShortcutButton(
            label = "YOUTUBE",
            color = YoutubeRed,
            modifier = Modifier.weight(1f),
            height = height,
            onLaunch = { viewModel.launchApp(com.tvhanan.domain.model.AppShortcut.YOUTUBE.appId) },
            onClose = { viewModel.closeApp(com.tvhanan.domain.model.AppShortcut.YOUTUBE.appId) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppShortcutButton(
    label: String,
    color: Color,
    modifier: Modifier,
    height: androidx.compose.ui.unit.Dp,
    onLaunch: () -> Unit,
    onClose: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(height)
            .background(
                Brush.linearGradient(listOf(color.copy(alpha = 0.18f), color.copy(alpha = 0.06f))),
                RoundedCornerShape(16.dp)
            )
            .border(1.dp, color.copy(alpha = 0.32f), RoundedCornerShape(16.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    HapticUtil.tick()
                    onLaunch()
                },
                onLongClick = {
                    HapticUtil.tick()
                    onClose()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}