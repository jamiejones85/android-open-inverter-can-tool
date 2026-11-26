package app.oic.ui

import android.net.Uri
import app.oic.GhReleaseAsset
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.oic.MainViewModel
import app.oic.Ui
import app.oic.oi.*

@Composable
fun OicTheme(content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(primary = Color(0xFF5EEAD4), onPrimary = Color(0xFF00201C))
    else lightColorScheme(primary = Color(0xFF0F766E))
    MaterialTheme(colorScheme = colors, content = content)
}

private class TabDef(val label: String, val icon: ImageVector)

private val TABS = listOf(
    TabDef("Dashboard", Icons.Filled.Info),
    TabDef("Device", Icons.Filled.Home),
    TabDef("Params", Icons.Filled.Settings),
    TabDef("Live", Icons.Filled.PlayArrow),
    TabDef("Tools", Icons.Filled.Build)
)

private fun LazyListScope.col(key: Any? = null, content: @Composable ColumnScope.() -> Unit) {
    item(key = key) { Column(verticalArrangement = Arrangement.spacedBy(6.dp), content = content) }
}

@Composable
fun OicApp(vm: MainViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.events.collect { snack.showSnackbar(it) } }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                TABS.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = null) }, label = { Text(t.label) }
                    )
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (tab) {
                0 -> DashboardScreen(ui, vm)
                1 -> DeviceScreen(ui, vm)
                2 -> ParamsScreen(ui, vm)
                3 -> LiveScreen(ui, vm)
                else -> ToolsScreen(ui, vm)
            }
        }
    }
}

@Composable
private fun Confirm(text: String, onYes: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onYes(); onDismiss() }) { Text("Send") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun NeedDb(ui: Ui, vm: MainViewModel, content: @Composable () -> Unit) {
    if (ui.db == null) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("No parameter database loaded.", style = MaterialTheme.typography.titleMedium)
            Text("Download it from the connected device (needs stm32-sine 5.27.R or later), or pick a JSON file.")
            val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? -> u?.let { vm.loadDbFile(it) } }
            Button(onClick = { vm.loadDb() }, enabled = ui.connected) { Text("Download from device") }
            OutlinedButton(onClick = { pick.launch(arrayOf("application/json", "text/*", "*/*")) }) { Text("Open database file") }
        }
    } else content()
}

