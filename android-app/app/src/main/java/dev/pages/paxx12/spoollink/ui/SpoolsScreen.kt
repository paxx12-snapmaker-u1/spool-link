package dev.pages.paxx12.spoollink.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.pages.paxx12.spoollink.model.SpoolResponse
import dev.pages.paxx12.spoollink.ui.components.*
import dev.pages.paxx12.spoollink.viewmodel.SpoolmanViewModel
import java.util.Calendar
import java.util.Date

enum class SpoolSort(val label: String) {
    DATE_ADDED("Date Added"),
    LAST_USED("Last Used"),
    NAME("Name"),
    MATERIAL("Material"),
    REMAINING("Remaining"),
    TAGS("Tags");

    fun compare(a: SpoolResponse, b: SpoolResponse, ascending: Boolean): Boolean {
        val r: Boolean = when (this) {
            DATE_ADDED -> (parseIso8601(a.registered) ?: Date(0)) > (parseIso8601(b.registered) ?: Date(0))
            LAST_USED -> (parseIso8601(a.lastUsed) ?: Date(0)) > (parseIso8601(b.lastUsed) ?: Date(0))
            NAME -> a.displayName < b.displayName
            MATERIAL -> (a.filament.material ?: "") < (b.filament.material ?: "")
            REMAINING -> (a.remainingWeight ?: 0.0) > (b.remainingWeight ?: 0.0)
            TAGS -> a.tagCount > b.tagCount
        }
        return if (ascending) r else !r
    }

