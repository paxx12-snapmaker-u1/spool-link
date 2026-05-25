package dev.pages.paxx12.spoollink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.pages.paxx12.spoollink.viewmodel.SpoolmanViewModel

@Composable
fun MainScreen(viewModel: SpoolmanViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> ScanScreen(viewModel)
                1 -> SpoolsScreen(viewModel)
                2 -> HistoryScreen(viewModel)
                3 -> SettingsScreen(viewModel)
            }

            viewModel.pendingAssignSpool?.let { spool ->
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 5.dp
                        )
                        Text(
                            "Scan NFC tag to assign to\n${spool.displayName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        OutlinedButton(
                            onClick = { viewModel.cancelTagAssignment() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }
        }
        NavigationBar {
            NavigationBarItem(
                icon = { Icon(Icons.Default.Nfc, contentDescription = null) },
                label = { Text("Scan") },
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 }
            )
            NavigationBarItem(
                icon = { Icon(Icons.Default.FormatListBulleted, contentDescription = null) },
                label = { Text("Spools") },
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 }
            )
            NavigationBarItem(
                icon = {
                    BadgedBox(badge = {
                        if (viewModel.scanHistory.isNotEmpty()) Badge { Text("${viewModel.scanHistory.size}") }
                    }) {
                        Icon(Icons.Default.History, contentDescription = null)
                    }
                },
                label = { Text("History") },
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 }
            )
            NavigationBarItem(
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                label = { Text("Settings") },
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 }
            )
        }
    }
}
