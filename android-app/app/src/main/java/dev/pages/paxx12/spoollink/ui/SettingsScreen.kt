package dev.pages.paxx12.spoollink.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.pages.paxx12.spoollink.api.SpoolmanApi
import dev.pages.paxx12.spoollink.model.FilamentNameStyle
import dev.pages.paxx12.spoollink.model.FilamentPresets
import dev.pages.paxx12.spoollink.viewmodel.SpoolmanViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SpoolmanViewModel) {
    var tempUrl by remember { mutableStateOf(viewModel.savedBaseUrl()) }
    var isTesting by remember { mutableStateOf(false) }
    var testLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var testPassed by remember { mutableStateOf(false) }
    var showSavedFeedback by remember { mutableStateOf(false) }
    var nameStyle by remember { mutableStateOf(viewModel.savedNameStyle()) }
    var showStyleMenu by remember { mutableStateOf(false) }

    var presetsEditorTarget by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) { viewModel.ensureSpoolsLoaded() }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { innerPadding ->
    Box(Modifier.fillMaxSize().padding(innerPadding)) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SectionHeader("Spoolman Server")
            Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
                Column {
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it; testLogs = emptyList(); testPassed = false },
                        label = { Text("Server URL") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            capitalization = KeyboardCapitalization.None,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                    )

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            scope.launch {
                                isTesting = true; testLogs = emptyList(); testPassed = false
                                val result = SpoolmanApi.testConnection(tempUrl)
                                testLogs = result.logs; testPassed = result.succeeded
                                isTesting = false
                            }
                        },
                        enabled = !isTesting && tempUrl.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(Modifier.size(16.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null,
                                modifier = Modifier.size(16.dp).padding(end = 4.dp))
                        }
                        Text(if (isTesting) "Testing…" else "Test Connection", fontWeight = FontWeight.SemiBold)
                    }

                    if (testLogs.isNotEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            testLogs.forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = when {
                                        line.startsWith("✗") -> MaterialTheme.colorScheme.error
                                        line.startsWith("✓") -> Color(0xFF34C759)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }

                    if (testPassed) {
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.updateBaseUrl(tempUrl)
                                tempUrl = viewModel.savedBaseUrl()
                                testLogs = emptyList(); testPassed = false
                                showSavedFeedback = true
                                scope.launch {
                                    kotlinx.coroutines.delay(1500)
                                    showSavedFeedback = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34C759)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null,
                                modifier = Modifier.size(16.dp).padding(end = 4.dp))
                            Text("Save", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Enter the base URL of your Spoolman server (e.g., http://192.168.1.100:7912)",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp))
        }

        item {
            SectionHeader("Current Configuration")
            Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Saved URL", style = MaterialTheme.typography.bodyMedium)
                    Text(viewModel.savedBaseUrl(), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                }
            }
        }

        item {
            SectionHeader("Spool Creation")
            Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
                Column {
                    Box {
                        Row(
                            Modifier.fillMaxWidth().clickable { showStyleMenu = true }.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Filament name", style = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(nameStyle.displayName, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        DropdownMenu(expanded = showStyleMenu, onDismissRequest = { showStyleMenu = false }) {
                            FilamentNameStyle.entries.forEach { style ->
                                DropdownMenuItem(
                                    text = {
                                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text(style.displayName)
                                            if (nameStyle == style) {
                                                Icon(Icons.Default.Check, contentDescription = null,
                                                    modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    onClick = {
                                        nameStyle = style
                                        viewModel.saveNameStyle(style)
                                        showStyleMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Pattern used for the filament name when creating a new spool.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp))
        }

        item {
            SectionHeader("Filament Presets")
            Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
                Column {
                    listOf("Brands", "Materials", "Variants", "Weights").forEachIndexed { i, label ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp))
                        Row(
                            Modifier.fillMaxWidth().clickable { presetsEditorTarget = label }.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            Icon(Icons.Default.ChevronRight, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("Presets appear as quick-pick suggestions when creating a spool.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 28.dp))
            Spacer(Modifier.height(32.dp))
        }
    }
    }
    }

    if (showSavedFeedback) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                modifier = Modifier.padding(bottom = 80.dp)
            ) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34C759))
                    Text("URL saved", color = MaterialTheme.colorScheme.inverseOnSurface)
                }
            }
        }
    }

    presetsEditorTarget?.let { target ->
        val spoolmanBrands = remember(viewModel.spools.toList()) {
            viewModel.spools.mapNotNull { it.filament.vendor?.name }.distinct().sorted()
        }
        val spoolmanMaterials = remember(viewModel.spools.toList()) {
            viewModel.spools.mapNotNull { it.filament.material }.distinct().sorted()
        }

        val (currentItems, spoolmanSuggestions) = when (target) {
            "Brands" -> viewModel.filamentPresets.brands to spoolmanBrands
            "Materials" -> viewModel.filamentPresets.materials to spoolmanMaterials
            "Weights" -> viewModel.filamentPresets.weights to emptyList()
            else -> viewModel.filamentPresets.variants to emptyList()
        }

        PresetEditorSheet(
            title = target,
            items = currentItems,
            spoolmanSuggestions = spoolmanSuggestions,
            onSave = { updated ->
                val current = viewModel.filamentPresets
                val newPresets = when (target) {
                    "Brands" -> current.copy(brands = updated)
                    "Materials" -> current.copy(materials = updated)
                    "Weights" -> current.copy(weights = updated)
                    else -> current.copy(variants = updated)
                }
                viewModel.updatePresets(newPresets)
            },
            onDismiss = { presetsEditorTarget = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetEditorSheet(
    title: String,
    items: List<String>,
    spoolmanSuggestions: List<String>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var editItems by remember { mutableStateOf(items.toMutableList()) }
    var newItem by remember { mutableStateOf("") }

    val unusedSuggestions = remember(editItems, spoolmanSuggestions) {
        val existing = editItems.map { it.trim().lowercase() }.toSet()
        spoolmanSuggestions.filter { it.trim().lowercase() !in existing }
    }

    ModalBottomSheet(onDismissRequest = { onSave(editItems); onDismiss() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { onSave(editItems); onDismiss() }) { Text("Done") }
            }
            Spacer(Modifier.height(8.dp))

            Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
                Column {
                    editItems.forEachIndexed { i, item ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp))
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(item, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { editItems = editItems.toMutableList().also { it.removeAt(i) } }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(start = 16.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newItem,
                            onValueChange = { newItem = it },
                            placeholder = { Text("Add…") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                val t = newItem.trim()
                                if (t.isNotEmpty() && editItems.none { it.equals(t, true) }) {
                                    editItems = editItems.toMutableList().also { it.add(t) }; newItem = ""
                                }
                            })
                        )
                        IconButton(
                            onClick = {
                                val t = newItem.trim()
                                if (t.isNotEmpty() && editItems.none { it.equals(t, true) }) {
                                    editItems = editItems.toMutableList().also { it.add(t) }; newItem = ""
                                }
                            },
                            enabled = newItem.isNotBlank()
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = "Add",
                                tint = if (newItem.isNotBlank()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (unusedSuggestions.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("From Spoolman", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
                    Column {
                        unusedSuggestions.forEachIndexed { i, suggestion ->
                            if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp))
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    editItems = editItems.toMutableList().also { it.add(suggestion) }
                                }.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(suggestion, style = MaterialTheme.typography.bodyMedium)
                                Icon(Icons.Default.Add, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 8.dp)
    )
}
