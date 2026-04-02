package me.gm.cleaner.plugin.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import me.gm.cleaner.plugin.ui.module.BinderViewModel
import me.gm.cleaner.plugin.ui.screens.about.AboutScreen
import me.gm.cleaner.plugin.ui.screens.appdetail.AppDetailScreen
import me.gm.cleaner.plugin.ui.screens.applist.AppListScreen
import me.gm.cleaner.plugin.ui.screens.createtemplate.CreateTemplateScreen
import me.gm.cleaner.plugin.ui.screens.settings.SettingsScreen
import me.gm.cleaner.plugin.ui.screens.templates.TemplatesScreen
import me.gm.cleaner.plugin.ui.screens.usagerecord.UsageRecordScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    startDestination: Any = AppRoute.AppList,
    binderViewModel: BinderViewModel = hiltViewModel(),
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable<AppRoute.AppList> {
            AppListScreen(
                binderViewModel = binderViewModel,
                onAppClick = { pkg, label ->
                    navController.navigate(AppRoute.AppDetail(packageName = pkg, label = label))
                },
            )
        }
        composable<AppRoute.AppDetail> { backStackEntry ->
            val route = backStackEntry.toRoute<AppRoute.AppDetail>()
            AppDetailScreen(
                packageName = route.packageName,
                label = route.label,
                onNavigateBack = { navController.popBackStack() },
                onCreateTemplate = {
                    navController.navigate(AppRoute.CreateTemplate())
                },
            )
        }
        composable<AppRoute.UsageRecord> {
            UsageRecordScreen()
        }
        composable<AppRoute.Settings> {
            SettingsScreen(
                onTemplatesClick = { navController.navigate(AppRoute.Templates) },
            )
        }
        composable<AppRoute.Templates> {
            TemplatesScreen(
                onNavigateBack = { navController.popBackStack() },
                onCreateTemplate = { navController.navigate(AppRoute.CreateTemplate()) },
            )
        }
        composable<AppRoute.CreateTemplate> { backStackEntry ->
            val route = backStackEntry.toRoute<AppRoute.CreateTemplate>()
            CreateTemplateScreen(
                templateName = route.templateName,
                hookOperation = route.hookOperation,
                packageNames = route.packageNames,
                permittedMediaTypes = route.permittedMediaTypes,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable<AppRoute.About> {
            AboutScreen()
        }
    }
}
