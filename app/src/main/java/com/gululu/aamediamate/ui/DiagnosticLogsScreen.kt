@file:OptIn(ExperimentalMaterial3Api::class)

package com.gululu.aamediamate.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.gululu.aamediamate.R
import com.gululu.aamediamate.diagnostics.DiagnosticEvent
import com.gululu.aamediamate.diagnostics.DiagnosticLevel
import com.gululu.aamediamate.diagnostics.DiagnosticLogger
import com.gululu.aamediamate.diagnostics.DiagnosticModule
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun DiagnosticLogsScreen(onBack: () -> Unit) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var events by remember { mutableStateOf<List<DiagnosticEvent>>(emptyList()) }
    var selectedLevels by remember {
        mutableStateOf(setOf(DiagnosticLevel.INFO, DiagnosticLevel.WARN, DiagnosticLevel.ERROR))
    }
    var selectedModules by remember { mutableStateOf(DiagnosticModule.values().toSet()) }
    var query by remember { mutableStateOf("") }
    var debugEnabled by remember { mutableStateOf(DiagnosticLogger.isDebugEnabled(context)) }

    val filteredEvents = remember(events, selectedLevels, selectedModules, query) {
        events.filter { event ->
            event.level in selectedLevels &&
                event.module in selectedModules &&
                event.matches(query)
        }
    }

    fun refreshEvents() {
        coroutineScope.launch {
            events = withContext(Dispatchers.IO) { DiagnosticLogger.getEvents(context) }
            debugEnabled = DiagnosticLogger.isDebugEnabled(context)
        }
    }

    LaunchedEffect(Unit) { refreshEvents() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.diagnostics_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { refreshEvents() }) {
                    Text(stringResource(R.string.refresh))
                }
                OutlinedButton(
                    onClick = {
                        if (debugEnabled) {
                            DiagnosticLogger.disableDebug(context)
                        } else {
                            DiagnosticLogger.enableDebugFor(context)
                        }
                        refreshEvents()
                    }
                ) {
                    Text(
                        if (debugEnabled) {
                            stringResource(R.string.diagnostics_debug_disable)
                        } else {
                            stringResource(R.string.diagnostics_debug_enable)
                        }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        val report = DiagnosticLogger.buildIssueReport(context, filteredEvents)
                        clipboardManager.setText(AnnotatedString(report))
                        coroutineScope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = context.getString(R.string.diagnostics_report_copied),
                                actionLabel = context.getString(R.string.diagnostics_open_github)
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/gululu1235/AAMediaMate/issues/new")
                                )
                                context.startActivity(intent)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.diagnostics_copy_report))
                }
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) { DiagnosticLogger.clear(context) }
                            refreshEvents()
                        }
                    }
                ) {
                    Text(stringResource(R.string.clear))
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            DiagnosticFilterRow(
                title = stringResource(R.string.diagnostics_levels),
                values = DiagnosticLevel.values().toList(),
                selectedValues = selectedLevels,
                label = { it.name },
                onToggle = { level ->
                    selectedLevels = selectedLevels.toggle(level)
                }
            )

            DiagnosticFilterRow(
                title = stringResource(R.string.diagnostics_modules),
                values = DiagnosticModule.values().toList(),
                selectedValues = selectedModules,
                label = { it.name.lowercase().replaceFirstChar(Char::uppercaseChar) },
                onToggle = { module ->
                    selectedModules = selectedModules.toggle(module)
                }
            )

            Text(
                text = stringResource(R.string.diagnostics_event_count, filteredEvents.size, events.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = filteredEvents.asReversed(),
                    key = { "${it.timestampMs}-${it.level}-${it.module}-${it.message}" }
                ) { event ->
                    DiagnosticEventRow(event)
                }
            }
        }
    }
}

@Composable
private fun <T> DiagnosticFilterRow(
    title: String,
    values: List<T>,
    selectedValues: Set<T>,
    label: (T) -> String,
    onToggle: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            values.forEach { value ->
                FilterChip(
                    selected = value in selectedValues,
                    onClick = { onToggle(value) },
                    label = { Text(label(value)) }
                )
            }
        }
    }
}

@Composable
private fun DiagnosticEventRow(event: DiagnosticEvent) {
    var expanded by remember(event.timestampMs, event.message) { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "${event.level.name} · ${event.module.name} · ${formatLocalTime(event.timestampMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = event.level.color()
            )
            Text(event.message, style = MaterialTheme.typography.bodyMedium)
            if (expanded) {
                if (event.details.isEmpty() && event.throwable.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.diagnostics_no_details),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    event.details.forEach { (key, value) ->
                        Text(
                            text = "$key: $value",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    event.throwable?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

private fun DiagnosticEvent.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val needle = query.trim()
    return message.contains(needle, ignoreCase = true) ||
        level.name.contains(needle, ignoreCase = true) ||
        module.name.contains(needle, ignoreCase = true) ||
        details.any { (key, value) ->
            key.contains(needle, ignoreCase = true) || value.contains(needle, ignoreCase = true)
        } ||
        throwable?.contains(needle, ignoreCase = true) == true
}

private fun <T> Set<T>.toggle(value: T): Set<T> {
    return if (value in this) this - value else this + value
}

@Composable
private fun DiagnosticLevel.color() = when (this) {
    DiagnosticLevel.ERROR -> MaterialTheme.colorScheme.error
    DiagnosticLevel.WARN -> MaterialTheme.colorScheme.tertiary
    DiagnosticLevel.INFO -> MaterialTheme.colorScheme.primary
    DiagnosticLevel.DEBUG -> MaterialTheme.colorScheme.secondary
}

private fun formatLocalTime(timestampMs: Long): String {
    return DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(timestampMs))
}
