# Spoolman API Protocol

This document describes how the app maps NFC tag data to Spoolman API fields and defines the wire format for each field.

## Base URL

All endpoints are relative to the user-configured base URL, normalized to always end with `/` and prefixed
with `http://` if no scheme is present.

```
http://<host>:<port>/
```

Default: `http://spoolman.local:7912/`

---

## Endpoints Used

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `api/v1/info` | Server version check (connection test) |
| `GET` | `api/v1/spool?limit=&offset=` | Paginated spool list |
| `GET` | `api/v1/spool/{id}` | Single spool by ID |
| `GET` | `api/v1/spool?limit=1000&allow_archived=true` | Candidate spools for UID search (client-side filtered) |
| `PATCH` | `api/v1/spool/{id}` | Update spool extra data (card UIDs) |
| `POST` | `api/v1/spool` | Create new spool |
| `GET` | `api/v1/filament?limit=&offset=` | Paginated filament list |
| `POST` | `api/v1/filament` | Create new filament |
| `GET` | `api/v1/vendor` | List all vendors |
| `POST` | `api/v1/vendor` | Create new vendor |
| `GET` | `api/v1/field/spool` | List custom spool fields |
| `POST` | `api/v1/field/spool/card_uids` | Create `card_uids` custom field if missing |
| `GET` | `api/v1/field/filament` | List custom filament fields |
| `POST` | `api/v1/field/filament/variant` | Create `variant` custom field if missing |

---

## Spool Object (`SpoolResponse`)

```json
{
  "id": 42,
  "filament": { ... },
  "remaining_weight": 213.5,
  "archived": false,
  "registered": "2024-01-15T10:30:00",
  "last_used": "2024-03-01T08:00:00",
  "extra": {
    "card_uids": "\"AABBCCDD,11223344\""
  }
}
```

| Field | Type | Notes |
|-------|------|-------|
| `id` | `integer` | Spoolman-assigned spool ID |
| `filament` | object | Nested filament object (see below) |
| `remaining_weight` | `float \| null` | Grams of filament remaining |
| `archived` | `boolean` | Archived spools are excluded from the active spool list |
| `registered` | ISO 8601 string \| null | Creation timestamp |
| `last_used` | ISO 8601 string \| null | Last use timestamp |
| `extra` | object \| null | Custom fields map; `card_uids` key stores NFC tag UIDs |

### Filament sub-object

```json
{
  "id": 7,
  "name": "Galaxy Black",
  "vendor": { "id": 3, "name": "Bambu" },
  "material": "PLA",
  "color_hex": "1A1A2E",
  "diameter": 1.75,
  "weight": 1000.0,
  "settings_extruder_temp": 220,
  "settings_bed_temp": 65
}
```

| Field | Type | Notes |
|-------|------|-------|
| `id` | `integer` | Filament ID |
| `name` | `string \| null` | Filament product name |
| `vendor` | object \| null | `{ id, name }` |
| `material` | `string \| null` | e.g. `"PLA"`, `"PETG"`, `"ABS"` |
| `color_hex` | `string \| null` | 6-char hex without `#`, e.g. `"FF5733"` |
| `diameter` | `float \| null` | mm, typically `1.75` or `2.85` |
| `weight` | `float \| null` | Spool weight in grams |
| `settings_extruder_temp` | `integer \| null` | Recommended nozzle temperature (°C) |
| `settings_bed_temp` | `integer \| null` | Recommended bed temperature (°C) |

---

## `card_uids` Custom Field

NFC tag UIDs are stored in a Spoolman custom field named `card_uids` on the spool entity.

### Field definition (created on first connection test)

```json
{
  "key": "card_uids",
  "name": "Card UIDs",
  "entity_type": "spool",
  "field_type": "text",
  "order": 1,
  "default_value": "\"\""
}
```

### Wire format

The `extra.card_uids` value in the Spoolman API is a **JSON-encoded string** — i.e., the string value itself
is wrapped in JSON quotes by the client before sending, so it arrives as a JSON string containing another JSON string:

```
extra.card_uids = "\"AABBCCDD,11223344\""
```

When decoded, the inner string is a comma-separated list of uppercase hex UIDs:

```
AABBCCDD,11223344
```

### Encoding rule

Before writing, the comma-separated UID string must be JSON-encoded:

```
jsonEncode("AABBCCDD,11223344") → "\"AABBCCDD,11223344\""
```

Android (`SpoolmanApi.jsonEncodeString`):
```kotlin
private fun jsonEncodeString(value: String): String = gson.toJson(value)
```

iOS (`SpoolmanAPI.jsonEncodeString`):
```swift
private static func jsonEncodeString(_ s: String) -> String {
    guard let data = try? JSONEncoder().encode(s) else { return "\"\(s)\"" }
    return String(data: data, encoding: .utf8) ?? "\"\(s)\""
}
```

### Decoding rule

On read, strip the outer JSON quotes if present (both apps handle both encoded and raw forms):

```kotlin
val decoded = if (raw.startsWith('"') && raw.endsWith('"'))
    raw.drop(1).dropLast(1) else raw
return decoded.split(",").map { it.trim() }.filter { it.isNotEmpty() }
```

