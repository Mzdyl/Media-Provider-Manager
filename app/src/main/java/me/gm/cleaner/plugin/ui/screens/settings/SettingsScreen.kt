package me.gm.cleaner.plugin.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.gm.cleaner.plugin.R
import org.json.JSONObject

@Composable
fun SettingsScreen(
    rootSpJson: String?,
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

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
        ) {
            Text(
                text = stringResource(R.string.global_header),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SettingsSwitchItem(
                title = stringResource(R.string.reject_nonstandard_dir_title),
                summary = stringResource(R.string.reject_nonstandard_dir_summary),
                checked = prefs.optBoolean("reject_nonstandard_dir", true),
                enabled = false,
                onCheckedChange = {},
            )
            SettingsSwitchItem(
                title = stringResource(R.string.usage_record_title),
                summary = stringResource(R.string.usage_record_summary),
                checked = usageRecord,
                onCheckedChange = { newValue ->
                    usageRecord = newValue
                    val updated = JSONObject(prefs.toString())
                    updated.put("usage_record", newValue)
                    onRootSettingsChange(updated.toString())
                },
            )
            HorizontalDivider()
            Text(
                text = stringResource(R.string.scoped_header),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SettingsNavItem(
                title = stringResource(R.string.template_management_title),
                onClick = onTemplatesClick,
            )
            HorizontalDivider()
            Text(
                text = stringResource(R.string.backup_restore_header),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SettingsNavItem(
                title = stringResource(R.string.backup_title),
                onClick = onBackup,
            )
            SettingsNavItem(
                title = stringResource(R.string.restore_title),
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

@Composable
private fun SettingsSwitchItem(
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
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
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Default.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
