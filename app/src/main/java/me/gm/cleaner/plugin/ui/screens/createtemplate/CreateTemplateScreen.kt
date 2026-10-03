/*
 * Copyright 2021 Green Mushroom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.gm.cleaner.plugin.ui.screens.createtemplate

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.MediaStore.Files.FileColumns
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.gm.cleaner.plugin.R
import me.gm.cleaner.plugin.model.Template
import me.gm.cleaner.plugin.model.FilterPathDisplay
import me.gm.cleaner.plugin.model.TemplateEditor
import me.gm.cleaner.plugin.ui.components.PreferenceGroup
import me.gm.cleaner.plugin.ui.components.SecondaryTopBar
import me.gm.cleaner.plugin.ui.components.SectionHeader
import me.gm.cleaner.plugin.ui.module.BinderViewModel
import me.gm.cleaner.plugin.ui.screens.templating.hookOperationLabel
import me.gm.cleaner.plugin.ui.screens.templating.mediaTypeLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MEDIA_TYPE_PLAYLIST = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTemplateScreen(
    originalTemplateName: String?,
    templateName: String?,
    hookOperation: List<String>?,
    packageNames: List<String>?,
    permittedMediaTypes: List<String>?,
    filterPaths: List<String>?,
    onNavigateBack: () -> Unit,
    binderViewModel: BinderViewModel,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val isEditing = originalTemplateName != null
    val initialOperations = hookOperation ?: listOf("query", "insert")
    val initialMediaTypes = permittedMediaTypes?.mapNotNull { it.toIntOrNull() }.orEmpty()
    val initialPaths = filterPaths?.distinct().orEmpty()
    var name by rememberSaveable { mutableStateOf(templateName.orEmpty()) }
    var selectedOperations by rememberSaveable { mutableStateOf(initialOperations) }
    var selectedMediaTypes by rememberSaveable { mutableStateOf(initialMediaTypes) }
    var selectedFilterPaths by rememberSaveable { mutableStateOf(initialPaths) }
    var isSaving by remember { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var pathEditorOpen by rememberSaveable { mutableStateOf(false) }
    var editingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pathInput by rememberSaveable { mutableStateOf("") }
    var errorResource by remember { mutableStateOf<Int?>(null) }
    val errorMessage = errorResource?.let { stringResource(it) }
    val pickerFailureMessage = stringResource(R.string.path_picker_failed)
    LaunchedEffect(errorMessage) {
        errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }
    val hasChanges = name != templateName.orEmpty() ||
        selectedOperations.toSet() != initialOperations.toSet() ||
        selectedMediaTypes.toSet() != initialMediaTypes.toSet() || selectedFilterPaths != initialPaths
    val requestBack: () -> Unit = {
        if (!isSaving) {
            if (hasChanges) showDiscardDialog = true else onNavigateBack()
        }
    }
    BackHandler { requestBack() }

    val hookOperations = listOf("query", "insert")
    val mediaTypes = listOf(
        MEDIA_TYPE_PLAYLIST,
        FileColumns.MEDIA_TYPE_SUBTITLE,
        FileColumns.MEDIA_TYPE_AUDIO,
        FileColumns.MEDIA_TYPE_VIDEO,
        FileColumns.MEDIA_TYPE_IMAGE,
        FileColumns.MEDIA_TYPE_DOCUMENT,
        FileColumns.MEDIA_TYPE_NONE,
    )
    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val path = runCatching { treeUriToFile(uri, context)?.path }.getOrNull()
        if (path == null) {
            scope.launch { snackbarHostState.showSnackbar(pickerFailureMessage) }
            return@rememberLauncherForActivityResult
        }
        selectedFilterPaths = (selectedFilterPaths + path).distinct().sorted()
    }

    fun saveTemplate() {
        if (isSaving) return
        if (name.isBlank() || selectedOperations.isEmpty()) {
            errorResource = R.string.template_save_validation
            return
        }
        val draft = Template(
            templateName = name.trim(),
            hookOperation = selectedOperations,
            applyToApp = packageNames,
            permittedMediaTypes = selectedMediaTypes.ifEmpty { null },
            filterPath = selectedFilterPaths.ifEmpty { null },
        )
        isSaving = true
        errorResource = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    binderViewModel.updateTemplates { existing ->
                        TemplateEditor.save(existing, originalTemplateName, draft)
                    }
                }
                onNavigateBack()
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: TemplateEditor.NameConflict) {
                errorResource = R.string.template_name_exists
            } catch (_: TemplateEditor.MissingTemplate) {
                errorResource = R.string.template_missing
            } catch (_: Exception) {
                errorResource = R.string.template_save_failed
            } finally {
                isSaving = false
            }
        }
    }

    Scaffold(
        topBar = {
            SecondaryTopBar(
                title = if (isEditing) {
                    stringResource(R.string.edit_template_title)
                } else {
                    stringResource(R.string.create_template_title)
                },
                onNavigateBack = requestBack,
                actions = {
                    TextButton(onClick = { saveTemplate() }, enabled = !isSaving) {
                        Text(stringResource(if (isSaving) R.string.saving else R.string.save))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            errorResource?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            Text(
                stringResource(R.string.template_scope_hint, packageNames.orEmpty().size),
                style = MaterialTheme.typography.bodySmall,
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(title = stringResource(R.string.template_name_title))
                PreferenceGroup(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                    OutlinedTextField(
                        enabled = !isSaving,
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.template_name_title)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(title = stringResource(R.string.hook_operation_title))
                PreferenceGroup {
                    hookOperations.forEachIndexed { index, operation ->
                        TemplateToggleRow(
                            enabled = !isSaving,
                            checked = operation in selectedOperations,
                            label = hookOperationLabel(context, operation),
                            onClick = {
                                selectedOperations = if (operation in selectedOperations) {
                                    selectedOperations - operation
                                } else {
                                    selectedOperations + operation
                                }
                            },
                        )
                        if (index != hookOperations.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(
                    title = stringResource(R.string.filter_path_title),
                    supporting = stringResource(R.string.filter_paths_hint),
                )
                PreferenceGroup {
                    PathPickerRow(
                        title = stringResource(R.string.add_path),
                        onClick = { if (!isSaving) openDocumentTreeLauncher.launch(null) },
                    )
                    TextButton(enabled = !isSaving, onClick = {
                        editingPath = null
                        pathInput = ""
                        pathEditorOpen = true
                    }) { Text(stringResource(R.string.enter_path)) }
                    if (selectedFilterPaths.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    selectedFilterPaths.forEachIndexed { index, path ->
                        FilterPathRow(
                            enabled = !isSaving,
                            path = path,
                            onEdit = {
                                editingPath = path
                                pathInput = path
                                pathEditorOpen = true
                            },
                            onRemove = {
                                selectedFilterPaths = selectedFilterPaths.filterNot { it == path }
                            },
                        )
                        if (index != selectedFilterPaths.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(
                    title = stringResource(R.string.permitted_media_types_title),
                    supporting = stringResource(R.string.media_types_hint),
                )
                PreferenceGroup {
                    mediaTypes.forEachIndexed { index, value ->
                        TemplateToggleRow(
                            enabled = !isSaving,
                            checked = value in selectedMediaTypes,
                            label = mediaTypeLabel(context, value),
                            onClick = {
                                selectedMediaTypes = if (value in selectedMediaTypes) {
                                    selectedMediaTypes - value
                                } else {
                                    selectedMediaTypes + value
                                }
                            },
                        )
                        if (index != mediaTypes.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.unsaved_changes)) },
            text = { Text(stringResource(R.string.quit_without_save)) },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onNavigateBack() }) {
                    Text(stringResource(R.string.discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (pathEditorOpen) {
        AlertDialog(
            onDismissRequest = { pathEditorOpen = false },
            title = { Text(stringResource(R.string.edit_path)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pathInput,
                        onValueChange = { pathInput = it },
                        label = { Text(stringResource(R.string.absolute_path)) },
                        singleLine = true,
                        isError = pathInput.isNotEmpty() && !pathInput.startsWith("/"),
                    )
                    Text(stringResource(R.string.path_exact_hint), style = MaterialTheme.typography.bodySmall)
                    PathCharacterWarning(pathInput)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pathInput.startsWith("/") && !isSaving,
                    onClick = {
                        selectedFilterPaths = (selectedFilterPaths.filterNot { it == editingPath } + pathInput)
                            .distinct().sorted()
                        pathEditorOpen = false
                    },
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pathEditorOpen = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

}

@Composable
private fun PathPickerRow(
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

@Composable
private fun FilterPathRow(
    enabled: Boolean,
    path: String,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = path,
                style = MaterialTheme.typography.bodyMedium,
            )
            PathCharacterWarning(path)
        }
        IconButton(onClick = onEdit, enabled = enabled) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_path))
        }
        IconButton(onClick = onRemove, enabled = enabled) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun TemplateToggleRow(
    enabled: Boolean,
    checked: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            enabled = enabled,
            checked = checked,
            onCheckedChange = null,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun PathCharacterWarning(path: String) {
    val revealed = remember(path) { FilterPathDisplay.revealed(path) }
    if (revealed != path) {
        Text(
            stringResource(R.string.path_invisible_warning, revealed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}