### PATCH body for `card_uids`

```json
{
  "extra": {
    "card_uids": "\"AABBCCDD,11223344\""
  }
}
```

---

## UID Format

NFC tag UIDs are formatted as **uppercase hex with no separators**:

```
AABBCCDD        (4-byte / 7-byte UID)
04A1B2C3D4E5F6  (7-byte UID)
```

Derived from the raw tag byte array:
```kotlin
tag.id.joinToString("") { "%02X".format(it) }
```

---

## Sync Logic ("Add to current, remove from others")

When an NFC tag is scanned or assigned to a spool:

1. **Fetch** the target spool via `GET api/v1/spool/{id}`.
2. **Append** the tag UID to `extra.card_uids` if not already present.
3. **Write** the updated UID list via `PATCH api/v1/spool/{id}`.
4. **Search** for other spools that contain the same UID by fetching all spools
   (`limit=1000&allow_archived=true`) and filtering client-side on `tagUIDs`.
5. **Remove** the UID from each other spool's `card_uids` and write back via `PATCH`.

---

## `variant` Custom Field (Filament)

The filament variant (subtype, e.g. "Silk", "Matte") is stored in a Spoolman custom field named `variant`
on the filament entity.

### Field definition (created on first connection test)

```json
{
  "key": "variant",
  "name": "Variant",
  "entity_type": "filament",
  "field_type": "text",
  "order": 1,
  "default_value": "\"\""
}
```

The value follows the same JSON-encoded string convention as `card_uids`:

```
extra.variant = "\"Silk\""
```

### Create Filament body with variant

```json
{
  "name": "Galaxy Black Silk",
  "material": "PLA",
  "extra": {
    "variant": "\"Silk\""
  }
}
```

`extra.variant` is omitted when the variant is empty or not known.

---

## Create Filament (`POST api/v1/filament`)

```json
{
  "name": "Galaxy Black",
  "vendor_id": 3,
  "material": "PLA",
  "color_hex": "1A1A2E",
  "diameter": 1.75,
  "weight": 1000.0,
  "density": 1.24,
  "settings_extruder_temp": 220,
  "settings_bed_temp": 65
}
```

`density` is derived from `material` using a lookup table; defaults to `1.24` (PLA) when unknown:

| Material | Density (g/cm³) |
|----------|----------------|
| PLA | 1.24 |
| PETG | 1.27 |
| ABS | 1.04 |
| ASA | 1.07 |
| TPU | 1.21 |
| Nylon / PA | 1.12 |
| PC | 1.19 |
| PVA | 1.19 |
| HIPS | 1.04 |
| PP | 0.90 |

Response: `{ "id": <integer> }`

---

## Create Spool (`POST api/v1/spool`)

```json
{
  "filament_id": 7,
  "extra": {
    "card_uids": "\"AABBCCDD\""
  }
}
```

`extra.card_uids` is omitted when no NFC tag UID is available at creation time.

Response: full `SpoolResponse` object.

---

## Create Vendor (`POST api/v1/vendor`)

```json
{ "name": "Bambu" }
```

Response: `{ "id": <integer>, "name": "Bambu" }`

Vendor lookup is case-insensitive. If a vendor with the same name already exists, its ID is reused.

---

## OpenSpool NFC Tag Format

Tags encoded in the OpenSpool format carry an NDEF record with:
- TNF: `MIME_MEDIA`
- Type: `application/json`

### JSON payload fields

| Field | Type | Notes |
|-------|------|-------|
| `protocol` | `string` | Must be `"openspool"` |
| `version` | `string` | e.g. `"1.0"` |
| `type` | `string` | Filament material class, e.g. `"pla"`, `"petg"` |
| `color_hex` | `string \| null` | 6-char hex without `#` |
| `brand` | `string \| null` | Manufacturer/brand name |
| `subtype` | `string \| null` | Material subtype, e.g. `"Silk"`, `"Matte"` |
| `min_temp` | `string \| null` | Minimum nozzle temperature (°C) as a string |
| `max_temp` | `string \| null` | Maximum nozzle temperature (°C) as a string |
| `bed_min_temp` | `string \| null` | Minimum bed temperature (°C) as a string |
| `bed_max_temp` | `string \| null` | Maximum bed temperature (°C) as a string |
| `alpha` | `string \| null` | Transparency/opacity hint |
| `weight` | `float \| null` | Spool weight in grams |
| `diameter` | `float \| null` | Filament diameter in mm |
| `spool_id` | `integer \| null` | Spoolman spool ID to link this tag to |

Temperature fields are strings (not integers) to match the OpenSpool spec. The app uses `max_temp` /
`bed_max_temp` as the single representative temperature when creating a filament.

### Example

```json
{
  "protocol": "openspool",
  "version": "1.0",
  "type": "pla",
  "color_hex": "FF5733",
  "brand": "Bambu",
  "subtype": "Silk",
  "min_temp": "190",
  "max_temp": "230",
  "bed_min_temp": "35",
  "bed_max_temp": "65",
  "weight": 1000.0,
  "diameter": 1.75,
  "spool_id": 42
}
```
