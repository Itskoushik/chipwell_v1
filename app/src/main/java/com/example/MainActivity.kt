package com.example

import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.chipvision.ui.AppScreen
import com.example.chipvision.ui.ChipVisionViewModel
import com.example.chipvision.ui.screens.DiagnosticsScreen
import com.example.chipvision.ui.screens.HistoryScreen
import com.example.chipvision.ui.screens.HomeScreen
import com.example.chipvision.ui.screens.InspectionScreen
import com.example.chipvision.ui.screens.SettingsScreen
import com.example.ui.theme.ChipVisionTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ChipVisionViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChipVisionTheme {
                val currentScreen by viewModel.currentScreen.collectAsState()

                // BackHandler returns to HOME screen from any other destination
                BackHandler(enabled = currentScreen != AppScreen.HOME) {
                    viewModel.navigateTo(AppScreen.HOME)
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = {
                                if (currentScreen == AppScreen.HOME) {
                                    Column {
                                        Text(
                                            text = "CHIPVEL",
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.2.sp,
                                            fontSize = 18.sp
                                        )
                                        Text(
                                            text = "Automated chip inspection by Harvel",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                } else {
                                    Text(
                                        text = when (currentScreen) {
                                            AppScreen.INSPECTION -> "Live Inspection"
                                            AppScreen.HISTORY -> "Inspection History"
                                            AppScreen.SETTINGS -> "Settings"
                                            AppScreen.DIAGNOSTICS -> "Model Diagnostics"
                                            AppScreen.HOME -> "CHIPVEL"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                            },
                            navigationIcon = {
                                if (currentScreen != AppScreen.HOME) {
                                    IconButton(
                                        onClick = { viewModel.navigateTo(AppScreen.HOME) },
                                        modifier = Modifier.testTag("top_bar_back_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back to Home"
                                        )
                                    }
                                } else {
                                    Icon(
                                        painter = painterResource(com.example.R.drawable.ic_launcher_foreground),
                                        contentDescription = "Chipvel logo",
                                        tint = androidx.compose.ui.graphics.Color.Unspecified,
                                        modifier = Modifier.padding(start = 12.dp).size(32.dp)
                                    )
                                }
                            },
                            actions = {
                                if (currentScreen != AppScreen.SETTINGS) {
                                    IconButton(
                                        onClick = { viewModel.navigateTo(AppScreen.SETTINGS) },
                                        modifier = Modifier.testTag("top_bar_settings_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = "Settings"
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.testTag("main_top_app_bar")
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier.testTag("main_bottom_nav")
                        ) {
                            NavigationBarItem(
                                selected = currentScreen == AppScreen.HOME,
                                onClick = { viewModel.navigateTo(AppScreen.HOME) },
                                icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                label = { Text("Home") },
                                modifier = Modifier.testTag("nav_home_tab")
                            )
                            NavigationBarItem(
                                selected = currentScreen == AppScreen.INSPECTION,
                                onClick = { viewModel.navigateTo(AppScreen.INSPECTION) },
                                icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = "Inspect") },
                                label = { Text("Inspect") },
                                modifier = Modifier.testTag("nav_inspect_tab")
                            )
                            NavigationBarItem(
                                selected = currentScreen == AppScreen.HISTORY,
                                onClick = { viewModel.navigateTo(AppScreen.HISTORY) },
                                icon = { Icon(Icons.Default.History, contentDescription = "History") },
                                label = { Text("History") },
                                modifier = Modifier.testTag("nav_history_tab")
                            )
                            NavigationBarItem(
                                selected = currentScreen == AppScreen.SETTINGS,
                                onClick = { viewModel.navigateTo(AppScreen.SETTINGS) },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                label = { Text("Settings") },
                                modifier = Modifier.testTag("nav_settings_tab")
                            )
                        }
                    }
                ) { innerPadding ->
                    when (currentScreen) {
                        AppScreen.HOME -> HomeScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                        AppScreen.INSPECTION -> InspectionScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                        AppScreen.HISTORY -> HistoryScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                        AppScreen.SETTINGS -> SettingsScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                        AppScreen.DIAGNOSTICS -> DiagnosticsScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
