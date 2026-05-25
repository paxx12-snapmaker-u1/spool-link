# Design Guidelines

## Android (Kotlin / Jetpack Compose)

### Architecture

- MVVM: single `SpoolmanViewModel` (extends `AndroidViewModel`) owns all state as `mutableStateOf` /
  `mutableStateListOf`; `MainActivity` is a thin Compose host.
- All state mutations happen on the main thread; `SpoolmanApi` methods use `withContext(Dispatchers.IO)` internally.

### Navigation

- Four-tab `NavigationBar` at the bottom: Scan (`Icons.Default.Nfc`), Spools (`Icons.Default.FormatListBulleted`),
  History (`Icons.Default.History`), Settings (`Icons.Default.Settings`).
- No NavHost — tabs driven by `var selectedTab by mutableIntStateOf(0)` and a `when` in `MainScreen`.
- History tab: `BadgedBox` with `Badge { Text("${viewModel.scanHistory.size}") }` when non-empty.

### Screens & Sheets

- Each screen owns a `Scaffold(topBar = { TopAppBar(...) })`, except `ScanScreen` which uses a plain `Scaffold`
  (no `TopAppBar`).
- Sheets use `ModalBottomSheet`; declared outside `Scaffold` content lambda to avoid z-index issues.
- `SpoolDetailSheet` and `CreateSpoolSheet` use `AdaptiveModal` — renders as a `Dialog` on wide screens
  (≥480 dp) or `ModalBottomSheet` on phones.
- Add `@OptIn(ExperimentalMaterial3Api::class)` to any composable using `TopAppBar`, `ModalBottomSheet`,
  or `ExposedDropdownMenuBox`.

### Colors & Tints

- Scanning active / success: `Color(0xFF34C759)` (Apple-style green)
- Scanning idle: `MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)`
- Stop button: `MaterialTheme.colorScheme.error`
- Start / primary action: `MaterialTheme.colorScheme.primary`
- Destructive / error text: `MaterialTheme.colorScheme.error`
- Save (connection test passed): `Color(0xFF34C759)` container color
- Conflict warning: `Color(0xFFFF9500)` (orange)

### Buttons

- Full-width primary buttons: `Button(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))`.
- Secondary action buttons in `ScanScreen`:
  `OutlinedButton(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))`.
- Destructive content color: `ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)`.
- Spinner inline: replaces the button icon with:

  ```kotlin
  CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
  ```

### Scan Indicator

- 160 dp outer circle (fill): `Color(0xFF34C759).copy(alpha = 0.15f)` when scanning,
  `surfaceVariant.copy(alpha = 0.5f)` when idle.
- 140 dp inner `Canvas` stroke (3 dp): green or idle color.
- Scanning: `CircularProgressIndicator(size = 90 dp, color = scanColor, strokeWidth = 3 dp)`.
- Idle: `Icon(Icons.Default.Nfc, size = 52 dp)`.

### Spinners

- `CircularProgressIndicator` replaces the button content while an async operation is in progress.
- The button is disabled (`enabled = false`) during the operation.

### NFC

- `NfcAdapter.enableForegroundDispatch` in `onResume`; disabled in `onPause`.
- `onNewIntent` calls `viewModel.processNfcTag(tag, ndefMessage)`.

### Pending Assignment Overlay

When `viewModel.pendingAssignSpool != null`, `MainScreen` renders a full-screen semi-transparent overlay
(`Color.Black.copy(alpha = 0.6f)`) with a large `CircularProgressIndicator`, an instruction `Text`, and an
`OutlinedButton("Cancel")`.

### Create Spool Sheet

- `AdaptiveModal` with `fillMaxHeight()`.
- Header row: icon (`Inventory2` in `primaryContainer`), "New Spool" title + subtitle, close `IconButton`.
- Filament picker row: tappable surface showing selected filament name+ID or "Create New". Opens
  `FilamentPickerSheet` (separate `AdaptiveModal` with search + list).
- Filament section: Brand, Material, Variant — each a `SettingsPickRow` (tappable, opens `PickerSheet`).
  Color row: `SettingsColorRow` (tappable, opens `ColorPickerSheet`).
- Properties section: Diameter (`SettingsInputRow` with `TextInputDialog`), Weight (`SettingsPickRow`),
  Nozzle temp, Bed temp.
- Tag section: read-only Card UID row (always shown).
- Footer: `HorizontalDivider` + row with `OutlinedButton("Cancel")` and `Button("Create Spool")` (equal
  weight, spinner while creating).
