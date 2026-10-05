package com.zalthar.zpad

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zalthar.zpad.ui.theme.appearanceNames
import com.zalthar.zpad.ui.theme.fontNames
import com.zalthar.zpad.ui.theme.noteColors
import com.zalthar.zpad.ui.theme.noteFont
import com.zalthar.zpad.ui.theme.themeNames

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsPage(theme: String, font: String, appearance: String, dark: Boolean, changeTheme: (String) -> Unit, changeFont: (String) -> Unit, changeAppearance: (String) -> Unit, textSize: Float, changeSize: (Float) -> Unit, onImport: () -> Unit, importEnabled: Boolean, onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
            item {
                SettingsSection("Mode") {
                    PillSelector(appearanceNames, appearance, changeAppearance)
                }
            }
            item {
                SettingsSection("Theme") {
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        themeNames.forEach { name -> ThemeTile(name, dark, theme == name) { changeTheme(name) } }
                    }
                }
            }
            item {
                SettingsSection("Font") {
                    fontNames.forEach { name ->
                        SettingsChoice(selected = font == name, onClick = { changeFont(name) }) {
                            Text(name, style = MaterialTheme.typography.bodyLarge, fontFamily = noteFont(name), modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                SettingsSection("Text size") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${textSize.toInt()} SP", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp), modifier = Modifier.width(48.dp))
                        Slider(value = textSize, onValueChange = changeSize, valueRange = 12f..28f, steps = 7, modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary, inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                                activeTickColor = MaterialTheme.colorScheme.onPrimary, inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant))
                        TextButton(onClick = { changeSize(16f) }, enabled = textSize != 16f) { Text("Reset") }
                    }
                }
            }
            item {
                SettingsSection("Storage") {
                    Text("Downloads/Zpad", style = MaterialTheme.typography.bodyLarge)
                    Text("Plain text files. Yours to keep.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    FilledTonalButton(onClick = onImport, enabled = importEnabled, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 48.dp)) {
                        Text("Import .txt")
                    }
                }
            }
        }
    }
}

/** One-of-many choice: a soft track with the selected option filled in the accent colour. */
@Composable
private fun PillSelector(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { name ->
            val active = name == selected
            val fill by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
            val ink by animateColorAsState(if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f).height(40.dp).clip(CircleShape).background(fill)
                .selectable(selected = active, role = Role.RadioButton, onClick = { onSelect(name) }), contentAlignment = Alignment.Center) {
                Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, color = ink)
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** A tiny page drawn in the theme's own colors. */
@Composable
private fun ThemeTile(name: String, dark: Boolean, selected: Boolean, onClick: () -> Unit) {
    val scheme = noteColors(name, dark)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(MaterialTheme.shapes.medium).selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(4.dp)) {
        Box(Modifier.size(84.dp, 104.dp).clip(MaterialTheme.shapes.medium).background(scheme.background)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(40.dp, 8.dp).background(scheme.onSurface, CircleShape))
                Box(Modifier.size(56.dp, 5.dp).background(scheme.onSurfaceVariant.copy(alpha = 0.6f), CircleShape))
                Box(Modifier.size(48.dp, 5.dp).background(scheme.onSurfaceVariant.copy(alpha = 0.6f), CircleShape))
                Box(Modifier.size(32.dp, 5.dp).background(scheme.onSurfaceVariant.copy(alpha = 0.6f), CircleShape))
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(20.dp).background(scheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                if (selected) Icon(Icons.Default.Check, null, tint = scheme.onPrimary, modifier = Modifier.size(14.dp))
            }
        }
        Text(name.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun SettingsChoice(selected: Boolean, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        content()
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
    }
}
