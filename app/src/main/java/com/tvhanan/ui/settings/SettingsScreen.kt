@file:Suppress("FunctionNaming", "LongParameterList", "LongMethod", "MagicNumber", "MaxLineLength", "TooManyFunctions")
package com.tvhanan.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.unit.dp
import com.tvhanan.domain.model.TvDevice
import com.tvhanan.ui.components.HapticGlassButton
import com.tvhanan.ui.components.MeshGradientBackground
import com.tvhanan.ui.components.ZoneLabel
import com.tvhanan.ui.theme.ConnectedColor
import com.tvhanan.ui.theme.DisconnectedColor
import com.tvhanan.ui.theme.GlassBorder
import com.tvhanan.ui.theme.GlassSurface
import com.tvhanan.ui.theme.NavAccent
import com.tvhanan.ui.theme.NavAccent2
import com.tvhanan.ui.theme.TextDim
import com.tvhanan.ui.theme.TextFaint
import com.tvhanan.ui.theme.TextPrimary
import com.tvhanan.ui.theme.BgBase

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onManualConnect: (String) -> Unit,
    onForgetAndExitToScan: () -> Unit,
    onExitApp: () -> Unit
) {
    val device by viewModel.tvDevice.collectAsStateWithLifecycle()
    val prefs by viewModel.uiPreferences.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()

    var showManualDialog by remember { mutableStateOf(false) }
    var showForgetDialog by remember { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showActionDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().background(BgBase)) {
        // Sinkronisasi efek aurora latar belakang halaman Pengaturan
        if (prefs.meshBackgroundEnabled) {
            MeshGradientBackground(modifier = Modifier.fillMaxSize())
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item { SettingsHeaderBar(onBack) }

            item {
                val isConnected by viewModel.isActuallyConnected.collectAsStateWithLifecycle()
                TvInfoCard(device, isConnected)
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZoneLabel("Koneksi", accentColor = NavAccent)
                    SettingsGroup {
                        SettingsRow(
                            title = "Hubungkan ulang TV",
                            description = "Cari ulang & sambungkan ke TV ini",
                            onClick = {
                                pendingAction = "reconnect"
                                showActionDialog = true
                                viewModel.reconnect()
                            }
                        )
                        SettingsRow(
                            title = "Pindai TV lain",
                            description = "Tambahkan TV baru di jaringan",
                            onClick = {
                                pendingAction = "scan"
                                showActionDialog = true
                                viewModel.scanForOtherTvs()
                            }
                        )
                        SettingsRow(
                            title = "Sambungkan manual",
                            description = "Masukkan IP TV secara langsung",
                            onClick = { showManualDialog = true }
                        )
                        if (device?.macAddress != null) {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            SettingsRow(
                                title = "Nyalakan TV (WOL)",
                                description = "Bisa butuh beberapa menit setelah TV dimatikan",
                                onClick = {
                                    viewModel.wakeTv()
                                    // Tampilkan pesan melayang (Toast) instan sebagai feedback visual
                                    android.widget.Toast.makeText(
                                        context, 
                                        "Sinyal bangun (WOL) telah dikirim ke TV", 
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                        SettingsRow(
                            title = "Lupakan TV ini",
                            description = "Hapus token & data koneksi tersimpan",
                            isLast = true,
                            onClick = { showForgetDialog = true }
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZoneLabel("Tampilan & Pengalaman", accentColor = NavAccent2)
                    SettingsGroup {
                        SettingsToggleRow(
                            title = "Getar saat tombol ditekan",
                            description = "Haptic feedback tiap tap",
                            checked = prefs.hapticEnabled,
                            onCheckedChange = viewModel::setHapticEnabled
                        )
                        SettingsToggleRow(
                            title = "Tetap terang di tangan",
                            description = "Cegah layar HP redup saat dipakai",
                            checked = prefs.keepScreenOn,
                            onCheckedChange = viewModel::setKeepScreenOn
                        )
                        SettingsToggleRow(
                            title = "Latar belakang dinamis",
                            description = "Efek aurora di background app",
                            checked = prefs.meshBackgroundEnabled,
                            onCheckedChange = viewModel::setMeshBackgroundEnabled,
                            isLast = true
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZoneLabel("Ukuran Tampilan Remote", accentColor = TextDim)
                    SizeSelector(
                        current = prefs.remoteSize,
                        onSelect = viewModel::setRemoteSize
                    )
                    Text(
                        text = "Sesuaikan ukuran tombol remote agar pas dengan layar HP kamu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextFaint,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZoneLabel("Lainnya", accentColor = TextFaint)
                    SettingsGroup {
                        SettingsRow(
                            title = "Tentang aplikasi",
                            description = "Versi, lisensi, dan info build",
                            trailingText = "v1.0.0",
                            onClick = {}
                        )
                        SettingsRow(
                            title = "Keluar dari aplikasi",
                            description = "Tutup remote sepenuhnya",
                            isDanger = true,
                            isLast = true,
                            onClick = { showExitDialog = true }
                        )
                    }
                }
            }

            item {
                Text(
                    text = "TV Remote · versi 1.0.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextFaint,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }

    if (showManualDialog) {
        ManualIpDialog(
            onDismiss = { showManualDialog = false },
            onConfirm = { typedIp ->
                showManualDialog = false
                if (typedIp.isNotBlank()) {
                    onManualConnect(typedIp.trim())
                }
            }
        )
    }

    if (showForgetDialog) {
        ConfirmDialog(
            title = "Lupakan TV ini?",
            description = "Token & data koneksi akan dihapus. Kamu perlu menyetujui ulang permintaan koneksi di TV saat menyambung lagi.",
            confirmLabel = "Lupakan",
            isDanger = true,
            onDismiss = { showForgetDialog = false },
            onConfirm = {
                showForgetDialog = false
                viewModel.forgetTv()
                onForgetAndExitToScan()
            }
        )
    }

    if (showExitDialog) {
        ConfirmDialog(
            title = "Keluar dari aplikasi?",
            description = "Remote akan ditutup sepenuhnya. Koneksi ke TV tetap tersimpan untuk dipakai lagi nanti.",
            confirmLabel = "Keluar",
            isDanger = true,
            onDismiss = { showExitDialog = false },
            onConfirm = {
                showExitDialog = false
                onExitApp()
            }
        )
    }

    if (showActionDialog) {
        ActionProgressDialog(
            actionState = actionState,
            onDismiss = {
                showActionDialog = false
                viewModel.resetActionState()
            }
        )
    }
}

@Composable
private fun SettingsHeaderBar(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HapticGlassButton(
            onClick = onBack,
            modifier = Modifier.size(38.dp),
            shape = CircleShape
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.size(width = 12.dp, height = 1.dp))
        Text("Pengaturan", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
    }
}

@Composable
private fun TvInfoCard(device: TvDevice?, isConnected: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(NavAccent.copy(alpha = 0.10f), NavAccent2.copy(alpha = 0.07f))
                ),
                RoundedCornerShape(22.dp)
            )
            .border(1.dp, NavAccent.copy(alpha = 0.22f), RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(GlassSurface, RoundedCornerShape(16.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Tv, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.size(width = 14.dp, height = 1.dp))
            Column {
                Text(
                    text = device?.name ?: "Belum ada TV tersambung",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "N-Series · Tizen 5.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
            }
        }

        if (device != null) {
            val statusColor = if (isConnected) ConnectedColor else DisconnectedColor
            val statusLabel = if (isConnected) "Tersambung" else "Tidak tersambung"
            Row(
                modifier = Modifier
                    .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(6.dp).background(statusColor, CircleShape))
                Spacer(modifier = Modifier.size(width = 6.dp, height = 1.dp))
                Text(statusLabel, color = statusColor, style = MaterialTheme.typography.bodySmall)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetaItem("Alamat IP", device.ipAddress, Modifier.weight(1f))
                    MetaItem("Port", "${device.port}", Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetaItem("Alamat MAC", device.macAddress ?: "Tidak diketahui", Modifier.weight(1f))
                    
                    // Logika tampilan status token yang lebih cerdas
                    val tokenStatus = when {
                        device.token != null -> "Ya"
                        device.port == 8001 -> "Tidak perlu" // TV lawas / port 8001 tidak butuh token
                        else -> "Belum"
                    }
                    MetaItem("Token", tokenStatus, Modifier.weight(1f))
                }
            }
        } else {
            Text(
                text = "Hubungkan ke TV lewat menu Koneksi di bawah.",
                style = MaterialTheme.typography.bodySmall,
                color = TextDim
            )
        }
    }
}

@Composable
private fun MetaItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextFaint)
        Spacer(modifier = Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassSurface, RoundedCornerShape(18.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
    ) {
        content()
    }
}

@Composable
private fun SettingsRow(
    title: String,
    description: String,
    onClick: () -> Unit,
    trailingText: String? = null,
    isDanger: Boolean = false,
    isLast: Boolean = false
) {
    Column {
        HapticGlassButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(0.dp),
            borderColor = Color.Transparent
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isDanger) DisconnectedColor.copy(alpha = 0.9f) else TextPrimary
                    )
                    Text(description, style = MaterialTheme.typography.bodySmall, color = TextFaint)
                }
                if (trailingText != null) {
                    Text(trailingText, style = MaterialTheme.typography.bodyMedium, color = TextDim)
                }
            }
        }
        if (!isLast) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(GlassBorder)
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isLast: Boolean = false
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                Text(description, style = MaterialTheme.typography.bodySmall, color = TextFaint)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = NavAccent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = TextFaint
                )
            )
        }
        if (!isLast) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GlassBorder))
        }
    }
}

