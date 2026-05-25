package dev.pages.paxx12.spoollink.model

data class TagField(val label: String, val value: String, val icon: String? = null, val colorHex: String? = null)

interface NFCTagPayload {
    val formatName: String
    val typeDescription: String?
    val colorHex: String?
    val spoolId: Int?
    val displayTitle: String
    val fields: List<TagField>
    val filamentMetadata: FilamentMetadata?
}

data class RawNDEFTagPayload(
    val mimeType: String?,
    val payloadSize: Int,
    val recordCount: Int,
    val techType: String? = null
) : NFCTagPayload {
    override val formatName: String = techType ?: "NDEF"
    override val typeDescription: String? = mimeType
    override val colorHex: String? = null
    override val spoolId: Int? = null
    override val displayTitle: String = mimeType?.let { "MIME: $it" } ?: techType ?: "Unknown Tag"
    override val filamentMetadata: FilamentMetadata? = null
    override val fields: List<TagField>
        get() = buildList {
            mimeType?.let { add(TagField("MIME Type", it)) }
            if (recordCount > 0) add(TagField("Records", "$recordCount"))
            if (payloadSize > 0) add(TagField("Payload", "$payloadSize B"))
        }
}
