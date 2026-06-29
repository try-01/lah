package com.tvhanan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.tvhanan.ui.navigation.TvRemoteNavGraph
import com.tvhanan.ui.theme.TvRemoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as TvRemoteApp

        setContent {
            TvRemoteTheme {
                val navController = rememberNavController()
                TvRemoteNavGraph(
                    navController = navController,
                    serviceLocator = app.serviceLocator,
                    onExitApp = { finish() }
                )
            }
        }
    }
}
