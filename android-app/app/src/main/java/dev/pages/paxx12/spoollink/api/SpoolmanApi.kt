package dev.pages.paxx12.spoollink.api

import dev.pages.paxx12.spoollink.model.SpoolResponse
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class SpoolmanApi(baseUrl: String) {
    private var normalizedUrl = normalizeUrl(baseUrl)
    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
    private var service: SpoolmanService = buildService(normalizedUrl)

    fun updateBaseUrl(newUrl: String) {
        normalizedUrl = normalizeUrl(newUrl)
        service = buildService(normalizedUrl)
    }

    private fun buildService(url: String): SpoolmanService = Retrofit.Builder()
        .baseUrl(url)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(SpoolmanService::class.java)

    suspend fun fetchSpools(limit: Int = 20, offset: Int = 0): List<SpoolResponse> =
        withContext(Dispatchers.IO) { service.fetchSpools(limit, offset) }

    suspend fun getSpool(id: Int): SpoolResponse =
        withContext(Dispatchers.IO) { service.getSpool(id) }

    suspend fun findSpoolsByCardUid(uid: String): List<SpoolResponse> = withContext(Dispatchers.IO) {
        val all = service.findSpoolsByCardUidCandidateSpools(limit = 1000, allowArchived = true)
        all.filter { it.tagUIDs.contains(uid.uppercase()) }
    }

    suspend fun updateSpoolCardUids(id: Int, uids: List<String>) = withContext(Dispatchers.IO) {
        val obj = JsonObject()
        val extra = JsonObject()
        extra.addProperty("card_uids", jsonEncodeString(uids.joinToString(",")))
        obj.add("extra", extra)
        val body = gson.toJson(obj).toRequestBody("application/json".toMediaType())
        service.updateSpool(id, body)
    }

    suspend fun findOrCreateVendor(name: String): Int = withContext(Dispatchers.IO) {
        val vendors = service.fetchVendors()
        vendors.firstOrNull { it.name?.equals(name, ignoreCase = true) == true }?.id
            ?: run {
                val obj = JsonObject().apply { addProperty("name", name) }
                service.createVendor(gson.toJson(obj).toRequestBody("application/json".toMediaType())).id
            }
    }

    suspend fun fetchFilaments(limit: Int = 1000, offset: Int = 0): List<SpoolResponse.FilamentResponse> =
        withContext(Dispatchers.IO) { service.fetchFilaments(limit, offset) }

    suspend fun createFilamentFromInfo(
        filamentName: String,
        vendorId: Int?,
        material: String?,
        colorHex: String?,
        diameter: Double,
        weight: Double?,
        nozzleTemp: Int?,
        bedTemp: Int?,
        variant: String? = null
    ): Int = withContext(Dispatchers.IO) {
        val density = material?.let { materialDensity[it] } ?: 1.24
        val filObj = JsonObject().apply {
            addProperty("name", filamentName)
            vendorId?.let { addProperty("vendor_id", it) }
            material?.let { addProperty("material", it) }
            colorHex?.let { addProperty("color_hex", it) }
            addProperty("diameter", diameter)
            weight?.let { addProperty("weight", it) }
            addProperty("density", density)
            nozzleTemp?.let { addProperty("settings_extruder_temp", it) }
            bedTemp?.let { addProperty("settings_bed_temp", it) }
            variant?.let {
                val extra = JsonObject()
                extra.addProperty("variant", jsonEncodeString(it))
                add("extra", extra)
            }
        }
        service.createFilament(
            gson.toJson(filObj).toRequestBody("application/json".toMediaType())
        ).id
    }

    suspend fun createSpoolFromInfo(
        filamentId: Int,
        cardUid: String?
    ): SpoolResponse = withContext(Dispatchers.IO) {
        val spoolObj = JsonObject().apply {
            addProperty("filament_id", filamentId)
            cardUid?.let {
                val extra = JsonObject()
                extra.addProperty("card_uids", jsonEncodeString(it))
                add("extra", extra)
            }
        }
        service.createSpool(gson.toJson(spoolObj).toRequestBody("application/json".toMediaType()))
    }

    private fun jsonEncodeString(value: String): String = gson.toJson(value)

    companion object {
        const val REQUEST_TIMEOUT_SECONDS = 3L

        private val materialDensity = mapOf(
            "PLA" to 1.24, "PETG" to 1.27, "ABS" to 1.04, "ASA" to 1.07,
            "TPU" to 1.21, "Nylon" to 1.12, "PA" to 1.12, "PC" to 1.19,
            "PVA" to 1.19, "HIPS" to 1.04, "PP" to 0.9
        )

        fun normalizeUrl(url: String): String {
            var s = url.trim()
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
            if (!s.endsWith("/")) s += "/"
            return s
        }

        data class ConnectionTestResult(
            val logs: List<String>,
            val error: String?,
            val succeeded: Boolean
        )

        suspend fun testConnection(baseUrl: String): ConnectionTestResult = withContext(Dispatchers.IO) {
            val logs = mutableListOf<String>()
            val normalized = normalizeUrl(baseUrl)
            val infoUrl = "${normalized}api/v1/info"
            logs.add("GET $infoUrl")
            val client = OkHttpClient.Builder()
                .connectTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
            try {
                val request = Request.Builder().url(infoUrl).build()
                val start = System.currentTimeMillis()
                val response = client.newCall(request).execute()
                val ms = System.currentTimeMillis() - start
                logs.add("${response.code} ${response.message} (${ms}ms)")
                if (!response.isSuccessful) {
                    return@withContext ConnectionTestResult(logs, "Server error: ${response.code}", false)
                }
                val body = response.body?.string() ?: ""
                val json = try { com.google.gson.JsonParser().parse(body) } catch (_: Exception) { null }
                val jsonObject = json?.takeIf { it.isJsonObject }?.asJsonObject
                val version = jsonObject?.get("version")
                    ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
                    ?.asString
                if (version == null) {
                    response.header("content-type")?.let { logs.add("Content-Type: $it") }
                    if (body.isBlank()) logs.add("Empty response body")
                    else logs.add("Body preview: ${body.replace(Regex("\\s+"), " ").take(160)}")
                    logs.add("✗ Missing top-level version")
                    return@withContext ConnectionTestResult(logs, "Missing top-level version", false)
                }
                var detail = "Spoolman v$version"
                jsonObject.get("db_type")
                    ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
                    ?.asString
                    ?.let { detail += " ($it)" }
                jsonObject.get("git_commit")
                    ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
                    ?.asString
                    ?.let { detail += " $it" }
                logs.add("✓ $detail")
            } catch (e: Exception) {
                logs.add("✗ ${e.message ?: "Unknown error"}")
                return@withContext ConnectionTestResult(logs, e.message, false)
            }

            val gson = Gson()
            suspend fun ensureField(entityType: String, key: String, name: String) {
                val listUrl = "${normalized}api/v1/field/$entityType"
                val createUrl = "${normalized}api/v1/field/$entityType/$key"
                logs.add("GET $listUrl")
                try {
                    val listReq = Request.Builder().url(listUrl).build()
                    val listResp = client.newCall(listReq).execute()
                    if (!listResp.isSuccessful) {
                        logs.add("⚠ could not read custom fields for $entityType")
                        return
                    }
                    val listBody = listResp.body?.string() ?: "[]"
                    val fields = try { gson.fromJson(listBody, Array<FieldResponse>::class.java).toList() }
                    catch (_: Exception) { emptyList() }

                    if (fields.any { it.key == key }) {
                        logs.add("✓ field $key exists")
                    } else {
                        logs.add("POST $createUrl")
                        val obj = JsonObject().apply {
                            addProperty("key", key)
                            addProperty("name", name)
                            addProperty("entity_type", entityType)
                            addProperty("field_type", "text")
                            addProperty("order", 1)
                            addProperty("default_value", gson.toJson(""))
                        }
                        val createReq = Request.Builder()
                            .url(createUrl)
                            .post(gson.toJson(obj).toRequestBody("application/json".toMediaType()))
                            .build()
                        val createResp = client.newCall(createReq).execute()
                        if (createResp.isSuccessful) {
                            logs.add("✓ field $key created")
                        } else {
                            val errorBody = createResp.body?.string().orEmpty()
                            logs.add("⚠ could not create field $key: $errorBody")
                        }
                    }
                } catch (e: Exception) {
                    logs.add("⚠ custom fields check failed: ${e.message ?: "Unknown error"}")
                }
            }
            ensureField("spool", "card_uids", "Card UIDs")
            ensureField("filament", "variant", "Variant")

            ConnectionTestResult(logs, null, true)
        }
    }
}
