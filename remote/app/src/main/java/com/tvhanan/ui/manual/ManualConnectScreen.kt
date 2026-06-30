@file:Suppress("FunctionNaming", "LongMethod")

package com.tvhanan.ui.manual

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tvhanan.domain.model.TvDevice
import com.tvhanan.ui.components.HapticGlassButton
import com.tvhanan.ui.components.MeshGradientBackground
import com.tvhanan.ui.theme.DisconnectedColor
import com.tvhanan.ui.theme.NavAccent
import com.tvhanan.ui.theme.NavAccent2
import com.tvhanan.ui.theme.TextDim
import com.tvhanan.ui.theme.TextFaint
import com.tvhanan.ui.theme.TextPrimary

@Composable
fun ManualConnectScreen(
    onConnect: (TvDevice) -> Unit,
    onBack: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current // 1. Deklarasikan
    var ipAddress by remember { mutableStateOf("") }
    var macAddress by remember { mutableStateOf("") }
    var ipError by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        MeshGradientBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(20.dp),
        ) {
            Text(
                text = "Koneksi Manual",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Lihat IP TV di Menu > Network > Network Status pada TV.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextDim,
            )

            Spacer(modifier = Modifier.height(24.dp))

            val glassFieldColors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NavAccent.copy(alpha = 0.5f),
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = NavAccent,
                    focusedLabelColor = NavAccent,
                    unfocusedLabelColor = TextFaint,
                    focusedPlaceholderColor = TextFaint,
                    unfocusedPlaceholderColor = TextFaint,
                )

            OutlinedTextField(
                value = ipAddress,
                onValueChange = { newValue ->
                    // Filter: hanya izinkan angka dan titik untuk IP address
                    if (newValue.isEmpty() || Regex("^\\d{0,3}(\\.\\d{0,3}){0,3}$").matches(newValue)) {
                        ipAddress = newValue
                        ipError = null
                    }
                },
                label = { Text("IP Address TV") },
                placeholder = { Text("192.168.1.100") },
                isError = ipError != null,
                supportingText = ipError?.let { msg -> { Text(msg, color = DisconnectedColor) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
                colors = glassFieldColors,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = macAddress,
                onValueChange = { macAddress = it },
                label = { Text("MAC Address (opsional)") },
                placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                singleLine = true,
                colors = glassFieldColors,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "MAC diperlukan untuk Wake-on-LAN",
                style = MaterialTheme.typography.bodySmall,
                color = TextFaint,
            )

            Spacer(modifier = Modifier.height(28.dp))

            HapticGlassButton(
                onClick = {
                    keyboardController?.hide() // 2. Tutup keyboard sebelum eksekusi aksi
                    if (validateIp(ipAddress)) {
                        val cleanMac = macAddress.trim().ifEmpty { null }
                        onConnect(
                            TvDevice(
                                ipAddress = ipAddress.trim(),
                                macAddress = cleanMac,
                            ),
                        )
                    } else {
                        ipError = "Format IP tidak valid"
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                gradientColors = listOf(NavAccent.copy(alpha = 0.22f), NavAccent2.copy(alpha = 0.18f)),
                borderColor = NavAccent.copy(alpha = 0.4f),
                enabled = ipAddress.isNotBlank(),
            ) {
                Text("Hubungkan", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(12.dp))

            HapticGlassButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Kembali", color = TextDim, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun validateIp(ip: String): Boolean {
    // Gunakan Regex murni agar tidak memblokir Main Thread atau memicu DNS Lookup
    val ipRegex = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$".toRegex()
    return ip.trim().matches(ipRegex)
}
