package me.gm.cleaner.plugin.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import me.gm.cleaner.plugin.model.Template
import me.gm.cleaner.plugin.model.Templates
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
    val sparseArray by binderViewModel.remoteSpCacheLiveData.observeAsState()
    val templateList: List<Template> = sparseArray?.let { sparse ->
        val json = sparse.get(me.gm.cleaner.plugin.model.SpIdentifiers.TEMPLATE_PREFERENCES)
        runCatching { Templates(json).values }.getOrDefault(emptyList())
    } ?: emptyList()

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
                templates = templateList,
                onNavigateBack = { navController.popBackStack() },
                onCreateTemplate = {
                    navController.navigate(AppRoute.CreateTemplate())
                },
                onToggleTemplate = { template, add ->
                    // TODO: implement toggle
                },
            )
        }
        composable<AppRoute.UsageRecord> {
            UsageRecordScreen(binderViewModel = binderViewModel)
        }
        composable<AppRoute.Settings> {
            SettingsScreen(
                onTemplatesClick = { navController.navigate(AppRoute.Templates) },
                onBackup = {
                    binderViewModel.readTemplateSp()
                },
                onRestore = {
                    // TODO: implement restore
                },
            )
        }
        composable<AppRoute.Templates> {
            TemplatesScreen(
                templates = templateList,
                onNavigateBack = { navController.popBackStack() },
                onCreateTemplate = { navController.navigate(AppRoute.CreateTemplate()) },
                onEditTemplate = { template ->
                    navController.navigate(
                        AppRoute.CreateTemplate(
                            templateName = template.templateName,
                            hookOperation = template.hookOperation.toList(),
                            packageNames = template.applyToApp?.map { it.toString() },
                            permittedMediaTypes = template.permittedMediaTypes?.map { it.toString() },
                        )
                    )
                },
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
                onSave = { navController.popBackStack() },
            )
        }
        composable<AppRoute.About> {
            AboutScreen()
        }
    }
}
