package me.gm.cleaner.plugin.ui.screens.usagerecord

import android.app.Application
import android.icu.text.DateFormat
import android.icu.util.TimeZone
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Date
import java.util.Locale
import me.gm.cleaner.plugin.R
import me.gm.cleaner.plugin.IMediaChangeObserver
import me.gm.cleaner.plugin.dao.MediaProviderOperation.Companion.OP_DELETE
import me.gm.cleaner.plugin.dao.MediaProviderOperation.Companion.OP_INSERT
import me.gm.cleaner.plugin.dao.MediaProviderOperation.Companion.OP_QUERY
import me.gm.cleaner.plugin.dao.MediaProviderRecord
import me.gm.cleaner.plugin.dao.RootPreferences
import me.gm.cleaner.plugin.ui.components.EmptyStateCard
import me.gm.cleaner.plugin.ui.components.TopLevelTopBar
import me.gm.cleaner.plugin.ui.module.BinderViewModel
import me.gm.cleaner.plugin.ui.module.usagerecord.UsageRecordState
import me.gm.cleaner.plugin.ui.module.usagerecord.UsageRecordViewModel

@Composable
fun UsageRecordScreen(
    binderViewModel: BinderViewModel,
    onOpenDrawer: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel = remember(binderViewModel) {
        UsageRecordViewModel(
            context.applicationContext as Application,
            binderViewModel,
        )
    }
    val recordsState by viewModel.recordsFlow.collectAsStateWithLifecycle(initialValue = null)
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel, binderViewModel) {
        while (!binderViewModel.pingBinder()) {
            kotlinx.coroutines.delay(500)
        }
        viewModel.reload()
    }

    DisposableEffect(binderViewModel) {
        val observer = object : IMediaChangeObserver.Stub() {
            override fun onChange() {
                viewModel.reload()
            }
        }
        binderViewModel.registerMediaChangeObserver(observer)
        onDispose {
            binderViewModel.unregisterMediaChangeObserver(observer)
        }
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDatePicker = false
                        viewModel.reload()
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
        ) {
            val state = rememberDatePickerState(initialSelectedDateMillis = viewModel.selectedTime)
            DatePicker(state = state)
            LaunchedEffect(state.selectedDateMillis) {
                state.selectedDateMillis?.let { viewModel.selectedTime = it }
            }
        }
    }

    val hideQuery by RootPreferences.isHideQueryFlowable.asFlow().collectAsStateWithLifecycle(
        initialValue = RootPreferences.isHideQueryFlowable.value
    )
    val hideInsert by RootPreferences.isHideInsertFlowable.asFlow().collectAsStateWithLifecycle(
        initialValue = RootPreferences.isHideInsertFlowable.value
    )
    val hideDelete by RootPreferences.isHideDeleteFlowable.asFlow().collectAsStateWithLifecycle(
        initialValue = RootPreferences.isHideDeleteFlowable.value
    )

    val selectedDateStr = remember(viewModel.selectedTime) {
        DateFormat.getInstanceForSkeleton(
            DateFormat.YEAR_ABBR_MONTH_DAY,
            Locale.getDefault(),
        ).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(viewModel.selectedTime))
    }

    Scaffold(
        topBar = {
            Column {
                TopLevelTopBar(
                    title = stringResource(R.string.usage_record),
                    onOpenDrawer = onOpenDrawer,
                    actions = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = stringResource(R.string.pick_date_title),
                            )
                        }
                        IconButton(onClick = { isSearching = !isSearching }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.search),
                            )
                        }
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = stringResource(R.string.filter),
                            )
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_hide_query_title)) },
                                onClick = {
                                    RootPreferences.isHideQueryFlowable.value = !hideQuery
                                    showFilterMenu = false
                                },
                                trailingIcon = { Checkbox(checked = hideQuery, onCheckedChange = null) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_hide_insert_title)) },
                                onClick = {
                                    RootPreferences.isHideInsertFlowable.value = !hideInsert
                                    showFilterMenu = false
                                },
                                trailingIcon = { Checkbox(checked = hideInsert, onCheckedChange = null) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_hide_delete_title)) },
                                onClick = {
                                    RootPreferences.isHideDeleteFlowable.value = !hideDelete
                                    showFilterMenu = false
                                },
                                trailingIcon = { Checkbox(checked = hideDelete, onCheckedChange = null) },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_clear)) },
                                onClick = {
                                    binderViewModel.clearAllTables()
                                    viewModel.reload()
                                    showFilterMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.ClearAll,
                                        contentDescription = null,
                                    )
                                },
                            )
                        }
                    },
                )
                Surface {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = selectedDateStr,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        AnimatedVisibility(visible = isSearching) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    viewModel.queryText = it
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.search)) },
                                singleLine = true,
                            )
                        }
                    }
                }
            }
        },
    ) { paddingValues ->
        when (val state = recordsState) {
            null, UsageRecordState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is UsageRecordState.Done -> {
                val filteredList = state.list.filter { record ->
                    if (searchQuery.isBlank()) true
                    else record.data.any { it.contains(searchQuery, ignoreCase = true) } ||
                        record.label?.contains(searchQuery, ignoreCase = true) == true ||
                        record.packageName.contains(searchQuery, ignoreCase = true)
                }
                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyStateCard(
                            title = stringResource(R.string.no_usage_records_found),
                            subtitle = selectedDateStr,
                            icon = Icons.Default.DateRange,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = filteredList,
                            key = { record ->
                                if (record.id != 0) {
                                    record.id
                                } else {
                                    "${record.timeMillis}:${record.packageName}:${record.operation}:${record.match}:${record.data.firstOrNull().orEmpty()}"
                                }
                            },
                        ) { record ->
                            UsageRecordItem(record = record)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageRecordItem(record: MediaProviderRecord) {
    val context = LocalContext.current
    val operationLabel = when (record.operation) {
        OP_QUERY -> stringResource(R.string.queried_at)
        OP_INSERT -> stringResource(R.string.inserted_at)
        OP_DELETE -> stringResource(R.string.deleted_at)
        else -> ""
    }
    val timeStr = DateUtils.formatDateTime(
        context,
        record.timeMillis,
        DateUtils.FORMAT_NO_NOON or DateUtils.FORMAT_NO_MIDNIGHT or
            DateUtils.FORMAT_ABBREV_ALL or DateUtils.FORMAT_SHOW_TIME,
    )
    val isIntercepted = record.intercepted.any { it }
    val operationColor = when (record.operation) {
        OP_QUERY -> MaterialTheme.colorScheme.primary
        OP_INSERT -> Color(0xFF2E7D32)
        OP_DELETE -> Color(0xFFD32F2F)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val operationText = if (isIntercepted) {
        buildAnnotatedString {
            pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
            append("$operationLabel$timeStr")
            pop()
        }
    } else {
        buildAnnotatedString { append("$operationLabel$timeStr") }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UsageRecordAppIcon(record = record, modifier = Modifier.size(32.dp))
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f),
                ) {
                    Text(
                        text = record.label ?: record.packageName,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = operationText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = operationColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            record.data.firstOrNull()?.let { firstData ->
                Text(
                    text = firstData,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 44.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (record.data.size > 1) {
                Text(
                    text = stringResource(R.string.and_more, record.data.size - 1),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 44.dp),
                )
            }
        }
    }
}

@Composable
private fun UsageRecordAppIcon(
    record: MediaProviderRecord,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pi = record.packageInfo
    if (pi != null) {
        val icon = remember(pi.packageName) {
            try {
                pi.applicationInfo?.loadIcon(context.packageManager)
            } catch (_: Exception) {
                null
            }
        }
        if (icon != null) {
            val bitmap = remember(icon) {
                android.graphics.Bitmap.createBitmap(
                    icon.intrinsicWidth.coerceAtLeast(1),
                    icon.intrinsicHeight.coerceAtLeast(1),
                    android.graphics.Bitmap.Config.ARGB_8888,
                ).also { bitmap ->
                    icon.setBounds(0, 0, bitmap.width, bitmap.height)
                    icon.draw(android.graphics.Canvas(bitmap))
                }
            }
            Image(
                painter = BitmapPainter(bitmap.asImageBitmap()),
                contentDescription = null,
                modifier = modifier,
            )
            return
        }
    }
    Image(
        painter = painterResource(R.drawable.ic_outline_apps_24),
        contentDescription = null,
        modifier = modifier,
    )
}
