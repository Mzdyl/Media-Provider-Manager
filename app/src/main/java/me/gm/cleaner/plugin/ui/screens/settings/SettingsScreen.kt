package me.gm.cleaner.plugin.ui.screens.settings

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.gm.cleaner.plugin.R
import me.gm.cleaner.plugin.ui.components.PreferenceGroup
import me.gm.cleaner.plugin.ui.components.SectionHeader
import me.gm.cleaner.plugin.ui.components.TopLevelTopBar
import org.json.JSONObject

@Composable
fun SettingsScreen(
    rootSpJson: String?,
    onOpenDrawer: () -> Unit,
    onTemplatesClick: () -> Unit,
    onBackup: () -> Unit,
    onRootSettingsChange: (String) -> Unit,
    onTemplateRestore: (String) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(rootSpJson) {
        runCatching { JSONObject(rootSpJson ?: "{}") }.getOrDefault(JSONObject())
    }
    var usageRecord by remember(rootSpJson) {
        mutableStateOf(prefs.optBoolean("usage_record", true))
    }

    Scaffold(
        topBar = {
            TopLevelTopBar(
                title = stringResource(R.string.settings),
                onOpenDrawer = onOpenDrawer,
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = stringResource(R.string.global_header),
                        supporting = stringResource(R.string.reject_nonstandard_dir_summary),
                    )
                    PreferenceGroup {
                        SettingsSwitchItem(
                            title = stringResource(R.string.reject_nonstandard_dir_title),
                            summary = stringResource(R.string.reject_nonstandard_dir_summary),
                            checked = prefs.optBoolean("reject_nonstandard_dir", true),
                            icon = Icons.Default.Settings,
                            enabled = false,
                            onCheckedChange = {},
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsSwitchItem(
                            title = stringResource(R.string.usage_record_title),
                            summary = stringResource(R.string.usage_record_summary),
                            checked = usageRecord,
                            icon = Icons.Default.Tune,
                            onCheckedChange = { newValue ->
                                usageRecord = newValue
                                val updated = JSONObject(prefs.toString())
                                updated.put("usage_record", newValue)
                                onRootSettingsChange(updated.toString())
                            },
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = stringResource(R.string.scoped_header),
                        supporting = stringResource(R.string.template_management_title),
                    )
                    PreferenceGroup {
                        SettingsNavItem(
                            title = stringResource(R.string.template_management_title),
                            summary = stringResource(R.string.create_template_title),
                            icon = Icons.AutoMirrored.Filled.Rule,
                            onClick = onTemplatesClick,
                        )
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader(
                        title = stringResource(R.string.backup_restore_header),
                        supporting = stringResource(R.string.backup_title),
                    )
                    PreferenceGroup {
                        SettingsNavItem(
                            title = stringResource(R.string.backup_title),
                            summary = stringResource(R.string.backup_restore_header),
                            icon = Icons.Default.ContentCopy,
                            onClick = onBackup,
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsNavItem(
                            title = stringResource(R.string.restore_title),
                            summary = stringResource(R.string.restore_ok),
                            icon = Icons.Default.ContentCopy,
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val text = clip.getItemAt(0).text?.toString()
                                    if (!text.isNullOrEmpty()) {
                                        try {
                                            JSONObject(text)
                                            onTemplateRestore(text)
                                            Toast.makeText(context, R.string.restore_ok, Toast.LENGTH_SHORT).show()
                                        } catch (_: Exception) {
                                            Toast.makeText(context, R.string.restore_fail_invalid, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun SettingsNavItem(
    title: String,
    summary: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 12.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
