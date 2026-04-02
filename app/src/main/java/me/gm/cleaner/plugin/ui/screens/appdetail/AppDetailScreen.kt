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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.gm.cleaner.plugin.R
import me.gm.cleaner.plugin.model.SpIdentifiers.TEMPLATE_PREFERENCES
import me.gm.cleaner.plugin.model.Template
import me.gm.cleaner.plugin.model.Templates
import me.gm.cleaner.plugin.ui.components.EmptyStateCard
import me.gm.cleaner.plugin.ui.components.SecondaryTopBar
import me.gm.cleaner.plugin.ui.module.BinderViewModel
import me.gm.cleaner.plugin.ui.screens.templating.templateFilterPathSummary
import me.gm.cleaner.plugin.ui.screens.templating.templateMediaTypeSummary
import me.gm.cleaner.plugin.ui.screens.templating.templateOperationSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    packageName: String,
    label: String?,
    templates: List<Template> = emptyList(),
    onNavigateBack: () -> Unit,
    onCreateTemplate: () -> Unit,
    onEditTemplate: (Template) -> Unit,
    binderViewModel: BinderViewModel,
) {
    val appTemplates = templates.filter { packageName in (it.applyToApp ?: emptyList()) }
    val availableTemplates = templates.filter { packageName !in (it.applyToApp ?: emptyList()) }
    var showTemplatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SecondaryTopBar(
                title = label ?: packageName,
                onNavigateBack = onNavigateBack,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = stringResource(R.string.applied_templates_count, appTemplates.size),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = packageName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            if (appTemplates.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = stringResource(R.string.applied_templates_count, 0),
                        subtitle = stringResource(R.string.add_to_template),
                        icon = Icons.Default.Check,
                    )
                }
            } else {
                items(appTemplates, key = { it.templateName }) { template ->
                    AppliedTemplateCard(
                        template = template,
                        onClick = { onEditTemplate(template) },
                        onRemove = {
                            updateTemplateApplyToApp(binderViewModel, template, packageName, remove = true)
                        },
                    )
                }
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = {
                            if (availableTemplates.isEmpty()) {
                                onCreateTemplate()
                            } else {
                                showTemplatePicker = true
                            }
                        }),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
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
                            text = if (availableTemplates.isEmpty()) {
                                stringResource(R.string.create_new_template)
                            } else {
                                stringResource(R.string.add_to_template)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
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
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
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
                    text = templateOperationSummary(context, template),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                templateMediaTypeSummary(context, template)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                templateFilterPathSummary(context, template)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.remove_from_template),
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
        title = { Text(stringResource(R.string.add_to_template)) },
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
                            text = stringResource(R.string.create_new_template),
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
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
