package dev.pages.paxx12.spoollink.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.pages.paxx12.spoollink.model.FilamentMetadata
import dev.pages.paxx12.spoollink.model.NFCTagPayload
import dev.pages.paxx12.spoollink.model.SpoolResponse
import dev.pages.paxx12.spoollink.ui.components.AdaptiveModal
import dev.pages.paxx12.spoollink.ui.components.parseHexColor
import dev.pages.paxx12.spoollink.viewmodel.SpoolmanViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSpoolSheet(
    tagPayload: NFCTagPayload,
    uidHex: String,
    viewModel: SpoolmanViewModel,
    onDismiss: () -> Unit
) {
    val meta = tagPayload.filamentMetadata
    var brand by remember { mutableStateOf(meta?.brand ?: "") }
    var material by remember { mutableStateOf(meta?.material ?: "") }
    var subtype by remember { mutableStateOf(meta?.subtype ?: "") }
    var colorHex by remember { mutableStateOf(meta?.colorHex?.uppercase() ?: "FFFFFF") }
    var diameter by remember { mutableStateOf(meta?.diameter?.let { "%.2f".format(it) } ?: "1.75") }
    var weight by remember { mutableStateOf(meta?.weight?.let { "${it.toInt()}" } ?: "1000") }
    var nozzleTemp by remember { mutableStateOf(meta?.nozzleTemp?.toString() ?: "") }
    var bedTemp by remember { mutableStateOf(meta?.bedTemp?.toString() ?: "") }

    var selectedFilamentId by remember { mutableStateOf<Int?>(null) }
    var showFilamentPicker by remember { mutableStateOf(false) }
    var showBrandPicker by remember { mutableStateOf(false) }
    var showMaterialPicker by remember { mutableStateOf(false) }
    var showVariantPicker by remember { mutableStateOf(false) }
    var showWeightPicker by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }

    val selectedFilament = viewModel.availableFilaments.firstOrNull { it.id == selectedFilamentId }
    val isUsingExistingFilament = selectedFilament != null

    val scope = rememberCoroutineScope()

    LaunchedEffect(material, isUsingExistingFilament) {
        if (isUsingExistingFilament) return@LaunchedEffect
        val key = material.trim().uppercase()
        val defaults = materialDefaults[key]
        if (defaults != null) {
            if (nozzleTemp.isBlank()) nozzleTemp = defaults.first.toString()
            if (bedTemp.isBlank()) bedTemp = defaults.second.toString()
        }
    }

    LaunchedEffect(selectedFilamentId) {
        val filament = viewModel.availableFilaments.firstOrNull { it.id == selectedFilamentId } ?: return@LaunchedEffect
        brand = filament.vendor?.name ?: ""
        material = filament.material ?: ""
        subtype = filament.variantDecoded ?: ""
        colorHex = (filament.colorHex ?: "").uppercase()
        diameter = filament.diameter?.let { "%.2f".format(it) } ?: "1.75"
        weight = filament.weight?.let { "${it.toInt()}" } ?: ""
        nozzleTemp = filament.settingsExtruderTemp?.toString() ?: ""
        bedTemp = filament.settingsBedTemp?.toString() ?: ""
    }

    LaunchedEffect(Unit) {
        viewModel.ensureSpoolsLoaded()
    }

    val brandSuggestions = remember(viewModel.filamentPresets, viewModel.spools.toList()) {
        val spoolman = viewModel.spools.mapNotNull { it.filament.vendor?.name }.toSet()
        viewModel.filamentPresets.brands + spoolman.subtract(viewModel.filamentPresets.brands.toSet()).sorted()
    }
    val materialSuggestions = remember(viewModel.filamentPresets, viewModel.spools.toList()) {
        val spoolman = viewModel.spools.mapNotNull { it.filament.material }.toSet()
        viewModel.filamentPresets.materials + spoolman.subtract(viewModel.filamentPresets.materials.toSet()).sorted()
    }
    val variantSuggestions = listOf("") + viewModel.filamentPresets.variants

    AdaptiveModal(onDismissRequest = onDismiss, modifier = Modifier.fillMaxHeight()) {
        Column(Modifier.fillMaxWidth().fillMaxHeight()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(Icons.Default.Inventory2, contentDescription = null,
                        modifier = Modifier.padding(12.dp).size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("New Spool", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Create a Spoolman spool and link this NFC tag.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel")
                }
            }

            LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp),
                modifier = Modifier.weight(1f)) {
                item {
                    Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(enabled = !viewModel.isLoadingFilaments) {
                                    viewModel.loadFilamentsIfNeeded()
                                    showFilamentPicker = true
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    selectedFilament?.let { "${listOfNotNull(it.vendor?.name, it.name).joinToString(" – ").ifBlank { "Unnamed Filament" }} (#${it.id})" } ?: "Create New",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                when {
                                    viewModel.isLoadingFilaments -> Text("Loading filaments…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    viewModel.filamentsErrorMessage != null -> Text(viewModel.filamentsErrorMessage!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (viewModel.isLoadingFilaments) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Pick filament",
                                    modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                item { FormSectionHeader("Filament") }
                item {
                    Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
                        Column {
                            SettingsPickRow("Brand", brand, "e.g. Bambu Lab",
                                onClick = { showBrandPicker = true },
                                locked = isUsingExistingFilament)
                            HorizontalDivider(Modifier.padding(start = 16.dp))
                            SettingsPickRow("Material", material, "PLA, PETG, ASA…",
                                onClick = { showMaterialPicker = true },
                                locked = isUsingExistingFilament)
                            HorizontalDivider(Modifier.padding(start = 16.dp))
                            SettingsPickRow("Variant", subtype, "Basic, Matte…",
                                onClick = { showVariantPicker = true },
                                locked = isUsingExistingFilament)
                            HorizontalDivider(Modifier.padding(start = 16.dp))
                            SettingsColorRow(colorHex = colorHex,
                                onPickerTap = { showColorPicker = true },
                                locked = isUsingExistingFilament)
                        }
                    }
                }

                item { FormSectionHeader("Properties") }
                item {
                    Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
                        Column {
                            SettingsInputRow("Diameter (mm)", diameter, "1.75", KeyboardType.Decimal,
                                onValueChange = { diameter = it },
                                locked = isUsingExistingFilament)
                            HorizontalDivider(Modifier.padding(start = 16.dp))
                            SettingsPickRow("Weight (g)", weight, "1000",
                                onClick = { showWeightPicker = true },
                                locked = isUsingExistingFilament)
                            HorizontalDivider(Modifier.padding(start = 16.dp))
                            SettingsInputRow("Nozzle temp (°C)", nozzleTemp, "220", KeyboardType.Number,
                                onValueChange = { nozzleTemp = it },
                                locked = isUsingExistingFilament)
                            HorizontalDivider(Modifier.padding(start = 16.dp))
                            SettingsInputRow("Bed temp (°C)", bedTemp, "60", KeyboardType.Number,
                                onValueChange = { bedTemp = it },
                                locked = isUsingExistingFilament)
                        }
                    }
                }

                item { FormSectionHeader("Tag") }
                item {
                    Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Card UID", style = MaterialTheme.typography.bodyMedium)
                            Text(uidHex, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }

            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel", fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick = {
                        scope.launch {
                            viewModel.createSpoolFromTag(
                                tagPayload = tagPayload,
                                uidHex = uidHex,
                                overrideMeta = FilamentMetadata(
                                    brand = brand.trimOrNull(),
                                    material = material.trimOrNull(),
                                    subtype = subtype.trimOrNull(),
                                    colorHex = colorHex.trimOrNull(),
                                    diameter = diameter.toDoubleOrNull() ?: 1.75,
                                    weight = weight.toDoubleOrNull(),
                                    nozzleTemp = nozzleTemp.toIntOrNull(),
                                    bedTemp = bedTemp.toIntOrNull(),
                                    spoolId = meta?.spoolId
                                ),
                                selectedFilamentId = selectedFilamentId
                            )
                            onDismiss()
                        }
                    },
                    enabled = !viewModel.isCreatingSpool,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (viewModel.isCreatingSpool) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Create Spool", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showBrandPicker) {
        PickerSheet("Brand", brandSuggestions, brand, onSelect = { brand = it; showBrandPicker = false }, onDismiss = { showBrandPicker = false })
    }
    if (showMaterialPicker) {
        PickerSheet("Material", materialSuggestions, material, onSelect = { material = it; showMaterialPicker = false }, onDismiss = { showMaterialPicker = false })
    }
    if (showVariantPicker) {
        PickerSheet("Variant", variantSuggestions, subtype, onSelect = { subtype = it; showVariantPicker = false }, onDismiss = { showVariantPicker = false })
    }
    if (showWeightPicker) {
        PickerSheet("Weight (g)", viewModel.filamentPresets.weights, weight,
            onSelect = { weight = it; showWeightPicker = false }, onDismiss = { showWeightPicker = false })
    }
    if (showColorPicker) {
        ColorPickerSheet(
            initial = colorHex,
            onSelect = { colorHex = it.uppercase(); showColorPicker = false },
            onDismiss = { showColorPicker = false }
        )
    }
    if (showFilamentPicker) {
        FilamentPickerSheet(
            filaments = viewModel.availableFilaments,
            selectedFilamentId = selectedFilamentId,
            onSelect = {
                selectedFilamentId = it
                showFilamentPicker = false
            },
            onDismiss = { showFilamentPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilamentPickerSheet(
    filaments: List<SpoolResponse.FilamentResponse>,
    selectedFilamentId: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(filaments, search) {
        val q = search.trim().lowercase()
        if (q.isBlank()) filaments
        else filaments.filter {
            listOfNotNull(it.vendor?.name, it.name, it.material, it.colorHex)
                .any { s -> s.lowercase().contains(q) }
        }
    }

    AdaptiveModal(onDismissRequest = onDismiss, modifier = Modifier.fillMaxHeight()) {
        Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 3.dp) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Filament", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    singleLine = true,
                    placeholder = { Text("Search filament") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
                    item {
                        FilamentPickerRow(
                            title = "Create New",
                            subtitle = "Create new filament from entered fields",
                            colorHex = null,
                            selected = selectedFilamentId == null,
                            onClick = { onSelect(null) }
                        )
                    }
                    items(filtered, key = { it.id }) { filament ->
                        FilamentPickerRow(
                            title = listOfNotNull(filament.vendor?.name, filament.name).joinToString(" – ").ifBlank { "Unnamed Filament" },
                            subtitle = listOfNotNull(
                                filament.material,
                                filament.colorHex?.uppercase()?.let { "#$it" }
                            ).joinToString(" • "),
                            colorHex = filament.colorHex,
                            selected = selectedFilamentId == filament.id,
                            onClick = { onSelect(filament.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilamentPickerRow(
    title: String,
    subtitle: String,
    colorHex: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(16.dp).clip(RoundedCornerShape(4.dp))
                .background(parseHexColor(colorHex) ?: MaterialTheme.colorScheme.surfaceVariant)
                .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsPickRow(
    label: String,
    value: String,
    placeholder: String,
    onClick: () -> Unit,
    locked: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !locked, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                value.ifEmpty { placeholder },
                style = MaterialTheme.typography.bodySmall,
                color = if (value.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (locked) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Icon(Icons.Default.ChevronRight, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsInputRow(
    label: String,
    value: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit,
    locked: Boolean = false
) {
    var showDialog by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !locked, onClick = { showDialog = true }).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                value.ifEmpty { placeholder },
                style = MaterialTheme.typography.bodySmall,
                color = if (value.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (locked) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (showDialog) {
        TextInputDialog(
            title = label,
            initial = value,
            placeholder = placeholder,
            keyboardType = keyboardType,
            onConfirm = { onValueChange(it); showDialog = false },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun SettingsColorRow(
    colorHex: String,
    onPickerTap: () -> Unit,
    locked: Boolean = false
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !locked, onClick = onPickerTap).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Color", style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("#$colorHex",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val swatchColor = parseHexColor(colorHex) ?: Color.Gray
            Box(
                Modifier.size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(swatchColor)
                    .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            )
            if (locked) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Icon(Icons.Default.ChevronRight, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    initial: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(placeholder) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun FormSectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerSheet(
    title: String,
    items: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    val filtered = if (searchText.isEmpty()) items else items.filter { it.contains(searchText, ignoreCase = true) }
    val showCustom = searchText.isNotBlank() && filtered.none { it.equals(searchText, ignoreCase = true) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.92f)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onDismiss) { Text("Done") }
            }
            OutlinedTextField(
                value = searchText, onValueChange = { searchText = it },
                placeholder = { Text("Search or type to add…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp),
                contentPadding = PaddingValues(bottom = 32.dp)) {
                if (showCustom) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(searchText) }.padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Text("Use \"$searchText\"", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        HorizontalDivider(Modifier.padding(start = 16.dp))
                    }
                }
                items(filtered) { item ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(item) }.padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (item.isEmpty()) "None" else item,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (item.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified
                        )
                        if (selected == item) Icon(Icons.Default.Check, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider(Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColorPickerSheet(initial: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val initColor = parseHexColor(initial) ?: Color(0xFFFFFFFF)
    val initHsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (initColor.red * 255).toInt(), (initColor.green * 255).toInt(), (initColor.blue * 255).toInt(), initHsv
    )

    var hue by remember { mutableFloatStateOf(initHsv[0]) }
    var sat by remember { mutableFloatStateOf(initHsv[1]) }
    var bri by remember { mutableFloatStateOf(initHsv[2]) }
    var hexInput by remember { mutableStateOf(initial.trimStart('#').uppercase()) }
    var hexError by remember { mutableStateOf(false) }

    fun currentColor(): Color {
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, bri))
        return Color(argb)
    }

    fun colorToHex(c: Color) = "%02X%02X%02X".format(
        (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt()
    )

    fun syncHexFromHsv() { hexInput = colorToHex(currentColor()); hexError = false }
    fun syncHsvFromHex(h: String) {
        val c = parseHexColor(h)
        if (c != null) {
            val arr = FloatArray(3)
            android.graphics.Color.RGBToHSV(
                (c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(), arr
            )
            hue = arr[0]; sat = arr[1]; bri = arr[2]
            hexError = false
        } else { hexError = h.length == 6 }
    }

    val colorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = colorSheetState,
        modifier = Modifier.fillMaxHeight(0.92f)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Color", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onSelect(colorToHex(currentColor())) },
                        shape = RoundedCornerShape(10.dp)) { Text("Done") }
                }
            }

            Box(
                Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(8.dp))
                    .background(currentColor())
                    .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            )

            SatBriCanvas(hue = hue, sat = sat, bri = bri, onChanged = { s, b ->
                sat = s; bri = b; syncHexFromHsv()
            })

            HueSlider(hue = hue, onHueChange = { hue = it; syncHexFromHsv() })

            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("#", style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { v ->
                        val clean = v.trimStart('#').uppercase().filter { it.isLetterOrDigit() }.take(6)
                        hexInput = clean
                        syncHsvFromHex(clean)
                    },
                    label = { Text("Hex") },
                    isError = hexError,
                    singleLine = true,
                    modifier = Modifier.width(140.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                )
            }

            Text("Presets", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            ColorPresetGrid(selected = colorToHex(currentColor()), onSelect = { hex ->
                hexInput = hex
                syncHsvFromHex(hex)
            })

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SatBriCanvas(hue: Float, sat: Float, bri: Float, onChanged: (Float, Float) -> Unit) {
    val pureHue = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    var size by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    Box(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val s = (offset.x / size.width).coerceIn(0f, 1f)
                    val b = (1f - offset.y / size.height).coerceIn(0f, 1f)
                    onChanged(s, b)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val s = (change.position.x / size.width).coerceIn(0f, 1f)
                    val b = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                    onChanged(s, b)
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            size = this.size
            drawRect(brush = Brush.horizontalGradient(listOf(Color.White, pureHue)))
            drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val cx = sat * this.size.width
            val cy = (1f - bri) * this.size.height
            drawCircle(color = Color.White, radius = 10.dp.toPx(), center = Offset(cx, cy), style = Stroke(2.dp.toPx()))
            drawCircle(color = Color.Black.copy(alpha = 0.4f), radius = 10.dp.toPx(), center = Offset(cx, cy), style = Stroke(1.dp.toPx()))
        }
    }
}

@Composable
private fun HueSlider(hue: Float, onHueChange: (Float) -> Unit) {
    val hueGradient = remember {
        Brush.horizontalGradient((0..12).map { i ->
            Color(android.graphics.Color.HSVToColor(floatArrayOf(i * 30f, 1f, 1f)))
        })
    }
    var width by remember { mutableFloatStateOf(1f) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(50))
            .pointerInput(Unit) {
                detectTapGestures { offset -> onHueChange((offset.x / width * 360f).coerceIn(0f, 360f)) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    onHueChange((change.position.x / width * 360f).coerceIn(0f, 360f))
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            width = size.width
            drawRect(brush = hueGradient)
            val cx = hue / 360f * size.width
            drawCircle(Color.White, radius = 12.dp.toPx(), center = Offset(cx, size.height / 2f),
                style = Stroke(2.5.dp.toPx()))
        }
    }
}

@Composable
private fun ColorPresetGrid(selected: String, onSelect: (String) -> Unit) {
    val cols = 9
    val rows = presetColors.chunked(cols)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { hex ->
                    val color = parseHexColor(hex) ?: Color.Gray
                    val isSelected = hex.equals(selected, ignoreCase = true)
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                if (isSelected) 2.5.dp else 0.5.dp,
                                if (isSelected) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                CircleShape
                            )
                            .clickable { onSelect(hex) }
                    )
                }
            }
        }
    }
}

private val presetColors = listOf(
    "FFFFFF", "F5F5F5", "E0E0E0", "BDBDBD", "9E9E9E", "757575", "616161", "424242", "212121",
    "FFEBEE", "FFCDD2", "EF9A9A", "E57373", "EF5350", "F44336", "E53935", "C62828", "B71C1C",
    "FFF3E0", "FFE0B2", "FFCC80", "FFB74D", "FFA726", "FF9800", "FB8C00", "E65100", "BF360C",
    "FFFDE7", "FFF9C4", "FFF176", "FFEE58", "FFEB3B", "FDD835", "F9A825", "F57F17", "FF6F00",
    "F1F8E9", "DCEDC8", "C5E1A5", "AED581", "9CCC65", "8BC34A", "7CB342", "558B2F", "1B5E20",
    "E0F7FA", "B2EBF2", "80DEEA", "4DD0E1", "26C6DA", "00BCD4", "00ACC1", "00838F", "006064",
    "E3F2FD", "BBDEFB", "90CAF9", "64B5F6", "42A5F5", "2196F3", "1E88E5", "1565C0", "0D47A1",
    "EDE7F6", "D1C4E9", "B39DDB", "9575CD", "7E57C2", "673AB7", "5E35B1", "4527A0", "311B92",
    "FCE4EC", "F8BBD0", "F48FB1", "F06292", "EC407A", "E91E63", "D81B60", "880E4F", "FF80AB"
)

private fun String.trimOrNull(): String? = trim().ifEmpty { null }

private val materialDefaults = mapOf(
    "PLA" to (220 to 60), "PETG" to (240 to 80), "ABS" to (250 to 100),
    "ASA" to (255 to 100), "TPU" to (230 to 50), "NYLON" to (260 to 90),
    "PA" to (260 to 90), "PC" to (270 to 110)
)