@Composable
private fun SizeSelector(current: RemoteSize, onSelect: (RemoteSize) -> Unit) {
    SettingsGroup {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SizeChip("Kompak", RemoteSize.COMPACT, current, onSelect, Modifier.weight(1f))
            SizeChip("Pas di layar", RemoteSize.FIT, current, onSelect, Modifier.weight(1f))
            SizeChip("Besar", RemoteSize.LARGE, current, onSelect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SizeChip(
    label: String,
    value: RemoteSize,
    current: RemoteSize,
    onSelect: (RemoteSize) -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = current == value
    HapticGlassButton(
        onClick = { onSelect(value) },
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(999.dp),
        gradientColors = if (isActive) listOf(NavAccent.copy(alpha = 0.22f), NavAccent2.copy(alpha = 0.18f)) else null,
        borderColor = if (isActive) NavAccent.copy(alpha = 0.4f) else GlassBorder
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (isActive) TextPrimary else TextDim
        )
    }
}

@Composable
private fun ManualIpDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var ip by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sambungkan manual") },
        text = {
            Column {
                Text(
                    "Masukkan alamat IP TV yang terlihat di Menu > Network > Network Status pada TV.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextDim
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = ip,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() || it == '.' }) {
                            ip = newValue
                        }
                    },
                    placeholder = { Text("192.168.1.42") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            HapticGlassButton(onClick = { onConfirm(ip) }, modifier = Modifier.height(40.dp)) {
                Text("Sambungkan", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
            }
        },
        dismissButton = {
            HapticGlassButton(onClick = onDismiss, modifier = Modifier.height(40.dp), borderColor = Color.Transparent) {
                Text("Batal", color = TextDim, style = MaterialTheme.typography.bodyMedium)
            }
        }
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    description: String,
    confirmLabel: String,
    isDanger: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(description, style = MaterialTheme.typography.bodyMedium, color = TextDim) },
        confirmButton = {
            HapticGlassButton(
                onClick = onConfirm,
                modifier = Modifier.height(40.dp),
                gradientColors = if (isDanger) listOf(Color(0xFFFF5A5A), Color(0xFFFF3D7A)) else null
            ) {
                Text(confirmLabel, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        },
        dismissButton = {
            HapticGlassButton(onClick = onDismiss, modifier = Modifier.height(40.dp), borderColor = Color.Transparent) {
                Text("Batal", color = TextDim, style = MaterialTheme.typography.bodyMedium)
            }
        }
    )
}

@Composable
private fun ActionProgressDialog(actionState: ConnectionActionState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (actionState) {
                    is ConnectionActionState.Loading -> "Memproses…"
                    is ConnectionActionState.ReconnectSuccess -> "Berhasil terhubung"
                    is ConnectionActionState.ScanResult -> "${actionState.devices.size} TV ditemukan"
                    is ConnectionActionState.Failed -> "Gagal"
                    ConnectionActionState.Idle -> "Tidak ada aksi"
                }
            )
        },
        text = {
            when (actionState) {
                is ConnectionActionState.Loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = NavAccent)
                        Spacer(modifier = Modifier.size(width = 10.dp, height = 1.dp))
                        Text("Menghubungi TV di jaringan…", color = TextDim, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is ConnectionActionState.ReconnectSuccess -> {
                    Text(
                        "${actionState.device.name} siap dikendalikan.",
                        color = TextDim,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is ConnectionActionState.ScanResult -> {
                    if (actionState.devices.isEmpty()) {
                        Text("Tidak ada TV ditemukan di jaringan ini.", color = TextDim, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Column {
                            actionState.devices.forEach { d ->
                                Text("${d.name} — ${d.ipAddress}", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                }
                is ConnectionActionState.Failed -> {
                    Text(actionState.message, color = DisconnectedColor, style = MaterialTheme.typography.bodyMedium)
                }
                ConnectionActionState.Idle -> {
                    Text(
                        "Belum ada TV yang tersambung untuk dihubungkan ulang. Coba pindai atau sambungkan manual dahulu.",
                        color = TextDim,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            HapticGlassButton(onClick = onDismiss, modifier = Modifier.height(40.dp)) {
                Text("Tutup", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    )
}
