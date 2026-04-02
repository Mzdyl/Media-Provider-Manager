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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import me.gm.cleaner.plugin.R
import me.gm.cleaner.plugin.dao.RootPreferences
import me.gm.cleaner.plugin.model.SpIdentifiers
import me.gm.cleaner.plugin.ui.components.EmptyStateCard
import me.gm.cleaner.plugin.ui.module.BinderViewModel
import me.gm.cleaner.plugin.ui.module.appmanagement.AppListModel
import me.gm.cleaner.plugin.ui.module.appmanagement.AppListState
import me.gm.cleaner.plugin.ui.module.appmanagement.AppListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    binderViewModel: BinderViewModel,
    onOpenDrawer: () -> Unit,
    onAppClick: (packageName: String, label: String) -> Unit,
) {
    val context = LocalContext.current
    val viewModel: AppListViewModel = viewModel(
        factory = AppListViewModel.provideFactory(
            context.applicationContext as Application,
            binderViewModel,
        ),
    )
    val appsState by viewModel.appsFlow.collectAsStateWithLifecycle(initialValue = null)
    val isSearching by viewModel.isSearchingFlow.collectAsStateWithLifecycle()
    val searchQuery by viewModel.queryTextFlow.collectAsStateWithLifecycle()
    val showRuleCount by RootPreferences.ruleCountFlowable.asFlow().collectAsStateWithLifecycle(
        initialValue = RootPreferences.ruleCountFlowable.value
    )
    val remoteSpCache by binderViewModel.remoteSpCacheLiveData.observeAsState()
    val templateSp = remoteSpCache?.get(SpIdentifiers.TEMPLATE_PREFERENCES)
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(templateSp) {
        if (templateSp != null && !viewModel.isLoading) {
            viewModel.update()
        }
    }

    Scaffold(
        topBar = {
            AppListTopBar(
                onOpenDrawer = onOpenDrawer,
                searchQuery = searchQuery,
                isSearching = isSearching,
                onSearchQueryChange = { viewModel.queryText = it },
                onSearchToggle = {
                    val nextValue = !isSearching
                    viewModel.isSearching = nextValue
                    if (!nextValue) {
                        viewModel.queryText = ""
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
    showRuleCount: Boolean,
    pullToRefreshState: androidx.compose.material3.pulltorefresh.PullToRefreshState,
    onRefresh: () -> Unit,
    onAppClick: (packageName: String, label: String) -> Unit,
) {
    val displayedList = when (state) {
        is AppListState.Loading -> state.list
        is AppListState.Done -> state.list
    }.orEmpty()
    PullToRefreshBox(
        isRefreshing = state is AppListState.Loading && state.list != null,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        state = pullToRefreshState,
    ) {
        if (displayedList.isEmpty()) {
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
                item(contentType = "summary") {
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
                                    text = "${displayedList.size}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
                items(
                    items = displayedList,
                    key = { it.packageInfo.packageName },
                    contentType = { "app" },
                ) { app ->
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
    val icon: android.graphics.drawable.Drawable? = remember(packageInfo.packageName) {
        try {
            packageInfo.applicationInfo?.loadIcon(context.packageManager)
        } catch (_: Exception) {
            null
        }
    }
    if (icon != null) {
        Image(
            painter = rememberDrawablePainter(drawable = icon),
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