    fun sectionHeader(spool: SpoolResponse): String {
        val cal = Calendar.getInstance()
        fun daysAgo(date: Date): Int {
            val now = cal.clone() as Calendar
            now.set(Calendar.HOUR_OF_DAY, 0); now.set(Calendar.MINUTE, 0)
            now.set(Calendar.SECOND, 0); now.set(Calendar.MILLISECOND, 0)
            val then = Calendar.getInstance().apply { time = date }
            then.set(Calendar.HOUR_OF_DAY, 0); then.set(Calendar.MINUTE, 0)
            then.set(Calendar.SECOND, 0); then.set(Calendar.MILLISECOND, 0)
            return ((now.timeInMillis - then.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
        }
        fun dateSection(s: String?): String {
            val d = parseIso8601(s) ?: return "Unknown"
            val days = daysAgo(d)
            return when {
                days == 0 -> "Today"; days <= 3 -> "Last 3 Days"
                days <= 7 -> "This Week"; days <= 30 -> "This Month"
                else -> "Older"
            }
        }
        return when (this) {
            DATE_ADDED -> if (spool.registered == null) "Unknown" else dateSection(spool.registered)
            LAST_USED -> if (spool.lastUsed == null) "Never Used" else dateSection(spool.lastUsed)
            NAME -> {
                val c = spool.displayName.firstOrNull()?.uppercaseChar() ?: '#'
                when (c) {
                    in 'A'..'E' -> "A – E"; in 'F'..'J' -> "F – J"; in 'K'..'O' -> "K – O"
                    in 'P'..'T' -> "P – T"; in 'U'..'Z' -> "U – Z"; else -> "#"
                }
            }
            MATERIAL -> spool.filament.material ?: "Unknown"
            REMAINING -> {
                val w = spool.remainingWeight
                when {
                    w == null -> "Unknown"; w == 0.0 -> "Empty"
                    w < 100 -> "< 100 g"; w < 500 -> "100 – 500 g"; else -> "> 500 g"
                }
            }
            TAGS -> when (spool.tagCount) { 0 -> "No Tags"; 1 -> "1 Tag"; else -> "Multiple Tags" }
        }
    }

    fun grouped(spools: List<SpoolResponse>, ascending: Boolean): List<Pair<String, List<SpoolResponse>>> {
        val sorted = spools.sortedWith { a, b -> if (compare(a, b, ascending)) -1 else 1 }
        val result = mutableListOf<Pair<String, MutableList<SpoolResponse>>>()
        for (spool in sorted) {
            val h = sectionHeader(spool)
            if (result.lastOrNull()?.first == h) result.last().second.add(spool)
            else result.add(h to mutableListOf(spool))
        }
        return result.map { it.first to it.second.toList() }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SpoolsScreen(viewModel: SpoolmanViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("spoolman_prefs", android.content.Context.MODE_PRIVATE) }
    var selectedSpool by remember { mutableStateOf<SpoolResponse?>(null) }
    var sortBy by remember { mutableStateOf(SpoolSort.entries.find { it.name == prefs.getString("spools_sort_by", null) } ?: SpoolSort.DATE_ADDED) }
    var sortAscending by remember { mutableStateOf(prefs.getBoolean("spools_sort_ascending", true)) }
    var showSortMenu by remember { mutableStateOf(false) }

    val grouped = remember(viewModel.spools.toList(), sortBy, sortAscending) {
        sortBy.grouped(viewModel.spools.toList(), sortAscending)
    }

    val duplicateTagUIDs = remember(viewModel.spools.toList()) {
        val counts = mutableMapOf<String, Int>()
        for (s in viewModel.spools) for (uid in s.tagUIDs) counts[uid] = (counts[uid] ?: 0) + 1
        counts.filter { it.value > 1 }.keys.toSet()
    }

    LaunchedEffect(Unit) {
        if (viewModel.spools.isEmpty()) viewModel.fetchSpools()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spools") },
                actions = {
                    IconButton(onClick = { viewModel.fetchSpools(reset = true) },
                        enabled = !viewModel.isFetchingSpools) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            Text("Sort by", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            SpoolSort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = {
                                        Row(horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()) {
                                            Text(sort.label)
                                            if (sortBy == sort) {
                                                Icon(
                                                    if (sortAscending) Icons.Default.KeyboardArrowUp
                                                    else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = null, modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        if (sortBy == sort) sortAscending = !sortAscending
                                        else { sortBy = sort; sortAscending = true }
                                        prefs.edit().putString("spools_sort_by", sortBy.name).putBoolean("spools_sort_ascending", sortAscending).apply()
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (viewModel.isFetchingSpools && viewModel.spools.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (viewModel.spoolsError != null && viewModel.spools.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null,
                        modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                    Text("Failed to Load Spools", style = MaterialTheme.typography.titleMedium)
                    Text(viewModel.spoolsError ?: "Unable to load spools",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { viewModel.fetchSpools(reset = true) }) { Text("Retry") }
                }
            }
        } else if (viewModel.spools.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Inventory2, contentDescription = null,
                        modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No Spools", style = MaterialTheme.typography.titleMedium)
                    Text("No spools found in Spoolman", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(contentPadding = padding) {
                grouped.forEach { (header, spoolItems) ->
                    stickyHeader {
                        Surface(modifier = Modifier.fillMaxWidth()) {
                            Text(header, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        }
                    }
                    items(spoolItems) { spool ->
                        SpoolRow(spool = spool, onClick = { selectedSpool = spool },
                            hasConflict = spool.tagUIDs.any { it in duplicateTagUIDs })
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
                    }
                }
                if (viewModel.hasMoreSpools) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            if (viewModel.isFetchingSpools) CircularProgressIndicator()
                            else TextButton(onClick = { viewModel.loadMoreSpools() }) { Text("Load More") }
                        }
                        LaunchedEffect(Unit) { viewModel.loadMoreSpools() }
                    }
                }
            }
        }
    }

    selectedSpool?.let { spool ->
        SpoolDetailSheet(
            spool = spool,
            viewModel = viewModel,
            onDismiss = { selectedSpool = null },
            onAssignTag = { s -> viewModel.startTagAssignment(s) }
        )
    }
}

@Composable
fun SpoolRow(spool: SpoolResponse, onClick: () -> Unit, hasConflict: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ColorSwatch(hex = spool.filament.colorHex, size = 44.dp, cornerRadius = 10.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(spool.displayName, style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                spool.filament.material?.let {
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(it, style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Text("·", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
                Text("#${spool.id}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        if (hasConflict) {
            Icon(Icons.Default.Warning, contentDescription = null,
                modifier = Modifier.size(16.dp), tint = Color(0xFFFF9500))
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpoolDetailSheet(
    spool: SpoolResponse,
    viewModel: SpoolmanViewModel,
    onDismiss: () -> Unit,
    onAssignTag: (SpoolResponse) -> Unit
) {
    val currentSpool = viewModel.spools.find { it.id == spool.id } ?: spool
    var showRemoveConfirm by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val baseUrl = viewModel.savedBaseUrl()
    val duplicateTagUIDs = remember(viewModel.spools.toList()) {
        val counts = mutableMapOf<String, Int>()
        for (s in viewModel.spools) for (uid in s.tagUIDs) counts[uid] = (counts[uid] ?: 0) + 1
        counts.filter { it.value > 1 }.keys.toSet()
    }
    val spoolWebUrl = "${baseUrl.trimEnd('/')}/spool/show/${spool.id}"

    AdaptiveModal(onDismissRequest = onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxHeight()
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColorSwatch(hex = currentSpool.filament.colorHex, size = 64.dp, cornerRadius = 14.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        Text(currentSpool.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            currentSpool.filament.material?.let {
                                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text(it, style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                }
                            }
                            TagCountBadge(currentSpool.tagCount)
                        }
                    }
                }
            }

            item {
                Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
                    Column {
                        DetailInfoRow("Spool ID", "#${currentSpool.id}")
                        currentSpool.remainingWeight?.let {
                            HorizontalDivider(Modifier.padding(start = 16.dp))
                            DetailInfoRow("Remaining", "${it.toInt()} g")
                        }
                        currentSpool.filament.colorHex?.let { hex ->
                            HorizontalDivider(Modifier.padding(start = 16.dp))
                            DetailInfoRow("Color") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(18.dp).let { m ->
                                        m.background(parseHexColor(hex) ?: MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                    })
                                    Text("#${hex.uppercase()}", style = MaterialTheme.typography.bodySmall,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                }
                            }
                        }
                        currentSpool.filament.diameter?.let {
                            HorizontalDivider(Modifier.padding(start = 16.dp))
                            DetailInfoRow("Diameter", "%.2f mm".format(it))
                        }
                        currentSpool.filament.weight?.let {
                            HorizontalDivider(Modifier.padding(start = 16.dp))
                            DetailInfoRow("Filament", "${it.toInt()} g")
                        }
                        currentSpool.filament.settingsExtruderTemp?.let {
                            HorizontalDivider(Modifier.padding(start = 16.dp))
                            DetailInfoRow("Nozzle", "$it °C")
                        }
                        currentSpool.filament.settingsBedTemp?.let {
                            HorizontalDivider(Modifier.padding(start = 16.dp))
                            DetailInfoRow("Bed", "$it °C")
                        }
                        formatDisplayDate(currentSpool.registered)?.let {
                            HorizontalDivider(Modifier.padding(start = 16.dp))
                            DetailInfoRow("Added", it)
                        }
                        formatDisplayDate(currentSpool.lastUsed)?.let {
                            HorizontalDivider(Modifier.padding(start = 16.dp))
                            DetailInfoRow("Last Used", it)
                        }
                    }
                }
            }

            item {
                Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Assigned Tags", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            if (currentSpool.tagUIDs.isNotEmpty()) {
                                TextButton(onClick = { showRemoveConfirm = true }) {
                                    Text("Remove All", color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        if (currentSpool.tagUIDs.isEmpty()) {
                            Text("No tags assigned", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp))
                        } else {
                            currentSpool.tagUIDs.forEachIndexed { i, uid ->
                                if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Nfc, contentDescription = null,
                                        modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(uid, style = MaterialTheme.typography.bodySmall,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        modifier = Modifier.weight(1f))
                                    if (uid in duplicateTagUIDs) {
                                        Icon(Icons.Default.Warning, contentDescription = null,
                                            modifier = Modifier.size(14.dp), tint = Color(0xFFFF9500))
                                    }
                                }
                            }
                            if (currentSpool.tagUIDs.any { it in duplicateTagUIDs }) {
                                HorizontalDivider(Modifier.padding(start = 16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null,
                                        modifier = Modifier.size(14.dp), tint = Color(0xFFFF9500))
                                    Text("One or more tags are assigned to multiple spools. Scan the tag to fix.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text("Tip: assign a tag from each side of the spool.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp))
            }

            item {
                val isAssigning = viewModel.pendingAssignSpool?.id == currentSpool.id
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onAssignTag(spool) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isAssigning
                    ) {
                        if (isAssigning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp).padding(end = 0.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Scanning for tag…", fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 4.dp))
                        } else {
                            Icon(Icons.Default.Nfc, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text("Assign NFC Tag", fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                    OutlinedButton(
                        onClick = { uriHandler.openUri(spoolWebUrl) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Open in Spoolman", fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove Tags") },
            text = { Text("Remove all ${currentSpool.tagCount} tag${if (currentSpool.tagCount == 1) "" else "s"} from ${currentSpool.displayName}?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeAllTags(spool)
                    showRemoveConfirm = false
                }) {
                    Text("Remove All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
    DetailInfoRow(label) { Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium) }
}

@Composable
private fun DetailInfoRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(80.dp))
        content()
    }
}
