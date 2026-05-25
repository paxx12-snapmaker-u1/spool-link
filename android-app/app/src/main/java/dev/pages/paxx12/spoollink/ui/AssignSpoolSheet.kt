package dev.pages.paxx12.spoollink.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.pages.paxx12.spoollink.model.NFCTagPayload
import dev.pages.paxx12.spoollink.model.SpoolResponse
import dev.pages.paxx12.spoollink.ui.components.ColorSwatch
import dev.pages.paxx12.spoollink.ui.components.TagCountBadge
import dev.pages.paxx12.spoollink.viewmodel.SpoolmanViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignSpoolSheet(
    tagPayload: NFCTagPayload,
    uidHex: String,
    viewModel: SpoolmanViewModel,
    onDismiss: () -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    var isAssigning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val filteredSpools = remember(viewModel.spools.toList(), searchText) {
        if (searchText.isEmpty()) viewModel.spools.toList()
        else viewModel.spools.filter {
            it.displayName.contains(searchText, ignoreCase = true) ||
                it.filament.material?.contains(searchText, ignoreCase = true) == true ||
                it.filament.vendor?.name?.contains(searchText, ignoreCase = true) == true
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel.spools.isEmpty()) viewModel.fetchSpools()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { if (!isAssigning) onDismiss() },
        sheetState = sheetState, modifier = Modifier.fillMaxHeight(0.92f)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Assign to Spool", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onDismiss, enabled = !isAssigning) { Text("Cancel") }
            }

            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("Search spools") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (viewModel.isFetchingSpools && viewModel.spools.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredSpools.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("No spools found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(filteredSpools) { spool ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = !isAssigning) {
                                scope.launch {
                                    isAssigning = true
                                    viewModel.processAssignment(spool, uidHex, tagPayload)
                                    isAssigning = false
                                    onDismiss()
                                }
                            }.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ColorSwatch(hex = spool.filament.colorHex, size = 40.dp, cornerRadius = 8.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(spool.displayName, style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium, maxLines = 1)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("#${spool.id}", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    spool.filament.material?.let {
                                        Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall)
                                        Text(it, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    spool.remainingWeight?.let {
                                        Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall)
                                        Text("${it.toInt()} g", style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall)
                                    TagCountBadge(spool.tagCount)
                                }
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        }
                        HorizontalDivider(Modifier.padding(start = 68.dp))
                    }
                }
            }
        }

        if (isAssigning) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
