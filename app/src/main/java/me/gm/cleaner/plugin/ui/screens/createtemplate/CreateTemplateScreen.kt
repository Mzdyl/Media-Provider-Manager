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

import android.content.pm.PackageInfo
import android.provider.MediaStore.Files.FileColumns
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.gm.cleaner.plugin.R
import me.gm.cleaner.plugin.model.SpIdentifiers.TEMPLATE_PREFERENCES
import me.gm.cleaner.plugin.model.Template
import me.gm.cleaner.plugin.model.Templates
import me.gm.cleaner.plugin.ui.module.BinderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTemplateScreen(
    templateName: String?,
    hookOperation: List<String>?,
    packageNames: List<String>?,
    permittedMediaTypes: List<String>?,
    onNavigateBack: () -> Unit,
    onSave: () -> Unit,
    binderViewModel: BinderViewModel,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isEditing = templateName != null

    // Form state
    var name by remember { mutableStateOf(templateName ?: "") }
    var selectedOperations by remember {
        mutableStateOf(hookOperation?.toSet() ?: setOf("query", "insert"))
    }
    var selectedPackages by remember { mutableStateOf(packageNames?.toSet() ?: emptySet<String>()) }
    var selectedMediaTypes by remember {
        mutableStateOf(permittedMediaTypes?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet<Int>())
    }
    var showAppPicker by remember { mutableStateOf(false) }
    var installedPackages by remember { mutableStateOf<List<PackageInfo>>(emptyList()) }
    var packagesLoaded by remember { mutableStateOf(false) }

    // Hook operation options
    val hookOperations = listOf("query", "insert")

    // Media type options: (FileColumns.MEDIA_TYPE_* constant, display label)
    val mediaTypes = listOf(
        FileColumns.MEDIA_TYPE_PLAYLIST to stringResource(R.string.media_type_playlist),
        FileColumns.MEDIA_TYPE_SUBTITLE to stringResource(R.string.media_type_subtitle),
        FileColumns.MEDIA_TYPE_AUDIO to stringResource(R.string.media_type_audio),
        FileColumns.MEDIA_TYPE_VIDEO to stringResource(R.string.media_type_video),
        FileColumns.MEDIA_TYPE_IMAGE to stringResource(R.string.media_type_image),
        FileColumns.MEDIA_TYPE_DOCUMENT to stringResource(R.string.media_type_document),
        FileColumns.MEDIA_TYPE_NONE to stringResource(R.string.media_type_none),
    )

    LaunchedEffect(Unit) {
        if (!packagesLoaded) {
            installedPackages = binderViewModel.getInstalledPackages(0)
                .sortedBy { it.applicationInfo?.loadLabel(context.packageManager).toString() }
            packagesLoaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isEditing) stringResource(R.string.edit_template_title) else stringResource(R.string.create_template_title))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                IconButton(onClick = {
                    val canSave = name.isNotBlank() && selectedOperations.isNotEmpty()
                    if (!canSave) {
                        // Show lightweight feedback
                        scope.launch {
                            snackbarHostState.showSnackbar("Please provide a name and at least one operation")
                        }
                        return@IconButton
                    }

                    // Check for duplicate name (only when creating new or renaming)
                    val existingTemplates = Templates(binderViewModel.readTemplateSp()).values
                    val nameConflict = existingTemplates.any {
                        it.templateName == name && it.templateName != templateName
                    }
                    if (nameConflict) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Template name already exists")
                        }
                        return@IconButton
                    }

                    val template = Template(
                        templateName = name,
                        hookOperation = selectedOperations.toList(),
                        applyToApp = selectedPackages.toList().ifEmpty { null },
                        permittedMediaTypes = selectedMediaTypes.toList().ifEmpty { null },
                        filterPath = null,
                    )

                    val json = Template.GSON.toJson(
                        existingTemplates.filterNot { it.templateName == templateName } + template
                    )
                    binderViewModel.writeSp(TEMPLATE_PREFERENCES, json)
                    onSave()
                }) {
                    Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save))
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
        ) {
            // Template name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.template_name_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Hook operations
            Text(
                text = stringResource(R.string.hook_operation_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            hookOperations.forEach { operation ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable {
                            selectedOperations = if (operation in selectedOperations) {
                                selectedOperations - operation
                            } else {
                                selectedOperations + operation
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = operation in selectedOperations,
                        onCheckedChange = null,
                    )
                    Text(
                        text = operation.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Apply to app
            Text(
                text = stringResource(R.string.apply_to_app_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Card(
                modifier = Modifier.fillMaxWidth()
                    .clickable { showAppPicker = true },
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (selectedPackages.isEmpty()) {
                            "All apps"
                        } else {
                            "${selectedPackages.size} app(s) selected"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (selectedPackages.isNotEmpty()) {
                        Text(
                            text = selectedPackages.take(3).joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Permitted media types
            Text(
                text = stringResource(R.string.permitted_media_types_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            mediaTypes.forEach { (value, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable {
                            selectedMediaTypes = if (value in selectedMediaTypes) {
                                selectedMediaTypes - value
                            } else {
                                selectedMediaTypes + value
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = value in selectedMediaTypes,
                        onCheckedChange = null,
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }

    // App picker dialog
    if (showAppPicker) {
        AppPickerDialog(
            installedPackages = installedPackages,
            selectedPackages = selectedPackages,
            onDismiss = { showAppPicker = false },
            onConfirm = { packages ->
                selectedPackages = packages
                showAppPicker = false
            },
        )
    }
}

@Composable
private fun AppPickerDialog(
    installedPackages: List<PackageInfo>,
    selectedPackages: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    var tempSelected by remember { mutableStateOf(selectedPackages) }

    androidx.compose.material3.AlertDialog(
        // Search field for app picker
    onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.apply_to_app_title)) },
        text = {
            var query by remember { mutableStateOf("") }
            Column(
                modifier = Modifier.fillMaxWidth().height(400.dp).verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.search)) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true,
                )
                val filteredPackages = installedPackages.filter { pi ->
                    val label = pi.applicationInfo?.loadLabel(context.packageManager)?.toString() ?: pi.packageName
                    val pkg = pi.packageName
                    label.contains(query, ignoreCase = true) || pkg.contains(query, ignoreCase = true)
                }
                filteredPackages.forEach { pi ->
                    val packageName = pi.packageName
                    val label = pi.applicationInfo?.loadLabel(context.packageManager)?.toString() ?: packageName
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                tempSelected = if (packageName in tempSelected) {
                                    tempSelected - packageName
                                } else {
                                    tempSelected + packageName
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val icon = try {
                            pi.applicationInfo?.loadIcon(context.packageManager)
                        } catch (_: Exception) { null }
                        if (icon != null) {
                            val bitmap = remember(packageName) {
                                android.graphics.Bitmap.createBitmap(
                                    icon.intrinsicWidth.coerceAtLeast(1),
                                    icon.intrinsicHeight.coerceAtLeast(1),
                                    android.graphics.Bitmap.Config.ARGB_8888,
                                ).also { b ->
                                    icon.setBounds(0, 0, b.width, b.height)
                                    icon.draw(android.graphics.Canvas(b))
                                }
                            }
                            Icon(
                                painter = BitmapPainter(bitmap.asImageBitmap()),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                            Text(
                                text = packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        if (packageName in tempSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(tempSelected) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
