package dev.pages.paxx12.spoollink.model

enum class FilamentNameStyle(val displayName: String) {
    BRAND_AND_SUBTYPE("Brand + Subtype/Material"),
    BRAND_MATERIAL_SUBTYPE("Brand + Material + Subtype"),
    MATERIAL_AND_SUBTYPE("Material + Subtype"),
    SUBTYPE_ONLY("Subtype only"),
    BRAND_COLOR_SUBTYPE("Brand + Color + Subtype/Material"),
    BRAND_MATERIAL_COLOR_SUBTYPE("Brand + Material + Color + Subtype"),
    COLOR_MATERIAL_SUBTYPE("Color + Material + Subtype"),
    COLOR_ONLY("Color only");

    companion object {
        fun fromName(name: String?): FilamentNameStyle =
            entries.find { it.name == name } ?: BRAND_AND_SUBTYPE
    }
}

data class FilamentMetadata(
    val brand: String?,
    val material: String?,
    val subtype: String?,
    val colorHex: String?,
    val diameter: Double?,
    val weight: Double?,
    val nozzleTemp: Int?,
    val bedTemp: Int?,
    val spoolId: Int?
) {
    val displayTitle: String
        get() {
            val parts = listOfNotNull(brand, subtype ?: material).filter { it.isNotEmpty() }
            return if (parts.isEmpty()) "Unknown" else parts.joinToString(" ")
        }

    val colorName: String?
        get() {
            val h = colorHex?.trimStart('#') ?: return null
            if (h.length != 6) return null
            val rv = h.substring(0, 2).toIntOrNull(16) ?: return null
            val gv = h.substring(2, 4).toIntOrNull(16) ?: return null
            val bv = h.substring(4, 6).toIntOrNull(16) ?: return null
            val r = rv / 255.0; val g = gv / 255.0; val b = bv / 255.0
            val maxC = maxOf(r, g, b); val minC = minOf(r, g, b); val delta = maxC - minC
            val l = (maxC + minC) / 2
            if (delta < 0.12) {
                return when {
                    l > 0.85 -> "White"
                    l < 0.15 -> "Black"
                    else -> "Gray"
                }
            }
            val s = if (l > 0.5) delta / (2 - maxC - minC) else delta / (maxC + minC)
            if (s < 0.15) return if (l > 0.7) "Silver" else "Gray"
            var hue = when {
                maxC == r -> { var h2 = ((g - b) / delta) % 6; if (h2 < 0) h2 += 6; h2 }
                maxC == g -> (b - r) / delta + 2
                else -> (r - g) / delta + 4
            } * 60
            if (hue >= 10 && hue < 40 && l < 0.45) return "Brown"
            return when {
                hue < 15 -> "Red"; hue < 40 -> "Orange"; hue < 65 -> "Yellow"
                hue < 165 -> "Green"; hue < 195 -> "Cyan"; hue < 255 -> "Blue"
                hue < 285 -> "Purple"; hue < 325 -> "Magenta"; hue < 345 -> "Pink"
                else -> "Red"
            }
        }

    fun filamentName(style: FilamentNameStyle = FilamentNameStyle.BRAND_AND_SUBTYPE): String {
        val b = brand?.trim()?.takeIf { it.isNotEmpty() }
        val m = material?.trim()?.takeIf { it.isNotEmpty() }
        val sub = subtype?.trim()?.takeIf { it.isNotEmpty() }
        val col = colorName

        val parts = when (style) {
            FilamentNameStyle.BRAND_AND_SUBTYPE -> listOfNotNull(b, sub ?: m)
            FilamentNameStyle.BRAND_MATERIAL_SUBTYPE -> listOfNotNull(b, m, sub)
            FilamentNameStyle.MATERIAL_AND_SUBTYPE -> listOfNotNull(m, sub)
            FilamentNameStyle.SUBTYPE_ONLY -> listOfNotNull(sub)
            FilamentNameStyle.BRAND_COLOR_SUBTYPE -> listOfNotNull(b, col, sub ?: m)
            FilamentNameStyle.BRAND_MATERIAL_COLOR_SUBTYPE -> listOfNotNull(b, m, col, sub)
            FilamentNameStyle.COLOR_MATERIAL_SUBTYPE -> listOfNotNull(col, m, sub)
            FilamentNameStyle.COLOR_ONLY -> listOfNotNull(col)
        }

        val deduped = parts.fold(emptyList<String>()) { acc, p ->
            if (acc.lastOrNull()?.equals(p, ignoreCase = true) == true) acc else acc + p
        }

        return deduped.joinToString(" ").ifEmpty { "Custom Filament" }
    }
}
