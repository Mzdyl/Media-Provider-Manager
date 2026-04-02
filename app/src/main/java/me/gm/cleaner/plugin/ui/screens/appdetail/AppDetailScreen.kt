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

package me.gm.cleaner.plugin.ui.screens.appdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.gm.cleaner.plugin.R
import me.gm.cleaner.plugin.model.SpIdentifiers.TEMPLATE_PREFERENCES
import me.gm.cleaner.plugin.model.Template
import me.gm.cleaner.plugin.model.Templates
import me.gm.cleaner.plugin.ui.module.BinderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    packageName: String,
    label: String?,
    templates: List<Template> = emptyList(),
    onNavigateBack: () -> Unit,
    onCreateTemplate: () -> Unit,
    binderViewModel: BinderViewModel,
) {
    val appTemplates = templates.filter { packageName in (it.applyToApp ?: emptyList()) }
    val availableTemplates = templates.filter { packageName !in (it.applyToApp ?: emptyList()) }
    var showTemplatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(label ?: packageName) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            item {
                Text(
                    text = "Applied Templates (${appTemplates.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(appTemplates, key = { it.templateName }) { template ->
                AppliedTemplateCard(
                    template = template,
                    onRemove = {
                        updateTemplateApplyToApp(binderViewModel, template, packageName, remove = true)
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = {
                        if (availableTemplates.isEmpty()) {
                            onCreateTemplate()
                        } else {
                            showTemplatePicker = true
                        }
                    }),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        Text(
                            text = if (availableTemplates.isEmpty()) "Create new template" else "Add to template",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }

    if (showTemplatePicker) {
        TemplatePickerDialog(
            templates = availableTemplates,
            onDismiss = { showTemplatePicker = false },
            onTemplateSelected = { template ->
                updateTemplateApplyToApp(binderViewModel, template, packageName, remove = false)
                showTemplatePicker = false
            },
            onCreateNew = {
                showTemplatePicker = false
                onCreateTemplate()
            },
        )
    }
}

private fun updateTemplateApplyToApp(
    binderViewModel: BinderViewModel,
    template: Template,
    packageName: String,
    remove: Boolean,
) {
    val existingTemplates = Templates(binderViewModel.readTemplateSp()).values.toMutableList()
    val index = existingTemplates.indexOfFirst { it.templateName == template.templateName }
    if (index == -1) return

    val currentPackages = template.applyToApp?.toMutableList() ?: mutableListOf()
    if (remove) {
        currentPackages.remove(packageName)
    } else {
        if (packageName !in currentPackages) {
            currentPackages.add(packageName)
        }
    }

    val updatedTemplate = template.copy(
        applyToApp = currentPackages.ifEmpty { null },
    )
    existingTemplates[index] = updatedTemplate

    val json = me.gm.cleaner.plugin.model.Template.GSON.toJson(existingTemplates)
    binderViewModel.writeSp(TEMPLATE_PREFERENCES, json)
}

@Composable
private fun AppliedTemplateCard(
    template: Template,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.templateName,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = template.hookOperation.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun TemplatePickerDialog(
    templates: List<Template>,
    onDismiss: () -> Unit,
    onTemplateSelected: (Template) -> Unit,
    onCreateNew: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to template") },
        text = {
            LazyColumn {
                items(templates, key = { it.templateName }) { template ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onTemplateSelected(template) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = template.templateName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onCreateNew() }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Create new template",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
