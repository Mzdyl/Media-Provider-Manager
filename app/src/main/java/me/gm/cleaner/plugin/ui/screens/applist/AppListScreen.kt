package me.gm.cleaner.plugin.ui.screens.applist

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Web
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.gm.cleaner.plugin.R
import me.gm.cleaner.plugin.dao.RootPreferences
import me.gm.cleaner.plugin.ui.components.EmptyStateCard
import me.gm.cleaner.plugin.ui.module.appmanagement.AppListState
import me.gm.cleaner.plugin.ui.module.appmanagement.AppListViewModel
import me.gm.cleaner.plugin.ui.module.appmanagement.AppListModel
import me.gm.cleaner.plugin.ui.module.BinderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    binderViewModel: BinderViewModel,
    onOpenDrawer: () -> Unit,
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
    val showRuleCount by RootPreferences.ruleCountFlowable.asFlow().collectAsStateWithLifecycle(
        initialValue = RootPreferences.ruleCountFlowable.value
    )
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    Scaffold(
        topBar = {
            AppListTopBar(
                onOpenDrawer = onOpenDrawer,
                searchQuery = searchQuery,
                isSearching = isSearching,
                onSearchQueryChange = { searchQuery = it },
                onSearchToggle = {
                    isSearching = !isSearching
                    if (!isSearching) {
                        searchQuery = ""
                    }
                },
                onRefresh = { viewModel.load() },
            )
        },
    ) { paddingValues ->
        when (val state = appsState) {
            null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                        if (state is AppListState.Loading) {
                            Text(
                                text = "${state.progress}%",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            is AppListState.Loading -> {
                if (state.list == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                            Text(
                                text = "${state.progress}%",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                } else {
                    AppListContent(
                        state = state,
                        paddingValues = paddingValues,
                        isSearching = isSearching,
                        searchQuery = searchQuery,
                        showRuleCount = showRuleCount,
                        pullToRefreshState = pullToRefreshState,
                        onRefresh = { viewModel.load() },
                        onAppClick = onAppClick,
                    )
                }
            }

            is AppListState.Done -> {
                AppListContent(
                    state = state,
                    paddingValues = paddingValues,
                    isSearching = isSearching,
                    searchQuery = searchQuery,
                    showRuleCount = showRuleCount,
                    pullToRefreshState = pullToRefreshState,
                    onRefresh = { viewModel.load() },
                    onAppClick = onAppClick,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppListContent(
    state: AppListState,
    paddingValues: PaddingValues,
    isSearching: Boolean,
    searchQuery: String,
    showRuleCount: Boolean,
    pullToRefreshState: androidx.compose.material3.pulltorefresh.PullToRefreshState,
    onRefresh: () -> Unit,
    onAppClick: (packageName: String, label: String) -> Unit,
) {
    val baseList = when (state) {
        is AppListState.Loading -> state.list
        is AppListState.Done -> state.list
    }.orEmpty()
    val filteredList = baseList.filter { app ->
        if (!isSearching || searchQuery.isBlank()) true
        else app.label.contains(searchQuery, ignoreCase = true) ||
            app.packageInfo.packageName.contains(searchQuery, ignoreCase = true)
    }
    PullToRefreshBox(
        isRefreshing = state is AppListState.Loading && state.list != null,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        state = pullToRefreshState,
    ) {
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                EmptyStateCard(
                    title = stringResource(R.string.no_apps_found),
                    subtitle = stringResource(R.string.empty_search_hint),
                    icon = Icons.Outlined.Web,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Web,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(
                                    text = stringResource(R.string.app_management),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    text = "${filteredList.size} / ${baseList.size}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
                items(filteredList, key = { it.packageInfo.packageName }) { app ->
                    AppListItem(
                        app = app,
                        showRuleCount = showRuleCount,
                        onClick = { onAppClick(app.packageInfo.packageName, app.label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppListItem(
    app: AppListModel,
    showRuleCount: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(
                packageInfo = app.packageInfo,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
            )
            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f)
            ) {
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
            if (showRuleCount && app.ruleCount > 0) {
                androidx.compose.material3.AssistChip(
                    onClick = onClick,
                    label = { Text("${app.ruleCount}", style = MaterialTheme.typography.labelMedium) },
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
