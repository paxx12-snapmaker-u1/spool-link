package dev.pages.paxx12.spoollink.model

import com.google.gson.annotations.SerializedName

data class OpenSpoolPayload(
    val protocol: String,
    val version: String,
    val type: String,
    @SerializedName("color_hex") val colorHex: String? = null,
    val brand: String? = null,
    val subtype: String? = null,
    @SerializedName("min_temp") val minTemp: String? = null,
    @SerializedName("max_temp") val maxTemp: String? = null,
    @SerializedName("bed_min_temp") val bedMinTemp: String? = null,
    @SerializedName("bed_max_temp") val bedMaxTemp: String? = null,
    val alpha: String? = null,
    val weight: Double? = null,
    val diameter: Double? = null,
    @SerializedName("spool_id") val spoolId: Int? = null
)
