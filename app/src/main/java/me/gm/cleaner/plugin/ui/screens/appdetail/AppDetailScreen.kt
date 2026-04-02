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

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.gm.cleaner.plugin.R
import me.gm.cleaner.plugin.dao.MediaProviderOperation.Companion.OP_DELETE
import me.gm.cleaner.plugin.dao.MediaProviderOperation.Companion.OP_INSERT
import me.gm.cleaner.plugin.dao.MediaProviderOperation.Companion.OP_QUERY
import me.gm.cleaner.plugin.model.SpIdentifiers.TEMPLATE_PREFERENCES
import me.gm.cleaner.plugin.model.Template
import me.gm.cleaner.plugin.model.Templates
import me.gm.cleaner.plugin.ui.components.EmptyStateCard
import me.gm.cleaner.plugin.ui.components.SectionHeader
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
    val context = LocalContext.current
    val packageInfo = remember(packageName, binderViewModel) {
        binderViewModel.getPackageInfo(packageName)
    }
    val usageTimes = remember(packageName, binderViewModel) {
        listOf(
            OP_QUERY to R.string.query_times,
            OP_INSERT to R.string.insert_times,
            OP_DELETE to R.string.delete_times,
        ).mapNotNull { (operation, resId) ->
            val count = binderViewModel.packageUsageTimes(operation, listOf(packageName))
            if (count == 0) null else context.getString(resId, count)
        }
    }

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
                AppOverviewCard(
                    packageName = packageName,
                    label = label,
                    packageInfo = packageInfo,
                    usageTimes = usageTimes,
                )
            }
            item {
                SectionHeader(
                    title = stringResource(R.string.applied_templates_count, appTemplates.size),
                    supporting = packageName,
                )
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
                        .clickable(onClick = onCreateTemplate),
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
                            text = stringResource(R.string.create_new_template),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            item {
                SectionHeader(
                    title = stringResource(R.string.add_to_template),
                    supporting = if (availableTemplates.isEmpty()) {
                        stringResource(R.string.no_templates)
                    } else {
                        stringResource(R.string.template_count, availableTemplates.size)
                    },
                )
            }
            if (availableTemplates.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = stringResource(R.string.no_templates),
                        subtitle = stringResource(R.string.create_template_title),
                        icon = Icons.Default.Add,
                    )
                }
            } else {
                items(availableTemplates, key = { it.templateName }) { template ->
                    AvailableTemplateCard(
                        template = template,
                        onAdd = {
                            updateTemplateApplyToApp(
                                binderViewModel = binderViewModel,
                                template = template,
                                packageName = packageName,
                                remove = false,
                            )
                        },
                    )
                }
            }
        }
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
private fun AppOverviewCard(
    packageName: String,
    label: String?,
    packageInfo: android.content.pm.PackageInfo?,
    usageTimes: List<String>,
) {
    val context = LocalContext.current
    Card(
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppPackageIcon(
                    packageInfo = packageInfo,
                    modifier = Modifier.size(52.dp),
                )
                Column(
                    modifier = Modifier
                        .padding(start = 14.dp)
                        .weight(1f),
                ) {
                    Text(
                        text = buildString {
                            append(label ?: packageName)
                            packageInfo?.versionName?.let {
                                append(" ")
                                append(it)
                            }
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = packageName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                AssistChip(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    label = { Text(stringResource(R.string.app_info)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                        )
                    },
                )
            }
            packageInfo?.applicationInfo?.targetSdkVersion?.let {
                Text(
                    text = "SDK $it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            if (usageTimes.isNotEmpty()) {
                Text(
                    text = usageTimes.joinToString(stringResource(R.string.delimiter)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun AppPackageIcon(
    packageInfo: android.content.pm.PackageInfo?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val icon = remember(packageInfo?.packageName) {
        try {
            packageInfo?.applicationInfo?.loadIcon(context.packageManager)
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
    } else {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            modifier = modifier,
        )
    }
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
private fun AvailableTemplateCard(
    template: Template,
    onAdd: () -> Unit,
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAdd),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
    {
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
            }
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
