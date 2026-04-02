package me.gm.cleaner.plugin.ui.screens.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.gm.cleaner.plugin.R

@Composable
fun AboutScreen() {
    var readmeContent by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            readmeContent = "README content will be loaded here"
            isLoading = false
        } catch (e: Exception) {
            errorMessage = e.message ?: e::class.java.simpleName
            isLoading = false
        }
    }

    Scaffold { paddingValues ->
        when {
            isLoading -> {
                Column(
                    modifier = Modifier.padding(paddingValues).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                }
            }
            errorMessage != null -> {
                Text(
                    text = stringResource(R.string.about_load_error, errorMessage!!),
                    modifier = Modifier.padding(paddingValues).padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            readmeContent != null -> {
                Text(
                    text = readmeContent ?: "",
                    modifier = Modifier.padding(paddingValues).padding(16.dp).fillMaxSize(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
