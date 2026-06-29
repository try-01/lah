package com.tvhanan.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tvhanan.di.ServiceLocator
import com.tvhanan.ui.settings.SettingsViewModel
import com.tvhanan.domain.model.TvDevice
import com.tvhanan.ui.manual.ManualConnectScreen
import com.tvhanan.ui.remote.RemoteScreen
import com.tvhanan.ui.remote.RemoteViewModel
import com.tvhanan.ui.scan.ScanScreen
import com.tvhanan.ui.scan.ScanViewModel
import com.tvhanan.ui.settings.SettingsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.tvhanan.domain.model.ConnectionState

object Routes {
    const val SCAN = "scan"
    const val MANUAL = "manual"
    const val REMOTE = "remote/{ip}/{port}?mac={mac}"
    const val SETTINGS = "settings"

    fun remoteRoute(device: TvDevice) =
        "remote/${device.ipAddress}/${device.port}?mac=${device.macAddress ?: ""}"
    fun remoteRoute(ip: String, port: Int = 8002, mac: String? = null) =
        "remote/$ip/$port?mac=${mac ?: ""}"
}

/**
 * Navigasi grafis utama aplikasi remote TV.
 */
@Composable
fun TvRemoteNavGraph(
    navController: NavHostController,
    serviceLocator: ServiceLocator,
    onExitApp: () -> Unit
) {
    val scope = rememberCoroutineScope()
    
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(serviceLocator.repository) as T
            }
        }
    )

    val startRoute by androidx.compose.runtime.produceState<String?>(initialValue = null) {
        val savedIp = serviceLocator.repository.lastIp.first()
        value = if (savedIp != null) {
            val savedPort = serviceLocator.repository.lastPort.first()?.toIntOrNull() ?: 8002
            val savedMac = serviceLocator.repository.macAddress.first()
            Routes.remoteRoute(savedIp, savedPort, savedMac)
        } else {
            Routes.SCAN
        }
    }

    if (startRoute == null) return

    NavHost(navController = navController, startDestination = startRoute!!) {
        composable(Routes.SCAN) {
            val viewModel: ScanViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return ScanViewModel(serviceLocator.repository) as T
                    }
                }
            )
            ScanScreen(
                viewModel = viewModel,
                onDeviceSelected = { device ->
                    scope.launch {
                        serviceLocator.repository.saveLastIp(device.ipAddress)
                        serviceLocator.repository.saveLastPort(device.port.toString())
                        device.macAddress?.let { serviceLocator.repository.saveMacAddress(it) }
                        navController.navigate(Routes.remoteRoute(device))
                    }
                },
                onManualConnect = {
                    navController.navigate(Routes.MANUAL)
                }
            )
        }

        composable(Routes.MANUAL) {
            ManualConnectScreen(
                onConnect = { device ->
                    scope.launch {
                        serviceLocator.repository.saveLastIp(device.ipAddress)
                        serviceLocator.repository.saveLastPort(device.port.toString())
                        device.macAddress?.let { serviceLocator.repository.saveMacAddress(it) }
                        navController.navigate(Routes.remoteRoute(device)) {
                            popUpTo(Routes.SCAN)
                        }
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.REMOTE,
            arguments = listOf(
                navArgument("ip") { type = NavType.StringType },
                navArgument("port") { type = NavType.IntType; defaultValue = 8002 },
                navArgument("mac") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val ip = backStackEntry.arguments?.getString("ip") ?: return@composable
            val port = backStackEntry.arguments?.getInt("port") ?: 8002
            val mac = backStackEntry.arguments?.getString("mac")?.ifBlank { null }

            val viewModel: RemoteViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                        return RemoteViewModel(ip, port, mac, serviceLocator.repository) as T
                    }
                }
            )

            val connectionStateForSync by viewModel.connectionState.collectAsStateWithLifecycle()

            androidx.compose.runtime.LaunchedEffect(ip, port, connectionStateForSync) {
                val mac = serviceLocator.repository.macAddress.first()
                val token = serviceLocator.repository.getToken()
                settingsViewModel.setActiveDevice(
                    ipAddress = ip,
                    port = port,
                    macAddress = mac,
                    token = token,
                    isConnected = connectionStateForSync == ConnectionState.CONNECTED
                )
            }

            androidx.compose.runtime.LaunchedEffect(ip, port) {
                viewModel.observeNewToken { newToken ->
                    val mac = serviceLocator.repository.macAddress.first()
                    settingsViewModel.setActiveDevice(
                        ipAddress = ip,
                        port = port,
                        macAddress = mac,
                        token = newToken,
                        isConnected = true
                    )
                }
            }

            val uiPrefs by settingsViewModel.uiPreferences.collectAsStateWithLifecycle()

            RemoteScreen(
                viewModel = viewModel,
                onOpenSettings = {
                    navController.navigate(Routes.SETTINGS) {
                        launchSingleTop = true // Mencegah penumpukan layar ganda pada double-tap cepat
                    }
                },
                scaleFactor = uiPrefs.remoteSize.scaleFactor,
                keepScreenOn = uiPrefs.keepScreenOn,
                hapticEnabled = uiPrefs.hapticEnabled,                 // Salurkan status getar
                meshBackgroundEnabled = uiPrefs.meshBackgroundEnabled   // Salurkan status latar belakang aurora
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = {
                    navController.popBackStack()
                },
                onManualConnect = { typedIp ->
                    navController.navigate(Routes.remoteRoute(typedIp)) {
                        popUpTo(Routes.SCAN)
                    }
                },
                onForgetAndExitToScan = {
                    navController.navigate(Routes.SCAN) {
                        popUpTo(0)
                    }
                },
                onExitApp = onExitApp
            )
        }
    }
}