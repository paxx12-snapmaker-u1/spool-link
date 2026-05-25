# Android App — Agent Guidelines

## Architecture

MVVM with Jetpack Compose. One `SpoolmanViewModel` (extends `AndroidViewModel`) holds all state.
`MainActivity` is a thin Compose host that wires NFC foreground dispatch.

```
MainActivity          → setContent { SpoolReaderTheme { MainScreen(vm) } }
                      → onResume/onPause: enable/disable NFC foreground dispatch
                      → onNewIntent: calls vm.processNfcTag(tag, ndefMessage)

SpoolmanViewModel     → all business logic, all mutable state (mutableStateOf / mutableStateListOf)
SpoolmanApi           → network layer (Retrofit + OkHttp), always withContext(Dispatchers.IO)
TagFormatParser       → NDEF → NFCTagPayload (formats/ package)
```

## Key Packages

| Package | Contents |
|---------|----------|
| `model/` | `SpoolResponse`, `TagPayload` (interface + RawNDEF), `FilamentMetadata`, `FilamentPresets`, `ScanResult` |
| `formats/` | `OpenSpoolFormat.kt` — `TagFormatParser` object, `OpenSpoolTagPayload` |
| `api/` | `SpoolmanService` (Retrofit interface), `SpoolmanApi` (wraps Retrofit, exposes suspend funs) |
| `viewmodel/` | `SpoolmanViewModel` — single ViewModel for entire app |
| `ui/` | Compose screens and sheets |
| `ui/components/` | `SharedComponents.kt` — `ColorSwatch`, `TagCountBadge`, `TagDetailView`, `SpoolInfoRow`, `AdaptiveModal`, `parseHexColor`, `parseIso8601`, `formatDisplayDate` |
| `ui/theme/` | `Theme.kt` — `SpoolReaderTheme` using dynamic color |

## UI Structure

`MainScreen` → `Column` with a `NavigationBar` at the bottom + 4 screens filling `weight(1f)`:

- `ScanScreen` — plain `Scaffold` (no `TopAppBar`), NFC scan button, tag result + action sheets
- `SpoolsScreen` — `Scaffold(TopAppBar)`, sortable grouped spool list, `SpoolDetailSheet`
- `HistoryScreen` — `Scaffold(TopAppBar)`, scan history list
- `SettingsScreen` — `Scaffold(TopAppBar)`, URL test, name style picker, preset editor sheets

Bottom sheets use `ModalBottomSheet`. `SpoolDetailSheet` and `CreateSpoolSheet` use `AdaptiveModal`
(renders as `Dialog` on wide screens, `ModalBottomSheet` on phones). No NavHost — tabs use a single
`var selectedTab by mutableIntStateOf(0)`.

## NFC Handling

- `MainActivity` always enables foreground dispatch in `onResume()`, disables in `onPause()`.
- `onNewIntent` extracts `Tag` + `NdefMessage?` and calls `viewModel.processNfcTag(tag, ndefMessage)`.
- The ViewModel ignores the call if `!isScanning && pendingAssignSpool == null`.
- `isScanning = true` after "Start Scanning" tapped; set to `false` once a tag is read or user stops.
- `pendingAssignSpool != null` signals "assign NFC tag to this spool" flow — bypasses normal scan.
- `MainScreen` renders a full-screen overlay while `pendingAssignSpool != null`.

## ViewModel State

| Field | Type | Purpose |
|-------|------|---------|
| `spools` | `mutableStateListOf<SpoolResponse>` | Loaded spool list |
| `isFetchingSpools` | `Boolean` | Spool fetch in progress |
| `hasMoreSpools` | `Boolean` | Pagination — more pages available |
| `spoolsError` | `String?` | Last spool fetch error |
| `scanHistory` | `mutableStateListOf<ScanResult>` | All scan results this session |
| `lastResult` | `ScanResult?` | Most recent scan |
| `isScanning` | `Boolean` | NFC foreground scan active |
| `statusMessage` | `String` | Scan screen status text |
| `pendingAssignSpool` | `SpoolResponse?` | Spool awaiting NFC tag assignment |
| `isCreatingSpool` | `Boolean` | Spool creation in progress |
| `availableFilaments` | `mutableStateListOf<SpoolResponse.FilamentResponse>` | Loaded filament list for picker |
| `isLoadingFilaments` | `Boolean` | Filament fetch in progress |
| `filamentsErrorMessage` | `String?` | Last filament fetch error |
| `filamentPresets` | `FilamentPresets` | User-defined brand/material/variant/weight presets |

