package dev.pages.paxx12.spoollink.viewmodel

import android.app.Application
import android.content.Context
import android.nfc.NdefMessage
import android.nfc.Tag
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.pages.paxx12.spoollink.api.SpoolmanApi
import dev.pages.paxx12.spoollink.formats.TagFormatParser
import dev.pages.paxx12.spoollink.model.*
import dev.pages.paxx12.spoollink.formats.MifareClassicReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SpoolmanViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("spoolman_prefs", Context.MODE_PRIVATE)
    private val defaultBaseUrl = "http://spoolman.local:7912"

    var baseUrl by mutableStateOf(
        SpoolmanApi.normalizeUrl(prefs.getString("base_url", defaultBaseUrl) ?: defaultBaseUrl)
    )
        private set

    private var api = SpoolmanApi(baseUrl)

    var isScanning by mutableStateOf(false)
        private set
    var statusMessage by mutableStateOf("Ready to scan")
        private set
    val scanHistory = mutableStateListOf<ScanResult>()
    var lastResult by mutableStateOf<ScanResult?>(null)
        private set
    val spools = mutableStateListOf<SpoolResponse>()
    var isFetchingSpools by mutableStateOf(false)
        private set
    var hasMoreSpools by mutableStateOf(false)
        private set
    var spoolsError by mutableStateOf<String?>(null)
        private set
    var pendingAssignSpool by mutableStateOf<SpoolResponse?>(null)
        private set
    var isCreatingSpool by mutableStateOf(false)
        private set
    val availableFilaments = mutableStateListOf<SpoolResponse.FilamentResponse>()
    var isLoadingFilaments by mutableStateOf(false)
        private set
    var filamentsErrorMessage by mutableStateOf<String?>(null)
        private set
    var filamentPresets by mutableStateOf(FilamentPresets.load(prefs))

    private var spoolsOffset = 0
    private val spoolsPageSize = 20

    fun savedBaseUrl(): String = baseUrl

    fun updateBaseUrl(url: String) {
        val normalized = SpoolmanApi.normalizeUrl(url)
        baseUrl = normalized
        prefs.edit().putString("base_url", normalized).apply()
        api.updateBaseUrl(normalized)
    }

    fun savedNameStyle(): FilamentNameStyle =
        FilamentNameStyle.fromName(prefs.getString("filamentNameStyle", null))

    fun saveNameStyle(style: FilamentNameStyle) {
        prefs.edit().putString("filamentNameStyle", style.name).apply()
    }

    fun updatePresets(presets: FilamentPresets) {
        filamentPresets = presets
        presets.save(prefs)
    }

    fun ensureSpoolsLoaded() {
        if (spools.isEmpty() && !isFetchingSpools) fetchSpools()
    }

    fun fetchSpools(reset: Boolean = true) {
        if (isFetchingSpools) return
        val offset = if (reset) 0 else spoolsOffset
        isFetchingSpools = true
        viewModelScope.launch {
            try {
                spoolsError = null
                val page = api.fetchSpools(limit = spoolsPageSize, offset = offset)
                val active = page.filter { !it.archived }
                if (reset) {
                    spools.clear()
                    spools.addAll(active)
                } else {
                    spools.addAll(active)
                }
                spoolsOffset = offset + page.size
                hasMoreSpools = page.size == spoolsPageSize
            } catch (e: Exception) {
                spoolsError = e.message ?: "Unable to load spools"
                hasMoreSpools = false
            }
            isFetchingSpools = false
        }
    }

    fun loadMoreSpools() {
        if (hasMoreSpools && !isFetchingSpools) fetchSpools(reset = false)
    }

    suspend fun refreshSpool(id: Int): SpoolResponse? {
        val updated = try { api.getSpool(id) } catch (_: Exception) { return null }
        val idx = spools.indexOfFirst { it.id == id }
        if (idx >= 0) spools[idx] = updated
        for (i in 0 until scanHistory.size) {
            if (scanHistory[i].spoolId == id) scanHistory[i] = scanHistory[i].withSpoolResponse(updated)
        }
        if (lastResult?.spoolId == id) lastResult = lastResult?.withSpoolResponse(updated)
        return updated
    }

    fun removeTag(uidHex: String, spool: SpoolResponse) {
        viewModelScope.launch {
            try {
                val updatedUIDs = spool.tagUIDs.filter { it.uppercase() != uidHex.uppercase() }
                api.updateSpoolCardUids(spool.id, updatedUIDs)
                refreshSpool(spool.id)
                if (lastResult?.spoolId == spool.id) {
                    lastResult = lastResult?.let {
                        ScanResult(it.id, it.timestamp, null, it.spoolName,
                            it.cardUid, it.success, it.message, it.tagPayload, null)
                    }
                    statusMessage = "Tag unlinked from ${spool.displayName}"
                }
            } catch (_: Exception) { }
        }
    }

    fun removeAllTags(spool: SpoolResponse) {
        viewModelScope.launch {
            try {
                api.updateSpoolCardUids(spool.id, emptyList())
                refreshSpool(spool.id)
                if (lastResult?.spoolId == spool.id) {
                    lastResult = lastResult?.let {
                        ScanResult(
                            it.id,
                            it.timestamp,
                            null,
                            it.spoolName,
                            it.cardUid,
                            it.success,
                            it.message,
                            it.tagPayload,
                            null
                        )
                    }
                    statusMessage = "Tag unlinked from ${spool.displayName}"
                }
            } catch (_: Exception) { }
        }
    }

    fun startScanning() {
        lastResult = null
        isScanning = true
        statusMessage = "Scanning for NFC tags…"
    }

    fun stopScanning() {
        isScanning = false
        statusMessage = "Ready to scan"
    }

    fun startTagAssignment(spool: SpoolResponse) {
        pendingAssignSpool = spool
        lastResult = null
        isScanning = true
        statusMessage = "Scan tag to assign to ${spool.displayName}…"
    }

    fun cancelTagAssignment() {
        pendingAssignSpool = null
        isScanning = false
        statusMessage = "Ready to scan"
    }

    fun processNfcTag(tag: Tag, ndefMessage: NdefMessage?) {
        if (!isScanning && pendingAssignSpool == null) return
        val uidHex = tag.id.joinToString("") { "%02X".format(it) }
        isScanning = false
        statusMessage = "Tag detected"
        val pending = pendingAssignSpool
        pendingAssignSpool = null
        viewModelScope.launch {
            val resolvedNdef = ndefMessage ?: withContext(Dispatchers.IO) {
                MifareClassicReader.tryReadNdef(tag)
            }
            val payload = TagFormatParser.parse(resolvedNdef, tag)
            if (pending != null) {
                processAssignment(pending, uidHex, payload)
            } else {
                processTag(payload, uidHex)
            }
        }
    }

    suspend fun processTag(payload: NFCTagPayload, uidHex: String) {
        if (payload.spoolId == null) {
            val foundSpool = try { api.findSpoolsByCardUid(uidHex).firstOrNull() } catch (_: Exception) { null }
            val result = ScanResult(
                spoolId = foundSpool?.id,
                spoolName = foundSpool?.displayName ?: payload.displayTitle,
                cardUid = uidHex,
                success = true,
                message = "Tag read (no Spoolman link)",
                tagPayload = payload,
                spoolResponse = foundSpool
            )
            statusMessage = "Tag read: ${payload.displayTitle}"
            scanHistory.add(0, result)
            lastResult = result
            return
        }

        val spoolId = payload.spoolId ?: return
        statusMessage = "Fetching spool $spoolId…"
        try {
            val spool = api.getSpool(spoolId)
            val currentUIDs = spool.tagUIDs
            val updatedUIDs = if (currentUIDs.contains(uidHex)) currentUIDs else currentUIDs + uidHex

            statusMessage = "Updating spool…"
            api.updateSpoolCardUids(spoolId, updatedUIDs)

            statusMessage = "Cleaning up other spools…"
            val matching = api.findSpoolsByCardUid(uidHex)
            for (other in matching) {
                if (other.id == spoolId) continue
                val cleaned = other.tagUIDs.filter { it != uidHex }
                if (cleaned.size != other.tagUIDs.size) {
                    api.updateSpoolCardUids(other.id, cleaned)
                    refreshSpool(other.id)
                }
            }

            val updatedSpool = refreshSpool(spoolId) ?: spool
            statusMessage = "Synced: ${spool.displayName}"
            val result = ScanResult(
                spoolId = spoolId,
                spoolName = spool.displayName,
                cardUid = uidHex,
                success = true,
                message = "Synced successfully",
                tagPayload = payload,
                spoolResponse = updatedSpool
            )
            scanHistory.add(0, result)
            lastResult = result
        } catch (e: Exception) {
            statusMessage = "Error: ${e.message}"
            val result = ScanResult(
                spoolId = spoolId,
                spoolName = payload.displayTitle,
                cardUid = uidHex,
                success = false,
                message = e.message ?: "Unknown error",
                tagPayload = payload,
                spoolResponse = null
            )
            scanHistory.add(0, result)
            lastResult = result
        }
    }

    suspend fun processAssignment(spool: SpoolResponse, uidHex: String, tagPayload: NFCTagPayload) {
        statusMessage = "Assigning tag to ${spool.displayName}…"
        try {
            val currentUIDs = spool.tagUIDs
            val updatedUIDs = if (currentUIDs.contains(uidHex)) currentUIDs else currentUIDs + uidHex
            api.updateSpoolCardUids(spool.id, updatedUIDs)

            val matching = api.findSpoolsByCardUid(uidHex)
            for (other in matching) {
                if (other.id == spool.id) continue
                val cleaned = other.tagUIDs.filter { it != uidHex }
                if (cleaned.size != other.tagUIDs.size) {
                    api.updateSpoolCardUids(other.id, cleaned)
                    refreshSpool(other.id)
                }
            }

            val updatedSpool = refreshSpool(spool.id) ?: spool
            statusMessage = "Tag assigned to ${spool.displayName}"
            val result = ScanResult(
                spoolId = spool.id,
                spoolName = spool.displayName,
                cardUid = uidHex,
                success = true,
                message = "Tag assigned",
                tagPayload = tagPayload,
                spoolResponse = updatedSpool
            )
            scanHistory.add(0, result)
            lastResult = result
        } catch (e: Exception) {
            statusMessage = "Error: ${e.message}"
        }
    }

    fun loadFilamentsIfNeeded() {
        if (availableFilaments.isNotEmpty() || isLoadingFilaments) return
        loadFilaments()
    }

    fun loadFilaments() {
        if (isLoadingFilaments) return
        isLoadingFilaments = true
        filamentsErrorMessage = null
        viewModelScope.launch {
            try {
                val filaments = api.fetchFilaments(limit = 1000, offset = 0)
                availableFilaments.clear()
                availableFilaments.addAll(filaments)
            } catch (e: Exception) {
                filamentsErrorMessage = e.message
            } finally {
                isLoadingFilaments = false
            }
        }
    }

    suspend fun createSpoolFromTag(
        tagPayload: NFCTagPayload,
        uidHex: String,
        overrideMeta: FilamentMetadata? = null,
        selectedFilamentId: Int? = null
    ) {
        isCreatingSpool = true
        statusMessage = "Creating spool…"
        try {
            val meta = overrideMeta ?: tagPayload.filamentMetadata
            val nameStyle = savedNameStyle()
            val filamentId = if (selectedFilamentId != null) {
                selectedFilamentId
            } else {
                if (meta == null) {
                    statusMessage = "Missing filament details"
                    isCreatingSpool = false
                    return
                }
                val vendorId = meta.brand?.let {
                    try { api.findOrCreateVendor(it) } catch (_: Exception) { null }
                }
                api.createFilamentFromInfo(
                    filamentName = meta.filamentName(nameStyle),
                    vendorId = vendorId,
                    material = meta.material,
                    colorHex = meta.colorHex,
                    diameter = meta.diameter ?: 1.75,
                    weight = meta.weight,
                    nozzleTemp = meta.nozzleTemp,
                    bedTemp = meta.bedTemp,
                    variant = meta.subtype
                )
            }
            val newSpool = api.createSpoolFromInfo(
                filamentId = filamentId,
                cardUid = uidHex
            )
            statusMessage = "Spool created: ${newSpool.displayName}"
            val idx = scanHistory.indexOfFirst { it.cardUid == uidHex }
            if (idx >= 0) {
                val old = scanHistory[idx]
                val updated = ScanResult(
                    old.id,
                    old.timestamp,
                    newSpool.id,
                    newSpool.displayName,
                    uidHex,
                    true,
                    "Spool created",
                    old.tagPayload,
                    newSpool
                )
                scanHistory[idx] = updated
                lastResult = updated
            }
            if (spools.isNotEmpty()) refreshSpool(newSpool.id)
        } catch (e: Exception) {
            statusMessage = "Error creating spool: ${e.message}"
        }
        isCreatingSpool = false
    }
}
