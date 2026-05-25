package dev.pages.paxx12.spoollink.model

import android.content.SharedPreferences
import com.google.gson.Gson

data class FilamentPresets(
    var brands: List<String> = emptyList(),
    var materials: List<String> = listOf("PLA", "PETG", "ABS", "ASA", "TPU", "Nylon", "PC", "PA"),
    var variants: List<String> = listOf("Basic", "Matte", "Silk", "Glossy", "Carbon Fiber"),
    var weights: List<String> = listOf("250", "500", "1000", "2000")
) {
    companion object {
        private const val KEY = "filamentPresets"

        fun load(prefs: SharedPreferences): FilamentPresets {
            val json = prefs.getString(KEY, null) ?: return FilamentPresets()
            return try {
                Gson().fromJson(json, FilamentPresets::class.java) ?: FilamentPresets()
            } catch (_: Exception) {
                FilamentPresets()
            }
        }
    }

    fun save(prefs: SharedPreferences) {
        prefs.edit().putString(KEY, Gson().toJson(this)).apply()
    }
}
