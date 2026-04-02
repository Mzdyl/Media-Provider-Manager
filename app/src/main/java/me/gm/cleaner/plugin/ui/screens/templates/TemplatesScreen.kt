package me.gm.cleaner.plugin.ui.screens.templates

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.gm.cleaner.plugin.R

@Composable
fun TemplatesScreen(
    onNavigateBack: () -> Unit,
    onCreateTemplate: () -> Unit,
) {
    Scaffold { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            Text(stringResource(R.string.template_management_title))
        }
    }
}
