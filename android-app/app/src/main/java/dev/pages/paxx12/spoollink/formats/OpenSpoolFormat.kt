package dev.pages.paxx12.spoollink.formats

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import dev.pages.paxx12.spoollink.model.*
import com.google.gson.Gson

object TagFormatParser {
    private val gson = Gson()

    fun parse(ndefMessage: NdefMessage?, tag: Tag? = null): NFCTagPayload {
        if (ndefMessage == null) {
            val techType = tag?.let { MifareClassicReader.tagDescription(it) }
            return RawNDEFTagPayload(null, 0, 0, techType)
        }
        for (record in ndefMessage.records) {
            if (record.tnf == NdefRecord.TNF_MIME_MEDIA) {
                val mimeType = String(record.type, Charsets.US_ASCII)
                if (mimeType == "application/json") {
                    val json = String(record.payload, Charsets.UTF_8)
                    try {
                        val raw = gson.fromJson(json, OpenSpoolPayload::class.java)
                        if (raw?.protocol == "openspool") return OpenSpoolTagPayload(raw)
                    } catch (_: Exception) { }
                }
            }
        }
        val first = ndefMessage.records.firstOrNull()
        val mimeType = first?.let {
            if (it.tnf == NdefRecord.TNF_MIME_MEDIA) String(it.type, Charsets.US_ASCII) else null
        }
        return RawNDEFTagPayload(
            mimeType = mimeType,
            payloadSize = first?.payload?.size ?: 0,
            recordCount = ndefMessage.records.size
        )
    }
}

data class OpenSpoolTagPayload(val raw: OpenSpoolPayload) : NFCTagPayload {
    override val formatName: String get() = "OpenSpool ${raw.version}"

    override val typeDescription: String?
        get() {
            val parts = listOfNotNull(raw.type.replaceFirstChar { it.uppercase() }, raw.subtype)
            return if (parts.isEmpty()) null else parts.joinToString(" ")
        }

    override val colorHex: String? get() = raw.colorHex
    override val spoolId: Int? get() = raw.spoolId

    override val displayTitle: String
        get() = filamentMetadata?.displayTitle ?: raw.type.replaceFirstChar { it.uppercase() }

    override val fields: List<TagField>
        get() = buildList {
            add(TagField("Type", raw.type.replaceFirstChar { it.uppercase() }))
            raw.subtype?.let { add(TagField("Material", it)) }
            raw.brand?.let { add(TagField("Brand", it)) }
            raw.colorHex?.let { add(TagField("Color", "#${it.uppercase()}", colorHex = it)) }
            if (raw.minTemp != null && raw.maxTemp != null)
                add(TagField("Nozzle", "${raw.minTemp}–${raw.maxTemp} °C"))
            if (raw.bedMinTemp != null && raw.bedMaxTemp != null)
                add(TagField("Bed", "${raw.bedMinTemp}–${raw.bedMaxTemp} °C"))
            raw.weight?.let { add(TagField("Weight", "${it.toInt()} g")) }
            raw.diameter?.let { add(TagField("Diameter", "$it mm")) }
            raw.spoolId?.let { add(TagField("Spool ID", "#$it")) }
        }

    override val filamentMetadata: FilamentMetadata
        get() = FilamentMetadata(
            brand = raw.brand,
            material = raw.type.uppercase(),
            subtype = raw.subtype,
            colorHex = raw.colorHex,
            diameter = raw.diameter,
            weight = raw.weight,
            nozzleTemp = raw.maxTemp?.toIntOrNull(),
            bedTemp = raw.bedMaxTemp?.toIntOrNull(),
            spoolId = raw.spoolId
        )
}
