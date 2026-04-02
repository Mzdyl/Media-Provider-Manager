package me.gm.cleaner.plugin.ui.screens.applist

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.gm.cleaner.plugin.R
import me.gm.cleaner.plugin.ui.module.appmanagement.AppListState
import me.gm.cleaner.plugin.ui.module.appmanagement.AppListViewModel
import me.gm.cleaner.plugin.ui.module.appmanagement.AppListModel
import me.gm.cleaner.plugin.ui.module.BinderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    binderViewModel: BinderViewModel,
    onAppClick: (packageName: String, label: String) -> Unit,
) {
    val context = LocalContext.current
    val viewModel = remember(binderViewModel) {
        AppListViewModel(
            context.applicationContext as Application,
            binderViewModel,
        )
    }
    val appsState by viewModel.appsFlow.collectAsStateWithLifecycle(initialValue = null)
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppListTopBar(
                searchQuery = searchQuery,
                isSearching = isSearching,
                onSearchQueryChange = { searchQuery = it },
                onSearchToggle = { isSearching = !isSearching },
                onRefresh = { viewModel.load() },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        when (val state = appsState) {
            null, is AppListState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    if (state is AppListState.Loading) {
                        Text(
                            text = "${state.progress}%",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            is AppListState.Done -> {
                val filteredList = state.list.filter { app ->
                    if (!isSearching || searchQuery.isBlank()) true
                    else app.label.contains(searchQuery, ignoreCase = true) ||
                        app.packageInfo.packageName.contains(searchQuery, ignoreCase = true)
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(filteredList, key = { it.packageInfo.packageName }) { app ->
                        AppListItem(
                            app = app,
                            onClick = { onAppClick(app.packageInfo.packageName, app.label) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppListItem(
    app: AppListModel,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(packageInfo = app.packageInfo, modifier = Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape))
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageInfo.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (app.ruleCount > 0) {
                androidx.compose.material3.AssistChip(
                    onClick = onClick,
                    label = { Text("${app.ruleCount}", style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AppIcon(
    packageInfo: android.content.pm.PackageInfo,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val icon = remember(packageInfo.packageName) {
        try {
            packageInfo.applicationInfo?.loadIcon(context.packageManager)
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
        Image(
            painter = painterResource(R.drawable.ic_outline_apps_24),
            contentDescription = null,
            modifier = modifier,
        )
    }
}

@Composable
private fun EmptyListState(paddingValues: PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(paddingValues),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No apps found",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
