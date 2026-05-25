package dev.pages.paxx12.spoollink.utils

import dev.pages.paxx12.spoollink.model.FilamentMetadata
import dev.pages.paxx12.spoollink.model.FilamentNameStyle
import org.junit.Assert.*
import org.junit.Test

class FilamentMetadataTest {

    @Test
    fun `filamentName brandAndSubtype style`() {
        val meta = FilamentMetadata("Bambu Lab", "PLA", "Basic", "FF0000", 1.75, 1000.0, 220, 60, null)
        assertEquals("Bambu Lab Basic", meta.filamentName(FilamentNameStyle.BRAND_AND_SUBTYPE))
    }

    @Test
    fun `filamentName returns Custom Filament when all fields empty`() {
        val meta = FilamentMetadata(null, null, null, null, null, null, null, null, null)
        assertEquals("Custom Filament", meta.filamentName(FilamentNameStyle.BRAND_AND_SUBTYPE))
    }

    @Test
    fun `colorName returns correct name for red`() {
        val meta = FilamentMetadata(null, null, null, "FF0000", null, null, null, null, null)
        assertEquals("Red", meta.colorName)
    }

    @Test
    fun `colorName returns White for ffffff`() {
        val meta = FilamentMetadata(null, null, null, "FFFFFF", null, null, null, null, null)
        assertEquals("White", meta.colorName)
    }

    @Test
    fun `colorName returns null for null colorHex`() {
        val meta = FilamentMetadata(null, null, null, null, null, null, null, null, null)
        assertNull(meta.colorName)
    }

    @Test
    fun `tagUIDs parsed from lotNr`() {
        val spool = dev.pages.paxx12.spoollink.model.SpoolResponse(
            id = 1,
            filament = dev.pages.paxx12.spoollink.model.SpoolResponse.FilamentResponse(
                1, null, null, null, null, null, null, null, null
            ),
            lotNr = "card_uid:AABBCCDD,card_uid:11223344,other",
            remainingWeight = null, archived = false, registered = null, lastUsed = null
        )
        assertEquals(listOf("AABBCCDD", "11223344"), spool.tagUIDs)
        assertEquals(2, spool.tagCount)
    }
}
