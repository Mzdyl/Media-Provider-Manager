package me.gm.cleaner.plugin.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import me.gm.cleaner.plugin.R
import me.gm.cleaner.plugin.dao.RootPreferences
import me.gm.cleaner.plugin.ui.navigation.AppRoute
import me.gm.cleaner.plugin.ui.navigation.AppNavHost
import me.gm.cleaner.plugin.ui.navigation.topLevelDestinations
import me.gm.cleaner.plugin.ui.theme.MediaProviderManagerTheme

private fun mapOldDestinationIdToRoute(id: Int): Any? = when (id) {
    R.id.applist_fragment -> AppRoute.AppList
    R.id.usage_record_fragment -> AppRoute.UsageRecord
    R.id.settings_fragment -> AppRoute.Settings
    R.id.about_fragment -> AppRoute.About
    else -> null
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediaProviderManagerTheme {
                MainScreen()
            }
        }
    }
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
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerHeader()
                drawerItems.forEach { item ->
                    if (item.section != null) {
                        Text(
                            text = stringResource(item.section),
                            modifier = Modifier.padding(
                                NavigationDrawerItemDefaults.ItemPadding
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val selected = currentDestination?.hierarchy?.any {
                        it.hasRoute(item.route::class)
                    } == true
                    NavigationDrawerItem(
                        icon = { Icon(item.icon, contentDescription = null) },
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
                        modifier = Modifier.padding(
                            NavigationDrawerItemDefaults.ItemPadding
                        ),
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                if (isTopLevel) {
                    TopAppBar(
                        title = {
                            val labelRes = drawerItems.find { item ->
                                currentDestination?.hierarchy?.any { it.hasRoute(item.route::class) } == true
                            }?.label
                            if (labelRes != null) Text(stringResource(labelRes))
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.open_drawer))
                            }
                        },
                    )
                } else {
                    TopAppBar(
                        title = {
                            val labelRes = drawerItems.find { item ->
                                currentDestination?.hierarchy?.any { it.hasRoute(item.route::class) } == true
                            }?.label
                            if (labelRes != null) Text(stringResource(labelRes))
                        },
                        navigationIcon = {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                    )
                }
            },
        ) { paddingValues ->
            AppNavHost(
                navController = navController,
                modifier = Modifier.padding(paddingValues).fillMaxSize(),
                startDestination = startDestination,
            )
        }
    }
}

@Composable
private fun DrawerHeader() {
    val context = LocalContext.current
    var statusText by remember { mutableStateOf(context.getString(R.string.loading)) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        try {
            statusText = context.getString(R.string.not_active)
        } catch (_: Exception) {
            statusText = context.getString(R.string.not_active)
        }
    }

    Column(
        modifier = Modifier.padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.padding(top = 8.dp))
        HorizontalDivider()
    }
}