// ---------------------------------------------------------------- Device
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceScreen(ui: Ui, vm: MainViewModel) {
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    var mode by remember { mutableStateOf("Normal") }
    var nodeText by remember(ui.nodeId) { mutableStateOf(ui.nodeId.toString()) }
    val hasDonated by vm.hasDonated.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val dbFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? -> u?.let { vm.loadDbFile(it) } }

    confirm?.let { (msg, act) -> Confirm(msg, act) { confirm = null } }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!hasDonated) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Support Development", style = MaterialTheme.typography.titleMedium)
                        }
                        Text("Enjoying OI Can Tool? Donate £5.00 to support ongoing development and remove this banner.", style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = { vm.donate(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Donate £5.00 & Remove Banner")
                        }
                    }
                }
            }
        }
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("CAN Adapter", style = MaterialTheme.typography.titleMedium)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = CircleShape,
                            color = if (ui.connected) Color(0xFF10B981) else Color(0xFF6B7280),
                            modifier = Modifier.size(10.dp)
                        ) {}
                        Text(ui.status, style = MaterialTheme.typography.bodyMedium)
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MainViewModel.BITRATES.forEach { (code, label) ->
                            FilterChip(
                                selected = ui.bitrate == code,
                                onClick = { vm.setBitrate(code) },
                                label = { Text(label) },
                                enabled = !ui.connected
                            )
                        }
                    }

                    Button(
                        onClick = { if (ui.connected) vm.disconnect() else vm.connect() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (ui.connected) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                    ) {
                        Text(if (ui.connected) "Disconnect Adapter" else "Connect Adapter")
                    }
                }
            }
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Node & Discovery", style = MaterialTheme.typography.titleMedium)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = nodeText,
                            onValueChange = { nodeText = it.filter(Char::isDigit).take(3) },
                            label = { Text("Node ID (1-127)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { nodeText.toIntOrNull()?.let(vm::setNode) },
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) { Text("Use") }
                        Button(
                            onClick = vm::scan,
                            enabled = ui.connected && !ui.scanning,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Text(if (ui.scanning) "Scanning…" else "Scan")
                        }
                    }

                    if (ui.nodes.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Responding Nodes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                ui.nodes.forEach { n ->
                                    FilterChip(
                                        selected = n == ui.nodeId,
                                        onClick = { vm.setNode(n) },
                                        label = { Text("$n") }
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm::readSerial, enabled = ui.connected, modifier = Modifier.weight(1f)) {
                            Text("Serial")
                        }
                        OutlinedButton(onClick = { vm.loadDb(forceReload = true) }, enabled = ui.connected, modifier = Modifier.weight(1f)) {
                            Text("Reload DB")
                        }
                        OutlinedButton(onClick = { dbFile.launch(arrayOf("application/json", "text/*", "*/*")) }, enabled = ui.connected, modifier = Modifier.weight(1f)) {
                            Text("From File")
                        }
                    }
                    ui.serial?.let { Text("Serial: $it", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                    Text("Database: ${ui.dbSource}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Parameter Commands", style = MaterialTheme.typography.titleMedium)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Text("Changes live in RAM until saved to flash.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { confirm = "Save all parameters to flash on node ${ui.nodeId}?" to { vm.command("save") } },
                            enabled = ui.connected,
                            modifier = Modifier.weight(1f)
                        ) { Text("Save to Flash") }
                        OutlinedButton(
                            onClick = { confirm = "Reload parameters from flash? Unsaved changes are lost." to { vm.command("load") } },
                            enabled = ui.connected,
                            modifier = Modifier.weight(1f)
                        ) { Text("Reload") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { confirm = "Reset all parameters to factory defaults?" to { vm.command("defaults") } },
                            enabled = ui.connected,
                            modifier = Modifier.weight(1f)
                        ) { Text("Defaults") }
                        OutlinedButton(
                            onClick = { confirm = "Reboot node ${ui.nodeId}?" to { vm.command("reset") } },
                            enabled = ui.connected,
                            modifier = Modifier.weight(1f)
                        ) { Text("Reboot") }
                    }
                }
            }
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Run Control", style = MaterialTheme.typography.titleMedium)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Start Mode", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Oi.START_MODES.keys.forEach { m ->
                                FilterChip(
                                    selected = mode == m,
                                    onClick = { mode = m },
                                    label = { Text(m) }
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                confirm = "START node ${ui.nodeId} in $mode mode? This can spin the motor. Make sure the drivetrain is safe." to
                                    { vm.command("start", Oi.START_MODES.getValue(mode)) }
                            },
                            enabled = ui.connected,
                            modifier = Modifier.weight(1f)
                        ) { Text("Start") }
                        Button(
                            onClick = { vm.command("stop") },
                            enabled = ui.connected,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) { Text("Stop") }
                    }
                }
            }
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Error Log", style = MaterialTheme.typography.titleMedium)
                        }
                        OutlinedButton(onClick = vm::readErrors, enabled = ui.connected) { Text("Read") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    ui.errors?.let { errs ->
                        if (errs.isEmpty()) {
                            Text("No errors recorded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                errs.forEach { err ->
                                    Text(err, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            var showReleasesDialog by remember { mutableStateOf(false) }
            val fwPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
                u?.let { vm.upgradeFirmware(it) }
            }
            if (showReleasesDialog) {
                GithubReleasesDialog(
                    vm = vm,
                    onDismiss = { showReleasesDialog = false },
                    onSelectAsset = { asset -> vm.downloadAndUpgradeRelease(asset) }
                )
            }
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Firmware Upgrade", style = MaterialTheme.typography.titleMedium)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Text("Flash binary firmware (.bin) via OpenInverter CAN bootloader protocol.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (ui.upgrading) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinearProgressIndicator(progress = { ui.upgradeProgress / 100f }, modifier = Modifier.fillMaxWidth())
                            Text(ui.upgradeStatus, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { fwPicker.launch(arrayOf("application/octet-stream", "*/*")) },
                                enabled = ui.connected,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Local .bin")
                            }
                            Button(
                                onClick = { showReleasesDialog = true },
                                enabled = ui.connected,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Online Releases")
                            }
                            OutlinedButton(
                                onClick = { vm.command("reset") },
                                enabled = ui.connected,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Reboot")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Params
@Composable
private fun ParamsScreen(ui: Ui, vm: MainViewModel) = NeedDb(ui, vm) {
    val db = ui.db!!
    var subTab by rememberSaveable { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<OiParam?>(null) }

    val items = remember(db, query, subTab) {
        db.params.filter { (if (subTab == 1) !it.isParam else it.isParam) && it.name.contains(query, ignoreCase = true) }
    }

    editing?.let { p -> EditDialog(p, ui.values[p.name], onWrite = { vm.write(p.name, it) }, onRead = { vm.readOne(p.name) }) { editing = null } }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TabRow(selectedTabIndex = subTab) {
                Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("Parameters") })
                Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("Spot Values") })
                Tab(selected = subTab == 2, onClick = { subTab = 2 }, text = { Text("CAN Map") })
            }

            if (subTab != 2) {
                OutlinedTextField(query, { query = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(if (subTab == 0) "${items.size} Parameters" else "${items.size} Spot Values", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    OutlinedButton(onClick = { vm.readAll(subTab == 0) }, enabled = ui.connected) { Text("Read All") }
                }
            }
        }

        if (subTab == 2) {
            MapScreenContent(ui, vm)
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.name }) { p ->
                    val v = ui.values[p.name]
                    Column {
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = { Text(listOfNotNull(p.category, if (p.enums.isEmpty() && p.bits.isEmpty()) p.unit else null).filter { it.isNotBlank() }.joinToString(" · ")) },
                            trailingContent = { Text(if (v == null) "–" else formatValue(p, v), fontFamily = FontFamily.Monospace) },
                            modifier = Modifier.clickable(enabled = p.isParam) { editing = p }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditDialog(p: OiParam, current: Double?, onWrite: (String) -> Unit, onRead: () -> Unit, onClose: () -> Unit) {
    var text by remember(p, current) { 
        mutableStateOf(current?.let { formatValue(p, it, symbolic = p.enums.isNotEmpty()) } ?: (p.enums.values.firstOrNull() ?: "")) 
    }
    
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(p.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                current?.let { Text("Device value: ${formatValue(p, it)}") }
                
                if (p.enums.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Value") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            p.enums.forEach { (_, enumName) ->
                                DropdownMenuItem(
                                    text = { Text(enumName) },
                                    onClick = {
                                        text = enumName
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    if (p.bits.isNotEmpty()) {
                        Text(p.bits.entries.joinToString("\n") { "${it.key} = ${it.value}" } + "\nCombine names with commas.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        val range = if (p.min != null && p.max != null) "${fixedToFloat(p.min).fmt()} to ${fixedToFloat(p.max).fmt()}" else ""
                        Text(listOf(p.unit, range).filter { it.isNotBlank() }.joinToString("   "), style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(text, { text = it }, label = { Text("New value") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = { TextButton(onClick = { onWrite(text); onClose() }) { Text("Write") } },
        dismissButton = {
            Row {
                TextButton(onClick = onRead) { Text("Read") }
                TextButton(onClick = onClose) { Text("Close") }
            }
        }
    )
}

// ---------------------------------------------------------------- Live + logging
@Composable
private fun LiveScreen(ui: Ui, vm: MainViewModel) = NeedDb(ui, vm) {
    val db = ui.db!!
    var query by remember { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(setOf<String>()) }
    var stepText by rememberSaveable { mutableStateOf("1.0") }
    var showGraph by remember { mutableStateOf(false) }
    val step = ((stepText.toDoubleOrNull() ?: 1.0).coerceIn(0.1, 60.0) * 1000).toLong()
    val logPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { u: Uri? ->
        if (u != null) vm.startPolling(selected.toList(), step, u)
    }
    val svgPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/svg+xml")) { u: Uri? ->
        if (u != null) vm.saveGraphSvg(u)
    }
    val items = remember(db, query) { db.params.filter { it.name.contains(query, ignoreCase = true) } }

    if (showGraph) {
        LiveGraphDialog(vm, onDismiss = { showGraph = false }, onSaveSvg = { svgPicker.launch("can-graph.svg") })
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(stepText, { stepText = it }, label = { Text("Interval s") }, singleLine = true, modifier = Modifier.width(110.dp))
                if (ui.polling) {
                    Button(onClick = vm::stopPolling) { Text(if (ui.logging) "Stop (${ui.logRows} rows)" else "Stop") }
                } else {
                    Button(onClick = { vm.startPolling(selected.toList(), step, null) }, enabled = ui.connected && selected.isNotEmpty()) { Text("Poll") }
                    OutlinedButton(onClick = { logPicker.launch("oic-log.csv") }, enabled = ui.connected && selected.isNotEmpty()) { Text("Log CSV") }
                    OutlinedButton(onClick = { showGraph = true }, enabled = selected.isNotEmpty()) { Text("Graph") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { selected = db.params.filter { !it.isParam }.map { it.name }.toSet() }) { Text("All spot values") }
                TextButton(onClick = { selected = emptySet() }) { Text("Clear") }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(items, key = { it.name }) { p ->
                val v = ui.values[p.name]
                Column {
                ListItem(
                    leadingContent = {
                        Checkbox(checked = p.name in selected,
                            onCheckedChange = { selected = if (it) selected + p.name else selected - p.name })
                    },
                    headlineContent = { Text(p.name) },
                    supportingContent = { Text(if (p.isParam) "parameter" else "spot value") },
                    trailingContent = { Text(if (v == null) "–" else formatValue(p, v), fontFamily = FontFamily.Monospace) }
                )
                HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveGraphDialog(vm: MainViewModel, onDismiss: () -> Unit, onSaveSvg: () -> Unit) {
    val history by vm.graphHistory.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Live Parameter Graph") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f)) {
                if (history.isEmpty() || history.values.all { it.size < 1 }) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("Start polling on Live tab to record graph data...", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    val colors = listOf(Color(0xFF5EEAD4), Color(0xFF38BDF8), Color(0xFFF43F5E), Color(0xFFFBBF24), Color(0xFFA855F7), Color(0xFF34D399))
                    Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height

                        var minX = Double.MAX_VALUE
                        var maxX = Double.MIN_VALUE
                        var minY = Double.MAX_VALUE
                        var maxY = Double.MIN_VALUE

                        for ((_, points) in history) {
                            for ((x, y) in points) {
                                if (x < minX) minX = x
                                if (x > maxX) maxX = x
                                if (y < minY) minY = y
                                if (y > maxY) maxY = y
                            }
                        }
                        if (minX == maxX) maxX = minX + 1.0
                        if (minY == maxY) { minY -= 1.0; maxY += 1.0 }

                        val gridColor = Color.DarkGray.copy(alpha = 0.3f)
                        for (i in 0..4) {
                            val gy = (canvasHeight / 4f) * i
                            drawLine(color = gridColor, start = androidx.compose.ui.geometry.Offset(0f, gy), end = androidx.compose.ui.geometry.Offset(canvasWidth, gy), strokeWidth = 1f)
                        }

                        var colorIdx = 0
                        for ((_, points) in history) {
                            if (points.isEmpty()) continue
                            val color = colors[colorIdx % colors.size]
                            colorIdx++

                            val path = androidx.compose.ui.graphics.Path()
                            points.forEachIndexed { index, (x, y) ->
                                val px = ((x - minX) / (maxX - minX)).toFloat() * canvasWidth
                                val py = canvasHeight - ((y - minY) / (maxY - minY)).toFloat() * canvasHeight
                                if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
                            }
                            drawPath(path = path, color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        history.keys.forEachIndexed { idx, name ->
                            val color = colors[idx % colors.size]
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Surface(shape = CircleShape, color = color, modifier = Modifier.size(8.dp)) {}
                                Text(name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = vm::clearGraphHistory) { Text("Clear") }
                Button(onClick = onSaveSvg) { Text("Save as SVG") }
                Button(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {}
    )
}

// ---------------------------------------------------------------- CAN map
@Composable
private fun MapScreenContent(ui: Ui, vm: MainViewModel) = NeedDb(ui, vm) {
    val db = ui.db!!
    var adding by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    confirm?.let { (msg, act) -> Confirm(msg, act) { confirm = null } }
    if (adding) AddMapDialog(db, onAdd = { d, id, ext, e -> vm.addMapEntry(d, id, ext, e) }) { adding = false }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        col {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::refreshMap, enabled = ui.connected) { Text("Read map") }
                OutlinedButton(onClick = { adding = true }, enabled = ui.connected) { Text("Add") }
            }
            Text("Map changes are live immediately. Use Save to flash on the Device tab to keep them.", style = MaterialTheme.typography.bodySmall)
        }
        for (dir in Direction.values()) {
            val list = if (dir == Direction.TX) ui.mapTx else ui.mapRx
            col {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(if (dir == Direction.TX) "Transmit" else "Receive", style = MaterialTheme.typography.titleMedium)
                    if (list.isNotEmpty()) TextButton(onClick = { confirm = "Remove every ${dir.name} mapping on the device?" to { vm.clearMap(dir) } }) { Text("Clear") }
                }
                if (list.isEmpty()) Text("None", style = MaterialTheme.typography.bodySmall)
            }
            for (msg in list) {
                col(key = "${dir.name}-${msg.listIndex}") {
                    Text("0x%X%s".format(msg.canId, if (msg.extended) " (extended)" else ""), fontFamily = FontFamily.Monospace)
                    msg.entries.forEachIndexed { pi, e ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                "${db.byId[e.paramId]?.name ?: "id ${e.paramId}"}  bit ${e.position}, len ${e.length}, gain ${e.gain.fmt()}, off ${e.offset}",
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { confirm = "Remove this mapping?" to { vm.removeMapEntry(dir, msg.listIndex, pi) } }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddMapDialog(db: ParamDb, onAdd: (Direction, Int, Boolean, MapEntry) -> Unit, onClose: () -> Unit) {
    var dir by remember { mutableStateOf(Direction.TX) }
    var canId by remember { mutableStateOf("0x100") }
    var ext by remember { mutableStateOf(false) }
    var param by remember { mutableStateOf("") }
    var pos by remember { mutableStateOf("0") }
    var len by remember { mutableStateOf("16") }
    var gain by remember { mutableStateOf("1") }
    var off by remember { mutableStateOf("0") }
    val p = db.byName[param]

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Add mapping") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Direction.values().forEach { d -> FilterChip(selected = dir == d, onClick = { dir = d }, label = { Text(d.name) }) }
                    FilterChip(selected = ext, onClick = { ext = !ext }, label = { Text("Extended ID") })
                }
                OutlinedTextField(canId, { canId = it }, label = { Text("CAN ID (hex 0x… or decimal)") }, singleLine = true)
                OutlinedTextField(param, { param = it }, label = { Text("Parameter name") }, singleLine = true,
                    isError = param.isNotEmpty() && p == null)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(pos, { pos = it }, label = { Text("Bit") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(len, { len = it }, label = { Text("Len") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(gain, { gain = it }, label = { Text("Gain") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(off, { off = it }, label = { Text("Offset") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Text("Negative length = big endian.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                enabled = p != null,
                onClick = {
                    val id = if (canId.startsWith("0x", true)) canId.drop(2).toIntOrNull(16) else canId.toIntOrNull()
                    val e = runCatching { MapEntry(p!!.id, pos.toInt(), len.toInt(), gain.toDouble(), off.toInt()) }.getOrNull()
                    if (id != null && e != null) { onAdd(dir, id, ext, e); onClose() }
                }
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}

// ---------------------------------------------------------------- Tools
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolsScreen(ui: Ui, vm: MainViewModel) {
    val logPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { u: Uri? ->
        if (u != null) vm.startCanLog(u)
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u: Uri? -> u?.let { vm.saveParams(it) } }
            val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? -> u?.let { vm.loadParams(it) } }
            val dbFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? -> u?.let { vm.loadDbFile(it) } }
            var confirmLoad by remember { mutableStateOf(false) }
            if (confirmLoad) Confirm("Write every parameter in the chosen file to node ${ui.nodeId}?", { load.launch(arrayOf("application/json", "*/*")) }) { confirmLoad = false }

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Menu, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Parameter Backup & Files", style = MaterialTheme.typography.titleMedium)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Text("Backup or restore all parameters as JSON files, or load a custom database file.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { save.launch("parameters.json") },
                            enabled = ui.connected && ui.db != null,
                            modifier = Modifier.weight(1f)
                        ) { Text("Save JSON") }
                        OutlinedButton(
                            onClick = { confirmLoad = true },
                            enabled = ui.connected && ui.db != null,
                            modifier = Modifier.weight(1f)
                        ) { Text("Load JSON") }
                    }
                    OutlinedButton(
                        onClick = { dbFile.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Use Custom DB File") }
                }
            }
        }
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("CAN Adapter", style = MaterialTheme.typography.titleMedium)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = CircleShape,
                            color = if (ui.connected) Color(0xFF10B981) else Color(0xFF6B7280),
                            modifier = Modifier.size(10.dp)
                        ) {}
                        Text(ui.status, style = MaterialTheme.typography.bodyMedium)
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        MainViewModel.BITRATES.forEach { (code, label) ->
                            FilterChip(
                                selected = ui.bitrate == code,
                                onClick = { vm.setBitrate(code) },
                                label = { Text(label) },
                                enabled = !ui.connected
                            )
                        }
                    }

                    Button(
                        onClick = { if (ui.connected) vm.disconnect() else vm.connect() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (ui.connected) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                    ) {
                        Text(if (ui.connected) "Disconnect Adapter" else "Connect Adapter")
                    }
                }
            }
        }
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("SavvyCAN Bus Logger", style = MaterialTheme.typography.titleMedium)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Text("Streams raw CAN frames directly to storage in SavvyCAN ASCII format without buffering.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (ui.logging) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Status: Streaming live... (${ui.logRows} frames captured)", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF10B981), fontFamily = FontFamily.Monospace)
                            Button(
                                onClick = vm::stopCanLog,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Stop Logging")
                            }
                        }
                    } else {
                        Button(
                            onClick = { logPicker.launch("savvycan-log.txt") },
                            enabled = ui.connected,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start SavvyCAN Stream to File")
                        }
                    }
                }
            }
        }
        item {
            val picoLogPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { u: Uri? ->
                if (u != null) vm.startPicoFlexLog(u)
            }
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("PicoFlex FlexRay / CAN Logger", style = MaterialTheme.typography.titleMedium)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Text("Connect to Macchina PicoFlex via USB and log CAN, CAN-FD, and FlexRay frames to SavvyCAN CSV format.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (ui.picoLogging) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Status: Logging FlexRay/CAN live... (${ui.picoLogRows} frames)", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF10B981), fontFamily = FontFamily.Monospace)
                            Button(
                                onClick = vm::stopPicoFlexLog,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Stop PicoFlex Logging")
                            }
                        }
                    } else {
                        Button(
                            onClick = { picoLogPicker.launch("picoflex-flexray-log.csv") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start PicoFlex FlexRay Log")
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Dashboard
fun formatOpmode(v: Double?): String = when (v?.toInt()) {
    0 -> "OFF"
    1 -> "RUN"
    2 -> "PRECHARGE"
    3 -> "PCH FAIL"
    4 -> "CHARGE"
    5 -> "PREHEAT"
    else -> "OFFLINE"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardScreen(ui: Ui, vm: MainViewModel) {
    LaunchedEffect(Unit) {
        vm.startDashboardPolling(3)
    }

    val soc = ui.values["SOC"] ?: ui.values["SOCFC"]
    val opmode = ui.values["opmode"]
    val udc = ui.values["udc"]
    val idc = ui.values["idc"]
    val power = ui.values["power"]
    val vMin = ui.values["BMS_Vmin"]
    val vMax = ui.values["BMS_Vmax"]
    val tMin = ui.values["BMS_Tmin"]
    val tMax = ui.values["BMS_Tmax"]
    val uaux = ui.values["uaux"]

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(0xFF0B1329))) {
        val isLandscape = this.maxWidth > this.maxHeight

        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = Color(0xFF22D3EE))
                    Text("EV COCKPIT DASHBOARD", style = MaterialTheme.typography.titleMedium, color = Color(0xFFF3F4F6), fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = CircleShape, color = if (ui.connected) Color(0xFF10B981) else Color(0xFFEF4444), modifier = Modifier.size(8.dp)) {}
                    Text("Node 3 · SDO Live", style = MaterialTheme.typography.labelMedium, color = Color(0xFF9CA3AF), fontFamily = FontFamily.Monospace)
                }
            }

            if (isLandscape) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Left Column: Circular Arc Gauge & SOC
                    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                            BatteryArcGauge(soc = soc?.toFloat() ?: 0f, modifier = Modifier.fillMaxSize())
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("⚡ ${soc?.toInt() ?: 0}%", style = MaterialTheme.typography.headlineLarge, color = Color(0xFF22D3EE), fontWeight = FontWeight.Bold)
                                Text("BATTERY SOC", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9CA3AF))
                            }
                        }
                    }

                    // Right Column: Metrics
                    Column(modifier = Modifier.weight(1.2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DashboardMetricsGrid(opmode = opmode, udc = udc, idc = idc, power = power, vMin = vMin, vMax = vMax, tMin = tMin, tMax = tMax, uaux = uaux)
                    }
                }
            } else {
                // Portrait Layout
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                            BatteryArcGauge(soc = soc?.toFloat() ?: 0f, modifier = Modifier.fillMaxSize())
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("⚡ ${soc?.toInt() ?: 0}%", style = MaterialTheme.typography.displayMedium, color = Color(0xFF22D3EE), fontWeight = FontWeight.Bold)
                                Text("BATTERY SOC", style = MaterialTheme.typography.labelSmall, color = Color(0xFF9CA3AF))
                            }
                        }
                    }

                    DashboardMetricsGrid(opmode = opmode, udc = udc, idc = idc, power = power, vMin = vMin, vMax = vMax, tMin = tMin, tMax = tMax, uaux = uaux)
                }
            }
        }
    }
}

@Composable
private fun BatteryArcGauge(soc: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val center = androidx.compose.ui.geometry.Offset(w / 2f, h / 2f)
            val radius = minOf(w, h) / 2f - 16f

            val startAngle = 135f
            val sweepAngle = 270f
            val cyanGlow = Color(0xFF06B6D4)
            val cyanBright = Color(0xFF22D3EE)
            val trackColor = Color(0xFF1E293B)

            // Background Arc Track
            drawArc(
                color = trackColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = 16f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )

            // Active Battery Fill Arc
            val currentSweep = sweepAngle * (soc.coerceIn(0f, 100f) / 100f)
            if (currentSweep > 0f) {
                drawArc(
                    color = cyanGlow.copy(alpha = 0.3f),
                    startAngle = startAngle,
                    sweepAngle = currentSweep,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = Stroke(width = 24f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
                drawArc(
                    color = if (soc > 20f) cyanBright else Color(0xFFEF4444),
                    startAngle = startAngle,
                    sweepAngle = currentSweep,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = Stroke(width = 12f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
        }
    }
}



@Composable
private fun DashboardMetricsGrid(
    opmode: Double?,
    udc: Double?,
    idc: Double?,
    power: Double?,
    vMin: Double?,
    vMax: Double?,
    tMin: Double?,
    tMax: Double?,
    uaux: Double? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("OPMODE", formatOpmode(opmode), Color(0xFF10B981), Modifier.weight(1f))
            MetricCard("PACK VOLTAGE", if (udc != null) "${udc.fmt()} V" else "–", Color(0xFF22D3EE), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("CURRENT & POWER", if (idc != null && power != null) "${idc.fmt()} A | ${power.fmt()} kW" else if (idc != null) "${idc.fmt()} A" else "–", Color(0xFFA855F7), Modifier.weight(1f))
            MetricCard("12V AUX VOLTAGE", if (uaux != null) "${uaux.fmt()} V" else "–", Color(0xFF34D399), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard("CELL V (MIN / MAX)", if (vMin != null && vMax != null) "${vMin.fmt()}V / ${vMax.fmt()}V" else "–", Color(0xFF38BDF8), Modifier.weight(1f))
            MetricCard("CELL TEMP (MIN / MAX)", if (tMin != null && tMax != null) "${tMin.toInt()}°C / ${tMax.toInt()}°C" else "–", Color(0xFFFBBF24), Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, accentColor: Color, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF1E293B))
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9CA3AF))
            Text(value, style = MaterialTheme.typography.titleMedium, color = accentColor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun GithubReleasesDialog(
    vm: MainViewModel,
    onDismiss: () -> Unit,
    onSelectAsset: (GhReleaseAsset) -> Unit
) {
    val releases by vm.ghReleases.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (releases.isEmpty()) {
            vm.fetchGithubReleases()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Stm32-VCU Online Releases")
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (releases.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(releases, key = { it.tagName }) { rel ->
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(rel.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(rel.tagName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                                    if (rel.body.isNotBlank()) {
                                        Text(rel.body.take(120) + "...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    HorizontalDivider()
                                    rel.assets.forEach { asset ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable { onSelectAsset(asset); onDismiss() }.padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                Text(asset.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            }
                                            Text("${asset.size / 1024} KB", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = vm::fetchGithubReleases) { Text("Refresh") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {}
    )
}
