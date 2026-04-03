package me.gm.cleaner.plugin.ui.main

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.gm.cleaner.plugin.R
import me.gm.cleaner.plugin.dao.RootPreferences
import me.gm.cleaner.plugin.ui.navigation.AppRoute
import me.gm.cleaner.plugin.ui.navigation.AppNavHost
import me.gm.cleaner.plugin.ui.navigation.topLevelDestinations
import me.gm.cleaner.plugin.ui.components.StatusBadge
import me.gm.cleaner.plugin.ui.module.BinderViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private fun mapOldDestinationIdToRoute(id: Int): Any? = when (id) {
    R.id.applist_fragment -> AppRoute.AppList
    R.id.usage_record_fragment -> AppRoute.UsageRecord
    R.id.settings_fragment -> AppRoute.Settings
    R.id.about_fragment -> AppRoute.About
    else -> null
}

data class DrawerItem(
    val route: Any,
    val label: Int,
    val icon: ImageVector,
    val section: Int? = null,
)

val drawerItems = listOf(
    DrawerItem(AppRoute.AppList, R.string.app_management, Icons.Outlined.Web, section = R.string.module),
    DrawerItem(AppRoute.UsageRecord, R.string.usage_record, Icons.Outlined.History, section = R.string.module),
    DrawerItem(AppRoute.Settings, R.string.settings, Icons.Default.Settings, section = R.string.module),
    DrawerItem(AppRoute.About, R.string.about, Icons.Default.Info),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val binderViewModel: BinderViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isTopLevel = currentDestination?.hierarchy?.any { dest ->
        topLevelDestinations.any { route ->
            dest.hasRoute(route::class)
        }
    } == true

    val startDestination = remember {
        mapOldDestinationIdToRoute(RootPreferences.startDestination) ?: AppRoute.AppList
    }

    ModalNavigationDrawer(
        gesturesEnabled = isTopLevel,
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.widthIn(max = 280.dp),
            ) {
                DrawerHeader(binderViewModel = binderViewModel)
                var lastSection: Int? = null
                drawerItems.forEach { item ->
                    if (item.section != null && item.section != lastSection) {
                        lastSection = item.section
                        Text(
                            text = stringResource(item.section),
                            modifier = Modifier.padding(
                                start = 24.dp,
                                end = 24.dp,
                                top = 8.dp,
                                bottom = 4.dp,
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    val selected = currentDestination?.hierarchy?.any {
                        it.hasRoute(item.route::class)
                    } == true
                    NavigationDrawerItem(
                        icon = {
                            androidx.compose.material3.Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(item.label)) },
                        selected = selected,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                            RootPreferences.startDestination = when (item.route) {
                                AppRoute.AppList -> R.id.applist_fragment
                                AppRoute.UsageRecord -> R.id.usage_record_fragment
                                AppRoute.Settings -> R.id.settings_fragment
                                AppRoute.About -> R.id.about_fragment
                                else -> R.id.applist_fragment
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                }
            }
        },
    ) {
        AppNavHost(
            navController = navController,
            modifier = Modifier.fillMaxSize(),
            startDestination = startDestination,
            binderViewModel = binderViewModel,
            onOpenDrawer = { scope.launch { drawerState.open() } },
        )
    }
}

@Composable
private fun DrawerHeader(
    binderViewModel: BinderViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    var isActive by remember { mutableStateOf(false) }
    var moduleVersion by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var pollingJob by remember { mutableStateOf<Job?>(null) }

    suspend fun checkActivation() {
        isRefreshing = true
        binderViewModel.refreshBinder()
        isActive = binderViewModel.pingBinder()
        moduleVersion = binderViewModel.moduleVersion
        isRefreshing = false
    }

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                if (binderViewModel.pingBinder()) {
                    isActive = true
                    moduleVersion = binderViewModel.moduleVersion
                    Toast.makeText(
                        context,
                        context.getString(R.string.module_activated),
                        Toast.LENGTH_SHORT,
                    ).show()
                    break
                }
                delay(500)
            }
            pollingJob = null
        }
    }

    androidx.compose.runtime.LaunchedEffect(binderViewModel) {
        checkActivation()
        if (!isActive) {
            startPolling()
        }
    }

    DisposableEffect(binderViewModel) {
        onDispose {
            pollingJob?.cancel()
            pollingJob = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 20.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_outline_apps_24),
            contentDescription = null,
            modifier = Modifier.size(36.dp),
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.size(6.dp))
        StatusBadge(
            text = if (isActive) stringResource(R.string.active) else stringResource(R.string.not_active),
            icon = if (isActive) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
            positive = isActive,
            onClick = {
                if (!isActive && !isRefreshing) {
                    scope.launch { checkActivation() }
                } else if (!isActive && pollingJob?.isActive != true) {
                    startPolling()
                }
            }
        )
        if (!isActive) {
            Spacer(modifier = Modifier.size(10.dp))
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (pollingJob?.isActive != true) {
                            startPolling()
                        }
                    },
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.restart_scope_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
        if (moduleVersion > 0) {
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = stringResource(R.string.module_version, moduleVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
}
