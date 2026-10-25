package app.oic.ui

import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Warning
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
    TabDef("Device", Icons.Filled.Home),
    TabDef("Params", Icons.Filled.Settings),
    TabDef("Live", Icons.Filled.Info),
    TabDef("CAN map", Icons.Filled.List),
    TabDef("Files", Icons.Filled.Menu)
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
                0 -> DeviceScreen(ui, vm)
                1 -> ParamsScreen(ui, vm)
                2 -> LiveScreen(ui, vm)
                3 -> MapScreen(ui, vm)
                else -> FilesScreen(ui, vm)
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
    val activity = LocalContext.current as? Activity

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
                            onClick = { activity?.let { vm.donate(it) } },
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
                            Text("Serial Number")
                        }
                        OutlinedButton(onClick = vm::loadDb, enabled = ui.connected, modifier = Modifier.weight(1f)) {
                            Text("Load Database")
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
            val fwPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
                u?.let { vm.upgradeFirmware(it) }
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
                        Button(
                            onClick = { fwPicker.launch(arrayOf("application/octet-stream", "*/*")) },
                            enabled = ui.connected,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Select Firmware (.bin) & Upgrade")
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
    var query by remember { mutableStateOf("") }
    var showValues by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<OiParam?>(null) }

    val items = remember(db, query, showValues) {
        db.params.filter { (showValues || it.isParam) && it.name.contains(query, ignoreCase = true) }
    }

    editing?.let { p -> EditDialog(p, ui.values[p.name], onWrite = { vm.write(p.name, it) }, onRead = { vm.readOne(p.name) }) { editing = null } }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(query, { query = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = !showValues, onClick = { showValues = false }, label = { Text("Parameters") })
                FilterChip(selected = showValues, onClick = { showValues = true }, label = { Text("+ spot values") })
                OutlinedButton(onClick = { vm.readAll(!showValues) }, enabled = ui.connected) { Text("Read all") }
            }
        }
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
    val step = ((stepText.toDoubleOrNull() ?: 1.0).coerceIn(0.1, 60.0) * 1000).toLong()
    val logPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { u: Uri? ->
        if (u != null) vm.startPolling(selected.toList(), step, u)
    }
    val items = remember(db, query) { db.params.filter { it.name.contains(query, ignoreCase = true) } }

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

// ---------------------------------------------------------------- CAN map
@Composable
private fun MapScreen(ui: Ui, vm: MainViewModel) = NeedDb(ui, vm) {
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

// ---------------------------------------------------------------- Files
@Composable
private fun FilesScreen(ui: Ui, vm: MainViewModel) {
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u: Uri? -> u?.let { vm.saveParams(it) } }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? -> u?.let { vm.loadParams(it) } }
    val dbFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? -> u?.let { vm.loadDbFile(it) } }
    var confirmLoad by remember { mutableStateOf(false) }
    if (confirmLoad) Confirm("Write every parameter in the chosen file to node ${ui.nodeId}?", { load.launch(arrayOf("application/json", "*/*")) }) { confirmLoad = false }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Parameter backup", style = MaterialTheme.typography.titleMedium)
        Text("Saves every parameter as JSON, in the same format as the oic command line tool.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { save.launch("parameters.json") }, enabled = ui.connected && ui.db != null) { Text("Save parameters to file") }
        OutlinedButton(onClick = { confirmLoad = true }, enabled = ui.connected && ui.db != null) { Text("Load parameters from file") }
        HorizontalDivider()
        Text("Parameter database", style = MaterialTheme.typography.titleMedium)
        Text("Current: ${ui.dbSource}")
        OutlinedButton(onClick = { dbFile.launch(arrayOf("application/json", "*/*")) }) { Text("Use database file instead") }
    }
}
