package com.screenm.ui

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.screenm.ScreenCaptureService
import com.screenm.discovery.DeviceDiscoveryManager
import com.screenm.model.ConnectionState
import com.screenm.model.DeviceInfo

class MainActivity : ComponentActivity() {

    private lateinit var discoveryManager: DeviceDiscoveryManager

    private var connectionState by mutableStateOf(ConnectionState.IDLE)
    private var pendingTargetDevice: DeviceInfo? = null
    private var mediaProjectionIntent: Intent? = null

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            mediaProjectionIntent = result.data
            startMirroringService()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Permission granted or not — proceed anyway
        requestMediaProjection()
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getStringExtra("state")?.let { ConnectionState.valueOf(it) }
            if (state != null) {
                connectionState = state
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        discoveryManager = DeviceDiscoveryManager(this)

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        registerReceiver(stateReceiver, IntentFilter(ScreenCaptureService.BROADCAST_STATE), flags)

        setContent {
            val devices by discoveryManager.devices.collectAsState()

            ScreenMTheme {
                MainScreen(
                    devices = devices,
                    connectionState = connectionState,
                    onDeviceSelected = { device ->
                        pendingTargetDevice = device
                        checkPermissionsAndStart()
                    },
                    onStop = {
                        stopService(Intent(this, ScreenCaptureService::class.java))
                        connectionState = ConnectionState.IDLE
                    }
                )
            }
        }
    }

    private fun checkPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        requestMediaProjection()
    }

    private fun requestMediaProjection() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun startMirroringService() {
        val intent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START
            mediaProjectionIntent?.let {
                putExtra(ScreenCaptureService.EXTRA_MEDIA_PROJECTION, it)
            }
            pendingTargetDevice?.let {
                putExtra(ScreenCaptureService.EXTRA_TARGET_IP, it.ipAddress)
            }
        }
        startForegroundService(intent)
    }

    override fun onResume() {
        super.onResume()
        discoveryManager.startDiscovery()
    }

    override fun onPause() {
        super.onPause()
        if (connectionState == ConnectionState.IDLE) {
            discoveryManager.stopDiscovery()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(stateReceiver) } catch (_: Exception) {}
        discoveryManager.destroy()
    }
}

@Composable
private fun ScreenMTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF6C63FF),
            secondary = Color(0xFF03DAC6),
            surface = Color(0xFF1A1A2E),
            background = Color(0xFF0F0F23),
            onPrimary = Color.White,
            onSurface = Color.White,
            onBackground = Color.White
        )
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    devices: List<DeviceInfo>,
    connectionState: ConnectionState,
    onDeviceSelected: (DeviceInfo) -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Text(
            text = "screenM",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Screen Mirroring",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        when (connectionState) {
            ConnectionState.IDLE -> DeviceList(devices, onDeviceSelected)
            ConnectionState.CONNECTING -> StatusView("Connecting...", Color(0xFF6C63FF))
            ConnectionState.STREAMING -> StatusView("Streaming", Color(0xFF4CAF50), onStop)
            ConnectionState.ERROR -> StatusView("Connection Failed", Color(0xFFE53935), onStop)
        }
    }
}

@Composable
private fun DeviceList(devices: List<DeviceInfo>, onSelect: (DeviceInfo) -> Unit) {
    Text(
        text = "Available Devices",
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(bottom = 12.dp)
    )

    if (devices.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Searching for TVs...",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(devices) { device ->
                DeviceCard(device, onSelect = { onSelect(device) })
            }
        }
    }
}

@Composable
private fun StatusView(text: String, color: Color, onStop: (() -> Unit)? = null) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (onStop == null) {
                CircularProgressIndicator(color = color, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(16.dp))
            }
            Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
            if (onStop != null) {
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                ) {
                    Text("Stop Mirroring", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: DeviceInfo, onSelect: () -> Unit) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (device.tizenVersion.isNotEmpty()) "Tizen ${device.tizenVersion}"
                           else device.ipAddress,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Text(
                text = if (device.isGoogleCastCapable) "Cast" else "WebRTC",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (device.isGoogleCastCapable) Color(0xFF4CAF50)
                        else MaterialTheme.colorScheme.primary
            )
        }
    }
}