Key ViewModel methods: `ensureSpoolsLoaded()`, `fetchSpools(reset)`, `loadMoreSpools()`, `processNfcTag()`,
`processAssignment()`, `startTagAssignment(spool)`, `cancelTagAssignment()`, `removeTag(uidHex, spool)`,
`removeAllTags(spool)`, `createSpoolFromTag()`, `loadFilaments()`, `loadFilamentsIfNeeded()`, `updatePresets(presets)`,
`savedBaseUrl()`, `updateBaseUrl(url)`, `savedNameStyle()`, `saveNameStyle(style)`.

## API Paths

All Spoolman endpoints are under `api/v1/`:

- `GET api/v1/spool?limit=&offset=` — paginated spool list
- `GET api/v1/spool/{id}` — single spool
- `PATCH api/v1/spool/{id}` — update `extra.card_uids`
- `GET api/v1/filament?limit=&offset=` — paginated filament list
- `POST api/v1/filament` — create filament
- `GET api/v1/vendor` / `POST api/v1/vendor` — vendor lookup/create
- `POST api/v1/spool` — create spool
- `GET api/v1/info` — server info (connection test)
- `GET api/v1/field/spool` / `POST api/v1/field/spool/card_uids` — ensure custom field exists

`SpoolmanApi.updateSpoolCardUids(id, uids)` patches `extra.card_uids` with a JSON-encoded comma-separated string.
Call `ensureField("spool", "card_uids", ...)` before the first write.

## card_uids Format

Tag UIDs are stored in the spool's `extra.card_uids` custom field as a JSON-encoded comma-separated string
of uppercase hex UIDs:

```
"AABBCCDD,11223344"
```

`SpoolResponse.tagUIDs` parses out all entries. `tagCount` is `tagUIDs.size`. The field value itself is
JSON-encoded (double-serialized string) as required by Spoolman's custom field API.

## State in ViewModel

All UI state is `var foo by mutableStateOf(...)` or `val list = mutableStateListOf<...>()`. These are
Compose snapshot-aware and must only be mutated on the main thread. All ViewModel suspend functions
run on `viewModelScope` (Main dispatcher); `SpoolmanApi` methods use `withContext(Dispatchers.IO)` internally.

Visibility pattern: expose as `private set` where the UI only reads, no setter on `mutableStateListOf`
(already a reference type).

## Preferences

`SharedPreferences("spoolman_prefs", MODE_PRIVATE)` stores:

- `base_url` — Spoolman server URL (normalized: always ends with `/`, has `http://` prefix)
- `filamentNameStyle` — `FilamentNameStyle.name` enum name
- `filamentPresets` — JSON-encoded `FilamentPresets`

The ViewModel accesses preferences via `application.getSharedPreferences(...)` since it extends `AndroidViewModel`.

## Compose Conventions

- Add `@OptIn(ExperimentalMaterial3Api::class)` to any composable using `TopAppBar`, `ModalBottomSheet`,
  or `ExposedDropdownMenuBox`.
- `ModalBottomSheet` must be declared **outside** a `Scaffold` content lambda to avoid z-index issues;
  place them at the call-site composable root level (after the `Scaffold { }` block).
- For grouped/sectioned `LazyColumn` lists, use `stickyHeader { }` (requires
  `import androidx.compose.foundation.lazy.*`).

## Adding a New Feature

1. If it needs network: add a method to `SpoolmanService` and a wrapper in `SpoolmanApi`
   (with `withContext(Dispatchers.IO)`).
2. If it needs state: add `var`/`val` to `SpoolmanViewModel` and a function that uses `viewModelScope.launch { }`.
3. If it's a new screen: add a `Composable` with its own `Scaffold(TopAppBar)`, wire it into
   `MainScreen`'s `when(selectedTab)` and add a `NavigationBarItem`.
4. If it's a sheet: use `AdaptiveModal` for detail/create sheets; use `ModalBottomSheet` directly for
   simpler picker sheets. Declare outside the `Scaffold` block.
