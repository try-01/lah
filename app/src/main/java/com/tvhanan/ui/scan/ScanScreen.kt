package com.tvhanan.ui.scan

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tvhanan.domain.model.TvDevice
import com.tvhanan.ui.components.HapticGlassButton
import com.tvhanan.ui.components.MeshGradientBackground
import com.tvhanan.ui.theme.DisconnectedColor
import com.tvhanan.ui.theme.GlassBorder
import com.tvhanan.ui.theme.NavAccent
import com.tvhanan.ui.theme.NavAccent2
import com.tvhanan.ui.theme.TextDim
import com.tvhanan.ui.theme.TextFaint
import com.tvhanan.ui.theme.TextPrimary

@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    onDeviceSelected: (TvDevice) -> Unit,
    onManualConnect: () -> Unit
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val lastIp by viewModel.lastIp.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        MeshGradientBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp)
        ) {
            Text(
                text = "Cari TV Samsung",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Pastikan HP dan TV terhubung ke Wi-Fi yang sama.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextDim
            )

            Spacer(modifier = Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HapticGlassButton(
                    onClick = { viewModel.startScan() },
                    modifier = Modifier.weight(1f).height(52.dp),
                    gradientColors = listOf(NavAccent.copy(alpha = 0.20f), NavAccent2.copy(alpha = 0.16f)),
                    borderColor = NavAccent.copy(alpha = 0.4f)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = NavAccent
                        )
                    } else {
                        Text("Cari TV", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                HapticGlassButton(
                    onClick = onManualConnect,
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Text("Manual", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (error != null) {
                Text(
                    text = error!!,
                    color = DisconnectedColor,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (lastIp != null && devices.isEmpty() && !isScanning) {
                Text(
                    text = "Terakhir terhubung: $lastIp",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextFaint
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(devices) { device ->
                    TvDeviceCard(
                        device = device,
                        onClick = {
                            viewModel.savePreferredDevice(device)
                            onDeviceSelected(device)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TvDeviceCard(
    device: TvDevice,
    onClick: () -> Unit
) {
    HapticGlassButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(NavAccent.copy(alpha = 0.12f), CircleShape)
                        .border(1.dp, NavAccent.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Tv, contentDescription = null, tint = NavAccent, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.size(width = 12.dp, height = 1.dp))
                Column {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = device.ipAddress,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextDim
                    )
                }
            }

            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = NavAccent, modifier = Modifier.size(20.dp))
        }
    }
}