- `ColorPickerSheet`: HSV saturation/brightness `Canvas` (200 dp tall), hue `Slider`, hex `OutlinedTextField`,
  preset color grid (9 columns, 30 dp circles).

### Spools Screen

- Grouped `LazyColumn` with `stickyHeader` section labels.
- Sort options (same 6 as iOS) stored in `SharedPreferences("spoolman_prefs")` keys `spools_sort_by` /
  `spools_sort_ascending`.
- Sort menu: `DropdownMenu` with up/down arrow icon on active sort.
- 44 dp `ColorSwatch` per row, `cornerRadius = 10 dp`; material capsule, `#id`, remaining weight, `TagCountBadge`.
- Conflict indicator: `Icons.Default.Warning` in orange.
- `SpoolDetailSheet` via `AdaptiveModal`.

### Spool Detail Sheet

- Header: 64 dp `ColorSwatch`, name (`titleMedium`/`SemiBold`), material capsule + `TagCountBadge`.
- Stats `Surface(tonalElevation = 2)` with `HorizontalDivider` rows: ID, Remaining, Color (18 dp swatch +
  `#HEX` monospaced), Diameter, Filament weight, Nozzle, Bed, Added, Last Used.
- Assigned Tags `Surface`: header row with "Remove All" `TextButton`; UID rows with `Icons.Default.Nfc` +
  monospaced text + optional warning icon; conflict explanation row.
- Tip text in `onSurfaceVariant`.
- Action buttons: `Button("Assign NFC Tag")` (spinner while assigning), `OutlinedButton("Open in Spoolman")`.

### Settings Screen

- `LazyColumn` with `SectionHeader` labels (uppercase `labelSmall`).
- Spoolman Server: `Surface(tonalElevation=2)` containing `OutlinedTextField`, `Button("Test Connection")`
  (spinner while testing), inline logs, `Button("Save")` (green, only when passed).
- Current Configuration: saved URL row.
- Spool Creation: filament name style `DropdownMenu` row.
- Filament Presets: Brands, Materials, Variants, Weights — each a chevron row opening a `PresetEditorSheet`
  (`ModalBottomSheet`).
- `PresetEditorSheet`: title + "Done" row, preset list with delete icons, `OutlinedTextField` + add button,
  "From Spoolman" suggestions section.
- Toast: `Surface(inverseSurface)` overlay at `Alignment.BottomCenter` with `CheckCircle` icon + "URL saved" text.

### Tag Detail Table (`TagDetailView` in `SharedComponents.kt`)

- `Surface(tonalElevation = 2, shape = RoundedCornerShape(12))`.
- Format row always first; then `payload.fields` with `HorizontalDivider(start = 16 dp)` between rows;
  color fields render a 20 dp swatch + monospaced value; Card UID last.
- Label column: `Modifier.width(72.dp)`, `bodySmall`/`onSurfaceVariant`.

### Spool Info Row (`SpoolInfoRow` in `SharedComponents.kt`)

- `Surface(tonalElevation = 2, shape = RoundedCornerShape(12))`.
- 36 dp `ColorSwatch`, spool name (`bodyMedium`/`Medium`), material capsule (`CircleShape`), remaining weight,
  `TagCountBadge`.

### Shared Components (`SharedComponents.kt`)

- `ColorSwatch(hex, size, cornerRadius)`: filled `RoundedCornerShape`; shows `BrokenImage` icon when hex is
  null or unparseable.
- `TagCountBadge(count)`: `CircleShape` surface; `primaryContainer` when `count > 0`, `surfaceVariant` otherwise.
- `AdaptiveModal`: `Dialog` at ≥480 dp (82% width, max 640 dp, `cornerRadius = 28`), else
  `ModalBottomSheet(fillMaxHeight = 0.92f)`.
- `parseHexColor(hex)`: returns `Color?` from 6-char hex string.
- `parseIso8601(s)`: tries `yyyy-MM-dd'T'HH:mm:ss.SSSSSS`, `yyyy-MM-dd'T'HH:mm:ss`, `yyyy-MM-dd`.
- `formatDisplayDate(s)`: returns `"MMM d, yyyy"` string or null.

### URL Handling

- URLs stored in `SharedPreferences("spoolman_prefs")` under key `"base_url"`.
- Normalised: always ends with `/`, always has `http://` prefix.
