package dev.pages.paxx12.spoollink.model

import java.util.Date
import java.util.UUID

data class ScanResult(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Date = Date(),
    val spoolId: Int?,
    val spoolName: String,
    val cardUid: String,
    val success: Boolean,
    val message: String,
    val tagPayload: NFCTagPayload,
    val spoolResponse: SpoolResponse?
) {
    fun withSpoolResponse(spool: SpoolResponse?): ScanResult = copy(spoolResponse = spool)
}
