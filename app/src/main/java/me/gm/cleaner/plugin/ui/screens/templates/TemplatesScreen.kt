package me.gm.cleaner.plugin.ui.screens.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import me.gm.cleaner.plugin.model.Template
import me.gm.cleaner.plugin.ui.components.EmptyStateCard
import me.gm.cleaner.plugin.ui.components.SecondaryTopBar
import me.gm.cleaner.plugin.ui.components.SectionHeader
import me.gm.cleaner.plugin.ui.screens.templating.templateFilterPathSummary
import me.gm.cleaner.plugin.ui.screens.templating.templateMediaTypeSummary
import me.gm.cleaner.plugin.ui.screens.templating.templateOperationSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    templates: List<Template> = emptyList(),
    onNavigateBack: () -> Unit,
    onCreateTemplate: () -> Unit,
    onDeleteTemplate: (Template) -> Unit,
    onEditTemplate: (Template) -> Unit,
) {
    var templatePendingDelete by remember { mutableStateOf<Template?>(null) }

    Scaffold(
        topBar = {
            SecondaryTopBar(
                title = stringResource(R.string.template_management_title),
                onNavigateBack = onNavigateBack,
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateTemplate,
                containerColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.create_template_title))
            }
        },
    ) { paddingValues ->
        if (templates.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                EmptyStateCard(
                    title = stringResource(R.string.no_templates),
                    subtitle = stringResource(R.string.create_template_title),
                    icon = Icons.AutoMirrored.Filled.Rule,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            SectionHeader(
                                title = stringResource(R.string.template_management_title),
                                supporting = stringResource(R.string.template_count, templates.size),
                            )
                        }
                    }
                }
                items(templates, key = { it.templateName }) { template ->
                    TemplateItem(
                        template = template,
                        onDelete = { templatePendingDelete = template },
                        onClick = { onEditTemplate(template) },
                    )
                }
            }
        }
    }

    templatePendingDelete?.let { template ->
        AlertDialog(
            onDismissRequest = { templatePendingDelete = null },
            title = { Text(stringResource(R.string.delete_template_title)) },
            text = { Text(stringResource(R.string.delete_template_message, template.templateName)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteTemplate(template)
                    templatePendingDelete = null
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { templatePendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun TemplateItem(
    template: Template,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val operationSummary = templateOperationSummary(context, template)
    val mediaTypeSummary = templateMediaTypeSummary(context, template)
    val filterPathSummary = templateFilterPathSummary(context, template)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.templateName,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = operationSummary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                mediaTypeSummary?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                filterPathSummary?.let {
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
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
