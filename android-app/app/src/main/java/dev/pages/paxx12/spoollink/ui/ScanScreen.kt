package dev.pages.paxx12.spoollink.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.pages.paxx12.spoollink.ui.components.SpoolInfoRow
import dev.pages.paxx12.spoollink.ui.components.TagDetailView
import dev.pages.paxx12.spoollink.viewmodel.SpoolmanViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(viewModel: SpoolmanViewModel) {
    var showAssignSheet by remember { mutableStateOf(false) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var showChangeSpoolSheet by remember { mutableStateOf(false) }
    var showSpoolDetail by remember { mutableStateOf(false) }
    var showDetachConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.ensureSpoolsLoaded() }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            ScanIndicator(isScanning = viewModel.isScanning)

            Text(
                text = viewModel.statusMessage,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Button(
                onClick = {
                    if (viewModel.isScanning) viewModel.stopScanning() else viewModel.startScanning()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (viewModel.isScanning) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = if (viewModel.isScanning) Icons.Default.Cancel else Icons.Default.Nfc,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    if (viewModel.isScanning) "Stop Scanning" else "Start Scanning",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            val result = viewModel.lastResult
            AnimatedVisibility(
                visible = result != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                if (result != null) {
                    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (result.success) Color(0xFF34C759) else MaterialTheme.colorScheme.error
                            )
                            Text(
                                if (result.success) "Tag read" else "Error",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (result.success) Color(0xFF34C759) else MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                timeFormatter.format(result.timestamp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        TagDetailView(payload = result.tagPayload, uidHex = result.cardUid)

                        if (result.spoolResponse != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SpoolmanSectionLabel()
                                SpoolInfoRow(
                                    spool = result.spoolResponse,
                                    modifier = Modifier.clickable { showSpoolDetail = true }
                                )
                                if (viewModel.spools.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { showChangeSpoolSheet = true },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp)
                                    ) { Text("Change Spool", fontWeight = FontWeight.Medium) }
                                }
                                OutlinedButton(
                                    onClick = { showDetachConfirm = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) { Text("Unlink from Spool", fontWeight = FontWeight.Medium) }
                            }
                        } else if (result.success) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SpoolmanSectionLabel()
                                OutlinedButton(
                                    onClick = { showAssignSheet = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) { Text("Assign to Existing Spool", fontWeight = FontWeight.Medium) }
                                Button(
                                    onClick = { showCreateSheet = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Create New Spool", fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(vertical = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val result = viewModel.lastResult
    if (result != null) {
        if (showSpoolDetail && result.spoolResponse != null) {
            SpoolDetailSheet(
                spool = result.spoolResponse,
                viewModel = viewModel,
                onDismiss = { showSpoolDetail = false },
                onAssignTag = { spool -> showSpoolDetail = false; viewModel.startTagAssignment(spool) }
            )
        }
        if (showAssignSheet) {
            AssignSpoolSheet(
                tagPayload = result.tagPayload,
                uidHex = result.cardUid,
                viewModel = viewModel,
                onDismiss = { showAssignSheet = false }
            )
        }
        if (showCreateSheet) {
            CreateSpoolSheet(
                tagPayload = result.tagPayload,
                uidHex = result.cardUid,
                viewModel = viewModel,
                onDismiss = { showCreateSheet = false }
            )
        }
        if (showChangeSpoolSheet) {
            AssignSpoolSheet(
                tagPayload = result.tagPayload,
                uidHex = result.cardUid,
                viewModel = viewModel,
                onDismiss = { showChangeSpoolSheet = false }
            )
        }
        if (showDetachConfirm && result.spoolResponse != null) {
            AlertDialog(
                onDismissRequest = { showDetachConfirm = false },
                title = { Text("Unlink from Spool") },
                text = { Text("Unlink ${result.cardUid} from ${result.spoolResponse.displayName}?") },
                confirmButton = {
                    TextButton(onClick = {
                        showDetachConfirm = false
                        viewModel.removeTag(result.cardUid, result.spoolResponse)
                    }) { Text("Unlink", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showDetachConfirm = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun ScanIndicator(isScanning: Boolean) {
    val scanColor = Color(0xFF34C759)
    val idleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
        Box(
            modifier = Modifier.size(160.dp).background(
                if (isScanning) scanColor.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                CircleShape
            )
        )
        Canvas(modifier = Modifier.size(140.dp)) {
            drawCircle(
                color = if (isScanning) scanColor else idleColor,
                radius = size.minDimension / 2 - 1.5.dp.toPx(),
                style = Stroke(width = 3.dp.toPx())
            )
        }
        if (isScanning) {
            CircularProgressIndicator(modifier = Modifier.size(90.dp), color = scanColor, strokeWidth = 3.dp)
        } else {
            Icon(Icons.Default.Nfc, contentDescription = null, modifier = Modifier.size(52.dp), tint = idleColor)
        }
    }
}

@Composable
private fun SpoolmanSectionLabel() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(start = 4.dp)
    ) {
        Icon(Icons.Default.Nfc, contentDescription = null,
            modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Spoolman", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
