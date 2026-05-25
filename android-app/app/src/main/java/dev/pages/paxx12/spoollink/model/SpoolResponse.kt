package dev.pages.paxx12.spoollink.model

import com.google.gson.annotations.SerializedName

data class SpoolResponse(
    val id: Int,
    val filament: FilamentResponse,
    @SerializedName("remaining_weight") val remainingWeight: Double?,
    val archived: Boolean = false,
    val registered: String?,
    @SerializedName("last_used") val lastUsed: String?,
    val extra: ExtraData?
) {
    data class FilamentResponse(
        val id: Int = 0,
        val name: String?,
        val vendor: VendorResponse?,
        val material: String?,
        @SerializedName("color_hex") val colorHex: String?,
        val diameter: Double?,
        val weight: Double?,
        @SerializedName("settings_extruder_temp") val settingsExtruderTemp: Int?,
        @SerializedName("settings_bed_temp") val settingsBedTemp: Int?,
        val extra: ExtraFilamentData? = null
    ) {
        data class VendorResponse(val id: Int = 0, val name: String?)

        data class ExtraFilamentData(val variant: String? = null)

        val variantDecoded: String?
            get() {
                val raw = extra?.variant?.takeIf { it.isNotEmpty() } ?: return null
                return if (raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"'))
                    raw.drop(1).dropLast(1).takeIf { it.isNotEmpty() }
                else raw.takeIf { it.isNotEmpty() }
            }
    }

    data class ExtraData(
        @SerializedName("card_uids") val cardUids: String?
    )

    val displayName: String
        get() {
            val parts = listOfNotNull(filament.vendor?.name, filament.name)
                .filter { it.isNotEmpty() }
            return if (parts.isEmpty()) "Unnamed Spool" else parts.joinToString(" – ")
        }

    val tagUIDs: List<String>
        get() {
            val raw = extra?.cardUids ?: return emptyList()
            if (raw.isEmpty()) return emptyList()
            val decoded = if (raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"'))
                raw.drop(1).dropLast(1)
            else raw
            return decoded.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }

    val tagCount: Int get() = tagUIDs.size
}
