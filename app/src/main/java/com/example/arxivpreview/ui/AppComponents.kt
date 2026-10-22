package com.example.arxivpreview.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Shared visual language for every destination, including secondary screens and readers. */
@Composable
internal fun AppLargeTitle(
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) { actions() }
        }
        subtitle?.let { Text(it, Modifier.padding(top = 4.dp, end = 8.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
internal fun AppNavigationBar(
    title: String,
    onBack: () -> Unit,
    backDescription: String = "Back",
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(color = MaterialTheme.colorScheme.background.copy(alpha = .97f)) {
        Column(Modifier.statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, backDescription, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                }
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2)
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) { actions() }
            }
            HorizontalDivider(thickness = .5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
internal fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value, onValueChange = onValueChange, singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(imeAction = if (onSearch != null) ImeAction.Search else ImeAction.Done),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = label; text = androidx.compose.ui.text.AnnotatedString(label) },
        decorationBox = { field ->
            Row(Modifier.clip(RoundedCornerShape(12.dp)).background(colors.onSurface.copy(alpha = .055f))
                .padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, Modifier.size(21.dp), tint = colors.onSurfaceVariant)
                Box(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 12.dp)) {
                    if (value.isEmpty()) Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                    field()
                }
                if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Cancel, "Clear $label", Modifier.size(18.dp), tint = colors.onSurfaceVariant)
                } else Spacer(Modifier.width(8.dp))
            }
        },
    )
}

@Composable
internal fun AppFormField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val fill = MaterialTheme.colorScheme.onSurface.copy(alpha = .04f)
    TextField(value, onValueChange, modifier, label = label, singleLine = singleLine, leadingIcon = leadingIcon,
        visualTransformation = visualTransformation, shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(focusedContainerColor = fill, unfocusedContainerColor = fill,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
}

@Composable
internal fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(checked, onCheckedChange, colors = SwitchDefaults.colors(
        checkedTrackColor = MaterialTheme.colorScheme.tertiary, checkedThumbColor = Color.White,
        checkedBorderColor = Color.Transparent, uncheckedBorderColor = Color.Transparent,
        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant, uncheckedThumbColor = Color.White))
}

@Composable
internal fun AppSegmentedControl(options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().selectableGroup().clip(RoundedCornerShape(10.dp))
        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .07f)).padding(3.dp)) {
        options.forEach { option ->
            val active = selected == option
            val background by animateColorAsState(if (active) MaterialTheme.colorScheme.surface else Color.Transparent, label = "segment")
            Box(Modifier.weight(1f).shadow(if (active) 1.dp else 0.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp)).background(background)
                .selectable(selected = active, role = Role.Tab, onClick = { onSelect(option) }), contentAlignment = Alignment.Center) {
                Text(option, Modifier.padding(horizontal = 4.dp, vertical = 9.dp), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
internal fun AppSectionHeading(title: String, detail: String? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        detail?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
internal fun AppInsetGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, Modifier.padding(start = 36.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
internal fun AppEmptyState(title: String, message: String, icon: ImageVector = Icons.Default.Description, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f))
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
    }
}

internal data class SubjectStyle(val color: Color, val icon: ImageVector)
internal fun subjectStyle(group: String): SubjectStyle = when {
    group.contains("Computer", true) -> SubjectStyle(Color(0xFF007AFF), Icons.Default.Terminal)
    group.contains("Mathematics", true) -> SubjectStyle(Color(0xFFAF52DE), Icons.Default.Functions)
    group.contains("Physics", true) || group.contains("Astro", true) -> SubjectStyle(Color(0xFFFF9500), Icons.Default.AutoAwesome)
    group.contains("Biology", true) -> SubjectStyle(Color(0xFF34C759), Icons.Default.Biotech)
    group.contains("Finance", true) || group.contains("Economics", true) -> SubjectStyle(Color(0xFF00A6A6), Icons.AutoMirrored.Filled.ShowChart)
    group.contains("Statistics", true) -> SubjectStyle(Color(0xFF5856D6), Icons.Default.BarChart)
    else -> SubjectStyle(Color(0xFF5AC8FA), Icons.Default.Science)
}

@Composable
internal fun SubjectIcon(group: String, modifier: Modifier = Modifier) {
    val style = subjectStyle(group)
    Box(modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(style.color.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
        Icon(style.icon, null, Modifier.size(24.dp), tint = style.color)
    }
}
