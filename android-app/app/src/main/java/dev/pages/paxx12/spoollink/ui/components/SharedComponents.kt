package dev.pages.paxx12.spoollink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.pages.paxx12.spoollink.model.NFCTagPayload
import dev.pages.paxx12.spoollink.model.SpoolResponse
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun parseHexColor(hex: String?): Color? {
    val h = hex?.trimStart('#') ?: return null
    if (h.length != 6) return null
    return try {
        val r = h.substring(0, 2).toInt(16)
        val g = h.substring(2, 4).toInt(16)
        val b = h.substring(4, 6).toInt(16)
        Color(r, g, b)
    } catch (_: Exception) { null }
}

@Composable
fun ColorSwatch(hex: String?, size: Dp, cornerRadius: Dp) {
    val color = parseHexColor(hex)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(color ?: MaterialTheme.colorScheme.surfaceVariant)
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        if (color == null) {
            Icon(
                imageVector = Icons.Default.BrokenImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.4f)
            )
        }
    }
}

@Composable
fun TagCountBadge(count: Int) {
    val isTagged = count > 0
    Surface(
        shape = CircleShape,
        color = if (isTagged) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = "Tags $count",
            style = MaterialTheme.typography.labelSmall,
            color = if (isTagged) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun SpoolInfoRow(spool: SpoolResponse, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ColorSwatch(hex = spool.filament.colorHex, size = 36.dp, cornerRadius = 8.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(spool.displayName, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    spool.filament.material?.let {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(it, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    spool.remainingWeight?.let {
                        Text("${it.toInt()} g left", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TagCountBadge(spool.tagCount)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveModal(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 480.dp) {
            Dialog(
                onDismissRequest = onDismissRequest,
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = modifier.fillMaxWidth(0.82f).widthIn(max = 640.dp),
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 6.dp
                ) {
                    content()
                }
            }
        } else {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = onDismissRequest,
                sheetState = sheetState,
                modifier = modifier.fillMaxHeight(0.92f)
            ) {
                content()
            }
        }
    }
}

@Composable
fun TagDetailView(payload: NFCTagPayload, uidHex: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp
    ) {
        Column {
            TagDetailRow("Format", payload.formatName)
            payload.fields.forEach { field ->
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                val swatchColor = field.colorHex?.let { parseHexColor(it) }
                if (swatchColor != null) {
                    TagDetailRow(field.label) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp))
                                    .background(swatchColor)
                                    .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            )
                            Text(field.value, fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                } else {
                    TagDetailRow(field.label, field.value)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
            TagDetailRow("Card UID") {
                Text(uidHex, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1)
            }
        }
    }
}

@Composable
private fun TagDetailRow(label: String, value: String) {
    TagDetailRow(label) {
        Text(value, style = MaterialTheme.typography.bodySmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
    }
}

@Composable
private fun TagDetailRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp))
        content()
    }
}

fun parseIso8601(s: String?): Date? {
    s ?: return null
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd"
    )
    for (fmt in formats) {
        try {
            return SimpleDateFormat(fmt, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(s)
        } catch (_: Exception) { }
    }
    return null
}

fun formatDisplayDate(s: String?): String? {
    val date = parseIso8601(s) ?: return null
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
}
